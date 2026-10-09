package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EquipmentStats(
    @JsonProperty("EquipmentID") String equipmentId,
    @JsonProperty(value = "EquipmentElectricity", defaultValue = "0.0") BigDecimal equipmentElectricity,
    @JsonProperty("ConnectorStatsInfos") List<ConnectorStats> connectorStatsInfos) {
    public EquipmentStats {
        if (connectorStatsInfos != null) connectorStatsInfos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(connectorStatsInfos));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public EquipmentStats() {
        this(null, new BigDecimal("0.0"), null);
    }
}
