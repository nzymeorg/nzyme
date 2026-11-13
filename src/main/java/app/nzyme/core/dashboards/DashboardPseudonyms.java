package app.nzyme.core.dashboards;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.crypto.Crypto;
import com.google.common.io.BaseEncoding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;

/*
 * Stand-ins for addresses and other identifiers on displays that hide them. The same value gets the same stand-in on
 * every node of a cluster, so a display can follow a host over time. Without the key, which no display gets, a
 * stand-in can be neither turned back nor recomputed.
 */
public class DashboardPseudonyms {

    private static final Logger LOG = LogManager.getLogger(DashboardPseudonyms.class);

    private static final String PURPOSE = "dashboard-pseudonyms";
    private static final String ALGORITHM = "HmacSHA256";
    private static final int PSEUDONYM_BYTES = 12;

    private final NzymeNode nzyme;

    private byte[] key;

    public DashboardPseudonyms(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    // The context keeps the stand-ins of one dashboard from being matched with those of another.
    public Scope scope(String context) {
        return new Scope(key(), context);
    }

    public static class Scope {

        private final Mac mac;
        private final String context;

        Scope(byte[] key, String context) {
            this.context = context;

            try {
                this.mac = Mac.getInstance(ALGORITHM);
                this.mac.init(new SecretKeySpec(key, ALGORITHM));
            } catch (GeneralSecurityException e) {
                throw new RuntimeException("Could not initialize pseudonyms.", e);
            }
        }

        public String of(String value) {
            return BaseEncoding.base64Url().omitPadding().encode(digest(value), 0, PSEUDONYM_BYTES);
        }

        public UUID of(UUID value) {
            return UUID.nameUUIDFromBytes(digest(value.toString()));
        }

        private byte[] digest(String value) {
            return mac.doFinal((context + "|" + value).getBytes(StandardCharsets.UTF_8));
        }

    }

    private synchronized byte[] key() {
        if (key == null) {
            try {
                key = nzyme.getCrypto().deriveClusterSecret(PURPOSE);
            } catch (Crypto.CryptoOperationException | RuntimeException e) {
                // Displays still get stand-ins. They differ between nodes and change with a restart of this node.
                LOG.error("Could not derive the key for dashboard pseudonyms. Using a temporary one.", e);
                key = new byte[32];
                new SecureRandom().nextBytes(key);
            }
        }

        return key;
    }

}
