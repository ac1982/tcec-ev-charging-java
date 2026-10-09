package io.github.ac1982.tcec.spring;

/** Authenticated peer and request identifiers. Contains no keys or access token. */
public record RequestContext(String operatorId, String timeStamp, String sequence) {}
