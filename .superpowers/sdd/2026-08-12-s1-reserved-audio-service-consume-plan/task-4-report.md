# Task 4 report: real S1 reserve/consume frontier

## Status

Complete for the reviewed Task 4 evidence scope on current managed base
`2b123f4bc`. The exact row-8775 transaction gate is GREEN. The terminal probe
stops at the next exact reference-observer frontier, movie row 12525 at M68K
`$72C24`; no semantic fix was attempted and no capture was published.

## Corrected evidence

The stale release-at-END claim is retracted. Its complete preserved RED log,
`target/audio-parity/native/action11-final-row8775-repro.log`, is 68 lines and
has SHA-256
`07f9a1965b4a9c1e82f56193975ecbf00f290a8c14de5e497dcdb423099b8e24`.
It proves `$71BB2` fails while kind 6 remains the active root. The old
12/13/20/21 released-root story and terminal-green claim are not acceptance
evidence.

The exact regular-file inputs were used in place without copying, renaming, or
symlinking:

- Sonic 1 World REV01 `Sonic The Hedgehog (W) (REV01) [!].gen`, SHA-1
  `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`.
- `sonic1-complete-withemeralds.bk2`, SHA-256
  `f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`,
  225101 rows.

The old install RED with the revised manifest failed configuration with status
`-3`, as expected. Its log is
`target/audio-parity/native/task4-old-action11-real-red.log`, SHA-256
`cb703f0f753ffd58e3c6d0c25c3957be580db6a716aafda4dcc154de460c170c`.

## Disposable revised core

A create-new diagnostic core/install was built from locked input identity
`36dde84c81429343b2f4425ff66c04f8fbdf54bcaf42a2459e68c52f95e9a0d4`.
All six native selftests passed. No committed identity, capability, recipe, or
artifact-lock file was changed by Task 4.

- Committed patch SHA-256:
  `fefab1d5f69ff1657d14eb744e3c4b57c0eefa351ee236c3166fe2614faa8504`.
- Raw core SHA-256:
  `ae8d7176bc283a1ec8db288eb634c31bcdfb4b610280a458cefb407427394e35`.
- Compressed core SHA-256:
  `a383b3762fc8000a0354b54397832208728863f559905ec6e8d163e66ab1bb35`.
- Diagnostic Build ID: `23efb896258c515d`.

## Row-8775 GREEN

Command:

```text
OPENGGF_S1_AUDIO_PREFIX=1 \
S1_ROM_PATH=<exact REV01 file> \
S1_AUDIO_BK2_PATH=<exact committed all-emeralds BK2> \
BIZHAWK_HOME=target/audio-parity/native/task4-reserve-consume-diagnostic-install \
tools/bizhawk-headless/test.sh \
  --filter 'S1CompleteRunAudioReferenceCaptureTests consume one deferred child begin during row 8775 wait service' \
  --jobs 1
```

Result: PASS. Log:
`target/audio-parity/native/task4-row8775-2b123f4bc-green.log`, SHA-256
`7c4e6da896a56f896e0e36befaa44335ede52715c073fd858b7a419b3a36b761`.

The gate proves the complete reviewed transaction: root kind-6 BEGIN `$003A`;
three distinct and strictly ordered marker-value-4 retries at `$71B4C` with
identical `A7=$FFFDB2` and return `$000B64`; exact `$71B82` consume and fresh
depth-1 kind-4 child BEGIN; `$71BB2` owned by the child; depth-1 child END at
`$71C4C`; no manifest M68K callback between child END and the kind-6 END at
`$0077`; then an adjacent fresh root DPCM BEGIN. Later DPCM work in the same row
is retained in the inventory and is not conflated with the tail-produced begin.

Three inherited assertion assumptions were corrected from authoritative output:
`$71BB2` event kind 10 is produced by configured action 7; the tail-produced
DPCM begin is selected by `waitEndOrdinal + 1`; and the child `$71C4C` close is
selected by depth-1 ancestry so the prior root close at ordinal 12 cannot match.
These are test-evidence corrections only, not production semantic changes.

The final synthetic class gate passed 41 tests with the ROM-backed host and
opt-in real gate skipped as intended. Its log is
`target/audio-parity/native/task4-s1-synthetic-final.log`, SHA-256
`f6a40b5a86479374848e8828d61fcc17fe53188ca9257661d0993351083d2d53`.

## Terminal frontier

The same command with `OPENGGF_S1_AUDIO_TERMINAL_PROBE=1` stopped at movie row
12525 with:

```text
first_fault=5:2:72c24:2:2:0:4
```

At `$72C24` (`cfStopSpecialFM4`), kind-2 DPCM token 6 is the active depth-1
child of root kind-4 token 5. The current conditional-close hooks do not admit
that topology. Log: `target/audio-parity/native/task4-terminal-2b123f4bc.log`,
SHA-256
`22dccba2f6c221fdbfc8428133182e07cdebd61577865ee83a3b506e6976942c`.

Task 4 stops here for a new reviewed frontier round. It does not broaden the
conditional-close contract, claim `Complete(225101)`, claim reference-vs-OpenGGF
semantic MATCH, or publish any complete or partial capture.
