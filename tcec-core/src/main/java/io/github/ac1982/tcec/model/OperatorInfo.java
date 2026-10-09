package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperatorInfo(
    @JsonProperty("OperatorID") String operatorId,
    @JsonProperty("OperatorName") String operatorName,
    @JsonProperty("OperatorTel1") String operatorTel1,
    @JsonProperty("OperatorTel2") String operatorTel2,
    @JsonProperty("OperatorRegAddress") String operatorRegAddress,
    @JsonProperty("OperatorNote") String operatorNote) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public OperatorInfo() {
        this(null, null, null, null, null, null);
    }
}
