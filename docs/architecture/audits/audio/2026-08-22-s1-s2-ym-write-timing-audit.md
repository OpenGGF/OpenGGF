# S1/S2 YM write-timing audit

## Ruling

Both audited ring paths cross the predeclared materiality threshold. Sonic 1
and Sonic 2 therefore need separate, source-owned timing-profile follow-ups.
This task deliberately leaves both runtime configurations at
`YmServiceTimingProfile.none()`; it does not reuse the locked-on S3K profile or
add an S1/S2 timing constant.

The threshold was fixed before capture: one isolated group must span at least
four internal YM samples (`4 * 1008 = 4032` master cycles), and collapsing the
group must change a key-on operator attenuation by at least eight units or the
bounded onset RMS by at least one percent. The attenuation branch decides both
games:

| Game/path | Representative isolated span | Atomic attenuation | Source-timed attenuation | Maximum change | Ruling |
|---|---:|---|---|---:|---|
| S1 `SndB5_Ring`, FM5 | 69,167 master cycles | `[1,1,311,313]` | `[89,73,391,378]` | 88 | material |
| S2 `Sound35_RingRight`, FM5 | 135,435 master cycles | `[0,22,0,38]` | `[56,79,57,96]` | 58 | material |

`TestS1S2YmWriteTimingAudit` verifies every capture's full 3,624-byte native
YM2612 pre-group context and digest. The diagnostic core saves that context
immediately before the first data write, replays atomic and timed lanes from
that exact state, restores the live context transactionally, and asserts that
the timed lane equals the live key-on attenuation. No OpenGGF seed or fixed
sample history participates. RMS is not needed
for the ruling because the independently predeclared attenuation condition is
already true.

## Authenticated capture boundary

The diagnostic lab joins writes to source events, never to a voice/register
fingerprint.

- S1: `QueueSound2` writes `$B5` at ROM `$1394`; accepted `Sound_PlaySFX` at
  `$721C6` arms the audit, and FM5 `cfSetVoice` at `$72C26` with `A5=$FFF280`
  emits the ordered group-start event.
- S2: the M68K `PlaySound2` request is at `$1376`. The Z80's shipped ring
  alternation reaches `zPlaySound` at `$0975` with `C=$B5` only for
  `Sound35_RingRight`; the next `cfSetVoice` at `$0E03` emits the ordered
  group-start event only when `IX=$1D90`, the exact `zSFX_FM5` owner.
  Wrong-owner starts and an intervening `cfSetVoice` poison the join rather
  than synthesising an owner from configured channel state. Left-speaker `$CE`
  admissions are excluded by source state, not by their register stream.

The event is emitted into the same native lab buffer as post-`fm_update` YM
writes, so ordering is native and dense. Every admitted group runs from that
event to FM5 key-on. A request within the source-authentic 37-frame
`5 + 5 + $1B` ring duration is classified as overlap; later requests are
isolated.

Inputs:

| Input | Identity |
|---|---|
| S1 ROM | `$OPENGGF_MAIN_WORKSPACE/s1.gen`, SHA-1 `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b` |
| S1 BK2 | `sonic1-complete-withemeralds.bk2`, SHA-256 `f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b` |
| S2 ROM | `$OPENGGF_MAIN_WORKSPACE/s2.gen`, SHA-1 `8bca5dcef1af3e00098666fd892dc1c2a76333f9` |
| S2 BK2 | `sonic-2-sonic-tails-complete-emeralds.bk2`, SHA-256 `e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5` |
| BizHawk/GPGX | BizHawk 2.11 commit `427556b5...`; GPGX commit `051d430d...` |
| Diagnostic patch/core | SHA-256 `9c204d55e1c7524bf94180aa930d6be6a88e332d5227f187a2ed3d048b6bd375` / `3e2cddbb22c93676046f980926fd14d0689bb5bfd36ee75d0d630c2289b940a3` |

## Sonic 1 source calculation

