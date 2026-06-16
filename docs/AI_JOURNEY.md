# The AI Journey

*A working journal of how AI went from "useless for this" to a measurable accuracy
multiplier on OpenGGF — and why the line "you can't prompt your way to ROM accuracy"
is no longer quite as true as it used to be.*

This is the story behind one sentence in our [README](../README.md):

> You can't prompt your way to ROM accuracy (yet!).

That parenthetical "(yet!)" is doing a lot of work. This page explains how it got there.

*Every date and claim below is anchored to the commit history; where the record is thin
(early throwaway experiments that never got committed), it's flagged as recollection, not fact.*

---

## 2013–2025 — It started by hand, and stayed that way for a long time

The first commit landed on **19 May 2013**. There was no AI involved, because there was
no AI to involve. The early history reads like exactly what it was: a person teaching
themselves how a Mega Drive moves a hedgehog around a screen, one stubborn detail at a time.

> *"Added basic acceleration/deceleration algorithm. Sonic now has properly configured run
> rates so our 'Sonic' will now run as if he's moving across a flat surface."*
>
> *"Fixed silly derp that caused camera to move 6 pixels at a time! (oops)"*
>
> *"Angle is now adjustable by pressing up or down"*

The hand-built core — the rendering pipeline, the physics rewrite, the subpixel movement
model, the sensor-based collision system — was designed and written by a person, over years,
against the community disassemblies.

That foundation matters to the rest of this story: every later experiment with AI happened
*on top of* a hand-built engine with a hard, objective definition of "correct" — the original
ROM. There was always something to be wrong against.

## June 2025 — The first experiments, meant to be thrown away

The first time AI touched this project, nobody intended to keep a line of what it wrote.

The plan was deliberately cynical, and at the time the cynicism was correct: we didn't trust the
models to write good code. So the idea was never "let AI build it." It was to point AI at the
enormous body of *reference* material that already exists — Sonic Retro write-ups, historical
ROM-hacking notes, the disassemblies — have it digest all of that and emit some terrible,
barely-readable code that nevertheless *worked*, then reverse-engineer that throwaway code by hand
into something clean enough to keep. AI as a research scratchpad, not an author.

One of the very first targets was parsing Sonic 2 level data straight out of the ROM — a good
first problem (compressed layouts, well-documented by the community) and a brutal one for a
2025-era model. Using the online version of Codex, alongside Copilot / GPT-4, it *didn't get very
far.* Pulling compressed data out of a Mega Drive ROM byte-exactly was exactly the kind of thing
the models were worst at: plausible-looking parsers that produced almost-right garbage.

