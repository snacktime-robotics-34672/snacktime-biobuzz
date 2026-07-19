# WORKFLOW.md — Snack Time Robotics · FTC 34672 · Development Playbook

This is the practical **how we work** guide. It pairs with `CLAUDE.md`: the charter is the *rules and
architecture*, this is the *process* for getting work done against them. Keep both at the repo root so
any AI session (e.g. Claude Code) reads them automatically. Where this says "review against the
charter," it means `CLAUDE.md`.

---

## 0. The model in one line

**The student or coach describes what the robot should do → the AI writes the code → the students
review and test it → commit.** Generation-first: humans direct, the AI implements. Roles live in
`CLAUDE.md` §1.

---

## 1. One-time setup (do once, per season)

Start from a base that ships most of the stack, then add the rest. See the integration notes and
`build.gradle.additions`; the short version:

1. Clone the **SolversLib Quickstart** as the project base — it ships SolversLib *and* the Pedro
   library already wired together. Confirm it builds and deploys clean before adding anything.
   **Verify what it actually ships** — open `build.dependencies.gradle` / `TeamCode/build.gradle`
   and look; don't assume.
2. **Pedro — always latest (team decision).** SolversLib's `pedroPathing` module is glue and does
   NOT bundle Pedro; we install Pedro ourselves, so just declare the version we want. Confirm what
   resolved with `./gradlew :TeamCode:dependencies`, then prove pathing actually runs in Phase 0 —
   a mismatch appears at runtime, not at build.
3. Add **Panels** (dashboard): repository + dependency; sync; build.
4. Add **Sloth** (hot reload) from its official guide — the fiddliest step, with FTC-Dashboard-fork
   nuances. All our code must live under `org.firstinspires.ftc.teamcode` or Sloth won't reload it.
5. Enable `buildConfig` and add the git-hash / build-time fields in `TeamCode/build.gradle` (§7).
6. Add the JUnit test dependency (for off-robot unit tests).
7. Copy the skeleton's `teamcode` packages in; one full install; deploy with Sloth thereafter.

Changing dependencies is a **WARN-AND-CONFIRM, full-install** activity (`CLAUDE.md` §6) — deliberate,
humans in the loop, never casual.

Docs: SolversLib `docs.seattlesolvers.com` · FTCLib (applies to SolversLib) `docs.ftclib.org` ·
Pedro `pedropathing.com` · Sloth `github.com/Dairy-Foundation/Sloth`.

---

## 2. Starting an AI session

- Make sure `CLAUDE.md` and this file are in context (Claude Code reads the repo root automatically).
- State the goal in one or two sentences of **intent** — what the robot should *do*, not how.
- Work in **small increments**: one behavior at a time, so each change is easy to review, test, and
  undo.

---

## 3. Writing a good request (the core skill — coach and students alike)

Anyone directing the AI — coach or student — lives or dies on how clearly they describe what they
want. Three rules:

1. **Describe behavior, not implementation.** Say what should happen; let the AI choose how.
2. **Give the trigger and the fallback.** When does it start, and what happens if a sensor drops out?
3. **Name the mechanism** using the words in the hardware map (`CLAUDE.md` §10).

Examples:

- Weak: *"Write intake code."*
- Strong: *"When the driver holds the right trigger, run the intake to pull a game piece in; release
  to stop. If the motor stalls, stop it so it doesn't burn out."*

- Weak: *"Make an auto."*
- Strong: *"At the start, drive to the first game piece while running the intake, then stop. If the
  camera can't see the target, keep driving on Pinpoint odometry rather than stopping."*

You never need to read code to write a good request — you describe, then judge whether the robot
behaves right. That's what lets the coach direct without coding, and it's the same skill students use
before they review the result.

---

## 4. The core loop

For every change:

1. **Describe** the behavior you want (§3).
2. **Generate** — the AI writes/refactors against `CLAUDE.md`, and adds a `CHANGELOG.md` entry.
3. **Review** — a student checks it matches the charter and can be explained.
4. **Test** — deploy via **Sloth** (sub-second) and, with **Panels** open, live-tune the
   configurables. Watch **Loop Hz** — it must stay at/above the target (`CLAUDE.md` §0).
