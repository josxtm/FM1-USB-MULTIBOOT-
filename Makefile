TOOLCHAIN ?= toolchain
CC_HOST ?= cc
CLANG = $(TOOLCHAIN)/bin/clang
LD = $(TOOLCHAIN)/bin/ld.lld
OBJCOPY = $(TOOLCHAIN)/bin/llvm-objcopy
OBJDUMP = $(TOOLCHAIN)/bin/llvm-objdump
READELF = $(TOOLCHAIN)/bin/llvm-readelf
CFLAGS = -target pi32v2 -mcpu=r3 -Os -std=c99 -ffreestanding -fno-builtin -fno-stack-protector \
         -ffunction-sections -fdata-sections -Wall -Wextra -Werror -Isrc -DBOARD_ID=1 -DDISP_BOARD=1
C_FILES = main storage fat32 husb timer lcd keys watchdog runtime
OBJECTS = $(addprefix build/,$(addsuffix .o,$(C_FILES))) build/start.o

.PHONY: all test core-test core-pi32v2-object clean
all: build/SHA256SUMS.txt

core-pi32v2-object: build/embedded_multiboot_pi32v2.o

build/embedded_multiboot_pi32v2.o: src/embedded_multiboot.c src/embedded_multiboot.h $(wildcard src/*.h)
	@mkdir -p build
	$(CLANG) $(CFLAGS) -c $< -o $@

core-test: build/embedded_multiboot_test
	./build/embedded_multiboot_test

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
	$(CC_HOST) -std=c99 -O2 -Wall -Wextra -Werror -Isrc tests/usb_storage_test.c src/storage.c src/fat32.c -o build/usb_storage_test
	./build/usb_storage_test

clean:
	rm -rf build
