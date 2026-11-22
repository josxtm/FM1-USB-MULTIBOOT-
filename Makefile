TOOLCHAIN ?= toolchain
CC_HOST ?= cc
CLANG = $(TOOLCHAIN)/bin/clang
LD = $(TOOLCHAIN)/bin/ld.lld
OBJCOPY = $(TOOLCHAIN)/bin/llvm-objcopy
OBJDUMP = $(TOOLCHAIN)/bin/llvm-objdump
READELF = $(TOOLCHAIN)/bin/llvm-readelf
CFLAGS = -target pi32v2 -mcpu=r3 -Os -std=c99 -ffreestanding -fno-builtin -fno-stack-protector \
         -ffunction-sections -fdata-sections -Wall -Wextra -Werror -Isrc -DBOARD_ID=1 -DDISP_BOARD=1
C_FILES = main storage msc_bot usb_diag_log fat32 husb timer lcd keys watchdog runtime
OBJECTS = $(addprefix build/,$(addsuffix .o,$(C_FILES))) build/start.o

.PHONY: all test core-test render-demo render-matrix memory-report core-sanitize fat32-image fat32-integration fat32-sanitize fm1cat-conformance msc-bot-test msc-fat32-test msc-fat32-sanitize usb-diag-log-test lcd-renderer-test ram-app-test usb-storage-test presets-test list-renderer-test launch-policy-test firmware-tools-test virtual-installer-test boot-recovery-test flash-backup-audit-test clock-test timer-test ram-image-validator-test ram-minimal-test ram-minimal-pi32v2-object ram-minimal-image ram-app-pi32v2-image ram-app-pi32v2-full-image core-pi32v2-object core-pi32v2-sim fat32-pi32v2-object fat32-pi32v2-sim ram-app-pi32v2-object ram-app-pi32v2-sim clean
all: build/SHA256SUMS.txt

STG2_C = stg2_selector stg2_selector_platform stg2_sd embedded_multiboot embedded_fat32_storage \
         fat32 embedded_lcd_list_renderer embedded_lcd_sink lcd keys embedded_keys embedded_clock timer runtime
STG2_OBJECTS = $(addprefix build/stg2/,$(addsuffix .o,$(STG2_C))) build/stg2/stg2_selector_start.o

.PHONY: stg2-selector stg2-host-test stg2-v24-test
stg2-selector: build/stg2/fm1-multiboot-selector-stg2.bin

STG2_HOST = src/stg2_selector.c src/stg2_sd.c src/embedded_multiboot.c \
            src/embedded_fat32_storage.c src/fat32.c src/embedded_lcd_list_renderer.c
