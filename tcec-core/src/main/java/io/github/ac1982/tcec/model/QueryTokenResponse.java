package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryTokenResponse(
    @JsonProperty("OperatorID") String operatorId,
    @JsonProperty("SuccStat") Integer succStat,
    @JsonProperty("AccessToken") String accessToken,
    @JsonProperty("TokenAvailableTime") Integer tokenAvailableTime,
    @JsonProperty("FailReason") Integer failReason) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryTokenResponse() {
        this(null, null, null, null, null);
    }
    @Override public String toString() { return "QueryTokenResponse[redacted]"; }
}
