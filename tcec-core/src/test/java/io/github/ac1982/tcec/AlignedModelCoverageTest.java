package io.github.ac1982.tcec;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ac1982.tcec.codec.WireJson;
import io.github.ac1982.tcec.model.*;
import io.github.ac1982.tcec.protocol.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed synthetic values verify the declared public field contract. */
class AlignedModelCoverageTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static JsonNode resource(String name) throws Exception {
        try (var stream = AlignedModelCoverageTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(stream, name);
            return JSON.readTree(stream);
        }
    }
    static Stream<JsonNode> schemas() throws Exception {
        return StreamSupport.stream(resource("model-field-contract.json").spliterator(), false);
    }
    static Stream<JsonNode> examples() throws Exception {
        return StreamSupport.stream(resource("aligned-model-examples.json").spliterator(), false);
    }
    private static Class<?> model(JsonNode node) throws Exception {
        return Class.forName("io.github.ac1982.tcec.model." + node.get("model").asText());
    }
    private static String typeName(Type type) {
        if (type instanceof Class<?> cls) return cls.getSimpleName();
        var generic = (ParameterizedType) type;
        return typeName(generic.getRawType()) + "<" + typeName(generic.getActualTypeArguments()[0]) + ">";
    }
    @ParameterizedTest @MethodSource("schemas")
    void everyReviewedFieldHasExactWireNameTypeAndDefault(JsonNode schema) throws Exception {
        var type = model(schema);
        var fields = new LinkedHashMap<String, java.lang.reflect.Field>();
        for (var component : type.getRecordComponents()) {
            var field = type.getDeclaredField(component.getName());
            fields.put(field.getAnnotation(JsonProperty.class).value(), field);
        }
        assertEquals(schema.get("fields").size(), fields.size(), type.getSimpleName());
        for (var expected : schema.get("fields")) {
            var field = fields.remove(expected.get("wire").asText());
            assertNotNull(field, expected.toString());
            assertEquals(expected.get("type").asText(), typeName(field.getGenericType()));
            String expectedDefault = expected.get("default").isNull() ? "" : expected.get("default").asText();
            assertEquals(expectedDefault, field.getAnnotation(JsonProperty.class).defaultValue());
        }
        assertTrue(fields.isEmpty());
    }
    @ParameterizedTest @MethodSource("examples")
    void everyModelRoundTripsAllPopulatedFields(JsonNode example) throws Exception {
        Class<?> type = model(example);
        JsonNode expected = example.get("value");
        Object result = WireJson.read(expected.toString(), type);
        assertEquals(expected, JSON.readTree(WireJson.write(result)), type.getSimpleName());
        assertEquals(result, WireJson.read(WireJson.write(result), type));
        assertEquals(type.getRecordComponents().length, expected.size());
        for (JsonNode value : expected) assertFalse(value.isNull());
    }
    @ParameterizedTest @MethodSource("schemas")
    void defaultsApplyOnlyWhenMissingAndExplicitNullsRemainNull(JsonNode schema) throws Exception {
        Class<?> type = model(schema);
        Object empty = type.getConstructor().newInstance();
        assertEquals(empty, WireJson.read("{}", type), type.getSimpleName());
        ObjectNode explicitNulls = JSON.createObjectNode();
        for (JsonNode field : schema.get("fields")) explicitNulls.putNull(field.get("wire").asText());
        Object nullValue = WireJson.read(explicitNulls.toString(), type);
        for (var component : type.getRecordComponents()) assertNull(component.getAccessor().invoke(nullValue));
        assertEquals("{}", WireJson.write(nullValue));
    }
    @Test void inventoryCountsExcludeSevenRetiredSpellingsAndIncludeAllActualBusinessFields() throws Exception {
        var schemas = resource("model-field-contract.json");
        assertEquals(74, schemas.size());
        assertEquals(329, StreamSupport.stream(schemas.spliterator(), false).mapToInt(x -> x.get("fields").size()).sum());
        assertEquals(15, Endpoints.standard().size());
        assertEquals(14, Endpoints.extensions().size());
        assertEquals(29, Endpoints.all().size());
        assertEquals(29, Endpoints.all().stream().map(Endpoint::path).distinct().count());
        assertEquals(29, Endpoints.all().stream().map(Endpoint::outboundPath).distinct().count());
        assertEquals(29, resource("endpoint-examples.json").size());
    }
    @Test void chargeStatusQueryAndNotificationKeepDistinctElectricityMoneySpellings() {
        var query = WireJson.read("{\"ElectMoney\":1.25,\"ServiceMoney\":0.5}", QueryEquipChargeStatusResponse.class);
        var notification = WireJson.read("{\"ElecMoney\":1.25,\"ServiceMoney\":0.5}", ChargingSnapshot.class);
        assertEquals(new BigDecimal("1.25"), query.electMoney());
        assertEquals(new BigDecimal("1.25"), notification.elecMoney());
        assertFalse(WireJson.write(query).contains("\"ElecMoney\""));
        assertFalse(WireJson.write(notification).contains("\"ElectMoney\""));
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"ElecMoney\":1}", QueryEquipChargeStatusResponse.class));
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"ElectMoney\":1}", ChargingSnapshot.class));
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"SevicePrice\":1}", PolicyInfo.class));
    }
    @Test void endpointPathsCorrectKnownMetadataDefectsWithoutAddingAnAdapter() {
        assertEquals("/notification_stationStatus", Endpoints.NOTIFICATION_STATION_STATUS.outboundPath());
        assertEquals(QueryParkingFreeInfoResponse.class, Endpoints.QUERY_PARKING_FREE_INFO.responseType());
        assertEquals("/evcs/sdk/query_token", Endpoints.QUERY_TOKEN.outboundPath());
        assertEquals("/evcs/sdk/query_drop_lock", Endpoints.QUERY_DROP_LOCK.outboundPath());
        assertEquals("/evcs/v1/query_open_link", Endpoints.QUERY_OPEN_LINK.outboundPath());
        assertEquals("custom_query", new Endpoint<>("custom_query", QueryTokenRequest.class, QueryTokenResponse.class, false).outboundPath());
        for (String bad : List.of("//evil.invalid", "https://evil.invalid", "/a/../b", "/a?b=1", "/a#x", "/a%2fb"))
            assertThrows(IllegalArgumentException.class, () -> new Endpoint<>("name", Object.class, Object.class, true, bad));
    }
    @Test void listDefensiveCopiesPreserveNullElementsWithoutInventingBusinessRules() {
        var input = new ArrayList<String>(Arrays.asList("0001", null));
        var request = new QueryStationStatusRequest(input);
        input.clear();
        assertEquals(Arrays.asList("0001", null), request.stationIds());
        assertThrows(UnsupportedOperationException.class, () -> request.stationIds().clear());
        assertEquals(request, WireJson.read("{\"StationIDs\":[\"0001\",null]}", QueryStationStatusRequest.class));
    }
}
