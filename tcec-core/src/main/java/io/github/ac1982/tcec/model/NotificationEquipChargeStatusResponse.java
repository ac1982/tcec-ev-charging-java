package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationEquipChargeStatusResponse(
    @JsonProperty("StartChargeSeq") String startChargeSeq,
    @JsonProperty("SuccStat") Integer succStat) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public NotificationEquipChargeStatusResponse() {
        this(null, null);
    }
}
