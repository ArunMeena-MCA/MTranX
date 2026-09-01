package com.wiredesk.mtmx.parser;

import com.prowidesoftware.swift.model.SwiftBlock2Input;
import com.prowidesoftware.swift.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.wiredesk.mtmx.exception.ParsingException;
import org.springframework.stereotype.Service;

/**
 * Parses SWIFT MT messages using Prowide Core (com.prowidesoftware:pw-swift-core)
 * instead of hand-rolled regex.
 *
 * This is the accuracy win this revision is built around: Prowide understands
 * SWIFT block structure, field continuation lines, and repetitive field
 * sequences correctly, rather than us re-deriving that with a regex that is
 * always an approximation of the real FIN grammar.
 *
 * <p><b>Verification note:</b> this class uses
 * {@code com.prowidesoftware.swift.model.SwiftMessage#parse(String)} and the
 * {@code SwiftTagListBlock}/{@code Tag} API, which is Prowide Core's
 * long-standing public surface for reading a message's tag/value pairs. I was
 * not able to compile this against the actual jar in the environment this was
 * written in (no Maven Central access there), so before relying on this in
 * production: run {@code mvn compile}, fix any method-name drift against the
 * exact Prowide version pinned in {@code pom.xml}, and add a unit test that
 * parses a real sample message from your own traffic.
 */
@Service
public class MtParserService {

    public ParsedMessage parse(String rawText, String declaredFormat) {
        SwiftMessage message;
        try {
            message = SwiftMessage.parse(rawText);
        } catch (Exception e) {
            throw new ParsingException(
                    "Input declared as " + declaredFormat + " could not be parsed as a SWIFT MT message by "
                            + "Prowide: " + e.getMessage(), e);
        }

        if (message == null) {
            throw new ParsingException(
                    "Prowide returned no message for input declared as " + declaredFormat
                            + " - confirm the input includes valid block 1/2/4 structure.");
        }

        SwiftTagListBlock block4 = message.getBlock4();
        if (block4 == null || block4.isEmpty()) {
            throw new ParsingException(
                    "No block 4 (text block) found in input declared as " + declaredFormat + ".");
        }

        ParsedMessage parsed = new ParsedMessage(declaredFormat, rawText);
        for (Tag tag : block4.getTags()) {
            String value = tag.getValue() == null ? "" : tag.getValue().trim();
            parsed.addField(tag.getName(), value);
        }

        // Block 3 (user header) - carries field 121 (UETR), but also other
        // transport/session tags (103 Service Id, 108 MUR, 111, 113, 119,
        // ...) that are not payment data and have no pacs.008 target at
        // all. Deliberately extracting ONLY "121" here, not every block 3
        // tag - the mapping doc's own citation for this entry only ever
        // asked for Block3/Field121, and dumping the rest into the same
        // fields map as block 4 would trip checkUnmappedFields' strict
        // unmapped_fields_policy for tags that were never meant to be
        // payment-mapped in the first place (confirmed: field 108/MUR did
        // exactly this the first time a real message carried one).
        // Optional in SWIFT MT structure - many messages have no block 3
        // at all. Confirmed real gap (not just theoretical): the mapping
        // doc's own notes on the "121" entry flagged this as unverified
        // since v1.0, and MtParserService only ever read block 4 until
        // now - meaning UETR was NEVER populated regardless of whether the
        // source MT103 carried one.
        // Field 119 (validation flag) is the OTHER Block 3 tag a mapping
        // doc now needs: per official Swift rule, "To trigger the MT 202
        // COV format validation, the user header of the message (block 3)
        // is mandatory and must contain the code COV in the validation
        // flag field 119." Exposed as a synthetic "COV_FLAG" field (not
        // literally "119") ONLY when its value is exactly "COV" - this is
        // a presence/absence GATE a mapping doc uses to decide whether a
        // whole optional sub-structure (e.g. pacs.009's UndrlygCstmrCdtTrf)
        // applies at all, not a value to copy anywhere.
        boolean covFlag = false;
        SwiftTagListBlock block3 = message.getBlock3();
        if (block3 != null) {
            for (Tag tag : block3.getTags()) {
                String value = tag.getValue() == null ? "" : tag.getValue().trim();
                if ("121".equals(tag.getName())) {
                    parsed.addField("121", value);
                } else if ("119".equals(tag.getName()) && "COV".equals(value)) {
                    covFlag = true;
                }
            }
        }
        if (covFlag) {
            parsed.addField("COV_FLAG", "COV");
        }

        // Block 1 (basic header), not block 4 - exposed as a synthetic
        // field so mapping docs can reference the MT header's Sender BIC
        // for target elements that fall back to it (e.g. pacs.008
        // DbtrAgt when MT103 field 52a is absent). Same "not read at all
        // until a mapping doc needed it" gap already flagged for Block 3/
        // field 121 (UETR) elsewhere in this codebase - UNVERIFIED
        // against the actual Prowide version pinned in pom.xml; confirm
        // getSender()'s behavior (and the input/output header direction
        // it assumes) once this compiles.
        String senderBic = message.getSender();
        if (senderBic == null || senderBic.isBlank()) {
            if (message.getBlock1() != null && message.getBlock1().getLogicalTerminal() != null) {
                senderBic = message.getBlock1().getLogicalTerminal();
            }
        }
        senderBic = normalizeToBicCore(senderBic);
        if (senderBic != null) {
            parsed.addField("__MT_SENDER_BIC__", senderBic);
        }

        // Same idea, Block 2 (application header) Receiver - added for
        // pacs.008 CdtrAgt's fallback when 57a is absent (market-practice
        // rule, per user instruction 2026-08-31: CBPR+/TARGET2 MT->ISO
        // guidance maps the Receiver to CreditorAgent in that case).
        // getReceiver()'s direction handling is UNVERIFIED beyond the
        // "Input" block2 case the manual fallback covers - same caveat as
        // getSender() above; Output-direction messages are not specially
        // handled here.
        String receiverBic = message.getReceiver();
        if (receiverBic == null || receiverBic.isBlank()) {
            if (message.getBlock2() instanceof SwiftBlock2Input block2Input
                    && block2Input.getReceiverAddress() != null) {
                receiverBic = block2Input.getReceiverAddress();
            }
        }
        receiverBic = normalizeToBicCore(receiverBic);
        if (receiverBic != null) {
            parsed.addField("__MT_RECEIVER_BIC__", receiverBic);
        }

        if (parsed.getFields().isEmpty()) {
            throw new ParsingException(
                    "Prowide parsed the message but no fields were found in block 4 for " + declaredFormat + ".");
        }

        return parsed;
    }

