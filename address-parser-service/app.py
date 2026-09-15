"""
Address-parser sidecar for the MT103->pacs.008 converter.

Splits free-text address lines (e.g. MT103 field 50K/59's unstructured
content) into street / city / postcode / country components, using
libpostal - a statistical NLP model trained on real-world postal address
data (OpenStreetMap + OpenAddresses), NOT a hand-written heuristic.

This exists specifically to support the pacs.008 "hybrid address" model
Swift requires from 14 November 2026: structured PostalAddress24 fields
(StrtNm/TwnNm/Ctry) populated ALONGSIDE the existing free-text AdrLine
content, not instead of it. See the Java caller (AddressParserClient) and
MT103_TO_PACS00800108.yaml's ADDRESS POLICY note for the full context.

DELIBERATE DESIGN CHOICE - country confidence gate: libpostal extracts a
"country" component as WRITTEN in the input (e.g. "india"), not
necessarily a real, canonical country name. This service cross-checks that
component against a real ISO 3166-1 list (via pycountry) before reporting
`confident: true`. If the country component is missing, or doesn't match
any real country, `confident` is false and the Java caller does NOT
populate the structured fields for that message - it falls back to
AdrLine-only, exactly today's behavior. This mirrors the same
"never guess, fail closed" posture already used throughout the mapping
document this service supports - libpostal's own city/street output is
NOT independently verified (there is no closed list of real cities to
check against), so `confident` is a country-level signal only, not a
blanket "trust every field" flag.

REFINEMENT (deterministic, no LLM - by explicit user decision): libpostal's
own "country" LABEL sometimes doesn't fire at all on unusual phrasing (e.g.
a country name immediately followed by an unrelated second city, with no
delimiter - "Chennai India New Delhi"), even though the country name is
plainly present in the text. Rather than accepting that as an unrecoverable
miss, this service ALSO scans the raw address text directly for a whole-word
match against the same authoritative ISO 3166 name/alias list, independent
of what libpostal did or didn't label. This is still a real, closed-list
lookup - not a guess - and is tried only as a SECOND pass, after libpostal's
own "country"-labeled component, which is more precise when it does fire
(a labeled component is less likely to be a coincidental false positive than
a bare substring match). See `_find_country_in_text` for the false-positive
mitigation (longest-name-first, word-boundary matching) and its documented,
irreducible limitation (a handful of country names collide with other real
words, e.g. "Georgia" the country vs. the US state).
"""

import logging
import re
from typing import List, Optional

import pycountry
from fastapi import FastAPI
from postal.parser import parse_address
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("address-parser-service")

app = FastAPI(title="MT103 Address Parser Sidecar")

# Cache of lowercased country names/aliases -> ISO 3166-1 alpha-2 code,
# built once at startup from pycountry's own authoritative country list -
# not a hand-maintained list, so it can't silently drift out of date.
_COUNTRY_LOOKUP = {}
for _country in pycountry.countries:
    _COUNTRY_LOOKUP[_country.name.lower()] = _country.alpha_2
    if hasattr(_country, "official_name"):
        _COUNTRY_LOOKUP[_country.official_name.lower()] = _country.alpha_2
    if hasattr(_country, "common_name"):
        _COUNTRY_LOOKUP[_country.common_name.lower()] = _country.alpha_2
logger.info("Loaded %d country name/alias -> ISO code entries", len(_COUNTRY_LOOKUP))

# Longest-name-first, so a multi-word country name (e.g. "United Arab
# Emirates") is tried before a shorter one that might otherwise partially
# overlap it, and pre-compiled once at startup (not per-request) since this
# scans every request. \b...\b word-boundary matching means "india" will NOT
# match inside "indiana" (no boundary between the shared "india" prefix and
# the following "n"), but it CANNOT distinguish a real country reference
# from an unrelated identical word - e.g. "Georgia" (country) is spelled
# identically to the US state of Georgia and to some real given names. This
# is a genuine, irreducible ambiguity in the country name itself, not a bug
# in the matching logic; it is the same ambiguity any parser (statistical,
# rule-based, or LLM) faces for these specific names, not something specific
# to this approach.
_COUNTRY_NAMES_BY_LENGTH = sorted(_COUNTRY_LOOKUP.keys(), key=len, reverse=True)
_COUNTRY_SCAN_PATTERN = re.compile(
    r"\b(" + "|".join(re.escape(name) for name in _COUNTRY_NAMES_BY_LENGTH) + r")\b",
    re.IGNORECASE,
)


