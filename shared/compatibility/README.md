# FM1 compatibility exchange v1

Canonical data: branch fm1-compatibility-data, shared/compatibility/firmware-compatibility.json. Both apps read via GitHub Contents API with explicit ref. SCREEN records physical LCD tests; ANALIZER records research only. LCD pass is independent of controls. Unknown version is null, never guessed. Initial catalog contains 15 candidates, no imported physical evidence yet.

## Sync contract
- Offline-first durable local outbox; UUID per event, UTC timestamp, immutable events. Corrections append a new event with supersedes referencing same source/firmware/version. Never delete history.
- GET file and blob SHA; merge catalog by ID and events by UUID; reject divergent payload for same UUID, validate schema, unique IDs, referential integrity and same-source supersedes. PUT with current SHA and explicit branch. On 409/422 refetch and retry bounded with backoff; retain outbox until confirmed. Never overwrite with stale snapshot.
- No automatic commands, firmware flashing or protocol changes from remote data. Never block LCD rendering on networking. Explicit sync and optional background sync only after consent.
- User-supplied fine-grained GitHub token limited to this repository, Contents read/write; store via Android Keystore-backed encryption, never log or embed. GitHub token cannot be scoped to one branch; dedicated branch is organizational isolation, not an authorization boundary. Public repository data is public: no serials, personal identifiers, tokens or raw captures by default.
- Export/import JSON for backup before uninstall; synced events recover after reinstall. Unsynced events require export. Unknown schema versions read-only until supported.
- Test concurrent writes, offline recovery, duplicate UUID, invalid schema, expired token, wrong branch, supersedes, and no LCD regression before shipping.
