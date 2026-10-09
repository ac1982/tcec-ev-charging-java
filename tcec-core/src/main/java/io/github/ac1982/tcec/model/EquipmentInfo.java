package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EquipmentInfo(
    @JsonProperty("EquipmentID") String equipmentId,
    @JsonProperty("ManufacturerID") String manufacturerId,
    @JsonProperty("ManufacturerName") String manufacturerName,
    @JsonProperty("EquipmentModel") String equipmentModel,
    @JsonProperty("ProductionDate") String productionDate,
    @JsonProperty(value = "EquipmentType", defaultValue = "5") Integer equipmentType,
    @JsonProperty("ConnectorInfos") List<ConnectorInfo> connectorInfos,
    @JsonProperty(value = "EquipmentLng", defaultValue = "0.0") BigDecimal equipmentLng,
    @JsonProperty(value = "EquipmentLat", defaultValue = "0.0") BigDecimal equipmentLat,
    @JsonProperty(value = "Power", defaultValue = "0.0") BigDecimal power,
    @JsonProperty("EquipmentName") String equipmentName) {
    public EquipmentInfo {
        if (connectorInfos != null) connectorInfos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(connectorInfos));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public EquipmentInfo() {
        this(null, null, null, null, null, 5, null, new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), null);
    }
}
