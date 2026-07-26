# SETUP.md — Getting the robot code running, from zero

For Aaron (coach/director) and the student programmers. Follow this **in order**. Each phase ends in
a **CHECKPOINT** — do not move past a failed checkpoint, because everything after it depends on it.
Debugging one broken thing is easy; debugging five stacked broken things is not.

> **Already partway through?** `STATUS.md` records the *current* verified state of this project —
> what's installed, what's left, and known landmines. Read it before following the phases below;
> Phases 1–5 are already done and proven (as of 2026-07-16).

**Two ground rules:**
1. **Verify, don't assume.** Every version number and every "the quickstart includes X" claim gets
   checked by looking, not by trusting a doc (including this one).
2. **You do not hand-edit Gradle.** Phases 1–5 are point-and-click. Phase 6 hands the dependency
   work to Claude Code, which can read your actual files and resolve current versions.

Time: roughly an evening for Phases 1–7, if nothing fights you.

---

## Phase 1 — Prerequisites (15 min)

- [ ] **Android Studio** installed and opens.
- [ ] **Git** installed *separately* — Android Studio needs it. Open a terminal, run `git --version`.
      If that errors, install git first (`git-scm.com`).
- [ ] **GitHub account connected**: Android Studio → Settings (Preferences on Mac) →
      Version Control → GitHub → **+** → sign in. This saves pain in Phase 7.
- [ ] **Control Hub** powered, and a **Driver Hub** (or phone) to see OpModes on.

**CHECKPOINT 1:** `git --version` prints a version, and your GitHub account is listed in Settings.

---

## Phase 2 — Make your own repo (10 min)

You start from the **SolversLib Quickstart** — a complete FTC project with SolversLib *and* the Pedro
Pathing library already wired together. You **fork** it rather than clone it directly, so your
commits push to a repo *you own*.

> Two different homes, don't confuse them: the **Quickstart** (a robot project — you fork this) lives
> on **GitHub**. The **SolversLib library itself** (what Gradle downloads) lives on the **Dairy
> Foundation** maven repo, not GitHub and not Maven Central. You never fork the library — it's one
> `repositories` line, the same Dairy repo Sloth uses.

1. Go to **`github.com/FTC-23511/SolversLib-Quickstart`**
2. Click **Fork** (top right).
3. In the fork dialog, rename the repository to **`snacktime-biobuzz`** — the game name identifies
   the season unambiguously (FTC seasons span two calendar years, so "2027" alone would be vague).
4. Click **Create fork**. You now own `github.com/<youraccount>/snacktime-biobuzz`.

> Later, once this project is wired and proven but still game-free (late August), copy that clean
> state into a permanent `snacktime-robot-template` repo and tick Settings → Template repository.
> Future seasons start from the template instead of repeating all nine phases.

**CHECKPOINT 2:** the repo exists under *your* GitHub account.

---

## Phase 3 — Clone it into Android Studio (20 min, mostly waiting)

1. Android Studio → **File → New → Project from Version Control**.
2. URL: your fork's URL (green **Code** button on your repo → copy the HTTPS link).
   Use **your fork's** URL, not the original.
3. Pick a folder. Click **Clone**.
4. **Wait.** The first Gradle sync downloads a lot — several minutes is normal. Watch the status bar.

**CHECKPOINT 3:** the project opens, Gradle sync finishes, no red errors. You can see
`FtcRobotController` and `TeamCode` in the left sidebar.

> Stuck syncing? Almost always internet, a firewall, or a stale Gradle cache.
> **File → Invalidate Caches / Restart** fixes a surprising amount.

---

## Phase 4 — Prove the untouched base works (20 min)

**Do not skip this.** You are proving the base is good *before* you introduce any variables.

1. **Build → Make Project.** Wait for **BUILD SUCCESSFUL**.
2. Connect to the Control Hub's Wi-Fi network.
3. **Run → Run 'TeamCode'** to install it.
4. On the Driver Hub, open the OpMode list.

**CHECKPOINT 4 (the big one):** the app installs and you see the quickstart's **example OpModes**
in the list. The base project builds and deploys.

If this fails, fix it here. Nothing later can work if this doesn't.

---

## Phase 5 — Inventory what you actually have (15 min)

This is the step that prevents the mistake of *assuming* what's installed.

1. Open **`build.dependencies.gradle`** (project root) and **`TeamCode/build.gradle`**. Read the
   `repositories` and `dependencies` blocks. Literally look at the lines.
2. In Android Studio's **Terminal** tab, run:
   ```
   ./gradlew :TeamCode:dependencies
   ```
   (On Windows: `gradlew :TeamCode:dependencies`)

Write down the answers:

- [ ] Is **SolversLib** there (`org.solverslib:core`, `org.solverslib:pedroPathing`)? What version?
- [ ] Is **Pedro** (`com.pedropathing:ftc`) there? What version? SolversLib does NOT bundle it, so
      it should be its own explicit line — that's what lets us run the latest.
