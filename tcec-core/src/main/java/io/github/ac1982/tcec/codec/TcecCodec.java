package io.github.ac1982.tcec.codec;
import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.protocol.RequestValidation;
import io.github.ac1982.tcec.security.PartnerCredentials;
import io.github.ac1982.tcec.security.TcecCrypto;
import io.github.ac1982.tcec.wire.RequestEnvelope;
import io.github.ac1982.tcec.wire.ResponseEnvelope;
import java.util.Objects;
/** Stateless, thread-safe per-partner codec. Signature verification always precedes decryption. */
public final class TcecCodec {
    private final PartnerCredentials credentials;
    public TcecCodec(PartnerCredentials credentials) { this.credentials = Objects.requireNonNull(credentials); }
    public RequestEnvelope encodeRequest(Object payload, String timeStamp, String seq) {
        if (timeStamp == null || !timeStamp.matches("[0-9]{14}") || seq == null || !seq.matches("[0-9]{4}")) throw new IllegalArgumentException("Invalid timestamp/sequence");
        String data = TcecCrypto.encrypt(WireJson.write(payload), credentials);
        String input = credentials.operatorId() + data + timeStamp + seq;
        return new RequestEnvelope(credentials.operatorId(), data, timeStamp, seq, TcecCrypto.hmac(input, credentials));
    }
    public void verifyRequest(RequestEnvelope envelope) {
        if (envelope == null || !credentials.operatorId().equals(envelope.operatorId()) || envelope.data() == null || envelope.timeStamp() == null || envelope.seq() == null
            || !TcecCrypto.verifies(envelope.operatorId() + envelope.data() + envelope.timeStamp() + envelope.seq(), envelope.sig(), credentials)) {
            throw new ProtocolException(ProtocolException.INVALID_SIGNATURE, "Request authentication failed");
        }
    }
    public <T> T decodeRequest(RequestEnvelope envelope, Class<T> type) {
        verifyRequest(envelope);
        T request = payload(envelope.data(), type);
        RequestValidation.validate(request);
        return request;
    }
    private <T> T payload(String data, Class<T> type) {
        try { return WireJson.read(TcecCrypto.decrypt(data, credentials), type); }
        catch (ProtocolException e) { throw new ProtocolException(ProtocolException.INVALID_PAYLOAD, "Invalid business payload"); }
    }
    public ResponseEnvelope encodeResponse(Object payload) { return response(0, "", TcecCrypto.encrypt(WireJson.write(payload), credentials)); }
    public ResponseEnvelope encodeError(int ret, String message) {
        if (ret == 0 || message == null) throw new IllegalArgumentException("Error code and message required");
        return response(ret, message, "");
    }
    private ResponseEnvelope response(int ret, String message, String data) { return new ResponseEnvelope(ret, message, data, TcecCrypto.hmac(Integer.toString(ret) + message + data, credentials), credentials.operatorId()); }
    /** OperatorID is optional, compared with configured identity when present, and never trusted as authenticated: the protocol response MAC does not cover it. */
    public <T> T decodeResponse(ResponseEnvelope envelope, Class<T> type) {
        if (envelope == null || (envelope.operatorId() != null && !credentials.operatorId().equals(envelope.operatorId())) || envelope.msg() == null || envelope.data() == null || !TcecCrypto.verifies(Integer.toString(envelope.ret()) + envelope.msg() + envelope.data(), envelope.sig(), credentials)) throw new ProtocolException(ProtocolException.INVALID_SIGNATURE, "Response authentication failed");
        if (envelope.ret() != 0) throw new ProtocolException(envelope.ret(), "Remote protocol error");
        return payload(envelope.data(), type);
    }
}
