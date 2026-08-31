# Snack Time Robotics — Team 34672 — Robot Code (Java)

A generation-first FTC codebase. **Read `CLAUDE.md` first** — it is the operating charter, and every
file here exists to serve one of its rules. This README just maps the tree to that charter.
`STATUS.md` is where this project actually is right now (read it first if setup is underway).
`SETUP.md` walks you from zero to a working robot the first time.
`WORKFLOW.md` is the day-to-day playbook (how to actually work against the charter), and
`CHANGELOG.md` is the plain-language history of changes; per CLAUDE.md §12 the AI adds an entry for
every change it makes, so undo and rollback stay easy.

## Stack
**Java** · FTC SDK (Control Hub) · **SolversLib** (commands/subsystems; maintained FTCLib fork) ·
**Pedro Pathing** (navigation, always latest) · **Limelight 3A** (perception coprocessor) ·
**Panels** (dashboard — the **Sloth fork**, see CLAUDE.md §2) · **Sloth** (sub-second hot reload).

## How this drops in
This is a **skeleton to layer onto a base FTC workspace**, not a standalone Gradle project. Recommended:
start from the **SolversLib Quickstart** (ships SolversLib + Pedro), then:
1. copy the `org/firstinspires/ftc/teamcode/**` packages into your `TeamCode` module,
2. apply the changes in `build.gradle.additions` (Sloth, latest Pedro, git-hash BuildConfig),
3. do one full install, then deploy with Sloth from then on.

> API note: SolversLib / Panels / Pedro class names shift between versions. This skeleton follows
> their documented patterns; **confirm exact signatures against the SolversLib javadocs**
> (repo.dairy.foundation/javadoc/releases/org/solverslib/core/latest) and adjust. The *structure* is
> what matters and is stable. TODOs mark the game-specific gaps.

## The tree (four layers, CLAUDE.md §3)
```
teamcode/
├── config/      TuningConfig.java        Cross-cutting flags (§6 Tier 1); mechanism tunables live in each subsystem
│                AutonFieldTweaks.java    Shape for per-(field×alliance) pose deltas
│                FieldTweaks.java         @Configurable matrix + lookup of those deltas
├── hardware/    BuildInfo.java           Git hash + build time for snapshot traceability (§7/§12)
├── subsystems/  Drivetrain.java          Mecanum + per-wheel health telemetry; owns its own tunables (§5/§6)
│                GameMechanism.java       TEMPLATE: fill in per mechanism at kickoff (enum + configurable + commands)
├── commands/    FollowPathCommand.java   Wraps a Pedro path as a CommandBase so autos compose as trees (§3)
│                DriveToPoseCommand.java  Drives to a target pose from wherever the robot is now (§3/§5)
├── diagnostics/ DiagnosticsCenter.java   Central health reporting; drains to Driver Hub (§5)
│                Problem.java, ProblemSeverity.java
│                PanelsProbe.java         Canary proving live dashboard tuning still reaches the robot (§6)
├── util/        BulkReads.java           MANUAL bulk caching — ours to own; biggest loop-time lever (§0/§4)
│                LoopTimer.java           Measures loop time; every OpMode telemeters it (§0/§4 rule 7)
│                RobotIdentity.java       Which robot am I? (comp vs test) from the hub network name (§6/§10)
│                Persistence.java         Per-robot tuning + snapshot, git hash, robot-aware/fail-closed (§7)
│                LogCleanup.java          Deletes matchlogs/CSVs >14 days; protects our JSONs (§14)
│                Datalogger.java          Buffered CSV time-series for debugging (§14)
│                StandYourGround.java     Braces on the spot when the driver releases the sticks (§3/§5)
│                AllianceMirror.java      Author autos for blue; derive red. Set seasonSymmetry at kickoff (§9)
│                ServoUtil, JoystickCurve, SlewRateLimiter, HeadingCorrector, Profiler, StaleWatcher, TelemetryMenu
│                profile/                 AsymmetricMotionProfile (+ Constraints, State) — accel≠decel profile
├── pedroPathing/ Constants.java          Follower/drivetrain/Pinpoint wiring; pod offsets (measured on-robot)
│                Tuning.java              Pedro's tuning-OpMode menu (localization, PIDF, path tests)
└── opmodes/     AutonomousExample.java   Command tree (not a switch) + alliance/pose menu in one place (§3/§9)
                 TeleOpExample.java       Gamepad → commands, minimal driver telemetry, robot-ID banner (§3/§8)
                 AutonMenu.java           Driver-Hub pre-match picker (alliance/pose/field/delay)
                 SystemsCheck.java        Pre-match diagnostic, plain LinearOpMode (§5)

test/logic/      Off-robot unit tests (`./gradlew :TeamCode:test`) for the pure logic (§9):
                 JoystickCurveTest, SlewRateLimiterTest, StaleWatcherTest,
                 AsymmetricMotionProfileTest, ServoUtilTest, PersistenceFileNamingTest,
                 PersistenceApplyFieldTest, StandYourGroundTest, DriveToPoseTest,
                 AllianceMirrorTest          — 73 tests
```

## Two robots, one codebase (CLAUDE.md §6/§7/§10, WORKFLOW.md §11, tuning/README.md)
The same commit runs on the **Competition robot** and a **Test bot**. The code reads the hub network
name (`34672-RC` → comp, `34672-T-RC` → test) via `util/RobotIdentity` and loads that robot's own
tuning; an unnamed hub fails closed (loads nothing, runs on in-code fallback defaults, says so
loudly). Canonical tuning is the **committed per-robot files** in `tuning/` — *both* robots are
saved in git; saving is a whole-file commit, never transcribing numbers into source. Pedro constants
are per-robot sets in code. A `ROBOT: …` banner shows on the Driver Hub and Panels.

## Write each auto once (CLAUDE.md §9)
**Author every field pose for BLUE.** `AutonMenu.resolve(pose)` mirrors it for the alliance the
driver picked, then applies that field's measured offsets — in that order. Never hand-write a red
pose; the moment one exists the two alliances drift apart and you are maintaining two autos.
**Kickoff task:** set `AllianceMirror.seasonSymmetry` to the season's field symmetry and verify it on
a real field. Getting it wrong doesn't error — it drives a good path into the wrong quarter.

## Start here (Phase 0, CLAUDE.md §13 — see STATUS.md for current state)
1. Name each hub in the REV Hardware Client (`34672-RC` / `34672-T-RC`) and match device config
   names to the hardware map (§10, WORKFLOW.md §11).
2. Get `Drivetrain` driving with Panels open (field view + per-wheel telemetry).
3. Prove the loop works end to end: **Sloth hot-reload**, a **snapshot** writing on stop, and the
   **SystemsCheck** passing — all before any game-specific code.

## The rule that keeps this safe
Nothing goes on the competition robot unless Kieran or Elijah can explain what it does (CLAUDE.md §1).
   
