# Pedro 2.1.2 → 3.0.1 migration map

Branch: `pedro-3`. Status: **dependencies bumped and resolving; code not yet migrated (88 compile
errors).** This file is the map for finishing it, so the work can be picked up by anyone.

Written 2026-09-22 against `com.pedropathing:core:3.0.1`, `revhub:3.0.1`, `tuning:1.0.1` and
SolversLib `0.3.6`, read from the published sources jars — not from guesswork.

---

## 1. The dependency set (done, on this branch)

Pedro 3 is a re-architecture, and the artifact NAMES changed:

| Old | New |
|---|---|
| `com.pedropathing:ftc:2.1.2` | `com.pedropathing:revhub:3.0.1` |
| `com.pedropathing:telemetry:1.0.0` | `com.pedropathing:tuning:1.0.1` |
| `org.solverslib:core:0.3.4` | `org.solverslib:core:0.3.6` |
| `org.solverslib:pedroPathing:0.3.4` | `org.solverslib:pedroPathing:0.3.6` |

SolversLib 0.3.6 is REQUIRED, not optional: its `pedroPathing` module depends on
`com.pedropathing:revhub:3.0.0`, and 0.3.5 and older are built against Pedro 2.x.

**It pulled two more libraries with it.** `com.pedropathing:tuning` needs Sloth **0.3.2**, while our
Panels and FTC Dashboard forks pinned Sloth **strictly 0.2.4**. That is a hard version conflict —
the build cannot resolve until all three move together:

| Old | New |
|---|---|
| `dev.frozenmilk.sinister:Sloth:0.2.4` | `dev.frozenmilk.sinister:Sloth:0.3.2` |
| `com.bylazar.sloth:fullpanels:0.2.4.1+1.0.12` | `com.bylazar.sloth:fullpanels:0.3.2+1.0.13` |
| `com.acmerobotics.slothboard:dashboard:0.2.4+0.5.1` | `com.acmerobotics.slothboard:dashboard:0.3.2+0.6.0` |

Both forks keep their Sloth-fork identity, which §2 requires. The dashboard fork also moves FTC
Dashboard 0.5.1 → 0.6.0. **After this lands, re-check the Panels canary** (type a number into
`PanelsProbe.probe` and watch the Tuning telemetry move) — CLAUDE.md §2 requires it after any Panels
or Sloth bump, and that failure mode is silent.

---

## 2. What changed in the code (the map)

### Packages that moved

| Pedro 2.1.2 | Pedro 3.0.1 |
|---|---|
| `com.pedropathing.geometry.Pose` | `com.pedropathing.math.Pose` |
| `com.pedropathing.geometry.BezierLine` | `com.pedropathing.api.Paths.line(...)` |
| `com.pedropathing.control.PIDFCoefficients` | `com.pedropathing.controllers.Controller` (composed) |
| `com.pedropathing.ftc.FollowerBuilder` | `new Follower(localizer, drivetrain, algorithm)` |
| `com.pedropathing.ftc.drivetrains.MecanumConstants` | `com.pedropathing.revhub.drivetrains.MecanumConfig` |
| `com.pedropathing.ftc.localization.constants.PinpointConstants` | `com.pedropathing.revhub.localizers.PinpointConfig` |
| `com.pedropathing.follower.FollowerConstants` | `com.pedropathing.algorithm.ForesightConfig` |
| `com.pedropathing.telemetry.SelectableOpMode` | gone — see the `tuning` artifact |
| `com.pedropathing.util.PoseHistory` | gone from that package |

### Follower methods that were renamed

| Old | New |
|---|---|
| `follower.getPose()` | `follower.pose()` |
| `follower.followPath(path, hold)` | `follower.follow(path)` |
| `follower.holdPoint(pose)` | `follower.hold(pose)` / `hold(pose, useScaling)` |
| `follower.setTeleOpDrive(f, s, t, robotCentric)` | `follower.manual(f, s, t)` |
| `follower.startTeleopDrive()` | no equivalent — `manual(...)` puts it in manual mode |
| `follower.breakFollowing()` | `follower.stop()` |
| `follower.isBusy()` | `follower.isBusy()` / `following()` / `holding()` / `idle()` |

### The configuration model is completely different