S1 is a 68K driver. The retained representative ledger contains all 909 native
decoded instruction occurrences from source admission through key-on, including
875 occurrences assigned to the 30 inter-write gaps. Every row records exact
PC, opcode, start cycle, next PC, delta, call/return or branch outcome, and
occurrence-exact ordinary/control-flow, busy-poll, and YM-write roles. Its
source field is a checked PC-to-disassembly-label/line mapping, rather than a
blanket path citation. `TestS1S2YmWriteTimingAudit`
hash-locks that ledger, reconstructs every gap from the ordered occurrence
chain plus the source-authentic terminal `$13C1` write boundary, and compares
it to the retained native write. Deleting, reordering, changing PC/opcode/count,
or injecting a fake primitive is an explicit failing mutation.

This supersedes and withdraws the round-1 aggregate decomposition (including
its suspicious 11/31 synthetic `MOVE.B` counts). The actual captured gaps
contain 27–63 decoded occurrences; they are not represented as multiplied
primitive buckets. The small isolated variation (66,836–69,167 master cycles) comes
from the shipped YM busy-poll exits and branch state; overlap groups span
66,577–69,426. The 5,000-frame capture contains 38 admitted FM5 groups:
18 isolated and 20 overlap.

The path audit covers:

- `FinishTrackUpdate` at `s1.sounddriver.asm:436-456`;
- `WriteFMIorII`, `WriteFMI`, and `WriteFMII` at lines 1713-1769, including
  both address/data busy polls;
- `cfSetVoice` / `SetVoice` at lines 2313-2375, including voice fields,
  carrier TL, panning, frequency, and key-on;
- `cfStopTrack` at lines 2489 onward, including FM key-off and the active
  overridden music-voice restore.

The retained source map observes the actual symbol boundaries: `$71CD8` is
owned by `FMUpdateTrack`, `$71D60` begins `FinishTrackUpdate`, and `$72A64`
is the `coordflagLookup` table entry rather than `CoordFlag` itself.

The shipped `FixBugs = 0` path is the authority. Completion/restore is audited
as the same FM5 owner lifetime ending after the 37-frame source duration; an
overlap replaces the owner before that completion path, while an isolated group
allows the key-off/restore path to complete.

Deterministic A/B compact result:

- JSON SHA-256 `a62c26df8363cfdcf622fdbbd31688032c2c3cfa6a51bde04101e8cdfa08c088`;
- terminal payload `032dee96c0163e089b689c8095ce9c95842e9bdf3a60006bd14953f3e254070a`;
- raw writes `87265ee3d08d91128faef6338695c426f6697df127b0431b16d77ade6210a509`;
- full instruction stream `b860dccea2be3c3bae9788fd4621e7fd57311e6c2d9e57ef34a5617222ce23aa`;
- canonical source map `96f514aa28a41038e6622f0237726cdbd0692301946ce974f58c9e789dfddd3c`;
- representative ledger `59000b1cbc90a3340e6f9142dfa96fd9ddea982af2d653ab5e52abf40557b689`;
- six-column projection `06888e2d89794879947fa2256aaf3bb9ae0244e0c147f400dd6363bc4d42125c`;
- FM5 samples `10ccfc1aa351f34e9b7be4b7eab44fb8003e73f8bef15bff3ba4218c50285534`.

## Sonic 2 source calculation

S2 uses its own Z80 driver. The representative ledger contains all 953 decoded
Z80 instruction occurrences, 899 assigned to the 30 inter-write gaps. It
records exact conditional outcomes through their next PCs, assigns every PC
to a specific driver label and bounded line range, and marks each
banked `LD r,(HL)` occurrence whose 10-T-state delta consists of the source
instruction's 7 T-states plus GPGX's exact 3-T-state bank-read wait. The joined
sequence derives the 135,435-master-cycle group without a copied total.

