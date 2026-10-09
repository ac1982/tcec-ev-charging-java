package io.github.ac1982.tcec;
import io.github.ac1982.tcec.codec.*;
import io.github.ac1982.tcec.model.*;
import io.github.ac1982.tcec.security.*;
import io.github.ac1982.tcec.wire.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class PublishedCryptoVectorsTest {
    static final PartnerCredentials KEYS = new PartnerCredentials("123456789", "1234567890abcdef", "1234567890abcdef", "1234567890abcdef", "1234567890abcdef");
    @Test void publishedRequestAndResponseVerifyAndDecryptByteForByte() throws Exception {
        String json = new String(getClass().getResourceAsStream("/published-crypto-vectors.json").readAllBytes(), StandardCharsets.UTF_8);
        JsonNode vectors = JsonMapper.builder().build().readTree(json);
        for (JsonNode vector : vectors) {
            JsonNode e = vector.get("envelope");
            String signingInput;
            if (e.has("OperatorID")) signingInput = e.get("OperatorID").asText() + e.get("Data").asText() + e.get("TimeStamp").asText() + e.get("Seq").asText();
            else signingInput = e.get("Ret").asInt() + e.get("Msg").asText() + e.get("Data").asText();
            assertEquals(e.get("Sig").asText(), TcecCrypto.hmac(signingInput, KEYS));
            if (vector.has("plaintext")) {
                assertEquals(vector.get("plaintext").asText(), TcecCrypto.decrypt(e.get("Data").asText(), KEYS));
                assertEquals(e.get("Data").asText(), TcecCrypto.encrypt(vector.get("plaintext").asText(), KEYS));
            }
        }
        TcecCodec codec = new TcecCodec(KEYS);
        QueryTokenRequest request = codec.decodeRequest(WireJson.read(vectors.get(0).get("envelope").toString(), RequestEnvelope.class), QueryTokenRequest.class);
        assertEquals("123456789", request.operatorId());
        QueryTokenResponse response = codec.decodeResponse(WireJson.read(vectors.get(1).get("envelope").toString(), ResponseEnvelope.class), QueryTokenResponse.class);
        assertEquals(7200, response.tokenAvailableTime());
        assertEquals(0, response.succStat());
    }
    @Test void unicodeAndBlockPaddingRoundTrip() {
        for (String value : new String[]{"", "充电站🔌", "0123456789abcdef", "a".repeat(10000)})
            assertEquals(value, TcecCrypto.decrypt(TcecCrypto.encrypt(value, KEYS), KEYS));
    }
    @Test void signatureIsCheckedBeforeCiphertextOrRemoteError() {
        TcecCodec codec = new TcecCodec(KEYS);
        ProtocolException e = assertThrows(ProtocolException.class, () -> codec.decodeResponse(new ResponseEnvelope(500, "SECRET", "not-base64", "A".repeat(32)), QueryTokenResponse.class));
        assertEquals(4001, e.ret()); assertFalse(e.getMessage().contains("SECRET"));
    }
    @Test void modifiedRetMessageDataAndSignatureAreRejected() {
        TcecCodec codec = new TcecCodec(KEYS);
        ResponseEnvelope good = codec.encodeResponse(new QueryTokenResponse("123456789",0,"public-example-token",7200,0));
        ResponseEnvelope[] changed = {
            new ResponseEnvelope(1,good.msg(),good.data(),good.sig()),
            new ResponseEnvelope(0,"changed",good.data(),good.sig()),
            new ResponseEnvelope(0,good.msg(),good.data().substring(1),good.sig()),
            new ResponseEnvelope(0,good.msg(),good.data(),good.sig().toLowerCase())};
        for (var e : changed) assertEquals(4001, assertThrows(ProtocolException.class, () -> codec.decodeResponse(e, QueryTokenResponse.class)).ret());
    }
    @Test void malformedCiphertextAndInvalidKeysFailClosed() {
        for (String encoded : new String[]{"", "%%%%", "YQ==", "AAAA", " AAAAAAAAAAAAAAAAAAAAAA=="})
            assertThrows(ProtocolException.class, () -> TcecCrypto.decrypt(encoded, KEYS));
        assertThrows(IllegalArgumentException.class, () -> new PartnerCredentials("123456789","secret","123","1234567890abcdef","sig"));
        assertThrows(IllegalArgumentException.class, () -> new PartnerCredentials("123456789","secret","1234567890abcdef","123","sig"));
        assertFalse(KEYS.toString().contains("abcdef"));
        byte[] copy = KEYS.dataSecret(); copy[0] = 0; assertEquals('1', KEYS.dataSecret()[0]);
    }
    @Test void operatorMismatchAndSignedInvalidPayloadAreRejected() {
        TcecCodec codec = new TcecCodec(KEYS);
        RequestEnvelope original = codec.encodeRequest(new QueryTokenRequest("123456789","secret"),"20261009120000","0001");
        var other = new RequestEnvelope("987654321",original.data(),original.timeStamp(),original.seq(),original.sig());
        assertEquals(4001,assertThrows(ProtocolException.class, () -> codec.decodeRequest(other,QueryTokenRequest.class)).ret());
        RequestEnvelope bad = codec.encodeRequest(java.util.Map.of("OperatorID","123456789"),"20261009120000","0001");
        assertEquals(4004,assertThrows(ProtocolException.class, () -> codec.decodeRequest(bad,QueryTokenRequest.class)).ret());
    }
}
