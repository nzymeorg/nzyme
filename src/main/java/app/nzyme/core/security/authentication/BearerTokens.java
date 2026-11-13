package app.nzyme.core.security.authentication;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class BearerTokens {

    public static String generate(String prefix) {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return prefix + BaseEncoding.base64Url().omitPadding().encode(raw);
    }

    public static String hash(String plaintextToken) {
        return Hashing.sha256().hashString(plaintextToken, StandardCharsets.UTF_8).toString();
    }

}
