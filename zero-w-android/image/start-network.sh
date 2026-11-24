#!/bin/sh
set -eu
# Private credential supplied by the owner on their card before boot.
passfile=/mnt/FM1DATA/FM1/WIFI.TXT
if ! nmcli connection show FM1-Zero >/dev/null 2>&1; then
    test -s "$passfile"
    pass=$(tr -d '\r\n' < "$passfile")
    test "${#pass}" -ge 8 && test "${#pass}" -le 63 || exit 1
    nmcli connection add type wifi ifname wlan0 con-name FM1-Zero ssid FM1-Zero         802-11-wireless.mode ap 802-11-wireless.band bg         wifi-sec.key-mgmt wpa-psk wifi-sec.psk "$pass"         ipv4.method shared ipv4.addresses 10.42.0.1/24 ipv6.method disabled
fi
rfkill unblock wifi
nmcli connection up FM1-Zero
