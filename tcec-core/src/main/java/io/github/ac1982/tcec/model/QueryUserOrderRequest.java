package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryUserOrderRequest(
    @JsonProperty("OutUserId") String outUserId,
    @JsonProperty("StartTime") String startTime,
    @JsonProperty("EndTime") String endTime,
    @JsonProperty(value = "PageNo", defaultValue = "1") Integer pageNo,
    @JsonProperty(value = "PageSize", defaultValue = "10") Integer pageSize,
    @JsonProperty("OrderIds") List<String> orderIds) {
    public QueryUserOrderRequest {
        if (orderIds != null) orderIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(orderIds));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryUserOrderRequest() {
        this(null, null, null, 1, 10, null);
    }
    @Override public String toString() { return "QueryUserOrderRequest[redacted]"; }
}
