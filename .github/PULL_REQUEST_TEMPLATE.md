## What the robot can now do

<!-- One or two plain sentences. Behaviour, not file names. -->

## How it was checked

<!-- On which robot, in which OpMode. "Not yet run on a robot" is a fine answer — say so. -->

## Charter checks (CLAUDE.md)

- [ ] Every new command has its **own timeout** — not left to the caller (§5)
- [ ] Numbers I might change at a competition are **tunables**, not literals (§6)
- [ ] Nothing new is **allocated or built as a string inside the loop** (§4 rule 8)
- [ ] No extra **hardware reads per loop**; sensors are read once and shared (§4 rules 2 and 5)
- [ ] Real maths is a **pure function with a unit test** (§9)
- [ ] `CHANGELOG.md` has a plain-language line for this change (§12)
- [ ] A **tuning file** change is the file the robot wrote — no numbers transcribed into source (§6)

## Loop time

<!-- Did Loop Hz change? If this adds work to the loop, say what and why it is worth it (§0). -->

## Explain-It Gate

- [ ] I can say out loud what every line does, and defend why it is built that way (§1)

<!-- Anything you could not explain, ask about it in the PR. Asking is the workflow. -->
