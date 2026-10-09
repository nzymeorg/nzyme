package app.nzyme.core.crypto.pgp;

import app.nzyme.core.crypto.Crypto;
import app.nzyme.plugin.distributed.messaging.MessageHandler;
import app.nzyme.plugin.distributed.messaging.MessageProcessingResult;
import app.nzyme.plugin.distributed.messaging.ReceivedMessage;
import tools.jackson.databind.ObjectMapper;
import com.google.common.io.BaseEncoding;
import app.nzyme.core.util.FilePermissions;
import java.nio.file.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;

public class PGPKeyMessageBusReceiver implements MessageHandler {

    private static final Logger LOG = LogManager.getLogger(PGPKeyMessageBusReceiver.class);

    private final Crypto crypto;
    private final File cryptoDirectoryConfig;
    private final ObjectMapper om;

    public PGPKeyMessageBusReceiver(File cryptoDirectory, Crypto crypto) {
        this.crypto = crypto;
        this.cryptoDirectoryConfig = cryptoDirectory;
        this.om = new ObjectMapper();
    }

    @Override
    public MessageProcessingResult handle(ReceivedMessage message) {
        try {
            if (!crypto.isPGPKeySyncEnabled()) {
                // Really, it should never even come this far, but why not check.
                LOG.warn("PGP sync is disabled. Not reading keys.");
                return MessageProcessingResult.FAILURE;
            }

            LOG.info("Received [{}] from [{}].", message.type(), message.sender());

            PGPKeyMessagePayload payload = om.readValue(message.parametersString(), PGPKeyMessagePayload.class);

            // Decrypt and decode received keys.
            byte[] privateKey, publicKey;
            try(InputStream privateKeyIn = new ByteArrayInputStream(crypto.getNodeLocalPGPKeys().privateKey()); InputStream privateKeyIn2 = new ByteArrayInputStream(crypto.getNodeLocalPGPKeys().privateKey())) {
                privateKey = crypto.decrypt(BaseEncoding.base64().decode(payload.privateKey()), privateKeyIn);
                publicKey = crypto.decrypt((BaseEncoding.base64().decode(payload.publicKey())), privateKeyIn2);
            } catch (Crypto.CryptoOperationException e) {
                throw new RuntimeException("Could not decrypt and decode received keys.", e);
            }

            LOG.info("Received keys decrypted and decoded.");

            // Write keys to files.
            File privateKeyLocation = Paths.get(cryptoDirectoryConfig.toString(),
                    Crypto.PGP_PRIVATE_KEY_FILE_NAME).toFile();
            File publicKeyLocation = Paths.get(cryptoDirectoryConfig.toString(),
                    Crypto.PGP_PUBLIC_KEY_FILE_NAME).toFile();

            FilePermissions.writeOwnerOnly(privateKeyLocation.toPath(), privateKey);
            Files.write(publicKeyLocation.toPath(), publicKey);

            LOG.info("Decrypted keys written to disk.");

            return MessageProcessingResult.SUCCESS;
        } catch(Exception e) {
            LOG.info("Could not process received PGP key.", e);
            return MessageProcessingResult.FAILURE;
        }
    }

    @Override
    public String getName() {
        return "PGP Key Receiver";
    }

}
