# Changelog — Snack Time Robotics (FTC 34672)

Plain-language history of meaningful changes, **newest first**. This pairs with the rollback
workflow in `CLAUDE.md` §12 (small commits, known-good tags, experiment branches).

**Rule (CLAUDE.md §12) — the AI writes every entry.** Every change the AI makes adds a line here,
in plain English a coach can read: *what* changed and *why*, naming the mechanism or area rather
than internal jargon. That's what lets "undo the intake change" map straight to the right commit.

**Format**

```
## YYYY-MM-DD
- <what changed> — <why>. (<file/area; tag if this is a known-good build>)
```

Tag a line with the git tag when a build is a known-good checkpoint, e.g. `(tag: comp-ready)`, so a
one-command rollback target is easy to find later.

---

## 2026-07-18 (continued, eighth pass)
- **Snapshots now capture loop-time stats (avgLoopHz, avgLoopMs, maxLoopMs).** Each snapshot records the OpMode's smoothed average loop rate and worst-case cycle time via a new `Snapshot.captureLoop(LoopTimer)` helper, so we can watch loop-time trends across sessions and catch regressions caused by code changes (§0 prime directive). Wired into TeleOpExample and AutonomousExample. Also swept Persistence docstrings that still said "TuningConfig statics" from before the multi-class registry — now correctly say "registered tunables." (`util/Persistence.java`, `opmodes/TeleOpExample.java`, `opmodes/AutonomousExample.java`)

## 2026-07-18 (continued, seventh pass)
- **Deadzone moved from Drivetrain into JoystickCurve.** The deadzone is an input-shaping concern, not a drivetrain concern, so it belongs alongside the other joystick curve parameters. JoystickCurve is now @Configurable with all four params as live-tunable statics (deadzone, minOutput, transitionPoint, transitionOutput). Registered in Persistence. (`util/JoystickCurve.java`, `subsystems/Drivetrain.java`, `util/Persistence.java`, `opmodes/TeleOpExample.java`)

## 2026-07-18 (continued, sixth pass)
- **Drivetrain tunables moved from TuningConfig into Drivetrain.java.** Speed caps, deadzone, and all heading-correction PIDF values are now public static fields at the top of the Drivetrain subsystem file, grouped under "Drivetrain" in Panels. TuningConfig now holds only the three cross-cutting flags (verboseTelemetry, diagnosticsProblemExpireSeconds, profilerEnabled). Persistence updated to scan both classes. References in TeleOpExample and HeadingCorrector updated to point to Drivetrain. (`subsystems/Drivetrain.java`, `config/TuningConfig.java`, `util/Persistence.java`, `util/HeadingCorrector.java`, `opmodes/TeleOpExample.java`)

## 2026-07-18 (continued, fifth pass)
- **Mechanism tunables now live in the subsystem file, not TuningConfig.** Each `@Configurable` subsystem class holds its own `public static` fields so Panels groups them by mechanism name. `Persistence` now scans a `TUNING_CLASSES` registry (namespaced `ClassName.fieldName` keys) instead of only `TuningConfig`, so all registered mechanism values are included in session persistence. `GameMechanism.java` updated as the template. CLAUDE.md §6 and TuningConfig updated so the rule is documented for kickoff. (`util/Persistence.java`, `subsystems/GameMechanism.java`, `config/TuningConfig.java`, `CLAUDE.md`)

## 2026-07-18 (continued, fourth pass)
- **Pedro localization fully set up on-robot.** Robot mass set to 6.5 kg. Pod offsets measured via OffsetsTuner (forwardPodY=6.735 in, strafePodX=0.287 in) and entered into PinpointConstants. OffsetsTuner added to Tuning menu. Field visualization wired up in Panels: robot shown as red circle with heading line, pose history in green, telemetry formatted to 3 decimal places with heading in degrees. (`pedroPathing/Constants.java`, `pedroPathing/Tuning.java`)

## 2026-07-18 (continued, third pass)
- **Snapshot now captures real battery voltage and port info per device.** Battery voltage was always 0.0 because the voltage sensor isn't ready during OpMode init — fixed by reading it on the first loop tick instead, storing it as a field, and using that in the stop snapshot. Hardware map in the snapshot now shows which port each device is on (e.g. `"LF_Motor": "port 0"`) instead of just the name, so the snapshot doubles as a wiring record. (`util/Persistence.java`, `opmodes/TeleOpExample.java`, `opmodes/AutonomousExample.java`)