Pedro 2 used plain constants classes you filled in. Pedro 3 uses `ConfigVar` fields inside a
`Configuration<T>`, where each value is `required()` or has a default, and values are validated
(`positive()`, `nonnegative()`, `nonnull()`). A missing required value is an error at build time,
not a silent zero.

`ForesightConfig` takes CONTROLLERS, not PIDF coefficient structs:
`Controller.proportional(kP)`, `.derivative(kD)`, `.integral(kI)`, `.staticFeedforward(kS)`,
`.proportionalFeedforward(kV)`, composed with `Controller.sum(...)`.

---

## 3. THE BLOCKER: Foresight needs numbers we do not have

`ForesightConfig` marks these `required()`:

- `linearBrakeCoefficients` (a `Matrix`)
- `quadraticBrakeCoefficients` (a `Matrix`)
- `headingBrakeCoefficients` (a `Vector2D`)
- `maxAchievableForwardVelocity`, `maxAchievableStrafeVelocity`
- `naturalForwardDeceleration`, `naturalStrafeDeceleration`

Our committed tuning carries the last four, near enough: the velocities are today's
`xVelocity` / `yVelocity`, and the natural decelerations are today's zero-power accelerations.

**The three brake coefficients are new data.** Foresight is a new braking algorithm and nothing in
our committed tuning corresponds to them. They come from Pedro 3's AutoTune procedures, run on the
robot. So:

> **Both robots must be re-tuned on the bench before Pedro 3 can drive.** This is not a code-only
> migration. Comp and test each need an AutoTune session.

---

## 4. Files to change, and what happens to each

| File | Lines | What happens |
|---|---|---|
| `pedroPathing/Constants.java` | 406 | **Rewrite.** Per-robot `MecanumConfig` + `PinpointConfig` + `ForesightConfig`, built through `Configuration`, then `new Follower(...)`. Keep the two-robot split (§6). |
| `pedroPathing/Tuning.java` | ~1500 | **Delete.** Pedro 3 ships the tuner suite in `com.pedropathing:tuning` plus AutoTune, a robot-hosted web page. The Quickstart's `pedro/procedures/*` shows the replacement. |
| `pedroPathing/PedroTuningStore.java` | 244 | **Redesign.** Its flat `Pedro.*` key table names 2.x fields that no longer exist. Open question: does AutoTune own this persistence now? |
| `pedroPathing/TuningRecorder.java` | — | Follows whatever `PedroTuningStore` becomes. |
| `util/StandYourGround.java` | — | **Probably delete.** Pedro 3.0.1 added `ManualDrive.driveOrHold(follower, f, s, t)`, which is this feature upstream, with a velocity threshold as well as an input threshold. |
| `util/AllianceMirror.java` | — | Import change, plus consider `PoseFactory`, which does mirroring at the factory. |
| `commands/FollowPathCommand.java` | — | New `Paths` API. |
| `commands/DriveToPoseCommand.java` | — | `Paths.line(start, target)`, `follow()`, `hold()`. |
| `opmodes/AutonMenu.java` | — | Import change only. |
| `opmodes/TeleOpExample.java` | — | Renamed follower calls; `manual()` instead of `setTeleOpDrive()`. |

Field-centric drive is now `ManualDrive.fieldCentric(...)`, which returns `DrivePowers` — the
rotation we currently hand to Pedro is done for us.

---

## 5. Phase-0 proof checklist (§13) — none of this is done yet

Run in this order, on the TEST BOT first, never on the comp robot:

1. `./gradlew :TeamCode:assembleDebug` passes, and a FULL INSTALL puts the app on the hub.
2. Sloth hot-reload still works — change a telemetry string in teamcode, reload, see it in under a
   second. (Sloth itself moved 0.2.4 → 0.3.2, so this is a real check, not a formality.)
3. **Panels canary** — type a number into `PanelsProbe.probe`, watch Tuning telemetry move. Silent
   failure mode; CLAUDE.md §2 requires this after any Panels or Sloth bump.
4. `34672 Systems Check` passes — all six motors found, both hubs answering.
5. AutoTune runs and produces brake coefficients on the test bot.
6. A path actually follows, and `hold()` holds.
7. Tuning saves and reloads per robot, and the values reach the follower (the read-back check §6
   describes).
8. Loop Hz is at or better than today's. Foresight is new code on the hot path.

Only after all eight pass on the test bot does the comp robot get it, and it needs its own AutoTune
session.
