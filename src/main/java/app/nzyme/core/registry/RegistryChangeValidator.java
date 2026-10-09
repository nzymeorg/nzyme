package app.nzyme.core.registry;

import app.nzyme.plugin.EncryptedRegistryKey;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraintValidator;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RegistryChangeValidator {

    private static final Logger LOG = LogManager.getLogger(RegistryChangeValidator.class);

    public record Change(String key, String value, boolean encrypted) { }

    private final Map<String, RegistryKey> plainKeys = Maps.newHashMap();
    private final Map<String, EncryptedRegistryKey> encryptedKeys = Maps.newHashMap();

    private RegistryChangeValidator() { }

    public static RegistryChangeValidator allowing(RegistryKey... keys) {
        RegistryChangeValidator v = new RegistryChangeValidator();
        for (RegistryKey key : keys) {
            v.plainKeys.put(key.key(), key);
        }
        return v;
    }

    public RegistryChangeValidator allowingEncrypted(EncryptedRegistryKey... keys) {
        for (EncryptedRegistryKey key : keys) {
            encryptedKeys.put(key.key(), key);
        }
        return this;
    }

    public Optional<List<Change>> validate(Map<String, Object> change) {
        if (change == null || change.isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Optional.empty();
        }

        List<Change> changes = Lists.newArrayList();
        for (Map.Entry<String, Object> c : change.entrySet()) {
            if (c.getKey() == null || c.getValue() == null) {
                LOG.info("Configuration parameter with null key or value.");
                return Optional.empty();
            }

            RegistryKey plain = plainKeys.get(c.getKey());
            EncryptedRegistryKey encrypted = encryptedKeys.get(c.getKey());

            if (plain != null) {
                if (!ConfigurationEntryConstraintValidator.checkConstraints(plain, c)) {
                    LOG.info("Configuration parameter [{}] failed constraints.", c.getKey());
                    return Optional.empty();
                }
                changes.add(new Change(plain.key(), c.getValue().toString(), false));
            } else if (encrypted != null) {
                if (!ConfigurationEntryConstraintValidator.checkConstraints(encrypted, c)) {
                    LOG.info("Configuration parameter [{}] failed constraints.", c.getKey());
                    return Optional.empty();
                }
                changes.add(new Change(encrypted.key(), c.getValue().toString(), true));
            } else {
                LOG.info("Unknown configuration parameter [{}].", c.getKey());
                return Optional.empty();
            }
        }

        return Optional.of(changes);
    }

}
