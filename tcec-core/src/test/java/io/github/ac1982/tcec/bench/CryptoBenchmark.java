package io.github.ac1982.tcec.bench;

import io.github.ac1982.tcec.security.PartnerCredentials;
import io.github.ac1982.tcec.security.TcecCrypto;
import java.util.concurrent.TimeUnit;

/**
 * Simple single-thread throughput check for the per-message crypto path (not a JUnit test).
 * Run: mvn -q -pl tcec-core test-compile && java -cp tcec-core/target/classes:tcec-core/target/test-classes:$(deps)
 *   io.github.ac1982.tcec.bench.CryptoBenchmark
 */
public final class CryptoBenchmark {
    public static void main(String[] args) {
        var credentials = new PartnerCredentials("123456789", "operator-secret", "1234567890abcdef", "1234567890abcdef", "sig-secret-0001");
        String payload = "{\"StationID\":\"111\",\"ConnectorID\":\"222\",\"StartChargeSeq\":\"MA6DBRAF3123456789012345678\",\"Soc\":42.5,\"TotalPower\":12.3456}".repeat(2);
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        for (int round = 0; round < 5; round++) {
            long ops = run(credentials, payload, threads, 1_000);
            System.out.printf("round %d: %,d encrypt+sign+verify+decrypt ops/s (%d thread%s)%n", round, ops, threads, threads == 1 ? "" : "s");
        }
    }

    private static long run(PartnerCredentials credentials, String payload, int threads, long millis) {
        var counters = new long[threads];
        var workers = new Thread[threads];
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        for (int t = 0; t < threads; t++) {
            int index = t;
            workers[t] = new Thread(() -> {
                long n = 0;
                while (System.nanoTime() < deadline) {
                    String data = TcecCrypto.encrypt(payload, credentials);
                    String sig = TcecCrypto.hmac("123456789" + data + "202610091600000001", credentials);
                    if (!TcecCrypto.verifies("123456789" + data + "202610091600000001", sig, credentials)) throw new AssertionError();
                    if (TcecCrypto.decrypt(data, credentials).length() != payload.length()) throw new AssertionError();
                    n++;
                }
                counters[index] = n;
            });
            workers[t].start();
        }
        for (Thread worker : workers) {
            try { worker.join(); } catch (InterruptedException e) { throw new IllegalStateException(e); }
        }
        long total = 0;
        for (long c : counters) total += c;
        return total * 1000 / millis;
    }
}
