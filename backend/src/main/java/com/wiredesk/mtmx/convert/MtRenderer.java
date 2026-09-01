package com.wiredesk.mtmx.convert;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Renders the target_path -> value tree back into SWIFT block-4 text.
 * For MT targets, target_path is expected to be the plain MT tag (e.g.
 * "32A"). This intentionally mirrors the earlier engine's simple
 * block-4-only renderer rather than building a full block 1/2 envelope
 * via Prowide's writer classes - a full envelope needs sender/receiver
 * BIC and session/sequence numbers the mapping doc doesn't currently
 * supply. Revisit with Prowide's SwiftWriter/SwiftBlock1/SwiftBlock2
 * builders once that information is available in the mapping doc or
 * service config.
 */
@Component
public class MtRenderer {
    public String render(Map<String, String> tree) {
        StringBuilder sb = new StringBuilder("{4:\n");
        for (Map.Entry<String, String> e : tree.entrySet()) {
            sb.append(":").append(e.getKey()).append(":").append(e.getValue()).append("\n");
        }
        sb.append("-}");
        return sb.toString();
    }
}
