package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryStationStatsRequest(
    @JsonProperty("StationID") String stationId,
    @JsonProperty("StartTime") String startTime,
    @JsonProperty("EndTime") String endTime) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryStationStatsRequest() {
        this(null, null, null);
    }
}
