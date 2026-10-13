# Changelog

## 1.0.2

### Fixed
- Spring starter: once a business handler has returned, the replay claim is kept even if the
  response is invalid or encoding/encryption/signing fails, so an identical retry is rejected as a
  replay rather than re-executing the handler. Only a handler that throws releases the claim.

## 1.0.1

Wire format, signatures and encryption are unchanged (published vectors pass).

### Added
- `ReplayStore.release(operatorId, timeStamp, fingerprint)` (default no-op) and
  `RequestVerifier.release(envelope)`. Implemented by `InMemoryReplayStore` and `RedisReplayStore`.

### Fixed
- Spring starter: a failing business handler no longer burns the replay claim. The claim is
  released so the partner's identical retry is processed instead of rejected with 4003.
  Deterministic decode/validation failures (4004) keep the claim. Handlers must remain idempotent.
- `RedisReplayStore.claim` / `RedisTokenStore.validate`: a Redis connection failure now throws
  `ProtocolException(500)` instead of returning `false`, so partners receive a retryable Ret 500
  rather than 4003 (repeated request) or 4002 (invalid token).
- `TcecSession`: a failed early token refresh falls back to the still-valid cached token, and
  callers no longer block behind an in-flight refresh while the cached token is valid.

### Performance
- `TcecCrypto` reuses per-thread initialized `Cipher`/`Mac` instances per `PartnerCredentials`
  (about 2.5x single-thread, 5.7x with 8 threads; see `CryptoBenchmark`). Reuse one
  `PartnerCredentials` instance per partner to benefit.

### Behavior changes to note
- Custom `ReplayStore`/`TokenStore` implementations are encouraged to throw
  `ProtocolException(SYSTEM_ERROR)` on outage rather than return `false`.

## 1.0.0
- Initial MIT release.
