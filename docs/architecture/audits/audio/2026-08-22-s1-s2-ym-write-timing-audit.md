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
| Diagnostic patch/core | SHA-256 `563ef6338c9ddbe41c711842688b3daa2f970d312e581484ac7b2a0196241414` / `1107ce61ea6d2c4cdd80f35cb2c0ec6f5ae58d4bd62a3fd8269a03c09f6eee36` |

## Sonic 1 source calculation

S1 is a 68K driver, so this calculation uses 68K instruction cycles multiplied
by seven master cycles. It does not import any Z80 T-state constant. The first
isolated FM5 group executes these successive 68K-cycle gaps:

`310, 310, 308, 308, 308, 310, 308, 308, 308, 310, 308, 308, 308, 310,
308, 308, 308, 310, 308, 308, 333, 328, 328, 328, 316, 420, 297, 716,
333, 310`.

Their sum is 9,881 68K cycles, or 69,167 master cycles, exactly the corrected
native group. The small isolated variation (66,836–69,167 master cycles) comes
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

The shipped `FixBugs = 0` path is the authority. Completion/restore is audited
as the same FM5 owner lifetime ending after the 37-frame source duration; an
overlap replaces the owner before that completion path, while an isolated group
allows the key-off/restore path to complete.

Deterministic A/B compact result:

- JSON SHA-256 `3ed28d9a1bc456a92446da311332e03056a07043edd235b285afdf211a743a5f`;
- terminal payload `c0eae0d4b65a6f1a8b3da93b501b377691b883bdf2b41bd3383e470851e74f43`;
- raw writes `87265ee3d08d91128faef6338695c426f6697df127b0431b16d77ade6210a509`;
- six-column projection `06888e2d89794879947fa2256aaf3bb9ae0244e0c147f400dd6363bc4d42125c`;
- FM5 samples `10ccfc1aa351f34e9b7be4b7eab44fb8003e73f8bef15bff3ba4218c50285534`.

## Sonic 2 source calculation

S2 uses its own Z80 driver. The first isolated FM5 group's successive shipped
T-state gaps are:

`252, 258, 258, 258, 278, 258, 258, 258, 258, 258, 258, 258, 258, 258,
258, 258, 258, 258, 258, 258, 263, 394, 278, 306, 306, 532, 325, 940,
233, 278`.

Their sum is 9,029 T-states; at 15 master cycles per Z80 T-state this is
135,435 master cycles, matching every isolated and overlapping captured
RingRight group. The capture contains 28 admitted FM5 groups: 14 isolated and
14 overlap.

The path audit covers:

- `zWriteFMIorII`, `zWriteFMI`, and `zWriteFMII` at
  `s2.sounddriver.asm:343-389`;
- `zFinishTrackUpdate` at lines 947 onward;
- `zSetMaxRelRate` and its four-operator write loop at lines 2090 onward;
- `cfSetVoice` / `zSetVoice` at lines 3271-3432, including banked voice reads,
  stored carrier TL, panning, frequency, and key-on;
- `cfStopTrack` at lines 3514 onward, including key-off, bank switch back to
  music, and active music-voice restore.

The driver is built with `FixDriverBugs = fixBugs = 0`; the shipped NOP/busy
wait and un-fixed branches are retained. All audited groups report zero DMA
stall markers. Banked reads therefore use the reviewed GPGX uncontended path;
the audit does not claim parity during simultaneous VDP DMA.

Deterministic A/B compact result:

- JSON SHA-256 `17be36378af251a6d1a00ca66c268ac607fcc710b75e0445f0dd6afc009f8777`;
- terminal payload `51d701514d615f86e5c2a6a2619921f7fdd7f1d064a2de335826884146193e02`;
- raw writes `7198cd1246f6653bc52ee087ffa2f4f626e849d318510a48674efa1932d3ca71`;
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
