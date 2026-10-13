package io.github.ac1982.tcec;

import io.github.ac1982.tcec.security.PartnerCredentials;
import io.github.ac1982.tcec.security.TcecCrypto;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reused per-thread Cipher/Mac instances must be stateless across calls, failures and threads. */
class CryptoReuseTest {
    static final PartnerCredentials A = new PartnerCredentials("123456789", "secret", "1234567890abcdef", "abcdef1234567890", "sig-a");
    static final PartnerCredentials B = new PartnerCredentials("987654321", "secret", "fedcba0987654321", "0987654321fedcba", "sig-b");

    @Test void repeatedCallsAreDeterministicAndIsolatedPerCredentials() {
        String first = TcecCrypto.encrypt("{\"x\":1}", A);
        assertEquals(first, TcecCrypto.encrypt("{\"x\":1}", A));
        assertNotEquals(first, TcecCrypto.encrypt("{\"x\":1}", B));
        assertEquals(TcecCrypto.hmac("m", A), TcecCrypto.hmac("m", A));
        assertNotEquals(TcecCrypto.hmac("m", A), TcecCrypto.hmac("m", B));
        assertThrows(ProtocolException.class, () -> TcecCrypto.decrypt(first, B));
    }

    @Test void badPaddingDoesNotPoisonTheReusedDecryptor() {
        String good = TcecCrypto.encrypt("hello", A);
        byte[] bytes = Base64.getDecoder().decode(good);
        bytes[bytes.length - 1] ^= 0x55;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        for (int i = 0; i < 10; i++) {
            var failure = assertThrows(ProtocolException.class, () -> TcecCrypto.decrypt(tampered, A));
            assertEquals("Invalid encrypted data", failure.getMessage());
            assertNull(failure.getCause());
            assertEquals("hello", TcecCrypto.decrypt(good, A));
        }
    }

    @Test void signatureFormatIsStrictUppercaseHex() {
        String sig = TcecCrypto.hmac("m", A);
        assertTrue(TcecCrypto.verifies("m", sig, A));
        assertFalse(TcecCrypto.verifies("m", sig.toLowerCase(), A));
        assertFalse(TcecCrypto.verifies("m", sig + "0", A));
        assertFalse(TcecCrypto.verifies("m", sig.substring(1) + "G", A));
        assertFalse(TcecCrypto.verifies("m", null, A));
    }

    @Test void concurrentUseAcrossThreadsAndCredentialsIsCorrect() throws Exception {
        String expectedA = TcecCrypto.encrypt("payload-a", A), expectedB = TcecCrypto.encrypt("payload-b", B);
        String sigA = TcecCrypto.hmac("payload-a", A), sigB = TcecCrypto.hmac("payload-b", B);
        var pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < 64; t++) {
                boolean useA = t % 2 == 0;
                Callable<Boolean> task = () -> {
                    for (int i = 0; i < 2_000; i++) {
                        PartnerCredentials c = useA ? A : B;
                        String text = useA ? "payload-a" : "payload-b";
                        String data = TcecCrypto.encrypt(text, c);
                        if (!data.equals(useA ? expectedA : expectedB)) return false;
                        if (!TcecCrypto.decrypt(data, c).equals(text)) return false;
                        if (!TcecCrypto.hmac(text, c).equals(useA ? sigA : sigB)) return false;
                    }
                    return true;
                };
                results.add(pool.submit(task));
            }
            for (var result : results) assertTrue(result.get());
        } finally { pool.shutdownNow(); }
    }
}
