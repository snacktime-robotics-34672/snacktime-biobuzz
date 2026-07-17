# STATUS.md — where this project actually is

**Last updated:** 2026-07-17 — Sloth hot-reload proven on the hub; 11 patterns ported from decode-2025 (see CHANGELOG).

**Read `CLAUDE.md` first** — that's the charter (rules + architecture) and it governs everything.
This file is only the *current state*: what's verified, what's left, and what to do next. Keep it
updated as things change; it's the handoff between sessions.

Everything below was **verified by running commands on Aaron's Mac**, not assumed from docs. That
distinction matters: earlier in planning we twice got burned by trusting a library's claims instead
of checking. Verify, don't assume (`SETUP.md` ground rule 1).

---

## Where we are

`SETUP.md` **Phases 1–5 are DONE and PROVEN:**

- Forked `FTC-23511/SolversLib-Quickstart` → own repo (local folder: `biobuzz-2026`).
- Cloned into Android Studio, Gradle sync clean.
- **Builds successfully.**
- **Deploys to the Control Hub** — example OpModes visible on the Driver Hub.
- Android SDK Platform 34 installed (the project requires `compileSdk 34`; API 35 alone is not
  enough — both coexist fine in the SDK Manager).

The base is proven end to end. Anything that breaks from here is something *we* added.

---

## What's ACTUALLY installed

Verified via `./gradlew :TeamCode:dependencies` and by reading `TeamCode/build.gradle`.

| Component | Version | Notes |
|---|---|---|
| FTC SDK | **11.1.0** | |
| `org.solverslib:core` | **0.3.4** | command framework (FTCLib fork) |
| `org.solverslib:pedroPathing` | **0.3.4** | glue only — does NOT bundle Pedro |
| `com.pedropathing:ftc` | **2.0.6** | **our own declared line — we own this version** |
| `com.pedropathing:telemetry` | 1.0.0 | |
| `com.bylazar:fullpanels` | **1.0.12** | full Panels bundle (see below) |
| `com.acmerobotics.dashboard` | 0.5.1 | ⚠️ **conflicts with Sloth** — see Landmines |

**Panels is already fully installed** — `fullpanels` pulls `field` (field view), `graph` (live
graphs), `configurables` (§6 Tier-1 live tuning), `capture` (match replay), `limelightproxy`,
`battery`, `gamepad`, `camerastream`, `themes`, `lights`, `pinger`. Every dashboard capability the
charter asks for (§8) is already here. **Nothing to add for Panels.**

**Repositories already in `TeamCode/build.gradle`:**
`maven.brott.dev` · `mymaven.bylazar.com/releases` · `repo.dairy.foundation/releases` ·
`repo.dairy.foundation/snapshots`

→ **Both Dairy Foundation repos are already present, so Sloth needs no new repository.**

**Already configured** (in `FtcRobotController/build.gradle`): `minSdkVersion 24`, Java 8
`compileOptions`, `compileSdk 34` — all of which SolversLib requires. Nothing to change.

---

## What's LEFT to do

Per `CLAUDE.md` §6, **every one of these is a dependency change: WARN AND CONFIRM with a human
before doing it, one library at a time, build after each.**

1. **Sloth** (sub-second hot reload) — the only real work left.
   - Repos already present. Needs the `buildscript` classpath + the plugin applied.
   - ⚠️ See the Dashboard landmine below. Handle deliberately, not by trial and error.
   - Docs: `github.com/Dairy-Foundation/Sloth`

2. **`buildConfig` git hash** — so `hardware/BuildInfo.java` works (§7 traceability).
   - It reads `org.firstinspires.ftc.teamcode.BuildConfig` → needs `GIT_HASH` + `BUILD_TIME`
     `buildConfigField`s on the **TeamCode** module.
   - **A working example already exists in this repo:** `FtcRobotController/build.gradle` does
     exactly this with `APP_BUILD_TIME`. Copy that pattern — but note it's a *different module*
     (namespace `com.qualcomm.ftcrobotcontroller`), so it doesn't help TeamCode directly.
   - **OPEN QUESTION — check this first:** does `build.common.gradle` (project root) already set
     `buildConfig = true` for the TeamCode module? `cat build.common.gradle`. If yes, we only add
     the two fields. If no, enable it too.

3. **JUnit** — `testImplementation "junit:junit:4.13.2"` so `./gradlew :TeamCode:test` runs the
   off-robot `IntakeLogicTest` (§9).

4. **Copy in the skeleton** — the `teamcode` packages from `snacktime-robot-skeleton.zip` into
   `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`, and the test into
   `TeamCode/src/test/java/...`. Then the docs (`CLAUDE.md`, `WORKFLOW.md`, `SETUP.md`,
   `CHANGELOG.md`, this file) at the **project root**.

