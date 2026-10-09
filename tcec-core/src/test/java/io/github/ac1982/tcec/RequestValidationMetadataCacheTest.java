package io.github.ac1982.tcec;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ac1982.tcec.codec.WireJson;
import io.github.ac1982.tcec.model.*;
import io.github.ac1982.tcec.protocol.Endpoints;
import io.github.ac1982.tcec.protocol.RequestValidation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RequestValidationMetadataCacheTest {
    private record Contract(Class<?> type, String required, String sequences) {
        @Override public String toString() { return type.getSimpleName(); }
    }
    static Stream<Contract> contracts() {
        return Stream.of(
            new Contract(QueryTokenRequest.class, "OperatorID OperatorSecret", ""),
            new Contract(QueryEquipAuthRequest.class, "EquipAuthSeq ConnectorID", "EquipAuthSeq"),
            new Contract(QueryEquipBusinessPolicyRequest.class, "EquipBizSeq ConnectorID", "EquipBizSeq"),
            new Contract(QueryStartChargeRequest.class, "StartChargeSeq ConnectorID QRCode", "StartChargeSeq"),
            new Contract(QueryStopChargeRequest.class, "StartChargeSeq ConnectorID", "StartChargeSeq"),
            new Contract(QueryEquipChargeStatusRequest.class, "StartChargeSeq", "StartChargeSeq"),
            new Contract(NotificationStartChargeResultRequest.class, "StartChargeSeq StartChargeSeqStat ConnectorID StartTime", "StartChargeSeq"),
            new Contract(NotificationStopChargeResultRequest.class, "StartChargeSeq StartChargeSeqStat ConnectorID SuccStat FailReason", ""),
            new Contract(ChargingSnapshot.class, "StartChargeSeq StartChargeSeqStat ConnectorID ConnectorStatus CurrentA VoltageA Soc StartTime EndTime TotalPower", "StartChargeSeq"),
            new Contract(ChargeOrderInfo.class, "StartChargeSeq", "StartChargeSeq"),
            new Contract(CheckChargeOrdersRequest.class, "CheckOrderSeq StartTime EndTime OrderCount TotalOrderPower TotalOrderMoney ChargeOrders", "CheckOrderSeq"),
            new Contract(QueryStationStatusRequest.class, "StationIDs", ""),
            new Contract(ModifyParkingCarNumberRequest.class, "StartChargeSeq CarNumber", ""),
            new Contract(NotificationUserAuthRequest.class, "Mobile OutUserId", ""),
            new Contract(QueryConfirmLinkRequest.class, "Mobile OutUserId QRCode", ""),
            new Contract(QueryOpenLinkRequest.class, "Mobile OutUserId", ""),
            new Contract(QueryOrderDetailLinkRequest.class, "Mobile OutUserId StartChargeSeq", ""),
            new Contract(QueryUserOrderRequest.class, "OutUserId", ""),
            new Contract(QueryParkingFreeInfoRequest.class, "StartChargeSeq CarNumber", ""),
            new Contract(QueryParkingFreeWaveRequest.class, "StartChargeSeq CarNumber", ""),
            new Contract(QueryParkingStationRequest.class, "StationID", ""),
            new Contract(QueryGroundLocksRequest.class, "StationID", ""),
            new Contract(QueryDropLockRequest.class, "StationID LockNum Lat Lng", ""),
            new Contract(QueryDropLockResultRequest.class, "StationID LockNum", ""),
            new Contract(QueryGroundLockAuthRequest.class, "Lat Lng StationIDs", "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contracts")
    void everyCachedRuleStillChecksRequiredValuesAndSequences(Contract contract) throws Exception {
        ObjectNode valid = example(contract.type());
        assertDoesNotThrow(() -> RequestValidation.validate(WireJson.read(valid.toString(), contract.type())));
        for (String name : contract.required().split(" ")) {
            ObjectNode changed = valid.deepCopy();
            changed.putNull(name);
            assertInvalid(WireJson.read(changed.toString(), contract.type()));
            if (valid.get(name).isString()) for (String blank : List.of("", " ", "\t\r\n")) {
                changed.put(name, blank);
                assertInvalid(WireJson.read(changed.toString(), contract.type()));
            }
        }
        if (!contract.sequences().isEmpty()) for (String name : contract.sequences().split(" ")) {
            for (int length : new int[]{1, 26, 28}) {
                ObjectNode changed = valid.deepCopy();
                changed.put(name, "a".repeat(length));
                assertInvalid(WireJson.read(changed.toString(), contract.type()));
            }
            ObjectNode changed = valid.deepCopy();
            changed.put(name, "a".repeat(27));
            assertDoesNotThrow(() -> RequestValidation.validate(WireJson.read(changed.toString(), contract.type())));
        }
    }

    @ParameterizedTest(name = "concurrent {0}")
    @MethodSource("contracts")
    void cachedAccessorsNeverShareRequestValuesAcrossThreads(Contract contract) throws Exception {
        ObjectNode valid = example(contract.type());
        ObjectNode invalid = valid.deepCopy();
        invalid.putNull(contract.required().split(" ")[0]);
        Object accepted = WireJson.read(valid.toString(), contract.type());
        Object rejected = WireJson.read(invalid.toString(), contract.type());
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> work = new ArrayList<>();
            for (int i = 0; i < 8; i++) work.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                for (int repeat = 0; repeat < 20; repeat++) {
                    assertDoesNotThrow(() -> RequestValidation.validate(accepted));
                    assertInvalid(rejected);
                    assertDoesNotThrow(() -> RequestValidation.validate(accepted));
                }
                return null;
            }));
            try { assertTrue(ready.await(10, TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            for (Future<?> task : work) task.get(30, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50, 51, 100})
    void stationAndGroundLockListBoundsRemainDistinct(int size) {
        var ids = Collections.nCopies(size, "001");
        var station = new QueryStationStatusRequest(ids);
        var locks = new QueryGroundLockAuthRequest(ids, BigDecimal.ZERO, BigDecimal.ZERO);
        if (size == 0 || size > 50) assertInvalid(station);
        else assertDoesNotThrow(() -> RequestValidation.validate(station));
        if (size == 0) assertInvalid(locks);
        else assertDoesNotThrow(() -> RequestValidation.validate(locks));
    }

    public record CustomRequest(@JsonProperty("Value") String value) {
        @Override public String value() { throw new AssertionError("A custom endpoint owns its own validation"); }
    }
    @Test void nullAndCustomRequestsKeepTheirOriginalBoundaries() {
        assertInvalid(null);
        assertDoesNotThrow(() -> RequestValidation.validate(new CustomRequest(null)));
        assertDoesNotThrow(() -> RequestValidation.validate(new Object()));
        assertDoesNotThrow(() -> RequestValidation.validate(new QueryStationsInfoRequest()));
        assertDoesNotThrow(() -> RequestValidation.validate(new QueryStationStatsRequest()));
        assertDoesNotThrow(() -> RequestValidation.validate(new NotificationStationStatusRequest(null)));
        assertDoesNotThrow(() -> RequestValidation.validate(new NotificationUserOrderInfoRequest()));
    }

    private static ObjectNode example(Class<?> type) throws Exception {
        String endpoint = Endpoints.all().stream().filter(value -> value.requestType() == type).findFirst().orElseThrow().path();
        try (var input = RequestValidationMetadataCacheTest.class.getResourceAsStream("/endpoint-examples.json")) {
            assertNotNull(input);
            for (JsonNode item : JsonMapper.builder().build().readTree(input))
                if (endpoint.equals(item.get("endpoint").asText())) return (ObjectNode) item.get("request");
        }
        throw new AssertionError("Missing independent request fixture: " + endpoint);
    }
    private static void assertInvalid(Object request) {
        ProtocolException error = assertThrows(ProtocolException.class, () -> RequestValidation.validate(request));
        assertEquals(ProtocolException.INVALID_PAYLOAD, error.ret());
        assertEquals("Invalid business request", error.getMessage());
        assertNull(error.getCause());
    }
}
