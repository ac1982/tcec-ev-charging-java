package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryStationsInfoRequest(
    @JsonProperty("LastQueryTime") String lastQueryTime,
    @JsonProperty(value = "PageNo", defaultValue = "1") Integer pageNo,
    @JsonProperty(value = "PageSize", defaultValue = "10") Integer pageSize) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryStationsInfoRequest() {
        this(null, 1, 10);
    }
}
