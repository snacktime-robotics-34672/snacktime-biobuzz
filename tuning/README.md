# Saving robot tuning to GitHub

These JSON files are the **canonical, git-backed tuning** for each physical robot. Tuned values live
on the robot's hub until someone pulls them into this repo and commits them. **git is the backup,
not the hub** — a hub re-flash wipes its copy (`CLAUDE.md` §7, §12).

| Robot | Hub network name | Committed file (here) | File on the hub |
|-------|------------------|-----------------------|-----------------|
| Competition | `34672-RC` | `tuning/comp_tuning.json` | `/sdcard/FIRST/settings/comp_tuning.json` |
| Test bot | `34672-T-RC` | `tuning/testbot_tuning.json` | `/sdcard/FIRST/settings/testbot_tuning.json` |

A file may not exist yet. It is created the first time that robot saves.

Each robot reads and writes only its own file, so tuning one robot can never touch the other's
values. A hub whose name is neither of those resolves to UNKNOWN and **saves nothing at all**.

---

## What ends up in the file

One file holds everything for one robot:

- Dashboard tunables — `TuningConfig.*`, `Drivetrain.*`, `JoystickCurve.*`, `FieldTweaks.*`,
  `Vision.*`. Keys are namespaced `ClassName.fieldName`.
- Pedro constants — 21 values under `Pedro.*`: the translational, heading and drive PIDF gains,
  centripetal scaling, mass, both zero-power accelerations, the drive velocities, and the two pod
  offsets.

> **Changed 2026-09-01.** Pedro's constants used to live only in `pedroPathing/Constants.java`.
> They now save and load with everything else. The values in `Constants.java` are **fallback
> defaults** — what a robot uses when its file is missing or rejected. The committed file wins.

---

## Step 1 — Tune, and let it save itself

Tune in Panels as normal. About **one second after you stop changing a value**, the robot writes it
to its own file on the hub. You do not need to stop the OpMode cleanly, and you do not need to be in
any particular OpMode — every OpMode saves.

The robot also saves on a clean stop, so both paths cover you.

**Do not transcribe numbers into source code.** Saving means committing the file the robot wrote.

## Step 2 — Confirm the robot actually saved it

Two ways, either is fine:

- **Re-init any OpMode** and read the top telemetry lines. You want:
  `LOADED TESTBOT TUNING (testbot_tuning.json, <timestamp>) — 63 values`
  Then check in Panels that your value is still what you set it to. That round trip proves the write
  and the read.
- **Check the RC log** for a `PEDRO_TUNED` entry, which is written whenever a Pedro value
  settles. See *Looking at the hub from Android Studio* below for how to read it.

If you instead see `no tuning file yet` or `ROBOT UNKNOWN`, stop and fix that first — an UNKNOWN hub
saves nothing. Name the hub `34672-RC` or `34672-T-RC` in the REV Hardware Client and reboot it.

## Step 3 — Connect your laptop to the hub

You need `adb`, which ships with Android Studio. Check it is on your PATH:

```bash
adb version
```

Then connect **one** of these two ways.

**Over USB** — use the Control Hub's **USB-C** port, not the USB-A ports (those are for the Limelight
and webcams), and use a data cable, not a charge-only one:

```bash
adb devices
```

**Over Wi-Fi** — join the hub's own network from your laptop's Wi-Fi (`34672-RC` or `34672-T-RC`),
then:

```bash
adb connect 192.168.43.1:5555
adb devices
```

Either way, `adb devices` must list a device. An empty list means you are not connected, and nothing
below will work.

## Step 4 — Pull the file into the repo

From the repo root:

```bash
./save-tuning.sh
```

It works out which robot it is talking to from the hub itself and pulls the right file into
`tuning/`. Force it if you ever need to:

```bash
./save-tuning.sh comp     # competition robot
./save-tuning.sh test     # test bot
```

By hand, if you prefer:

```bash
adb pull /sdcard/FIRST/settings/comp_tuning.json    tuning/comp_tuning.json
adb pull /sdcard/FIRST/settings/testbot_tuning.json tuning/testbot_tuning.json
```

