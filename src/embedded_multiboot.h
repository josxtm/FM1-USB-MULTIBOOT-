/* SPDX-License-Identifier: MIT */
#ifndef FM1_EMBEDDED_MULTIBOOT_H
#define FM1_EMBEDDED_MULTIBOOT_H
#ifndef FM1_MB_TYPES
#define FM1_MB_TYPES
typedef unsigned char uint8_t;
typedef unsigned short uint16_t;
typedef unsigned int uint32_t;
typedef unsigned long long mb_u64;
#endif

#define MB_MAX_ENTRIES 64u
#define MB_MAX_CATALOG 24576u
#define MB_MAX_IMAGE (4u * 1024u * 1024u)
#define MB_ID_MAX 8u
#define MB_LABEL_MAX 24u
#define MB_VERSION_MAX 16u

enum mb_state { MB_BOOT, MB_NO_STORAGE, MB_LOADING_CATALOG, MB_EMPTY, MB_BROWSE,
    MB_INSPECT, MB_VERIFY, MB_READY_FOR_SIMULATION, MB_INCOMPATIBLE, MB_ERROR,
    MB_RECOVERY };
/* A successful read returns bytes; EOF is normal only at the end of a file. */
enum mb_io_result { MB_IO_OK, MB_IO_EOF, MB_IO_MISSING, MB_IO_DISCONNECTED, MB_IO_TIMEOUT, MB_IO_ERROR };
enum mb_launch_result { MB_LAUNCH_BLOCKED, MB_LAUNCH_UNSUPPORTED, MB_LAUNCH_INCOMPATIBLE,
    MB_LAUNCH_INSUFFICIENT_MEMORY, MB_LAUNCH_UNVERIFIED, MB_LAUNCH_READY_FOR_SOFTWARE_SIMULATION };
enum mb_event { MB_EVENT_PREVIOUS, MB_EVENT_NEXT, MB_EVENT_PLAY, MB_EVENT_REC, MB_EVENT_CANCEL };

struct mb_entry { char id[MB_ID_MAX + 1], label[MB_LABEL_MAX + 1], version[MB_VERSION_MAX + 1];
    char file[13], sha256[65]; uint32_t size; uint8_t metadata_valid, file_present, integrity_verified;
    uint8_t compatibility_verified; };
struct mb_catalog { struct mb_entry entries[MB_MAX_ENTRIES]; uint32_t count; };
struct mb_storage { void *context; enum mb_io_result (*read)(void *, const char *, uint32_t, uint8_t *, uint32_t, uint32_t *); };
struct mb_renderer { void *context; void (*begin)(void *); void (*card)(void *, const struct mb_entry *, int, int); void (*text)(void *, const char *); void (*end)(void *); };
struct mb_core { enum mb_state state; struct mb_catalog catalog; struct mb_storage storage; struct mb_renderer renderer; uint32_t selected; uint8_t cancelled; char error[64]; };

void mb_init(struct mb_core *, struct mb_storage, struct mb_renderer);
enum mb_io_result mb_load_catalog(struct mb_core *);
enum mb_io_result mb_verify_selected(struct mb_core *);
void mb_event(struct mb_core *, enum mb_event);
const struct mb_entry *mb_selected(const struct mb_core *);
enum mb_launch_result mb_prepare_launch(const struct mb_core *);
void mb_render(const struct mb_core *);
const char *mb_state_name(enum mb_state);
#endif
