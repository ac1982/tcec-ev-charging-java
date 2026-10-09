package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryParkingFreeWaveRequest(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("CarNumber") String carNumber) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryParkingFreeWaveRequest() {
        this(null, null);
    }
}
