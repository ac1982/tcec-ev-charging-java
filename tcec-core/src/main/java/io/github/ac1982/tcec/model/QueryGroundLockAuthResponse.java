package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryGroundLockAuthResponse(
    @JsonProperty("LocksAuth") List<GroundLockAuth> locksAuth) {
    public QueryGroundLockAuthResponse {
        if (locksAuth != null) locksAuth = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(locksAuth));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryGroundLockAuthResponse() {
        this(null);
    }
}
