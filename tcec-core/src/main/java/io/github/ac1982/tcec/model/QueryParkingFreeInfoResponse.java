package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryParkingFreeInfoResponse(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("Duration") BigDecimal duration,
    @JsonProperty("QrCode") String qrCode,
    @JsonProperty("CarNumber") String carNumber,
    @JsonProperty("WaiverStatus") Integer waiverStatus) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryParkingFreeInfoResponse() {
        this(null, null, null, null, null);
    }
}