## 2026-07-18 (continued, second pass)
- **Added tuning backup: save on stop, load on init.** `Persistence.saveTuning()` writes every TuningConfig value (including dashboard-modified values) to `current_tuning.json` on the hub. `Persistence.loadAndApplyTuning(telemetry)` reads it back and applies values to the live TuningConfig statics via reflection — dashboard values supersede source defaults automatically. All three OpModes wired: load at init, save at stop. Driver Hub shows `LOADED TUNING FROM FILE (timestamp)` whenever a file is found. Survives robot restarts and code hot-reloads; a hub re-flash wipes the file (git is the disaster backup — grep `SNAPSHOT:` in the RC log after any session and paste values back to `TuningConfig.java`). (`util/Persistence.java`, `opmodes/TeleOpExample.java`, `opmodes/AutonomousExample.java`, `opmodes/SystemsCheck.java`)

---

## 2026-07-18 (continued)
- **Build-time manifest generated on every build.** A `generateBuildManifest` Gradle task (wired to `preBuild`) writes `build-manifest.json` at the repo root every time code is deployed — hot-reload or full install. Contains git hash, build time, all TuningConfig default values parsed from source, and hardware device names scanned automatically from the teamcode source (catches `new MotorEx(hardwareMap, "name")`, `.hardwareMapName("name")`, and `hardwareMap.get(Class, "name")` patterns — no manual list). No robot or ADB needed; the file is on your Mac immediately after any build. `build-manifest.json` is gitignored. (TeamCode/build.gradle, .gitignore)
- **Snapshot now auto-captures all hardware devices and TuningConfig values.** `writeSnapshot(snap, hardwareMap)` enumerates every configured device from the RC hardware map via `getAllNames(HardwareDevice.class)` — motors, servos, sensors — sorted alphabetically so diffs are stable. TuningConfig values are captured via reflection so new tunables appear automatically with no Persistence change. The full JSON is emitted via `RobotLog.i("SNAPSHOT:…")` so it appears in `robotControllerLog.txt` (grep `SNAPSHOT:`) without needing ADB file access. All three OpModes updated to pass `hardwareMap`. (`util/Persistence.java`, `opmodes/SystemsCheck.java`, `opmodes/TeleOpExample.java`, `opmodes/AutonomousExample.java`)

---

## 2026-07-18
- **Fixed SystemsCheck motor names** — updated from stale placeholder names (`front_left` etc.) to the correct RC config names (`LF_Motor`, `LR_Motor`, `RF_Motor`, `RR_Motor`); removed non-existent `intake_motor` so the check runs 4 motors instead of 5. Add mechanism motors back here once they are wired and in the RC config.
- **Deleted Pedro quickstart samples** (`samples/PedroAutoSample`, `PedroTeleOpSample`, `PedroCommands`) — they crashed Sinister's `OnCreateMenuScanner` at app startup. Root cause: both TeleOp and Auto samples used `new TelemetryData(telemetry)` as an instance field initializer; `telemetry` is null at construction time in FTC (set by the framework after the constructor), so Sinister NPE'd when it instantiated the classes to build the Driver Hub menu. These were placeholder quickstart samples, confirmed safe to delete per STATUS.md.

---

