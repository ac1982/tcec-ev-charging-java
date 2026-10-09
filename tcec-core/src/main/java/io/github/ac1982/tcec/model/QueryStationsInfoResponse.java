package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryStationsInfoResponse(
    @JsonProperty("PageNo") Integer pageNo,
    @JsonProperty("PageCount") Integer pageCount,
    @JsonProperty("ItemSize") Integer itemSize,
    @JsonProperty("StationInfos") List<StationInfo> stationInfos) {
    public QueryStationsInfoResponse {
        if (stationInfos != null) stationInfos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(stationInfos));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryStationsInfoResponse() {
        this(null, null, null, null);
    }
}
