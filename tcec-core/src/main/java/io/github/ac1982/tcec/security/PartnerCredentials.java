package io.github.ac1982.tcec.security;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
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
    }
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
