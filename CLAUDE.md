# CLAUDE.md — Snack Time Robotics · FTC 34672 · Robot Code Charter

This file is the single source of truth for how this codebase is built and how anyone —
human or AI assistant — is expected to work in it. **Read it fully before writing or
changing any code.** It doubles as `AGENTS.md`; keep one canonical copy at the repo root.

If a request conflicts with a rule marked **NON-NEGOTIABLE**, stop and raise it rather than
working around it.

---

## 0. Prime directive — fast loop time is paramount

**Fast loop time is the single highest priority in this codebase.** When a design choice trades
loop speed for convenience, features, or brevity, **loop speed wins**. The only thing it does not
override is safety: a fast loop that drops a fallback, a timeout, or a stale-cache guard is not a
win. Optimize loop time hard, but never by deleting the reliability rules in §5.

What this means in practice:

- **Target:** the control loop runs at **100+ Hz — under ~10 ms per cycle**. This is *measured*,
  never assumed.
- **Measure it, always.** Every OpMode instruments and telemeters its own loop time (ms and Hz), so
  a regression is visible the instant it appears. A loop-time readout is required, like battery
  voltage.
- **Every per-loop cost must justify itself.** When generating or reviewing code, prefer the
  lower-loop-cost approach, and **flag anything that adds work to the loop** — extra hardware reads,
  new allocations, heavier math — in the change summary so it's a conscious decision.

The concrete rules that deliver this live in §4; they take precedence when anything in this document
would otherwise slow the loop.

---

## 1. Who works here and how

We build **generation-first**: AI writes most of the code; humans direct, review, and test it.
Roles are deliberate:

