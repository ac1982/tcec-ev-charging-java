package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReconciliationOrder(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("TotalPower") BigDecimal totalPower,
    @JsonProperty("TotalMoney") BigDecimal totalMoney) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public ReconciliationOrder() {
        this(null, null, null);
    }
}
