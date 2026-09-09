// Masker: every mask rule from design v0.1 section 7.2
package com.docshelf.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaskerTest {

    private final Masker masker = new Masker();

    @Test
    void aadhaar() {
        assertThat(masker.aadhaar("1234 5678 1234")).isEqualTo("XXXX XXXX 1234");
        assertThat(masker.aadhaar("123456781234")).isEqualTo("XXXX XXXX 1234");
    }

    @Test
    void pan() {
        assertThat(masker.pan("ABCDE1234F")).isEqualTo("XXXXX1234X");
        assertThat(masker.pan("abcde1234f")).isEqualTo("XXXXX1234X");
    }

    @Test
    void passport() {
        assertThat(masker.passport("X1234567")).isEqualTo("X•••••67");
    }

    @Test
    void drivingLicence() {
        assertThat(masker.drivingLicence("MH12 20151232345")).isEqualTo("MH12 •••••••2345");
        assertThat(masker.drivingLicence("MH1220151232345")).isEqualTo("MH12 •••••••2345");
    }

    @Test
    void phone() {
        assertThat(masker.phone("+919876543210")).isEqualTo("+91 98•••• •210");
        assertThat(masker.phone("9876543210")).isEqualTo("+91 98•••• •210");
    }

    @Test
    void emailAndGeneric() {
        assertThat(masker.email("ramesh@example.com")).isEqualTo("r•••••@example.com");
        assertThat(masker.generic("P/123456/01", 4)).isEqualTo("•••••••6/01");
        assertThat(masker.generic(null, 4)).isNull();
        assertThat(masker.forIdType("PASSPORT", "X1234567")).isEqualTo("X•••••67");
        assertThat(masker.forIdType("AADHAAR", "1234 5678 1234")).isEqualTo("XXXX XXXX 1234");
    }
}