## 2026-07-17
- **Incorporated 11 patterns from FTC 5327's decode-2025.** After comparing their codebase against ours (they run the same stack: SolversLib + Pedro + Sloth + Panels), we pulled in what fills real gaps and skipped what duplicates SolversLib primitives or breaks our layer boundaries. Everything went into teamcode (Tier 2 hot-reload). Ports:
  - **Auto command trees, unblocked.** `commands/FollowPathCommand.java` wraps Pedro's `follower.followPath()` as a proper `CommandBase` so autos compose as a plan instead of a hand-rolled state machine (§3). Credit: Powercube from Watt-sUP 16166 via decode-2025.
  - **Alliance / start pose / field / delay picked on the Driver Hub, not in source.** `util/TelemetryMenu.java` (OpenFTC Team, verbatim) + `opmodes/AutonMenu.java`. `AutonomousExample` now runs a pre-match selection loop; §9 requirement met (no more `ALLIANCE = "RED"` hardcoded).
  - **Per-subsystem health telemetry.** `diagnostics/{Problem,ProblemSeverity,DiagnosticsCenter}.java`. Any subsystem calls `DiagnosticsCenter.reportProblem(code, data)`, `AutonomousExample` drains it to Driver Hub each loop. Fills §5 gap. Registered as a scheduler subsystem so its periodic() expiry runs automatically.
  - **Command lifecycle logging.** Wired into `AutonomousExample.initialize()` — `RobotLog` line on every command initialize/finish/interrupt when `TuningConfig.verboseTelemetry` is on. Free traceability for §14; gated so match logs stay clean.
  - **Per-field pose deltas.** `config/AutonFieldTweaks.java` (shape) + `config/FieldTweaks.java` (`@Configurable` matrix + lookup). AutonMenu now selects a field; drift on Field 2 gets a Panels-editable x/y/heading offset without retuning the whole path. Pattern from decode-2025 AutonBase lines 22-28.
  - **PIDF heading hold (opt-in).** `util/HeadingCorrector.java` — voltage-compensated PIDF that resists heading drift when the stick isn't turning, with a configurable lag so it doesn't fight residual turn momentum. All gains moved into `TuningConfig` as live configurables; typo `supressed`→`suppressed`. Disabled by default until on-robot tuning.
  - **Slew rate limiter.** `util/SlewRateLimiter.java` — jerk-limits a value, asymmetric positive/negative rates for gravity-assisted mechanisms. Stripped the `androidx.core.math.MathUtils.clamp` call and inlined it.
  - **Joystick exponential curve.** `util/JoystickCurve.java` — pure static function extracted from SalineGamepad's `joystickScaling`. Deadzone + linear-then-exponential shape for finer low-speed control. Ready to wire into TeleOp when the drivers want a curved feel (October reminder already covers the try).
  - **Per-block profiler.** `util/Profiler.java` — `Profiler.timeIt(name, runnable)` tracks moving avg/min/max per named block. Gated behind `TuningConfig.profilerEnabled` — off for matches (RobotLog is I/O, §4 rule 8).
  - **Asymmetric motion profile.** `util/profile/{AsymmetricMotionProfile,ProfileConstraints,ProfileState}.java`. SolversLib only ships `TrapezoidProfile` (symmetric); this adds different accel/decel for gravity-assisted mechanisms (arm falling, lift descending). Genuinely new capability, not a SolversLib duplicate.
  - **Stale-channel utility.** `util/StaleWatcher.java` — the small useful idea from decode-2025's DataBus.Seat (write timestamp + `hasChangedSinceSeconds`), without the global registry that would have broken §3. For "warn if Limelight hasn't updated in 500ms" style checks.
