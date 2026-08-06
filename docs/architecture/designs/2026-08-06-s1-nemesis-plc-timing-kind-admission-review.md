# Admission review: a `NEMESIS_PLC_QUEUE` hardware-timing kind for the S1 PLC pipeline

Date: 2026-08-06
Status: **Review, submitted for approval.** This is the "separate ROM evidence and
design review" that
[`2026-07-27-cross-game-hardware-timing-trace-contract.md`](2026-07-27-cross-game-hardware-timing-trace-contract.md)
requires before a PLC kind may enter the timing registry. No production code
accompanies it. The implementation it authorises is
[`../plans/trace/2026-08-06-s1-nemesis-plc-timing-stream-plan.md`](../plans/trace/2026-08-06-s1-nemesis-plc-timing-stream-plan.md).

Evidence base:
[`../research/trace/2026-08-06-s1-plc-arming-row-observability.md`](../research/trace/2026-08-06-s1-plc-arming-row-observability.md)
(383,502 probed frames),
[`../research/trace/2026-07-28-s1-s2-plc-readiness-evidence.md`](../research/trace/2026-07-28-s1-s2-plc-readiness-evidence.md)
(the reviewed REV01 address set),
[`2026-07-28-s1-s2-plc-service-queues.md`](2026-07-28-s1-s2-plc-service-queues.md)
(the native-model design whose disposition this review reopens), and
[`2026-08-06-level-load-span-timing-port-scope.md`](2026-08-06-level-load-span-timing-port-scope.md)
(the immediately preceding *rejection* of a different port entry, whose reasoning
this review must survive).

---

## 0. Reader's summary

| Question | Answer |
|---|---|
| Does S1 have a polled, gameplay-visible PLC readiness gate? | **Yes** — but on `v_plc_buffer` (`$FFFFF680`), not `v_plc_patternsleft`. Nine gameplay-visible poll sites, four of them unbounded frame-locked spin loops. §2. |
| Is the timing already reproduced by lag, phase, or deterministic queue service? | **No.** The discriminator is sub-frame 68000 cycle position; the two outcomes leave an identical recorded row shape and identical lag classification. §3. |
| What exactly does the stream buy? | **One row, in one fixture.** Reverting `99746ffa9` is correct on 1,169 of 1,170 measured armings. The stream exists for `ghz2_2` 107. This is stated up front because it is the strongest argument against the change. §4. |
| Does it drag the S3K fixture corpus with it? | **No** — and this is the decisive structural difference from the rejected `LEVEL_LOAD` proposal. §7.3. |
| Is the `compressedLength` waiver granted? | **Yes, with an amendment.** Waived; the mode bit must move into `compressionVariant` rather than being silently dropped. §6. |
| Does `TestHardwareTimingAuthorityGuard` need editing? | **No.** It holds no kind literals. §8.2. |
| Biggest risk? | Fail-closed starvation of the level-load / title-card PLC drain, which has no representable edge. §9. Resolved by confining the recorded authority to one named ROM call site. |

---

## 1. A correction to the brief this review was given

The task that commissioned this review asked it to *"identify the actual ROM poll
sites on `v_plc_patternsleft` (the title-card / level-init wait loops,
`SignpostArtLoad`)"*. Two of those three premises are false, and the review would
be trivially rebutted if it repeated them.

Measured over `docs/s1disasm/` (`_Variables.asm:163-177` for the RAM block,
`sonic.asm` and `_incObj/` for the code):

1. **`v_plc_patternsleft` (`$FFFFF6F8`) has exactly six touches in the entire
   program, all six inside the PLC service block** (`sonic.asm:1382`, `1397`,
   `1415`, `1432`, `1444`, `1476`). Every one is inside `RunPLC` (`1379`),
   `ProcessPLC_9Tiles` (`1431`), `ProcessPLC_3Tiles` (`1443`) or `ProcessPLC`
   (`1455`). **There is no external reader.** `sonic.asm:1382` is a re-entrancy
   guard whose `bne.s` falls to `rts`, not a spin. `sonic.asm:1476`'s loop is
   bounded by `v_plc_framepatternsleft` to 9 or 3 iterations. There is no
   `patternsleft` wait loop anywhere in Sonic 1.
2. **`SignpostArtLoad` (`sonic.asm:3186-3208`) reads no PLC state at all.** It
   reads `v_debuguse`, `v_act`, `v_screenposx`, `v_limitright2`, `f_timecount`
   and `v_limitleft2`, and then `bra.w NewPLC`. It is a *producer*, and because
   `NewPLC` calls `ClearPLC` (`sonic.asm:1342`) it is a camera-position-triggered
   mid-gameplay queue **reset**.
3. Raw-hex search for `F6F8` and `F680` returns zero address literals; every
   access is symbolic.

The real gate is `v_plc_buffer` (`$FFFFF680`) — the head slot's 4-byte art
pointer, which doubles as the program's public "queue empty" flag. §2 inventories
it. The corrected claim is *stronger* than the briefed one, not weaker.

---

## 2. The eligibility gate

The contract's §"3. External work completion" admits a new kind only when all
five criteria hold. Taken in turn, with citations.

### 2.1 Criterion 1 — production code has submitted real ROM-backed work

Today: **no.** `HardwareTimingService.submit` has exactly two production callers,
both S3K (`S3kKosModuleQueue.java:105`, `S3kKosDecompressionQueue.java:110`).
`Sonic1GameModule` does not override `GameModule.createRuntimeArtCoordinator`
(`GameModule.java:46-50`) and therefore inherits `RuntimeArtCoordinator.NONE`,
even though `GameplayModeContext.java:193-201` already constructs a
`HardwareTimingService` for every S1 session.

