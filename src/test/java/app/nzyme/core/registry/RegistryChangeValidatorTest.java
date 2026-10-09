package app.nzyme.core.registry;

import app.nzyme.plugin.EncryptedRegistryKey;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraint;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.testng.Assert.*;

public class RegistryChangeValidatorTest {

    private static final RegistryKey BOOL = RegistryKey.create(
            "test_bool",
            Optional.of(new ArrayList<>() {{ add(ConfigurationEntryConstraint.createSimpleBooleanConstraint()); }}),
            Optional.of("false"),
            false
    );

    private static final RegistryKey NUMBER = RegistryKey.create(
            "test_number",
            Optional.of(new ArrayList<>() {{ add(ConfigurationEntryConstraint.createNumberRangeConstraint(1, 10)); }}),
            Optional.of("5"),
            false
    );

    private static final EncryptedRegistryKey SECRET = EncryptedRegistryKey.create(
            "test_secret",
            Optional.of(new ArrayList<>() {{ add(ConfigurationEntryConstraint.createStringLengthConstraint(1, 64)); }}),
            false
    );

    private static Map<String, Object> change(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void testAcceptsKnownKeysAndReturnsCanonicalKeys() {
        Optional<List<RegistryChangeValidator.Change>> result = RegistryChangeValidator
                .allowing(BOOL, NUMBER)
                .allowingEncrypted(SECRET)
                .validate(change("test_bool", true, "test_number", 7, "test_secret", "hunter2"));

        assertTrue(result.isPresent());
        assertEquals(result.get().size(), 3);

        for (RegistryChangeValidator.Change c : result.get()) {
            switch (c.key()) {
                case "test_bool" -> { assertEquals(c.value(), "true"); assertFalse(c.encrypted()); }
                case "test_number" -> { assertEquals(c.value(), "7"); assertFalse(c.encrypted()); }
                case "test_secret" -> { assertEquals(c.value(), "hunter2"); assertTrue(c.encrypted()); }
                default -> fail("Unexpected key " + c.key());
            }
        }
    }

    @Test
    public void testRejectsUnknownKeyEvenWhenOthersAreValid() {
        Optional<List<RegistryChangeValidator.Change>> result = RegistryChangeValidator
                .allowing(BOOL)
                .validate(change("test_bool", true, "quota_taps", 9999));

        assertTrue(result.isEmpty());
    }

    @Test
    public void testRejectsKeyOfOtherAllowlist() {
        // A key that exists elsewhere but is not allowed by this resource.
        assertTrue(RegistryChangeValidator.allowing(BOOL).validate(change("test_number", 5)).isEmpty());
    }

    @Test
    public void testRejectsConstraintViolations() {
        assertTrue(RegistryChangeValidator.allowing(BOOL).validate(change("test_bool", "maybe")).isEmpty());
        assertTrue(RegistryChangeValidator.allowing(NUMBER).validate(change("test_number", 11)).isEmpty());
        assertTrue(RegistryChangeValidator.allowing(NUMBER).validate(change("test_number", "abc")).isEmpty());
    }

    @Test
    public void testRejectsNullValueAndEmptyChange() {
        assertTrue(RegistryChangeValidator.allowing(BOOL).validate(change("test_bool", null)).isEmpty());
        assertTrue(RegistryChangeValidator.allowing(BOOL).validate(new HashMap<>()).isEmpty());
        assertTrue(RegistryChangeValidator.allowing(BOOL).validate(null).isEmpty());
    }

    @Test
    public void testEncryptedKeyIsNotAcceptedAsPlainKey() {
        // SECRET is not in the plain allowlist and not registered as encrypted here.
        assertTrue(RegistryChangeValidator.allowing(BOOL).validate(change("test_secret", "x")).isEmpty());
    }

}
