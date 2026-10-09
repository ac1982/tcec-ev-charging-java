package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectorStatusInfo(
    @JsonProperty("ConnectorID") String connectorId,
    @JsonProperty("Status") Integer status,
    @JsonProperty("ParkStatus") Integer parkStatus,
    @JsonProperty("LockStatus") Integer lockStatus) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public ConnectorStatusInfo() {
        this(null, null, null, null);
    }
}
