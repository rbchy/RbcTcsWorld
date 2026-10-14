#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner once the emulator has booted (CI job "mobile-android").
# Phone tests (Appium, Chrome on Android) against the backend + storefront running on the CI machine:
# the emulator reaches the machine itself as 10.0.2.2.
set -euo pipefail

adb wait-for-device
echo "::notice title=Android::$(adb shell getprop ro.product.model | tr -d '\r'), Android $(adb shell getprop ro.build.version.release | tr -d '\r')"
if ! adb shell pm list packages | grep -q 'com.android.chrome'; then
  echo "::error title=Android::Chrome is not installed on this emulator image"; exit 1
fi
echo "::notice title=Chrome on Android::$(adb shell dumpsys package com.android.chrome | grep -m1 versionName | tr -d '\r ')"

# no "Welcome to Chrome" / sign-in screens in front of the shop
adb root >/dev/null 2>&1 || true
adb wait-for-device
adb shell 'echo "_ --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line'
adb shell am set-debug-app --persistent com.android.chrome

# Appium 3: fetch the chromedriver that matches this Chrome
appium --address 127.0.0.1 --port 4723 --allow-insecure '*:chromedriver_autodownload' \
       --log appium.log --log-level info --log-no-colors &
for _ in $(seq 1 60); do curl -sf http://127.0.0.1:4723/status >/dev/null && break; sleep 1; done
curl -sf http://127.0.0.1:4723/status >/dev/null

mvn -B -f automation/pom.xml test -Dgroups=mobile -DexcludedGroups=none -Dbrowser=android-chrome \
    -DbaseUrl=http://localhost:8081 -DuiUrl=http://10.0.2.2:5173 -DappiumUrl=http://127.0.0.1:4723 \
    -Dsurefire.rerunFailingTestsCount=1
