package io.github.ac1982.tcec;

import io.github.ac1982.tcec.codec.*;
import io.github.ac1982.tcec.model.*;
import io.github.ac1982.tcec.protocol.Endpoints;
import io.github.ac1982.tcec.security.*;
import io.github.ac1982.tcec.wire.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WireAlignmentSecurityTest {
    private final PartnerCredentials keys = new PartnerCredentials("123456789", "synthetic-secret", "1234567890abcdef", "1234567890abcdef", "synthetic-sign");
    private final TcecCodec codec = new TcecCodec(keys);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T04:00:00Z"), ZoneOffset.UTC);

    @Test void responseOperatorIdentityIsIncludedAndValidatedWhenPresent() {
        var response = codec.encodeResponse(new QueryStationsInfoResponse(1, 0, 0, java.util.List.of()));
        assertEquals(keys.operatorId(), response.operatorId());
        assertEquals(1, codec.decodeResponse(WireJson.read(WireJson.write(response), ResponseEnvelope.class), QueryStationsInfoResponse.class).pageNo());
        for (String identity : new String[]{"987654321", "", "12345678", "1234567890", "12345678/"}) {
            var changed = new ResponseEnvelope(response.ret(), response.msg(), response.data(), response.sig(), identity);
            assertEquals(ProtocolException.INVALID_SIGNATURE, assertThrows(ProtocolException.class, () -> codec.decodeResponse(changed, QueryStationsInfoResponse.class)).ret());
        }
    }

    @Test void absentResponseIdentityStillRequiresValidMacAndNeverSelectsCredentials() {
        var response = codec.encodeResponse(new QueryStationsInfoResponse(1, 0, 0, java.util.List.of()));
        assertDoesNotThrow(() -> codec.decodeResponse(new ResponseEnvelope(response.ret(), response.msg(), response.data(), response.sig()), QueryStationsInfoResponse.class));
        String explicitNull = WireJson.write(response).replace("\"OperatorID\":\"123456789\"", "\"OperatorID\":null");
        assertDoesNotThrow(() -> codec.decodeResponse(WireJson.read(explicitNull, ResponseEnvelope.class), QueryStationsInfoResponse.class));
        assertThrows(ProtocolException.class, () -> codec.decodeResponse(new ResponseEnvelope(response.ret(), response.msg(), response.data(), "0".repeat(32), keys.operatorId()), QueryStationsInfoResponse.class));
    }

    @Test void knownRequestMetadataIsInertAtPayloadRootOnly() {
        for (var endpoint : Endpoints.all()) {
            Object request = WireJson.read("{\"serialVersionUID\":897603497102698112}", endpoint.requestType());
            assertFalse(WireJson.write(request).contains("serialVersionUID"), endpoint.path());
        }
        for (String value : new String[]{"\"1\"", "1.0", "true", "null", "{}", "[]", "9223372036854775808"}) {
            assertThrows(ProtocolException.class, () -> WireJson.read("{\"serialVersionUID\":" + value + "}", QueryStationsInfoRequest.class));
        }
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"serialVersionUID\":1,\"Unknown\":1}", QueryStationsInfoRequest.class));
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"serialVersionUID\":1,\"serialVersionUID\":1}", QueryStationsInfoRequest.class));
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"ConnectorStatusInfo\":{\"ConnectorID\":\"001\",\"Status\":0,\"serialVersionUID\":1}}", NotificationStationStatusRequest.class));
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"serialVersionUID\":1}", QueryTokenResponse.class));
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"serialVersionUID\":1}", Object.class));
    }

    @Test void sameSecondAndSequencePermitDistinctAuthenticatedMessagesButRejectExactReplay() {
        var verifier = new RequestVerifier(clock, RequestVerifier.CHINA_ZONE, Duration.ofMinutes(5), new InMemoryReplayStore(clock, 100));
        var token = codec.encodeRequest(new QueryTokenRequest("123456789", "synthetic-secret"), "20261009120000", "0001");
        var business = codec.encodeRequest(new QueryStationsInfoRequest(null, 1, 10), "20261009120000", "0001");
        codec.verifyRequest(token); verifier.verify(token);
        codec.verifyRequest(business); verifier.verify(business);
        assertThrows(ProtocolException.class, () -> verifier.verify(token));
        assertThrows(ProtocolException.class, () -> verifier.verify(business));
    }

    @Test void exactAuthenticatedReplayIsAtomicAcrossConcurrentReceivers() throws Exception {
        var verifier = new RequestVerifier(clock, RequestVerifier.CHINA_ZONE, Duration.ofMinutes(5), new InMemoryReplayStore(clock, 100));
        var request = codec.encodeRequest(new QueryStationsInfoRequest(), "20261009120000", "0001");
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var calls = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 32; i++) calls.add(workers.submit(() -> {
                try { codec.verifyRequest(request); verifier.verify(request); return true; }
                catch (ProtocolException rejected) { return false; }
            }));
            int accepted = 0; for (var call : calls) if (call.get()) accepted++;
            assertEquals(1, accepted);
        }
    }

    @Test void documentedDefaultsDistinguishAbsentAndExplicitNullAndApplyInsideLists() {
        var absent = WireJson.read("{}", QueryStationsInfoRequest.class);
        assertEquals(1, absent.pageNo()); assertEquals(10, absent.pageSize());
        var explicit = WireJson.read("{\"PageNo\":null,\"PageSize\":null}", QueryStationsInfoRequest.class);
        assertNull(explicit.pageNo()); assertNull(explicit.pageSize());
        var nested = WireJson.read("{\"StationInfos\":[{\"EquipmentInfos\":[{}]}]}", QueryStationsInfoResponse.class);
        assertEquals(0, nested.stationInfos().getFirst().equipmentInfos().getFirst().equipmentLng().signum());
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"PageNo\":1.5}", QueryStationsInfoRequest.class));
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"PageNo\":\"1\"}", QueryStationsInfoRequest.class));
    }
}
