package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryTokenRequest(
    @JsonProperty("OperatorID") String operatorId,
    @JsonProperty("OperatorSecret") String operatorSecret) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryTokenRequest() {
        this(null, null);
    }
    @Override public String toString() { return "QueryTokenRequest[redacted]"; }
}
