package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChargeOrderInfo(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("ConnectorID") String connectorId,
    @JsonProperty("StartTime") String startTime,
    @JsonProperty("EndTime") String endTime,
    @JsonProperty(value = "TotalPower", defaultValue = "0.0") BigDecimal totalPower,
    @JsonProperty(value = "TotalElecMoney", defaultValue = "0.0") BigDecimal totalElecMoney,
    @JsonProperty(value = "TotalServiceMoney", defaultValue = "0.0") BigDecimal totalServiceMoney,
    @JsonProperty(value = "TotalMoney", defaultValue = "0.0") BigDecimal totalMoney,
    @JsonProperty("StopReason") Integer stopReason,
    @JsonProperty("SumPeriod") Integer sumPeriod,
    @JsonProperty("ChargeDetails") List<ChargeDetail> chargeDetails) {
    public ChargeOrderInfo {
        if (chargeDetails != null) chargeDetails = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(chargeDetails));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public ChargeOrderInfo() {
        this(null, null, null, null, new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), null, null, null);
    }
}
