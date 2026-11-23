package app.nzyme.core.crypto.tls;

import app.nzyme.core.configuration.node.MiscConfiguration;
import app.nzyme.core.configuration.node.NodeConfiguration;
import app.nzyme.core.configuration.node.PerformanceConfiguration;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.security.NodeFilePermissions;
import app.nzyme.core.util.FilePermissions;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.joda.time.DateTime;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TLSUtilsReloadRefusalTest {

    private Path directory;
    private Path cryptoDirectory;
    private Path configurationFile;
    private Path tlsKey;

    @BeforeMethod
    public void setUp() throws IOException {
        if (!FilePermissions.isSupported()) {
            throw new SkipException("File system does not support POSIX permissions.");
        }

        directory = Files.createTempDirectory("nzyme-tls-reload-test");
        cryptoDirectory = Files.createDirectory(directory.resolve("crypto"));
        configurationFile = Files.writeString(directory.resolve("nzyme.conf"), "test");
        tlsKey = Files.writeString(cryptoDirectory.resolve(Crypto.TLS_KEY_FILE_NAME), "test");

        chmod(cryptoDirectory, "rwx------");
        chmod(configurationFile, "rw-r-----");
        chmod(tlsKey, "rw-------");
    }

    @AfterMethod(alwaysRun = true)
    public void cleanUp() throws IOException {
        if (directory == null) {
            return;
        }

        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static void chmod(Path path, String permissions) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    private static TLSKeyAndCertificate tls(KeyPair certificateKeys, KeyPair key) throws Exception {
        X500Name name = new X500Name("CN=nzyme-test");
        Date now = new Date();
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now.getTime()), now,
                        new Date(now.getTime() + 86400000L), name, certificateKeys.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withECDSA").build(certificateKeys.getPrivate()))
        );

        return TLSKeyAndCertificate.create(
                UUID.randomUUID(),
                TLSSourceType.FILE_LOADED,
                List.of(certificate),
                key.getPrivate(),
                TLSUtils.calculateTLSCertificateFingerprint(certificate),
                new DateTime(certificate.getNotBefore()),
                new DateTime(certificate.getNotAfter())
        );
    }

    private static TLSKeyAndCertificate matchingPair() throws Exception {
        KeyPair keys = keyPair();
        return tls(keys, keys);
    }

    private NodeFilePermissions filePermissions() {
        return new NodeFilePermissions(NodeConfiguration.create(
                false,
                Optional.empty(),
                "",
                URI.create("https://127.0.0.1:22900/"),
                URI.create("https://127.0.0.1:22900/"),
                Optional.empty(),
                "",
                cryptoDirectory.toString(),
                Optional.empty(),
                "",
                Optional.empty(),
                Optional.empty(),
                true,
                PerformanceConfiguration.create(1, 1),
                MiscConfiguration.create("", ""),
                Optional.of(configurationFile.toString())
        ));
    }

    @Test
    public void testAcceptsMatchingPairWithSafePermissions() throws Exception {
        assertTrue(TLSUtils.findReloadRefusal(matchingPair(), filePermissions()).isEmpty());
    }

    @Test
    public void testRefusesKeyThatDoesNotMatchCertificate() throws Exception {
        Optional<String> refusal = TLSUtils.findReloadRefusal(tls(keyPair(), keyPair()), filePermissions());

        assertTrue(refusal.isPresent());
        assertTrue(refusal.get().contains("does not match"));
    }

    @Test
    public void testRefusesKeyReadableByAllUsers() throws Exception {
        chmod(tlsKey, "rw-r--r--");

        Optional<String> refusal = TLSUtils.findReloadRefusal(matchingPair(), filePermissions());

        assertTrue(refusal.isPresent());
        assertTrue(refusal.get().contains("chmod 600 " + tlsKey));
    }

    @Test
    public void testRefusesKeyReadableByGroup() throws Exception {
        chmod(tlsKey, "rw-r-----");

        assertTrue(TLSUtils.findReloadRefusal(matchingPair(), filePermissions()).isPresent());
    }

    @Test
    public void testRefusesCryptoDirectoryAccessibleByAllUsers() throws Exception {
        chmod(cryptoDirectory, "rwxr-xr-x");

        Optional<String> refusal = TLSUtils.findReloadRefusal(matchingPair(), filePermissions());

        assertTrue(refusal.isPresent());
        assertTrue(refusal.get().contains("chmod 700 " + cryptoDirectory));
    }

    @Test
    public void testAcceptsAgainAfterPermissionsAreFixed() throws Exception {
        TLSKeyAndCertificate pair = matchingPair();

        chmod(tlsKey, "rw-r--r--");
        assertTrue(TLSUtils.findReloadRefusal(pair, filePermissions()).isPresent());

        chmod(tlsKey, "rw-------");
        assertFalse(TLSUtils.findReloadRefusal(pair, filePermissions()).isPresent());
    }

}
