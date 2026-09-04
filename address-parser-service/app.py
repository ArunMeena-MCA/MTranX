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
"""

import logging
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


class ParseAddressRequest(BaseModel):
    lines: List[str]


class ParseAddressResponse(BaseModel):
    street: Optional[str] = None
    city: Optional[str] = None
    postcode: Optional[str] = None
    country_code: Optional[str] = None
    confident: bool = False


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

    raw_country = components.get("country")
    country_code = _COUNTRY_LOOKUP.get(raw_country.lower()) if raw_country else None

    # Confidence is gated ENTIRELY on the country cross-check - see the
    # module docstring for why. A message with a real, recognized country
    # but no city/street is still reported confident (whatever WAS
    # extracted is trustworthy); a message where libpostal guessed a
    # "country" that isn't a real country at all is not.
    confident = country_code is not None

    return ParseAddressResponse(
        street=street,
        city=city,
        postcode=postcode,
        country_code=country_code,
        confident=confident,
    )
