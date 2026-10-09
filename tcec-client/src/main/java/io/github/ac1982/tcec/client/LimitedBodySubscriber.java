package io.github.ac1982.tcec.client;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bounds buffered response bytes before allocating a complete response body. */
final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;

    LimitedBodySubscriber(int limit) { this.limit = limit; }

    @Override public CompletionStage<byte[]> getBody() { return result; }
    @Override public void onSubscribe(Flow.Subscription subscription) {
        if (this.subscription != null) { subscription.cancel(); return; }
        this.subscription = subscription;
        subscription.request(1);
    }
    @Override public void onNext(List<ByteBuffer> buffers) {
        if (result.isDone()) return;
        for (ByteBuffer buffer : buffers) {
            if (buffer.remaining() > limit - bytes.size()) {
                subscription.cancel();
                result.completeExceptionally(new TransportException("Response exceeds configured size limit"));
                return;
            }
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }
    @Override public void onError(Throwable error) { result.completeExceptionally(error); }
    @Override public void onComplete() { result.complete(bytes.toByteArray()); }
}
