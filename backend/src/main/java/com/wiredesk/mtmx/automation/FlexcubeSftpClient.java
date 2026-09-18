package com.wiredesk.mtmx.automation;

import com.wiredesk.mtmx.config.FlexcubeProperties;
import com.wiredesk.mtmx.exception.FlexcubeIntegrationException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive;
import net.schmizz.sshj.userauth.method.AuthMethod;
import net.schmizz.sshj.userauth.method.AuthPassword;
import net.schmizz.sshj.userauth.method.AuthPublickey;
import net.schmizz.sshj.userauth.method.PasswordResponseProvider;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.PasswordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * FlexcubeFileStore implementation for flexcube.access-mode=sftp (the default, and the original
 * behavior before the "local" mode existed - see FlexcubeProperties' own Javadoc). Opens one
 * fresh SSH connection per poll cycle (FlexcubeFileWatcherJob calls {@link #withSession}), rather
 * than holding a long-lived connection across the 5-minute gap between cycles, which would risk
 * going stale or timing out unnoticed.
 *
 * <p>Host key verification: if {@code flexcube.known-hosts-path} is set, the host key is checked
 * against that file (same format as ~/.ssh/known_hosts). If NOT set, host key verification is
 * skipped entirely (PromiscuousVerifier) - insecure, logged as a warning on every connection so
 * it is never silently relied upon in production.
 */
@Component
@ConditionalOnProperty(prefix = "flexcube", name = "access-mode", havingValue = "sftp", matchIfMissing = true)
public class FlexcubeSftpClient implements FlexcubeFileStore {

    private static final Logger log = LoggerFactory.getLogger(FlexcubeSftpClient.class);

    private final FlexcubeProperties props;

    public FlexcubeSftpClient(FlexcubeProperties props) {
        this.props = props;
    }

    @Override
    public <T> T withSession(FlexcubeFileWork<T> work) {
        SSHClient ssh = connectAndAuthenticate();
        try {
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                return work.run(new SftpSession(sftp));
            }
        } catch (IOException e) {
            throw new FlexcubeIntegrationException(
                    "OBPS/FLEXCUBE SFTP operation failed against " + props.getHost() + ":" + props.getPort()
                            + " - " + e.getMessage(), e);
        } finally {
            closeQuietly(ssh);
        }
    }

    /** One factor within an attempt. KEYBOARD_INTERACTIVE and PASSWORD both use the configured password, just via a different SSH auth method name. */
    private enum Factor { PUBLICKEY, PASSWORD, KEYBOARD_INTERACTIVE }

    private record AuthAttempt(List<Factor> factors, String description) {
    }

    /**
     * The OBPS SFTP account was believed to require BOTH publickey and a password-like second
     * factor together (2026-09-16, from a manual WinSCP login that prompted for both) - but after
     * all 4 two-factor combinations failed with NEITHER ever reaching partial success (including
     * two that tried publickey FIRST, which should have succeeded outright on its own if the
     * server's policy were "any single listed method suffices" and the key itself were valid), and
     * the server's own reported allowed-methods list came back as
     * "[publickey, gssapi-keyex, gssapi-with-mic, password]" (2026-09-18) - which reads like the
     * default OpenSSH shape of four INDEPENDENT alternatives, not a forced multi-factor chain -
     * this now also tries publickey ALONE and password ALONE, in case the real server policy never
     * required both together and the earlier WinSCP prompt for a password was actually just its own
     * local dialog for the key's passphrase, not a second server-side factor. Six attempts total,
     * most conventional first:
     * <ol>
     *   <li>publickey alone
     *   <li>password alone
     *   <li>publickey, then plain SSH "password"
     *   <li>publickey, then "keyboard-interactive" (some OpenSSH servers have PasswordAuthentication
     *       disabled and only accept password-style prompts via keyboard-interactive instead)
     *   <li>plain "password", then publickey
     *   <li>"keyboard-interactive", then publickey
     * </ol>
     * A fresh SSHClient is required for each attempt - auth state cannot be cleanly reset on a
     * connection that already received a definitive failure. If ALL SIX still fail, set
     * FLEXCUBE_SFTP_DEBUG_LOGGING=DEBUG in backend/.env for the raw SSH protocol exchange (shows
     * whether the key itself is even being recognized/tried by the server at all, via
     * SSH_MSG_USERAUTH_PK_OK vs an outright rejection) - the most likely remaining causes at that
     * point are a key/account mismatch (this key's public half not actually in the account's
     * authorized_keys) or a wrong username/password, not method negotiation.
     */
    private static final List<AuthAttempt> AUTH_ATTEMPTS = List.of(
            new AuthAttempt(List.of(Factor.PUBLICKEY), "publickey alone"),
            new AuthAttempt(List.of(Factor.PASSWORD), "password alone"),
            new AuthAttempt(List.of(Factor.PUBLICKEY, Factor.PASSWORD), "publickey then password"),
            new AuthAttempt(List.of(Factor.PUBLICKEY, Factor.KEYBOARD_INTERACTIVE), "publickey then keyboard-interactive"),
            new AuthAttempt(List.of(Factor.PASSWORD, Factor.PUBLICKEY), "password then publickey"),
            new AuthAttempt(List.of(Factor.KEYBOARD_INTERACTIVE, Factor.PUBLICKEY), "keyboard-interactive then publickey"));

    private SSHClient connectAndAuthenticate() {
        checkPrivateKeyFileExists();
        UserAuthException lastAuthFailure = null;
        // The server's OWN advertised allowed-methods list from its last USERAUTH_FAILURE
        // response (2026-09-18, added after all 4 guessed combinations failed) - this is ground
        // truth from the server itself, not another guess, and is surfaced in the final error
        // below so the real answer is visible directly instead of requiring a fifth blind attempt.
        Collection<String> lastServerAllowedMethods = null;
        for (AuthAttempt attempt : AUTH_ATTEMPTS) {
            SSHClient ssh = new SSHClient();
            try {
                configureHostKeyVerification(ssh);
                ssh.connect(props.getHost(), props.getPort());
                List<AuthMethod> order = new ArrayList<>();
                for (Factor factor : attempt.factors()) {
                    order.add(buildAuthMethod(ssh, factor));
                }
                ssh.auth(props.getUsername(), order);
                log.info("SFTP authenticated successfully using: {}", attempt.description());
                return ssh;
            } catch (UserAuthException e) {
                lastAuthFailure = e;
                lastServerAllowedMethods = ssh.getUserAuth().getAllowedMethods();
                log.warn("SFTP auth failed trying '{}': {} (server says it allows: {})", attempt.description(),
                        e.getMessage(), lastServerAllowedMethods);
                closeQuietly(ssh);
            } catch (IOException e) {
                closeQuietly(ssh);
                throw new FlexcubeIntegrationException("Failed to connect to OBPS/FLEXCUBE SFTP host "
                        + props.getHost() + ":" + props.getPort() + " - " + e.getMessage(), e);
            }
        }
        throw new FlexcubeIntegrationException("SFTP authentication failed trying all of: "
                + AUTH_ATTEMPTS.stream().map(AuthAttempt::description).reduce((a, b) -> a + "; " + b).orElse("")
                + " against " + props.getHost() + ":" + props.getPort() + " - "
                + (lastAuthFailure != null ? lastAuthFailure.getMessage() : "unknown reason")
                + ". The server itself last reported it allows: "
                + (lastServerAllowedMethods == null || lastServerAllowedMethods.isEmpty()
                        ? "(none reported)" : lastServerAllowedMethods)
                + " - if that list looks different from what was tried above, this document's own guessed "
                + "combinations don't match reality; ask whoever administers the OBPS SFTP account to confirm "
                + "sshd_config's AuthenticationMethods against that exact reported list instead of guessing further.",
                lastAuthFailure);
    }

    private AuthMethod buildAuthMethod(SSHClient ssh, Factor factor) throws IOException {
        return switch (factor) {
            case PUBLICKEY -> {
                KeyProvider keys = ssh.loadKeys(props.getPrivateKeyPath(),
                        props.getPassphrase() == null || props.getPassphrase().isBlank()
                                ? null : props.getPassphrase().toCharArray());
                // 2026-09-19: logged once per attempt (cheap, only runs on auth failure/retry) so
                // a stuck "publickey alone" failure can be cross-checked against known SSH server
                // gotchas that are specific to the key TYPE - e.g. many OpenSSH 8.8+ servers reject
                // the legacy "ssh-rsa"/SHA-1 signature scheme for RSA keys (sshj's own default
                // config already prefers rsa-sha2-256/512 over it - see DefaultConfig - but a real,
                // reported sshj issue (#740) covers cases where that negotiation can still pick the
                // wrong algorithm) - this only matters at all if the key is actually RSA, which this
                // line settles rather than assumes.
                log.info("Loaded private key type: {}", keys.getType());
                yield new AuthPublickey(keys);
            }
            case PASSWORD -> new AuthPassword(PasswordUtils.createOneOff(props.getPassword().toCharArray()));
            // Answers any keyboard-interactive prompt with the configured password - see
            // AUTH_ATTEMPTS' own Javadoc for why this exists alongside plain AuthPassword.
            case KEYBOARD_INTERACTIVE -> {
                PasswordFinder finder = PasswordUtils.createOneOff(props.getPassword().toCharArray());
                yield new AuthKeyboardInteractive(new PasswordResponseProvider(finder) {
                    @Override
                    public boolean shouldRetry() {
                        // A single fixed password answered once - retrying would just resend the
                        // same answer and loop, not help.
                        return false;
                    }
                });
            }
        };
    }

    /** Matches a Windows drive letter followed immediately by anything other than "/" or "\" - see checkPrivateKeyFileExists()'s own Javadoc. */
    private static final Pattern SUSPICIOUS_DRIVE_RELATIVE_PATH = Pattern.compile("^[A-Za-z]:[^\\\\/].*");

    /**
     * Checked ONCE up front, before any connection attempt (2026-09-18, real production case:
     * a Windows path pasted into FLEXCUBE_SFTP_PRIVATE_KEY_PATH with single backslashes silently
     * lost them, e.g. "F:\OCI-preSales-...ppk" became "F:OCI-preSales-...ppk"). Root cause,
     * confirmed directly against java.util.Properties (the same escaping rules Spring's
     * "optional:file:.env[.properties]" config import uses to parse backend/.env): backslash is
     * an escape character in properties-file format, so a single "\" before a character with no
     * recognized escape meaning is silently dropped, not preserved literally.
     *
     * <p>Deliberately does NOT rely on {@code new File(path).exists()} alone - confirmed directly
     * (2026-09-18) that a path in exactly this corrupted shape ("F:something", a drive letter with
     * no separator right after it) can return true from exists() even when it is not the real key
     * file: Windows treats "F:something" as relative to THAT DRIVE'S OWN CURRENT DIRECTORY, not
     * the drive's root, so it can coincidentally resolve to an unrelated file rather than reliably
     * failing. The structural shape itself (drive letter + non-separator) is therefore checked
     * directly and treated as invalid regardless of what exists() says, since a real path should
     * never legitimately look like this after either forward-slash or doubled-backslash config.
     */
    private void checkPrivateKeyFileExists() {
        String path = props.getPrivateKeyPath();
        if (path == null) {
            return;
        }
        boolean suspiciousShape = SUSPICIOUS_DRIVE_RELATIVE_PATH.matcher(path).matches();
        if (suspiciousShape || !new File(path).exists()) {
            throw new FlexcubeIntegrationException(
                    "Private key file path '" + path + "' (FLEXCUBE_SFTP_PRIVATE_KEY_PATH) "
                            + (suspiciousShape
                                    ? "has a drive letter with no separator right after it, which Windows treats as "
                                            + "relative to that drive's own current directory (not its root) - "
                                            + "unreliable even if it happens to resolve to something. "
                                    : "does not exist. ")
                            + "This exact shape happens when a Windows path with backslashes is pasted into "
                            + "backend/.env: that file is parsed as a Java properties file, where a single "
                            + "backslash is an escape character and gets silently dropped (e.g. "
                            + "'F:\\folder\\key.ppk' becomes 'F:folderkey.ppk'). Use forward slashes instead "
                            + "(F:/folder/key.ppk) or double every backslash (F:\\\\folder\\\\key.ppk).");
        }
    }

    private void configureHostKeyVerification(SSHClient ssh) throws IOException {
        if (props.getKnownHostsPath() != null && !props.getKnownHostsPath().isBlank()) {
            ssh.loadKnownHosts(new File(props.getKnownHostsPath()));
        } else {
            log.warn("flexcube.known-hosts-path is not set - skipping SFTP host key verification "
                    + "(insecure). Set FLEXCUBE_SFTP_KNOWN_HOSTS_PATH once you have the real OBPS host key "
                    + "fingerprint.");
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
        }
    }

    private static void closeQuietly(SSHClient ssh) {
        try {
            ssh.disconnect();
        } catch (IOException ignored) {
            // best-effort cleanup only
        }
    }

    /** SFTP always joins paths with "/" per the protocol spec, regardless of the remote OS - never the local OS separator. */
    private class SftpSession implements FlexcubeFileSession {
        private final SFTPClient sftp;

        SftpSession(SFTPClient sftp) {
            this.sftp = sftp;
        }

        @Override
        public String resolvePath(String subfolder, String fileName) {
            return join(join(props.getBasePath(), subfolder), fileName);
        }

        @Override
        public void ensureDirectory(String subfolder) throws IOException {
            String path = join(props.getBasePath(), subfolder);
            try {
                if (sftp.statExistence(path) == null) {
                    sftp.mkdir(path);
                    log.info("Created remote directory {}", path);
                }
            } catch (IOException e) {
                // 2026-09-19: previously this bubbled up as a bare "No such file" with no
                // indication of WHICH path - real production case: this is the FIRST SFTP write
                // FlexcubeFileWatcherJob performs each cycle, so an FLEXCUBE_BASE_PATH typo/mismatch
                // surfaces here first and needs to be immediately diagnosable, not guessed at.
                throw new IOException("Could not create/verify directory '" + path + "' (built from "
                        + "FLEXCUBE_BASE_PATH + this subfolder name) - if this path doesn't exist on the "
                        + "server, or FLEXCUBE_BASE_PATH's PARENT directory doesn't exist, SFTP mkdir fails "
                        + "with exactly this error: " + e.getMessage(), e);
            }
        }

        @Override
        public List<FlexcubeFileEntry> listFiles(String subfolder) throws IOException {
            String path = join(props.getBasePath(), subfolder);
            List<FlexcubeFileEntry> entries = new ArrayList<>();
            try {
                for (RemoteResourceInfo info : sftp.ls(path)) {
                    if (info.isRegularFile()) {
                        FileAttributes attrs = info.getAttributes();
                        entries.add(new FlexcubeFileEntry(info.getName(), info.getPath(),
                                Instant.ofEpochSecond(attrs.getMtime())));
                    }
                }
            } catch (IOException e) {
                throw new IOException("Could not list directory '" + path + "' (built from FLEXCUBE_BASE_PATH + "
                        + "FLEXCUBE_MT_OUT_FOLDER) - " + e.getMessage(), e);
            }
            return entries;
        }

        @Override
        public String readFile(String path) throws IOException {
            try (RemoteFile rf = sftp.open(path, EnumSet.of(OpenMode.READ))) {
                try (InputStream in = rf.new RemoteFileInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IOException("Could not read file '" + path + "' - " + e.getMessage(), e);
            }
        }

        @Override
        public void writeFile(String path, String content) throws IOException {
            try (RemoteFile rf = sftp.open(path, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))) {
                try (OutputStream out = rf.new RemoteFileOutputStream()) {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new IOException("Could not write file '" + path + "' - " + e.getMessage(), e);
            }
        }

        @Override
        public void moveFile(String fromPath, String toPath) throws IOException {
            try {
                sftp.rename(fromPath, toPath);
            } catch (IOException e) {
                throw new IOException("Could not move '" + fromPath + "' to '" + toPath + "' - " + e.getMessage(), e);
            }
        }

        private String join(String base, String child) {
            return base.endsWith("/") ? base + child : base + "/" + child;
        }
    }
}
