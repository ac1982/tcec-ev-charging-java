package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChargingSnapshot(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("StartChargeSeqStat") Integer startChargeSeqStat,
    @JsonProperty("ConnectorID") String connectorId,
    @JsonProperty("ConnectorStatus") Integer connectorStatus,
    @JsonProperty(value = "CurrentA", defaultValue = "0.0") BigDecimal currentA,
    @JsonProperty(value = "CurrentB", defaultValue = "0.0") BigDecimal currentB,
    @JsonProperty(value = "CurrentC", defaultValue = "0.0") BigDecimal currentC,
    @JsonProperty(value = "VoltageA", defaultValue = "0.0") BigDecimal voltageA,
    @JsonProperty(value = "VoltageB", defaultValue = "0.0") BigDecimal voltageB,
    @JsonProperty(value = "VoltageC", defaultValue = "0.0") BigDecimal voltageC,
    @JsonProperty(value = "Soc", defaultValue = "0.0") BigDecimal soc,
    @JsonProperty("StartTime") String startTime,
    @JsonProperty("EndTime") String endTime,
    @JsonProperty(value = "TotalPower", defaultValue = "0.0") BigDecimal totalPower,
    @JsonProperty(value = "ElecMoney", defaultValue = "0.0") BigDecimal elecMoney,
    @JsonProperty(value = "ServiceMoney", defaultValue = "0.0") BigDecimal serviceMoney,
    @JsonProperty(value = "TotalMoney", defaultValue = "0.0") BigDecimal totalMoney,
    @JsonProperty("SumPeriod") Integer sumPeriod,
    @JsonProperty("ChargeDetails") List<ChargeDetail> chargeDetails) {
    public ChargingSnapshot {
        if (chargeDetails != null) chargeDetails = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(chargeDetails));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public ChargingSnapshot() {
        this(null, null, null, null, new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), null, null, new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), null, null);
    }
}
