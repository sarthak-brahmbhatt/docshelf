// Redactor: Aadhaar (with/without spaces, Verhoeff-gated), PAN, passport, phone, MRZ lines, folio numbers untouched
package com.docshelf.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.docshelf.common.Verhoeff;
import org.junit.jupiter.api.Test;

class RedactorTest {

    private final Redactor redactor = new Redactor();

    private static String validAadhaar() {
        String base = "98765432109";
        return base + Verhoeff.checkDigit(base);
    }

    private static String spaced(String a) {
        return a.substring(0, 4) + " " + a.substring(4, 8) + " " + a.substring(8);
    }

    @Test
    void aadhaarWithAndWithoutSpaces() {
        String a = validAadhaar();
        Redaction r = redactor.redact("Aadhaar No: " + spaced(a) + "\nAlso printed as " + a + " on the back.");
        assertThat(r.text()).doesNotContain(a).doesNotContain(spaced(a));
        assertThat(r.text()).contains("⟨AADHAAR#1⟩");
        assertThat(r.counts()).containsEntry("AADHAAR", 2);
        assertThat(r.placeholders()).containsEntry("⟨AADHAAR#1⟩", spaced(a));
        assertThat(r.rehydrate(r.text())).contains(spaced(a));
    }

    @Test
    void invalidChecksumIsNotAadhaarButLongNumberRuleCatchesIt() {
        String a = validAadhaar();
        String bad = a.substring(0, 11) + ((a.charAt(11) - '0' + 1) % 10);
        Redaction r = redactor.redact("Number " + bad);
        assertThat(r.counts()).doesNotContainKey("AADHAAR");
        assertThat(r.text()).contains("⟨NUM#1⟩");
    }

    @Test
    void folioNumbersAreNotRedacted() {
        String a = validAadhaar();
        Redaction r = redactor.redact("Folio No: " + a + "\nFolio: 1234567/89 ISIN INF109K01BL4\nPolicy No: 123456789012");
        assertThat(r.text()).contains("Folio No: " + a).contains("1234567/89").contains("INF109K01BL4")
                .contains("Policy No: 123456789012");
        assertThat(r.counts()).isEmpty();
    }

    @Test
    void panPassportPhoneEmail() {
        Redaction r = redactor.redact("PAN ABCDE1234F, Passport No. Z1234567, mobile +91 98765 43210 or 09876543211, "
                + "mail sunita@example.com");
        assertThat(r.text()).contains("⟨PAN#1⟩").contains("⟨PASSPORT#1⟩").contains("⟨PHONE#1⟩")
                .contains("⟨PHONE#2⟩").contains("⟨EMAIL#1⟩");
        assertThat(r.text()).doesNotContain("ABCDE1234F").doesNotContain("Z1234567").doesNotContain("98765")
                .doesNotContain("example.com");
        assertThat(r.counts()).containsEntry("PAN", 1).containsEntry("PASSPORT", 1).containsEntry("PHONE", 2)
                .containsEntry("EMAIL", 1);
    }

    @Test
    void passportShapeWithoutKeywordIsKept() {
        Redaction r = redactor.redact("Batch ref A1234567 shipped");
        assertThat(r.text()).contains("A1234567");
    }

    @Test
    void mrzLinesRedactedWhole() {
        String l1 = "P<INDBRAHMBHATT<<SUNITA<<<<<<<<<<<<<<<<<<<<<";
        String l2 = "Z12345674IND5804127F3103159<<<<<<<<<<<<<<<04";
        Redaction r = redactor.redact("Passport\n" + l1 + "\n" + l2 + "\nEnd");
        assertThat(r.text()).isEqualTo("Passport\n⟨MRZ⟩\n⟨MRZ⟩\nEnd");
        assertThat(r.counts()).containsEntry("MRZ", 2);
    }

    @Test
    void drivingLicenceAndVid() {
        Redaction r = redactor.redact("DL No MH12 20151232345 VID 1234 5678 9012 3456");
        assertThat(r.text()).contains("⟨DL#1⟩").contains("⟨VID#1⟩");
        assertThat(r.text()).doesNotContain("20151232345").doesNotContain("3456");
    }

    @Test
    void idempotentOnRedactedText() {
        Redaction first = redactor.redact("PAN ABCDE1234F phone 9876543210");
        Redaction second = redactor.redact(first.text());
        assertThat(second.text()).isEqualTo(first.text());
        assertThat(second.counts()).isEmpty();
    }

    @Test
    void amountsDatesAndShortNumbersUntouched() {
        Redaction r = redactor.redact("Premium Rs. 24,500.00 due 14/03/2027 sum assured 1000000 policy P/123456/01/2026/001234");
        assertThat(r.counts()).isEmpty();
        assertThat(r.text()).contains("1000000").contains("P/123456/01/2026/001234");
    }
}
