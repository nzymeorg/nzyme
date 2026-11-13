package app.nzyme.core.dashboards;

import app.nzyme.core.security.authentication.BearerTokens;

public class DisplayTokens {

    public static final String PREFIX = "nzd_";

    public static String generate() {
        return BearerTokens.generate(PREFIX);
    }

    public static String hash(String plaintextToken) {
        return BearerTokens.hash(plaintextToken);
    }

    public static boolean isDisplayToken(String bearerToken) {
        return bearerToken != null && bearerToken.startsWith(PREFIX);
    }

}
