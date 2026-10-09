package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChargeDetail(
    @JsonProperty("DetailStartTime") String detailStartTime,
    @JsonProperty("DetailEndTime") String detailEndTime,
    @JsonProperty(value = "ElecPrice", defaultValue = "0.0") BigDecimal elecPrice,
    @JsonProperty(value = "ServicePrice", defaultValue = "0.0") BigDecimal servicePrice,
    @JsonProperty(value = "DetailPower", defaultValue = "0.0") BigDecimal detailPower,
    @JsonProperty(value = "DetailElecMoney", defaultValue = "0.0") BigDecimal detailElecMoney,
    @JsonProperty(value = "DetailServiceMoney", defaultValue = "0.0") BigDecimal detailServiceMoney) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public ChargeDetail() {
        this(null, null, new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"), new BigDecimal("0.0"));
    }
}
