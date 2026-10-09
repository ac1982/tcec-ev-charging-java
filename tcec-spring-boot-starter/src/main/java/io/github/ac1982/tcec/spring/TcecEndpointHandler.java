package io.github.ac1982.tcec.spring;

import io.github.ac1982.tcec.protocol.Endpoint;
import java.util.Objects;
import java.util.function.BiFunction;

/** Application business behavior. Implement idempotency for charging and order operations. */
public interface TcecEndpointHandler<Q, R> {
    Endpoint<Q, R> endpoint();
    R handle(Q request, RequestContext context);

    static <Q, R> TcecEndpointHandler<Q, R> of(Endpoint<Q, R> endpoint, BiFunction<Q, RequestContext, R> handler) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(handler, "handler");
        return new TcecEndpointHandler<>() {
            @Override public Endpoint<Q, R> endpoint() { return endpoint; }
            @Override public R handle(Q request, RequestContext context) { return handler.apply(request, context); }
        };
    }
}
