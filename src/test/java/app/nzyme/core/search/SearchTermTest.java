package app.nzyme.core.search;

import org.testng.annotations.Test;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SearchTermTest {

    private static SearchTerm term(String query) {
        return SearchTerm.parse(query).orElseThrow();
    }

    @Test
    public void testRejectsQueriesThatAreTooShortOrTooLong() {
        assertTrue(SearchTerm.parse(null).isEmpty());
        assertTrue(SearchTerm.parse("").isEmpty());
        assertTrue(SearchTerm.parse(" a ").isEmpty());
        assertTrue(SearchTerm.parse("a".repeat(SearchTerm.MAXIMUM_LENGTH + 1)).isEmpty());
        assertTrue(SearchTerm.parse("ab").isPresent());
        assertTrue(SearchTerm.parse("a".repeat(SearchTerm.MAXIMUM_LENGTH)).isPresent());
    }

    @Test
    public void testCleansWhitespaceAndControlCharacters() {
        assertEquals(term("  conference\t\n room\u0000 ").raw(), "conference room");
    }

    @Test
    public void testRecognizesMacAddressNotations() {
        for (String notation : new String[]{"74:56:3c:43:77:af", "74-56-3C-43-77-AF", "7456.3c43.77af", "74563C4377AF"}) {
            SearchTerm term = term(notation);
            assertTrue(term.is(SearchTerm.Kind.MAC), notation);
            assertEquals(term.mac(), "74:56:3C:43:77:AF", notation);
            assertTrue(term.isIdentifier());
        }
    }

    @Test
    public void testRecognizesMacAddressPrefixes() {
        SearchTerm oui = term("74:56:3c");
        assertTrue(oui.is(SearchTerm.Kind.MAC_PREFIX));
        assertFalse(oui.is(SearchTerm.Kind.MAC));
        assertEquals(oui.macPrefix(), "74:56:3C:");
        assertEquals(oui.macStartsWith(), "74:56:3C:%");
        assertFalse(oui.isIdentifier());

        assertEquals(term("74-56-").macPrefix(), "74:56:");
        assertFalse(term("74").is(SearchTerm.Kind.MAC_PREFIX));
        assertFalse(term("facade").is(SearchTerm.Kind.MAC_PREFIX));
    }

    @Test
    public void testRecognizesAddressesAndNetworks() {
        assertEquals(term("192.168.0.134").address(), "192.168.0.134");
        assertEquals(term("FE80::1").address(), "fe80::1");
        assertEquals(term("10.0.0.0/8").cidr(), "10.0.0.0/8");
        assertTrue(term("10.0.0.0/8").is(SearchTerm.Kind.CIDR));

        assertFalse(term("10.0.0.0/33").is(SearchTerm.Kind.CIDR));
        assertFalse(term("192.168.0").is(SearchTerm.Kind.IP));
        assertFalse(term("999.1.1.1").is(SearchTerm.Kind.IP));
    }

    @Test
    public void testAddressesWithZoneStayText() {
        // PostgreSQL does not take these as addresses. Bound as one, every source would fail.
        assertFalse(term("fe80::1%1").is(SearchTerm.Kind.IP));
        assertFalse(term("fe80::1%eth0").is(SearchTerm.Kind.IP));
        assertFalse(term("fe80::%1/64").is(SearchTerm.Kind.CIDR));
        assertTrue(term("fe80::1%eth0").is(SearchTerm.Kind.TEXT));
    }

    @Test
    public void testNeverResolvesNames() {
        // A name must stay text. Resolving it would send what a user searches for to a DNS server.
        SearchTerm name = term("example.org");
        assertFalse(name.is(SearchTerm.Kind.IP));
        assertTrue(name.isRecordedText());
    }

    @Test
    public void testRecognizesUuidsHashesAndJa4() {
        UUID uuid = UUID.randomUUID();
        assertEquals(term(uuid.toString().toUpperCase()).uuid(), uuid);

        assertTrue(term("2b3f2b4d320523e0933ccda74370156a3802aa212c3e0cadc87a65ea446176ab").is(SearchTerm.Kind.HASH));
        assertFalse(term("2b3f2b4d").is(SearchTerm.Kind.HASH));
        assertTrue(term("t13d1516h2_8daaf6152771_b186095e22b6").is(SearchTerm.Kind.JA4));
    }

    @Test
    public void testEscapesPatternCharacters() {
        assertEquals(term("50%_off\\").contains(), "%50\\%\\_off\\\\%");
    }

    @Test
    public void testAddressInTextDoesNotMatchLongerAddresses() {
        // PostgreSQL evaluates the pattern. Its syntax for what is used here is the one of Java.
        Pattern pattern = Pattern.compile(term("1.1.1.1").addressInText());

        assertTrue(pattern.matcher("DNS 1.1.1.1, 1.0.0.1.").find());
        assertTrue(pattern.matcher("server [1.1.1.1]").find());
        assertTrue(pattern.matcher("gateway 1.1.1.1.").find());
        assertFalse(pattern.matcher("DNS 11.1.1.10").find());
        assertFalse(pattern.matcher("DNS 1.1.1.12").find());
        assertFalse(pattern.matcher("DNS 1.1.1.1.5").find());
        assertFalse(pattern.matcher("DNS 21.1.1.1").find());
    }

    @Test
    public void testShortTextDoesNotSearchRecordedData() {
        assertFalse(term("ab").isRecordedText());
        assertTrue(term("abc").isRecordedText());
        assertFalse(term("192.168.0.1").isRecordedText());
    }

}
