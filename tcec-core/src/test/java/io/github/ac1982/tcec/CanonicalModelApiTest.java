package io.github.ac1982.tcec;

import io.github.ac1982.tcec.model.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalModelApiTest {
    @Test void serviceFeeAccessorsUseCanonicalSpelling() {
        assertEquals(new BigDecimal("0.0"), new ChargeDetail().servicePrice());
        assertEquals(new BigDecimal("0.0"), new ChargeDetail().detailServiceMoney());
        assertEquals(new BigDecimal("0.0"), new PolicyInfo().servicePrice());
        assertEquals(new BigDecimal("0.0"), new ChargeOrderInfo().totalServiceMoney());
        assertEquals(new BigDecimal("0.0"), new ChargingSnapshot().serviceMoney());
        for (Class<?> type : List.of(ChargeDetail.class, PolicyInfo.class, ChargeOrderInfo.class, ChargingSnapshot.class)) {
            assertTrue(Arrays.stream(type.getDeclaredMethods()).noneMatch(method ->
                    method.getName().contains("Sevice") || method.getName().contains("sevice")), type.getSimpleName());
        }
    }

    @Test void expandedModelsExposeCompleteAndDefaultConstructorsOnly() {
        for (Class<?> type : List.of(StationInfo.class, StationStatusInfo.class,
                QueryEquipAuthResponse.class, QueryStationStatusResponse.class)) {
            assertEquals(2, type.getConstructors().length, type.getSimpleName());
            assertTrue(Arrays.stream(type.getConstructors()).anyMatch(constructor -> constructor.getParameterCount() == 0));
            assertTrue(Arrays.stream(type.getConstructors()).anyMatch(constructor ->
                    constructor.getParameterCount() == type.getRecordComponents().length));
        }
        assertEquals("full-id", new StationStatusInfo("full-id", "station-id", List.of()).fullStationId());
        assertEquals("123456789", new QueryEquipAuthResponse("123456789", "sequence", "connector", 0, 0).operatorId());
        assertEquals("1", new QueryStationStatusResponse("1", List.of()).total());
    }
}