5. **Observe** the robot and its telemetry; iterate from step 1 as needed.
6. **Explain-It Gate** — nothing goes on the competition robot unless Kieran or Elijah can say out
   loud what it does (`CLAUDE.md` §1).
7. **Commit** — small commit, plain-language message, changelog entry (§8).

---

## 5. Which kind of change am I making?

Use the cheapest tier that does the job (`CLAUDE.md` §6):

- **Tuning a number** (powers, gains, timeouts) → change it live on the **dashboard**. No push. The
  robot saves it to its own hub file on stop; to back it up, pull that file into `tuning/` and commit
  it — a whole-file commit, no copying numbers into source. **Two robots:** each keeps its own
  committed file; both are saved (see §11).
- **Changing logic/behavior** (teamcode) → **Sloth hot reload**, sub-second.
- **Changing a dependency / library** → **STOP. Warn and confirm** with a human, then full install
  (`CLAUDE.md` §6, NON-NEGOTIABLE).

---

## 6. Testing & verification

- **Off-robot unit tests** for pure logic (`./gradlew :TeamCode:test`) — no robot needed; run them on
  every change to logic.
- **Systems Check OpMode** before *every* match — exercises each motor, reads each sensor, pass/fail.
- **Panels** for tuning and health — flip `verboseTelemetry` **on** at the bench to watch a
  subsystem, **off** for matches to keep the loop lean.
- **Datalogger** when investigating a specific problem — turn it on, reproduce, pull the CSV, plot it.
- **Practice robot** for parallel software/mechanical work; **scrimmages** with other teams to verify
  robustness under real conditions. The practice robot uses identical config names to the comp robot.

---

## 7. Debugging (triage order)

When something's wrong, work top-down:

1. **Loop Hz** — is it below target? A slow loop causes most "weird" behavior. Use the **Android
   Profiler** to find the cost (`CLAUDE.md` §14).
2. **Logcat** — live crash traces and your log messages while tethered ("did this even run?").
3. **Persistent RC logs / datalog** — for a match you couldn't watch: pull them off the hub and read
   what happened.
4. **Snapshot** — the JSON written on stop tells you the tuning, alliance/pose, and the **git hash**
   of exactly what was running (`CLAUDE.md` §7).
5. **Undo** — if a recent change caused it, revert it (§8).

---

## 8. Undo & version control

- **Commit small and often**, with plain-language messages. Fine-grained commits mean you can revert
  one change without losing others.
- **Changelog every change** (the AI writes the entry) so "undo the intake change" maps to a commit.
- **Tag known-good builds** at each event (`comp-ready`, `quals-1-working`) for one-command rollback.
- **Experiment on branches**; keep `main` always competition-ready.
- To undo with the AI: *"Roll back the change that broke X"* → it reads `CHANGELOG.md`, finds the
  entry, and reverts the matching commit.

---

## 9. Competition day

- **Freeze and tag** a known-good build (`comp-ready`) before the event.
- Run the **Systems Check** before every match; glance at the snapshot's starting battery voltage.
- Keep the **Driver Hub** telemetry minimal and glanceable (intake state, mode, battery, Loop Hz).
- **Hotfix discipline:** only tune configurables or make tiny, explained Sloth changes at an event —
  no dependency changes, no big rewrites. Commit and tag anything that works before the next match.

---

## 10. Season cadence

Each season is a fresh build (`CLAUDE.md` philosophy). Order:

1. **Phase 0** — stand up the stack and *prove the whole loop works* (Sloth reload, a snapshot, the
   systems check) on something trivial, before game-specific code (`CLAUDE.md` §13).
