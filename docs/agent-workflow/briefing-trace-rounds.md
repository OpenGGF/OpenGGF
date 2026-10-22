# Briefing a trace round

Distilled from ~25 consecutive S3K/S2 trace rounds on 2026-08-14/15, in which **the
orchestrator's candidate causes were wrong eight rounds running** and every round that ignored
them and instrumented the decision site was right. This is not a confession; it is a measured
result about what makes these rounds succeed. Brief this way.

Companion to [trace-replay-bug-fixing](../../.agents/skills/trace-replay-bug-fixing) — that
skill is the procedure, this is how to hand the work over.

## Index

One hundred and twenty-six rules and several worked sections, accumulated across many rounds. The narrative
below is the argument for each; this table is for finding one mid-round. **The measurement
hazards are the ones to re-read before reporting a number** — every single one produces output
that looks like a real result.

### Briefing and framing

| Rule | In one line |
|---|---|
| The one that matters most | Supply the measured symptom, never your hypothesis |
| 1 | Supply the measured symptom, never your hypothesis |
| 2 | The first-reported field is alphabetical, not causal |
| 9 | A latent correction can be worth more as evidence than as a fix |
| 17 | A compensation stack is load-bearing — remove it in one move |
| 3 | Name the fitted-constant trap explicitly, in the brief |
| 4 | Make "found-not-fixed" a full success, in writing |
| 6 | Stamp every number with the commit it was measured at |
| 7 | Tell the round you are probably wrong |
| 31 | "No field reported" states which check fired, not what is wrong |

### Evidence and inference

| Rule | In one line |
|---|---|
| 5 | Verification instructions that have actually caught things |
| 24 | Diff failure *messages*, never class names |
| 28 | Check whether an absence is in the data or only in your view |
| 29 | Check a suspicious delta against both endpoints |
| 33 | BizHawk reports the PC *after* the storing instruction |
| 32 | A row's emulator frame is `bk2_frame_offset + row + 1` |
| 40 | A comment reasoning about which engine hook runs first — in either direction — is the tell for a fitted constant; they come in families |
| 10 | The symptom's axis is not evidence about the cause's axis |
| 11 | Build the disconfirming case by hand before trusting a ratio |
| 12 | Some divergences are not frame-derivable; hunting a predicate is itself the error |
| 13 | "A did not match B" has a third answer — the index is broken |
| 14 | Closure ownership is the row after, never the row before |
| 18 | An identical error fingerprint means the same lever, whatever the site |
| 19 | Port the READ, not the event that last wrote the field |
| 23 | The headline field is not the earliest divergence — read the histogram |
| 47 | Measure the quantity, do not reconstruct it |
| 48 | Object execution order is observable behaviour |
| 53 | A sign-flipping single pixel is phase — a creation frame early or late — not a wrong constant |
| 56 | `loc_XXXX` is a ROM address; citing it as a line number lands on plausible unrelated code |
| 57 | A cited frame number can itself be an artefact of the defect it justifies |
| 58 | The count of cancelling errors is usually understated; two is rarely the whole set |
| 59 | When the engine looks right and the recording looks broken, the engine is probably missing a ROM bug |
| 60 | Right family, wrong mechanics is still wrong — test the mechanism's own prediction |
| 61 | An absence in a routine is not a positive fact about it; read one level deeper |
| 63 | Resolve a divergence's values to their disassembly names before hypothesising |
| 64 | A profile no matrix runs is covered by nothing; know which ones yours omits |
| 65 | A shared exception type is not a shared defect; instrument the throw's contents |
| 66 | A substring match is not a value match; `0 errors` matches inside `5840 errors` |
| 67 | A ratchet only checked at raise time will always be raised; ask for its whole history |
| 68 | An in-place routine change is not a spawn; look for a routine byte changing on an occupied slot |
| 69 | Check that your two numbers are the same kind of measurement before differencing them |
| 70 | Review the artifact, not the account of it; a rejected candidate never rides a merged branch |
| 71 | Validate a classifier against a hand-verified control, or discard its output |
| 72 | Prove a new assertion fires; never place one where a `finally` can swallow the real failure |

### Measurement hazards — all produce plausible output

An end-of-frame oracle can hide the pre-object writer it intends to test
(2026-10-22, S1 water clock). Positioned LZ3 checks all ended three pixels lower
because later physics/radius work replaced the observed Y. The existing
RecordingFrameDriver physics wrapper samples after the real water callback and
then still executes the full original frame. That boundary reproduced six gate
failures and one control before the correction, including lag and rollover;
restore/forward and another BK2 then passed. Read the actual clock publisher:
ObjectManager increments after this callback, although native VBlank_Exit has
already incremented before LZWaterFeatures. See the
[water-clock evidence](../architecture/audits/2026-10-22-s1-chain-gap-closure.md).

An injected offline audio backend can be replaced at a later launch boundary
(2026-10-21, full Time Attack capture). EGL and `java.awt.headless` isolate no
audio devices. Ordinary Engine launch preparation previously replaced the supplied
headless backend with LWJGL. Verify exact backend and no-device sink ownership
through startup, level launch and return, and check the production PCM packets;
startup identity alone does not establish isolation for the recording.
