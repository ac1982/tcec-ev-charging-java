package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryStartChargeRequest(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("ConnectorID") String connectorId,
    @JsonProperty("QRCode") String qrCode) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryStartChargeRequest() {
        this(null, null, null);
    }
}