This supersedes the round-1 aggregate decomposition and its suspicious 39
`LD r,(IX+d)` bucket. Thirty-nine is the actual *total decoded occurrence
count* only for gap 21; the ledger shows the heterogeneous PC/opcode sequence
and its one exact bank-wait occurrence. The capture contains 28 admitted FM5
groups: 14 isolated and 14 overlap.

The path audit covers:

- `zWriteFMIorII`, `zWriteFMI`, and `zWriteFMII` at
  `s2.sounddriver.asm:343-389`;
- `zFinishTrackUpdate` at lines 947 onward;
- `zSetMaxRelRate` is at source lines 2088-2101, but is inside the
  `FixDriverBugs` block and therefore has no address or executed occurrence in
  the shipped `fixBugs = 0` driver. The earlier `$CFC` attribution is
  withdrawn: `$CFC-$D19` is `cfPanningAMSFMS` at lines 3004-3045;
- `cfSetVoice` / `zSetVoice` at lines 3271-3432, including banked voice reads,
  stored carrier TL, panning, frequency, and key-on;
- `cfStopTrack` at lines 3514 onward, including key-off, bank switch back to
  music, and active music-voice restore.

Likewise, `$C46` is `zFMNoteOn`, `$C63` begins `zBankSwitchToMusic`, and
`$CFC` is `cfPanningAMSFMS`. The canonical map is consumed by the generator;
the Java gate hash-locks it, requires exactly one range for every ledger PC,
and rejects overlapping/unknown coverage and boundary, label, or line edits.

The driver is built with `FixDriverBugs = fixBugs = 0`; the shipped NOP/busy
wait and un-fixed branches are retained. All audited groups report zero DMA
stall markers. Banked reads therefore use the reviewed GPGX uncontended path;
the audit does not claim parity during simultaneous VDP DMA.

Deterministic A/B compact result:

- JSON SHA-256 `7af6752daf84249dd2513627e4f1c7260b15e3c28817aaa764440602708296c3`;
- terminal payload `b522de0b43aa6dbc6b04e53fe6a7b92295be320ae4a986af8a88f8bf48273167`;
- capture script `a21fbd6ee44173fd8bbe20b0af747bc703f0f1c7f9c521a1866f89e579cd7388`;
- raw writes `ea68ebff17ad939b9b17040f7ce846a4bfef895a4401abcf8104a1e972f0179e`;
- full instruction stream `d03eed2d2679b2287c626c5098b96140c22e3746e425a23901ef023998826c3c`;
- canonical source map `96f514aa28a41038e6622f0237726cdbd0692301946ce974f58c9e789dfddd3c`;
- representative ledger `b8f632aab340f07e2ed863944f2cfd3d39badbe0410a23979ebb10dd81e86372`;
- six-column projection `c4dbf980004f7b1af450a0889a6bc5ae8b443b5378cd0fb546d889dd8a1a54d2`;
- FM5 samples `a277dfd825e9c72706094b837666a3ddeb17b97ed04ad5d6f2b5cf21913ddaeb`.

## Corrected S3K closure

The expanded lab regenerated the existing locked-on oracle twice. A/B files
are byte-identical at SHA-256
`f420226631d8a98beb0c8d097ad4457eaa9b02efd5429ee9c2c05d7380105220`.
All 12 groups retain the exact 34-write register/value order, group 7 retains
the 151,590-master-cycle relative terminal delta and
`[757,976,882,1023]` native key-on attenuation, and every DMA/fault/overflow
marker is zero. Raw write, projection, and FM5 hashes remain unchanged; only
the diagnostic patch/core provenance and terminal digest changed. Absolute
first-write phase remains informational.

## Follow-up boundary

The S1 and S2 follow-ups are recorded separately in:

- `docs/architecture/plans/audio/2026-08-22-s1-ym-write-profile-follow-up.md`;
- `docs/architecture/plans/audio/2026-08-22-s2-ym-write-profile-follow-up.md`.

Neither follow-up is implemented by this audit commit.
