#!/usr/bin/env bash
# Installs Tetra on the phone and has Android Auto approve Tetra Maps for the car again. Android Auto lists a sideloaded
# Car App Library app in a real car only once it has seen it on the desktop head unit, and every install drops that
# (Plan.md, 23 Oct). Usage: tools/phone-install.sh [apk]; PHONE=<serial> for another phone.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB=~/Android/Sdk/platform-tools/adb
PHONE=${PHONE:-208c8192}
APK=${1:-app/build/outputs/apk/debug/app-arm64-v8a-debug.apk}
DHU=~/Android/Sdk/extras/google/auto
LIBS=~/.cache/tetra-dhu-libs # libc++ for the head unit, unpacked from Ubuntu's packages (no sudo needed)
LOG=$(mktemp)
a() { "$ADB" -s "$PHONE" "$@"; }

a install -r "$APK"

if ! ldconfig -p | grep -q 'libc++abi.so.1' && [ ! -e "$LIBS/libc++abi.so.1" ]; then
  t=$(mktemp -d); (cd "$t" && apt-get download -q libc++1 libc++abi1 >/dev/null)
  for d in "$t"/*.deb; do dpkg -x "$d" "$t/root"; done
  mkdir -p "$LIBS"; cp -a "$t"/root/usr/lib/x86_64-linux-gnu/libc++*.so.1* "$LIBS/"; rm -rf "$t"
fi

if ! a shell 'ss -ltn 2>/dev/null | grep -q :5277'; then
  echo "On the phone: Android Auto › ⋮ › Start head unit server — waiting for it…"
  until a shell 'ss -ltn 2>/dev/null | grep -q :5277'; do sleep 2; done
fi
a forward tcp:5277 tcp:5277 >/dev/null
sleep 5; a logcat -b all -c # Android Auto's own check after the install (a refusal) stays out of the head unit's

echo "Head unit: connecting…"
# The head unit quits when its stdin closes: a fifo this script holds open feeds it
FIFO=$(mktemp -u); mkfifo "$FIFO"
(cd "$DHU" && LD_LIBRARY_PATH="$LIBS" exec ./desktop-head-unit <"$FIFO" >"$LOG" 2>&1) &
HU=$!
exec 3>"$FIFO"
trap 'kill $HU 2>/dev/null || true; a forward --remove tcp:5277 2>/dev/null || true; rm -f "$LOG" "$FIFO"' EXIT

# Android Auto checks every app as the head unit connects; Tetra passes silently, a refusal is logged
for _ in $(seq 150); do
  if a logcat -d -b all | grep -q 'CAR.VALIDATOR: Package'; then
    sleep 5 # the check's other lines, and time for Android Auto to keep the approval
    if a logcat -d -b all | grep 'CAR.VALIDATOR.*DENIED.*com.kalotrapezis.drive'; then echo "Android Auto refused Tetra Maps."; exit 1; fi
    echo "Android Auto approved Tetra Maps. You can stop the head unit server on the phone."; exit 0
  fi
  kill -0 "$HU" 2>/dev/null || { echo "The head unit quit:"; tail -5 "$LOG"; exit 1; }
  sleep 2
done
echo "The head unit didn't connect within 5 minutes."; exit 1
