# FM1 MULTIBOOT OS — Architecture draft A1

Status: **design and host-browser prototype only**. Nothing here is installed on the FM1.

## Product requirements

1. Power on -> Cover Flow selection on the built-in 240 x 240 ST7789 display.
2. Use OCT- / OCT+ to navigate, PLAY to select, REC to cancel or open recovery.
3. Browse a firmware collection on an external 32 GB USB-C microSD reader.
4. Prepare the card using Android (download/import, validate, export); no desktop PC required.
5. The final persistent launcher must be independent of DARK-1. DARK-1 may remain an optional *file* on the card.
6. When no card exists, display a recovery/diagnostics choice rather than looping or flashing.
7. Do not retain unneeded synthesis firmware in internal Flash. Keep essential recovery and factory-protected data.

## Current evidence vs. unresolved assumptions

| Area | Evidence today | Not yet proven |
| --- | --- | --- |
| FM1 Flash | Puya JEDEC 0x856014, 1 MiB; user ran backup in FM-1 Flasher | Externally exportable, restorable backup verified |
| Android USB | ROM-compatible 4C4A:8057, WL82/UBOOT1.00 and valid INQUIRY/CSW observed on tablet | Arbitrary ROM RAM load and safe return |
| RAM diagnostic | APP1 compiled, validated, simulation passes | Actual execution, LCD and button behavior |
| FM1 USB Host | Firmware in src/husb.c and simulated BOT/FAT32 | FM1 USB-C role, 5 V VBUS source, reader enumeration |
| microSD | Android mount reported vfat | Explicit FAT32 verification, power when attached to FM1 |
| Firmware switch | Conceptual menu | Running any full XIP image from USB or RAM; safe reflash path |

A 4C4A:8057 INQUIRY signature is not sufficient proof of immutable ROM vs a compatible resident loader.

## Proposed decomposition

### A. Reset / recovery boundary (future hardware-dependent)

* Immutable silicon boot path remains untouched.
* A minimal, recoverable stage-0 would validate and transfer to a menu stage.
* No implementation should presume the stage-0 survives full-stock image reflashes; partition/layout collisions require evidence.
* An internal launcher cannot coexist with arbitrary images overwriting its Flash address range unless an explicit isolation/recovery strategy is proven.

### B. Cover Flow UI core

* Owns selected firmware ID, navigation, busy/error/no-card status, and rendering parameters.
* No flash, USB, or firmware execution calls in the UI reducer.
* In a production FM1 build: a bounded, precomputed RGB565 sprite set or streaming decode into one LCD scanline buffer.
* Draw only visible covers; avoid a 240 x 240 framebuffer (115,200 bytes RGB565).
* Route actual FM1 keys through a short event mapper: K_PREV, K_NEXT, K_SEL, K_REC (see src/keys.c).
* Browser mock uses the same abstract PREV/NEXT/SELECT/BACK actions, but is **not** hardware firmware.

### C. Storage / catalog abstraction

* Independent provider exposing list, metadata and cover data with bounded reads.
* Initial FAT32 driver in src/storage.c lists short 8.3 filenames in the root; it does **not** yet support general folders, long filenames or the planned catalog.
* Proposed future v1 catalog is a bounded, versioned text manifest under /FM1/INDEX.TXT only after subdirectory support exists. Prototype uses a hardcoded demo array.
* Verify capacity/format and media integrity before exposing a launch action.
* Treat firmware archives as untrusted inputs: require exact image type, target, layout, integrity and verified source/license.

### D. Launch backend (none implemented)

Do **not** promise direct USB execution of legacy images:

1. USB execution: requires code relocation / execution model supported by the MCU (not established).
2. RAM execution: only if a target program is RAM-linked and peripherals/memory fit (not established for DARK-1 or other full synths).
3. Internal Flash deployment: requires verified stage-0 separation, power-loss recovery, image layout and write verification; wear must be considered.

A UI selection may request an action only via an explicit verified launch backend. The current prototype **always blocks** launch.

### E. Android library manager (future)

* Import locally, download only from vetted sources, preserve original bytes, validate hashes and licenses.
* Use the Android Storage Access Framework to export files plus manifest/cover to FAT32 microSD, no format/delete without approval.
* Separate media library management from RAM loader and low-level USB transport.
* No OTA or Flash changes are part of this branch.

## Proposed state machine

- NO_CARD: Cover Flow disabled; show diagnostic/retry, do not write.
- READY: render library and navigate.
- EMPTY: card present but no valid firmware entries.
- VERIFYING: future immutable image check in progress, cancelable before any destructive operation.
- LAUNCH_BLOCKED: UI-only prototype; display why execution is unavailable.
- ERROR: storage/read/validation problem with recoverable navigation.
- RECOVERY: future protected path, not implemented here.

Selection never directly writes Flash, even in READY.

## Cover Flow visual contract (240 x 240)

* Dark background, turquoise accent, readable high-contrast text.
* Center cover dominates; one neighboring cover is visible on each side, scaled/skewed for perspective.
* Target animation about 220 ms per step on host. This is a visual target, **not** a verified FM1 frame rate.
* No image assets from firmware authors redistributed in prototype; generated abstract covers only.
* Fixed size text/small metadata and battery/USB state reserved for future device integration.
* REC should always yield a non-destructive back/recovery action.
* Simulator has keyboard, touch buttons and card-presence toggle.

## Safety gates before *any* persistent replacement

- [ ] Export snapshot and verify full offline restore from Android.
- [ ] Confirm exact physical Flash layout, boot code, calibration and protected sectors.
- [ ] Verify FM1 USB-C role/CC wiring, VBUS and safe power to external reader.
- [ ] Run RAM diagnostic on a physical FM1 only after safe transport review.
- [ ] Confirm real reader enumeration and FAT32 read-only file access.
- [ ] Verify firmware type / map / XIP compatibility and a recoverable stage-0.
- [ ] Simulate interrupted installation and verify target recovery mechanism.
- [ ] Obtain explicit permission for the **specific** destructive installation, separately from the general product goal.

## Implementation checkpoints

A1 (this branch): architecture + offline browser Cover Flow + pure reducer tests.
A2: portable rendering primitives and asset conversion, first tested on host; hardware LCD later.
A3: bounded FAT32 catalog reader (simulation before hardware); VBUS investigation.
A4: verify safe launcher/backends, Android manager export, end-to-end test.
A5: persistent deployment only after the safety gates above.

No modification of the existing Fase 1 or Fase 2 code is required for A1.
