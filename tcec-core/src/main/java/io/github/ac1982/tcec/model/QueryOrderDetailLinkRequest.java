package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryOrderDetailLinkRequest(
    @JsonProperty("Mobile") String mobile,
    @JsonProperty("OutUserId") String outUserId,
    @JsonProperty("StartChargeSeq") String startChargeSeq) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryOrderDetailLinkRequest() {
        this(null, null, null);
    }
    @Override public String toString() { return "QueryOrderDetailLinkRequest[redacted]"; }
}
