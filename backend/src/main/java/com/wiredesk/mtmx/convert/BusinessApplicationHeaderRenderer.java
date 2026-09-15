package com.wiredesk.mtmx.convert;

import com.wiredesk.mtmx.exception.TransformationException;
import com.wiredesk.mtmx.mapping.model.BusinessApplicationHeaderConfig;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

/**
 * Renders an ISO 20022 Business Application Header (head.001.001.02, "AppHdr")
 * from the already-converted tree, per a mapping doc's opt-in
 * BusinessApplicationHeaderConfig section - see that class's Javadoc for why
 * this is a small, fixed-shape structure built directly here rather than
 * reusing MxRenderer's generic dotted-path renderer.
 *
 * <p>Structure matches BusinessApplicationHeaderV02 in schema order: Fr, To
 * (both Party44Choice/FIId - this engine only ever populates the
 * financial-institution branch, never OrgId, since every value it draws on
 * is a BICFI already produced for the Document itself), BizMsgIdr,
 * MsgDefIdr, an optional BizSvc, then CreDt. Fr/To/BizMsgIdr/MsgDefIdr/CreDt
 * are mandatory per the schema - a config path that resolves to nothing in
 * the converted tree is refused with a clear error rather than silently
 * producing an incomplete AppHdr.
 *
 * <p>v2.35 CORRECTION (2026-09-11): reverted the v2.34 namespace change - independently
 * verified via web search (not just a single pasted example this time) that head.001.001.02 is
 * CBPR+'s actual current version; head.001.001.01 is described as superseded/legacy in every
 * source found. Two "source of truth" examples the user supplied disagreed with each other
 * (.01 vs .02) - rather than flip a third time on a single new example, this was checked against
 * independent sources before changing it back. Also added renderEnvelope() below: the same user
 * feedback included a MORE COMPLETE reference example than the one used for the v2.32/v2.34 work,
 * showing Document nested INSIDE the same Envelope as AppHdr (not two separate documents) -
 * confirming the earlier "keep them separate, combining is network-specific" caution was overly
 * conservative for this specific network. render() below still produces a standalone AppHdr
 * fragment (kept, still independently useful), but the real combined transport output now comes
 * from renderEnvelope(), which composes the ALREADY-RENDERED AppHdr and Document strings into one
 * genuine, single-root XML document via DOM import - not string concatenation (the previous
 * approach produced two "&lt;?xml?&gt;"-declared documents in one string, which isn't valid
 * single-document XML). Document is still validated against the real pacs.008 XSD as its own
 * standalone root BEFORE this combining step runs (see ConversionOrchestrator) - unaffected.
 */
@Component
public class BusinessApplicationHeaderRenderer {

    private static final String NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:head.001.001.02";
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

            Element root = doc.createElementNS(NAMESPACE, "AppHdr");
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", NAMESPACE);
            doc.appendChild(root);

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

    /**
     * Combines an already-rendered AppHdr XML string and an already-rendered Document XML string
     * into ONE real {@code <Envelope>} document - real DOM import, not text concatenation, so the
     * result is genuinely well-formed single-root XML (exactly one "&lt;?xml?&gt;" declaration).
     * Order matters: AppHdr first, then Document, matching the confirmed real CBPR+ convention.
     */
    public String renderEnvelope(String appHdrXml, String documentXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document appHdrDoc = builder.parse(new InputSource(new StringReader(appHdrXml)));
            Document documentDoc = builder.parse(new InputSource(new StringReader(documentXml)));
            // Both fragments were already pretty-printed on their own (indentation = real
            // whitespace-only text nodes, not just formatting) - stripped here before import, or
            // the final serialization's OWN indentation would stack on top of theirs, producing
            // the doubled/blank-line padding a naive import+reserialize would otherwise show.
            stripWhitespaceTextNodes(appHdrDoc.getDocumentElement());
            stripWhitespaceTextNodes(documentDoc.getDocumentElement());

            Document envelopeDoc = builder.newDocument();
            Element envelope = envelopeDoc.createElementNS(ENVELOPE_NAMESPACE, "Envelope");
            envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", ENVELOPE_NAMESPACE);
            envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", XSI_NAMESPACE);
            envelopeDoc.appendChild(envelope);

            envelope.appendChild(envelopeDoc.importNode(appHdrDoc.getDocumentElement(), true));
            envelope.appendChild(envelopeDoc.importNode(documentDoc.getDocumentElement(), true));

            return documentToString(envelopeDoc);
        } catch (Exception e) {
            throw new TransformationException("Failed to combine AppHdr and Document into an Envelope: " + e.getMessage());
        }
    }

    /** Recursively removes whitespace-only text nodes - see renderEnvelope()'s own comment for why. */
    private void stripWhitespaceTextNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                node.removeChild(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                stripWhitespaceTextNodes(child);
            }
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
