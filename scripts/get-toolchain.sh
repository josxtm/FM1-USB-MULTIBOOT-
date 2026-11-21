#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
set -eu
dest=${1:-toolchain}
version=23.1.5-2
archive=pi32v2-llvm-$version-linux-x64.tar.xz
sum=cb896562418a2be93edb9bac26af4bc81a106a32974002a78e6b9fc203c93990
test "$(uname -s)" = Linux && test "$(uname -m)" = x86_64
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
curl -fLSs --retry 3 "https://github.com/jvitkauskas/pi32v2-llvm/releases/download/pi32v2-$version/$archive" -o "$scratch/$archive"
(cd "$scratch" && printf '%s  %s\n' "$sum" "$archive" | sha256sum -c)
mkdir -p "$dest"
tar -xJf "$scratch/$archive" --strip-components=1 -C "$dest"
"$dest/bin/clang" --version
