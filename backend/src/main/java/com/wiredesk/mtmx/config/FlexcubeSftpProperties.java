package com.wiredesk.mtmx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connection details for the OBPS/FLEXCUBE SFTP file-drop integration - see
 * automation/FlexcubeFileWatcherJob and automation/FlexcubeSftpClient. Bound from
 * application.yml's "flexcube.*" keys, which read the matching FLEXCUBE_* environment
 * variables - populate those in backend/.env (gitignored, never committed - see
 * backend/.env.example for the placeholder keys), the same pattern already used for
 * ORACLE_DB_*.
 *
 * <p>{@code remoteBasePath} is the ONE folder the user described (containing the existing
 * MT_out, plus MT_history/MX_out/MT_failed this integration creates) - the four subfolder
 * names themselves are NOT configurable, since the user specified them by exact name.
 *
 * <p>{@code privateKeyPath} accepts EITHER an OpenSSH-format key OR a raw PuTTY .ppk file
 * directly (verified 2026-09-16 against sshj's own source: SSHClient.loadKeys() auto-detects
 * the ".ppk"/"PuTTY-User-Key-File-" header and dispatches to its built-in PuTTYKeyFile parser,
 * including AES-256-CBC-encrypted keys) - no manual PuTTYgen conversion needed, matching
 * whatever file WinSCP already uses.
 *
 * <p>{@code password} is a SEPARATE secret from {@code passphrase}: passphrase unlocks/decrypts
 * the private key file locally (never sent to the server); password is sent to the server as a
 * second authentication factor, since the OBPS SFTP account was confirmed (2026-09-16, user
 * tested manually via WinSCP) to require BOTH publickey and password together, not either alone.
 * FlexcubeSftpClient does not know which order the server expects them in, so it tries
 * publickey-then-password first and falls back to password-then-publickey on auth failure - see
 * its own connectAndAuthenticate() for the reasoning (sshj's ssh.auth(username, methods) chains
 * methods via the SSH protocol's own "partial success" mechanism when given in the right order,
 * but does not know the order in advance).
 */
@Component
@ConfigurationProperties(prefix = "flexcube")
public class FlexcubeSftpProperties {

    private boolean enabled = false;
    private String host;
    private int port = 22;
    private String username;
    private String privateKeyPath;
    private String passphrase;
    private String password;
    /** Optional. If blank, host key verification is skipped (logged as a warning once at startup) - see FlexcubeSftpClient. */
    private String knownHostsPath;
    private String remoteBasePath;
    private long pollIntervalMs = 300_000L;
    /**
     * A file's SFTP mtime must be at least this old before it is picked up - guards against
     * reading a file FLEXCUBE is still in the middle of writing. 30s is a conservative default
     * for a 5-minute poll cycle; adjust via FLEXCUBE_MIN_FILE_AGE_MS if FLEXCUBE's own write
     * pattern is known to differ (e.g. if it writes via a temp-name-then-rename pattern, this
     * guard is less critical but still harmless).
     */
    private long minFileAgeMs = 30_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getKnownHostsPath() {
        return knownHostsPath;
    }

    public void setKnownHostsPath(String knownHostsPath) {
        this.knownHostsPath = knownHostsPath;
    }

    public String getRemoteBasePath() {
        return remoteBasePath;
    }

    public void setRemoteBasePath(String remoteBasePath) {
        this.remoteBasePath = remoteBasePath;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getMinFileAgeMs() {
        return minFileAgeMs;
    }

    public void setMinFileAgeMs(long minFileAgeMs) {
        this.minFileAgeMs = minFileAgeMs;
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank()
                && username != null && !username.isBlank()
                && privateKeyPath != null && !privateKeyPath.isBlank()
                && password != null && !password.isBlank()
                && remoteBasePath != null && !remoteBasePath.isBlank();
    }

    public String mtOutPath() {
        return join(remoteBasePath, "MT_out");
    }

    public String mtHistoryPath() {
        return join(remoteBasePath, "MT_history");
    }

    public String mxOutPath() {
        return join(remoteBasePath, "MX_out");
    }

    public String mtFailedPath() {
        return join(remoteBasePath, "MT_failed");
    }

    private static String join(String base, String child) {
        if (base.endsWith("/")) {
            return base + child;
        }
        return base + "/" + child;
    }
}
