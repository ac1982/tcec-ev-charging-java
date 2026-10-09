package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckChargeOrdersResponse(
    @JsonProperty("CheckOrderSeq") String checkOrderSeq,
    @JsonProperty("StartTime") String startTime,
    @JsonProperty("EndTime") String endTime,
    @JsonProperty("TotalDisputeOrder") Integer totalDisputeOrder,
    @JsonProperty(value = "TotalDisputePower", defaultValue = "0.0") BigDecimal totalDisputePower,
    @JsonProperty(value = "TotalDisputeMoney", defaultValue = "0.0") BigDecimal totalDisputeMoney,
    @JsonProperty("DisputeOrders") List<DisputeOrder> disputeOrders) {
    public CheckChargeOrdersResponse {
        if (disputeOrders != null) disputeOrders = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(disputeOrders));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public CheckChargeOrdersResponse() {
        this(null, null, null, null, new BigDecimal("0.0"), new BigDecimal("0.0"), null);
    }
}
