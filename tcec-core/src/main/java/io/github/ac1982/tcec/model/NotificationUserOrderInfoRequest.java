package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationUserOrderInfoRequest(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("XJChargeSeq") String xjChargeSeq,
    @JsonProperty("StationName") String stationName,
    @JsonProperty("ConnectorID") String connectorId,
    @JsonProperty("StartTime") String startTime,
    @JsonProperty("EndTime") String endTime,
    @JsonProperty("ChargeTime") Integer chargeTime,
    @JsonProperty("TotalPower") BigDecimal totalPower,
    @JsonProperty("TotalElecMoney") BigDecimal totalElecMoney,
    @JsonProperty("TotalSeviceMoney") BigDecimal totalSeviceMoney,
    @JsonProperty("TotalMoney") BigDecimal totalMoney,
    @JsonProperty("OrderStatus") Integer orderStatus,
    @JsonProperty("RealPayMoney") BigDecimal realPayMoney) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public NotificationUserOrderInfoRequest() {
        this(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
