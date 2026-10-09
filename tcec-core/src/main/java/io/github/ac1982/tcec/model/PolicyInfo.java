package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolicyInfo(
    @JsonProperty("StartTime") String startTime,
    @JsonProperty(value = "ElecPrice", defaultValue = "0.0") BigDecimal elecPrice,
    @JsonProperty(value = "ServicePrice", defaultValue = "0.0") BigDecimal servicePrice) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public PolicyInfo() {
        this(null, new BigDecimal("0.0"), new BigDecimal("0.0"));
    }
}
