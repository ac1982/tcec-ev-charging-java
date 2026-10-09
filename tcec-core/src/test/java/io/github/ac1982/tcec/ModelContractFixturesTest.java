package io.github.ac1982.tcec;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ac1982.tcec.codec.WireJson;
import io.github.ac1982.tcec.model.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Synthetic protocol-contract examples with fixed expected values.
 * The expected JSON is a fixed resource: tests do not generate expectations with WireJson.
 */
class ModelContractFixturesTest {
    private static final String SEQ = "123456789202610090000000001";
    private static final String CONNECTOR_ID = "00000000000000000000000001";
    private static final String START = "2026-10-09 12:00:00";
    private static final String END = "2026-10-09 12:30:00";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Example(String name, Object value) {
        @Override public String toString() { return name; }
    }

    static Stream<Example> maximalExamples() {
        var connector = new ConnectorInfo(CONNECTOR_ID, "测试充电接口 01", 4,
                750, 200, 125, decimal("60.0"), "B2-0001", 2);
        var equipment = new EquipmentInfo("00000000000000000000001", "987654321",
                "示例设备制造商", "TEST-DC-60", "2020-02-29", 1, List.of(connector),
                decimal("116.123456"), decimal("39.123456"), decimal("60.0"), "测试直流设备 01");
        var station = new StationInfo("00000000000000000001", "123456789", "123456789",
                "示例充电站", "CN", "110101", "公开测试地址", "01000000000", "4000000000",
                1, 50, 2, decimal("116.123456"), decimal("39.123456"), "地下二层，沿测试标识进入", 4,
                List.of("https://example.invalid/station/front.png", "https://example.invalid/station/map.png"),
                "国标车辆", "B2 层", "全天", "以测试费率表为准", "每千瓦时 0.5000 元", "测试停车说明",
                "1,2,3", 0, "合成数据：引号 \" 和反斜杠 \\，emoji 🔌", List.of(equipment), 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        var detail = new ChargeDetail(START, END, decimal("0.1234"), decimal("0.5"),
                decimal("2.25"), decimal("0.28"), decimal("1.13"));
        var snapshot = new ChargingSnapshot(SEQ, 2, CONNECTOR_ID, 3, decimal("10.1"), decimal("0"),
                decimal("10.3"), decimal("220"), decimal("221.2"), decimal("222.3"), decimal("55"),
                START, END, decimal("2.25"), decimal("0.28"), decimal("1.13"), decimal("1.41"),
                1, List.of(detail));
        var disputes = new CheckChargeOrdersResponse(SEQ, "2026-10-01 00:00:00", "2026-10-09 23:59:59",
                2, decimal("3.5"), decimal("2.25"), List.of(
                new DisputeOrder(SEQ, decimal("2.25"), decimal("1.41"), 1),
                new DisputeOrder("123456789202610090000000002", decimal("1.25"), decimal("0.84"), 99)));
        return Stream.of(
                new Example("station-query-with-timestamp", new QueryStationsInfoRequest("2026-10-08 23:59:59", 2, 50)),
                new Example("station-with-all-optional-fields", new QueryStationsInfoResponse(1, 1, 1, List.of(station))),
                new Example("start-notification-with-ident-code", new NotificationStartChargeResultRequest(
                        SEQ, 2, CONNECTOR_ID, START, "public-test-ident-001")),
                new Example("charging-snapshot-with-all-phases", snapshot),
                new Example("reconciliation-with-nonempty-disputes", disputes),
                new Example("operator-with-all-optional-fields", new OperatorInfo("123456789", "示例运营商",
                        "4000000000", "4000000001", "公开测试地址", "合成运营商信息，仅用于协议测试")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maximalExamples")
    void independentlyConstructedModelsMatchFixedJsonFieldNamesTypesAndValues(Example example) throws Exception {
        JsonNode expected;
        try (var input = getClass().getResourceAsStream("/maximal-model-examples.json")) {
            assertNotNull(input);
            expected = JSON.readTree(input).get(example.name());
        }
        assertNotNull(expected, example.name());
        assertEquals(expected, JSON.readTree(WireJson.write(example.value())),
                "Exact field names, JSON types, array order and values must match the independent fixture");
        assertEquals(example.value(), WireJson.read(expected.toString(), example.value().getClass()),
                "Fixed wire JSON must reconstruct the independently constructed model");
        assertEveryModeledFieldIsPopulated(example.value(), expected);
    }

    /** Guards against accidentally turning a maximal fixture into another partial happy path. */
    private static void assertEveryModeledFieldIsPopulated(Object value, JsonNode expected) throws Exception {
        if (!value.getClass().isRecord()) return;
        int fields = 0;
        for (var component : value.getClass().getRecordComponents()) {
            JsonProperty property = value.getClass().getDeclaredField(component.getName()).getAnnotation(JsonProperty.class);
            assertNotNull(property, component.getName());
            String name = property.value();
            assertTrue(expected.has(name), () -> value.getClass().getSimpleName() + "." + name);
            JsonNode child = expected.get(name);
            assertFalse(child.isNull(), name);
            Object actual = component.getAccessor().invoke(value);
            assertNotNull(actual, name);
            if (actual instanceof List<?> list) {
                assertTrue(child.isArray(), name);
                assertFalse(list.isEmpty(), name);
                for (int i = 0; i < list.size(); i++) assertEveryModeledFieldIsPopulated(list.get(i), child.get(i));
            } else {
                assertEveryModeledFieldIsPopulated(actual, child);
            }
            fields++;
        }
        assertEquals(fields, expected.size(), value.getClass().getSimpleName());
    }

    @Test void optionalNullsStayAbsentWhileExplicitZerosStayNumbers() {
        var omitted = new QueryStationsInfoRequest(null, null, null);
        assertEquals(new QueryStationsInfoRequest(null, 1, 10), WireJson.read("{}", QueryStationsInfoRequest.class));
        assertEquals(omitted, WireJson.read("{\"LastQueryTime\":null,\"PageNo\":null,\"PageSize\":null}",
                QueryStationsInfoRequest.class));
        assertEquals("{}", WireJson.write(omitted));
        var status = new ConnectorStatusInfo(CONNECTOR_ID, 0, null, null);
        JsonNode node = JSON.readTree(WireJson.write(status));
        assertEquals(JSON.readTree("{\"ConnectorID\":\"" + CONNECTOR_ID + "\",\"Status\":0}"), node);
        assertTrue(node.get("ConnectorID").isString());
        assertTrue(node.get("Status").isIntegralNumber());
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
}
