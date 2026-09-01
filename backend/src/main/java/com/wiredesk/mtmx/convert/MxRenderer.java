package com.wiredesk.mtmx.convert;

import com.wiredesk.mtmx.exception.TransformationException;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Renders a dotted-path tree into a nested XML structure, in the ORDER
 * the target XSD's element sequences require (via XsdOrderingIndex) when
 * mtmx.xsd-dir is configured and the matching XSD is found. Without an
 * XSD, falls back to a best-effort heuristic (GrpHdr-like top-level keys
 * first) that cannot guarantee schema-correct ordering for arbitrary
 * conversion pairs - ValidatorService already warns loudly when no XSD is
 * configured, and this is the same underlying gap. Always supply the
 * official XSD for production traffic.
 */
@Component
public class MxRenderer {

    private final XsdIndexRegistry xsdIndexRegistry;

    public MxRenderer(XsdIndexRegistry xsdIndexRegistry) {
        this.xsdIndexRegistry = xsdIndexRegistry;
    }

    public String render(String targetFormat, Map<String, String> tree) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            String namespace = "urn:iso:std:iso:20022:tech:xsd:" + targetFormat;
            Element root = doc.createElementNS(namespace, "Document");
            root.setPrefix(null);
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", namespace);
            doc.appendChild(root);

            Optional<XsdOrderingIndex> xsdIndex = xsdIndexRegistry.get(targetFormat);

            Map<String, Element> nodes = new HashMap<>();
            Element documentBody = root;
            String wrapperElement = xsdIndex.map(XsdOrderingIndex::getRootWrapperElement)
                    .orElse("pacs.008.001.08".equals(targetFormat) ? "FIToFICstmrCdtTrf" : null);
            if (wrapperElement != null) {
                documentBody = doc.createElementNS(namespace, wrapperElement);
                root.appendChild(documentBody);
            }
            nodes.put("", documentBody);

            Map<String, String> orderedTree = xsdIndex.map(idx -> idx.order(tree))
                    .orElseGet(() -> orderedTree(targetFormat, tree));
            for (Map.Entry<String, String> entry : orderedTree.entrySet()) {
                String[] parts = entry.getKey().split("\\.");
                String lastPart = parts[parts.length - 1];
                if (lastPart.startsWith("@")) {
                    // ISO 20022 amount fields (ActiveCurrencyAndAmount etc.)
                    // carry currency as an XML ATTRIBUTE on the amount
                    // element, not a child element - "Foo.Bar.@Ccy" sets
                    // attribute Ccy on the Foo.Bar element instead of
                    // creating a "@Ccy" child. Works regardless of whether
                    // the base "Foo.Bar" text entry is processed before or
                    // after this one - ensurePath() creates it either way.
                    Element parentEl = ensurePath(doc, namespace, nodes, parts, parts.length - 1);
                    parentEl.setAttribute(lastPart.substring(1), entry.getValue());
                } else {
                    Element el = ensurePath(doc, namespace, nodes, parts, parts.length);
                    el.setTextContent(entry.getValue());
                }
            }

            return documentToString(doc);
        } catch (Exception e) {
            throw new TransformationException("Failed to render MX output: " + e.getMessage());
        }
    }

    /**
     * Creates (or reuses) elements for parts[0..uptoExclusive), returning
     * the element at that depth. A path segment like "AdrLine#2" (from
     * decompose_party's lines_from: rule) is a REPEATED element: the "#N"
     * suffix keeps it unique in the nodes cache (so each repetition gets
     * its own DOM node instead of being treated as the same element) while
     * the actual XML tag name has the suffix stripped.
     */
    private Element ensurePath(Document doc, String namespace, Map<String, Element> nodes, String[] parts, int uptoExclusive) {
        String parentKey = "";
        StringBuilder keyBuilder = new StringBuilder();
        Element current = nodes.get("");
        for (int i = 0; i < uptoExclusive; i++) {
            if (i > 0) {
                keyBuilder.append(".");
            }
            keyBuilder.append(parts[i]);
            String key = keyBuilder.toString();
            Element existing = nodes.get(key);
            if (existing == null) {
                int hashIdx = parts[i].indexOf('#');
                String tagName = hashIdx < 0 ? parts[i] : parts[i].substring(0, hashIdx);
                existing = doc.createElementNS(namespace, tagName);
                nodes.get(parentKey).appendChild(existing);
                nodes.put(key, existing);
            }
            parentKey = key;
            current = existing;
        }
        return current;
    }

    private Map<String, String> orderedTree(String targetFormat, Map<String, String> tree) {
        if (!"pacs.008.001.08".equals(targetFormat)) {
            return tree;
        }

        Map<String, String> ordered = new LinkedHashMap<>();
        tree.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("GrpHdr."))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        tree.entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("GrpHdr."))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
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
