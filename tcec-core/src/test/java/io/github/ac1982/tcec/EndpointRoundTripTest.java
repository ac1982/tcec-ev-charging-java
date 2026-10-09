package io.github.ac1982.tcec;
import io.github.ac1982.tcec.codec.*;
import io.github.ac1982.tcec.model.*;
import io.github.ac1982.tcec.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.stream.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class EndpointRoundTripTest {
    static Stream<JsonNode> examples() throws Exception {
        JsonNode values=JsonMapper.builder().build().readTree(EndpointRoundTripTest.class.getResourceAsStream("/endpoint-examples.json"));
        return StreamSupport.stream(values.spliterator(),false);
    }
    @ParameterizedTest @MethodSource("examples")
    void allDeclaredRequestAndResponseTypesRoundTrip(JsonNode example) {
        String path=example.get("endpoint").asText();
        Endpoint<?,?> endpoint=Endpoints.all().stream().filter(e->e.path().equals(path)).findFirst().orElseThrow();
        Object request=WireJson.read(example.get("request").toString(),endpoint.requestType());
        Object response=WireJson.read(example.get("response").toString(),endpoint.responseType());
        TcecCodec codec=new TcecCodec(PublishedCryptoVectorsTest.KEYS);
        assertEquals(request,codec.decodeRequest(codec.encodeRequest(request,"20261009120000","0001"),endpoint.requestType()));
        assertEquals(response,codec.decodeResponse(codec.encodeResponse(response),endpoint.responseType()));
        assertEquals(example.get("request"),JsonMapper.builder().build().readTree(WireJson.write(request)));
        assertEquals(example.get("response"),JsonMapper.builder().build().readTree(WireJson.write(response)));
    }
    @Test void registryHasExactlyFifteenCorrectlyCasedEndpoints() {
        assertEquals(15,Endpoints.standard().size()); assertEquals(15,Endpoints.standard().stream().map(Endpoint::path).distinct().count());
        assertEquals("notification_stationStatus",Endpoints.NOTIFICATION_STATION_STATUS.path());
        assertEquals("check_charge_orders",Endpoints.CHECK_CHARGE_ORDERS.path());
        assertFalse(Endpoints.QUERY_TOKEN.tokenRequired());
    }
    @Test void businessValuesArePreservedForHandlerValidation() {
        assertDoesNotThrow(()->new QueryStationStatsRequest("001","2026-02-30","2026-03-01"));
        assertDoesNotThrow(()->new PolicyInfo("240000",new BigDecimal("0.00001"),BigDecimal.ONE));
        assertDoesNotThrow(()->new ConnectorStatusInfo("001",99,1,50));
        assertDoesNotThrow(()->new QueryStationStatusRequest(Collections.nCopies(51,"001")));
    }
    @Test void immutableListsAndStringIdsPreserveLeadingZeros() {
        List<String> mutable=new ArrayList<>(List.of("0001"));var request=new QueryStationStatusRequest(mutable);mutable.clear();
        assertEquals(List.of("0001"),request.stationIds());assertThrows(UnsupportedOperationException.class,()->request.stationIds().add("0002"));
    }
}
