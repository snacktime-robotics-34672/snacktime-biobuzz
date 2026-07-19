# STATUS.md — where this project actually is

**Last updated:** 2026-07-18 — Pinpoint wired in and localization proven live on-robot; tunables
reorganized into per-subsystem files; hub log auto-cleanup added.

**Read `CLAUDE.md` first** — that's the charter (rules + architecture) and it governs everything.
This file is only the *current state*: what's verified, what's left, and what to do next. Keep it
updated as things change; it's the handoff between sessions.

Everything below was **verified by running commands on Aaron's Mac**, not assumed from docs. That
distinction matters: earlier in planning we twice got burned by trusting a library's claims instead
of checking. Verify, don't assume.

---

## Where we are

**Phase 0 (§13 of CLAUDE.md) acceptance status:**
- ✅ Base stack builds and deploys (SETUP.md Phases 1–5)
- ✅ **Sloth hot-reload proven on-robot** — sub-second load confirmed 2026-07-17
- ✅ `GIT_HASH` + `BUILD_TIME` in TeamCode `BuildConfig` (verified in generated source)
- ✅ JUnit tests run off-robot (`./gradlew :TeamCode:test` — all green)
- ✅ **Pinpoint wired in; localization proven live on-robot** — pod offsets measured via
  `OffsetsTuner` (`forwardPodY=6.735`, `strafePodX=0.287`), robot mass set, Panels field view shows
  live pose + heading + history trail (`LocalizationTest`)
- ⏳ **Snapshot writes proof on-robot** (still pending) — pull `snacktime_snapshot.json` off the hub
  or grep `SNAPSHOT:` in the RC log, confirm `gitHash` is a real commit hash, not `"unknown"`
- ⏳ **Pedro follows a path proof on-robot** (still pending) — localization tracks pose correctly,
  but no path-follow run is confirmed yet. The `Tuning` menu → `Tests` folder has `Line` /
  `Triangle` / `Circle` OpModes ready to run against the now-real pod offsets.

**Ready-to-use capabilities already in teamcode** (all Tier 2, hot-reloadable):
- **TeleOp**: field-centric mecanum drive, deadzone (now in `JoystickCurve`), LEFT_BUMPER slow
  mode, loop-time readout
- **Autonomous**: Driver-Hub pre-match menu (alliance / start pose / field / delay), command-tree
  scheduling, command lifecycle logging (gated behind `verboseTelemetry`), snapshot persistence
- **Path following**: `FollowPathCommand` wraps Pedro so autos compose as command trees; Pedro
  localization live and tracking pose on Panels field view
- **Health telemetry**: `DiagnosticsCenter.reportProblem(code, data)` from any subsystem drains to
  Driver Hub each loop
- **Per-field pose deltas**: `FieldTweaks.lookup(isRed, field)` returns the live-tunable pose
  offsets selected via the menu
- **HeadingCorrector**: opt-in PIDF heading hold (disabled by default; enable via
  `Drivetrain.headingCorrectionEnabled`)
- **Servos**: `ServoUtil.degreesToPositionClamped(deg, min, max, range)` — soft limits + degrees API
- **Tuning backup**: `Persistence.saveTuning()` / `loadAndApplyTuning(telemetry)` — dashboard
  values saved on every stop (now including loop-time stats), restored on every init; Driver Hub
  shows `LOADED TUNING FROM FILE`. Scans a `TUNING_CLASSES` registry so each `@Configurable`
  subsystem's own tunables are captured automatically (namespaced `ClassName.fieldName`)
- **Build manifest**: `build-manifest.json` written at repo root on every build (Gradle task) —
  hardware names scanned from source automatically, tunable source defaults included
- **Hub log auto-cleanup**: `LogCleanup.maybeRun()` runs at every OpMode init, deletes matchlogs and
  stray CSVs older than 14 days so hub storage doesn't fill up over a season
- **Small utilities ready as needed**: `JoystickCurve` (deadzone + exponential curve, all params
  tunable), `SlewRateLimiter`, `Profiler`, `StaleWatcher`, `AsymmetricMotionProfile`

---

## What's ACTUALLY installed

Verified via `./gradlew :TeamCode:dependencies` and by reading `TeamCode/build.gradle`.