2. **At kickoff** — translate the game strategy into subsystems and commands (the coach's call).
3. **Build mechanisms** — describe each, generate, review, test; scale the patterns in the flagship
   subsystem to each new mechanism.
4. **Harden** — reliability, fallbacks, per-subsystem health, and competition readiness.

---

## 11. Two robots: identity & tuning

We run a **Competition robot** and a **Test bot** off the *same commit* — never a fork (the rules
are `CLAUDE.md` §6/§7/§10; this is how you actually work with them). The code figures out which
robot it's on at runtime from the **hub network name**, and loads that robot's own tuning.

### One-time setup per hub (do this before anything else works right)
1. In the **REV Hardware Client**, name each Control Hub:
   - Competition robot → **`34672-C-RC`**
   - Test bot → **`34672-T-RC`**
   The single-letter suffix (`-C` / `-T`) is required — the FTC name validator rejects words like
   `-COMP`, and it's also the competition-inspection convention.
2. **Reboot** the hub so the new name takes effect.
3. That's it — a student can't change identity from an OpMode; it takes the Hardware Client + a
   reboot, on purpose.

### How to tell which robot you're on
Every OpMode shows a loud banner, first line, on the Driver Hub **and** Panels:
- `ROBOT: COMPETITION` / `ROBOT: TEST BOT` — you're good.
- `ROBOT: *** UNKNOWN ***` — the hub name matches neither pattern. The robot **loads no tuning**
  and runs on the in-code (competition) defaults. Fix the hub name (step 1 above). Systems Check
  also warns on this.

### Tuning ownership — how both robots stay saved, without transcription
There are two kinds of tuning, saved two ways:

**Dashboard values** (`@Configurable` — `Drivetrain`, `JoystickCurve`): each robot has its own
committed file, `tuning/comp_tuning.json` / `tuning/testbot_tuning.json`, and **both are canonical
and backed up in git** — neither is disposable.
- Each robot reads *its own* file at init and writes it on stop. Separate files, so tuning one robot
  never touches the other's values.
- **To save: commit the file the robot wrote** — a plain `git commit` is NOT enough, because the
  values are on the hub, not in the repo. Pull the hub file into `tuning/` first, then commit. With
  the hub connected, run `./save-tuning.sh` (no argument — it auto-detects comp vs test), then
  `git commit && push`. It's a whole-file commit; you **never copy individual numbers into source
  code.** Run it *after* tuning + stopping the OpMode (that's when the hub writes the file), not just
  on connecting. (Details and the manual `adb pull` equivalent: `tuning/README.md`.)
- The in-code defaults are only a last-resort fallback for a fresh/reflashed hub before its file is
  restored (`adb push` the committed file back).

**Pedro values** (velocities, PIDFs, pod offsets): per-robot constant sets in
`pedroPathing/Constants.java` (`compFollowerConstants` / `testFollowerConstants`), both committed to
git, picked by identity when the follower is built. You tune with Pedro's own OpModes, put the number
into the right set, and commit — a few values, tuned rarely.

### Worked example: tuning one value, `Drivetrain.headingP`
The heading-hold gain genuinely wants to differ between robots — the loaded comp robot has more
rotational inertia than a bare test chassis. Here's the whole lifecycle:

1. **In code**, `Drivetrain.java` has `public static double headingP = 1.2;` — just a fallback
   default now, not the canonical value.
2. **At init**, each robot loads its own committed file over that default: the comp hub applies
   `comp_tuning.json`, the test hub `testbot_tuning.json`. The `ROBOT:` banner tells you which you're
   on.
3. **On the test bot**, Kieran drags `headingP` to `0.8` live in Panels (no deploy). He stops the
   OpMode → the value saves to `testbot_tuning.json` on the test hub. Power-cycle → `0.8` reloads.
4. **To save it durably**, he pulls that file into `tuning/testbot_tuning.json` and commits it (one
   `git commit`, no number-copying). Now the test bot's tuning is in git. Committing it touches
   **only** the test file — the comp robot's `comp_tuning.json` is untouched.
5. **The comp robot** works the same way, into its own file. Both robots end up saved in git,
   independently, with no transcription and no way to corrupt each other.
6. **Deploying the same commit** to either hub does **not** touch either hub's tuning file — each
   robot keeps its own values even though the code is byte-identical.

Edge cases: a **reflashed** hub falls back to the in-code defaults until you `adb push` its committed
file back; an **unnamed** hub shows `ROBOT: *** UNKNOWN ***` and loads nothing. Both are safe, neither
guesses.

### When you pull files off a hub
Snapshots and tuning files are per-robot and self-describing (`..._COMPETITION.json` /
`..._TESTBOT.json`). The `robot` and `networkName` fields inside each snapshot tell you exactly which
robot and which hub produced it.

---

*Rules and architecture: see `CLAUDE.md`. Change history: see `CHANGELOG.md`.*
