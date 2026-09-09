// Verhoeff checksum: known vectors and Aadhaar shape rule
package com.docshelf.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerhoeffTest {

    @Test
    void knownVectors() {
        assertThat(Verhoeff.checkDigit("236")).isEqualTo(3);
        assertThat(Verhoeff.isValid("2363")).isTrue();
        assertThat(Verhoeff.isValid("2364")).isFalse();
        assertThat(Verhoeff.isValid("12ab")).isFalse();
        assertThat(Verhoeff.isValid("")).isFalse();
    }

    @Test
    void aadhaarShape() {
        String base = "23456789012";
        String valid = base + Verhoeff.checkDigit(base);
        assertThat(Verhoeff.isAadhaar(valid)).isTrue();
        assertThat(Verhoeff.isAadhaar("0" + valid.substring(1))).isFalse();
        assertThat(Verhoeff.isAadhaar(valid.substring(0, 11))).isFalse();
    }
}
