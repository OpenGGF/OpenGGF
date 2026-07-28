# S1/S2 PLC readiness evidence

Date: 2026-07-28

## Disposition

`EVIDENCE_INCOMPLETE`. Tasks 2–9 are blocked. No engine queue/runtime
behaviour was changed, and this document does not authorize trace hydration or
a hardware-timing event kind.

## Structural findings

Both retail queues are sixteen six-byte entries at `0xFFF680`; neither
`AddPLC`/`LoadPLC` checks capacity. S1 services 9 patterns for title,
title-card, ending, and fade handlers, and 3 for level and paused handlers;
Continue does not service PLC. S2 uses 6-pattern handlers for title/title-card,
results/fade, and menu paths, and `ProcessDPLC2`'s 3-pattern handler for level
and paused paths; ending does not service PLC. Both `RunPLC` and
`RunPLC_RAM` publish `PatternsLeft` before decoder preparation in the retail
build. A clear or replacement zeroes the buffer but not all decoder scalars,
so capture must prove it occurs only while idle before a logical queue can
model it.

## Tooling and attempted captures

The diagnostic Lua scripts write only to an explicit
`OGGF_PLC_PROBE_OUTPUT` path and reject an existing path. They refuse to run
until reviewed environment-supplied addresses pin the RAM fields, VInt/HInt
sampling, each preparation/service/pop pre/post pair, the post-shift empty
point, and every consumer. This is intentional: routine-entry placeholders
cannot yield approval. Their JSONL records contain raw frame/order, handler,
lag, HInt deferral, queue head/destination/count, and slot count. Replacement
suppresses its nested retail clear, so it is represented atomically rather
than as a false replace-then-clear queue transition. They are deliberately
separate from canonical trace recorders.

ROM hashes were verified:

| game | SHA-1 |
|---|---|
| S1 World REV01 | `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b` |
| S2 World REV01 | `8bca5dcef1af3e00098666fd892dc1c2a76333f9` |

The native headless harness was exercised successfully with the S1 GHZ movie:

```bash
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh --mode trace --rom s1.gen \
  --movie src/test/resources/traces/s1/ghz1_fullrun/ghz1_fullrun.bk2 \
  --output /tmp/<scratch>
```

It reported BizHawk 2.11, the verified S1 hash, 4,806 movie frames, and a
3,905-frame trace. That harness does not expose execute hooks and therefore
cannot produce the isolated PLC event stream.

The required Lua diagnostic launches for S1 complete-run and S2 ARZ were also
attempted with `BIZHAWK_HOME` set explicitly. Both fail before Lua starts
because this execution environment has no X server:

```text
System.ArgumentNullException: Could not open display (X-Server required)
```

The missing acquisition dependency is an X-capable BizHawk 2.11 session (or a
native headless execute-hook extension) able to replay each required lifecycle
twice. Before that session is used for approval, the reviewed environment
configuration must pin every required hook address. Until then there are no
repeat hashes, no observed preparation-race disposition, and no
approved/rejected predictor comparison.

## Rerunnable analyzer

`PlcTimingEvidenceTool` derives pattern counts directly from the supplied ROM
Nemesis headers. A dedicated `plc_frame_state` record is the only source of a
structural frame; it never builds a frame from service-oracle records. The
tool requires a consumer observation and independently sampled progress at
the preparation and service boundaries, slot-count reduction at pop, and a
zero-slot post-shift empty observation. It then compares its predicted
prepare/service/pop/empty/poll edges against the oracle. Its unit test mutates
handler, lag, HInt deferral, preparation, poll order, and budget; each
mutation rejects the oracle.

The committed gzip vector is an explicit incomplete-gate marker, not captured
evidence. It must be replaced only with compact, reviewed derived vectors
after byte-identical repeated captures cover title cards, boss readiness,
results, Game Over, and special-stage results for both games.