- Unit tests added for the pure-logic ports: `JoystickCurveTest`, `SlewRateLimiterTest`, `StaleWatcherTest`, `AsymmetricMotionProfileTest`. `./gradlew :TeamCode:test` green.
- `TuningConfig` gained `diagnosticsProblemExpireSeconds`, `profilerEnabled`, and the heading-corrector gains (P/I/D/F + enabled + threshold + lag + nominal voltage).
- **Relaxed the Explain-It Gate.** §1 rewritten: bar is now "real understanding, not surface-level simplicity." Sophisticated code (HashMaps, atomics, generics) is welcome when it earns its keep; if a student doesn't understand something they're reviewing, the answer is to ask the AI to explain it, not strip it out. §1 AI bullet updated to make teaching a first-class part of the job. §9 conventions bullet dropped "middle-schooler" framing. Also saved as a durable feedback memory.
- **Verified:** SolversLib 0.3.4 already ships `com.seattlesolvers.solverslib.command.DeferredCommand` — do NOT port a duplicate. Use the SolversLib one directly.
- **Explicit SKIPs from decode-2025** (documented so we don't accidentally port them later): SalineSubsystem fast/slow-tick base class (fights §4 bulk-cache), background IMU thread (§3 no-fusion + §4 rule 5), ConfigPersister (stock FTC Dashboard internals — wrong dashboard, and we have Persistence), WActuatorGroup wrapper (duplicates SolversLib PIDF+profiles, §2), DataBus global registry (§3 layer breach), custom I2C bridges + SRSHub, their DataLogger (allocates per call), PythonExecutor (dead code), their pinned Pedro 2.0.0 / SolversLib 0.3.2 / RoadRunner deps.
- **Sloth hot-reload PROVEN on the hub** — quick sub-second load confirmed end-to-end (Phase 0 §13 acceptance for Sloth complete). Along the way, fixed a stray `do` token on line 18 of `TeamCode/build.gradle` that was breaking Gradle sync (Groovy tried to parse `do android { ... }` as a do-while loop; error pointed at line 48 `repositories {` where the parser finally gave up, not the real cause). Knocks out STATUS.md "Next action" #2; remaining Phase 0 proofs are snapshot writes and Pedro path-following. (TeamCode/build.gradle, STATUS.md)
- **Removed intake skeleton; added GameMechanism template.** Deleted `Intake.java`, `IntakeLogic.java`, and `IntakeLogicTest.java` — this robot has no intake hardware. Replaced with `GameMechanism.java`, a blank subsystem template showing the pattern (hardware init, intent-level methods, InstantCommand wrappers, periodic telemetry) to fill in at kickoff. Cleaned all intake references from `AutonomousExample.java` and `TuningConfig.java`. (subsystems/GameMechanism.java, opmodes/AutonomousExample.java, config/TuningConfig.java)
- **Added field-centric TeleOp drive; removed intake placeholder.** `TeleOpExample` now uses Pedro's `Follower.setTeleOpDrive(forward, strafe, turn, false)` — Pedro reads the Pinpoint heading and rotates stick inputs to field coordinates before applying power. Speed cap and slow-mode (LEFT_BUMPER) still work by pre-scaling inputs. Heading in degrees shown on Driver Hub. Intake subsystem, button bindings, and related code removed — this robot has no intake hardware. Sign convention (`-leftY, -leftX, -rightX`) matches `PedroTeleOpSample`; verify on robot. (opmodes/TeleOpExample.java)

## 2026-07-16
- Confirmed hardware: REV Servo Hub added to the hardware map (CLAUDE.md §10); Pinpoint 2.0 confirmed
  as localization source with I2C-port and pod-wiring notes from Pedro's docs. (STATUS.md, CLAUDE.md §10)
- Verified the file inventory after copying the skeleton in: 18 Java files total (13 ours + 5 from
  the SolversLib Quickstart — Pedro's real Constants/Tuning plus 3 sample OpModes). Documented what
  each is and which to keep. (STATUS.md)
- Escalated the FTC-Dashboard-vs-Sloth conflict from "known risk" to "confirmed blocking, top
  priority" — Sloth load is not working yet. (STATUS.md)
- Opened a question on "logcat needs to be installed" — likely the Logcat panel or adb/platform-tools
  PATH, not resolved yet. (STATUS.md)
- Setup phases 1–5 complete and verified: forked the SolversLib Quickstart, builds clean, deploys to
  the Control Hub. Added `STATUS.md` recording the real installed inventory. (STATUS.md)
- Rewrote `build.gradle.additions` down to only what's actually missing — the Quickstart already
  ships SolversLib 0.3.4, Pedro 2.0.6, Panels (fullpanels 1.0.12), and all four maven repos.
  Remaining: Sloth, the git-hash buildConfig, JUnit. (build.gradle.additions)
- Pinned verified versions into the charter's stack section. (CLAUDE.md §2)

## 2026-07-15
- **Wired real hardware config names into code.** Updated `Drivetrain.java` motor names to match
  the RC config (`LF_Motor`, `LR_Motor`, `RF_Motor`, `RR_Motor`). Completed `pedroPathing/Constants.java`
  which previously called `FollowerBuilder.build()` with no drivetrain or localizer (null crash at
  runtime) — now wires `mecanumDrivetrain(mecanumConstants)` with the correct motor names/directions
  and `pinpointLocalizer(pinpointConstants)` with `"pinpoint"` as the I2C config name. Pod offsets
  (forwardPodY, strafePodX) are still Pedro's placeholder defaults — measure from the robot and
  update before running the Pedro tuning OpModes. Updated `CLAUDE.md §10` with the real port/name
  assignments. Build clean. (Drivetrain.java, pedroPathing/Constants.java, CLAUDE.md)
- **Bumped Pedro Pathing `ftc` from 2.0.6 → 2.1.2.** Brings in the predictive braking algorithm
  (2.1.0), automatic offsets tuner (2.1.0), and heading snap + `isRobotStuck()` fixes (2.1.2).
  `pedroPathing:telemetry` stays at 1.0.0 — confirmed against the official Pedro Quickstart.
  SolversLib 0.3.4 compatibility matrix covers Pedro 2.0.0+; build clean. Runtime proof (Pedro
  actually follows a path) still required — a version mismatch only surfaces at runtime. (TeamCode/build.gradle)
- **Added JUnit 4.13.2 for off-robot unit tests.** `testImplementation "junit:junit:4.13.2"` added
  to TeamCode dependencies; `testOptions { unitTests.returnDefaultValues = true }` added so Android
  stub methods don't throw in the test JVM. `./gradlew :TeamCode:test` now runs cleanly — verified
  against the skeleton's `IntakeLogicTest` (4 tests, 0 failures, 3ms). Test-only dep: does not
  affect the APK. (TeamCode/build.gradle; §9)
- **Wired git-hash + build-time into TeamCode `BuildConfig`.** Enables `buildConfig = true` on the
  TeamCode module (build.common.gradle doesn't) and injects `GIT_HASH` (from `git rev-parse --short
  HEAD`) and `BUILD_TIME` as `buildConfigField`s in `defaultConfig`. Both fall back to `"unknown"`
  if unavailable. This makes `hardware/BuildInfo.java` return real values instead of `"unknown"`, so
  every JSON snapshot from `util/Persistence.java` points back to exact source (§7 traceability, §12).
  Verified: generated `BuildConfig.java` contains real hash + timestamp; build clean. (TeamCode/build.gradle)
- **Installed Sloth 0.2.4 for sub-second hot reload.** Added the Load 0.2.4 classpath to the root
  `build.gradle` buildscript, applied `dev.frozenmilk.sinister.sloth.load` in `TeamCode`, and added
  `dev.frozenmilk.sinister:Sloth:0.2.4` as an implementation dep. Swapped the direct FTC Dashboard
  line to Sloth's fork (`com.acmerobotics.slothboard:dashboard:0.2.4+0.5.1`) to resolve the known
  conflict — same API, no source changes required. Build clean, classpath verified. On-robot proof
  of hot-reload (§13 acceptance) still pending — needs one full install then a teamcode edit test.
  (build.gradle, TeamCode/build.gradle)
- Fixed a bad import in the skeleton's `util/Persistence.java` — `ReadWriteFile` moved from
  `org.firstinspires.ftc.robotcore.internal.system` to `com.qualcomm.robotcore.util` in a prior SDK
  release; the skeleton hadn't caught up. Unblocks any TeamCode build. (TeamCode/.../util/Persistence.java)
- Restored `build.dependencies.gradle` from HEAD — the file had been wiped to a single character,
  which was breaking the FtcRobotController module compile (all FTC SDK deps missing). No intentional
  change was in flight; treated as an accidental blank. (build.dependencies.gradle)
- **Switched from Kotlin/NextFTC to Java/SolversLib.** Reasons: students already know Java; the FTC
  community is Java; SolversLib is actively maintained with a published Pedro compatibility matrix;
  and crucially its pedroPathing module does NOT bundle Pedro, so we install the latest Pedro
  ourselves instead of fighting a stale transitive pin. Also shares the Dairy Foundation ecosystem
  with Sloth. Skeleton rewritten in Java (13 files). (whole repo)
- Added `util/BulkReads.java` — SolversLib doesn't manage bulk caching, so this is ours to own; it's
  the biggest single lever on loop time. (CLAUDE.md §0/§4)
- Students can now direct the AI, not just review it — describing behavior is a shared skill for
  coach and students alike. (WORKFLOW.md §0/§3; CLAUDE.md §1)
- Pinned Pedro Pathing explicitly to the latest version (2.1.2) — the NextFTC Pedro extension is
  glue only and pulls an older Pedro (2.0.0) transitively; declaring it explicitly overrides that.
  Must be re-verified in Phase 0, since a version mismatch would appear at runtime, not at build.
  (build.gradle.additions; CLAUDE.md §2)

## 2026-07-11
- Initial repo skeleton — four-layer architecture, a single-motor active intake as the flagship
  subsystem, loop-timer, JSON snapshots, buffered datalogger, and the pre-match systems check.
  Establishes the generation-first workflow defined in `CLAUDE.md`. (whole repo)
