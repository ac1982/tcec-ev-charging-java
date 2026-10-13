package io.github.ac1982.tcec.security;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
/** Per-partner material. Supply literal UTF-8 values, not hex/base64 representations. */
public final class PartnerCredentials {
    private final String operatorId, operatorSecret;
    private final byte[] dataSecret, dataSecretIv, sigSecret;
    public PartnerCredentials(String operatorId, String operatorSecret, String dataSecret, String dataSecretIv, String sigSecret) {
        if (operatorId == null || !operatorId.matches("[A-Za-z0-9]{9}")) throw new IllegalArgumentException("OperatorID must contain 9 ASCII letters/digits");
        if (operatorSecret == null || operatorSecret.isBlank()) throw new IllegalArgumentException("OperatorSecret is required");
        this.operatorId = operatorId; this.operatorSecret = operatorSecret;
        this.dataSecret = bytes(dataSecret); this.dataSecretIv = bytes(dataSecretIv); this.sigSecret = bytes(sigSecret);
        if (this.dataSecret.length != 16) throw new IllegalArgumentException("2016 AES-128 DataSecret must be exactly 16 UTF-8 bytes");
        if (this.dataSecretIv.length != 16) throw new IllegalArgumentException("DataSecretIV must be exactly 16 UTF-8 bytes");
        if (this.sigSecret.length == 0) throw new IllegalArgumentException("SigSecret is required");
        this.aesKey = new SecretKeySpec(this.dataSecret, "AES");
        this.iv = new IvParameterSpec(this.dataSecretIv);
        this.macKey = new SecretKeySpec(this.sigSecret, "HmacMD5");
    }
    // Immutable key specs plus per-thread initialized primitives (Cipher/Mac are not thread-safe).
    // Built lazily; never exposed outside this package.
    private final SecretKeySpec aesKey, macKey;
    private final IvParameterSpec iv;
    private final ThreadLocal<Mac> mac = new ThreadLocal<>();
    private final ThreadLocal<Cipher> encryptor = new ThreadLocal<>(), decryptor = new ThreadLocal<>();
    Mac mac() throws GeneralSecurityException {
        Mac cached = mac.get();
        if (cached == null) { cached = Mac.getInstance("HmacMD5"); cached.init(macKey); mac.set(cached); }
        return cached;
    }
    Cipher cipher(int mode) throws GeneralSecurityException {
        ThreadLocal<Cipher> slot = mode == Cipher.ENCRYPT_MODE ? encryptor : decryptor;
        Cipher cached = slot.get();
        if (cached == null) { cached = Cipher.getInstance("AES/CBC/PKCS5Padding"); cached.init(mode, aesKey, iv); slot.set(cached); }
        return cached;
    }
    /** Drops a primitive whose state may be inconsistent after a failed operation. */
    void discard(int mode) { (mode == Cipher.ENCRYPT_MODE ? encryptor : decryptor).remove(); }
    void discardMac() { mac.remove(); }
    private static byte[] bytes(String value) { return Objects.requireNonNull(value, "key is required").getBytes(StandardCharsets.UTF_8); }
    public String operatorId() { return operatorId; }
    public String operatorSecret() { return operatorSecret; }
    public byte[] dataSecret() { return dataSecret.clone(); }
    public byte[] dataSecretIv() { return dataSecretIv.clone(); }
    public byte[] sigSecret() { return sigSecret.clone(); }
    public boolean verifiesOperatorSecret(String value) {
        return value != null && MessageDigest.isEqual(operatorSecret.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
    }
    @Override public String toString() { return "PartnerCredentials[redacted]"; }
}
