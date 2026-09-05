# SMPS parity cycle 5: S3K SFX header-order admission

Status: local candidate, not delivered. Full-suite and post-merge verification
are pending; full-game audio parity and authenticity remain open.

## Source and representation

Retail `zSFXTrackInitLoop` calls `zGetSFXChannelPointers` before copying the
current header, then finishes the pass with IX naming that newly initialized
track (`docs/skdisasm/Sound/Z80 Sound Driver.asm:1997-2103`). On the next PSG
header, shipped `fix_sndbugs=0` calls `zSilencePSGChannel` through that stale IX
and then emits unconditional `FF` (:2109-2165). Thus PSG2 to PSG1 is
`FF BF FF`, and the reverse is `FF 9F FF`. An intervening FM/DAC header replaces
IX and prevents a stale PSG latch.

S3K still services SFX in fixed channel-RAM order. A constructor-derived,
immutable index projection retains ROM header order for admission only and
resolves to the same live Track identities after snapshot restoration. It adds
no production mutable temporal state. The public test/manual `addTrack` seam
appends to that projection so its existing behavior is not silently lost. The
shipped-header reachability test proves every S3K SFX PlaybackControl header is
exactly `80`; arbitrary synthetic flag forms are outside this bounded repair.

## Red, corrections, and mutations

- Initial red: consecutive PSG headers produced `FF FF` instead of
  `FF BF FF` (`target/sfx-header-order/red.log`).
- The first implementation walked `getTracks()`. S3K sorting reversed the
  controlled PSG pair and erased an FM separator; those failures are retained
  in `target/sfx-header-order/green-attempt.log`.
- Omitting the previous-header silence fails with `FF FF`
  (`target/sfx-header-order/mutation-omit-previous.log`). Using the current
  header instead fails with `FF 9F FF`
  (`target/sfx-header-order/mutation-current-header.log`).

## Focused evidence

- Synthetic ordering, raw `80/A0/C0` mapping, and snapshot identity: 9 tests,
  no failures/errors/skips (`target/sfx-header-order/final-unit.log`). The test
  also pins fixed channel-RAM runtime order and parsed-SFX `addTrack` behavior.
- ROM-backed Skid header order: 1 test, no failures/errors/skips
  (`target/sfx-header-order/skid-rom.log`).
- Combined restored hard-oracle run: 11 tests, no failures/errors/skips
  (`target/sfx-header-order/restored-focused.log`). The frontier advances from service
  2012 event 1 to service 2357 `MUS_FM4.overridden` (`false` vs `true`).
