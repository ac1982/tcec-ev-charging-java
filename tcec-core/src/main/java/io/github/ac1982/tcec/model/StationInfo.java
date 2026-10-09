package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StationInfo(
    @JsonProperty("StationID") String stationId,
    @JsonProperty("OperatorID") String operatorId,
    @JsonProperty("EquipmentOwnerID") String equipmentOwnerId,
    @JsonProperty("StationName") String stationName,
    @JsonProperty("CountryCode") String countryCode,
    @JsonProperty("AreaCode") String areaCode,
    @JsonProperty("Address") String address,
    @JsonProperty("StationTel") String stationTel,
    @JsonProperty("ServiceTel") String serviceTel,
    @JsonProperty(value = "StationType", defaultValue = "1") Integer stationType,
    @JsonProperty(value = "StationStatus", defaultValue = "0") Integer stationStatus,
    @JsonProperty(value = "ParkNums", defaultValue = "0") Integer parkNums,
    @JsonProperty(value = "StationLng", defaultValue = "0.0") BigDecimal stationLng,
    @JsonProperty(value = "StationLat", defaultValue = "0.0") BigDecimal stationLat,
    @JsonProperty("SiteGuide") String siteGuide,
    @JsonProperty("Construction") Integer construction,
    @JsonProperty("Pictures") List<String> pictures,
    @JsonProperty("MatchCars") String matchCars,
    @JsonProperty("ParkInfo") String parkInfo,
    @JsonProperty("BusineHours") String busineHours,
    @JsonProperty("ElectricityFee") String electricityFee,
    @JsonProperty("ServiceFee") String serviceFee,
    @JsonProperty("ParkFee") String parkFee,
    @JsonProperty("Payment") String payment,
    @JsonProperty(value = "SupportOrder", defaultValue = "0") Integer supportOrder,
    @JsonProperty("Remark") String remark,
    @JsonProperty("EquipmentInfos") List<EquipmentInfo> equipmentInfos,
    @JsonProperty("HasParkingLock") Integer hasParkingLock,
    @JsonProperty("FreeParking") Integer freeParking,
    @JsonProperty("SubPark") Integer subPark,
    @JsonProperty("DutyNum") Integer dutyNum,
    @JsonProperty("Wc") Integer wc,
    @JsonProperty("Rainshed") Integer rainshed,
    @JsonProperty("Lounge") Integer lounge,
    @JsonProperty("LightMeal") Integer lightMeal,
    @JsonProperty("Shop") Integer shop,
    @JsonProperty("LimitFreeParking") Integer limitFreeParking) {
    public StationInfo {
        if (pictures != null) pictures = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(pictures));
        if (equipmentInfos != null) equipmentInfos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(equipmentInfos));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public StationInfo() {
        this(null, null, null, null, null, null, null, null, null, 1, 0, 0, new BigDecimal("0.0"), new BigDecimal("0.0"), null, null, null, null, null, null, null, null, null, null, 0, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