- **Coach (Aaron)** — directs at the *intent* level: describes the behavior the robot should
  have, sets priorities, and judges whether the robot behaves correctly. Does not read code to
  do this. Requests are written as plain-language behavior ("run the intake while driving to the
  next game piece; if the camera loses the target, keep driving on odometry").
- **Student programmers (Kieran & Elijah)** — they **direct the AI too**, describing behavior the
  same way the coach does, and they are the human directors at the code level: they read, review,
  integrate, and test what the AI produces, and run the fast-reload test loop. Directing, reviewing,
  and testing *is* the engineering work here — not a lesser version of writing it by hand.
- **AI assistant** — generates and refactors implementation against this document, writes its
  own documentation and telemetry, and helps diagnose from real robot data.

### The Explain-It Gate — NON-NEGOTIABLE
Nothing goes on the **competition** robot unless Kieran or Elijah can say out loud what it does.
This is the one rule that prevents a codebase nobody understands. If a piece of code can't be
explained, it isn't ready — simplify it or don't field it.

---

## 2. The stack

- **Base:** FTC SDK **11.1.0** on the REV Control Hub (`compileSdk 34`, `minSdkVersion 24`, Java 8).
- **Language / Framework:** **Java** with **SolversLib 0.3.4** (the maintained FTCLib fork) — command +
  subsystem model. Hosted on the Dairy Foundation, the same home as Sloth. AI-readable docs at
  `docs.seattlesolvers.com/llms.txt` (any page + `.md` returns markdown) — use them rather than
  guessing API names. **Never add FTCLib**: the two cannot coexist.
- **Navigation:** Pedro Pathing — **always run the latest version** (team decision). SolversLib's
  `pedroPathing` module is glue only and, unlike other frameworks' extensions, **does not bundle
  Pedro** — we install Pedro ourselves, so choosing the latest is the normal case, not an override.
  SolversLib publishes a compatibility matrix (0.3.3+ targets Pedro 2.0.0 *and higher*); re-check it
  when bumping either. Never assume what resolved — verify with `./gradlew :TeamCode:dependencies`,
  and prove pathing actually runs in Phase 0 (§13), because a mismatch surfaces at **runtime**, not
  at build. Changing this pin is WARN-AND-CONFIRM (§6).
- **Perception:** Limelight 3A as an on-board coprocessor. All detection runs on the Limelight,
  never on the Control Hub.
- **Dashboard:** **Panels** (`com.bylazar:fullpanels`) — ships the whole bundle: field view, live
  graphs, live configurables (§6 Tier 1), capture/replay, Limelight proxy, battery, gamepad.
  Note stock **FTC Dashboard is also present** and **conflicts with Sloth's modified fork** —
  see `STATUS.md`.
- **Fast reload:** Sloth (see §6).

### Why this stack (decided)
**Java, not Kotlin:** our students already know Java, the FTC community runs on it (a real backstop
at 11pm before a qualifier), and FTCLib — which SolversLib forks — is a port of WPILib, one of the
best-documented patterns in robotics, so AI generation quality stays high.

**SolversLib, not a custom framework:** we adopt the command scheduler and its subsystem conflict
resolution (two commands can never fight over one motor) rather than owning those edge cases
ourselves. What we deliberately do *not* inherit is a stale Pedro pin — see Navigation above.

**What we own anyway:** bulk-read discipline (`util/BulkReads.java`) — SolversLib does not manage
bulk caching, and it is the biggest single lever on loop time (§0).

---

## 3. Architecture — four layers with hard boundaries

Each layer only talks to the one directly below it.

1. **Hardware abstraction** — thin typed wrappers around each device. Nothing above this layer
   touches `hardwareMap` directly. This boundary exists purely for AI-workability: it lets the
   assistant reason about a subsystem without knowing the wiring, keeps each generated change's
   blast radius contained to one device, and centralizes fail-loud device checks in one place.
   It is *not* here for portability — we bind directly to our chosen framework and library types
   (e.g. Pedro's geometry) rather than wrapping them to stay swappable.
2. **Subsystems** — Drivetrain, Intake, Vision, Localization (adjust to the season's
   game). Each owns its hardware and exposes *intent-level* methods (`intake.intake()`), and
   publishes its own state and health telemetry.
3. **Commands** — small, composable units reused across auto and teleop. Complex behavior is built
   by composing sequential and parallel groups, **not** by hand-rolling state machines.
4. **OpMode orchestration** — thin. Auto builds a command tree; teleop binds gamepad to commands.
   Almost no logic lives here.

### State machines
SolversLib's command model *replaces* the finite-state-machine pattern — the command scheduler is
itself the state-machine engine, and each command has its own small lifecycle
(initialize → execute → isFinished → end). Build multi-step behavior by composing command groups,
never a large `switch`. Use an explicit state `enum` **only inside a single subsystem** that has
genuinely distinct modes (e.g. an intake that is STOPPED → INTAKING → EJECTING), driven from that
subsystem's `periodic()`. So: command-based backbone; a small local state machine only where one
mechanism has real modes.

### Localization & vision roles
**The Pinpoint is the single source of robot pose. No sensor fusion.** Fusing the camera and
Pinpoint through a Kalman filter was tried before and cost more than it returned, so localization is
Pinpoint odometry, full stop. The Limelight's job is **relative target detection and aiming** — where
the target is relative to the robot right now — *not* global pose; its output is never blended into
the pose estimate. This is also a prime-directive win: no per-loop fusion math on the hot path.

---

## 4. Loop-time rules — NON-NEGOTIABLE

These rules deliver the prime directive (§0). The loop budget is dominated by hardware I/O, not
math. Every OpMode loop:

1. **Clear the bulk cache once, as the first line of the loop.** Hubs run in MANUAL bulk-caching
   mode. Reading a stale cache is the one failure this discipline must never allow — so the clear
   comes first, every loop, always.
2. **Read → process → write.** Batch all sensor reads, do all computation on that one consistent
   snapshot, then issue all motor writes. Never read the same encoder twice in a cycle.
3. **Never block the loop.** No `sleep`, no busy-waits, no long work inside a command's update.
   Delays go through the framework's delay/timeout primitives. (File I/O is blocking — see §7.)
4. **Perception stays on the Limelight.** The Control Hub only polls the latest result; it never
   runs inference.
5. **Read each I2C device (e.g. Pinpoint) once per loop, no more.** I2C is the most expensive
   operation on the bus.
6. **Mind the telemetry budget.** Telemetry costs loop time, especially to the Driver Station.
   Keep the in-match Driver Hub set minimal and glanceable; push heavy debugging data to the dev
   dashboard only.
7. **Measure the loop.** Every OpMode tracks its own cycle time and telemeters ms + Hz. Watch it
   during testing; if it drops below the §0 target, treat it as a bug and find the added cost.
8. **No needless per-loop allocations.** Don't create new objects, strings, or lists inside the
   loop where you can avoid it — repeated allocation causes garbage-collection hitches that spike
   loop time. Building telemetry strings every cycle is the usual culprit; keep it lean and only
   send what you're watching. Mechanism: verbose subsystem telemetry is gated behind a live
   `verboseTelemetry` flag — **off for matches** (lean, allocation-free loop), **on at the bench**
   when you need to watch a subsystem's health (§5). Loop-time itself is always telemetered via
   the `LoopTimer` utility, passing numbers rather than hand-built strings.

---

## 5. Reliability & safety rules

- **Every command has a timeout.** Nothing may hang the robot through a whole match.
- **Graceful degradation.** The Limelight is used for aiming, not pose (§3), so if it drops out,
  keep driving on Pinpoint odometry and aim from the known field geometry rather than freezing. Wire
  fallbacks in from the start, not as an afterthought.
- **Per-subsystem health telemetry.** Every subsystem publishes enough state (currents,
  velocities, target-vs-actual) to spot a fault by watching, not by reading code. Per-wheel drive
  telemetry is required.
- **Deterministic init, fail loud.** Verify every device is present before START; if something is
  missing, say so clearly rather than starting degraded.
- **Pre-match systems-check OpMode.** Maintain one OpMode that exercises every motor and reads
  every sensor with clear pass/fail telemetry. Run it before each match — it catches wiring and
  config faults on the bench instead of mid-match.
- **Actuator safety limits.** Every actuator has soft position limits and sensible power/current
  caps, so a bad command can't drive a mechanism into a hard stop or damage the robot.

---

## 6. The reload & configuration model — NON-NEGOTIABLE

There are **three tiers** of changing the robot's behavior. Always use the cheapest tier that
does the job, and structure new code so tuning stays in the cheap tiers.

### Tier 1 — Live configurable (zero push, instant)
Any *value* you might tune is a configurable field, changed from the dashboard in real time with
**no deploy at all**. This covers most day-to-day tuning, because most tuning is numbers.

- Mark tunables as configurable (Panels `@Configurable` / FTC Dashboard `@Config`), `static`,
  non-`final`.
- **Rule:** if it is a number you might adjust at a competition, it **must** be a configurable —
  never a hardcoded literal. Examples: PID/PIDF gains, feedforward constants, mechanism powers,
  target poses, speed caps, command timeouts, sensor thresholds.

### Tier 2 — Hot reload via Sloth (sub-second)
Changing *logic* inside the `org.firstinspires.ftc.teamcode` package hot-reloads in under a
second — only the teamcode is sent, not the whole app, and changes persist across robot restarts
and power cycles. **This is the default deploy for code changes.**

- **Rule:** all code we write lives in `org.firstinspires.ftc.teamcode` (or a subpackage), or
  Sloth will not hot-reload it.

### Tier 3 — Full install (~40s+, avoid when possible)
A full install is required only when you:

- add or change a library / Gradle dependency,
- change a file marked pinned,
- change anything outside the teamcode package, or
- change OpMode registration (name, class name, or enabled status).

Keep this list rare by keeping all logic in teamcode and all tunables as configurables.

### What the AI must do here
- Prefer a **configurable** over a literal. Prefer a **teamcode-local** change over editing a
  library. When a change *will* require a full install (Tier 3), **say so explicitly** in the
  summary of the change so the team isn't surprised when a hot reload doesn't take.

### Changing a library — WARN AND CONFIRM — NON-NEGOTIABLE
Adding, removing, upgrading, or swapping **any** library or Gradle dependency is the
highest-consequence edit in this codebase: it forces a full install (Tier 3), can break Sloth
hot-reload or dashboard compatibility, and shifts the whole dependency surface in ways that can
destabilize the robot. Therefore:

- The AI must **never** change a dependency on its own. Before touching `build.gradle` or any
  dependency file, it must **stop and warn** — naming the exact library and version, why it's
  needed, what it breaks (hot reload), and any alternative that avoids a new dependency — and then
  **wait for explicit human confirmation** (Kieran, Elijah, or Aaron) before proceeding.
- A new dependency is a last resort, never a convenience. Always prefer a solution built from the
  libraries already in the stack.
- Once confirmed, do the full install and commit with a message naming the dependency that changed
  and why.

### Tuning discipline
- **One change at a time.** Change a single configurable, observe, record the result, then move
  on. Never chase two variables at once.
- **Promote good values back to source.** Live-tuned configurables are runtime-only. Once a value
  is dialed in, commit it into the code as the new default — the snapshot in §7 makes this a
  copy-paste, not a transcription.

---

## 7. Persistence & snapshots — data and tuning to disk

The robot writes a JSON snapshot of its important data to the hub. One file gives us tuning
persistence, a post-match record, and traceability at once.

### Auto-export — always on
On every OpMode `stop()` (and on init), write a JSON snapshot to the hub's FIRST storage folder.
This is always on and carries no risk, because the file is a *record* — it never feeds back in on
its own. The snapshot includes:

- all configurable / tuning values,
- the **git commit hash and build timestamp** of the running code, so any snapshot ties back to
  exactly which code produced it,
- the alliance and starting pose that were selected,
- the last-known-good robot pose,
- the pre-match systems-check results and starting battery voltage.

### Load-on-init — guarded, optional
Loading tuning from a `current-tuning.json` at init is allowed **only** with these guards, so a
file can never silently override reviewed code:

- it is **loud** — telemeter `LOADED TUNING FROM FILE (<timestamp>)` whenever it happens,
- it **falls back** to code defaults if the file is missing or corrupt,
- values loaded this way are **promoted into git before the next session** — the committed code
  stays the canonical source of truth.

### Rules — NON-NEGOTIABLE
- **File I/O never happens in the main loop.** Writes and reads occur only on init, on stop, or on
  an explicit button press — file access is slow and blocking (see §4).
- **git is the real backup, not the hub.** Snapshots survive app restarts and normal code deploys,
  but a hub re-flash wipes them. Anything that matters long-term lives in git.
- This is **complementary** to Panels capture/replay, not a replacement — capture/replay replays
  telemetry streams for debugging a run; this persists tuning and metadata.

---

## 8. Verification — judge by behavior, not by reading code

Correctness is confirmed by watching the robot and its telemetry, because the people directing
the code mostly aren't reading it line by line.

- **Development dashboard (bench/testing):** Panels — field view showing live robot pose, the
  Pedro path, pose history, and the Limelight target; live graphs for tuning; live configurables;
  and **capture/replay** for debugging a bad autonomous from the actual run data.
- **In-match (Driver Hub):** during real matches you are not connected to Panels. Design a clean,
  glanceable Driver Hub telemetry layout: intake state, game-piece count, current mode,
  battery health. Panels can mirror telemetry to the Driver Hub so it's built once and consistent.
- **Test OpModes:** every subsystem gets a small standalone test OpMode so behavior can be checked
  in isolation via fast reload.
- **Practice robot + scrimmages.** A dedicated practice robot lets the software and mechanical teams
  work in parallel; it must be held to the same reliability bar as the competition robot. Robustness
  is verified in scrimmage matches against other teams, not just on the bench.

---

## 9. Coding conventions for AI-generated code

- **Plain, explicit, heavily commented.** No clever shorthand. Optimize for a middle-schooler
  being able to read it and explain it (see the Explain-It Gate).
- **Small, single-responsibility files** with explicit interfaces. Contained blast radius per
  change.
- **Extract pure logic into testable functions.** Keep math and decisions (mechanism math,
  geometry, targeting logic) as pure functions separate from hardware, so they can be unit-tested
  off the robot. The AI writes the function *and* its tests; logic is validated without the robot.
- **Tunables are configurables; logic lives in teamcode.** (see §6)
- **Every subsystem publishes health telemetry** and has a test OpMode.
- **Alliance and starting pose are chosen in exactly one place** and passed down — no auto
  hardcodes its own — so the robot never runs from the wrong pose.
- **Document as you go.** Each subsystem and command has a short comment saying what it does,
  what it owns, and how to tell if it's working.
- **Consistent naming** matching the hardware map in §10.

---

## 10. Hardware map

Persistent control/sensor suite (carries across seasons):

| Role | Device | Bus | Notes |
|------|--------|-----|-------|
| Controller | REV Control Hub | — | MANUAL bulk caching |
| Actuator hub | REV Servo Hub | — | needs RC + DS apps on 10.0+ to configure as a Servo Hub (else shows as generic Expansion Hub); firmware/address via REV Hardware Client |
| Vision | Limelight 3A ×2 | USB 3.0 | detection on-device; used for relative **aiming, not pose** (§3) |
| Odometry | goBILDA Pinpoint (V2) | I2C | **single source of pose (no fusion)**; read once/loop; mind wire routing/ferrite |
| Distance | Brushland Labs Color Rangefinder | Analog | analog distance mode; cheap to read (rides bulk read) |
| Drivetrain | Mecanum (goBILDA Yellow Jacket motors) | — | per-wheel health telemetry required |

**Game-specific mechanisms** (fill in at kickoff — e.g. intake, delivery, lift): add each with
its config name, port, and the intent-level methods its subsystem exposes. Keep any real mechanism
math as testable pure logic (§9), with its tunables exposed as Tier-1 configurables.

> Keep the config names in this table identical to the Robot Controller configuration on the hub —
> **and identical across the practice robot and the competition robot** — so code runs unmodified on both.

**Two cameras.** We run two Limelights: one stays on the competition robot, the second collects
training data and iterates the vision model, so model work never competes for competition-robot time.
The neural-net model lives on the camera and does **not** hot-reload via Sloth (§6) — it is its own
artifact with its own versioning. Deploy a validated model to the competition camera deliberately,
and keep the previous model to roll back to.

---

## 11. The workflow loop

1. **Coach describes** the desired behavior in plain language.
2. **AI generates/refactors** against this document.
3. **Students review** and integrate.
4. **Test via fast reload** (Tier 2) and live-tune (Tier 1) on the bench with Panels open.
5. **Observe** behavior + telemetry; iterate.
6. **Explain-It Gate** before it touches the competition robot.
7. **Commit to git** — small, reviewable, revertable changes.

---

## 12. Version control

Use git. Small commits with plain-language messages describing the *behavior* changed. Because the
AI writes most of the code, git is the safety net: every change is reviewable and any change is
revertable. Never field a competition build that isn't committed. The running code's commit hash is
written into every snapshot (§7), so a saved snapshot always points back to exact source.

### Change log & rollback
git is the undo engine — every commit is a restore point. To make undo easy and legible (especially
AI-assisted undo, where you say "put back the version before the intake change" and the assistant
must know exactly what to revert):

- **Commit small and often**, with plain-language messages describing the behavior changed.
  Fine-grained commits mean `git revert` removes just one change, not half a day's work.
- **Tag known-good builds** at each event (`quals-1-working`, `comp-ready`). Returning to a
  known-good state is then one command — the fastest rollback when something breaks the morning of a
  competition.
- **Keep a plain-language `CHANGELOG.md`** at the repo root: one dated, plain-English line per
  meaningful change — what changed and why. It's the human-readable bridge between "what did we
  change last week" and "which commit to revert," and it's what makes AI-assisted undo reliable.
- **Experiment on branches.** Do risky or exploratory generation on a branch and keep `main` always
  competition-ready; if it doesn't pan out, delete the branch — nothing on main to undo.

**AI rule — NON-NEGOTIABLE:** every change the AI makes adds its own `CHANGELOG.md` entry and a
commit message describing what changed and why. The log stays trustworthy only if keeping it current
isn't manual work left to the humans.

---

## 13. Phase 0 (do this first)

Before any game-specific code: stand up the repo, adopt the stack, get a bare drivetrain driving
with live telemetry, and **prove the full generation-first loop works** — including confirming
Sloth hot-reload works with the chosen framework, and that a snapshot writes and reloads correctly
— on something trivial, before the season is on the line.

---

## 14. Diagnostics & tooling

How we find and fix problems. Some tools are **standing** — always in the kit — and the rest are
**available**, reached for when a specific need comes up. Logging is I/O, so all of it obeys §4:
never let diagnostics tax the match loop.

### Standing tools
- **Logcat** — Android's live log stream, viewed in Android Studio over ADB. Emit with `RobotLog`
  (or `Log.d` / `Log.e`); you get full stack traces on a crash plus any custom messages. Use it for
  "why did it crash" and "did this branch actually run." Weakness: it's volatile — gone when you
  disconnect, so it can't diagnose a match you couldn't stay tethered to. Log meaningfully (all
  errors, key state transitions); never spam it from the hot loop.
- **Persistent RC match logs** — the Robot Controller writes durable log files to the hub for every
  OpMode run (errors, system events). They survive disconnection — pull them via ADB or the hub's
  Manage page to review a qualifier match hours later. This is logcat's blind spot, covered.
- **Datalogger** — appends chosen signals to a CSV each loop, giving a time-series you can plot to
  find the exact instant a value spiked. Distinct from snapshots (§7, once at start/stop) and live
  telemetry (§8). It is the *one* deliberate exception to "no file I/O in the loop" (§7): the writer
  is BUFFERED, so a per-loop write is a cheap in-memory append and the real disk flush happens on
  stop. Rules: open once (start), never flush per loop, close on stop, keep columns few, and treat
  it as a bench/diagnostic tool you switch on to investigate — not always-on match logging. The
  skeleton ships a `Datalogger` utility (`util/Datalogger.kt`).
- **Android Profiler** — profiles CPU, memory, and allocations on the running app. This is how we
  actually defend the prime directive (§0): when Loop Hz drops and the cause isn't obvious, the
  profiler finds the method eating the budget or the allocations causing the GC hitches.

### Also available (reach for as needed)
- **Debugger** (Android Studio) — breakpoints and variable inspection; great for init and logic
  bugs, awkward for real-time control because pausing breaks timing.
- **Limelight web interface** — live camera feed, detection overlays, and vision-pipeline tuning.
- **REV Hardware Client** + the hub's **Manage** page — firmware/OS updates, Wi-Fi, configuration,
  and below-the-code diagnostics for when a hub itself misbehaves.
- **ADB** — the connection layer under most of the above; works over Wi-Fi for a moving robot.
- **Pedro Pathing Visualizer** — design and preview autonomous paths on a laptop before deploying.
- **CI (GitHub Actions)** — auto-run the off-robot unit tests (§9) on every push, so a generated
  change is verified before it reaches the robot.