| Component | Version | Notes |
|---|---|---|
| FTC SDK | **11.1.0** | (see 11.2 hold in Landmines) |
| `org.solverslib:core` | **0.3.4** | command framework (FTCLib fork) |
| `org.solverslib:pedroPathing` | **0.3.4** | glue only — does NOT bundle Pedro |
| `com.pedropathing:ftc` | **2.1.2** | our own declared line — we own this version |
| `com.pedropathing:telemetry` | 1.0.0 | |
| `com.bylazar:fullpanels` | **1.0.12** | full Panels bundle (field/graphs/configurables/capture/etc.) |
| `dev.frozenmilk:Load` | **0.2.4** | Sloth Load Gradle plugin — root buildscript classpath |
| `dev.frozenmilk.sinister:Sloth` | **0.2.4** | Sloth hot-reload runtime |
| `com.acmerobotics.slothboard:dashboard` | **0.2.4+0.5.1** | Sloth's fork of FTC Dashboard (resolves the conflict — same API) |
| `junit` | 4.13.2 | `testImplementation` only, does not affect APK |

**Repositories in `TeamCode/build.gradle`:**
`maven.brott.dev` · `mymaven.bylazar.com/releases` · `repo.dairy.foundation/releases` ·
`repo.dairy.foundation/snapshots`

---

## Next action

Two proofs remain before Phase 0 is fully verified. Both need the robot in front of you.

