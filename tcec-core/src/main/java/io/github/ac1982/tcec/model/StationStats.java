package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StationStats(
    @JsonProperty("StationID") String stationId,
    @JsonProperty("StartTime") String startTime,
    @JsonProperty("EndTime") String endTime,
    @JsonProperty(value = "StationElectricity", defaultValue = "0.0") BigDecimal stationElectricity,
    @JsonProperty("EquipmentStatsInfos") List<EquipmentStats> equipmentStatsInfos) {
    public StationStats {
        if (equipmentStatsInfos != null) equipmentStatsInfos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(equipmentStatsInfos));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public StationStats() {
        this(null, null, null, new BigDecimal("0.0"), null);
    }
}