## Step 5 — Look at what changed

```bash
git status --short tuning/
git diff tuning/
```

Read the diff before committing. You should recognise the numbers you turned. If a value you did not
touch has changed, find out why before pushing it.

## Step 6 — Commit and push

```bash
git add tuning/
git commit -m "Tune comp: drive PIDF after the Friday practice field"
git push origin master
```

Say what changed and why, in plain words. We commit straight to `master`.

## Step 7 — Confirm it is on GitHub

```bash
git log --oneline -1 --decorate
```

`origin/master` should be on the same line as your commit. If it is not, the push did not go through.

---

## Looking at the hub from Android Studio

You do not need a terminal to see what is on the robot. Android Studio has a file browser for the
connected device: **View → Tool Windows → Device Explorer**, or the tab on the right-hand edge.
Older versions call it **Device File Explorer**. Newer ones show a device dropdown at the top and
two tabs — you want **Files**.

| What | Where on the hub |
|---|---|
| Tuning files | `/sdcard/FIRST/settings/comp_tuning.json`, `testbot_tuning.json` |
| Snapshots | `/sdcard/FIRST/settings/snacktime_snapshot_COMPETITION.json` (or `_TESTBOT`) |
| Match logs | `/sdcard/FIRST/matchlogs/` |
| RC log | `/sdcard/FIRST/robotControllerLog.txt` |

If `/sdcard` will not expand, try `/storage/emulated/0/FIRST/` — the same place under a different
mount name. Double-click a `.json` to open it, or right-click and **Save As** to pull it somewhere.

**Two things to know before relying on it:**

1. **It needs the same adb connection as everything else** (Step 3). Device Explorer is a front end
   for adb, so an empty device dropdown means the same thing an empty `adb devices` does.
2. **Double-clicking opens a downloaded copy, not the file on the hub.** It lands in a temporary
   folder, so this is for looking, not for the git workflow — it does not put anything in `tuning/`,
   and editing that copy does not change the robot. Use `./save-tuning.sh` to save for real.

**Where it earns its place** is the RC log, which the script does not touch. Open
`robotControllerLog.txt` and search for:

- `PEDRO_TUNED` — the paste-ready block written each time a Pedro value settles. This is the
  decisive check for "did my edit actually register?" No entry means the change was never seen.
- `SNAPSHOT:` — the full record of a run, including the git commit the robot was running.
- `Persistence:` — every tuning save and load, with the file path it used.

---

## Restoring a robot after a hub re-flash

The committed file is the backup. Push it back onto the hub:

```bash
adb push tuning/comp_tuning.json /sdcard/FIRST/settings/comp_tuning.json
```

A hub with no file runs on the in-code fallback defaults and says so loudly on the Driver Hub. It
never silently loads the other robot's tuning.

---

## When something goes wrong

| What you see | What it means | What to do |
|---|---|---|
| `adb devices` lists nothing | Not connected | USB-C port and a data cable, or join the hub Wi-Fi and `adb connect 192.168.43.1:5555` |
| `could not pull ... json` | That robot has never saved | Run an OpMode on it, change a value, stop it, try again |
| `ROBOT UNKNOWN` on the Driver Hub | Hub name is neither robot's | Rename to `34672-RC` or `34672-T-RC` in the REV Hardware Client, reboot |
| `no tuning file yet` at init | Fresh or re-flashed hub | Normal. Restore with `adb push`, or tune and save to create it |
| `*** n NOT RESTORED ***` at init | A value saved but could not be read back | The robot is on the in-code default for it. The log names the field — report it |
| A value you set is back to its old number | The file was loaded over your edit | Change it, wait a second for the autosave, confirm, then re-init |
| Your commit is not on GitHub | The push failed | `git push origin master` again and read the error |

## What is NOT in these files

- **Snapshots** (`snacktime_snapshot_*.json`) are post-match records, not tuning. They are
  gitignored.
- **Encoder directions, encoder resolution and yaw scalar** are read once when the localizer is
  built. They live in `pedroPathing/Constants.java` and are changed in code, not in Panels.
