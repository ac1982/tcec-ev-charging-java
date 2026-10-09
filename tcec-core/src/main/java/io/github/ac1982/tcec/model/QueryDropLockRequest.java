package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryDropLockRequest(
    @JsonProperty("StationID") String stationId,
    @JsonProperty("LockNum") String lockNum,
    @JsonProperty("Lat") BigDecimal lat,
    @JsonProperty("Lng") BigDecimal lng) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryDropLockRequest() {
        this(null, null, null, null);
    }
}