5. **Optional — `photon`** — already sitting commented out in `TeamCode/build.gradle`, one
   uncomment away. Photon is a loop-time optimization library, directly relevant to the prime
   directive (§0). **Measure Loop Hz before and after** rather than assuming it helps.

---

## Decisions already made

- **Pedro stays at 2.0.6 for now.** Policy is "always latest" (§2), and 2.1.2 exists. But 2.0.6 is
  what Seattle Solvers actually tested SolversLib 0.3.4 against, and "we must succeed" argues
  against making an unvalidated jump the opening move. **Bump to 2.1.2 as ONE isolated change after
  the stack is proven**, using Phase 0's path-following test as the proof. It is a one-line edit in
  `TeamCode/build.gradle` and trivially revertable — the cost is low, so the only reason to wait is
  to keep one variable at a time.

- **Java + SolversLib, not Kotlin + NextFTC.** Reasoning in `CLAUDE.md` §2. The short version: this
  Quickstart's `TeamCode/build.gradle` proves the point — Pedro is declared on its own line,
  separate from `pedroPathing`. We own the version outright. NextFTC's extension dragged Pedro in
  transitively at a pinned old version.

---

## Landmines & notes

- **FTC Dashboard vs Sloth — CONFIRMED BLOCKING, not just a risk.** `implementation
  "com.acmerobotics.dashboard:dashboard:0.5.1"` sits in `TeamCode/build.gradle`. Sloth ships its
  **own modified fork** of FTC Dashboard, and its docs warn that a library pulling dashboard via
  `implementation`/`api` needs excluding. As of 2026-07-16 this is confirmed to actually be causing
  a problem ("Sloth load needs to be fixed") — **this is the top priority in "What's LEFT."** Read
  Sloth's current docs for the exclude/compileOnly fix rather than guessing at it.- **Logcat — DROPPED.** Not worth fixing. RC persistent match logs (pulled via ADB or the hub's
  Manage page after a run) cover post-match event review. Panels covers live bench monitoring.
  Logcat in Android Studio would only add "see a crash stack trace in real time rather than pulling
  it from the hub after" — a minor convenience, not a blocker. Use `RobotLog` for all event logging;
  it feeds both Logcat and the RC persistent logs simultaneously.

- **FTC SDK 11.2 — HOLD until Sloth Load is released with Gradle 9.1 support.**
  11.2 bumps Gradle 8.9 → 9.1 and AGP 8.7.0 → 8.13.2. Sloth's Load plugin 0.2.4 breaks under
  Gradle 9.1 — the fix is merged (PR #10, May 7, 2026) but not yet released. Upgrading now would
  lose Sloth hot-reload with no workaround. Watch the Dairy Foundation repo for Load 0.2.5+, then
  do the upgrade as one coordinated change: Gradle wrapper, AGP classpath, and all 9 SDK deps
  (11.1.0 → 11.2.0) in the same build. Revisit September 2026.

- **No dependency locking.** There are no lockfiles. The `{strictly X}` markers all over the
  dependency report are **Android Gradle Plugin variant-alignment constraints** — generated *from*
  resolution to keep compile and runtime classpaths aligned, not imposed *on* it. The report's own
  legend says so: `(c) - A dependency constraint, not a dependency`. **They do not block a version
  bump.** Change the Pedro line and AGP regenerates the constraint to match.

- **SolversLib has AI-readable docs.** `https://docs.seattlesolvers.com/llms.txt` for the index, and
  appending `.md` to any docs page returns markdown. Use these rather than guessing at API names.

- **SolversLib ↔ Pedro compatibility matrix** (re-check, it moves): 0.3.3+ → Pedro 2.0.0 *and
  higher*; 0.3.2 → 1.0.9; 0.3.1 → 1.0.8.

- **FTCLib and SolversLib cannot coexist.** Their docs are explicit. Never add FTCLib.

- **API verification.** The skeleton follows SolversLib's documented patterns but the exact class
  names should be confirmed against the javadocs:
  `repo.dairy.foundation/javadoc/releases/org/solverslib/core/latest`. The *structure* is stable;
  the signatures may need adjusting.

---

## Confirmed hardware (from Aaron, 2026-07-16)

- **REV Servo Hub** is on the robot. Add to the config sheet alongside the Control/Expansion Hub.
  Note from the FTC SDK changelog: both the Robot Controller and Driver Station apps must be on
  **10.0+** for a Servo Hub to configure as a Servo Hub rather than show up as a generic Expansion
  Hub — worth a version check during Robot Controller configuration. Firmware/address changes need
  the REV Hardware Client.
- **Pinpoint 2.0** (goBILDA) confirmed as the localization source — matches the standing decision in
  `CLAUDE.md` §3 (single source of pose, no fusion). Two mounting facts from Pedro's own docs, worth
  having on hand during wiring: it must go on an **I2C port other than port 0** (the Control Hub's
  built-in IMU owns port 0), sticker/port side up, forward pod → X port, strafe pod → Y port. These
  belong in `pedroPathing/Constants.java`'s `PinpointConstants` once the robot is built.

---

## File inventory (as of the last handoff)

`find TeamCode -name '*.java' -path '*teamcode*'` should show **18** files: our 13, plus 5 the
SolversLib Quickstart ships on its own —

- **`pedroPathing/Constants.java`, `Tuning.java`** — Pedro's real configuration (drivetrain geometry,
  PIDF gains, localizer setup) and its tuning OpModes. **Keep — this is where Pinpoint gets wired in
  for real**, not sample clutter.
