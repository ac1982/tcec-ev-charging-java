package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryOrderDetailLinkResponse(
    @JsonProperty("Link") String link) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryOrderDetailLinkResponse() {
        this(null);
    }
    @Override public String toString() { return "QueryOrderDetailLinkResponse[redacted]"; }
}
