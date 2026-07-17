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
