package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryEquipBusinessPolicyResponse(
    @JsonProperty("EquipBizSeq") String equipBizSeq,
    @JsonProperty("ConnectorID") String connectorId,
    @JsonProperty("SuccStat") Integer succStat,
    @JsonProperty("FailReason") Integer failReason,
    @JsonProperty("SumPeriod") Integer sumPeriod,
    @JsonProperty("PolicyInfos") List<PolicyInfo> policyInfos) {
    public QueryEquipBusinessPolicyResponse {
        if (policyInfos != null) policyInfos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(policyInfos));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryEquipBusinessPolicyResponse() {
        this(null, null, null, null, null, null);
    }
}
