package io.github.ac1982.tcec.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Immutable wire data; business validation is the responsibility of the application handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryGroundLocksResponse(
    @JsonProperty("groundLocks") List<GroundLock> groundLocks) {
    public QueryGroundLocksResponse {
        if (groundLocks != null) groundLocks = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(groundLocks));
    }
    /** Constructs the documented empty value, including declared wire defaults. */
    public QueryGroundLocksResponse() {
        this(null);
    }
}
