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
# output to the log file only, and stopped at the end: a background process that keeps the script's output
# open makes the emulator action wait until the job times out
appium --address 127.0.0.1 --port 4723 --allow-insecure '*:chromedriver_autodownload' \
       --log appium.log --log-level info --log-no-colors > /dev/null 2>&1 &
appium_pid=$!
trap 'kill "$appium_pid" 2>/dev/null || true' EXIT
for _ in $(seq 1 60); do curl -sf http://127.0.0.1:4723/status >/dev/null && break; sleep 1; done
curl -sf http://127.0.0.1:4723/status >/dev/null

# one session by hand first: if Chrome cannot be driven at all, say why in seconds instead of four timeouts
started=$(date +%s)
answer=$(curl -s --max-time 600 -X POST http://127.0.0.1:4723/session -H 'Content-Type: application/json' -d '{
  "capabilities": {"alwaysMatch": {"platformName": "Android", "browserName": "Chrome",
    "appium:automationName": "UiAutomator2", "appium:chromedriverAutodownload": true,
    "appium:uiautomator2ServerInstallTimeout": 120000, "appium:adbExecTimeout": 120000,
    "goog:chromeOptions": {"args": ["--disable-fre", "--no-first-run", "--no-default-browser-check"]}}}}')
session=$(echo "$answer" | python3 -c "import json,sys; print(json.load(sys.stdin).get('value',{}).get('sessionId',''))" 2>/dev/null || true)
if [ -z "$session" ]; then
  msg=$(echo "$answer" | python3 -c "import json,sys; v=json.load(sys.stdin).get('value',{}); print(v.get('error',''), '-', v.get('message','')[:900])" 2>/dev/null || echo "$answer" | head -c 900)
  echo "::error title=Appium session (Android Chrome)::$msg"
  grep -iE "error|fail" appium.log | tail -15 | sed 's/^/::warning title=appium.log::/' || true
  exit 1
fi
curl -s -X POST "http://127.0.0.1:4723/session/$session/url" -H 'Content-Type: application/json' \
     -d '{"url": "http://10.0.2.2:5173/#/"}' >/dev/null
title=$(curl -s "http://127.0.0.1:4723/session/$session/title" | python3 -c "import json,sys; print(json.load(sys.stdin)['value'])")
curl -s -X DELETE "http://127.0.0.1:4723/session/$session" >/dev/null
echo "::notice title=Appium session (Android Chrome)::ready in $(( $(date +%s) - started )) s, page title: $title"

status=0
mvn -B -f automation/pom.xml test -Dgroups=mobile -DexcludedGroups=none -Dbrowser=android-chrome \
    -DbaseUrl=http://localhost:8081 -DuiUrl=http://10.0.2.2:5173 -DappiumUrl=http://127.0.0.1:4723 || status=$?
if [ "$status" -ne 0 ]; then
  .github/scripts/annotate-failures.sh automation/target/surefire-reports || true
  grep -iE "error|fail" appium.log | tail -10 | sed 's/^/::warning title=appium.log::/' || true
fi
exit "$status"
