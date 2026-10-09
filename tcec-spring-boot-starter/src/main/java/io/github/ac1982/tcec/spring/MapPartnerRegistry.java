package io.github.ac1982.tcec.spring;

import io.github.ac1982.tcec.security.PartnerCredentials;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable partner lookup; credentials are already validated by their constructor. */
public final class MapPartnerRegistry implements PartnerRegistry {
    private final Map<String, PartnerCredentials> partners;

    public MapPartnerRegistry(Collection<PartnerCredentials> credentials) {
        Objects.requireNonNull(credentials, "credentials");
        Map<String, PartnerCredentials> map = new HashMap<>();
        for (PartnerCredentials credential : credentials) {
            Objects.requireNonNull(credential, "credential");
            if (map.putIfAbsent(credential.operatorId(), credential) != null) {
                throw new IllegalArgumentException("Duplicate partner operator identifier");
            }
        }
        this.partners = Map.copyOf(map);
    }

    @Override public Optional<PartnerCredentials> find(String operatorId) {
        return operatorId == null ? Optional.empty() : Optional.ofNullable(partners.get(operatorId));
    }
}
