package com.wiredesk.mtmx.automation;

import com.wiredesk.mtmx.config.FlexcubeSftpProperties;
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
import net.schmizz.sshj.userauth.method.AuthMethod;
import net.schmizz.sshj.userauth.method.AuthPassword;
import net.schmizz.sshj.userauth.method.AuthPublickey;
import net.schmizz.sshj.userauth.password.PasswordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Thin wrapper around sshj's SSHClient/SFTPClient for the OBPS/FLEXCUBE integration - opens one
 * fresh connection per poll cycle (FlexcubeFileWatcherJob calls {@link #withConnection}), rather
 * than holding a long-lived connection across the 5-minute gap between cycles, which would risk
 * going stale or timing out unnoticed. Reconnecting every cycle is simple and appropriate at this
 * poll frequency.
 *
 * <p>Host key verification: if {@code flexcube.known-hosts-path} is set, the host key is checked
 * against that file (same format as ~/.ssh/known_hosts - populate it once via
 * {@code ssh-keyscan <host> >> known_hosts} or by connecting once with WinSCP/PuTTY and accepting
 * the fingerprint, then exporting it). If NOT set, host key verification is skipped entirely
 * (PromiscuousVerifier) - this is insecure (no protection against a machine-in-the-middle) and is
 * logged as a warning on every connection specifically so it is not silently relied upon in
 * production; set knownHostsPath once you have the real host key fingerprint.
 */
@Component
public class FlexcubeSftpClient {

    private static final Logger log = LoggerFactory.getLogger(FlexcubeSftpClient.class);

    private final FlexcubeSftpProperties props;

    public FlexcubeSftpClient(FlexcubeSftpProperties props) {
        this.props = props;
    }

    /** One remote file's name, path, and last-modified instant (SFTP mtime) - used for the min-file-age staleness guard. */
    public record RemoteFileEntry(String name, String path, Instant mtime) {
    }

    public interface SftpWork<T> {
        T run(SFTPClient sftp) throws IOException;
    }

    public <T> T withConnection(SftpWork<T> work) {
        SSHClient ssh = connectAndAuthenticate();
        try {
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                return work.run(sftp);
            }
        } catch (IOException e) {
            throw new FlexcubeIntegrationException(
                    "OBPS/FLEXCUBE SFTP operation failed against " + props.getHost() + ":" + props.getPort()
                            + " - " + e.getMessage(), e);
        } finally {
            try {
                ssh.disconnect();
            } catch (IOException ignored) {
                // best-effort cleanup only
            }
        }
    }

    /**
     * The OBPS SFTP account requires BOTH publickey and password (confirmed 2026-09-16 via a
     * manual WinSCP login that prompted for both) - sshj's ssh.auth(username, methods) DOES
     * correctly chain two methods via the SSH protocol's own "partial success" response (verified
     * directly against sshj's SSHClient/UserAuthImpl source), but only if given in the exact
     * order the server expects, which we don't know. Rather than guess and hard-fail if wrong,
     * try publickey-then-password first (the more common convention), and if the server rejects
     * that order, open a FRESH connection and retry password-then-publickey - auth state cannot
     * be cleanly reset on a connection that has already received a definitive failure, so a new
     * SSHClient is required for the second attempt, not just a second ssh.auth() call.
     */
    private SSHClient connectAndAuthenticate() {
        UserAuthException lastAuthFailure = null;
        for (boolean publickeyFirst : List.of(true, false)) {
            SSHClient ssh = new SSHClient();
            try {
                configureHostKeyVerification(ssh);
                ssh.connect(props.getHost(), props.getPort());
                KeyProvider keys = ssh.loadKeys(props.getPrivateKeyPath(),
                        props.getPassphrase() == null || props.getPassphrase().isBlank()
                                ? null : props.getPassphrase().toCharArray());
                AuthMethod pubkeyMethod = new AuthPublickey(keys);
                AuthMethod passwordMethod = new AuthPassword(PasswordUtils.createOneOff(props.getPassword().toCharArray()));
                List<AuthMethod> order = publickeyFirst
                        ? List.of(pubkeyMethod, passwordMethod)
                        : List.of(passwordMethod, pubkeyMethod);
                ssh.auth(props.getUsername(), order);
                return ssh;
            } catch (UserAuthException e) {
                lastAuthFailure = e;
                closeQuietly(ssh);
                log.warn("SFTP auth failed trying {}-first order{}: {}", publickeyFirst ? "publickey" : "password",
                        publickeyFirst ? " - retrying with password-first" : " - no more orders to try", e.getMessage());
            } catch (IOException e) {
                closeQuietly(ssh);
                throw new FlexcubeIntegrationException("Failed to connect to OBPS/FLEXCUBE SFTP host "
                        + props.getHost() + ":" + props.getPort() + " - " + e.getMessage(), e);
            }
        }
        throw new FlexcubeIntegrationException("SFTP authentication failed in both publickey-first and "
                + "password-first order against " + props.getHost() + ":" + props.getPort() + " - "
                + (lastAuthFailure != null ? lastAuthFailure.getMessage() : "unknown reason"), lastAuthFailure);
    }

    private void configureHostKeyVerification(SSHClient ssh) throws IOException {
        if (props.getKnownHostsPath() != null && !props.getKnownHostsPath().isBlank()) {
            ssh.loadKnownHosts(new File(props.getKnownHostsPath()));
        } else {
            log.warn("flexcube.known-hosts-path is not set - skipping SFTP host key verification "
                    + "(insecure). Set FLEXCUBE_KNOWN_HOSTS_PATH once you have the real OBPS host key "
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

    /** Creates the remote directory if it does not already exist. No-op (not an error) if it's already there. */
    public void ensureDirectory(SFTPClient sftp, String remotePath) throws IOException {
        if (sftp.statExistence(remotePath) == null) {
            sftp.mkdir(remotePath);
            log.info("Created remote directory {}", remotePath);
        }
    }

    public List<RemoteFileEntry> listFiles(SFTPClient sftp, String remoteDir) throws IOException {
        List<RemoteFileEntry> entries = new ArrayList<>();
        for (RemoteResourceInfo info : sftp.ls(remoteDir)) {
            if (info.isRegularFile()) {
                FileAttributes attrs = info.getAttributes();
                entries.add(new RemoteFileEntry(info.getName(), info.getPath(),
                        Instant.ofEpochSecond(attrs.getMtime())));
            }
        }
        return entries;
    }

    public String readFile(SFTPClient sftp, String remotePath) throws IOException {
        try (RemoteFile rf = sftp.open(remotePath, EnumSet.of(OpenMode.READ))) {
            try (InputStream in = rf.new RemoteFileInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    public void writeFile(SFTPClient sftp, String remotePath, String content) throws IOException {
        try (RemoteFile rf = sftp.open(remotePath,
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))) {
            try (OutputStream out = rf.new RemoteFileOutputStream()) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /** Moves (renames) a file within the same SFTP server - MT_out/MT_history/MX_out/MT_failed all live under the same remoteBasePath, so a plain rename suffices. */
    public void moveFile(SFTPClient sftp, String fromPath, String toPath) throws IOException {
        sftp.rename(fromPath, toPath);
    }
}
