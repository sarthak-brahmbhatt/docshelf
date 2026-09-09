// FieldCipher: round trip, distinct IVs, AAD binding and tamper detection
package com.docshelf.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldCipherTest {

    private final FieldCipher cipher = new FieldCipher(TestKeys.keyService());

    @Test
    void roundTrip() {
        String aad = FieldCipher.aad("id_document", "number", UUID.randomUUID());
        byte[] ct = cipher.encrypt("1234 5678 9012", aad);
        assertThat(ct).startsWith("v1".getBytes());
        assertThat(ct.length).isEqualTo(2 + 12 + "1234 5678 9012".length() + 16);
        assertThat(cipher.decryptToString(ct, aad)).isEqualTo("1234 5678 9012");
    }

    @Test
    void sameInputEncryptsDifferently() {
        String aad = FieldCipher.aad("family_member", "pan", "x");
        assertThat(cipher.encrypt("ABCDE1234F", aad)).isNotEqualTo(cipher.encrypt("ABCDE1234F", aad));
    }

    @Test
    void wrongAadFails() {
        byte[] ct = cipher.encrypt("secret", FieldCipher.aad("t", "c", 1));
        assertThatThrownBy(() -> cipher.decrypt(ct, FieldCipher.aad("t", "c", 2)))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("authentication failed");
    }

    @Test
    void tamperedCiphertextFails() {
        String aad = FieldCipher.aad("t", "c", 1);
        byte[] ct = cipher.encrypt("secret value", aad);
        ct[ct.length - 1] ^= 0x01;
        assertThatThrownBy(() -> cipher.decrypt(ct, aad)).isInstanceOf(CryptoException.class);
        ct[ct.length - 1] ^= 0x01;
        ct[20] ^= 0x01;
        assertThatThrownBy(() -> cipher.decrypt(ct, aad)).isInstanceOf(CryptoException.class);
    }

    @Test
    void unknownFormatRejected() {
        assertThatThrownBy(() -> cipher.decrypt(new byte[] {1, 2, 3}, "a")).isInstanceOf(CryptoException.class);
        assertThat(cipher.encrypt((String) null, "a")).isNull();
        assertThat(cipher.decrypt(null, "a")).isNull();
    }

    @Test
    void differentMasterKeyCannotDecrypt() {
        String aad = FieldCipher.aad("t", "c", 1);
        byte[] ct = cipher.encrypt("secret", aad);
        FieldCipher other = new FieldCipher(new KeyService(new byte[32]));
        assertThatThrownBy(() -> other.decrypt(ct, aad)).isInstanceOf(CryptoException.class);
    }
}
