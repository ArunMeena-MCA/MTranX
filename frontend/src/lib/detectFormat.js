/**
 * Detects a message's source format from its raw text, so the Source
 * dropdown can auto-select instead of requiring a manual pick every paste.
 *
 * MT detection uses SWIFT FIN Block 2 (Application Header), the only place
 * the message type actually lives - format is "{2:I<3-digit-type>..." for
 * an input message or "{2:O<3-digit-type>..." for output (see the SWIFT
 * User Handbook Block 2 spec). Block 1 alone (just "{1:...") carries no
 * message type, so it is deliberately not used as a signal here - guessing
 * from it would be exactly the kind of unsourced inference this project
 * avoids elsewhere.
 *
 * MX detection reads the ISO 20022 XML namespace
 * ("urn:iso:std:iso:20022:tech:xsd:<message-id>"), which already matches
 * this app's target_format naming convention (e.g. "pacs.008.001.08").
 *
 * Returns null when the text doesn't contain a recognizable block 2 or MX
 * namespace - callers should leave the dropdown untouched in that case
 * rather than guess.
 */
export function detectSourceFormat(text) {
  if (!text) {
    return null;
  }
  const trimmed = text.trim();
  if (!trimmed) {
    return null;
  }

  const block2Match = trimmed.match(/\{2:[IO](\d{3})/);
  if (block2Match) {
    return `MT${block2Match[1]}`;
  }

  const xmlNamespaceMatch = trimmed.match(/xmlns(?::\w+)?\s*=\s*"urn:iso:std:iso:20022:tech:xsd:([\w.]+)"/);
  if (xmlNamespaceMatch) {
    return xmlNamespaceMatch[1];
  }

  return null;
}

/**
 * Classifies a format string as MT or MX, using the same convention the
 * backend itself already relies on for its MT-vs-MX rendering choice
 * (ConverterService: doc.getTargetFormat().toUpperCase().startsWith("MT")).
 * Reused here so the Convert page's direction toggle can filter/derive
 * MT<->MX vs MX<->MT mapping pairs without inventing a second convention.
 */
export function isMtFormat(format) {
  return typeof format === "string" && format.trim().toUpperCase().startsWith("MT");
}
