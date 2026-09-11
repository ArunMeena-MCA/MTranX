package com.wiredesk.mtmx.mapping.model;

/**
 * Opt-in instructions for producing an ISO 20022 Business Application Header
 * (head.001.001.02, "AppHdr") alongside the converted Document, for MT-&gt;MX
 * conversions only. Absent (the default for every mapping doc that doesn't
 * declare this section, e.g. MT202_TO_PACS00900108.yaml today) means no
 * AppHdr is produced at all - purely additive, zero behavior change for any
 * existing conversion that doesn't opt in.
 *
 * <p>Each *_target_path reads from the ALREADY-CONVERTED tree (not a raw
 * source field), the same convention FieldMapping.dateFromTargetPath and
 * currencyFromTargetPath already use - so this section can point at whatever
 * BICFI/MsgId/CreDtTm entries the mapping doc's own field_mappings already
 * populate, rather than re-deriving them a second way.
 *
 * <p><b>Why AppHdr is a SEPARATE field on the conversion result, not merged
 * into the Document XML:</b> ISO 20022 itself does not define a single
 * combining root element for AppHdr+Document - real-world integrations
 * confirm "AppHdr and Document are sibling elements, AppHdr first" as the
 * one settled convention, but the actual PARENT wrapper (SOAP envelope,
 * FileAct payload, a vendor's own envelope element, ...) is
 * implementation/network-specific, not a fixed standard. Inventing one here
 * and nesting Document inside it would also break the existing real-XSD
 * structural validation this engine already runs against the Document XML
 * as its own standalone root. Keeping AppHdr as an independently-valid,
 * separate XML fragment lets the caller combine it with Document however
 * their actual gateway/network expects, without this engine guessing at a
 * combining structure the standard doesn't define.
 */
public class BusinessApplicationHeaderConfig {
    private boolean enabled = false;

    /** Converted-tree path holding the sending institution's BIC (AppHdr/Fr/FIId/FinInstnId/BICFI). */
    private String fromTargetPath;

    /** Converted-tree path holding the receiving institution's BIC (AppHdr/To/FIId/FinInstnId/BICFI). */
    private String toTargetPath;

    /** Converted-tree path holding the business message identifier (AppHdr/BizMsgIdr). */
    private String bizMsgIdrTargetPath;

    /** Converted-tree path holding the creation date/time (AppHdr/CreDt) - typically the same GrpHdr/CreDtTm the Document itself uses, for consistency. */
    private String creDtTargetPath;

    /**
     * Optional (AppHdr/BizSvc) - a fixed, per-mapping-doc constant identifying the business
     * service/network flavor (e.g. "swift.cbprplus.02" for SWIFT CBPR+), NOT derived from the
     * message content, unlike the other four *_target_path fields. Null/absent omits the
     * element entirely - it's genuinely optional per the schema, unlike Fr/To/BizMsgIdr/
     * MsgDefIdr/CreDt.
     */
    private String bizSvc;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFromTargetPath() {
        return fromTargetPath;
    }

    public void setFromTargetPath(String fromTargetPath) {
        this.fromTargetPath = fromTargetPath;
    }

    public String getToTargetPath() {
        return toTargetPath;
    }

    public void setToTargetPath(String toTargetPath) {
        this.toTargetPath = toTargetPath;
    }

    public String getBizMsgIdrTargetPath() {
        return bizMsgIdrTargetPath;
    }

    public void setBizMsgIdrTargetPath(String bizMsgIdrTargetPath) {
        this.bizMsgIdrTargetPath = bizMsgIdrTargetPath;
    }

    public String getCreDtTargetPath() {
        return creDtTargetPath;
    }

    public void setCreDtTargetPath(String creDtTargetPath) {
        this.creDtTargetPath = creDtTargetPath;
    }

    public String getBizSvc() {
        return bizSvc;
    }

    public void setBizSvc(String bizSvc) {
        this.bizSvc = bizSvc;
    }
}
