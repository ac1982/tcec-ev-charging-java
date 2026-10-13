package io.github.ac1982.tcec.security;
import io.github.ac1982.tcec.ProtocolException;
import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
/**
 * Legacy algorithms mandated by T/CEC 102.4-2016. Always protect transport with TLS.
 * Thread-safe: initialized Cipher/Mac instances are reused per thread and per credentials.
 * doFinal returns each primitive to its initialized state (same key and protocol-fixed IV).
 */
public final class TcecCrypto {
    private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();
    private TcecCrypto() {}
    public static String encrypt(String plaintext, PartnerCredentials credentials) {
        try {
            return Base64.getEncoder().encodeToString(credentials.cipher(Cipher.ENCRYPT_MODE).doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            credentials.discard(Cipher.ENCRYPT_MODE);
            throw new ProtocolException(500, "Encryption failed");
        }
    }
    public static String decrypt(String encoded, PartnerCredentials credentials) {
        try {
            byte[] ciphertext = Base64.getDecoder().decode(encoded);
            if (ciphertext.length == 0 || ciphertext.length % 16 != 0 || !Base64.getEncoder().encodeToString(ciphertext).equals(encoded)) throw new IllegalArgumentException();
            byte[] bytes;
            try { bytes = credentials.cipher(Cipher.DECRYPT_MODE).doFinal(ciphertext); }
            catch (GeneralSecurityException | RuntimeException e) { credentials.discard(Cipher.DECRYPT_MODE); throw e; }
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (GeneralSecurityException | IllegalArgumentException | CharacterCodingException | NullPointerException e) {
            throw new ProtocolException(ProtocolException.INVALID_REQUEST, "Invalid encrypted data");
        }
    }
    public static String hmac(String input, PartnerCredentials credentials) {
        try {
            return UPPER_HEX.formatHex(credentials.mac().doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            credentials.discardMac();
            throw new ProtocolException(500, "Signing failed");
        }
    }
    public static boolean verifies(String input, String supplied, PartnerCredentials credentials) {
        if (supplied == null || supplied.length() != 32) return false;
        for (int i = 0; i < 32; i++) {
            char c = supplied.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) return false;
        }
        return MessageDigest.isEqual(hmac(input, credentials).getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    }
}
