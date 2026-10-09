package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectorInfo(
    @JsonProperty("ConnectorID") String connectorId,
    @JsonProperty("ConnectorName") String connectorName,
    @JsonProperty("ConnectorType") Integer connectorType,
    @JsonProperty("VoltageUpperLimits") Integer voltageUpperLimits,
    @JsonProperty("VoltageLowerLimits") Integer voltageLowerLimits,
    @JsonProperty("Current") Integer current,
    @JsonProperty(value = "Power", defaultValue = "0.0") BigDecimal power,
    @JsonProperty("ParkNo") String parkNo,
    @JsonProperty("NationalStandard") Integer nationalStandard) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public ConnectorInfo() {
        this(null, null, null, null, null, null, new BigDecimal("0.0"), null, null);
    }
}
