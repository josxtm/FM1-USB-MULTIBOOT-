# FM1CAT v1 conformance — C99, Android y simulador

## Shared fixtures

The canonical fixtures are under `tests/fixtures/fm1cat/`:

- `valid-empty.csv`
- `valid-one.csv`
- `valid-crlf.csv`
- `invalid-path.csv`
- `invalid-duplicate.csv`

They use the exact FM1CAT v1 header, ASCII fields, `ID.BIN`/`ID.FWS`, lowercase SHA-256 and `UNVERIFIED` compatibility.

## Matrix

| Case | C99 core | Android `FirmwareCatalog` | Expected |
|---|---:|---:|---|
| Empty catalog | yes | yes | accept |
| One entry | yes | yes | accept |
| CRLF line endings | yes | yes | accept |
| `../` path | yes | yes | reject |
| Duplicate ID | yes | yes | reject |

The C core also exercises 64 entries and the 24 KiB bound in `tests/embedded_multiboot_test.c`. Android executes the shared fixtures in `FirmwareCatalogConformanceTest`. The browser simulator continues to enforce the same FM1CAT v1 grammar, while its content inspection remains browser-only and non-executing.

The C storage HAL receives the short catalog filename but constructs and validates `FM1/FW/<ID.BIN|ID.FWS>` before every firmware read. Absolute paths, separators and parent traversal are rejected.
