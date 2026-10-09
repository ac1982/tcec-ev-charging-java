package io.github.ac1982.tcec.security;
import io.github.ac1982.tcec.ProtocolException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
/** Legacy algorithms mandated by T/CEC 102.4-2016. Always protect transport with TLS. */
public final class TcecCrypto {
    private TcecCrypto() {}
    public static String encrypt(String plaintext, PartnerCredentials credentials) {
        try {
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(credentials.dataSecret(), "AES"), new IvParameterSpec(credentials.dataSecretIv()));
            return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new ProtocolException(500, "Encryption failed"); }
    }
    public static String decrypt(String encoded, PartnerCredentials credentials) {
        try {
            byte[] ciphertext = Base64.getDecoder().decode(encoded);
            if (ciphertext.length == 0 || ciphertext.length % 16 != 0 || !Base64.getEncoder().encodeToString(ciphertext).equals(encoded)) throw new IllegalArgumentException();
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(credentials.dataSecret(), "AES"), new IvParameterSpec(credentials.dataSecretIv()));
            byte[] bytes = cipher.doFinal(ciphertext);
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (GeneralSecurityException | IllegalArgumentException | CharacterCodingException | NullPointerException e) {
            throw new ProtocolException(ProtocolException.INVALID_REQUEST, "Invalid encrypted data");
        }
    }
    public static String hmac(String input, PartnerCredentials credentials) {
        try {
            var mac = Mac.getInstance("HmacMD5");
            mac.init(new SecretKeySpec(credentials.sigSecret(), "HmacMD5"));
            return HexFormat.of().withUpperCase().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new ProtocolException(500, "Signing failed"); }
    }
    public static boolean verifies(String input, String supplied, PartnerCredentials credentials) {
        if (supplied == null || !supplied.matches("[0-9A-F]{32}")) return false;
        return MessageDigest.isEqual(hmac(input, credentials).getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    }
}
