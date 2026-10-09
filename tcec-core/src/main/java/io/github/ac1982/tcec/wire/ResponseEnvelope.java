package io.github.ac1982.tcec.wire;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseEnvelope(
    @JsonProperty(value = "Ret", required = true) int ret,
    @JsonProperty(value = "Msg", required = true) String msg,
    @JsonProperty(value = "Data", required = true) String data,
    @JsonProperty(value = "Sig", required = true) String sig,
    @JsonProperty("OperatorID") String operatorId) {
    public ResponseEnvelope(int ret, String msg, String data, String sig) { this(ret, msg, data, sig, null); }
    @Override public String toString() { return "ResponseEnvelope[ret=" + ret + ", redacted]"; }
}
