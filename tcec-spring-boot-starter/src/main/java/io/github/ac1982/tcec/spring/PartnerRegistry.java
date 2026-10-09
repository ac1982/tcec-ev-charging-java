package io.github.ac1982.tcec.spring;

import io.github.ac1982.tcec.security.PartnerCredentials;
import java.util.Optional;

/** Looks up trusted, validated credentials. Request fields must never supply key material. */
@FunctionalInterface
public interface PartnerRegistry {
    Optional<PartnerCredentials> find(String operatorId);
}