def _find_country_in_text(text: str) -> Optional[str]:
    """Second-pass, whole-word scan of raw text against the ISO country list -
    see the module docstring's REFINEMENT section for why this exists and its
    documented limitation.

    Verified (see the test that caught this before shipping): a naive
    "return the first match" implementation silently picks the WRONG country
    when a real US address mentions both a state that collides with a
    country name and the actual country - e.g. "Atlanta, Georgia, United
    States" contains both "Georgia" (-> GE) and "United States" (-> US) as
    literal substrings; taking the first match would confidently return GE
    for a US address. Since this service's whole design principle is "don't
    guess, fail closed," multiple DISTINCT country codes found in the same
    text is treated as genuinely ambiguous and returns None - the caller
    then reports confident=false for country, exactly like "no country
    found at all," rather than silently picking one of several candidates.
    """
    codes = {
        _COUNTRY_LOOKUP[name.lower()]
        for name in _COUNTRY_SCAN_PATTERN.findall(text)
    }
    if len(codes) == 1:
        return next(iter(codes))
    return None  # zero matches (nothing found) or 2+ distinct matches (ambiguous) - both are "don't guess" cases


class ParseAddressRequest(BaseModel):
    lines: List[str]


class ParseAddressResponse(BaseModel):
    street: Optional[str] = None
    city: Optional[str] = None
    postcode: Optional[str] = None
    country_code: Optional[str] = None
    confident: bool = False
    # Diagnostic only (the Java caller doesn't read this) - tells a human
    # reviewer WHICH mechanism resolved the country, for auditability.
    country_source: Optional[str] = None


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/parse-address", response_model=ParseAddressResponse)
def parse_address_endpoint(request: ParseAddressRequest):
    if not request.lines:
        return ParseAddressResponse()

    # libpostal's own documented usage: join multi-line addresses with
    # commas for best parsing results - it was trained on comma-separated
    # address strings, not newline-separated ones.
    address_text = ", ".join(line.strip() for line in request.lines if line.strip())
    if not address_text:
        return ParseAddressResponse()

    parsed_components = parse_address(address_text)
    logger.info("libpostal parse of %r -> %r", address_text, parsed_components)

    components = {}
    for value, label in parsed_components:
        components.setdefault(label, value)

    street_parts = [components.get("house_number"), components.get("road")]
    street = " ".join(p for p in street_parts if p) or None

    city = components.get("city")
    postcode = components.get("postcode")

    # Pass 1 (preferred): libpostal's own "country"-labeled component. More
    # precise than a bare text scan when it fires, since it's positionally
    # informed, not just a substring match.
    raw_country = components.get("country")
    country_code = _COUNTRY_LOOKUP.get(raw_country.lower()) if raw_country else None
    country_source = "libpostal_label" if country_code else None

    # Pass 2 (fallback): libpostal didn't label anything as "country" at
    # all, or what it labeled didn't match a real country - scan the raw
    # text directly. See _find_country_in_text and the module docstring's
    # REFINEMENT section.
    if country_code is None:
        country_code = _find_country_in_text(address_text)
        if country_code:
            country_source = "raw_text_scan"
            logger.info(
                "Country recovered via raw-text scan (libpostal did not label it): %r -> %s",
                address_text, country_code,
            )

    # Confidence is gated ENTIRELY on the country cross-check succeeding via
    # EITHER pass - see the module docstring for why. A message with a real,
    # recognized country but no city/street is still reported confident
    # (whatever WAS extracted is trustworthy); a message where neither pass
    # found a real country is not.
    confident = country_code is not None

    return ParseAddressResponse(
        street=street,
        city=city,
        postcode=postcode,
        country_code=country_code,
        confident=confident,
        country_source=country_source,
    )