Under this design: **yes.** The submitted work is one Nemesis PLC entry — a ROM
address and a VRAM destination read out of the engine's own `NemesisPlcServiceQueue`,
which was populated from `Sonic1Constants.ART_LOAD_CUES_ADDR` through
`PlcParser.parse`. The ROM identifies a PLC entry by exactly those two values;
the 6-byte `v_plc_buffer` slot is `plc_slot_size: equ 4+2` (`_Variables.asm:163`).
Nothing about the job comes from the trace. §7.1 specifies the submission point.

This criterion is the one the rejected `LEVEL_LOAD` proposal failed outright
(`2026-08-06-level-load-span-timing-port-scope.md` §1.4: *"nothing is
submitted"*). Here it is satisfied by real ROM-derived queue content that the
engine already parses today.

### 2.2 Criterion 2 — the ROM exposes a readiness value polled by ordinary main-loop code

**Satisfied, on `v_plc_buffer`.** Complete inventory of `tst.l (v_plc_buffer).w`
readiness polls, nine gameplay-visible plus one service-internal:

| # | Site | Routine | Shape |
|---|---|---|---|
| 1 | `sonic.asm:1380` | `RunPLC` (1379) | service-internal; is there anything to arm |
| 2 | `sonic.asm:2203` | `LevelSelect` (2198) | **unbounded spin**; `bne.s LevelSelect` |
| 3 | `sonic.asm:2841` | `Level_TtlCardLoop` (2814) | **unbounded spin**; `bne.s Level_TtlCardLoop` |
| 4 | `sonic.asm:3412` | `SS_NormalExit` (3403) | **unbounded spin** |
| 5 | `sonic.asm:3888` | `Cred_WaitLoop` (3880) | **unbounded spin** |
| 6 | `_incObj/3A Got Through Card.asm:29` | `Got_ChkPLC` (Obj 3A routine 0) | frame-sliced gate; `rts` until empty |
| 7 | `_incObj/7E, 7F Special Stage Results…asm:30` | `SSR_ChkPLC` (Obj 7E routine 0) | frame-sliced gate |
| 8 | `_incObj/39 Game Over.asm:18` | `Over_ChkPLC` (Obj 39 routine 0) | frame-sliced gate |
| 9 | `_incObj/85,84,86 Boss - FZ Main…asm:157` | `BossFinal_Eggman_Wait` | frame-sliced gate |
| 10 | `_incObj/4A Unused - Special Stage Entry.asm:20` | `Van_ChkPLC` | frame-sliced gate; unreferenced object |

Two are worth quoting because they are the sharpest.

`LevelSelect` swallows player input while the queue is non-empty:

```
2202			bsr.w	RunPLC					; run any potential PLC
2203			tst.l	(v_plc_buffer).w			; are any patterns in the PLC still left to be loaded?
2204			bne.s	LevelSelect				; if yes, block quitting level select until finished
2205			andi.b	#btnABC+btnStart,(v_jpadpress1).w	; is A, B, C, or Start pressed?
```

The Final Zone boss will not start until the queue drains, and seeds its RNG once
per waiting frame — so the fight's entire random stream is a function of how many
frames decompression took:

```
156	BossFinal_Eggman_Wait:
157			tst.l	(v_plc_buffer).w			; is art still being loaded?
158			bne.s	.exit					; yes, come back later
...
164	.exit:
165			addq.l	#1,(v_random).w				; seed the RNG for the fight
166			rts
```

Sites 6-9 are object-dispatcher gates rather than `bne` loops, but they are
polls in the contract's sense: the object stays in routine 0 and re-reads the
value on every object scan until it clears. The contract's own S1/S2 audit
section already anticipates this class — *"ordinary loops can continue while a
PLC remains pending"*.

### 2.3 Criterion 3 — the main loop can continue while that value remains pending

**Satisfied, and measured.** Over 383,502 probed frames the `3R`/`9R` order
string — level V-blank service ran, `RunPLC` was entered and declined because the
head is still busy — occurs 168,309 + 7,979 times on the emerald movie and
174,002 + 3,055 on the complete run. Every one of those is a fully executed
gameplay iteration with pending PLC work.

### 2.4 Criterion 4 — completion timing depends on hardware work not represented by `lag` or the execution phase

**Satisfied, and this is the load-bearing criterion.**

The shape at issue is *"a PLC entry completes on row `f`, and recorded row `f+1`
is a lag row"*. It occurs 15 times across every fixture advertising
`load_queue_state_per_frame`. The ROM resolves it two different ways:

| | order on row `f` | order on row `f+1` | arms on |
|---|---|---|---|
| 14 cases | `3SRAW` — service, retire, **arm** | *(nothing executes)* | `f` |
| 1 case (`ghz2_2` 107) | `3S` — service, retire, **no `RunPLC` entry at all** | `RAW` — **arm** | `f+1` |

Both `f` rows retire the head. Both `f+1` rows are lag rows by the recorded
counters — `v_framecount` held, V-blank counter advanced. Lag classification is
therefore identical, execution phase is identical, and deterministic queue
service predicts the same thing for both. The only difference is where the
physical frame boundary fell relative to `Level_MainLoop`'s tail call to `RunPLC`
(`sonic.asm:3032`), which is a 68000 cycle-position fact. No committed trace
column carries it, and none can without recording sub-frame timing — which the
contract's Non-goals forbid.

This is not a hook straddling an instruction. In the one case, `RunPLC` is not
entered at all on row `f`; the entire arm — entry, path taken, `patternsleft`
write — happens inside row `f+1`.

### 2.5 Criterion 5 — readiness can affect a gameplay-visible lifecycle

**Satisfied at the level of the kind. Not demonstrated at the level of the
15 instances, and this review will not pretend otherwise.**

For the kind: §2.2's ten poll sites include level-select input acceptance,
title-card duration, credits-page duration, end-of-act card start, Game Over card
start, and the Final Zone boss's start *and RNG stream*. PLC readiness is as
gameplay-visible as anything in the game.

For the instances, an honest reading of the probe data: in all 15 measured cases
the arming position within `{f, f+1}` has **no downstream service-schedule
consequence**, because row `f+1` runs no `ProcessPLC` in either outcome and both
resume real decoding on row `f+2`.

```
14 cases:  f: retire + arm (patternsleft := 18)   f+1: lag, no service   f+2: 18 -> 15
 1 case:   f: retire, no arm (patternsleft = 0)   f+1: arm := 14         f+2: 14 -> 11
```

The observable difference is confined to rows `f` and `f+1` themselves, in the
recorded `queue.s1_nemesis_plc.remaining_work` and `v_plc_buffer_dest` columns.
That is a strict-comparison difference, not a divergence in the polled gate.

Two things follow, and both belong in the record:

- The general mechanism *does* have gameplay consequence — whenever the frame
  after a deferred arm is not a lag row, the deferral costs a real service
  opportunity and shifts the drain, and through it every §2.2 consumer. The
  15-case corpus cannot show this because it was *selected* on "row `f+1` is a
  lag row".
- A reviewer who wants to reject this proposal should reject it here. The
  criterion is met by the kind and not by the sample, and §4 quantifies exactly
  what the sample is worth.

---

## 3. Why the native model cannot close this, and what changes since it was approved

`2026-07-28-s1-s2-plc-service-queues.md` gave S1/S2 PLC a native-model
disposition and explicitly deferred `PLC_QUEUE`:

> If the native predictor fails with identical submissions and structural
> phase/lag, implementation stops. A separate design review must demonstrate an
> independently identifiable, already-submitted, prepared queue job and the
> smallest consumer-visible completion boundary before `PLC_QUEUE` can be
> considered.

That is precisely the gate this document answers, and it is the gate its own text
anticipated failing. What has changed since is measurement, not opinion:

1. The native predictor **does** fail, on exactly the shape that design named as
   the risk: *"a completed entry requires a later main-loop `RunPLC` preparation
   before the next entry can be serviced"* and *"completion visibility must be
   pinned relative to each polling consumer"*. `99746ffa9`'s
   `TraceExecutionModel.isIterationHeldIntoNextRow` is the attempt to derive that
   pinning from recorded counters, and it is wrong 14 times in 15.
2. The discriminator has been shown to be sub-frame cycle position, which no
   structural predictor over `{lag, phase, ROM pattern counts}` can carry.
3. The recorder has been shown to be able to observe it exactly (the
   observability research), so the failure is *not* "we cannot see it either".

The narrow reading of that design's escape clause is satisfied: an independently
identifiable job (§7.2), already submitted (§7.1), already prepared (§7.1), and
the smallest consumer-visible boundary (§5).

---

## 4. What the stream is actually worth

Stated plainly, because a hostile reviewer will compute it anyway.

Census over 383,502 probed frames (`sonic1-complete-withemeralds.bk2` 202,301
frames, `s1-complete-run.bk2` 181,201):

- 1,170 armings and 1,170 retirements.
- The order string `RAW` — an arm on a row whose V-blank ran no `ProcessPLC` —
  occurs **once**, at raw 9849 (`ghz2_2` row 108).
- Of the two `3S` frames (retire with `RunPLC` never reached), only raw 9848
  leaves a pending entry behind. The other, raw 202260, retires the last entry to
  an empty queue, so there was nothing to arm.

Therefore:

| Model | Correct on |
|---|---|
| `99746ffa9` (defer whenever the next recorded row is a lag row) | 1 / 1,170 for the deferral decision; wrong on the other 14 of the 15-case shape |
| Revert (always arm at the loop tail of the frame that retired) | 1,169 / 1,170 |
| Revert + recorded stream | 1,170 / 1,170 |

**The revert alone closes 14 of the 15 cases. The entire apparatus in this review
exists for the fifteenth**, `ghz2_2` 107 — which the emerald run's visual lane
must cross to reach `mz2_3` at all, and which `LiveTraceComparator:318` is the
sole production consumer of.

Three options were weighed:

1. **Revert only, and pin the frontier at `ghz2_2` 107.** Cheapest. Leaves the
   whole-run visual lane permanently blocked one segment into the run, and leaves
   a known-unmodellable divergence with no owner.
2. **Model it from ROM state.** Ruled out by measurement — the discriminator is
   not in any ROM-visible state at frame granularity.
3. **The recorded stream.** What this review proposes.

The user has selected (3). This section exists so that the selection is on the
record with its cost, not so that it is re-litigated.

---

## 5. Kind identity, boundary, and registry change

### 5.1 The kind

`HardwareWorkKind.NEMESIS_PLC_QUEUE`, wire name `nemesis_plc_queue`.

- Named for the hardware-service class, not the game. S1 and S2 share
  `com.openggf.level.resources.NemesisPlcServiceQueue`; an `S1_*` name would be a
  game carve-out under hard rule 2. Which games *wire* a coordinator is a module
  decision, exactly as `createRuntimeArtCoordinator` is today (S3K overrides it;
  S1 and S2 do not). §7.4 covers S2.
- **Appended** to the enum, never inserted. `HardwareTimingSchedule.CANONICAL_ORDER`
  sorts on `kind().ordinal()`, and every committed S3K stream's canonical ordering
  depends on the existing two constants keeping indices 0 and 1.

### 5.2 The edge's meaning

> The ROM's `Level_MainLoop` `RunPLC` (`sonic.asm:3032`) promoted the FIFO head to
> the active decode slot on this raw frame.

The engine retains ownership of *which* entry (its own ROM-parsed queue, in its
own FIFO order), the pattern budget, the per-frame decrement, and retirement. The
edge decides only *when* the promotion becomes observable — the exact test hard
rule 4 states.

The **retirement** edge was considered and rejected on measurement, not taste: in
all 15 cases the head retires on row `f` in both outcomes, so a retirement edge
carries zero discriminating information. The arming is the only recordable
boundary that separates them.

### 5.3 The boundary

`pre_main_loop`, coupled at parse time the way `KOS_DECOMPRESSION_QUEUE` already
is (`HardwareTimingStreamLoader.java:83-87`,
`HardwareTimingSchedule.java:55-59`).

`RunPLC` sits at the `Level_MainLoop` tail, after `ExecuteObjects` (3006),
`DeformLayers` (3026), `BuildSprites` (3029), `ObjPosLoad` (3030) and
`PaletteCycle` (3031), and ahead of the loop-top `WaitForVBlank`. That is what
`HardwareServiceBoundary`'s own javadoc means by *"`PRE_MAIN_LOOP` is the last
boundary of a frame, not the first"*, and `LevelFrameStep.java:145-150` confirms
the engine's traversal order is `VINT_SERVICE` → objects → `POST_OBJECTS` →
`PRE_MAIN_LOOP` → `prepareAfterLoop(phase)`.

Measured support: the arming never precedes the frame's V-blank service. `A`
follows `3`/`9` in every frame containing both — **0 exceptions in 383,502
frames**. (A bare `R` *can* precede it: `R3` occurs 140 times. It is the arming,
not the `RunPLC` call, whose boundary is pinned.)

*Note: `2026-07-28-s1-s2-plc-service-queues.md` states the order as
`VINT_SERVICE -> PRE_MAIN_LOOP -> object/event scan -> POST_OBJECTS`. That is
stale; `HardwareServiceBoundary` and `LevelFrameStep` are the current authority.
The plan carries a task to correct it.*

### 5.4 Held-counter rows

`ghz2_2`'s edge lands on a lag row. That is the suppressed-row `pre_main_loop`
admission the cross-game contract already defines (§"2026-08-02 suppressed-row
boundary clarification") and `HardwareTimingReplayPort.applySuppressedRowCompletion`
(`:143-166`) already implements. **No new boundary semantics are introduced by
this proposal.** The precondition it enforces — `lastAppliedBoundary ==
VINT_SERVICE` on the latched current row — is exactly what a lag row produces
(`LevelFrameStep.java:123-128`), and it reproduces the probe's `RAW` order string
precisely: `Sonic1PlcService.serviceVBlank(LAG)` performs no service, matching the
absent `3` on raw 9849.

### 5.5 The registry guard, changed deliberately and visibly

`TestS1S2PlcComparisonOnlyGuard.timingKindRegistryAdmitsOnlyKosinskiWork`
(`:79-90`) asserts two things:

```java
assertFalse(... anyMatch(name -> name.contains("PLC")),
        "PLC readiness is native deterministic service, not timing-stream authority");
if (!admittedKinds.equals(Set.of("KOS_MODULE_QUEUE", "KOS_DECOMPRESSION_QUEUE"))) {
    fail("hardware timing may admit only S3K Kosinski work, but was " + admittedKinds);
}
```

A kind named to dodge the `PLC` substring still fails the second assertion. That
is deliberate, and it is the right design: the guard is a **closed registry**, and
opening it must be a visible, reviewed edit rather than a naming trick. This
review authorises that edit and specifies what replaces it.

The replacement must preserve the confinement, not merely widen the set. Required
post-change assertions in the same class:

1. **Exact registry.** `HardwareWorkKind` values are exactly
   `{KOS_MODULE_QUEUE, KOS_DECOMPRESSION_QUEUE, NEMESIS_PLC_QUEUE}`. Still a
   closed set; still fails on any fourth kind.
2. **Exact PLC subset.** The kinds whose name contains `PLC` are exactly
   `{NEMESIS_PLC_QUEUE}`, with the message rewritten to the accurate scoped
   statement: *"S1/S2 PLC readiness is native deterministic service except at the
   reviewed `Level_MainLoop` arming site."* A `PLC_TRANSFER_QUEUE` or a
   `DPLC_QUEUE` still fails.
3. **The shared kernel stays clean.** `com.openggf.level.resources.NemesisPlcServiceQueue`
   — the kernel S1 and S2 share — must not import `com.openggf.game.timing`.
   Readiness gating lives in the *game-owned* service and coordinator, so S2 does
   not silently inherit an authority nobody reviewed for it. **This assertion is
   new and is the substantive replacement for what the old test protected.**
4. **One submitter.** The only production source constructing a
   `HardwareWorkSubmission` with `NEMESIS_PLC_QUEUE` is
   `com.openggf.game.sonic1.resources.Sonic1RuntimeArtCoordinator`.
5. **The fingerprint waiver is confined.** The only kind submitted with
   `compressedLength == 0` is `NEMESIS_PLC_QUEUE` (§6.5).

The three existing package-isolation tests in that class
(`nativePlcServicesDoNotDependOnTracePackages`,
`traceProductionSourcesDoNotDependOnNativePlcServices`,
`replayAndBootstrapSourcesDoNotReferenceNativePlcServices`) are **unchanged and
must stay green**. `Sonic1PlcService` gains an import of `com.openggf.game.timing`,
which none of them forbids.

---

## 6. The submission fingerprint, and the `compressedLength` waiver

### 6.1 The claim under evaluation

A reviewer argued that `compressedLength` should be waived for Nemesis: the
stream is self-terminating, so its length is a pure function of `romSourceAddress`
given the ROM and carries zero identity information. The proposed replacement
tuple was `(kind, romSourceAddress, destinationAddress, header-derived pattern
count, variant)`, with the header word at `romSourceAddress` a 2-byte read
available identically to both sides. The claimed saving is the deletion of a C#
Nemesis scanner and its cross-implementation vectors.

### 6.2 Verifying the cost claim first

The cost claim is **correct, and understated**. Populating `compressedLength`
honestly requires new codec work on *both* sides:

- **C#.** There is no Nemesis decompressor or stream scanner anywhere in
  `tools/bizhawk-headless/`. The only Nemesis ROM parsing is
  `LoadQueueStateProjector.cs:123-138` (`NemesisPatternCount`), which reads the
  2-byte big-endian header masked `& 0x7FFF` and nothing else. A compressed
  length needs the full walk: code table to the `0xFF` terminator, then the
  bit-stream row decode to `patterns × 8` rows, in the shape of
  `HardwareTimingEventEngine.InspectStandardKos` (`:790-891`) whose
  `CompressedLength` is a cursor delta through the terminator.
- **Java.** `NemesisReader` reports decompressed bytes only. Its `ByteReader`
  (`NemesisReader.java:167-184`) and `BitReader` track **no absolute position or
  byte count**, and it consumes from a `ReadableByteChannel` positioned by
  `ResourceLoader`. There is no `NemesisReader.inspect` twin of
  `KosinskiReader.inspectStandard` / `inspectModuled`
  (`KosinskiReader.java:24, 31, 291, 333`). `NemesisPlcPatternCounts.derive`
  gets its count by fully decompressing and dividing by `Pattern.PATTERN_SIZE_IN_ROM`.
- **Vectors.** Kosinski parity is held by
  `src/test/resources/kosinski/standard-scanner-vectors.tsv`, consumed by
  `tests/HardwareTimingEventEngineTests.cs:1650-1691` *and*
  `TestHardwareSubmissionFingerprint.standardKosScannerAndFingerprintsMatchLanguageNeutralVectors`,
  with a nine-feature coverage assertion. A Nemesis equivalent is a third
  deliverable, not a rounding error.

By contrast the waived tuple requires **zero new codec code on either side**: the
recorder already reads the header word, and the engine already parses the entry.

### 6.3 Verdict: waiver granted, with one amendment

**Granted.** `compressedLength` is not carried for `NEMESIS_PLC_QUEUE`.

**Amendment: the header's bit 15 must not be silently dropped.** The reviewer's
tuple used the *masked* pattern count, which discards the Nemesis XOR-mode flag.
That bit selects a different decoder (`v_plc_ptrnemcode`, `$1502` vs `$150C` —
`_Variables.asm:166`; `sonic.asm:1386` picks the dumping routine). Two entries
identical in address, destination and pattern count but differing in mode decode
to different art, and the fingerprint must separate them. The right home for a
codec mode is `compressionVariant`, which is already a free-form string field
carrying `"kosinski"` / `"kosinski_moduled"` on the S3K side.

Adopted tuple:

| field | value | who computes it |
|---|---|---|
| `kind` | `"NEMESIS_PLC_QUEUE"` | both |
| `romSourceAddress` | the queue slot's 4-byte art pointer | engine: `PlcEntry.romAddr`; recorder: `v_plc_buffer` slot 0 longword, read at the arming hook **before** `sonic.asm:1405` rewrites it |
| `compressedLength` | **`0` — waived** | both |
| `destinationAddress` | the slot's 2-byte VRAM destination | engine: `PlcEntry.tileIndex` → VRAM word; recorder: `v_plc_buffer_dest` (slot 0 + 4) |
| `destinationLength` | `(header & 0x7FFF) × 0x20` | both, from a 2-byte big-endian ROM read at `romSourceAddress` |
| `compressionVariant` | `"nemesis"` or `"nemesis_xor"` from header bit 15 | both, same 2-byte read |
| `moduleCount` | `1` | both |

Both sides must read the header **from the ROM at `romSourceAddress`**, not
derive it from a decompression result. That makes the field a literal shared
2-byte read rather than two independent computations that happen to agree, and it
matches `LoadQueueStateProjector.NemesisPatternCount` exactly.

### 6.4 Justifying it against the contract's "ROM source span" wording

The contract says the fingerprint is *"generated independently by the recorder and
engine from a canonical tuple of kind, ROM source span, destination span,
compression variant, and module count"*.

1. **The span is still specified; only its redundant representation is dropped.**
   Nemesis is self-terminating. Under a hash-pinned ROM — REV01 CRC32 `AFE05EEE`,
   SHA-1 `69E1…FE5B`, and the fixture's own `rom_checksum` field — the byte length
   of the stream at address *A* is a *total function of A*. Two submissions with
   the same `romSourceAddress` necessarily have the same length. The field
   therefore adds no discriminating power over a field already in the tuple. It is
   derived data, not identity data.
2. **Including it is not free of risk.** It would make the timing port's
   correctness depend on two independent Nemesis bitstream decoders agreeing to
   the byte, one of which would exist for no other purpose. A divergence there
   fails admission with a *fingerprint mismatch* — a structural timing error
   reported for what is actually an art-decoding bug, in a stream whose entire
   job is to answer a scheduling question. That is a net increase in failure
   surface for zero identity gain, and it inverts the contract's own failure
   semantics, which reserve fingerprint mismatch for *"the engine submitted the
   wrong work"*.
3. **What identity actually needs to be caught is still caught.** The ROM's own
   identity for a PLC entry is the 6-byte cue; `romSourceAddress` and
   `destinationAddress` reproduce it verbatim. A wrong-entry submission fails on
   those. A misparsed header fails on `destinationLength`. A wrong decoder mode
   fails on `compressionVariant`. The only class of divergence the waiver stops
   catching is "both sides agree on the entry and the header but disagree on how
   many bytes the bitstream occupies" — which cannot change *which* work was
   submitted, only whether the engine's decoder is correct, and which is the
   business of the art pipeline's own tests.
4. **The waiver is per-kind and guarded.** §5.5 assertion 5 pins it. S3K keeps
   its measured lengths from `InspectStandardKos` / `InspectKosModule` and its
   vector file unchanged.

The contract's Completion-event-schema section should record this as an explicit,
scoped amendment rather than leave it as an implementation liberty. The plan
carries that task.

### 6.5 What the waiver does *not* license

It does not license waiving the span for a codec whose length is *not* a function
of its start — i.e. any framed or externally-sized format. It does not license
zeroing `destinationLength` or `moduleCount`. And it does not license the weaker
`(source, destination, patterns)` identity that
`QueueDiagnosticSnapshot.fingerprint` already occupies for comparison purposes:
the timing fingerprint remains a canonical SHA-256 over the full seven-field
encoding, with `compressedLength` present in the encoding and equal to zero, so
`HardwareSubmissionFingerprint.computeCanonical` and
`HardwareTimingEventEngine.ComputeSubmissionFingerprint` remain byte-identical
implementations of one function.

---

## 7. Confinement under hard rule 4

### 7.1 The stream may only delay already-submitted, production-created work

Submission point: the S1 runtime art coordinator's `beforeTimingService(PRE_MAIN_LOOP)`
hook, reached only through `HardwareBoundaryDispatch.serviceBoundary`. Within
`HardwareBoundaryDispatch` the order is fixed:

```
1. runtimeArtCoordinator.beforeTimingService(PRE_MAIN_LOOP)   <- submit the arming request
2. hardwareTiming.service(PRE_MAIN_LOOP)                      <- prepare it
3. notify.onBoundary(PRE_MAIN_LOOP)                           <- replay port applies the edge
4. runtimeArtCoordinator.afterTimingService(PRE_MAIN_LOOP)    <- claim ready work, prepareHead()
```

The coordinator submits when, and only when, its own queue state says the ROM
would arm: no active decode entry, and at least one queued entry. It computes the
descriptor from that entry. It has no access to the schedule, the trace, or the
edge; the replay port cannot reach step 1. If the engine has nothing to submit,
the edge fails with `engine pending: <none>`
(`HardwareTimingService.java:510-514`).

The deferral is genuinely a *delay*: in the `ghz2_2` case the submission occurs at
step 1 of raw 9848 and the edge admits it a full physical row later, at raw 9849,
through `applySuppressedRowCompletion`.

Anticipated objection: *"submission and release happen at the same boundary in
1,169 of 1,170 cases, so the edge is effectively creating the work."* It is not.
The submission's identity, ordinal, and descriptor are all engine-derived before
any edge is consulted, and a mismatch on any of them fails closed. What the edge
supplies is a single bit — *now*, or *not yet* — over work that already exists.

### 7.2 It carries no gameplay values

The wire event is unchanged: `{event, raw_frame, boundary, kind, ordinal,
submission_fingerprint}` (`HardwareTimingStreamLoader.java:34-35`). No pattern
count, no queue depth, no address, no payload.

### 7.3 It does not key on frame index, zone, route, or game name

`raw_frame` is the physical capture row and is part of the existing schema; the
prohibition is on *branching logic* keyed on a frame index, which this design has
none of. Nothing in the engine reads a zone id, an act, a fixture name, a route,
or a game name. The kind is game-neutral; the submission is produced by a
game-owned coordinator, which is the placement `docs/architecture/per-game-rule-placement.md`
prescribes and which `S3kRuntimeArtCoordinator` already occupies.

### 7.4 It does not drag the rest of the corpus with it

`2026-08-06-level-load-span-timing-port-scope.md` §4.2 rejected `LEVEL_LOAD`
partly because *"adding `LEVEL_LOAD` makes it `RECORDED` for every fixture that
has a timing stream. S3K loads levels too."* That argument is correct and does
**not** apply here, for a reason that is verifiable rather than asserted:

- The v5 registry is all-or-nothing, so `NEMESIS_PLC_QUEUE` becomes `RECORDED`
  for every S3K timing fixture too.
- But its **only submitter** is `Sonic1RuntimeArtCoordinator`, reached only from
  a coordinator that `Sonic3kGameModule.createRuntimeArtCoordinator` does not
  return. S3K registers no `PlcLifecycleService`
  (`2026-07-28-s1-s2-plc-service-queues.md`), instantiates no
  `NemesisPlcServiceQueue`, and submits no Nemesis work.
- A `RECORDED` policy over a kind with zero submissions is inert:
  `firstOrdinals` establishes no base, `pendingSubmissions()` stays empty,
  `endRecordedAdmission` passes, and `handoffTo`'s policy-equality check compares
  the same static map on both sides.

**No S3K fixture needs re-recording, and no S3K behaviour changes.**
`TestCommittedHardwareTimingFixtures` staying green is the verification, not the
claim.

S2 is in the same position for now: it shares the kernel but wires no coordinator,
so `Sonic2PlcService` continues to arm natively and S2 fixtures carry no timing
stream. If S2 later wires one, its fixtures need re-recording — that is a future
scope decision, and §5.5 assertion 3 ensures it cannot happen by accident through
the shared kernel.

### 7.5 `TestHardwareTimingAuthorityGuard` stays green, unmodified

Verified: the class holds **no kind literals at all**. It is entirely
package/path/regex shaped —
`TIMING_PACKAGE_PREFIX = "com.openggf.trace.timing"` (`:28`),
`GAMEPLAY_OWNER_PACKAGE_PREFIXES` (`:67`), `ROOT_GAMEPLAY_OWNER_TYPES` (`:78`),
filename-construction and parser-isolation scans (`:281`, `:595-614`).

The design keeps every constraint it enforces:

- `Sonic1PlcService`, `Sonic1RuntimeArtCoordinator` and `NemesisPlcServiceQueue`
  import `com.openggf.game.timing` only — never `com.openggf.trace.timing`. This
  is the same route `S3kKosModuleQueue` uses: it holds an injected
  `HardwareTimingService` and asks `timing.isReady(handle)`, and never knows a
  trace exists.
- `PlcFrameLifecycleCoordinator` is a gameplay owner and gains no timing-parser
  import.
- The consumption point does not move, so the guard's exemption list
  (`TraceHardwareTimingBoundaryObserver`, `HardwareTimingReplayPort`,
  `TraceSuppressedRowClosure`, `TraceSessionLauncher`) is untouched.

**If any implementation step would require editing this class, that step is
outside the contract and the design is wrong.** This is a hard acceptance
criterion, not a preference.

---

## 8. Schema version

Per the user's decision the schema is bumped and **no backwards compatibility,
shim, or migration path is provided**. `trace_schema` moves 5 → 6.

Sites, exhaustively:

| Site | Change |
|---|---|
| `src/main/java/com/openggf/trace/TraceMetadata.java:495-498` | hard-checks `== 5` |
| `src/main/java/com/openggf/trace/TraceRunManifest.java:41` | `TRACE_SCHEMA = 5`, referenced at `:177`, `:183`, `:391` |
| `tools/bizhawk-headless/src/Recording/TraceContract.cs:12` | `Schema = 5`, emitted by `AppendNativeEnvelope` (`:21-29`) into every metadata writer |
| `tools/traces/validate_trace_v5.py` | `KIND_ORDER` (`:32`) gains `nemesis_plc_queue`; the boundary check at `:219` gains the same `pre_main_loop` constraint; the script name and schema constant follow the bump |
| every committed `metadata.json` and `run_manifest.json` | the `trace_schema` integer |

Two things a reviewer should know before approving the bump:

- **It invalidates every committed fixture's metadata, not just the S1 run.**
  `TraceMetadata` rejects any value but the current one, with no fallback. This is
  a mechanical integer re-stamp rather than a re-capture — no payload byte changes
  — but it touches the whole corpus and must be done in one commit or the suite is
  red in between.
- **Strictly, nothing requires it.** The container grammar is unchanged: same
  filename, same six fields, same canonical ordering, same "presence of the file
  enables the one current registry" rule. Only the registry widens, and v5 already
  declares that *"no admission policy is inferred from which event kinds happen to
  be present"*. The bump buys a legible marker that the registry changed. That is
  a reasonable thing to want and the user has asked for it; it is recorded here as
  a decision rather than a necessity so that the alternative is not lost.

---

## 9. The fail-closed starvation risk, and how the schedule design accounts for it

This is the risk a reviewer raised, and it is real, structural, and larger than
the brief implied.

### 9.1 The mechanism

Under `RECORDED` admission a job's readiness is released *only* by
`admitRecordedCompletion`. `HardwareTimingService.releasePreparedInFifoOrder` is
guarded by `admissionPolicyFor(kind) == LIVE` (`:68-75`, `:73-75`). There is no
other path to `admitReadiness`. So an S1 Nemesis job submitted where no edge can
ever arrive is **permanently pending**, and any consumer that waits on it never
advances.

The three failure modes that follow, worst first:

1. **The level-load / title-card drain.** `Level_TtlCardLoop` (`sonic.asm:2814`)
   spins on `tst.l (v_plc_buffer).w` and calls `RunPLC` every iteration. The
   engine models this in its level-entry path, and the S1 minimal title-card scan
   goes through `LevelFrameStep.executeHardwareTimedObjectScan`, **which traverses
   the hardware boundaries** (`:353`, `:362`). A naive coordinator that submits at
   every `PRE_MAIN_LOOP` would submit here — and there is no edge, because the
   recorder does not record these rows at all: `S1RunCaptureRunner` arms a segment
   only when `v_gamemode == 0x0C && obCtrlLock == 0`, which is after the title
   card. Result: the level never finishes loading.
2. **The 21 inter-segment gaps.** The emerald run's manifest has 34 segments and
   gaps of 216-236 rows between level segments. Transition-gap rows never service
   a production V-blank (`TraceSessionLauncher.suppressesRunNativeLevelBody`), so
   no boundary is traversed and `lastServicedBoundary` is never set — meaning an
   edge could not be admitted there even if one existed, and
   `HardwareTimingStreamLoader` could not represent one anyway
   (`raw_frame` is bounded to `[0, traceFrameCount)`, `:88-91`).
3. **An arming spilling past a segment's last recorded row.** If the ROM's
   deferred arm landed on the first *gap* row after a segment, the engine would
   hold a pending submission across `handoffTo`, which demands a matching
   next-segment edge by `(kind, ordinal, fingerprint)` (`:227-243`).

### 9.2 The resolution

**Confine the recorded authority to one named ROM call site.** The stream governs
`Level_MainLoop`'s `RunPLC` at `sonic.asm:3032` and nothing else; the other ten
`RunPLC` call sites arm natively.

That rule is expressed in the reviewed `PlcLifecyclePhase` vocabulary rather than
invented: `Sonic1PlcService.prepareAfterLoop(phase)` routes through the
coordinator **only for `ORDINARY_LEVEL`**, and calls `queue.prepareHead()`
directly for `TITLE_SCREEN`, `LEVEL_SELECT`, `LEVEL_TITLE_CARD`, `PALETTE_FADE`,
`SPECIAL_STAGE_RESULTS`, `CREDITS_TEXT` and `CREDITS_DEMO`. The phase is claimed
at `frame.claim(phase)` (`LevelFrameStep.java:144`), before the frame's first
boundary, so it is already latched when `PRE_MAIN_LOOP` is reached.

This is not a trace-shaped rule and not a carve-out:

- `ORDINARY_LEVEL` ⟺ `Level_MainLoop` is a ROM-loop classification that
  `2026-07-28-s1-s2-plc-service-queues.md` already reviewed and that the engine
  already computes for its 3-versus-9-pattern budget.
- It contains no zone, act, route, fixture, frame index or game name.
- It is the same distinction the recorder's own arm predicate makes from ROM
  state (`v_gamemode == 0x0C && obCtrlLock == 0`), so the two sides gate on the
  same fact from two directions.

Consequences, all of them intentional:

- Failure mode 1 disappears: the title-card drain claims `LEVEL_TITLE_CARD`,
  submits nothing, and arms natively.
- Failure mode 2 disappears: gap rows run no level body, so no phase is claimed
  and no boundary is traversed. The ordinal ledger does not advance across a gap
  on either side — the recorder must count armings **only while a segment is
  armed**, which is a deliberate divergence from the S3K recorder's run-wide
  ledger (`S3KCompleteRunCaptureRunner`'s null-writer
  `ObserveUnexportedHardwareBoundary`). It is the correct divergence, because
  S3K's engine *can* submit and claim during a gap and S1's cannot. The result is
  that `NEMESIS_PLC_QUEUE` ordinals are contiguous across the whole run with no
  gaps, which is strictly easier to satisfy than the contract's gap-tolerant
  rule.
- Failure mode 3 is made **loud instead of silent**: Nemesis submissions are
  marked `exportableAcrossSegment = false`, so a pending arming at a segment
  boundary fails `handoffTo` with *"non-exportable pending hardware submission at
  segment end"* naming the job, rather than hanging or being quietly absorbed.
  This is also semantically right — a PLC arming cannot survive a level restart,
  because the restart's `ClearPLC` (`sonic.asm:2711`) wipes the queue.

### 9.3 What remains, and what must be measured before it is claimed closed

Two residual hazards, both with a named measurement in the plan:

- Whether any recorded row in any of the 34 segments claims a phase other than
  `ORDINARY_LEVEL` while an arming fires — an end-of-act `PALETTE_FADE` inside an
  armed window would produce an engine/recorder count mismatch.
- Whether any arming fires on the first gap row immediately after a segment's
  last recorded row — the failure-mode-3 trigger.

Both are answerable from the existing probe. Neither is guessed at here.

### 9.4 A defence in depth that costs nothing

Because starvation manifests as an unbounded wait rather than an exception, the
engine's PLC-drain consumers must be bounded. The plan requires that the
level-entry drain and any other bounded-iteration PLC wait fail with a message
naming the pending `NEMESIS_PLC_QUEUE` handle when it exceeds its iteration
budget, rather than spinning. That converts every unanticipated instance of §9.1
into a diagnosable structural failure.

---

## 10. Comparison policy

Unchanged and strict. The edge reproduces a missing external timing input; every
gameplay and queue column stays compared, on every row, including the deferred
one. No field is exempted, relabelled as timing-correlated, or suppressed.

One consequence must be verified rather than assumed, because it is the design's
least obvious failure mode. On the denied row (`ghz2_2` 107 / raw 9848) the ROM
samples `v_plc_patternsleft == 0` with a non-empty queue. The engine's
`NemesisPlcServiceQueue.captureDiagnostics` reports an idle-with-queued state as
`prepared=false, remaining=-1` (`:94-105`). If the comparator does not already
treat those as equal, the correct edge will still produce a *new* comparison
failure on row 107. The plan carries this as a phase-gated verification with the
fix owned by the diagnostic projection, not by the timing port.

---

## 11. What this review does not authorise

- Any further PLC-adjacent kind. `PLC_QUEUE` as a generic name, a DPLC kind, a
  VDP transfer fence, a plane-draw fence and `LEVEL_LOAD` all remain
  non-authoritative candidates, each needing its own evidence and review.
  `LEVEL_LOAD` specifically remains **rejected** on the grounds in
  `2026-08-06-level-load-span-timing-port-scope.md`, which this proposal does not
  disturb: that span submits nothing, polls nothing, and has no representable
  `raw_frame`.
- Composite kinds. The admitted kind is one named ROM service class with one
  named arming site. "Any long 68k routine we could not derive" is exactly the
  smuggling channel the closed registry exists to shut, and §5.5's replacement
  assertions keep it shut.
- S2 or S3K Nemesis authority. Neither wires a coordinator; neither may gain one
  without a scope decision and a fixture re-record.
- Hydrating, seeding, or reading any comparison column. The timing port remains a
  dedicated input with no access to `physics.csv` or `aux_state.jsonl`.
- Editing `TestHardwareTimingAuthorityGuard`.

---

## 12. Recommendation

**Admit `NEMESIS_PLC_QUEUE`** to the v5 hardware-timing registry, at
`pre_main_loop`, with the waived-`compressedLength` fingerprint of §6.3, confined
to `Level_MainLoop`'s `RunPLC` by the `ORDINARY_LEVEL` phase gate of §9.2, with
the registry guard replaced as specified in §5.5 and
`TestHardwareTimingAuthorityGuard` untouched.

Implement per
[`../plans/trace/2026-08-06-s1-nemesis-plc-timing-stream-plan.md`](../plans/trace/2026-08-06-s1-nemesis-plc-timing-stream-plan.md),
whose phases 0-2 are measurement and are prerequisites for specifying phases 4
and 6.

The honest summary for anyone approving this: it buys one row of one fixture
today, and it buys a correct owner for a class of divergence that has no other
owner. The first half of that sentence is why it should be scheduled behind the
S3K vertical slice; the second is why it should be built rather than papered over.
