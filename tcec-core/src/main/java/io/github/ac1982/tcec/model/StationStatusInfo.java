package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StationStatusInfo(
    @JsonProperty("FullStationID") String fullStationId,
    @JsonProperty("StationID") String stationId,
    @JsonProperty("ConnectorStatusInfos") List<ConnectorStatusInfo> connectorStatusInfos) {
    public StationStatusInfo {
        if (connectorStatusInfos != null) connectorStatusInfos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(connectorStatusInfos));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public StationStatusInfo() {
        this(null, null, null);
    }
}