build/stg2-selector-test: tests/stg2_selector_test.c $(STG2_HOST) $(wildcard src/*.h)
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc $(filter %.c,$^) -o $@

build/stg2-spi-mmio-test: tests/stg2_spi_mmio_test.c src/stg2_selector_platform.c $(wildcard src/*.h)
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -ffunction-sections -fdata-sections -Wl,--gc-sections -Isrc $< -o $@

stg2-host-test: build/stg2-selector-test build/stg2-spi-mmio-test
	python3 -m unittest tests/stg2_image_test.py
	./build/stg2-spi-mmio-test
	@set -e; tmp=$$(mktemp -d build/stg2-test.XXXXXX); trap 'rm -rf "$$tmp"' EXIT; \
		python3 scripts/generate_fat32_image.py "$$tmp/card.img" 12 >/dev/null; \
		./build/stg2-selector-test "$$tmp/card.img"

MBOOT24 ?= build/m-bootloader-v24
stg2-v24-test: stg2-selector
	test "$$(git -C "$(MBOOT24)" rev-parse HEAD)" = 395e6cc469ba1d6eae9922a752d1f416ff07b69a
	$(CC_HOST) -std=c11 -O2 -Wall -Wextra -Werror -DPLATFORM_SIM \
		-I"$(MBOOT24)/include" -I"$(MBOOT24)/tests" tests/stg2_v24_bridge.c "$(MBOOT24)/src/core.c" -o build/stg2-v24-test
	./build/stg2-v24-test build/stg2/fm1-multiboot-selector-stg2.bin

build/stg2/%.o: src/%.c $(wildcard src/*.h) Makefile
	@mkdir -p build/stg2
	$(CLANG) $(CFLAGS) -DMB_NATIVE_STG2 -fstack-usage -c $< -o $@

build/stg2/stg2_selector_start.o: src/stg2_selector_start.S
	@mkdir -p build/stg2
	$(CLANG) $(CFLAGS) -c $< -o $@

build/stg2/fm1-multiboot-selector-stg2.elf: $(STG2_OBJECTS) src/stg2_selector.ld
	$(LD) --gc-sections --no-undefined -Map build/stg2/fm1-multiboot-selector-stg2.map -T src/stg2_selector.ld -o $@ $(STG2_OBJECTS)

build/stg2/fm1-multiboot-selector-stg2.bin: build/stg2/fm1-multiboot-selector-stg2.elf scripts/validate_stg2.py Makefile
	$(OBJCOPY) -O binary -j .image $< build/stg2/selector.flat
	$(READELF) -h -l -S -s $< > build/stg2/elf-report.txt
	$(OBJDUMP) -d $< > build/stg2/disassembly.txt
	python3 scripts/validate_stg2.py $< build/stg2/selector.flat $@ build/stg2/ABI.txt build/stg2/disassembly.txt
	awk -F '\t' 'NF!=3 || $$3!="static" {bad=1} {sum+=$$2; if ($$2>max) {max=$$2; frame=$$1}} END { \
		printf "Largest static frame: %s (%u B)\nSum of all emitted static frames: %u B / 8192 B\n", frame, max, sum; \
		exit (bad || max==0 || sum>8192)}' build/stg2/*.su > build/stg2/stack-report.txt
	cd build/stg2 && sha256sum fm1-multiboot-selector-stg2.bin fm1-multiboot-selector-stg2.elf > SHA256SUMS.txt

core-pi32v2-object: build/embedded_multiboot_pi32v2.o

core-pi32v2-sim: build/embedded_multiboot_pi32v2.elf

fat32-pi32v2-object: build/embedded_fat32_multiboot_pi32v2.o build/embedded_fat32_pi32v2.o build/fat32_pi32v2.o build/msc_bot_pi32v2.o build/usb_diag_log_pi32v2.o build/embedded_lcd_renderer_pi32v2.o build/embedded_keys_pi32v2.o build/embedded_fat32_pi32v2_sim.o build/embedded_fat32_pi32v2_runtime.o

fat32-pi32v2-sim: build/embedded_fat32_pi32v2.elf

ram-app-pi32v2-object: build/embedded_ram_app_pi32v2.o build/embedded_presets_pi32v2.o build/mb_launch_policy_pi32v2.o build/embedded_ram_app_renderer_pi32v2.o build/embedded_lcd_sink_pi32v2.o build/embedded_ram_app_keys_pi32v2.o build/embedded_ram_app_physical_keys_pi32v2.o build/embedded_ram_app_lcd_pi32v2.o build/embedded_ram_app_timer_pi32v2.o build/embedded_ram_app_clock_pi32v2.o build/embedded_ram_app_runtime_pi32v2.o build/mb_app_start_pi32v2.o

ram-app-pi32v2-sim: build/embedded_ram_app_pi32v2.elf

ram-app-pi32v2-image: build/embedded_ram_app_pi32v2.elf scripts/app_trailer.py scripts/validate_ram_image.py
	$(OBJCOPY) -O binary -j .image build/embedded_ram_app_pi32v2.elf build/embedded_ram_app_pi32v2.flat.bin
	python3 scripts/app_trailer.py build/embedded_ram_app_pi32v2.flat.bin build/embedded_ram_app_pi32v2.bin fm1
	$(READELF) -h -l -S -s build/embedded_ram_app_pi32v2.elf > build/embedded_ram_app_pi32v2.elf-report.txt
	python3 scripts/validate_ram_image.py build/embedded_ram_app_pi32v2.elf build/embedded_ram_app_pi32v2.flat.bin build/embedded_ram_app_pi32v2.bin

build/embedded_presets_pi32v2.o: src/embedded_presets.c src/embedded_presets.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/mb_launch_policy_pi32v2.o: src/mb_launch_policy.c src/mb_launch_policy.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_pi32v2.o: src/embedded_ram_app.c src/embedded_ram_app.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_full_pi32v2.o: src/embedded_ram_app.c src/embedded_ram_app.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -DMB_USB_STORAGE_LINKED -DMB_TEXT_SELECTOR -c $< -o $@

build/embedded_usb_msc_storage_pi32v2.o: src/embedded_usb_msc_storage.c src/embedded_usb_msc_storage.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/husb_pi32v2.o: src/husb.c src/husb.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/storage_pi32v2.o: src/storage.c src/storage.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_lcd_sink_pi32v2.o: src/embedded_lcd_sink.c src/embedded_lcd_renderer.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_list_renderer_pi32v2.o: src/embedded_lcd_list_renderer.c src/embedded_lcd_list_renderer.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_renderer_pi32v2.o: src/embedded_lcd_renderer.c src/embedded_lcd_renderer.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_keys_pi32v2.o: src/embedded_keys.c src/embedded_keys.h src/embedded_multiboot.h src/mbl.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_lcd_pi32v2.o: src/lcd.c src/lcd.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_physical_keys_pi32v2.o: src/keys.c src/keys.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_timer_pi32v2.o: src/timer.c src/usbio.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_clock_pi32v2.o: src/embedded_clock.c src/embedded_clock.h src/mbl.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_runtime_pi32v2.o: src/runtime.c src/mbl.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/mb_app_start_pi32v2.o: src/multiboot_ram_start.S
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_ram_app_pi32v2.elf: build/embedded_ram_app_pi32v2.o build/embedded_presets_pi32v2.o build/mb_launch_policy_pi32v2.o build/embedded_ram_app_renderer_pi32v2.o build/embedded_lcd_sink_pi32v2.o build/embedded_ram_app_keys_pi32v2.o build/embedded_ram_app_physical_keys_pi32v2.o build/embedded_ram_app_lcd_pi32v2.o build/embedded_ram_app_timer_pi32v2.o build/embedded_ram_app_clock_pi32v2.o build/embedded_ram_app_runtime_pi32v2.o build/mb_app_start_pi32v2.o build/embedded_multiboot_pi32v2.o src/multiboot_ram.ld
	$(LD) --gc-sections --no-undefined -Map build/embedded_ram_app_pi32v2.map -T src/multiboot_ram.ld -o $@ $(filter %.o,$^)

RAM_FULL_OBJECTS = build/embedded_ram_app_full_pi32v2.o build/embedded_presets_pi32v2.o build/mb_launch_policy_pi32v2.o build/embedded_ram_app_list_renderer_pi32v2.o build/embedded_lcd_sink_pi32v2.o build/embedded_ram_app_keys_pi32v2.o build/embedded_ram_app_physical_keys_pi32v2.o build/embedded_ram_app_lcd_pi32v2.o build/embedded_ram_app_timer_pi32v2.o build/embedded_ram_app_clock_pi32v2.o build/embedded_ram_app_runtime_pi32v2.o build/mb_app_start_pi32v2.o build/embedded_multiboot_pi32v2.o build/embedded_usb_msc_storage_pi32v2.o build/embedded_fat32_pi32v2.o build/fat32_pi32v2.o build/msc_bot_pi32v2.o build/usb_diag_log_pi32v2.o build/husb_pi32v2.o build/storage_pi32v2.o

build/ram-app-usb-full.pi32v2.elf: $(RAM_FULL_OBJECTS) src/multiboot_ram.ld
	$(LD) --gc-sections --no-undefined -Map build/ram-app-usb-full.map -T src/multiboot_ram.ld -o $@ $(filter %.o,$^)

ram-app-pi32v2-full-image: build/ram-app-usb-full.pi32v2.elf scripts/app_trailer.py scripts/validate_ram_image.py
	$(OBJCOPY) -O binary -j .image $< build/ram-app-usb-full.flat.bin
	python3 scripts/app_trailer.py build/ram-app-usb-full.flat.bin build/ram-app-usb-full.bin fm1
	$(READELF) -h -l -S -s $< > build/ram-app-usb-full.elf-report.txt
	$(OBJDUMP) -d $< > build/ram-app-usb-full.dis
	python3 scripts/validate_ram_image.py $< build/ram-app-usb-full.flat.bin build/ram-app-usb-full.bin
	sha256sum build/ram-app-usb-full.bin build/ram-app-usb-full.pi32v2.elf > build/ram-app-usb-full.SHA256SUMS.txt

build/embedded_fat32_multiboot_pi32v2.o: src/embedded_multiboot.c src/embedded_multiboot.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_fat32_pi32v2.o: src/embedded_fat32_storage.c src/embedded_fat32_storage.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/fat32_pi32v2.o: src/fat32.c src/storage.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_fat32_pi32v2_sim.o: src/embedded_fat32_pi32v2_sim.c src/embedded_fat32_storage.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/msc_bot_pi32v2.o: src/msc_bot.c src/msc_bot.h src/mbl.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/usb_diag_log_pi32v2.o: src/usb_diag_log.c src/usb_diag_log.h src/mbl.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_lcd_renderer_pi32v2.o: src/embedded_lcd_renderer.c src/embedded_lcd_renderer.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_keys_pi32v2.o: src/embedded_keys.c src/embedded_keys.h src/embedded_multiboot.h src/mbl.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_fat32_pi32v2_runtime.o: src/runtime.c src/mbl.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_fat32_pi32v2.elf: build/embedded_fat32_multiboot_pi32v2.o build/embedded_fat32_pi32v2.o build/fat32_pi32v2.o build/msc_bot_pi32v2.o build/usb_diag_log_pi32v2.o build/embedded_fat32_pi32v2_sim.o build/embedded_fat32_pi32v2_runtime.o src/embedded_multiboot.ld
	$(LD) -nostdlib -Map build/embedded_fat32_pi32v2.map -T src/embedded_multiboot.ld -e embedded_fat32_sim_entry -o $@ $^

build/embedded_multiboot_pi32v2.o: src/embedded_multiboot.c src/embedded_multiboot.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_multiboot_sim_pi32v2.o: src/embedded_multiboot_sim.c src/embedded_multiboot.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_multiboot_runtime_pi32v2.o: src/runtime.c src/mbl.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/embedded_multiboot_pi32v2.elf: build/embedded_multiboot_pi32v2.o build/embedded_multiboot_sim_pi32v2.o build/embedded_multiboot_runtime_pi32v2.o src/embedded_multiboot.ld
	$(LD) -nostdlib -Map build/embedded_multiboot_pi32v2.map -T src/embedded_multiboot.ld -o $@ build/embedded_multiboot_pi32v2.o build/embedded_multiboot_sim_pi32v2.o build/embedded_multiboot_runtime_pi32v2.o

core-test: build/embedded_multiboot_test
	./build/embedded_multiboot_test

render-demo: build/embedded_render_demo
	./build/embedded_render_demo

render-matrix: build/embedded_render_matrix
	./build/embedded_render_matrix

fat32-image:
	python3 scripts/generate_fat32_image.py build/fat32-test.img 3

fat32-integration: build/fat32_multiboot_integration
	@for count in 0 1 2 3 64; do \
		python3 scripts/generate_fat32_image.py build/fat32-test.img $$count >/dev/null; \
		./build/fat32_multiboot_integration build/fat32-test.img '' $$count build/fat32-coverflow-$$count.svg; \
	done
	python3 scripts/generate_fat32_image.py build/fat32-test.img 3 >/dev/null
	./build/fat32_multiboot_integration build/fat32-test.img disconnect
	./build/fat32_multiboot_integration build/fat32-test.img timeout
	./build/fat32_multiboot_integration build/fat32-test.img bad-bpb
	./build/fat32_multiboot_integration build/fat32-test.img bad-volume
	./build/fat32_multiboot_integration build/fat32-test.img cycle
	./build/fat32_multiboot_integration build/fat32-test.img truncated
	./build/fat32_multiboot_integration build/fat32-test.img hash

build/fat32_multiboot_integration: tests/fat32_multiboot_integration.c src/embedded_fat32_storage.c src/embedded_fat32_storage.h src/embedded_multiboot.c src/embedded_multiboot.h src/fat32.c src/storage.c src/storage.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/fat32_multiboot_integration.c src/embedded_fat32_storage.c src/embedded_multiboot.c src/fat32.c -o $@

fm1cat-conformance: build/fm1cat_conformance_test
	./build/fm1cat_conformance_test

msc-bot-test: build/msc_bot_test
	./build/msc_bot_test

usb-diag-log-test: build/usb_diag_log_test
	./build/usb_diag_log_test

lcd-renderer-test: build/embedded_lcd_renderer_test
	./build/embedded_lcd_renderer_test

ram-app-test: build/embedded_ram_app_test
	./build/embedded_ram_app_test

usb-storage-test: build/embedded_usb_storage_test
	python3 scripts/generate_fat32_image.py build/fat32-test.img 3 >/dev/null
	./build/embedded_usb_storage_test build/fat32-test.img

build/embedded_usb_storage_test: tests/embedded_usb_storage_test.c src/embedded_usb_msc_storage.c src/embedded_fat32_storage.c src/fat32.c src/embedded_multiboot.c
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc $^ -o $@

firmware-tools-test:
	python3 -m unittest tests/firmware_tools_test.py

virtual-installer-test:
	python3 -m unittest tests/virtual_flash_installer_test.py

.PHONY: virtual-slotfile-test virtual-slotfile-demo
virtual-slotfile-test:
	python3 -m unittest -v tests/virtual_slotfile_test.py

virtual-slotfile-demo: build/msc_fat32_integration build/fat32_multiboot_integration
	@set -e; work=$$(mktemp -d build/slotfile.XXXXXX); trap 'rm -rf "$$work"' EXIT; \
		python3 scripts/virtual_slotfile.py demo "$$work/case" > build/virtual-slotfile-trace.json; \
		python3 scripts/generate_fat32_image.py "$$work/card.img" --slotfile-card "$$work/case/card"; \
		./build/msc_fat32_integration "$$work/card.img" 2; \
		./build/fat32_multiboot_integration "$$work/card.img" '' 2

boot-recovery-test:
	python3 -m unittest tests/boot_recovery_model_test.py

flash-backup-audit-test:
	python3 -m unittest tests/audit_flash_backup_test.py

presets-test: build/presets_encoder_test
	./build/presets_encoder_test

list-renderer-test: build/embedded_lcd_list_test
	./build/embedded_lcd_list_test

build/embedded_lcd_list_test: tests/embedded_lcd_list_test.c src/embedded_lcd_list_renderer.c src/embedded_lcd_list_renderer.h src/embedded_multiboot.c $(wildcard src/*.h)
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/embedded_lcd_list_test.c src/embedded_lcd_list_renderer.c src/embedded_multiboot.c -o $@

build/presets_encoder_test: tests/presets_encoder_test.c src/embedded_presets.c src/embedded_presets.h src/embedded_multiboot.h src/mbl.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/presets_encoder_test.c src/embedded_presets.c -o $@

launch-policy-test: build/mb_launch_policy_test
	./build/mb_launch_policy_test

clock-test: build/embedded_clock_test
	./build/embedded_clock_test

timer-test: build/timer_arithmetic_test
	./build/timer_arithmetic_test

build/timer_arithmetic_test: tests/timer_arithmetic_test.c src/embedded_clock.c src/embedded_clock.h src/mbl.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/timer_arithmetic_test.c src/embedded_clock.c -o $@

ram-minimal-test: build/ram_minimal_test
	./build/ram_minimal_test

build/ram_minimal_test: tests/ram_minimal_test.c src/ram_minimal.c src/mbl.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/ram_minimal_test.c src/ram_minimal.c -o $@

ram-minimal-pi32v2-object: build/ram_minimal_pi32v2.o

ram-minimal-image: build/ram_minimal_pi32v2.elf scripts/app_trailer.py scripts/validate.py
	$(OBJCOPY) -O binary -j .image build/ram_minimal_pi32v2.elf build/ram_minimal.flat.bin
	python3 scripts/app_trailer.py build/ram_minimal.flat.bin build/ram_minimal.bin fm1
	$(READELF) -h -l -S -s build/ram_minimal_pi32v2.elf > build/ram_minimal.elf-report.txt
	$(OBJDUMP) -d build/ram_minimal_pi32v2.elf > build/ram_minimal.dis
	python3 scripts/validate.py build/ram_minimal_pi32v2.elf build/ram_minimal.flat.bin build/ram_minimal.bin
	sha256sum build/ram_minimal.bin build/ram_minimal_pi32v2.elf > build/ram_minimal.SHA256SUMS.txt

build/ram_minimal_pi32v2.o: src/ram_minimal.c src/mbl.h
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/ram_minimal_start_pi32v2.o: src/ram_minimal_start.S
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/ram_minimal_pi32v2.elf: build/ram_minimal_pi32v2.o build/ram_minimal_start_pi32v2.o src/ram_minimal.ld
	$(LD) --gc-sections --no-undefined -Map build/ram_minimal.map -T src/ram_minimal.ld -o $@ $(filter %.o,$^)

build/embedded_clock_test: tests/embedded_clock_test.c src/embedded_clock.c src/embedded_clock.h src/mbl.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/embedded_clock_test.c src/embedded_clock.c -o $@

ram-image-validator-test:
	python3 -m py_compile scripts/validate_ram_image.py

build/embedded_ram_app_test: tests/embedded_ram_app_test.c src/embedded_ram_app.c src/embedded_ram_app.h src/embedded_lcd_renderer.c src/embedded_keys.c src/embedded_clock.c src/embedded_multiboot.c $(wildcard src/*.h)
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/embedded_ram_app_test.c src/embedded_ram_app.c src/embedded_lcd_renderer.c src/embedded_lcd_sink.c src/embedded_keys.c src/embedded_presets.c src/embedded_clock.c src/embedded_multiboot.c src/mb_launch_policy.c -o $@

build/mb_launch_policy_test: tests/mb_launch_policy_test.c src/mb_launch_policy.c src/mb_launch_policy.h src/embedded_multiboot.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/mb_launch_policy_test.c src/mb_launch_policy.c -o $@

build/embedded_lcd_renderer_test: tests/embedded_lcd_renderer_test.c src/embedded_lcd_renderer.c src/embedded_lcd_renderer.h src/embedded_lcd_sink.c src/embedded_keys.c src/embedded_keys.h src/embedded_multiboot.c $(wildcard src/*.h)
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/embedded_lcd_renderer_test.c src/embedded_lcd_renderer.c src/embedded_lcd_sink.c src/embedded_keys.c src/embedded_multiboot.c -o $@

build/usb_diag_log_test: tests/usb_diag_log_test.c src/usb_diag_log.c src/usb_diag_log.h src/mbl.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/usb_diag_log_test.c src/usb_diag_log.c -o $@

msc-fat32-test: build/msc_fat32_integration
	@for count in 0 1 2 3 64; do \
		python3 scripts/generate_fat32_image.py build/fat32-test.img $$count >/dev/null; \
		./build/msc_fat32_integration build/fat32-test.img $$count; \
	done

build/msc_fat32_integration: tests/msc_fat32_integration.c src/msc_bot.c src/fat32.c src/embedded_fat32_storage.c src/embedded_multiboot.c src/storage.h src/msc_bot.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/msc_fat32_integration.c src/msc_bot.c src/fat32.c src/embedded_fat32_storage.c src/embedded_multiboot.c -o $@

msc-fat32-sanitize: build/msc_fat32_sanitize
	python3 scripts/generate_fat32_image.py build/fat32-test.img 3 >/dev/null
	./build/msc_fat32_sanitize build/fat32-test.img 3

build/msc_fat32_sanitize: tests/msc_fat32_integration.c src/msc_bot.c src/fat32.c src/embedded_fat32_storage.c src/embedded_multiboot.c
	@mkdir -p build
	$(CC_HOST) -std=c99 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined -fno-omit-frame-pointer -Isrc tests/msc_fat32_integration.c src/msc_bot.c src/fat32.c src/embedded_fat32_storage.c src/embedded_multiboot.c -o $@

build/msc_bot_test: tests/msc_bot_test.c src/msc_bot.c src/msc_bot.h src/mbl.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/msc_bot_test.c src/msc_bot.c -o $@

build/fm1cat_conformance_test: tests/fm1cat_conformance_test.c src/embedded_multiboot.c src/embedded_multiboot.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/fm1cat_conformance_test.c src/embedded_multiboot.c -o $@

fat32-sanitize: build/fat32_multiboot_sanitize
	python3 scripts/generate_fat32_image.py build/fat32-test.img 3 >/dev/null
	./build/fat32_multiboot_sanitize build/fat32-test.img

build/fat32_multiboot_sanitize: tests/fat32_multiboot_integration.c src/embedded_fat32_storage.c src/embedded_multiboot.c src/fat32.c
	@mkdir -p build
	$(CC_HOST) -std=c99 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined -fno-omit-frame-pointer -Isrc tests/fat32_multiboot_integration.c src/embedded_fat32_storage.c src/embedded_multiboot.c src/fat32.c -o $@

build/embedded_render_matrix: tests/embedded_render_matrix.c src/embedded_multiboot.c src/embedded_multiboot.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/embedded_render_matrix.c src/embedded_multiboot.c -o $@

memory-report: build/memory_report
	./build/memory_report | tee build/memory-budget.txt

core-sanitize: build/embedded_multiboot_sanitize
	./build/embedded_multiboot_sanitize

build/memory_report: tests/memory_report.c src/embedded_multiboot.h src/embedded_fat32_storage.h src/embedded_lcd_renderer.h src/embedded_keys.h src/storage.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/memory_report.c -o $@

build/embedded_multiboot_sanitize: tests/embedded_multiboot_test.c src/embedded_multiboot.c src/embedded_multiboot.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined -fno-omit-frame-pointer -Isrc tests/embedded_multiboot_test.c src/embedded_multiboot.c -o $@

build/embedded_render_demo: tests/embedded_render_demo.c src/embedded_multiboot.c src/embedded_multiboot.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/embedded_render_demo.c src/embedded_multiboot.c -o $@

build/embedded_multiboot_test: tests/embedded_multiboot_test.c src/embedded_multiboot.c src/embedded_multiboot.h
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/embedded_multiboot_test.c src/embedded_multiboot.c -o $@

build/%.o: src/%.c $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/start.o: src/start.S
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

build/fm1-usb-diagnostics.elf: $(OBJECTS) src/ram.ld
	$(LD) --gc-sections --no-undefined -Map build/fm1-usb-diagnostics.map -T src/ram.ld -o $@ $(OBJECTS)

build/fm1-usb-diagnostics-ram-experimental.bin: build/fm1-usb-diagnostics.elf scripts/app_trailer.py
	$(OBJCOPY) -O binary -j .image $< build/flat.bin
	python3 scripts/app_trailer.py build/flat.bin $@ fm1
	$(OBJDUMP) -d $< > build/fm1-usb-diagnostics.dis

build/SHA256SUMS.txt: build/fm1-usb-diagnostics-ram-experimental.bin scripts/validate.py
	$(READELF) -h -l -S -s build/fm1-usb-diagnostics.elf > build/elf-report.txt
	python3 scripts/validate.py build/fm1-usb-diagnostics.elf build/flat.bin $<
	cd build && sha256sum fm1-usb-diagnostics-ram-experimental.bin fm1-usb-diagnostics.elf > SHA256SUMS.txt
	cd build && sha256sum -c SHA256SUMS.txt

test:
	@mkdir -p build
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/usb_storage_test.c src/storage.c src/msc_bot.c src/fat32.c -o build/usb_storage_test
	./build/usb_storage_test

clean:
	rm -rf build