- [ ] Is **Panels** there?
- [ ] Is **Sloth** there? (Almost certainly not — that's Phase 7.)

**CHECKPOINT 5:** you have a written list of what's installed and what's missing. This list drives
Phase 6 — you're not guessing.

---

## Phase 6 — Add our code and docs (15 min)

1. Unzip `snacktime-robot-skeleton.zip`.
2. Copy these package folders into `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`:
   `config/`, `hardware/`, `logic/`, `subsystems/`, `util/`, `opmodes/`
3. Copy the test folder into `TeamCode/src/test/java/org/firstinspires/ftc/teamcode/`.
4. Copy **`CLAUDE.md`**, **`WORKFLOW.md`**, **`CHANGELOG.md`**, **`SETUP.md`** (this file), and
   `README.md` into the **project root** — the top folder, next to `build.dependencies.gradle`.
   Root placement is what makes Claude Code read them automatically.

**Expect the build to FAIL now.** The code references Panels and Sloth, which aren't installed yet.
That's correct and expected — Phase 7 fixes it.

**CHECKPOINT 6:** files are in place; the build fails with "unresolved reference" errors about
Panels/Sloth (not about our own class names).

---

## Phase 7 — Hand the Gradle work to Claude Code (30–60 min)

The dependency wiring is the part that's painful by hand and easy for an AI that can read your
actual files. Install Claude Code, point it at the project folder, and give it roughly this:

> Read CLAUDE.md and WORKFLOW.md first — they're the rules for this repo.
>
> I need you to finish the dependency setup. Work **one library at a time**, and build after each so
> a failure points at exactly one thing. Per CLAUDE.md §6, **warn me and get my confirmation before
> each dependency change**.
>
> 1. First run `./gradlew :TeamCode:dependencies` and tell me what's already installed.
> 2. Install **Panels** (the dashboard) per its current official docs.
> 3. Install **Sloth** (hot reload) per its current official docs at
>    github.com/Dairy-Foundation/Sloth. Note the FTC Dashboard fork nuance — if another library
>    pulls dashboard via implementation/api, it needs excluding.
> 4. Make sure **Pedro is at the latest version** — see build.gradle.additions for the reasoning.
>    SolversLib's pedroPathing module does not bundle Pedro, so we declare it ourselves. Check
>    SolversLib's compatibility matrix at docs.seattlesolvers.com first, then show me
>    `./gradlew :TeamCode:dependencies` output proving which Pedro actually resolved.
> 5. Enable `buildConfig` in TeamCode/build.gradle and add the `GIT_HASH` and `BUILD_TIME` fields
>    that `hardware/BuildInfo.kt` expects.
> 6. Add the JUnit test dependency so `./gradlew :TeamCode:test` runs.
>
> Look up current version numbers — don't trust the example numbers in build.gradle.additions.

Then: **one full install** (Run 'TeamCode'). After this, deploy with Sloth.

**CHECKPOINT 7:**
- [ ] `Build → Make Project` = BUILD SUCCESSFUL
- [ ] `./gradlew :TeamCode:test` passes (the IntakeLogic unit tests)
- [ ] `./gradlew :TeamCode:dependencies` shows the **latest** Pedro
- [ ] Our OpModes ("34672 TeleOp", "34672 Auto", "34672 Systems Check") appear on the Driver Hub

---

## Phase 8 — First commit and push (10 min)

1. Android Studio → **Git → Commit**. Select all. Message: `Add charter, workflow, and robot skeleton`.
2. **Commit and Push**.
3. Check your GitHub repo in a browser — the files should be there.

**CHECKPOINT 8:** your work is on GitHub. From here, git is your undo button (CLAUDE.md §12).

---

## Phase 9 — Charter Phase 0: prove the loop (a session or two)

Now prove the *workflow* works on something trivial, before the season is on the line
(CLAUDE.md §13). In order:

1. **Config names.** Set the Robot Controller configuration on the hub to match the names in
   `CLAUDE.md` §10 and the subsystem files (`front_left`, `front_right`, `back_left`, `back_right`,
   `intake_motor`). Use the **same names on the practice and competition robots**.
2. **Systems Check** — run it. Every motor found, each pulse moves the right mechanism.
3. **Drive it.** TeleOp, sticks move the robot correctly. Open **Panels** and watch per-wheel
   telemetry (flip `verboseTelemetry` on) and **Loop Hz** (must hit the §0 target).
4. **Prove Sloth.** Change something trivial and visible (e.g. a telemetry label), hot-reload, and
   confirm it appears in about a second. *This is the heartbeat of the whole method.*
5. **Prove the snapshot.** Stop the OpMode, then pull the JSON off the hub and confirm it has your
   tuning values and the git hash.
6. **Prove Pedro follows a path.** Critical: a Pedro/extension version mismatch appears at
   **runtime**, not at build. "It compiled" proves nothing. Drive one simple path on the practice
   robot and watch it in Panels' field view.

**CHECKPOINT 9:** all six. Now you have a working robot *and* a working method, and you're ready for
game-specific code at kickoff.

---

## When things go wrong

| Symptom | Look here |
| --- | --- |
| Gradle sync fails | Internet/firewall; File → Invalidate Caches / Restart |
| "Unresolved reference" for Panels/Sloth | That library isn't installed yet — Phase 7 |
| Builds fine, crashes on the robot | **Logcat** (CLAUDE.md §14) — a version mismatch looks like this |
| Sloth doesn't reload my change | Is the file under `org.firstinspires.ftc.teamcode`? Did you change a dependency (needs full install)? |
| Wrong wheel moves | Robot Controller config names vs. §10; motor directions in `initialize()` |
| Loop Hz is low | Android Profiler (CLAUDE.md §14); check for per-loop allocations |

**Version numbers drift.** Every number in `build.gradle.additions` is an example, not gospel — have
Claude Code look up current ones. **Sloth is the fiddliest install**; if it fights you, Dairy
publishes templates with it pre-wired that you could start from instead.

---

*Rules: `CLAUDE.md` · Day-to-day process: `WORKFLOW.md` · History: `CHANGELOG.md`*
