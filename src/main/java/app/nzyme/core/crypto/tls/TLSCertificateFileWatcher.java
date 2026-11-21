package app.nzyme.core.crypto.tls;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.NodeFilePermissions;
import app.nzyme.plugin.distributed.messaging.Message;
import app.nzyme.plugin.distributed.messaging.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/*
 * Picks up a TLS certificate that was replaced on disk (tls.cert / tls.key in the crypto directory) while the node
 * is running, so automated renewal (ACME clients, `tailscale cert`, cert-manager, ...) only has to write the two
 * files. Without this, a renewed certificate on disk is only loaded at the next restart of nzyme.
 *
 * When the certificate on disk differs from the one the HTTP server is using, and its key matches it, this sends
 * the same CHECK_RESTART_HTTP_SERVER message that a certificate upload in the web interface sends. That handler
 * re-runs the TLS bootstrap and restarts the HTTP server only if the certificate actually changed.
 *
 * A pair that can't be parsed, or whose key doesn't match the certificate (for example while the two files are
 * being written one after the other), is skipped and the certificate in use is kept. The next run tries again.
 *
 * The same file permission check that runs at startup (Crypto#verifyFilePermissions) runs before a reload. If it
 * finds problems, the new certificate is not loaded: the node would refuse to start with those files at its next
 * restart, so they are refused now. The next run after the permissions are fixed loads the certificate.
 */
public class TLSCertificateFileWatcher extends Periodical {

    private static final Logger LOG = LogManager.getLogger(TLSCertificateFileWatcher.class);

    // Don't re-request a restart for the same certificate more often than this if the restart didn't take effect.
    private static final int RETRY_MINUTES = 10;

    private final NzymeNode nzyme;

    private String lastProblem = null;
    private String lastRequestedSignature = null;
    private DateTime lastRequestedAt = null;

    public TLSCertificateFileWatcher(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    protected void execute() {
        if (nzyme.getHttpServer() == null || nzyme.getHttpServer().getCertificateInUse() == null) {
            return;
        }

        Optional<TLSKeyAndCertificate> disk;
        try {
            disk = nzyme.getCrypto().loadTLSCertificateFromDisk();
        } catch (Exception e) {
            problem("Could not read TLS certificate or key from disk. Keeping the certificate in use.", e);
            return;
        }

        if (disk.isEmpty()) {
            // No certificate files on disk. Certificate comes from the database or a wildcard certificate.
            return;
        }

        TLSKeyAndCertificate onDisk = disk.get();
        TLSKeyAndCertificate inUse = nzyme.getHttpServer().getCertificateInUse();

        if (onDisk.signature().equals(inUse.signature())) {
            lastProblem = null;
            lastRequestedSignature = null;
            return;
        }

        if (onDisk.certificates().isEmpty()
                || !TLSUtils.keyMatchesCertificate(onDisk.key(), onDisk.certificates().get(0))) {
            problem("TLS key on disk does not match the TLS certificate on disk [" + onDisk.signature() + "]. " +
                    "Keeping the certificate in use.", null);
            return;
        }

        List<NodeFilePermissions.Problem> permissionProblems = nzyme.getCrypto().findFilePermissionProblems();
        if (!permissionProblems.isEmpty()) {
            problem("Not loading the TLS certificate on disk [" + onDisk.signature() + "] because nzyme would " +
                    "refuse to start with these file permissions. Keeping the certificate in use. " +
                    NodeFilePermissions.buildMessage(permissionProblems), null);
            return;
        }

        if (onDisk.signature().equals(lastRequestedSignature)
                && lastRequestedAt.isAfter(DateTime.now().minusMinutes(RETRY_MINUTES))) {
            return;
        }

        LOG.info("TLS certificate on disk [{}, expires {}] differs from the certificate in use [{}]. Requesting " +
                "HTTP server certificate reload.", onDisk.signature(), onDisk.expiresAt(), inUse.signature());

        lastProblem = null;
        lastRequestedSignature = onDisk.signature();
        lastRequestedAt = DateTime.now();

        nzyme.getMessageBus().send(Message.create(
                nzyme.getNodeInformation().id(),
                MessageType.CHECK_RESTART_HTTP_SERVER,
                Collections.emptyMap(),
                true
        ));
    }

    // Log each distinct problem once, not every minute.
    private void problem(String message, Exception e) {
        if (message.equals(lastProblem)) {
            return;
        }
        lastProblem = message;

        if (e == null) {
            LOG.warn(message);
        } else {
            LOG.warn(message, e);
        }
    }

    @Override
    public String getName() {
        return "TLS Certificate File Watcher";
    }

}