The committed fingerprints of this era are modest and exploratory, which is fitting. The online
Codex branches from **June 2025** are things like `codex/explain-codebase-structure-and-learning-path`
(PR #32, 9 June 2025) and `codex/add-unit-tests` (PR #33, 15 June 2025), landing next to the first
`AGENTS.md` guidelines. AI was being asked to *explain* and *scaffold*, not to ship. Most of the
actual experiments never made it into a commit at all — they were always meant to be thrown away.

## November 2025 — Agents start producing work worth keeping

By late November 2025 the relationship changed: for the first time, AI output was being kept and
built upon rather than reverse-engineered and discarded. Two threads ran at once.

**Online Codex stayed the steadier hand**, still working the way it always had — grounded in
scavenged reference material rather than writing from nothing. The fingerprints are the
sound-engine branch (`codex/github-mention-add-sound-engine`, merged via PR #68 on 27 Nov 2025)
and the reference docs that landed alongside it: a *"music docs and Nemesis S2 guide"* and a
`Saxman compression - Sega Retro.htm` page, both committed **27 November 2025**.

**The Jules and Gemini experiments ran in parallel — and they were a headache.** The first
*bot-authored* commits in the repo land on **24 November 2025**, under `google-labs-jules[bot]`
(Google's Gemini-backed Jules agent): updating `AGENTS.md`, fixing sprite-flipping logic,
correcting physics angle calculation for flipped chunks. Jules could go impressively deep — but
steering it was the whole job. Getting usable, close-to-accurate code out of the Jules/Gemini
path took *many* prompts, a lot of re-rolling, and constant correction against the disassembly.
Good rate limits and real capability; controllability not yet there — and in honest side-by-side
use it never clearly beat Codex.

Where it went deepest was audio. There are **no audio-engine commits anywhere in the repo
before this window** — the entire SMPS/YM2612 stack is built here. The first SMPS implementation
commit, on **27 November 2025**, is *"Implement FM Instrument Loading for SMPS Audio Engine"* —
by Jules. Over the following weeks Jules authored the bulk of the audio engine (~88 distinct
commits, more if you count rebase duplicates): the YM2612
implementation (CSM, SSG-EG, attack-logic), DAC noise and volume correctness, SMPS sequencer
accuracy against SMPSPlay/libvgm, FM voice loading, PSG envelopes — with Codex contributing on
the sound-engine branches and the human side co-driving hard (*"Flesh out YM2612 implementation,
add tests"*, 28 Nov). The honest record is that the audio engine is an **AI-built,
heavily-steered subsystem**, not a hand-written one. (This is the claim the README got wrong,
and this page exists partly to set it straight.)

### The Saxman thread — and the lesson that named the whole project

Compression was the other through-line, and it reaches back to those first throwaway experiments.
In memory it's tangled up with the level-data fight — the early attempts to pull *any* compressed
data out of the Sonic 2 ROM. (Strictly, **Saxman** is the format Sonic 2 uses for its
sound-driver data, not its level layouts — but the experience was the same fight either way:
wrestling a Mega Drive compression scheme into a *byte-exact* decompressor, with a brutally clear
pass/fail test — do the decompressed bytes match the ROM?)

The early attempts — as best we remember, the Copilot / GPT-4 / Gemini 2.5 Pro generation of
tools, mostly in experiments that predate the committed history — did not go well. The models
could produce code that *looked* like a working decompressor. It compiled. It even ran. It just
didn't produce the right bytes: size parsing was subtly wrong, sliding-window edge cases were
mishandled, and "looks plausible" turned out to be a very long way from "bit-exact." A genuine
failed port — confident-sounding output, no working decompressor.

That failure was the useful part. It taught the lesson that shaped everything after:
**plausibility is not accuracy, and only the ROM gets a vote.** A model can pattern-match its
way to code that resembles the answer; it cannot pattern-match its way to a byte-exact match
with hardware it has never observed. You need an oracle.

(For the record: Saxman *was* eventually made correct — the size-parsing fix landed on
**4 December 2025** — but only once the approach changed from "prompt until it looks right" to
"prompt, then verify against the ROM, then fix what the verification caught.")

## February–March 2026 — AI in earnest: specs and plans

The point where AI use stopped being ad-hoc and became a *method* is documented, literally, in
a folder. Starting **14–16 March 2026**, design specs and implementation plans begin
accumulating under [`docs/superpowers/`](superpowers/) — the artifacts of a structured
spec → plan → implement → review workflow. As of this writing there are **94 design specs and
146 implementation plans** in that tree, dated and committed, covering everything from the AIZ1
miniboss and signpost, to multi-sidekick chains, to cross-game animation donation, to the
common-utility refactors.

This is the real inflection point. Before this, AI was a fast pair of hands on bounded tasks.
After it, every non-trivial change started life as a written spec and a reviewable plan,
executed under direct architectural oversight, with the disassembly as the reference and a human
gate on every commit. The `Co-Authored-By` trailers from this era onward are overwhelmingly
**Claude** (Opus 4.5 → 4.8, Sonnet 4.6, Fable 5), with Codex and Gemini also in the mix — but
the agent matters less than the discipline around it. AI proposed; the disassembly and the
reviewer disposed.

This was faster than hand-work, sometimes dramatically so. But note what it still *wasn't*:
accuracy *from prompting*. It was accuracy from a human holding the disassembly in one hand and
the model's output in the other. The oracle was still a person reading assembly.

## 27 March 2026 — The turning point: trace replay

The real shift came with a small set of unglamorous commits on **27 March 2026**:

> *Add BizHawk trace replay foundational data types*
> *Add AbstractTraceReplayTest base class and concrete GHZ1 test*
> *Add TraceBinder comparison engine with tolerance tests*

The idea: run the **real game** in an emulator, record a frame-by-frame trace of its actual
internal state — positions, speeds, angles, status bits, object routines — and then replay the
*same inputs* through our engine and compare, field by field, frame by frame. The first frame
where our engine disagrees with the ROM is, by definition, the first thing we got wrong.

This is the piece that had been missing in 2025. It turns "is this accurate?" from a judgement
call into a number: **the first divergent frame.** And a number is something you can *optimise
against* — including with AI.

## Today — The frontier loop

What we run now is a tight, repeatable loop, and it's where AI genuinely earns the "(yet!)":

1. Run the full `*TraceReplay` sweep. Find the trace that diverges earliest — the **frontier**.
2. Pull the exact first-divergence frame and field (e.g. *leader `g_speed` sign flip at frame
   19089, AIZ2 end-boss approach*).
3. Form a hypothesis, **grounded in a real ROM instruction** — not a heuristic that happens to
   correlate. (House rule: a parity fix has to model something the original code actually does,
   with a disassembly citation. No zone/frame/route carve-outs.)
4. Fix it. Re-run. The frontier moves forward — or it doesn't, and the hypothesis was wrong.
5. Log it in [`TRACE_FRONTIER_LOG.md`](TRACE_FRONTIER_LOG.md) and pick the next target.

Inside this loop, AI is no longer guessing at accuracy. It's reading a precise failure
signal, cross-referencing the disassembly, proposing a ROM-backed change, and getting graded
by the emulator on the very next run. The model doesn't decide whether it's right. The trace
does. That changes everything.

The results are concrete and measured in frames. To pick one slice: the Sonic 3 & Knuckles
Angel Island Zone trace has been driven from an early-frame divergence all the way **past the
AIZ2 battleship bombing run and into the end-boss arena approach (frame ~19089)** — thousands
of frames of byte-comparable, ROM-faithful play, each one earned by closing a specific
divergence the trace pointed at. Dozens of these traces run across S1, S2, and S3K, and the
frontier log tracks every move.

## A note on the tools themselves

The agent doing the typing changed a lot over this story, and the changes were *measurable* —
which is the whole point of building against an oracle. Roughly in order:

- **Online Codex** — the first true driver (late 2025). Grounded in scavenged documentation, it
  was the first tool to produce work worth keeping.
- **Jules** — adopted alongside Codex, largely for its generous rate limits. It was useful, but
  in honest side-by-side use it **never really outperformed Codex**; it just let you do more of
  the same per day.
- **Claude Code** — the first *measurable jump* in performance. The `Co-Authored-By` trailers
  start appearing around **22 January 2026** (Opus 4.5), and Claude quickly became the workhorse
  through Opus 4.6 and 4.7 — the dominant signature in the entire commit history.
- **Today: Opus 4.8 and GPT-5.5, side by side.** GPT-5.5 was state-of-the-art for a stretch and
  did some genuinely strong work (it commits quietly, under the human's name on `codex/*`
  branches, so it's underrepresented in the co-author trailers). **Opus 4.8 then narrowed the
  gap** enough to bring the work back to Claude Code, and the two now run in parallel depending
  on the task.
- **Fable 5 — the one that got away.** We had it for a brief two-day window (25 commits across 10–12 June
  2026) and it showed real promise before it went. Here's hoping Anthropic brings it back.

The throughline: no single tool "won." Each raised the floor, the oracle kept score, and the
discipline around them stayed constant.

## The honest accounting

So — can you prompt your way to ROM accuracy?

**Not directly.** A prompt still can't *originate* hardware-exact behaviour. The things that
made any of this possible — the engine, the architecture, the trace harness, the
"model-the-ROM-instruction-or-don't-ship-it" discipline, and the review on every commit — are
human. And where AI *built* a whole subsystem (the audio engine), it did so under constant
human steering against a reference, not from a clever prompt.

**But the gap has narrowed in a way that genuinely surprised us.** With a real oracle in the
loop — a frame-exact trace of the original game — AI stops being a plausible-code generator and
becomes something closer to a tireless accuracy-debugging partner: it reads the first divergent
frame, finds the relevant routine in the disassembly, proposes a grounded fix, and submits to
being graded by the ROM. That's not "prompting your way to accuracy." It's *prompting your way
through the search space toward accuracy, with the ROM as judge.*

That's the difference between the failed Saxman port of late 2025 and the trace frontier loop of
2026. Roughly the same class of models. Completely different outcome. The thing that changed
wasn't the prompt — it was that we finally gave it something it couldn't fool: the real game,
one frame at a time.

You still can't prompt your way to ROM accuracy.

But the "(yet!)" is getting louder every frontier we close.

---

*This journal is updated as the story continues. For the live state of the accuracy work, see
[`TRACE_FRONTIER_LOG.md`](TRACE_FRONTIER_LOG.md). For the project's stance on AI authorship, see
the "Did you use AI to write this?" section of the [README](../README.md).*

*Authorship: written by Claude Opus 4.8, with Farrell dictating the history — and fact-checked
against the commit log every step of the way. Fitting, for a page about exactly this. Or, in the
author's own words: "You write it up so it's nice, I really can't be arsed writing a novel on
this."*
