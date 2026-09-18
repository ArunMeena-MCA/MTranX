package com.wiredesk.mtmx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Config for the OBPS/FLEXCUBE file-drop integration - see automation/FlexcubeFileWatcherJob,
 * automation/FlexcubeFileStore, and its two implementations (automation/FlexcubeSftpClient,
 * automation/FlexcubeLocalFileStore). Bound from application.yml's "flexcube.*" keys, which read
 * the matching FLEXCUBE_* environment variables - populate those in backend/.env (gitignored,
 * never committed - see backend/.env.example), the same pattern already used for ORACLE_DB_*.
 *
 * <p>RENAMED from FlexcubeSftpProperties (2026-09-17): this class originally assumed the backend
 * always reaches FLEXCUBE over SFTP from a separate machine. The user is now instead planning to
 * run this backend directly ON the app server that already holds the MT_out folder, making the
 * whole exchange a plain local filesystem path - {@code accessMode} selects between the two
 * ("sftp", the original behavior and still the default so nothing already deployed on the
 * previous branch silently changes; or "local", added for this new deployment). {@code basePath}
 * (renamed from remoteBasePath) is the one folder containing MT_out either way - only its
 * *meaning* changes with accessMode (a remote SFTP path, or a plain local path the backend
 * process can read/write directly). The SFTP-specific fields below (host/port/username/etc.) are
 * simply unused - not merely optional, genuinely ignored - when accessMode=local.
 *
 * <p>{@code privateKeyPath} (sftp mode only) accepts EITHER an OpenSSH-format key OR a raw PuTTY
 * .ppk file directly (verified 2026-09-16 against sshj's own source: SSHClient.loadKeys()
 * auto-detects the "PuTTY-User-Key-File-" header and dispatches to its built-in PuTTYKeyFile
 * parser, including AES-256-CBC-encrypted keys) - no manual PuTTYgen conversion needed.
 *
 * <p>{@code password} (sftp mode only) is a SEPARATE secret from {@code passphrase}: passphrase
 * unlocks/decrypts the private key file locally (never sent to the server); password is sent to
 * the server as a second authentication factor, since the OBPS SFTP account was confirmed
 * (2026-09-16, user tested manually via WinSCP) to require BOTH publickey and password together.
 * See FlexcubeSftpClient's own connectAndAuthenticate() for how the two are combined.
 *
 * <p>The four subfolder names (mtOutFolder/mtHistoryFolder/mxOutFolder/mtFailedFolder) were
 * ORIGINALLY hardcoded as "MT_out"/"MT_history"/"MX_out"/"MT_failed" (2026-09-16), based on the
 * user's own description of their intended structure. Made configurable (2026-09-17) after the
 * user's real path turned out to be "/u01/ems/swift/out/..." - the actual existing source folder
 * is named plain "out", not "MT_out" - proving the literal names should never have been assumed.
 * Defaults are kept as the original names so nothing changes for anyone already relying on them;
 * override via FLEXCUBE_MT_OUT_FOLDER etc. to match whatever the real deployment actually uses.
 *
 * <p>QUOTE STRIPPING (2026-09-18, real production case): {@code privateKeyPath}, {@code basePath},
 * and {@code knownHostsPath} strip a single MATCHING pair of leading/trailing quotes (' or ") if
 * present - confirmed directly against java.util.Properties that backend/.env's quotes are NOT
 * stripped by the parser the way shell syntax or many .env loaders would (a value like
 * {@code ="F:/some path.ppk"} is parsed keeping both literal quote characters, so Java then tries
 * to open a file whose name literally starts and ends with a quote mark, which of course doesn't
 * exist). Quoting a value here is a natural instinct once a path contains spaces (exactly what
 * happened) - this makes that instinct harmless instead of a silent failure two config mistakes in
 * a row. Deliberately NOT applied to password/passphrase/username/host: quoting isn't a natural
 * habit for those, and a real secret could legitimately start or end with a quote character -
 * stripping there risks silently corrupting it instead of fixing a mistake.
 */
@Component
@ConfigurationProperties(prefix = "flexcube")
public class FlexcubeProperties {

    private boolean enabled = false;
    /** "sftp" (default, original behavior) or "local" - see this class's own Javadoc. */
    private String accessMode = "sftp";
    private String host;
    private int port = 22;
    private String username;
    private String privateKeyPath;
    private String passphrase;
    private String password;
    /** Optional, sftp mode only. If blank, host key verification is skipped (logged as a warning once at startup) - see FlexcubeSftpClient. */
    private String knownHostsPath;
    private String basePath;
    private String mtOutFolder = "MT_out";
    private String mtHistoryFolder = "MT_history";
    private String mxOutFolder = "MX_out";
    private String mtFailedFolder = "MT_failed";
    private long pollIntervalMs = 300_000L;
    /**
     * A file's last-modified time must be at least this old before it is picked up - guards
     * against reading a file FLEXCUBE is still in the middle of writing. 30s is a conservative
     * default for a 5-minute poll cycle; adjust via FLEXCUBE_MIN_FILE_AGE_MS if FLEXCUBE's own
     * write pattern is known to differ (e.g. if it writes via a temp-name-then-rename pattern,
     * this guard is less critical but still harmless).
     */
    private long minFileAgeMs = 30_000L;
    /**
     * Pause inserted between two consecutive files that actually go through the converter
     * (2026-09-19, user-requested: "so that LLM does not get overloaded" - each conversion's
     * semantic audit is an LLM call, and a cycle can carry many files at once). Only applied
     * between real conversion attempts - never after a file skipped for being too fresh or for
     * having no supported mapping doc, since neither of those calls the LLM at all and delaying
     * them would slow the cycle down for no benefit. See FlexcubeFileWatcherJob's own runCycle().
     */
    private long conversionDelayMs = 5_000L;
    /**
     * Local folder (on THIS backend's own filesystem, never the remote FLEXCUBE/OBPS server -
     * this is this process's own bookkeeping, not part of the file exchange) holding
     * failure.json/successful.json - see FlexcubeFileTrackingService. Defaults next to
     * backend/sample/ (the automated dashboard's own local archive folder) for consistency.
     */
    private String stateDir = "./flexcube-state";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }

    public boolean isLocalMode() {
        return "local".equalsIgnoreCase(accessMode);
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
        this.privateKeyPath = stripSurroundingQuotes(privateKeyPath);
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
        this.knownHostsPath = stripSurroundingQuotes(knownHostsPath);
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = stripSurroundingQuotes(basePath);
    }

    /** See this class's own "QUOTE STRIPPING" Javadoc for why this exists and why it's scoped to path fields only. */
    private static String stripSurroundingQuotes(String value) {
        if (value != null && value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    public String getMtOutFolder() {
        return mtOutFolder;
    }

    public void setMtOutFolder(String mtOutFolder) {
        this.mtOutFolder = mtOutFolder;
    }

    public String getMtHistoryFolder() {
        return mtHistoryFolder;
    }

    public void setMtHistoryFolder(String mtHistoryFolder) {
        this.mtHistoryFolder = mtHistoryFolder;
    }

    public String getMxOutFolder() {
        return mxOutFolder;
    }

    public void setMxOutFolder(String mxOutFolder) {
        this.mxOutFolder = mxOutFolder;
    }

    public String getMtFailedFolder() {
        return mtFailedFolder;
    }

    public void setMtFailedFolder(String mtFailedFolder) {
        this.mtFailedFolder = mtFailedFolder;
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

    public long getConversionDelayMs() {
        return conversionDelayMs;
    }

    public void setConversionDelayMs(long conversionDelayMs) {
        this.conversionDelayMs = conversionDelayMs;
    }

    public String getStateDir() {
        return stateDir;
    }

    public void setStateDir(String stateDir) {
        this.stateDir = stateDir;
    }

    public boolean isConfigured() {
        if (basePath == null || basePath.isBlank()) {
            return false;
        }
        if (isLocalMode()) {
            return true;
        }
        return host != null && !host.isBlank()
                && username != null && !username.isBlank()
                && privateKeyPath != null && !privateKeyPath.isBlank()
                && password != null && !password.isBlank();
    }
}
