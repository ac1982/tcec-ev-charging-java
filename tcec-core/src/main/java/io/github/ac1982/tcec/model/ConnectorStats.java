package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectorStats(
    @JsonProperty("ConnectorID") String connectorId,
    @JsonProperty(value = "ConnectorElectricity", defaultValue = "0.0") BigDecimal connectorElectricity) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public ConnectorStats() {
        this(null, new BigDecimal("0.0"));
    }
}
