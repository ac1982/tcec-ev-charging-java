package io.github.ac1982.tcec.wire;
import com.fasterxml.jackson.annotation.JsonProperty;
public record RequestEnvelope(
    @JsonProperty(value = "OperatorID", required = true) String operatorId,
    @JsonProperty(value = "Data", required = true) String data,
    @JsonProperty(value = "TimeStamp", required = true) String timeStamp,
    @JsonProperty(value = "Seq", required = true) String seq,
    @JsonProperty(value = "Sig", required = true) String sig) {
    @Override public String toString() { return "RequestEnvelope[redacted]"; }
}
