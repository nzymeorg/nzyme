package app.nzyme.core.crypto.tls;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import app.nzyme.core.security.NodeFilePermissions;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.joda.time.DateTime;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.*;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TLSUtils {

    private static final Pattern CERT_BASE64_BLOCKS = Pattern.compile(
            "-+BEGIN\\s+.*CERTIFICATE[^-]*-+(?:\\s|\\r|\\n)+" +
                    "([a-z0-9+/=\\r\\n]+)" +
                    "-+END\\s+.*CERTIFICATE[^-]*-+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_BASE64_BLOCK = Pattern.compile(
            "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" +
                    "([a-z0-9+/=\\r\\n]+)" +
                    "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+",
            Pattern.CASE_INSENSITIVE);

    public static List<X509Certificate> readCertificateChainFromPEM(String pem) throws PEMParserException {
        final Matcher m = CERT_BASE64_BLOCKS.matcher(pem);

        final List<X509Certificate> certs = Lists.newArrayList();
        int pos = 0;

        while (m.find(pos)) {
            byte[] bytes = BaseEncoding.base64().decode(
                    CharMatcher
                            .breakingWhitespace()
                            .removeFrom(m.group(1))
            );

            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                certs.add((X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(bytes)));
            } catch(Exception e) {
                throw new PEMParserException("Could not construct certificate.", e);
            }

            pos = m.end();
        }

        if (certs.isEmpty()) {
            throw new PEMParserException("Could not find any certificates in certificate file.");
        }

        return certs;
    }

    public static PrivateKey readKeyFromPEM(String pem) throws PEMParserException {
        final Matcher m = KEY_BASE64_BLOCK.matcher(pem);

        if (!m.find()) {
            throw new PEMParserException("No key found in data.");
        }

        return parseKey(
                BaseEncoding.base64().decode(
                        CharMatcher
                                .breakingWhitespace()
                                .removeFrom(m.group(1)))
        );
    }

    public static PrivateKey deserializeKey(String base64) throws PEMParserException {
        return parseKey(BaseEncoding.base64().decode(base64));
    }

    /**
     * OKAY this is a little weird. Because of how the certificates/keys are internally represented and how the crypto
     * APIs let us access the key (surprise: it's not PEM), we are not storing the entire original PEM to make our lives
     * easier. That's why we have to perform all sorts of weird maneuvers and reconstructive surgery ot handle both
     * PKCS#1 and PKCS#8 formats. We are also using exception for control flow because the APIs are a mess.
     * This needs a ton of unit test at all times.
     *
     * @param bytes Serialized private key, stripped of PEM header, footer and newlines
     * @return Parsed PrivateKey to use in Java crypto APIs
     * @throws PEMParserException if key could not be parsed
     */
    public static PrivateKey parseKey(byte[] bytes) throws PEMParserException {
        try {
            // Try PKCS#8 format first. It appears to be the more common one.
            return parsePKCS8Key(bytes);
        } catch (Exception ignored) {
            try {
                // Didn't work - Try PKCS1 key.
                return parsePKCS1Key(bytes);
            } catch (Exception e) {
                throw new PEMParserException("Parsing key failed. Both PKCS#1 and PKCS#8 parsing did not succeed.", e);
            }
        }
    }

    private static PrivateKey parsePKCS8Key(byte[] bytes) throws PEMParserException, NoSuchAlgorithmException {
        // Try PKCS8 first.
        KeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (InvalidKeySpecException ignore) {
            try {
                return KeyFactory.getInstance("DSA").generatePrivate(keySpec);
            } catch (InvalidKeySpecException ignore2) {
                try {
                    return KeyFactory.getInstance("EC").generatePrivate(keySpec);
                } catch (InvalidKeySpecException e) {
                    throw new PEMParserException("Could not construct private key.", e);
                }
            }
        }
    }

    private static PrivateKey parsePKCS1Key(byte[] bytes) throws IOException {
        // PEM parser required for PKCS1, but it needs a whole reconstructed PEM file.
        String key = reconstructECPEMFromBytes(bytes);
        Object parsed = new PEMParser(new StringReader(key)).readObject();
        KeyPair pair = new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) parsed);
        return pair.getPrivate();
    }

    private static String reconstructECPEMFromBytes(byte[] bytes) {
        // PEM file is blocks of 64 characters.
        Iterable<String> split = Splitter.fixedLength(64)
                .split(BaseEncoding.base64().encode(bytes));
        StringBuilder result = new StringBuilder();

        result.append("-----BEGIN EC PRIVATE KEY-----\n");
        for (String s : split) {
            result.append(s).append("\n");
        }
        result.append("-----END EC PRIVATE KEY-----");

        return result.toString();
    }

    public static String serializeCertificateChain(List<X509Certificate> certificates) throws CertificateEncodingException {
        List<String> strings = Lists.newArrayList();
        for (Certificate certificate : certificates) {
            strings.add(BaseEncoding.base64().encode(certificate.getEncoded()));
        }

        return Joiner.on(",").join(strings);
    }

    public static List<X509Certificate> deSerializeCertificateChain(String serialized) throws CertificateException {
        List<X509Certificate> certificates = Lists.newArrayList();

        for (String s : Splitter.on(",").split(serialized)) {
            byte[] certBytes = BaseEncoding.base64().decode(s);
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            certificates.add((X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes)));
        }

        return certificates;
    }

    public static String calculateTLSCertificateFingerprint(X509Certificate certificate) throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(certificate.getEncoded());
        return DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
    }

    /*
     * True if the private key belongs to the certificate's public key: signs a random challenge with the key and
     * verifies it with the certificate. Catches a cert/key pair that doesn't belong together, for example while
     * the two files are being replaced one after the other.
     */
    public static boolean keyMatchesCertificate(PrivateKey key, X509Certificate certificate) {
        String algorithm = switch (key.getAlgorithm()) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            case "Ed25519", "EdDSA" -> "Ed25519";
            case "Ed448" -> "Ed448";
            default -> null;
        };

        if (algorithm == null) {
            return false;
        }

        try {
            byte[] challenge = new byte[32];
            new SecureRandom().nextBytes(challenge);

            Signature signer = Signature.getInstance(algorithm);
            signer.initSign(key);
            signer.update(challenge);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(challenge);
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /*
     * Why a certificate and key read from disk must not be loaded into a running node, if there is a reason. Every
     * reload at runtime has to go through this. The node start has its own checks.
     */
    public static Optional<String> findReloadRefusal(TLSKeyAndCertificate onDisk, NodeFilePermissions filePermissions) {
        if (onDisk.certificates().isEmpty()
                || !keyMatchesCertificate(onDisk.key(), onDisk.certificates().get(0))) {
            return Optional.of("The TLS key on disk does not match the TLS certificate on disk.");
        }

        List<NodeFilePermissions.Problem> permissionProblems = filePermissions.check();
        if (!permissionProblems.isEmpty()) {
            return Optional.of("The node would refuse to start with these file permissions. "
                    + NodeFilePermissions.buildMessage(permissionProblems));
        }

        return Optional.empty();
    }

    public static TLSKeyAndCertificate readTLSKeyAndCertificateFromInputStreams(UUID nodeId,
                                                                                TLSSourceType sourceType,
                                                                                InputStream certificate,
                                                                                InputStream privateKey) throws TLSCertificateCreationException {
        String certificateInput, keyInput;
        try {
            certificateInput = new String(certificate.readAllBytes());
            keyInput = new String(privateKey.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Could not read provided TLS certificate form data.", e);
        }

        List<X509Certificate> certificates;
        PrivateKey key;
        try {
            certificates = TLSUtils.readCertificateChainFromPEM(certificateInput);
            key = TLSUtils.readKeyFromPEM(keyInput);
        } catch(Exception e) {
            throw new TLSCertificateCreationException("Could not build key/certificate from provided data.", e);
        }

        // We have a valid certificate and key from here on. Serialize to Base64.
        X509Certificate firstCert = certificates.get(0);
        String fingerprint;

        try {
            fingerprint = TLSUtils.calculateTLSCertificateFingerprint(firstCert);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new TLSCertificateCreationException("Could not build certificate fingerprint.", e);
        }

        return TLSKeyAndCertificate.create(
                nodeId,
                sourceType,
                certificates,
                key,
                fingerprint,
                new DateTime(firstCert.getNotBefore()),
                new DateTime(firstCert.getNotAfter())
        );
    }

    public static TLSWildcardKeyAndCertificate readTLSWildcardKeyAndCertificateFromInputStreams(String nodeMatcher,
                                                                                                TLSSourceType sourceType,
                                                                                                InputStream certificate,
                                                                                                InputStream privateKey)
            throws TLSCertificateCreationException {
        String certificateInput, keyInput;
        try {
            certificateInput = new String(certificate.readAllBytes());
            keyInput = new String(privateKey.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Could not read provided TLS certificate form data.", e);
        }

        List<X509Certificate> certificates;
        PrivateKey key;
        try {
            certificates = TLSUtils.readCertificateChainFromPEM(certificateInput);
            key = TLSUtils.readKeyFromPEM(keyInput);
        } catch(Exception e) {
            throw new TLSCertificateCreationException("Could not build key/certificate from provided data.", e);
        }

        // We have a valid certificate and key from here on. Serialize to Base64.
        X509Certificate firstCert = certificates.get(0);
        String fingerprint;

        try {
            fingerprint = TLSUtils.calculateTLSCertificateFingerprint(firstCert);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new TLSCertificateCreationException("Could not build certificate fingerprint.", e);
        }

        return TLSWildcardKeyAndCertificate.create(
                null,
                nodeMatcher,
                sourceType,
                certificates,
                key,
                fingerprint,
                new DateTime(firstCert.getNotBefore()),
                new DateTime(firstCert.getNotAfter())
        );
    }


    public static final class PEMParserException extends Exception {

        public PEMParserException(String msg) {
            super(msg);
        }

        public PEMParserException(String msg, Throwable t) {
            super(msg, t);
        }

    }

    public static final class TLSCertificateCreationException extends Exception {

        public TLSCertificateCreationException(String msg, Throwable t) {
            super(msg, t);
        }

    }

}
