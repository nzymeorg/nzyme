package app.nzyme.core.ethernet.dns;

import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DnsNames {

    /*
     * The labels below the registered domain: "a.b.example.com" with registered domain "example.com" is "a.b".
     * Null when the name is the registered domain itself or the registered domain is unknown.
     */
    @Nullable
    public static String subdomain(@Nullable String name, @Nullable String registeredDomain) {
        if (name == null || registeredDomain == null || registeredDomain.isBlank()) {
            return null;
        }

        String n = name.toLowerCase().trim();
        String d = registeredDomain.toLowerCase().trim();
        while (n.endsWith(".")) {
            n = n.substring(0, n.length() - 1);
        }

        if (!n.endsWith("." + d) || n.length() <= d.length() + 1) {
            return null;
        }

        return n.substring(0, n.length() - d.length() - 1);
    }

    // Shannon entropy in bits per character of the labels below the registered domain. Null when there are none.
    @Nullable
    public static Float subdomainEntropy(@Nullable String name, @Nullable String registeredDomain) {
        String subdomain = subdomain(name, registeredDomain);
        if (subdomain == null) {
            return null;
        }

        return (float) shannonEntropy(subdomain);
    }

    public static double shannonEntropy(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }

        Map<Character, Integer> counts = new HashMap<>();
        for (char c : s.toCharArray()) {
            counts.merge(c, 1, Integer::sum);
        }

        double entropy = 0;
        double length = s.length();
        for (int count : counts.values()) {
            double p = count / length;
            entropy -= p * (Math.log(p) / Math.log(2));
        }

        return entropy;
    }

}
