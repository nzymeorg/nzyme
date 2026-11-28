package app.nzyme.core.ethernet.portalintegrity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/*
 * The answers of the connectivity check URLs of operating systems and browsers on a network that does not
 * intercept them. A device decides that it is behind a captive portal when it gets anything else.
 */
public final class ConnectivityChecks {

    private static final int MAX_BODY_LENGTH = 4096;

    private static final String APPLE_SUCCESS = "<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>";

    public interface CleanResponse {
        boolean matches(int status, byte[] raw);
    }

    private static final Map<String, CleanResponse> KNOWN = Map.ofEntries(
            Map.entry("http://detectportal.firefox.com/", body("success")),
            Map.entry("http://detectportal.firefox.com/success.txt", body("success")),
            Map.entry("http://captive.apple.com/", body(APPLE_SUCCESS)),
            Map.entry("http://captive.apple.com/hotspot-detect.html", body(APPLE_SUCCESS)),
            Map.entry("http://connectivitycheck.gstatic.com/generate_204", noContent()),
            Map.entry("http://www.gstatic.com/generate_204", noContent()),
            Map.entry("http://www.google.com/generate_204", noContent()),
            Map.entry("http://clients3.google.com/generate_204", noContent()),
            Map.entry("http://connectivity-check.ubuntu.com/", noContent()),
            Map.entry("http://www.msftconnecttest.com/connecttest.txt", body("Microsoft Connect Test")),
            Map.entry("http://www.msftncsi.com/ncsi.txt", body("Microsoft NCSI")),
            Map.entry("http://nmcheck.gnome.org/check_network_status.txt", body("NetworkManager is online")),
            Map.entry("http://networkcheck.kde.org/", body("OK"))
    );

    private ConnectivityChecks() {}

    public static Optional<CleanResponse> of(String controlUrl) {
        return Optional.ofNullable(KNOWN.get(PortalUrls.withoutQuery(controlUrl)));
    }

    private static CleanResponse noContent() {
        return (status, raw) -> status == 204;
    }

    private static CleanResponse body(String expected) {
        return (status, raw) -> status == 200 && bodyOf(raw).map(b -> b.trim().equals(expected)).orElse(false);
    }

    // Empty for a body that is too long to be the answer of a connectivity check.
    private static Optional<String> bodyOf(byte[] raw) {
        String response = new String(raw, StandardCharsets.ISO_8859_1);

        int headerEnd = response.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            return Optional.empty();
        }

        String headers = response.substring(0, headerEnd).toLowerCase(Locale.ROOT);
        String body = response.substring(headerEnd + 4);

        if (headers.contains("\r\ntransfer-encoding:") && headers.contains("chunked")) {
            body = dechunk(body);
        }

        return body == null || body.length() > MAX_BODY_LENGTH ? Optional.empty() : Optional.of(body);
    }

    private static String dechunk(String body) {
        StringBuilder result = new StringBuilder();

        int position = 0;
        while (position < body.length() && result.length() <= MAX_BODY_LENGTH) {
            int lineEnd = body.indexOf("\r\n", position);
            if (lineEnd < 0) {
                return null;
            }

            String sizeField = body.substring(position, lineEnd);
            int extension = sizeField.indexOf(';');
            if (extension >= 0) {
                sizeField = sizeField.substring(0, extension);
            }

            int size;
            try {
                size = Integer.parseInt(sizeField.trim(), 16);
            } catch (NumberFormatException e) {
                return null;
            }

            if (size == 0) {
                return result.toString();
            }

            // The size is from the network and can be anything up to the largest integer.
            int chunkStart = lineEnd + 2;
            if (size < 0 || size > body.length() - chunkStart) {
                return null;
            }

            result.append(body, chunkStart, chunkStart + size);
            position = chunkStart + size + 2;
        }

        return result.length() > MAX_BODY_LENGTH ? null : result.toString();
    }

}
