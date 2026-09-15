package com.wiredesk.mtmx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connection details for the Oracle database the automated dashboard reads MT103 messages from
 * (table pmtb_msg_dly_msg_out - see automation/OracleMessageFetchService). Bound from
 * application.yml's "oracle.*" keys, which in turn read the matching ORACLE_DB_* environment
 * variables - populate those in backend/.env (gitignored, never committed - see
 * backend/.env.example for the placeholder keys), the same pattern already used for
 * GEMINI_API_KEY/GROQ_API_KEY.
 *
 * <p>The JDBC URL is built as {@code jdbc:oracle:thin:@//host:port/serviceName} - the modern
 * "service name" connection style. If your DBA gave you a SID instead of a service name, this
 * will need a different URL shape (the older {@code @host:port:SID} form) - flag that rather
 * than assuming, since the two are not interchangeable.
 */
@Component
@ConfigurationProperties(prefix = "oracle")
public class OracleProperties {

    private String host;
    private int port = 1521;
    private String serviceName;
    private String username;
    private String password;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank()
                && serviceName != null && !serviceName.isBlank()
                && username != null && !username.isBlank();
    }

    public String jdbcUrl() {
        return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + serviceName;
    }
}
