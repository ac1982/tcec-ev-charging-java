package io.github.ac1982.tcec;
import io.github.ac1982.tcec.codec.*;
import io.github.ac1982.tcec.model.*;
import io.github.ac1982.tcec.wire.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WireJsonTest {
    private static final String REQUEST = "{\"OperatorID\":\"123456789\",\"Data\":\"x\",\"TimeStamp\":\"20261009120000\",\"Seq\":\"0001\",\"Sig\":\"x\"}";
    @Test void rejectsDuplicateTrailingUnknownAndWrongCaseProperties() {
        for (String value : new String[]{REQUEST+" {}", REQUEST.replace("\"Data\":\"x\"","\"Data\":\"x\",\"Data\":\"y\""),REQUEST.replace("OperatorID","operatorID"),REQUEST.replace("\"Sig\":\"x\"","\"Sig\":\"x\",\"Unknown\":1")})
            assertThrows(ProtocolException.class, () -> WireJson.read(value,RequestEnvelope.class));
    }
    @Test void rejectsNumericOrBooleanIdentifiersAndFloatReturnCode() {
        for (String value : new String[]{"123456789", "123456789.0", "true"})
            assertThrows(ProtocolException.class, () -> WireJson.read(REQUEST.replace("\"123456789\"",value), RequestEnvelope.class));
        for (String ret : new String[]{"0.9", "\"0\"", "null", "true"})
            assertThrows(ProtocolException.class, () -> WireJson.read("{\"Ret\":"+ret+",\"Msg\":\"\",\"Data\":\"x\",\"Sig\":\"x\"}",ResponseEnvelope.class));
    }
    @Test void rejectsMissingRequiredEnvelopeFieldsAndNullRoot() {
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"Msg\":\"\",\"Data\":\"x\",\"Sig\":\"x\"}",ResponseEnvelope.class));
        assertNull(WireJson.read("{\"StartChargeSeq\":\"123456789202610090000000001\",\"ConnectorID\":\"001\"}",QueryStartChargeRequest.class).qrCode());
        assertThrows(ProtocolException.class, () -> WireJson.read("null",QueryTokenRequest.class));
    }
    @Test void parserBoundsAndSensitiveRecordLoggingAreSafe() {
        assertThrows(ProtocolException.class, () -> WireJson.read("[".repeat(65) + "0" + "]".repeat(65), Object.class));
        assertThrows(ProtocolException.class, () -> WireJson.read("9".repeat(129), Object.class));
        var message = new NotificationStartChargeResultRequest("123456789" + "20261009" + "0000000001", 2, "001", "2026-10-09 12:00:00", "private-stop-code");
        assertFalse(message.toString().contains("private-stop-code"));
    }
    @Test void omissionAndWireSpellingArePreserved() {
        assertEquals("{}",WireJson.write(new QueryStationsInfoRequest(null,null,null)));
        String json=WireJson.write(new PolicyInfo("000000",new java.math.BigDecimal("0.1234"),new java.math.BigDecimal("0.5000")));
        assertTrue(json.contains("\"ServicePrice\":0.5000")); assertFalse(json.contains("SevicePrice"));
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"Status\":\"0\"}",NotificationStationStatusResponse.class));
    }
}