1. **On-robot: prove snapshot writes** — run any OpMode, pull `snacktime_snapshot.json` off the
   hub (ADB or the hub's Manage page), confirm the `gitHash` field is a real commit hash like
   `84cca60`, not `"unknown"`.
2. **On-robot: prove Pedro follows a path** — Pinpoint is wired in and pod offsets are measured
   (`forwardPodY=6.735`, `strafePodX=0.287`), and `LocalizationTest` confirms pose tracking works.
   What's left is running an actual path: open `Tuning` (Pedro Pathing group) → `Tests` folder →
   `Line` (or `Triangle`/`Circle`) and confirm the Follower drives the commanded path, not just
   that pose updates. A version mismatch (Pedro 2.1.2 ↔ SolversLib 0.3.4) surfaces at **runtime,
   not build** — "it compiled" proves nothing here.

**Pre-season opportunity:** order Pollen from AndyMark and build the goBILDA StarterBot Base so
Phase 0 can prove itself against real game pieces before the September 12, 2026 kickoff.

---

## Recent significant additions (2026-07-18, third session)

- **Pedro localization set up and proven live on-robot.** Robot mass set to 6.5 kg. Pod offsets
  measured via `OffsetsTuner` (`forwardPodY=6.735` in, `strafePodX=0.287` in) and entered into
  `PinpointConstants`. Panels field view wired up: robot drawn as a red circle with heading line,
  pose history in green, telemetry to 3 decimal places with heading in degrees.
  (`pedroPathing/Constants.java`, `pedroPathing/Tuning.java`)
- **Tunables reorganized: mechanism values now live in each subsystem file, not `TuningConfig`.**
  Each `@Configurable` subsystem class holds its own `public static` tunables so Panels groups them
  by mechanism name. `Drivetrain.java` now owns speed caps, deadzone, and heading-correction PIDF
  gains directly. `TuningConfig` is down to three cross-cutting flags (`verboseTelemetry`,
  `diagnosticsProblemExpireSeconds`, `profilerEnabled`). `Persistence` scans a `TUNING_CLASSES`
  registry (namespaced `ClassName.fieldName` keys) instead of only `TuningConfig`.
- **Deadzone moved into `JoystickCurve`.** All four curve params (deadzone, minOutput,
  transitionPoint, transitionOutput) are `@Configurable` statics there now — input-shaping concern,
  not a drivetrain concern.
- **Snapshots now record loop-time stats** (`avgLoopHz`, `avgLoopMs`, `maxLoopMs`) via
  `Snapshot.captureLoop(LoopTimer)`, so loop-time regressions are visible across sessions (§0).
- **Hub log auto-cleanup added.** `LogCleanup.maybeRun()` runs at every OpMode init but only acts
  once 14+ days have passed (tracked via a stamp file); deletes matchlog `.log`/`.txt` and stray
  `.csv` files older than 14 days. Age- and extension-guarded — snapshot/tuning JSONs are safe by
  construction. Wired into all three OpModes; documented in `CLAUDE.md §14`.

---

## Recent significant additions (2026-07-18, second session)

- **Tuning backup system** — `Persistence.saveTuning()` writes all TuningConfig values (including
  dashboard-modified ones) to `current_tuning.json` on the hub on every OpMode stop.
  `loadAndApplyTuning(telemetry)` reads and applies them on every OpMode init via reflection;
  dashboard values supersede source defaults. All three OpModes wired.
  Disaster backup: `grep "SNAPSHOT:" robotControllerLog.txt` after any session for a full JSON
  of runtime values → paste into `TuningConfig.java` → commit.

- **Build-time manifest** — `generateBuildManifest` Gradle task (wired to `preBuild`) writes
  `build-manifest.json` at repo root on every build. Hardware names scanned automatically from
  teamcode source; TuningConfig source defaults parsed from source. No robot or ADB needed.
  Gitignored. Requires Gradle sync (Android Studio "Sync Now" banner).

- **Snapshot improvements** — `writeSnapshot(snap, hardwareMap)` now enumerates all configured
  devices via `getAllNames(HardwareDevice.class)` and captures all TuningConfig values via
  reflection. Full JSON also logged via RobotLog (`SNAPSHOT:` tag) so it's in the downloaded
  RC log without ADB file hunting.

---

## Recent significant additions (2026-07-18)

- **Deleted Pedro quickstart samples** — `PedroAutoSample`, `PedroTeleOpSample`, `PedroCommands`
  were crashing Sinister's `OnCreateMenuScanner` at app startup. Both TeleOp and Auto samples used
  `new TelemetryData(telemetry)` as an instance field initializer; in FTC, `telemetry` is null at
  construction time (set by the framework only after the constructor), so Sinister NPE'd when it
  tried to instantiate the classes to build the Driver Hub menu.
  **Deploy:** hot-reload (Sloth) should suffice; if the crashed menu persists, do a full install.

---

## Recent significant additions (2026-07-17)

- **11 patterns ported from FTC 5327's decode-2025** — details in the CHANGELOG's 2026-07-17
  entry. Fills concrete gaps: auto command trees, Driver-Hub alliance selection, health telemetry,
  per-field pose deltas, PIDF heading hold, plus small utilities. Explicit *skips* also documented
  (SalineSubsystem, WActuatorGroup, DataBus, custom I2C bridges, etc.) so we don't accidentally
  revisit them.
- **CLAUDE.md §1 Explain-It Gate relaxed.** Bar is now "real understanding, not surface
  simplicity." Sophisticated code (HashMaps, atomics, generics, small state machines) is welcome
  when it earns its keep. If a student doesn't understand something they're reviewing, the answer
  is to ask the AI to explain it, not to strip it out. AI's job now explicitly includes teaching.
- **Drive deadzone** added — `driveDeadzone = 0.05` in `TuningConfig`, applied in `TeleOpExample`
  via `applyDeadzone(...)`.
- **ServoUtil** added — degrees ↔ position with soft-limit clamping (`§5` compliant).
- **Verified:** SolversLib 0.3.4 already ships `com.seattlesolvers.solverslib.command.DeferredCommand`.
  Do NOT port a duplicate — use the SolversLib one directly.

---

## Decisions still standing

- **Pedro at 2.1.2.** Bumped 2.0.6 → 2.1.2 on 2026-07-15 (predictive braking, `isRobotStuck` fixes).
  Compatibility matrix says SolversLib 0.3.3+ supports Pedro 2.0.0 and higher; on-robot path-follow
  proof is what confirms the pair actually works.
- **Java + SolversLib, not Kotlin + NextFTC.** Charter §2 covers reasoning; short version: NextFTC
  drags Pedro in transitively at a pinned old version, SolversLib doesn't.
- **Pinpoint is the single source of pose. No sensor fusion.** Charter §3. Limelight is for
  aiming (relative), not pose (global).
- **Bulk reads MANUAL mode.** `bulkReads.clear()` is the first line of every loop. `util/BulkReads`
  owns this.
- **All tunables are `@Configurable` statics, live in the subsystem that owns them.** Mechanism
  values live in the mechanism's own subsystem file (e.g. `Drivetrain.java`); only cross-cutting
  flags stay in `TuningConfig`. Live-editable from Panels; once a value is dialed in, promote it
  back to source (§6 "Promote good values back to source"). New `@Configurable` classes must be
  added to `Persistence.TUNING_CLASSES` to be captured in session persistence.

---

## Landmines & notes

- **FTC Dashboard vs Sloth — RESOLVED.** We're on `com.acmerobotics.slothboard:dashboard:0.2.4+0.5.1`
  (Sloth's fork) instead of stock `com.acmerobotics.dashboard`. Same API, no source changes, hot
  reload works. If anyone re-adds the stock dashboard, it will break Sloth again.

- **FTC SDK 11.2 — HOLD until Sloth Load supports Gradle 9.1.** 11.2 bumps Gradle 8.9 → 9.1 and
  AGP 8.7.0 → 8.13.2. Sloth's Load plugin 0.2.4 breaks under Gradle 9.1 — the fix is merged
  (PR #10, May 7, 2026) but not yet released. Upgrading now would lose Sloth hot-reload with no
  workaround. Watch the Dairy Foundation repo for Load 0.2.5+, then do the upgrade as one
  coordinated change: Gradle wrapper, AGP classpath, and all 9 SDK deps (11.1.0 → 11.2.0) in the
  same build. **Revisit September 2026.**

- **Panels `@Configurable` on nested objects — UNTESTED.** `FieldTweaks` holds 6 static
  `AutonFieldTweaks` instances, each with `xOffsetInches`/`yOffsetInches`/`headingOffsetDeg`
  fields. If Panels doesn't recurse into the nested objects, we'll flatten to 18 individual
  `public static double` fields. Verify at first bench session.

- **No dependency locking.** The `{strictly X}` markers in the dependency report are Android
  Gradle Plugin variant-alignment constraints, not lockfile pins — the report's own legend says
  `(c) - A dependency constraint, not a dependency`. They do not block a version bump.

- **SolversLib has AI-readable docs.** `https://docs.seattlesolvers.com/llms.txt` for the index;
  appending `.md` to any docs page returns markdown. Use these rather than guessing at API names.

- **SolversLib ↔ Pedro compatibility matrix** (re-check, it moves): 0.3.3+ → Pedro 2.0.0 *and
  higher*; 0.3.2 → 1.0.9; 0.3.1 → 1.0.8.

- **FTCLib and SolversLib cannot coexist.** Their docs are explicit. Never add FTCLib.

- **SolversLib 0.3.4 already ships `DeferredCommand`** (verified in the core sources jar). Don't
  port a duplicate; use `com.seattlesolvers.solverslib.command.DeferredCommand`.

- **Logcat — DROPPED.** RC persistent match logs (pulled via ADB or the hub's Manage page) cover
  post-match review; Panels covers live bench monitoring. Use `RobotLog` for all event logging —
  feeds Logcat + RC logs simultaneously.

---

## Confirmed hardware (from Aaron, 2026-07-16; Pinpoint wired + measured 2026-07-18)

- **REV Servo Hub** on the robot. Add to the RC configuration alongside the Control/Expansion Hub.
  Both Robot Controller and Driver Station apps must be on **10.0+** or the Servo Hub configures as
  a generic Expansion Hub. Firmware/address changes via REV Hardware Client.
- **Pinpoint V2** (goBILDA) — the single source of pose (§3). **Physically wired in and pod
  offsets measured** via `OffsetsTuner` (`forwardPodY=6.735` in, `strafePodX=0.287` in), entered
  into `pedroPathing/Constants.java` → `PinpointConstants`. Mount facts (from Pedro's own docs):
  I2C port **other than 0** (Control Hub's built-in IMU owns port 0), sticker/port side up, forward
  pod → X port, strafe pod → Y port.
- **Drivetrain motors** — wired into `Drivetrain.java` with real RC-config names: `LF_Motor`,
  `LR_Motor`, `RF_Motor`, `RR_Motor` (ports 0-3). `pedroPathing/Constants.java` uses these too.

---

## File inventory (as of 2026-07-18)

`find TeamCode/src/main/java -name '*.java' -path '*teamcode*' | wc -l` → **30 files** across:

- `opmodes/` — `AutonomousExample`, `TeleOpExample`, `AutonMenu`, `SystemsCheck`
- `subsystems/` — `Drivetrain` (now owns its own tunables), `GameMechanism` (template)
- `commands/` — `FollowPathCommand`
- `diagnostics/` — `DiagnosticsCenter`, `Problem`, `ProblemSeverity`
- `config/` — `TuningConfig` (cross-cutting flags only), `AutonFieldTweaks`, `FieldTweaks`
- `hardware/` — `BuildInfo` (generated `GIT_HASH` + `BUILD_TIME`)
- `util/` — `BulkReads`, `LoopTimer`, `Persistence`, `Datalogger`, `ServoUtil`, `HeadingCorrector`,
  `JoystickCurve`, `Profiler`, `SlewRateLimiter`, `StaleWatcher`, `TelemetryMenu`, `LogCleanup`
- `util/profile/` — `AsymmetricMotionProfile`, `ProfileConstraints`, `ProfileState`
- `pedroPathing/` — `Constants` (Pinpoint wired in, real pod offsets), `Tuning` (from Quickstart,
  includes `OffsetsTuner` and the `Line`/`Triangle`/`Circle` path-follow tests)

**Off-robot tests** in `TeamCode/src/test/java/.../logic/`:
`JoystickCurveTest`, `SlewRateLimiterTest`, `StaleWatcherTest`, `AsymmetricMotionProfileTest`,
`ServoUtilTest`. All green under `./gradlew :TeamCode:test`.

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

### Pinpoint V2 (goBILDA Odometry Computer) — ✅ DONE (2026-07-18)
Driver is already in the FTC SDK 11.1.0 (`com.qualcomm.hardware.gobilda.GoBildaPinpointDriver`).
Physically wired in; pod offsets measured via `OffsetsTuner` and entered into
`pedroPathing/Constants.java` → `PinpointConstants`; `LocalizationTest` confirms live pose tracking
on the Panels field view. **Remaining:** add the `"pinpoint"` config name to `CLAUDE.md §10`
hardware map (still a TODO — table doesn't list it yet), and run the actual `Line`/`Triangle`/
`Circle` path-follow test (see Next Action above — pose tracking ≠ path following proven).

---

## Pre-season opportunity (BIOBUZZ)

Kickoff is **September 12, 2026**, but the Game Preview is already out:
- Scoring element is **Pollen** — ~3in plastic balls, similar to DECODE's Artifacts. **Purchasable
  from AndyMark now, ships immediately.**
- Every ecosystem partner published a **StarterBot Base = drivetrain + intake** — which is exactly
  the mechanism this skeleton already models. **goBILDA has one** (our ecosystem).

→ Phase 0 doesn't have to be abstract. Order Pollen, build the goBILDA StarterBot Base, and prove
the whole loop against *real game pieces* months before anyone has a field.

---

## Scheduled reminders

Managed via `claude.ai/code/routines`:

- **2026-10-05 09:00 EDT (13:00 UTC)** — one-shot: try squared joystick input curve during driving
  practice. If drivers disagree on preferred style, implement the driver-selectable init-phase
  curve option. Routine ID: `trig_015KmZfTx11ZwZRzAuyrBBRs`. (Note: the joystick curve utility
  `util/JoystickCurve.java` is already ported and ready to swap into `TeleOpExample.applyDeadzone`.)

---

## Handoff notes for the next session

- **Aaron (coach)** directs at the intent level and does not read code line-by-line. Talk to him
  in plain-language behavior terms.
- **Kieran & Elijah (students)** are the code-level directors. Per the relaxed Explain-It Gate,
  they can handle sophisticated patterns — but when they don't understand something, teach them,
  don't strip it out.
- **Recent commits** worth being aware of:
  - `7ef874b` — hub log auto-cleanup (14-day)
  - `84cca60` — snapshots record loop-time stats
  - `140d077`–`c0b771a` — tunables reorganized: mechanism values into subsystem files, deadzone
    into `JoystickCurve`
  - `d0fec73` — Pedro localization set up and proven live on-robot (offsets, robot mass, field viz)
  - `bb096bb` — 11 patterns ported from decode-2025; Explain-It Gate relaxed
- **Working tree is clean** — everything through `7ef874b` is committed. Two stray untracked
  directories (`META-INF/`, `com/pedropathing/...` — an accidentally-extracted Pedro sources jar)
  were found and deleted 2026-07-18; not committed since they were never tracked.
- **Do not commit unless asked.** Aaron controls when commits happen.
