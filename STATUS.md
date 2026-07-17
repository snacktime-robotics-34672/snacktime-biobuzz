# STATUS.md — where this project actually is

**Last updated:** 2026-07-17 — Phase 0 mostly proven; 11 patterns ported from decode-2025; Explain-It Gate relaxed.

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
- ⏳ **Snapshot writes proof on-robot** (still pending)
- ⏳ **Pedro follows a path proof on-robot** (still pending — needs Pinpoint mounted + pod offsets)

**Ready-to-use capabilities already in teamcode** (all Tier 2, hot-reloadable):
- **TeleOp**: field-centric mecanum drive, deadzone, LEFT_BUMPER slow mode, loop-time readout
- **Autonomous**: Driver-Hub pre-match menu (alliance / start pose / field / delay), command-tree
  scheduling, command lifecycle logging (gated behind `verboseTelemetry`), snapshot persistence
- **Path following**: `FollowPathCommand` wraps Pedro so autos compose as command trees
- **Health telemetry**: `DiagnosticsCenter.reportProblem(code, data)` from any subsystem drains to
  Driver Hub each loop
- **Per-field pose deltas**: `FieldTweaks.lookup(isRed, field)` returns the live-tunable pose
  offsets selected via the menu
- **HeadingCorrector**: opt-in PIDF heading hold (disabled by default; enable via
  `TuningConfig.headingCorrectionEnabled`)
- **Servos**: `ServoUtil.degreesToPositionClamped(deg, min, max, range)` — soft limits + degrees API
- **Small utilities ready as needed**: `JoystickCurve`, `SlewRateLimiter`, `Profiler`,
  `StaleWatcher`, `AsymmetricMotionProfile`

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
   `bb096bb`, not `"unknown"`.
2. **On-robot: prove Pedro follows a path** — with the Pinpoint physically wired in and
   `pedroPathing/Constants.java` → `PinpointConstants` filled in with real pod offsets, run the
   Pedro tuning OpModes. A version mismatch (Pedro 2.1.2 ↔ SolversLib 0.3.4) surfaces at
   **runtime, not build** — "it compiled" proves nothing here.

**Pre-season opportunity:** order Pollen from AndyMark and build the goBILDA StarterBot Base so
Phase 0 can prove itself against real game pieces before the September 12, 2026 kickoff.

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
- **All tunables live in `TuningConfig` as `@Configurable` statics.** Live-editable from Panels;
  once a value is dialed in, promote it back to source (§6 "Promote good values back to source").

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

## Confirmed hardware (from Aaron, 2026-07-16)

- **REV Servo Hub** on the robot. Add to the RC configuration alongside the Control/Expansion Hub.
  Both Robot Controller and Driver Station apps must be on **10.0+** or the Servo Hub configures as
  a generic Expansion Hub. Firmware/address changes via REV Hardware Client.
- **Pinpoint V2** (goBILDA) — the single source of pose (§3). Mount facts (from Pedro's own docs):
  I2C port **other than 0** (Control Hub's built-in IMU owns port 0), sticker/port side up, forward
  pod → X port, strafe pod → Y port. Pod offsets belong in `pedroPathing/Constants.java` →
  `PinpointConstants`.
- **Drivetrain motors** — wired into `Drivetrain.java` with real RC-config names: `LF_Motor`,
  `LR_Motor`, `RF_Motor`, `RR_Motor` (ports 0-3). `pedroPathing/Constants.java` uses these too.

---

## File inventory (as of 2026-07-17)

`find TeamCode/src/main/java -name '*.java' -path '*teamcode*' | wc -l` → **~30 files** across:

- `opmodes/` — `AutonomousExample`, `TeleOpExample`, `AutonMenu`
- `subsystems/` — `Drivetrain`, `GameMechanism` (template)
- `commands/` — `FollowPathCommand`
- `diagnostics/` — `DiagnosticsCenter`, `Problem`, `ProblemSeverity`
- `config/` — `TuningConfig`, `AutonFieldTweaks`, `FieldTweaks`
- `hardware/` — `BuildInfo` (generated `GIT_HASH` + `BUILD_TIME`)
- `util/` — `BulkReads`, `LoopTimer`, `Persistence`, `Datalogger`, `ServoUtil`, `HeadingCorrector`,
  `JoystickCurve`, `Profiler`, `SlewRateLimiter`, `StaleWatcher`, `TelemetryMenu`
- `util/profile/` — `AsymmetricMotionProfile`, `ProfileConstraints`, `ProfileState`
- `pedroPathing/` — `Constants`, `Tuning` (from Quickstart; edited by us — `Pinpoint` wired in)
- `samples/` — `PedroCommands`, `PedroAutoSample`, `PedroTeleOpSample` (from Quickstart; safe to
  delete once real code exists — they only clutter the Driver Hub OpMode list)

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

### Pinpoint V2 (goBILDA Odometry Computer)
Driver is already in the FTC SDK 11.1.0 (`com.qualcomm.hardware.gobilda.GoBildaPinpointDriver`) —
no extra dependency needed. What needs doing:
- Wire it physically: **I2C port other than port 0** (the Control Hub's built-in IMU owns port 0),
  sticker/port side up, forward pod → X port, strafe pod → Y port.
- Fill in `pedroPathing/Constants.java` → `PinpointConstants` with the actual pod offsets
  (mm from robot center) and encoder directions. These values come from your physical mounting.
- Add the config name to `CLAUDE.md §10` hardware map once locked in.

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
  - `bb096bb` — 11 patterns ported from decode-2025; Explain-It Gate relaxed
  - `1cb8f1f` — drive deadzone; fixed stray `do` token in build.gradle; Sloth marked proven
  - `e4fe17a` — Sloth Load first working
  - `ede6404` — field-centric TeleOp; intake skeleton replaced with `GameMechanism` template
- **git is on `master`, tracking `origin/master`, clean.** All commits pushed as of handoff.
- **Do not commit unless asked.** Aaron controls when commits happen.
