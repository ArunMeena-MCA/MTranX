package com.wiredesk.mtmx.parser;

import com.wiredesk.mtmx.exception.ParsingException;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Parses ISO 20022 MX XML messages into a flat local-name-path -> text map,
 * the same generic approach the earlier engine used with lxml in Python.
 *
 * <p><b>Why not Prowide's typed MX classes here:</b> Prowide's ISO 20022
 * library (pw-iso20022) generates one strongly-typed Java class per message
 * (e.g. a pacs.008 class), which is great when you know exactly which
 * message/version you're handling at compile time. This engine is meant to
 * support many conversion pairs driven entirely by the mapping-doc folder at
 * runtime, without a Java class per message type - so a generic,
 * namespace-agnostic DOM walk is the right fit for the parsing layer here.
 * If/when you want the stronger guarantee of unmarshalling into Prowide's
 * exact generated model for a specific message (which also gives you
 * automatic structural validation against the bundled XSD), that's a good
 * enhancement to wire in as an alternative implementation of this same
 * parsing step for that specific conversion pair - see DOCUMENTATION.md.
 */
@Service
public class MxParserService {

    public ParsedMessage parse(String rawText, String declaredFormat) {
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Basic XXE hardening - we don't need external entities/DTDs for ISO 20022 messages.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new ByteArrayInputStream(rawText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ParsingException("Input declared as " + declaredFormat + " is not well-formed XML: " + e.getMessage(), e);
        }

        ParsedMessage parsed = new ParsedMessage(declaredFormat, rawText);
        parsed.setXmlDocument(doc);

        Element root = doc.getDocumentElement();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                collectLeafText((Element) n, new ArrayDeque<>(), parsed);
            }
        }

        if (parsed.getFields().isEmpty()) {
            throw new ParsingException("No leaf text elements found in " + declaredFormat + " XML input.");
        }
        return parsed;
    }

    private void collectLeafText(Element el, Deque<String> pathStack, ParsedMessage parsed) {
        String name = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
        pathStack.push(name);

        NodeList children = el.getChildNodes();
        boolean hasElementChild = false;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChild = true;
                collectLeafText((Element) n, pathStack, parsed);
            } else if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(n.getTextContent());
            }
        }

        if (!hasElementChild && !text.toString().trim().isEmpty()) {
            List<String> parts = new ArrayList<>(pathStack);
            Collections.reverse(parts);
            parsed.addField(String.join(".", parts), text.toString().trim());
        }

        pathStack.pop();
    }
}
