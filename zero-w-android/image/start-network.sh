#!/bin/sh
set -eu
# Record only stages and device state; never log credentials or command arguments.
report=/mnt/FM1DATA/FM1/ARRANQUE.TXT
stage="inicio del servicio"
report_state() {
    {
        printf 'Fase: %s\n' "$stage"
        nmcli -t -f DEVICE,TYPE,STATE device status || true
        rfkill list wifi || true
    } > "$report.tmp" && mv "$report.tmp" "$report"
}
trap report_state EXIT
report_state
stage="activar radio Wi-Fi"
rfkill unblock wifi
nmcli radio wifi on
stage="configurar canal de 2,4 GHz para España"
if command -v iw >/dev/null; then iw reg set ES || true; fi
stage="esperando wlan0"
n=0
until nmcli -t -f DEVICE device status | grep -qx wlan0; do
    n=$((n + 1))
    [ "$n" -lt 30 ] || exit 1
    sleep 1
done
stage="leer clave y crear perfil"
# Private credential supplied by the owner on their card before boot.
passfile=/mnt/FM1DATA/FM1/WIFI.TXT
if ! nmcli connection show FM1-Zero >/dev/null 2>&1; then
    if [ ! -e "$passfile" ]; then
        umask 077
        # Generate on this unit; never bake shared credentials into the image.
        tmp=$(mktemp /mnt/FM1DATA/FM1/.wifi.XXXXXX)
        od -An -N12 -tx1 /dev/urandom | tr -d ' \n' > "$tmp"
        printf '\n' >> "$tmp"
        mv "$tmp" "$passfile"
    fi
    pass=$(tr -d '\r\n' < "$passfile")
    test "${#pass}" -ge 8 && test "${#pass}" -le 63 || exit 1
    nmcli connection add type wifi ifname wlan0 con-name FM1-Zero ssid FM1-Zero         802-11-wireless.mode ap 802-11-wireless.band bg 802-11-wireless.channel 6         wifi-sec.key-mgmt wpa-psk wifi-sec.psk "$pass"         ipv4.method shared ipv4.addresses 10.42.0.1/24 ipv6.method disabled
fi
# Pi Zero W / Trixie: the integrated chipset can fail AP startup when PMF is enabled.
# Keep WPA2-PSK and CCMP encryption; disable only management-frame protection.
stage="configurar WPA2 compatible con Zero W"
nmcli connection modify FM1-Zero wifi-sec.key-mgmt wpa-psk wifi-sec.proto rsn wifi-sec.pairwise ccmp wifi-sec.group ccmp wifi-sec.pmf 1
stage="activar punto de acceso FM1-Zero"
nmcli --wait 45 connection up FM1-Zero ifname wlan0
stage="punto de acceso activado"