- **`samples/PedroCommands.java`, `PedroAutoSample.java`, `PedroTeleOpSample.java`** — genuine
  reference examples showing the SolversLib-wraps-Pedro command pattern in real code. Worth reading
  once alongside our `AutonomousExample.java` for exact class/method names where our skeleton has
  TODOs. Safe to delete later to declutter the Driver Hub OpMode list; `pedroPathing/` files must stay.

If the count isn't 18, something's stale — re-check against the current
`snacktime-robot-skeleton.zip` before proceeding.

---

## Next action

**"What's LEFT to do" items 1–3 are DONE** (code-side, 2026-07-15):
- Sloth 0.2.4 installed (`a52654c`) — **on-robot hot-reload PROVEN 2026-07-17** (sub-second load on the hub).
- `GIT_HASH` + `BUILD_TIME` in TeamCode `BuildConfig` (`42696d9`) — verified in generated source.
- JUnit 4.13.2 added (`bd80d2f`) — `./gradlew :TeamCode:test` runs 4 tests, 0 failures.
- Logcat dropped as a task — RC persistent match logs + Panels cover the use cases; no fix needed.

Remaining before Phase 0 is proven:

1. **Commit the skeleton + docs** (STATUS.md #4) — `CLAUDE.md`, `STATUS.md`, `WORKFLOW.md`,
   `SETUP.md`, `CHANGELOG.md`, `build.gradle.additions`, and all untracked `teamcode/` Java files
   are still untracked. Commit them when ready to make the skeleton the baseline.
2. **On-robot: prove snapshot writes** — run any OpMode, pull `snacktime_snapshot.json` off the
   hub, confirm `gitHash` field is a real commit hash (not `"unknown"`).
3. **On-robot: prove Pedro follows a path** — a version mismatch appears at runtime, not at build.
   "It compiled" proves nothing. Run the Pedro tuning OpModes with the Pinpoint wired in.

---

## Phase 0 hardware to-do (needs the robot in front of you)

### REV Servo Hub
No code change needed — servos on it are accessed identically to any other servo
(`hardwareMap.servo.get("name")`). What needs doing on the hardware side:
- Add it to the Robot Controller configuration on the hub (RC app or REV Hardware Client).
- Confirm both the Robot Controller **and** Driver Station apps are on **10.0+** — below that, the
  Servo Hub shows up as a generic Expansion Hub and can't be configured correctly.
- Set firmware and I2C address via REV Hardware Client if needed.
- Once config names are set, add them to `CLAUDE.md §10` hardware map.

### Pinpoint V2 (goBILDA Odometry Computer)
Driver is already in the FTC SDK 11.1.0 (`com.qualcomm.hardware.gobilda.GoBildaPinpointDriver`) —
no extra dependency needed. What needs doing:
- Wire it physically: **I2C port other than port 0** (the Control Hub's built-in IMU owns port 0),
  sticker/port side up, forward pod → X port, strafe pod → Y port.
- Fill in `pedroPathing/Constants.java` → `PinpointConstants` with the actual pod offsets
  (mm from robot center) and encoder directions. These values come from your physical mounting.
- Add the config name to `CLAUDE.md §10` hardware map once locked in.

## Pre-season opportunity (BIOBUZZ)

Kickoff is **September 12, 2026**, but the Game Preview is already out:
- Scoring element is **Pollen** — ~3in plastic balls, similar to DECODE's Artifacts. **Purchasable
  from AndyMark now, ships immediately.**
- Every ecosystem partner published a **StarterBot Base = drivetrain + intake** — which is exactly
  the mechanism this skeleton already models. **goBILDA has one** (our ecosystem).

→ Phase 0 doesn't have to be abstract. Order Pollen, build the goBILDA StarterBot Base, and prove
the whole loop against *real game pieces* months before anyone has a field.
