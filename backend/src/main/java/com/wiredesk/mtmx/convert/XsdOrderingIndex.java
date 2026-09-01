package com.wiredesk.mtmx.convert;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads an ISO 20022 XSD and indexes, for every named complexType:
 * - the element order its xs:sequence/xs:choice declares, so MxRenderer can
 *   emit output in the order the SCHEMA requires rather than the order a
 *   mapping doc's YAML happens to list entries in;
 * - which of those children are MANDATORY (minOccurs not "0"), and which
 *   attributes are required (xs:simpleContent/xs:extension/xs:attribute
 *   use="required"), so ValidatorService can proactively check for missing
 *   mandatory structure BEFORE relying solely on the reactive SAX-based XSD
 *   validation pass - giving clearer, field-path-specific error messages
 *   for the exact class of bug this engine kept hitting one at a time
 *   (GrpHdr envelope, Dbtr, DbtrAgt, Ccy attributes, ...).
 *
 * Generic across any ISO 20022 message: the message-root wrapper element
 * (e.g. FIToFICstmrCdtTrf for pacs.008) is discovered from the schema's
 * "Document" complexType rather than hardcoded per message type, so this
 * works for any &lt;SRC&gt;_TO_&lt;TGT&gt;.yaml pair whose target XSD is
 * supplied via mtmx.xsd-dir.
 *
 * <p>Deliberately narrow: xs:complexContent/xs:extension inheritance and
 * xs:any are not handled. ISO 20022 message schemas are consistently flat
 * named complexTypes with plain sequences/choices, so this covers the real
 * cases; an element this index has no ordering information for sorts after
 * every element it does know about, rather than failing the whole
 * conversion - degrading to "no worse than insertion order" instead of
 * blocking output. xs:choice groups are treated as ordinary mandatory
 * children for completeness purposes (any content at/under the choice
 * element's own name satisfies it) rather than enforcing "exactly one
 * branch" - sufficient for every case this document actually produces
 * (e.g. CashAccount38/Id, which this document always routes through the
 * Othr branch), not a full choice-cardinality validator.
 */
public class XsdOrderingIndex {

    private final Map<String, List<String>> childOrderByType = new LinkedHashMap<>();
    private final Map<String, String> childTypeByTypeAndChild = new LinkedHashMap<>();
    private final Map<String, Set<String>> mandatoryChildrenByType = new LinkedHashMap<>();
    private final Map<String, Set<String>> mandatoryAttributesByType = new LinkedHashMap<>();
    private String rootWrapperElement;
    private String rootWrapperType;

    public static XsdOrderingIndex parse(File xsdFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().parse(xsdFile);

        XsdOrderingIndex index = new XsdOrderingIndex();
        NodeList complexTypes = doc.getElementsByTagNameNS("*", "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element ct = (Element) complexTypes.item(i);
            String typeName = ct.getAttribute("name");
            if (typeName == null || typeName.isEmpty()) {
                continue; // anonymous inline type - not addressable by name, skip
            }

            Element sequence = directChild(ct, "sequence");
            Element choice = sequence == null ? directChild(ct, "choice") : null;
            Element group = sequence != null ? sequence : choice;
            if (group != null) {
                List<String> order = new ArrayList<>();
                Set<String> mandatory = new LinkedHashSet<>();
                for (Element el : directChildren(group, "element")) {
                    String name = el.getAttribute("name");
                    String type = el.getAttribute("type");
                    if (name.isEmpty()) {
                        continue;
                    }
                    order.add(name);
                    if (!type.isEmpty()) {
                        index.childTypeByTypeAndChild.put(typeName + "::" + name, stripPrefix(type));
                    }
                    // Only a SEQUENCE's minOccurs means "this specific child
                    // is individually required". A CHOICE means "at least
                    // ONE of these branches", not "all of these" - marking
                    // every choice branch mandatory would false-positive on
                    // every choice type this document uses (e.g.
                    // AccountIdentification4Choice: IBAN or Othr - this
                    // document always uses Othr, so IBAN would wrongly be
                    // reported missing). The parent-level check already
                    // covers "at least one branch populated" (any content
                    // under the choice ELEMENT's own path satisfies its
                    // sequence-level mandatoriness) - full choice-cardinality
                    // validation (exactly one branch, no more) is out of
                    // scope, same as this index's existing ordering caveat.
                    if (sequence != null) {
                        String minOccurs = el.hasAttribute("minOccurs") ? el.getAttribute("minOccurs") : "1";
                        if (!"0".equals(minOccurs)) {
                            mandatory.add(name);
                        }
                    }
                }
                index.childOrderByType.put(typeName, order);
                if (!mandatory.isEmpty()) {
                    index.mandatoryChildrenByType.put(typeName, mandatory);
                }
            }

            Element simpleContent = directChild(ct, "simpleContent");
            if (simpleContent != null) {
                Element extension = directChild(simpleContent, "extension");
                if (extension != null) {
                    Set<String> requiredAttrs = new LinkedHashSet<>();
                    for (Element attr : directChildren(extension, "attribute")) {
                        String name = attr.getAttribute("name");
                        if (!name.isEmpty() && "required".equals(attr.getAttribute("use"))) {
                            requiredAttrs.add(name);
                        }
                    }
                    if (!requiredAttrs.isEmpty()) {
                        index.mandatoryAttributesByType.put(typeName, requiredAttrs);
                    }
                }
            }
        }

        List<String> documentChildren = index.childOrderByType.get("Document");
        if (documentChildren != null && !documentChildren.isEmpty()) {
            index.rootWrapperElement = documentChildren.get(0);
            index.rootWrapperType = index.childTypeByTypeAndChild.get("Document::" + index.rootWrapperElement);
        }

        if (index.rootWrapperElement == null) {
            throw new IllegalStateException(
                    "Could not find a 'Document' complexType with at least one child element in " + xsdFile.getName()
                            + " - this XSD doesn't match the expected ISO 20022 shape.");
        }

        return index;
    }

    private static String stripPrefix(String qname) {
        int colon = qname.indexOf(':');
        return colon < 0 ? qname : qname.substring(colon + 1);
    }

    private static Element directChild(Element parent, String localName) {
        for (Element child : directChildren(parent, localName)) {
            return child;
        }
        return null;
    }

    private static List<Element> directChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && localName.equals(el.getLocalName())) {
                result.add(el);
            }
        }
        return result;
    }

    public String getRootWrapperElement() {
        return rootWrapperElement;
    }

    public String getRootWrapperType() {
        return rootWrapperType;
    }

    /**
     * Sorts tree entries (dotted paths relative to the root wrapper
     * element) into schema sequence order, stably - entries this index
     * has no ordering information for (unknown element name at that
     * depth, or nesting the schema walk couldn't follow) keep their
     * relative insertion order, placed after every element that IS
     * ordered at that same depth/parent.
     */
    public Map<String, String> order(Map<String, String> tree) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(tree.entrySet());
        List<int[]> keys = new ArrayList<>(entries.size());
        for (Map.Entry<String, String> e : entries) {
            keys.add(sortKey(e.getKey()));
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            indices.add(i);
        }
        indices.sort((a, b) -> {
            int cmp = compareKeys(keys.get(a), keys.get(b));
            return cmp != 0 ? cmp : Integer.compare(a, b); // stable tie-break
        });

        Map<String, String> ordered = new LinkedHashMap<>();
        for (int i : indices) {
            Map.Entry<String, String> e = entries.get(i);
            ordered.put(e.getKey(), e.getValue());
        }
        return ordered;
    }

    private int compareKeys(int[] a, int[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private int[] sortKey(String dottedPath) {
        String[] segments = dottedPath.split("\\.");
        int[] key = new int[segments.length];
        String currentType = rootWrapperType;
        for (int i = 0; i < segments.length; i++) {
            // "AdrLine#2" is a repeated element (see MxRenderer.ensurePath) -
            // strip the "#N" for schema lookup so every repetition sorts
            // by the shared element name's schema position, not as an
            // unknown segment.
            int hashIdx = segments[i].indexOf('#');
            String segName = hashIdx < 0 ? segments[i] : segments[i].substring(0, hashIdx);
            List<String> order = currentType == null ? null : childOrderByType.get(currentType);
            int idx = (order == null) ? -1 : order.indexOf(segName);
            key[i] = idx < 0 ? Integer.MAX_VALUE : idx;
            currentType = (currentType == null) ? null : childTypeByTypeAndChild.get(currentType + "::" + segName);
        }
        return key;
    }

    /**
     * Proactively walks the schema top-down from the root wrapper,
     * checking that every MANDATORY child/attribute the schema itself
     * declares is actually present in the tree - but ONLY descending into
     * a container once something is already being written under it, so an
     * entirely-optional branch of the schema that this mapping doc simply
     * never populates (e.g. PmtTpInf, SttlmTmReq) is correctly left alone
     * rather than being demanded. The root wrapper itself is always
     * checked, since a real conversion always populates it.
     *
     * <p>Returns human-readable messages (one per missing mandatory
     * child/attribute), not exceptions - the caller decides severity.
     * Bounded recursion depth as a guard against any unexpected cyclic
     * type reference; ISO 20022 message schemas are not recursive in
     * practice.
     */
    public List<String> findMissingMandatory(Map<String, String> tree) {
        List<String> missing = new ArrayList<>();
        checkContainer("", rootWrapperType, tree, missing, 0);
        return missing;
    }

    private void checkContainer(String pathPrefix, String type, Map<String, String> tree, List<String> missing, int depth) {
        if (type == null || depth > 20) {
            return;
        }
        Set<String> mandatoryChildren = mandatoryChildrenByType.get(type);
        if (mandatoryChildren != null) {
            for (String child : mandatoryChildren) {
                String childPath = pathPrefix.isEmpty() ? child : pathPrefix + "." + child;
                boolean present = tree.keySet().stream()
                        .anyMatch(k -> k.equals(childPath) || k.startsWith(childPath + ".") || k.startsWith(childPath + "#"));
                if (!present) {
                    missing.add(childPath + " (mandatory per schema type " + type + ", not populated by any field_mappings entry)");
                    continue;
                }
                String childType = childTypeByTypeAndChild.get(type + "::" + child);
                checkContainer(childPath, childType, tree, missing, depth + 1);
            }
        }
        Set<String> mandatoryAttrs = mandatoryAttributesByType.get(type);
        if (mandatoryAttrs != null) {
            for (String attr : mandatoryAttrs) {
                String attrPath = pathPrefix + ".@" + attr;
                if (!tree.containsKey(attrPath)) {
                    missing.add(attrPath + " (mandatory attribute per schema type " + type + ")");
                }
            }
        }
    }
}