    /**
     * A valid BICFI (BICFIDec2014Identifier) is exactly 8 or 11 characters
     * (4-letter/digit institution + 2-letter country + 2-alphanumeric
     * location [+ 3-alphanumeric branch]). A raw SWIFT logical-terminal
     * (LT) address is 12 characters: that same 8-char BIC, followed by a
     * 1-character LT/terminal identifier (position 9), followed by the
     * 3-character branch code (positions 10-12) - standard SWIFT FIN
     * header address format. getSender()/getReceiver() were assumed to
     * already return a clean BIC, but empirically returned the raw 12-char
     * address at least once - normalizing here regardless of which path
     * produced the value (Prowide convenience method or the manual Block
     * 1/2 fallback) rather than trusting either source's length.
     *
     * <p>v2.3 BUG FIX: this previously truncated a 12-char address to just
     * the first 8 characters (bare BIC8), which silently DISCARDED the
     * branch code entirely - "APACGB61AXXX" (BIC8=APACGB61, LT-id=A,
     * branch=XXX) became "APACGB61" instead of the correct BIC11
     * "APACGB61XXX". Fixed to drop ONLY the single LT-identifier character
     * at position 9 (index 8), keeping BIC8 + branch.
     */
    private String normalizeToBicCore(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() == 8 || trimmed.length() == 11) {
            return trimmed;
        }
        if (trimmed.length() == 12) {
            return trimmed.substring(0, 8) + trimmed.substring(9);
        }
        return trimmed; // some other length - genuinely malformed; let XSD validation reject it rather than guess
    }
}
