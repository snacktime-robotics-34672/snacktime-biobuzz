#!/bin/bash
#
# save-tuning.sh — pull a robot's tuning file off its hub into the repo, ready to commit.
#
# WHY: tuned values are written to a file ON THE HUB (/sdcard/FIRST/settings/), not to this repo.
# A plain `git commit` won't back them up until that file is pulled into tuning/. This script does
# the pull in one step. Run it AFTER you've tuned and stopped the OpMode (that's when the hub file
# is written) — not just on connecting.
# Background: tuning/README.md and WORKFLOW.md §11.
#
# USAGE:
#   ./save-tuning.sh          # auto-detect which robot from the connected hub (recommended)
#   ./save-tuning.sh comp     # force competition robot (hub 34672-C-RC)
#   ./save-tuning.sh test     # force test bot          (hub 34672-T-RC)
#
# PREREQS:
#   - adb installed and on your PATH (Android platform-tools; ships with Android Studio).
#   - the robot's hub connected to this computer (USB, or adb-over-Wi-Fi).
#
# After it runs, review the change and commit:
#   git add tuning/ && git commit -m "Tune <robot>: <what changed>" && git push

set -e

# Always operate from the repo root, wherever the script is called from.
cd "$(dirname "$0")"

if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: adb not found on PATH. Install Android platform-tools (ships with Android Studio)."
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "ERROR: no hub connected via adb. Plug in the robot's hub (USB or Wi-Fi adb)."
  exit 1
fi

robot="$1"

if [ -z "$robot" ]; then
  # Auto-detect from the most recent per-robot snapshot file the hub has written. The snapshot
  # filename encodes the robot (snacktime_snapshot_COMPETITION.json etc.), so it tells us which
  # robot this hub is without us having to guess.
  latest=$(adb shell 'ls -t /sdcard/FIRST/settings/snacktime_snapshot_*.json 2>/dev/null' | tr -d '\r' | head -1 || true)
  case "$latest" in
    *snacktime_snapshot_COMPETITION.json) robot="comp" ;;
    *snacktime_snapshot_TESTBOT.json)     robot="test" ;;
    *snacktime_snapshot_UNKNOWN.json)
      echo "ERROR: this hub's robot identity is UNKNOWN. Name it 34672-C-RC or 34672-T-RC in the"
      echo "       REV Hardware Client and reboot, then re-run. (See WORKFLOW.md §11.)"
      exit 1 ;;
    *)
      echo "ERROR: couldn't auto-detect the robot — no per-robot snapshot on this hub yet."
      echo "       Run an OpMode once (that writes a snapshot), or pass it explicitly:"
      echo "       ./save-tuning.sh comp   |   ./save-tuning.sh test"
      exit 1 ;;
  esac
  echo "Auto-detected robot: $robot"
fi

case "$robot" in
  comp) file="comp_tuning.json" ;;
  test) file="testbot_tuning.json" ;;
  *)    echo "Usage: ./save-tuning.sh [comp|test]"; exit 1 ;;
esac

hub_path="/sdcard/FIRST/settings/$file"
echo "Pulling $hub_path  ->  tuning/$file"
if ! adb pull "$hub_path" "tuning/$file"; then
  echo "ERROR: could not pull $file."
  echo "       Has the '$robot' robot been run and stopped at least once (which writes the file)?"
  exit 1
fi

echo
echo "Changes to tuning/ (this is what will be committed):"
git status --short tuning/ 2>/dev/null || true
echo
echo "Next:  git add tuning/ && git commit -m \"Tune $robot: <what changed>\" && git push"