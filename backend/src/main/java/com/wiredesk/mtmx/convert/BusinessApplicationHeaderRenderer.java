package com.wiredesk.mtmx.convert;

import com.wiredesk.mtmx.exception.TransformationException;
import com.wiredesk.mtmx.mapping.model.BusinessApplicationHeaderConfig;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Map;

/**
 * Renders an ISO 20022 Business Application Header (head.001.001.01, "AppHdr")
 * from the already-converted tree, per a mapping doc's opt-in
 * BusinessApplicationHeaderConfig section - see that class's Javadoc for why
 * this is a small, fixed-shape structure built directly here rather than
 * reusing MxRenderer's generic dotted-path renderer, and for why the result
 * is kept as its OWN separate XML fragment rather than merged into the
 * Document XML.
 *
 * <p>Structure matches BusinessApplicationHeaderV01 in schema order: Fr, To
 * (both Party44Choice/FIId - this engine only ever populates the
 * financial-institution branch, never OrgId, since every value it draws on
 * is a BICFI already produced for the Document itself), BizMsgIdr,
 * MsgDefIdr, an optional BizSvc, then CreDt. Fr/To/BizMsgIdr/MsgDefIdr/CreDt
 * are mandatory per the schema - a config path that resolves to nothing in
 * the converted tree is refused with a clear error rather than silently
 * producing an incomplete AppHdr.
 *
 * <p>v2.34 CORRECTION (2026-09-10, from a real worked CBPR+ example the user
 * supplied - the same JPMorgan "Migration to ISO 20022" worked example
 * already cited elsewhere in this document's history, matched by its
 * identical BizSvc="swift.cbprplus.02" value): two things were wrong
 * against real production output. (1) The namespace was head.001.001.02;
 * the real example uses head.001.001.01 - corrected. (2) AppHdr was
 * rendered bare; real CBPR+ output wraps it in an outer
 * {@code <Envelope xmlns="urn:swift:xsd:envelope">} element (with an
 * xmlns:xsi declaration alongside it) - added. This Envelope wrapping is
 * SWIFT's own convention for this specific network (this whole feature is
 * already an opt-in, SWIFT/CBPR+-flavored mechanism at the mapping-doc
 * level - see BusinessApplicationHeaderConfig), so applying it
 * unconditionally here (rather than adding yet another config toggle for a
 * single data point) is the right scope. Deliberately NOT extended to wrap
 * the Document XML the same way: that field is still validated as its own
 * standalone root against the real pacs.008 XSD elsewhere in this engine,
 * and the user's correction was specifically about the AppHdr shape, not a
 * request to change that.
 */
@Component
public class BusinessApplicationHeaderRenderer {

    private static final String NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:head.001.001.01";
    private static final String ENVELOPE_NAMESPACE = "urn:swift:xsd:envelope";
    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";

    public String render(Map<String, String> tree, BusinessApplicationHeaderConfig cfg, String targetFormat) {
        String from = required(tree, cfg.getFromTargetPath(), "from_target_path");
        String to = required(tree, cfg.getToTargetPath(), "to_target_path");
        String bizMsgIdr = required(tree, cfg.getBizMsgIdrTargetPath(), "biz_msg_idr_target_path");
        String creDt = required(tree, cfg.getCreDtTargetPath(), "cre_dt_target_path");

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element envelope = doc.createElementNS(ENVELOPE_NAMESPACE, "Envelope");
            envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", ENVELOPE_NAMESPACE);
            envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", XSI_NAMESPACE);
            doc.appendChild(envelope);

            Element root = doc.createElementNS(NAMESPACE, "AppHdr");
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", NAMESPACE);
            envelope.appendChild(root);

            root.appendChild(financialInstitutionParty(doc, "Fr", from));
            root.appendChild(financialInstitutionParty(doc, "To", to));
            root.appendChild(textElement(doc, "BizMsgIdr", bizMsgIdr));
            root.appendChild(textElement(doc, "MsgDefIdr", targetFormat));
            if (cfg.getBizSvc() != null && !cfg.getBizSvc().isBlank()) {
                root.appendChild(textElement(doc, "BizSvc", cfg.getBizSvc()));
            }
            root.appendChild(textElement(doc, "CreDt", creDt));

            return documentToString(doc);
        } catch (Exception e) {
            throw new TransformationException("Failed to render Business Application Header: " + e.getMessage());
        }
    }

    private Element financialInstitutionParty(Document doc, String tagName, String bicfi) {
        Element party = doc.createElementNS(NAMESPACE, tagName);
        Element fiId = doc.createElementNS(NAMESPACE, "FIId");
        Element finInstnId = doc.createElementNS(NAMESPACE, "FinInstnId");
        finInstnId.appendChild(textElement(doc, "BICFI", bicfi));
        fiId.appendChild(finInstnId);
        party.appendChild(fiId);
        return party;
    }

    private Element textElement(Document doc, String tagName, String value) {
        Element el = doc.createElementNS(NAMESPACE, tagName);
        el.setTextContent(value);
        return el;
    }

    private String required(Map<String, String> tree, String targetPath, String paramName) {
        if (targetPath == null) {
            throw new TransformationException("business_application_header." + paramName
                    + " is not configured in the mapping doc - cannot render an AppHdr without it.");
        }
        String value = tree.get(targetPath);
        if (value == null || value.isBlank()) {
            throw new TransformationException("business_application_header." + paramName + " points to '"
                    + targetPath + "', but that path was not populated in the converted output for this message - "
                    + "refusing to produce an AppHdr with a missing mandatory element.");
        }
        return value;
    }

    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
