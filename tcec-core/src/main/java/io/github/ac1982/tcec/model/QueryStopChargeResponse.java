package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryStopChargeResponse(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("StartChargeSeqStat") Integer startChargeSeqStat,
    @JsonProperty("SuccStat") Integer succStat,
    @JsonProperty("FailReason") Integer failReason) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryStopChargeResponse() {
        this(null, null, null, null);
    }
}
