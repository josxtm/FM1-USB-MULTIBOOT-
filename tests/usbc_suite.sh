#!/usr/bin/env bash
set -eu
test_binary="$1"
tmp="$(mktemp -d build/usbc-test.XXXXXX)"
trap 'rm -rf "$tmp"' EXIT
fixture=tests/fixtures/manager-alpha03/FM1
python3 scripts/verify_manager_card.py "$fixture" --files --flat
python3 scripts/generate_fat32_image.py "$tmp/card.img" --manager-fixture "$fixture" --mbr
"$test_binary" "$tmp/card.img" 2 android build/usbc-explorer.ppm
# The legacy and synthetic fixtures use superfloppy unless noted.
mkdir -p "$tmp/v1" "$tmp/empty"
cp -r "$fixture/INDEX.CSV" "$fixture/FW" "$tmp/v1/"
python3 scripts/generate_fat32_image.py "$tmp/v1.img" --manager-fixture "$tmp/v1"
"$test_binary" "$tmp/v1.img" 2 v1
"$test_binary" "$tmp/v1.img" 0 exfat
"$test_binary" "$tmp/v1.img" 0 bad-fat
python3 scripts/generate_fat32_image.py "$tmp/empty.img" --manager-fixture "$tmp/empty"
"$test_binary" "$tmp/empty.img" 0 missing-index
for n in 250 410 1024 4096; do
    python3 tests/usbc_fixture.py "$tmp/card$n" "$n"
    python3 scripts/generate_fat32_image.py "$tmp/card$n.img" --manager-fixture "$tmp/card$n"
    "$test_binary" "$tmp/card$n.img" "$n" matrix
done
for fault in page index tie file missing; do
    python3 tests/usbc_fixture.py "$tmp/$fault" 65 --fault "$fault"
    python3 scripts/generate_fat32_image.py "$tmp/$fault.img" --manager-fixture "$tmp/$fault"
    "$test_binary" "$tmp/$fault.img" 65 "$fault"
done
