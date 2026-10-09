package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryUserOrderResponse(
    @JsonProperty("PageNo") Integer pageNo,
    @JsonProperty("PageSize") Integer pageSize,
    @JsonProperty("TotalPage") Integer totalPage,
    @JsonProperty("TotalCount") Integer totalCount,
    @JsonProperty("OrderInfoList") List<OrderInfo> orderInfoList) {
    public QueryUserOrderResponse {
        if (orderInfoList != null) orderInfoList = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(orderInfoList));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryUserOrderResponse() {
        this(null, null, null, null, null);
    }
}
