package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisputeOrder(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty(value = "TotalPower", defaultValue = "0.0") BigDecimal totalPower,
    @JsonProperty(value = "TotalMoney", defaultValue = "0.0") BigDecimal totalMoney,
    @JsonProperty("DisputeReason") Integer disputeReason) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public DisputeOrder() {
        this(null, new BigDecimal("0.0"), new BigDecimal("0.0"), null);
    }
}
