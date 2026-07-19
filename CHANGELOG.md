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

## 2026-07-19 (continued, identity wired into Pedro tuning + Panels finding)
- **Wired robot identity into Pedro's `Tuning.java` suite.** Previously only `TeleOpExample`/
  `AutonomousExample` showed the identity banner — the ~15 OpModes in Pedro's own tuning menu
  (`LocalizationTest`, `OffsetsTuner`, all the PIDF tuners) had zero `RobotIdentity` visibility at
  all, on Panels or the Driver Station, despite being exactly where Step 2's hands-on tuning happens.
  Fixed at the one shared choke point: identity resolves once in `onSelect()`, banner is emitted from
  `drawCurrent()` (called by nearly every sub-OpMode's `init_loop()`/`loop()`), so one change covers
  the whole suite instead of touching 15 files.
- **Root-caused "banner missing from Panels" as a Panels-side rendering quirk, not our code.**
  Decompiled `com.bylazar:telemetry:1.0.5`'s actual bytecode rather than guess: `debug()` has no
  dedup/filtering, `update(Telemetry)` mirrors the *entire* line list to the Driver Station
  unconditionally on every call before handing the same list to Panels. Confirmed via `adb logcat`
  that `RobotIdentity` resolves correctly every OpMode selection, and confirmed the banner *does*
  reach the Driver Station from the same send that Panels receives. Since it's proven present in the
  exact batch Panels gets but doesn't render there, the gap is in Panels' web frontend, not our
  teamcode — closed, not left open. `STATUS.md` Step 1 updated with the finding so it isn't
  re-investigated later.
  (`pedroPathing/Tuning.java`, `STATUS.md`)

## 2026-07-19 (continued, driver hub polish)
- **Identity banner is now larger/colored on the Driver Hub, plain text still to Panels.** New
  `RobotIdentity.bannerHtml()` wraps the banner in `Telemetry.DisplayFormat.HTML` tags (verified
  real FTC SDK feature — "subset of HTML tags... color & size"), color-coded per robot (red=comp,
  green=test, orange=unknown). `TeleOpExample`/`AutonomousExample` set the display format once at
  init and show the HTML banner first on the Driver Hub; Panels keeps the plain-text banner (it has
  no HTML display-format concept). Also strengthened the existing RC-log line to include the literal
  banner text, not just the raw resolved name, so a post-match log read matches what was on screen.
- **Added X/Y position to TeleOp's Driver Hub telemetry**, alongside the existing Heading — reads
  off the same `follower.getPose()` call already being made each loop, so no new hardware read.
  (`util/RobotIdentity.java`, `opmodes/TeleOpExample.java`, `opmodes/AutonomousExample.java`)

## 2026-07-19 (continued, first real per-robot tuning save)
- **First test-bot tuning file pulled and committed.** `tuning/testbot_tuning.json` confirms the
  robot-aware persistence round-trip actually works on-robot: filename is the new per-robot name
  (not the old `current_tuning.json`), all 17 expected keys present (`TuningConfig`×3 +
  `Drivetrain`×10 + `JoystickCurve`×4), no metadata wrapper — matches `Persistence.saveTuning()`
  exactly. Values are still source defaults (no live-tuning done yet) — this proves the save
  mechanism, not tuned values. Closes most of Step 3 in `STATUS.md`; reload-after-power-cycle still
  to confirm. (`tuning/testbot_tuning.json`)

## 2026-07-19 (continued, step-1 checklist)
- **Added a loop-time check to Step 1's on-hub checklist.** The identity banner (`RobotIdentity` +
  `PanelsTelemetry…debug()`) should be loop-time-free by construction (string built once at init, one
  extra line riding the existing telemetry packet, §4 rule 8) — but that's never actually been
  measured on-robot: every prior `maxLoopMs` verification predates the banner code (`73a1ecc`). Step 1
  now calls out glancing at `avgLoopHz`/`maxLoopMs` on that same run's snapshot to confirm the
  code-level reasoning holds, not just assume it. (`STATUS.md`)

