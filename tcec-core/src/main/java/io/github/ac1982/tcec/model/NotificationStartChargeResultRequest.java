package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationStartChargeResultRequest(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("StartChargeSeqStat") Integer startChargeSeqStat,
    @JsonProperty("ConnectorID") String connectorId,
    @JsonProperty("StartTime") String startTime,
    @JsonProperty("IdentCode") String identCode) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public NotificationStartChargeResultRequest() {
        this(null, null, null, null, null);
    }
    @Override public String toString() { return "NotificationStartChargeResultRequest[redacted]"; }
}
