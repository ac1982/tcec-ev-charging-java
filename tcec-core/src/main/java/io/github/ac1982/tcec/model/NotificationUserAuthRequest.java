package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationUserAuthRequest(
    @JsonProperty("Mobile") String mobile,
    @JsonProperty("OutUserId") String outUserId) {
    /** Constructs the documented empty value, including declared wire defaults. */
    public NotificationUserAuthRequest() {
        this(null, null);
    }
    @Override public String toString() { return "NotificationUserAuthRequest[redacted]"; }
}