## 2026-07-19 (continued, handoff prep)
- **Refreshed `STATUS.md` for session handoff.** "Next action" reordered to Aaron's stated priority:
  (1) confirm hub Wi-Fi identity on-hub, (2) tune the Test Bot's Pedro, (3) confirm the JSON tuning
  save/download workflow end-to-end via `./save-tuning.sh`, (4) Limelight object detection, (5) Pedro
  path-follow to the ball. Added the `save-tuning.sh` helper to the capabilities list and the tuning
  description, and refreshed the handoff notes (recent commits, org, two-robot model pointer).
  `project_next_phase_plan` memory synced to the same 5-step order. (`STATUS.md`)

## 2026-07-19 (continued, save helper auto-detect)
- **`save-tuning.sh` now auto-detects which robot from the connected hub.** Just run `./save-tuning.sh`
  (no argument) after tuning — it reads the per-robot snapshot filename the hub writes to tell comp
  from test, then pulls the right tuning file. Explicit `comp`/`test` still works as an override. This
  was chosen over "auto-pull on connect," which is the wrong trigger (connect-time grabs last
  session's stale values, since the hub file is written on OpMode stop) and risks clobbering
  uncommitted repo edits. Clear errors if the hub is UNKNOWN or hasn't written a snapshot yet.
  (`save-tuning.sh`, `tuning/README.md`, `WORKFLOW.md`)

## 2026-07-19 (continued, save helper)
- **Added `save-tuning.sh` — one command to back up a robot's tuning.** A plain `git commit` doesn't
  save tuning, because the values live on the hub, not the repo, until pulled in. `./save-tuning.sh
  comp` (or `test`) does the `adb pull` of the right per-robot file into `tuning/` and shows what
  changed; you then `git commit && push`. Has clear errors if adb is missing or no hub is connected.
  It's committed to the repo (with the executable bit), so anyone who clones gets it — documented in
  `tuning/README.md` and `WORKFLOW.md` §11, incl. the manual `adb pull` equivalent. (`save-tuning.sh`,
  `tuning/README.md`, `WORKFLOW.md`)

## 2026-07-19 (continued, tuning model finalized)
- **Finalized the two-robot tuning model: committed per-robot files are canonical; both robots saved.**
  This *revises* the same-day robot-aware persistence below. The first cut treated the test bot's
  tuning as disposable scratch (`TESTBOT_SCRATCH_do_not_promote.json`, gitignored) with the in-code
  defaults as the sole canonical, "promoted" by hand-transcribing numbers into source. Two problems:
  Aaron needs **both** robots' tuning saved (not just comp), and the transcribe-into-code step relies
  on commit discipline he (rightly) doesn't trust. New model: each robot's tuning is a **committed
  file** — `tuning/comp_tuning.json` and `tuning/testbot_tuning.json` — and "saving" is just
  committing the whole file the robot already wrote (pull from hub → `git commit`), never copying
  numbers into `.java`. In-code defaults drop to a fallback. Files are separate, so one robot's tuning
  can't corrupt the other's. Renamed the test file (`testbot_tuning.json`), reversed the gitignore
  (tuning files are now committed; snapshots stay ignored), added `tuning/README.md` with the
  pull/commit and restore commands, and updated the unit test. No drift warning (kept simple, decided).
  (`util/Persistence.java`, `.gitignore`, `tuning/README.md`, `CLAUDE.md` §6/§7, `WORKFLOW.md` §11,
  `README.md`, `PersistenceFileNamingTest`)
- **Decided: Pedro constants stay in code, as per-robot sets — not folded into the tuning JSON.**
  `pedroPathing/Constants.java` will hold `compFollowerConstants`/`testFollowerConstants` (+ comp/test
  `PinpointConstants`), picked by `RobotIdentity` when the follower is built; both committed to git.
  Kept out of the JSON because Pedro's tuners print numbers you record (rare, few values), the follower
  is built once at init, and holding whole `FollowerConstants` objects survives Pedro version bumps.
  Documented in CLAUDE.md §6; **implemented later** — with only one robot today, two identical sets
  would be premature. (`CLAUDE.md` §6, `STATUS.md`)

## 2026-07-19 (continued, docs 2)
- **Set the next step: confirm robot identity on the real hubs.** STATUS.md "Next action" now leads
  with Step 0 — name the hubs `34672-C-RC` / `34672-T-RC`, reboot, and verify the `ROBOT:` banner,
  the resolved value, the per-robot snapshot filename, and the fail-closed UNKNOWN case on-robot
  (the on-hub behavior the unit tests can't cover). (`STATUS.md`)

## 2026-07-19 (continued, docs)
- **Documented the two-robot workflow where people actually look for it.** New `WORKFLOW.md §11`
  "Two robots: identity & tuning" — the operational how-to a coach/student needs: name each hub in
  the REV Hardware Client (`34672-C-RC` / `34672-T-RC`) and reboot, read the `ROBOT: …` banner,
  and the tuning-ownership rule (promote only from the comp robot; test tuning is scratch, never
  committed). Added a pointer from §5. (`WORKFLOW.md`)
- **Refreshed `README.md` to match the current tree.** It was stale — still listed the removed intake
  files (`IntakeLogic`, `Intake`, `IntakeLogicTest`) and an old Persistence description. Now reflects
  the real four-layer tree (commands/diagnostics/pedroPathing/util+profile), the robot-aware
  Persistence, `RobotIdentity`, the actual test files, and a short "Two robots, one codebase"
  section. (`README.md`)

## 2026-07-19 (continued)
- **Charter: we run ONE Limelight, not two.** Updated the hardware map and the vision note — model
  iteration and competition use share the single camera, so model deploys stay deliberate (validate
  first, keep the previous model to roll back to). (`CLAUDE.md` §10, `STATUS.md`)
- **Made the whole persistence layer robot-aware — tuning and snapshots are now per-robot.** Builds
  on the RobotIdentity work below. Tuning files are chosen by identity: competition robot writes/reads
  `comp_tuning.json`, test bot uses `TESTBOT_SCRATCH_do_not_promote.json` (deliberately loud name,
  gitignored), and an **UNKNOWN** hub loads and saves *nothing* (fail closed) — it never gets handed
  the competition robot's tuning by accident, and says so loudly on the Driver Hub. Snapshots are now
  per-robot too (`snacktime_snapshot_COMPETITION.json` / `_TESTBOT.json` / `_UNKNOWN.json`) so pulling
  both robots' files into one folder never clobbers. The in-code static defaults remain the canonical
  COMPETITION tuning (git backup); the test bot's scratch file stays on its hub and is never committed
  — that's what lets students tune the test bot freely without endangering the comp tuning. The
  file-selection logic is a pure function with off-robot unit tests (`PersistenceFileNamingTest`, 6
  tests, incl. the safety-critical UNKNOWN→null fail-closed case). `LogCleanup` confirmed safe — it
  only deletes `.log`/`.txt`/`.csv`, never our `.json` files. `.gitignore` covers all per-robot hub
  artifacts. CLAUDE.md §6/§7/§10 amended with the two-robot model. Hot-reloadable (Tier 2), no
  dependency change. (`util/Persistence.java`, `opmodes/*.java`, `.gitignore`,
  `TeamCode/src/test/.../PersistenceFileNamingTest.java`, `CLAUDE.md`)

## 2026-07-19
- **Added robot identity from the hub network name, shown loudly on the Driver Hub and Panels.** New
  `util/RobotIdentity.java` reads the Control Hub's network name at init and resolves which physical
  robot it's running on: a name ending `-C-RC` → COMPETITION, `-T-RC` → TESTBOT, anything else →
  UNKNOWN (fail-closed — an unidentified hub is never assumed to be the comp robot). This is the
  groundwork for loading per-robot drivetrain/Pedro tuning safely: the same commit runs on both
  robots and figures out at runtime which one it's on, keyed to the hub name (set once in the REV
  Hardware Client, requires a reboot to change — a student can't flip it from an OpMode). A loud
  `ROBOT: COMPETITION / TEST BOT / *** UNKNOWN ***` banner now shows first on the Driver Hub and is
  mirrored to Panels in all three OpModes, so which robot you're on is always visible. Identity +
  network name are also captured in every snapshot. SystemsCheck displays it and warns (not fails)
  on UNKNOWN. Verified against RobotCore 11.1.0 sources: the API (`DeviceNameManagerFactory
  .getInstance().getDeviceName()`) is real, and the FTC name validator restricts the team suffix to
  a single letter — so `-C`/`-T`, not `-COMP`/`-TEST`. Hot-reloadable (Tier 2), no dependency change.
  (`util/RobotIdentity.java`, `opmodes/TeleOpExample.java`, `opmodes/AutonomousExample.java`,
  `opmodes/SystemsCheck.java`, `util/Persistence.java`)

## 2026-07-18 (continued, eighteenth pass)
- **Decided against paying for enforced branch protection on `master`.** The `snacktime-robotics-34672`
  org is private and on the GitHub Free plan, which doesn't offer required status checks/reviews for
  private repos (needs GitHub Team, $4/user/month, or a public repo). The CI workflow added
  yesterday still runs and reports pass/fail either way — just not as a hard merge gate. Relying on
  team discipline for now, appropriate at this team size. (decision only, no code)

## 2026-07-18 (continued, seventeenth pass)
- **Added GitHub Actions CI to run the off-robot unit tests automatically.** New workflow runs
  `./gradlew :TeamCode:test` (Temurin JDK 21, matching the local dev environment) on every push to
  `master` and every PR targeting it, so a generated change is verified before it reaches the robot
  (§9, §14). This is the piece that makes a future "require status checks to pass" branch
  protection rule actually useful — not a Gradle/library dependency change, so no WARN-AND-CONFIRM
  (§6) needed; purely CI infrastructure outside the app build.
  (`.github/workflows/tests.yml`)

## 2026-07-18 (continued, sixteenth pass)
- **Coach set the next three-step development plan.** With Phase 0 closed, work moves to: (1)
  PIDF-tune Pedro path following, (2) build Limelight object detection from scratch (nothing exists
  yet beyond TODO markers), (3) compose a path-follow-to-detected-object command on top of both.
  Recorded in `STATUS.md`'s "Next action" so the sequencing survives a session handoff.
  (decision only, no code)

## 2026-07-18 (continued, fifteenth pass)
- **Snapshot timestamp switched from milliseconds to seconds.** `savedAtMillis` (epoch millis,
  hard to read at a glance) renamed to `savedAtSeconds` (epoch seconds). `Snapshot` is only ever
  built at OpMode init/stop, never in the hot loop, so this has no loop-time cost either way.
  (`util/Persistence.java`)

## 2026-07-18 (continued, fourteenth pass)
- **Root-caused and fixed the `maxLoopMs` outlier** (`1005ms` worst-case cycle against a healthy
  ~6.6ms average, seen in the pulled snapshot). Cause: `Persistence.readBatteryVolts()` is
  deliberately deferred to the first loop iteration (the voltage sensor reads `0.0` too early
  during init) and isn't covered by `BulkReads`' cache — it's a real, synchronous hub round-trip.
  `loopTimer.reset()` was firing at the end of `initialize()`, before that one-time cost landed in
  the loop, so it silently counted toward every session's worst-case reading. Moved `reset()` to
  fire right after that one-time read instead, in both `TeleOpExample` and `AutonomousExample`.
  Confirmed fixed on-robot: `maxLoopMs` dropped `1005 → 300.6 → 27.0ms` across three snapshot pulls,
  landing at a normal, explainable first-loop cost instead of an unexplained 46–150x spike.
  (`opmodes/TeleOpExample.java`, `opmodes/AutonomousExample.java`)

## 2026-07-18 (continued, thirteenth pass)
- **Snapshot loop-time fields rounded to 1 decimal place.** `avgLoopHz`, `avgLoopMs`, and
  `maxLoopMs` in `snacktime_snapshot.json` were full double precision (e.g. `151.62107592675073`)
  — noise past the first decimal doesn't help anyone reading the file. Rounded in
  `Snapshot.captureLoop()`, which only runs once at OpMode `stop()` (not the hot loop), so this has
  no effect on loop time. (`util/Persistence.java`)

## 2026-07-18 (continued, twelfth pass)
- **TeleOp forward/backward direction fixed.** On-robot testing found the drivetrain drove forward
  and backward opposite of the controller, while strafe matched correctly. Since Pedro's `Line`
  path test drove the correct physical direction autonomously, the motor wiring/directions in
  `pedroPathing/Constants.java` were confirmed fine — this was a TeleOp joystick-mapping bug only.
  Fixed by dropping the sign flip on the forward term (`driver.getLeftY()` instead of
  `-driver.getLeftY()`); strafe and turn left unchanged. Hot-reloadable (Tier 2).
  (`opmodes/TeleOpExample.java`)
- **Confirmed the Pinpoint's RC config name (`pinpoint`) and pod offsets in `CLAUDE.md §10`.**
  Hardware table previously didn't record the config name at all. (`CLAUDE.md`)

## 2026-07-18 (continued, eleventh pass) — Phase 0 complete, 6-of-6
- **All Phase 0 (§13) on-robot proofs closed out.** `SystemsCheck` passed (all 4 drive motors +
  sensors). `LocalizationTest` confirmed correct live pose tracking. `Tuning` → `Tests` → `Line`
  ran the Follower through the commanded 40" path — it drifted (expected pre-PIDF-tuning behavior,
  not a proof failure); the proof itself is that Pedro 2.1.2 and SolversLib 0.3.4 work together at
  runtime with no version-mismatch crash, which is confirmed. Pulled `snacktime_snapshot.json` via
  `adb pull /sdcard/FIRST/settings/snacktime_snapshot.json` and verified `gitHash: "d8eff89"`
  matched the exact commit running on the hub — snapshot-writes proof closed.
  One open item surfaced by the pulled snapshot: `maxLoopMs: 1005` (a single ~1s worst-case cycle
  against an otherwise healthy `avgLoopHz: 151.6`) — cause not yet identified, tracked as follow-up
  work in `STATUS.md`, not written off. (`STATUS.md`)
- **Decided: `snacktime_snapshot.json` stays a single file, overwritten on every `stop()`.**
  Considered date-stamping it for per-session history, but that already exists — every snapshot is
  also written to the RC's persistent log under a `SNAPSHOT:` line, one per OpMode run, with
  14-day retention via `LogCleanup`. Adding dated snapshot files would duplicate that history and
  need its own cleanup sweep for no real benefit. Closed, not left open. (decision only, no code)

## 2026-07-18 (continued, tenth pass)
- **Removed a stray extracted Pedro sources jar from the repo root.** `META-INF/` and
  `com/pedropathing/...` were loose source files (bare manifest + `ftc` package sources) left over
  from someone unzipping the `com.pedropathing:ftc:2.1.2` sources jar directly into the repo —
  never tracked by git, not needed to build (Gradle resolves the real dependency from Maven).
  Deleted; no build or runtime effect. (repo root)
- **Refreshed `STATUS.md` to match current repo state.** It had drifted ~10 commits behind:
  Pinpoint is now shown as physically wired in with measured pod offsets and proven-live
  localization, the two remaining Phase 0 proofs are now stated precisely (run the `Line`/
  `Triangle`/`Circle` Pedro path test; pull the snapshot JSON), tunables documentation now reflects
  the subsystem-file reorg, the file inventory and hardware sections are current, and the stale
  "uncommitted work" list was replaced with the actual clean-tree state. (STATUS.md)

## 2026-07-18 (continued, ninth pass)
- **Automatic hub log cleanup every 14 days.** New `LogCleanup.maybeRun()` runs at every OpMode init but only actually does work when 14+ days have passed since the last cleanup (tracked via a `last_log_cleanup.txt` stamp file). Deletes matchlog .log/.txt files and stray .csv files older than 14 days so hub storage doesn't fill up over a season. Wired into all three OpModes. Age- and extension-guarded; snapshot/tuning JSONs are safe by construction. CLAUDE.md §14 updated. (`util/LogCleanup.java`, `opmodes/TeleOpExample.java`, `opmodes/AutonomousExample.java`, `opmodes/SystemsCheck.java`, `CLAUDE.md`)

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
