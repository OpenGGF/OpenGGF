# Sonic 1 GHZ1 Gameplay-Audio Timeline Result

**Date:** 2026-08-09

**Result:** Deterministic valid mismatch at the gameplay-request boundary

## Run and identities

The hardened runner completed two independent captures from each producer and
returned its documented mismatch exit code `3`:

```bash
/usr/bin/bash -p tools/audio/run_s1_ghz1_gameplay_audio_timeline.sh \
  --rom '../../Sonic The Hedgehog (W) (REV01) [!].gen' \
  --bizhawk-home '../../docs/BizHawk-2.11-linux-x64'
```

The final local, ignored evidence is preserved in
`target/audio-parity/s1-ghz1-gameplay/run.AhiDZDUs/`.

| Input | Verified identity |
|---|---|
| ROM | Sonic 1 World REV01; SHA-1 `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`; CRC32 `afe05eee` |
| BK2 | Complete-with-emeralds movie; SHA-256 `f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`; 225,101 rows |
| Reference | BizHawk 2.11, Genesis Plus GX |
| Segment | BK2 frames `[860,4975)`; 4,115 records; GHZ `$81` baseline |

| Producer | SHA-256 for each duplicate | Bytes each | Requests |
|---|---|---:|---:|
| BizHawk reference | `8ab8fd1b052598495e6faecaf505758808e0015ec7762d45225b28baba073e59` | 2,050,831 | 154 |
| OpenGGF | `337959a8aa9ba4122041cd5700b1fd64069bd4d1315129433244279183815948` | 2,058,662 | 175 |

`cmp` passed for both duplicate pairs. Detailed JSONL, logs, and reports remain
ignored and are not repository artifacts.

## Request inventory

This is a non-reconstructive inventory: it records counts and first/last BK2
frames, not the ordered per-frame payload.

| ID | Source name | Reference count / span | OpenGGF count / span |
|---|---|---|---|
| `$87` | `bgm_Invincible` | 1 / 3219 | 1 / 3218 |
| `$88` | `bgm_ExtraLife` | 1 / 3699 | 1 / 3698 |
| `$8E` | `bgm_GotThrough` | 1 / 4386 | 1 / 4385 |
| `$A0` | `sfx_Jump` | 22 / 959–4292 | 24 / 958–4291 |
| `$A1` | `sfx_Lamppost` | 1 / 3565 | 1 / 3564 |
| `$A4` | `sfx_Skid` | absent | 4 / 3969–3972 |
| `$B5` | `sfx_Ring` | 64 / 973–4109 | 36 / 977–3944 |
| `$B9` | `sfx_Collapse` | 3 / 2494–2739 | 3 / 2493–2738 |
| `$BE` | `sfx_Roll` | 1 / 3502 | 1 / 3501 |
| `$C1` | `sfx_BreakItem` | 11 / 1150–4077 | 14 / 1149–4076 |
| `$C3` | `sfx_GiantRing` | 1 / 4329 | 1 / 4328 |
| `$C5` | `sfx_Cash` | 1 / 4795 | 1 / 4794 |
| `$C9` | `sfx_Bonus` | 3 / 4295–4305 | 3 / 4294–4304 |
| `$CC` | `sfx_Spring` | 3 / 1240–2403 | 3 / 1239–2402 |
| `$CD` | `sfx_Switch` | 29 / 4679–4791 | 29 / 4679–4791 |
| `$CE` | `sfx_RingLeft` | absent | 37 / 972–4108 |
| `$CF` | `sfx_Signpost` | 1 / 4262 | 1 / 4261 |
| `$D0` | `sfx_Waterfall` | 11 / 1275–2107 | 14 / 1274–3898 |

## Contention evidence

Both acceptance classes are present in both complete streams; otherwise the
comparator would have returned capture failure instead of the semantic
mismatch.

- Music/SFX takeover and restore: reference `$A0` request ordinal 1 takes PSG1
  from GHZ `$81` at frame 959, and `$81` owns PSG1 again at frame 985. OpenGGF
  observes the same ordinal-1 ownership transition at 958 and restoration at
  984.
- SFX while another SFX is active: reference `$B5` request ordinal 11 takes
  FM4 from `$CC` ordinal 10 at frame 1250. OpenGGF `$CE` ordinal 11 takes FM4
  from `$CC` ordinal 10 at frame 1249.

## First mismatch and stop boundary

The first semantic result is `REQUEST_EXTRA`, frame 958 request 0. OpenGGF
submits `$A0` (`sfx_Jump`) on frame 958; the REV01 driver selects and consumes
the same queued PSG1 request at `PlaySoundID` on frame 959. The BK2's first B
press is row/frame 958. In the ROM,
`Sonic_Jump` reads `v_jpadpress2` and calls `QueueSound2`; OpenGGF's
`PlayableSpriteMovement.doJump()` calls `AudioManager.playSfx(GameSound.JUMP)`.
Both baselines own ordinal 0 and both `$A0` requests carry ordinal 1 with the
same PSG1 arbitration and owner identities; the mismatch is therefore only
which frame contains that semantic request, not an ordinal incompatibility.
This is request scheduling at the gameplay/input-to-audio boundary, so the
planned rule requires stopping before `PlaySoundID`, role arbitration,
presentation ownership, libvgm chip behavior, or chip-port ordering. No
gameplay timing or audio-driver behavior was tuned against this movie.

Before the observer corrections, no semantic comparison was available: the
reference producer stopped on shipped driver-RAM clearing and non-local SMPS
returns. The final lifecycle classification includes `$71BD4`, the DAC
continuation after `cfStopTrack`; its normal later `$71C4C` return closes the
same tick exactly once, while a target outside the DAC/track continuations
still closes immediately. After these source-derived lifecycle/serialization
corrections, all four captures complete and the request mismatch above is
stable. OpenGGF's capture producer also reserves ordinal 0 for the shared GHZ
baseline without altering driver-observer admission ordinals. There is no
post-behavior-fix result because a behavior fix was not warranted within this
audio task.

## Verification and listening checklist

The explicit driver snapshot, fade, chip observer, YM2612 GPGX parity,
timeline, reducer, comparator, and authority suite ran 86 tests with zero
failures or errors and one property-gated capture skip. The real property-gated
capture was exercised twice by the hardened runner instead.

Structural evidence does not establish audible correctness. Human review
should still check:

- jump onset against the input and visible take-off;
- ring stereo alternation, especially `$B5` versus `$CE`;
- waterfall/SFX and SFX/SFX steals for clicks or stuck voices;
- invincibility and extra-life takeover/restoration;
- switch, signpost, and act-clear transitions;
- YM2612/PSG balance and port-order-sensitive attacks.
