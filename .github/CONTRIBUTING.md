# Contributing — Snack Time Robotics, FTC 34672

This replaces the FTC SDK's own contributing notes, which were about sending changes to the official
SDK. This file is about **our** repo.

**Read `CLAUDE.md` first.** It is the operating charter. Everything below is how you put work into the
repo without breaking it.

## Who works here

- **Coach (Aaron)** describes the behaviour the robot should have, in plain language.
- **Student programmers** direct the AI the same way, and are the human reviewers at the code level.
  They read, integrate and test what the AI produces.
- **The AI** generates code against the charter, writes its own telemetry and documentation, and
  **explains anything a student asks about**. Asking is the workflow, not an admission.

Nothing goes on the competition robot unless a student can say out loud what it does *and defend why
it is built that way* (the Explain-It Gate, `CLAUDE.md` §1).

## Getting work in

`master` is competition-ready at all times, and **it refuses a direct push**. The GitHub ruleset
"Review Before Merging" requires a pull request with one approving review. Org admins bypass it; that
is the emergency escape hatch, not the normal path.

```bash
# before you start
git status
git switch master
git pull --ff-only
git switch -c yourname/what-you-are-doing

# when it works
git branch --show-current
git status
git add <the files you changed>       # never `git add .`
git commit -m "What the robot can now do"
git show --stat

# check master did not move under you
git fetch origin
git log --oneline HEAD..origin/master  # nothing printed = you match master
                                       # lines printed = git merge origin/master

# send it in
git push -u origin yourname/what-you-are-doing
```

The push prints a link. Open it and click **Create pull request**, or run `gh pr create --fill`. Fill
in the template — it is the charter checklist, and it is faster to tick than to argue about later.

**Who approves:** Aaron (`atkinsonaaron`) or `cmyers734`. Students do not approve each other's work —
every change gets read by an adult before it can reach the competition robot. GitHub never lets you
approve your own request either, so there is no way around this, and that is the point.

If your request is sitting there, say so out loud at practice. A pull request nobody has looked at is
not a process problem, it is a five-second reminder.

## Four commands nobody types here

`git reset --hard` · `git push --force` · `git clean -fd` · `git checkout .`

Each throws work away with no undo, and none of them is ever the answer to a confusing message. A
confusing git message is a question, not a command.

## Tuned numbers are code

Values you turn in Panels live in the robot's memory, then in a file on the hub, and only count once
they are **committed**. Pull the hub's file with `./save-tuning.sh` and commit the file the robot
wrote — never transcribe numbers into source. Full procedure in `tuning/README.md`.

## Every change carries its own paperwork

- A `CHANGELOG.md` line, in plain English: what changed and why (`CLAUDE.md` §12).
- A commit message describing the behaviour, not the files.
- Off-robot unit tests for any real maths (`CLAUDE.md` §9). CI runs them on every pull request.

## Adding a library is a stop-and-ask

Adding, upgrading or swapping any Gradle dependency is the highest-consequence change in this repo:
it forces a full install and can silently break Sloth hot reload or the dashboard. Never do it in a
pull request without asking first (`CLAUDE.md` §6).
