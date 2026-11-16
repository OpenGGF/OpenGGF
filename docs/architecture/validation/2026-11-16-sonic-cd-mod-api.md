# Sonic CD Mod API/runtime host

Implementation branch: `feature/ai-scd-mod-api`, based on fetched
`origin/develop` `3a868d33d9cb59137d55b50eb4492c5b137e2792`.
Owner: implementation agent, OpenAI Codex GPT-6.1-sol. No delegated workers.
Stageplay, route tapes, editor projects and content import are separate lanes.

## Contracts

- `scd` patch manifests and `any` plans use the existing registration transaction,
  derived ownership and callback fault boundary. Native decoded placement patches
  retain `CdLevel`, geometry and native ring ownership.
- Native run character admission is module-owned. Hosted runs open disc media,
  rather than sending the selected CD game through cartridge detection.
- File-addressed art goes through `GameDataSource`/`HostedGameMedia`, then the
  existing bounded decoders. Scene program views use exact ISO names and
  file-relative offsets. No ROM/disc asset is committed or replaced by standalone
  assets. Program object art publishes only after owned preparation succeeds.
- Music overrides address native sub-CPU music commands, including CDDA and Past
  PCM music. Native music suppression preserves FM and PCM effect output. Stop,
  fade and CDDA pause/resume requests control the active streamed replacement.
- CD art overlays keep the native program owner available via module services;
  drawing still uses the resolved art provider. Native goal owners raise only the
  shared observer signal, never the ROM end flag used by player physics.
- The descriptor remains unpublished candidate `0.7.0`, with no published
  baseline. `ModApiVersion` describes the additions and the normalized `0.7` pin
  is regenerated using `ModApiSignatureSurface --snapshot`.

## Explicit limits

The donor compositor in `CrossGameFeatureProvider.loadPlayerSpriteArt` obtains
native host animation families with `RomByteReader.fromRom(romManager().getRom())`.
CD's families and scripts are in the selected stage program. Until that compositor
has a stage-backed host-family adapter, the CD module refuses cross-game hosting
through `supportsCrossGameHost`; initialization clears previous donor state and
reports an `IOException`. CD is not a donor either. No missing-ROM fallback is used.

Flat authored CD zones admit owned objects and one collision path with 16x16-block
terrain. Native objects/rings require their stage's program/PLC/state context and
are refused there; a native decoded-level patch preserves that context. Stock
insertion anchors, authored water/deformation and hosted arenas are not exposed.
Common CD provider art keys are catalogued; stage-specific sheets remain expert
art-provider overrides. Program object art intake supports direct mappings, not
DPLC flattening. Scene character decoding retains native CD word-count DPLCs.

## Evidence and rejected approaches

The initial manifest/scene tests failed on CD refusals. A subsequent real disc boot
found an owned addition in the decoded inventory which never updated: simply
appending entries is wrong for CD's stored-order spawner. The placement encoder
now merges owned additions before the native scan boundary, preserving native
relative order and placement identities. The same compiled example mod binds
stock PP monitors and adds a visible marker using disc-loaded art.

A failing goal-post test showed that accepting a CD run did not make its finish
observable. The existing native timer-stop sites now emit the shared completion
signal, with no native physics/end-flag change.

Booting a program-art decorator exposed native CD art-owner assumptions:
`CdHud.writeDigit` cast the resolved overlay to `CdObjectArtProvider`. Native art
consumers now ask the module for their program owner; the integration test waits
for the title card to release control naturally rather than unlocking the player.

The broad-catch guard rejected extending scene art's old log-and-continue handlers.
Those handlers were removed and their legacy inventory rows retired; source
failures now propagate. A comment added to the release descriptor was rejected by
its strict key=value parser and removed; candidate version/topology are unchanged.

## Verification record

All Maven execution uses `tools/testing/maven_queue.py -Dmse=off`; GL boots select
`-Dopenggf.headless.gl=surfaceless-egl`. The wrapper found `soniccd.disc.path` and
reported no missing media. Seven Past-music direct native comparisons and thirteen
SSZ boss native comparisons require optional local recordings; their skips do not
mean disc-backed runtime coverage was skipped.

Focused selections include manifest/registration/fault boundaries, CD runtime and
program art, flat zones and policies, music metadata and output separation,
scene-run admission, all CD payload loading, native common goal objects, CD audio
live path/FM queue/session isolation, stock art catalogs, Mod API pin/surface/
release/runtime policies, and named broad-catch/per-game/game-policy guards.
The API/guard repair selection passed 48 tests with no failures or skips.
The final focused selection passed **41 tests, zero failures/errors/skips**:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestSonicCdModRuntime,TestCdModMusic,TestCdModZones,TestStockArtOverrideCatalog,TestSonicCdCommonObjects2,TestModApiSignatureSurface" test
```

The separate CD output-separation selection passed all five `TestCdAudioMixGains`
tests, including replacement suppression preserving FM/PCM effects. The smoke
outcome will be recorded after completion. The smoke selection is 4,976 classes with two workers, bounded to smoke
plus guards, excluding exhaustive/trace/native profiles. Its command timeout is
40 minutes of execution per invocation; queue wait is separate. New/worse
failures block delivery and are compared with the same test identity on the base.

Full-tree citation checking reports inherited stale/unavailable references and
local disassembly checkouts different from the pinned references. It is not a
clean whole-tree citation claim; staged-citation validation applies at commit.

## Restore correction before final smoke

The first smoke attempt was deliberately stopped for a newly identified audio
selection restore defect: `VERDICT INCOMPLETE`, 8/100 ordinary batches, 3,889
tests, zero failures/errors, three allowed skips, guards not run. The owned runner
stopped Maven and cleaned temporary output; inspected diagnostics were acknowledged.
A new regression test then failed because restoring the CD audio state from
before an override still routed a later pause to that replacement.

The correction keeps suppression in the existing CD sub-CPU audio `State`, with
compatibility constructors, and removes the duplicate flag from the audio
profile. This is confined to the existing CD audio owner; no general rewind or
widescreen implementation changes. Native-versus-streamed selection is restored,
while configured mix gains remain presentation configuration. The correction's
focused selection passed **35 tests, zero failures/errors/skips**:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestCdModMusic,TestCdAudioMixGains,TestCdAudioLivePath,TestSonicCdModRuntime,TestModApiSignatureSurface" test
```

## Follow-up seams

Carry the source capability, its ownership and exact program identity through
Stageplay/editor/import preparation. `MediaSceneArt` provides detached disc views;
`SceneArt.program` is the creator-facing file view. Offline library construction
still uses cartridge readers and needs a source-aware extension. Preserve CD's
zone/act/period projection rather than inventing cartridge addresses or treating
an empty `GameDataSource.rom()` as standalone content. Native terrain/Stageplay
kits, donor host-family composition and route fingerprints require their own
source-aware owners and verification.
