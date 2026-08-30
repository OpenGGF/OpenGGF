# Audio frontier log

Chronological record of audio driver-oracle comparisons: engine sound-driver
behaviour measured against reference captures of the shipped ROM drivers
running under emulated hardware. This file mirrors the role of
`trace-frontier-log.md` for the audio oracles: every entry records the exact
command, the commit/worktree context, the fixture identity, the pass/fail
result, the error count, and the first divergence's tick and field, so a
frontier that moves — in either direction — is attributable to a specific
change. Entries are appended, newest last, and never rewritten.

Entry format:

- **Date / worktree / commit** — where the measurement ran.
- **Oracle** — which comparator and fixture pair produced the number.
- **Command** — the exact reproducible invocation.
- **Result** — MATCH / DIVERGENCE / INVALID, with total and divergent tick
  counts.
- **First divergence** — tick ordinal, movie row, field, expected/actual.
- **Reading** — what the divergence is, with the disassembly citation; never a
  fix plan.

A comparison that never ran looks identical to a green one: no entry may be
added without its break-it evidence (a deliberately corrupted input visibly
moving the report) recorded at least once for the oracle that produced it.

---

## 2026-08-30 — S2 driver oracle: first measurement (expected red)

- **Worktree:** `.worktrees/sdre-oracle-s2`, branch `feature/ai-sdre-oracle-s2`
  (commit recorded in the entry's own commit).
- **Oracle:** `S2AudioOracleComparator` against
  `src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz`
  (movie rows 10150-10899 of the pinned S2 complete-emeralds movie; EHZ music
  reload anchor at row 10195; recorded by the TraceChaser headless harness
  with the patch-0001 GPGX audio observer — see the fixture's metadata JSON
  and `docs/architecture/research/audio/2026-08-30-s2-driver-oracle.md`).
- **Command:**

  ```bash
  mvn -q -Dmse=off compile dependency:build-classpath -Dmdep.outputFile=target/oracle-classpath.txt
  java -cp "target/classes:$(cat target/oracle-classpath.txt)" \
    com.openggf.tools.audio.parity.s2.S2AudioOracleTool \
    --fixture "$PWD/src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz" \
    --rom "$PWD/s2.gen"
  ```

- **Result:** DIVERGENCE — 698 of 698 recovered driver-update ticks divergent.
- **First divergence:** tick 0 (movie row 10201), `global.tempoTimeout`,
  expected `0x3c`, actual `0x0`.
- **Reading:** the ROM seeds `TempoTimeout = CurrentTempo = 9Eh` at song load
  (`s2.sounddriver.asm:1820-1822`) and runs `TempoWait` at the top of the
  first `zUpdateMusic` (`sd:545-551`): `9Eh + 9Eh = 13Ch` → carry → `3Ch`.
  The engine's first update leaves its tempo accumulator at 0 — neither the
  load-time seed nor the first-update accumulation is modelled
  (gap analysis §1.2 #1/#2; behaviour spec §3.1). Every subsequent tick also
  diverges (cadence differences cascade through durations, envelope cursors
  and the write stream), so 698/698 is the honest count, and the tick-0 field
  is the frontier to move first. Two measurement facts recovered from the
  reference along the way, both now encoded in the comparator's tick
  recovery: the Saxman EHZ load masks interrupts across movie rows
  10195-10200 (those frames hold a half-initialised driver image and no
  `zUpdateMusic` service), and the caught-up Z80 misses row 10202's V-int
  entirely — one oracle tick is therefore one completed `zUpdateMusic`
  service from the observer's service stream, not one video frame.
- **Break-it evidence** (`TestS2AudioOracleComparator`, outputs from the
  evidence run at this commit):
  - untampered self-comparison: `S2 driver oracle: MATCH (698 ticks)`;
  - reference byte corrupted (tick 40, `FM1.DurationTimeout ^ 0x55`):
    `S2 driver oracle: DIVERGENCE at tick 40 (movie row 10242), field
    track.FM1.durationTimeout: … expected=0x41 actual=0x14 [1 of 698 ticks
    divergent]`;
  - engine write corrupted (tick 20, `writes[0] value ^ 0x40`):
    `S2 driver oracle: DIVERGENCE at tick 20 (movie row 10222), field
    writes[0]: … expected=ym0[0x28]=0x0 actual=ym0[0x28]=0x40 [1 of 698
    ticks divergent]`.
