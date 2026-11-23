package app.nzyme.core.crypto.tls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Date;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TLSUtilsKeyMatchTest {

    @BeforeClass
    public void setUp() {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static KeyPair ec() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    private static X509Certificate selfSigned(KeyPair kp, String sigAlg) throws Exception {
        X500Name name = new X500Name("CN=nzyme-test");
        Date now = new Date();
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now.getTime()), now, new Date(now.getTime() + 86400000L), name, kp.getPublic());
        return new JcaX509CertificateConverter().getCertificate(
                b.build(new JcaContentSignerBuilder(sigAlg).build(kp.getPrivate())));
    }

    // Round-trip the key through a PKCS#8 PEM and nzyme's own parser, like a key loaded from tls.key.
    private static PrivateKey viaPem(PrivateKey key) throws Exception {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return TLSUtils.readKeyFromPEM(pem);
    }

    @Test
    public void testMatchingRSA() throws Exception {
        KeyPair kp = rsa();
        assertTrue(TLSUtils.keyMatchesCertificate(kp.getPrivate(), selfSigned(kp, "SHA256withRSA")));
    }

    @Test
    public void testMatchingEC() throws Exception {
        KeyPair kp = ec();
        assertTrue(TLSUtils.keyMatchesCertificate(kp.getPrivate(), selfSigned(kp, "SHA256withECDSA")));
    }

    @Test
    public void testMatchingECKeyReadFromPem() throws Exception {
        KeyPair kp = ec();
        assertTrue(TLSUtils.keyMatchesCertificate(viaPem(kp.getPrivate()), selfSigned(kp, "SHA256withECDSA")));
    }

    @Test
    public void testMatchingRSAKeyReadFromPem() throws Exception {
        KeyPair kp = rsa();
        assertTrue(TLSUtils.keyMatchesCertificate(viaPem(kp.getPrivate()), selfSigned(kp, "SHA256withRSA")));
    }

    @Test
    public void testMismatchedEC() throws Exception {
        // Renewal in progress: new certificate already written, old key still in place.
        KeyPair oldPair = ec();
        KeyPair newPair = ec();
        assertFalse(TLSUtils.keyMatchesCertificate(oldPair.getPrivate(), selfSigned(newPair, "SHA256withECDSA")));
    }

    @Test
    public void testMismatchedRSA() throws Exception {
        assertFalse(TLSUtils.keyMatchesCertificate(rsa().getPrivate(), selfSigned(rsa(), "SHA256withRSA")));
    }

    @Test
    public void testMismatchedKeyType() throws Exception {
        assertFalse(TLSUtils.keyMatchesCertificate(rsa().getPrivate(), selfSigned(ec(), "SHA256withECDSA")));
    }

}
