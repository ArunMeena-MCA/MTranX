package com.wiredesk.mtmx.automation;

import com.wiredesk.mtmx.config.OracleProperties;
import com.wiredesk.mtmx.exception.OracleFetchException;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls raw MT103 messages from pmtb_msg_dly_msg_out for the automated dashboard, per the user's
 * own stated query: media in ('SWIFT','FINPLUS') and swift_msg_type='103', further restricted to
 * module='PX' and media='SWIFT' specifically for the MESSAGE column (both filters kept verbatim
 * as separately stated, even though media='SWIFT' alone already implies the narrower half of the
 * IN-list - not simplified, so the query stays traceable back to exactly what was asked for).
 *
 * <p>Plain JDBC (DriverManager), no connection pool - this reads at most a handful of rows per
 * dashboard "fetch" click, not a sustained high-throughput workload, so a pool would be
 * unjustified complexity for what this is today.
 *
 * <p>UNVERIFIED assumption, disclosed rather than silently relied on: the MESSAGE column is
 * assumed to contain a complete SWIFT message including its block 1/2 (and block 3, for the
 * UETR) envelope - the same shape every MtParserService.parse() call elsewhere in this engine
 * already expects. If your table only stores the block 4 body (starting directly at ":20:"),
 * parsing will fail with a clear error on each row rather than silently guessing a fabricated
 * envelope - if that happens, this needs a real fix informed by what the column actually
 * contains, not a guess.
 */
@Service
public class OracleMessageFetchService {

    private static final String QUERY = """
            SELECT REFERENCE_NO, MESSAGE
            FROM pmtb_msg_dly_msg_out
            WHERE media IN ('SWIFT','FINPLUS')
              AND swift_msg_type = '103'
              AND module = 'PX'
              AND media = 'SWIFT'
            """;

    private final OracleProperties props;

    public OracleMessageFetchService(OracleProperties props) {
        this.props = props;
    }

    public List<FetchedMtMessage> fetchFirst(int limit) {
        if (!props.isConfigured()) {
            throw new OracleFetchException(
                    "Oracle source is not configured - set ORACLE_DB_HOST, ORACLE_DB_SERVICE_NAME and "
                            + "ORACLE_DB_USERNAME (and ORACLE_DB_PASSWORD) in backend/.env before using the "
                            + "automated dashboard.");
        }

        List<FetchedMtMessage> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(props.jdbcUrl(), props.getUsername(), props.getPassword());
             PreparedStatement stmt = conn.prepareStatement(QUERY)) {
            stmt.setMaxRows(limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String referenceNo = rs.getString("REFERENCE_NO");
                    String message = rs.getString("MESSAGE");
                    results.add(new FetchedMtMessage(referenceNo, message));
                }
            }
        } catch (SQLException e) {
            // Deliberately does not include props.getPassword() or the raw jdbcUrl() in the
            // message - only host/service name, enough to diagnose a wrong endpoint without
            // echoing a credential into logs or the HTTP error response.
            throw new OracleFetchException("Failed to fetch messages from Oracle (" + props.getHost() + ":"
                    + props.getPort() + "/" + props.getServiceName() + "): " + e.getMessage(), e);
        }
        return results;
    }
}
