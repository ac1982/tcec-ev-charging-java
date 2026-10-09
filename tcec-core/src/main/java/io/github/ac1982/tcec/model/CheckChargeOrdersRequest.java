package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckChargeOrdersRequest(
    @JsonProperty("CheckOrderSeq") String checkOrderSeq,
    @JsonProperty("StartTime") String startTime,
    @JsonProperty("EndTime") String endTime,
    @JsonProperty("OrderCount") Integer orderCount,
    @JsonProperty(value = "TotalOrderPower", defaultValue = "0.0") BigDecimal totalOrderPower,
    @JsonProperty(value = "TotalOrderMoney", defaultValue = "0.0") BigDecimal totalOrderMoney,
    @JsonProperty("ChargeOrders") List<ReconciliationOrder> chargeOrders) {
    public CheckChargeOrdersRequest {
        if (chargeOrders != null) chargeOrders = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(chargeOrders));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public CheckChargeOrdersRequest() {
        this(null, null, null, null, new BigDecimal("0.0"), new BigDecimal("0.0"), null);
    }
}
