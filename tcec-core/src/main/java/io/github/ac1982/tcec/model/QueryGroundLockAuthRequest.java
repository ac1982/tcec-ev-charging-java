package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryGroundLockAuthRequest(
    @JsonProperty("StationIDs") List<String> stationIds,
    @JsonProperty("Lat") BigDecimal lat,
    @JsonProperty("Lng") BigDecimal lng) {
    public QueryGroundLockAuthRequest {
        if (stationIds != null) stationIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(stationIds));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryGroundLockAuthRequest() {
        this(null, null, null);
    }
}
