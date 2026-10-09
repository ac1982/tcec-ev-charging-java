package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryStationStatusResponse(
    @JsonProperty("Total") String total,
    @JsonProperty("StationStatusInfos") List<StationStatusInfo> stationStatusInfos) {
    public QueryStationStatusResponse {
        if (stationStatusInfos != null) stationStatusInfos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(stationStatusInfos));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryStationStatusResponse() {
        this(null, null);
    }
}
