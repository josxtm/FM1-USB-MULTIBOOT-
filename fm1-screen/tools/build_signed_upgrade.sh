#!/usr/bin/env bash
set -euo pipefail

tag="$1"
certificate="$2"
build="$(sed -n 's/^fm1Build=//p' gradle.properties)"
apk="FM1-SCREEN-${tag#fm1-screen-}.apk"
mkdir -p distribution upgrade

./gradlew --no-daemon :app:assembleRelease :app:assembleReleaseAndroidTest
cp app/build/outputs/apk/release/app-release.apk "distribution/$apk"
cp app/build/outputs/apk/androidTest/release/app-release-androidTest.apk upgrade/test.apk
python3 tools/release_metadata.py "distribution/$apk" "$tag" "$certificate"

./gradlew --no-daemon :app:assembleRelease :app:assembleDebug -Pfm1Build="$((build + 1))"
cp app/build/outputs/apk/release/app-release.apk upgrade/upgrade.apk
cp app/build/outputs/apk/debug/app-debug.apk upgrade/wrong-signature.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs upgrade/upgrade.apk |
    grep -Fx "Signer #1 certificate SHA-256 digest: $certificate"
