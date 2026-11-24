#!/usr/bin/env bash
set -euo pipefail
# Run in a fresh CI directory. Only attach and partition our new image file.
src=$(realpath "$(dirname "$0")/../zero/catalog_server.py")
base=2026-10-06-raspios-trixie-armhf-lite.img.xz
curl --fail --location --retry 3 -o "$base" "https://downloads.raspberrypi.com/raspios_lite_armhf/images/raspios_lite_armhf-2026-10-06/$base"
echo "2c302771f34c67a0b7ae7aa2e0dcdd3eac660504be59193c011ba79d412e1632  $base" | sha256sum -c -
img=FM1-Zero-W.img
test ! -e "$img"
xz -dc "$base" > "$img"
# A sparse image with room for minor capacity differences between 32 GB cards.
truncate -s 31500000256 "$img"
parted -s "$img" unit s resizepart 2 16777215s
parted -s "$img" unit s mkpart primary fat32 16777216s 100%
dev=$(losetup --find --show --partscan "$img")
tmp=$(mktemp -d)
trap 'umount -R "$tmp" 2>/dev/null || true; losetup -d "$dev"' EXIT
udevadm settle
e2fsck -fp "${dev}p2" || test "$?" -eq 1
resize2fs "${dev}p2"
mkfs.vfat -F 32 -n FM1DATA "${dev}p3"
mount "${dev}p2" "$tmp"
mount "${dev}p1" "$tmp/boot/firmware"
sed -Ei 's@ ?init=[^ ]*firstboot[^ ]*@@g;s@ ?init=[^ ]*init_resize[^ ]*@@g' "$tmp/boot/firmware/cmdline.txt"
mkdir -p "$tmp/mnt/FM1DATA"
mount "${dev}p3" "$tmp/mnt/FM1DATA"
mkdir -p "$tmp/mnt/FM1DATA/FM1/FIRMWARES"
printf 'FM1DATA-V1\n' > "$tmp/mnt/FM1DATA/FM1/ZERO.ID"
echo 'LABEL=FM1DATA /mnt/FM1DATA vfat defaults,nofail,umask=022 0 0' >> "$tmp/etc/fstab"
install -Dm644 "$src" "$tmp/opt/fm1/catalog.py"
install -Dm755 "$(dirname "$src")/../image/start-network.sh" "$tmp/opt/fm1/start-network"
cat > "$tmp/etc/systemd/system/fm1.service" <<'UNIT'
[Unit]
After=NetworkManager.service
Requires=NetworkManager.service
RequiresMountsFor=/mnt/FM1DATA
[Service]
Type=simple
ExecStartPre=+/opt/fm1/start-network
ExecStart=/usr/bin/python3 /opt/fm1/catalog.py --bind 10.42.0.1 --port 8080
User=nobody
Restart=on-failure
RestartSec=8
[Install]
WantedBy=multi-user.target
UNIT
mkdir -p "$tmp/etc/systemd/system/multi-user.target.wants"
ln -s ../fm1.service "$tmp/etc/systemd/system/multi-user.target.wants/fm1.service"
sync
umount -R "$tmp"
losetup -d "$dev"
trap - EXIT
xz -T2 -2 "$img"
sha256sum "$img.xz" > "$img.xz.sha256"
