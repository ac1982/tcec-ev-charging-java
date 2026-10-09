package io.github.ac1982.tcec;
import io.github.ac1982.tcec.codec.*;
import io.github.ac1982.tcec.model.*;
import io.github.ac1982.tcec.protocol.*;
import io.github.ac1982.tcec.security.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RequestValidationTest {
    private static final String SEQUENCE = "123456789202610090000000001";
    @Test void incompleteDtoIsRepresentableButCannotReachChargeHandler() {
        var missing = WireJson.read("{}", QueryStartChargeRequest.class);
        assertNull(missing.startChargeSeq());
        assertEquals(4004, assertThrows(ProtocolException.class, () -> RequestValidation.validate(missing)).ret());
        var keys = new PartnerCredentials("123456789", "example-secret", "1234567890abcdef", "1234567890abcdef", "example-sig");
        var codec = new TcecCodec(keys);
        var signed = codec.encodeRequest(missing, "20261009120000", "0001");
        assertEquals(4004, assertThrows(ProtocolException.class, () -> codec.decodeRequest(signed, QueryStartChargeRequest.class)).ret());
    }
    @Test void criticalChargeFieldsRejectNullEmptyAndWhitespaceAtExecutionBoundary() {
        for (String missing : new String[]{null, "", " "}) {
            assertThrows(ProtocolException.class, () -> RequestValidation.validate(new QueryStartChargeRequest(SEQUENCE, "001", missing)));
            assertThrows(ProtocolException.class, () -> RequestValidation.validate(new QueryStartChargeRequest(SEQUENCE, missing, "qr")));
            assertThrows(ProtocolException.class, () -> RequestValidation.validate(new QueryStopChargeRequest(missing, "001")));
        }
        assertDoesNotThrow(() -> RequestValidation.validate(new QueryStartChargeRequest(SEQUENCE, "001", "qr")));
    }
    @Test void stationListIsBoundedButDoesNotImposeUndocumentedDomainEnums() {
        assertThrows(ProtocolException.class, () -> RequestValidation.validate(new QueryStationStatusRequest(null)));
        assertThrows(ProtocolException.class, () -> RequestValidation.validate(new QueryStationStatusRequest(List.of())));
        assertDoesNotThrow(() -> RequestValidation.validate(new QueryStationStatusRequest(java.util.Collections.nCopies(50, "001"))));
        assertThrows(ProtocolException.class, () -> RequestValidation.validate(new QueryStationStatusRequest(java.util.Collections.nCopies(51, "001"))));
    }
    @Test void sequencesAreValidatedForActionsButDtoDefaultsRemainIndependent() {
        assertThrows(ProtocolException.class, () -> RequestValidation.validate(new QueryEquipAuthRequest("short", "001")));
        assertThrows(ProtocolException.class, () -> RequestValidation.validate(new QueryEquipChargeStatusRequest("short")));
        assertDoesNotThrow(() -> RequestValidation.validate(new QueryStationsInfoRequest()));
        assertDoesNotThrow(() -> RequestValidation.validate(new QueryStationStatsRequest()));
    }
    @Test void extensionActionsRequireTheirContractIdentifiers() {
        for (var endpoint : Endpoints.extensions()) {
            Object missing = WireJson.read("{}", endpoint.requestType());
            if (endpoint == Endpoints.NOTIFICATION_USER_ORDER_INFO) assertDoesNotThrow(() -> RequestValidation.validate(missing));
            else assertThrows(ProtocolException.class, () -> RequestValidation.validate(missing), endpoint.path());
        }
    }
}
