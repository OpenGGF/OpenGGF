# Original S3 and standalone S&K audio bindings — 2026-10-28

This is a prerequisite of `s3k-competition:1`, `s3k-standalone:2` and
`s3k-standalone:1`, not an additional counted TAS mechanism. Parent owns module
composition and the combined support assessment.

## Resource ownership and native proof

The supplied original `${HOME}/code/projects/OpenGGF/s3k.gen` is served
through existing immutable catalogue windows. S&K is `[0,$200000)`, CRC32
`0658F691`, SHA-1 `88D6499D874DCB5721FF58D76FE1B9AF811192E3`; original S3 is
`[$200000,$400000)`, CRC32 `9BC192CE`, SHA-1
`75E9C4705259D84112B3E697A6C00A0813D47D71`. No ROM file is copied, linked,
patched or supplied by a disassembly. Addresses below are relative to the logical
cartridge view.

The pinned research source is skdisasm `8b2cf3c4178f2382f844da67798de4befc50c5e0`,
read with immutable `git show` from the existing main optional repository. Its
inherited checkout and dirty/unpopulated development copies were preserved.

- `LevelMusic_Playlist` (s3.asm:5681-5705) is at original S3 `$4680`. Entries
  28–37 contain `$20,$20,$21,$21,$22,$22,$23,$23,$24,$24`.
- `z80_MusicBanks` (Sound/Z80 Sound Driver.asm:2841-2866) and
  `z80_MusicPointers` (Sound/Z80 Sound Driver.asm:4585-4610) select the original
  music resources. Original S3 stores its driver and tables raw at `$E6000`,
  music banks at driver-relative `$B48`, pointers at `$161A`, 50 songs.
  `bankswitchToMusicS3` (Sound/Z80 Sound Driver.asm:309-322) names the bank's
  low four bits plus the fixed original S3 region; the loader resolves the ROM
  data address directly, without modelling bank-register writes.
- Original S3 title `$25` -> `$DE18F`; menu `$2D` -> `$DF5E4`; Azure Lake `$20`
  -> `$D99F7`, Balloon Park `$21` -> `$DA4FD`, Desert Palace `$22` -> `$DB0EC`,
  Chrome Gadget `$23` -> `$DC324`, Endless Mine `$24` -> `$DDA47`.
- S&K's Kosinski-compressed driver and additional data are at `$F6960` and
  `$F7760`. It has 51 music-table entries, driver-relative bank table `$B65`,
  music pointers `$1618`. Its title `$25` -> `$EF88E`, Knuckles intro `$1F`
  -> `$EF5A3`, MHZ1 `$0F` -> `$E8AFE`, MHZ2 `$10` -> `$E9106`.
- `z80_SoundDriverPointers` (Sound/Z80 Sound Driver.asm:4450-4465) names shared
  voices `$17D8`, SFX pointers `$167E`, modulation pointers `$130E`, PSG pointers
  `$1387`. Both original formats load this table block at `$1300`; S3 stores it
  raw at `$E7300`. `z80_ModEnvPointers` (Sound/Z80 Sound Driver.asm:4470-4478)
  contains 8 entries; `z80_VolEnvPointers` (Sound/Z80 Sound Driver.asm:4494-4501)
  contains 39. Each cartridge's own table block supplies its 35 universal voices.
- `DAC_Banks` (Sound/Z80 Sound Driver.asm:630-648) contains notes `$81..$C4`,
  after its dummy `$80` entry. S3's table is driver-relative `$DC`, S&K's `$D6`.
  Original S3 supplies 68 mappings. Standalone S&K supplies its 27 ordinary drums
  `$81..$9B` and `$B2/$B3`; other table entries name the absent S3 cartridge and
  are explicitly unavailable. Locked-on keeps both cartridges and its S&K `$B2`
  sample. No fallback bytes or manufactured silent samples are introduced.
- Original S3 `$81` descriptor `$E0088`: rate 4, 1918 DPCM bytes at `$E04A6`;
  `$9B` descriptor `$E810A`: rate 12, 1568 bytes at `$E81D7`; `$B2` descriptor
  `$F015F`: rate 22, 2120 bytes at `$F3465`. Independent nibble decoding gives
  decoded lengths 3836/3136/4240 and SHA-256 values pinned in the resource test.
- The SFX bank is `$F8000` in each cartridge; original S3 ring header `$FDE30`,
  voice `$FDE45`, jump header `$FE64C`. Independent voice bytes, PSG/modulation
  envelope values and universal-table SHA-256 are checked, separately from sound.

## Composition contract

`Sonic3kAudioResourceProfile.fromRom(actualOpenLogicalRom)` resolves exact 2 MiB
S3 or S&K views from their headers; 4 MiB S&K + S3 resolves `LOCKED_ON`.
Unrecognised, mismatched and closed layouts fail explicitly. Alternatively,
`fromLogicalRom(RomIdentity.S3/SK/S3K)` consumes an authoritative catalogue identity.
No game name, level, route, frame or captured world selects resources.

The root module must retain one `new Sonic3kAudioProfile(resourceProfile)` per
enum and resolve its current actual open logical ROM when selecting the profile.
Competition explicitly chooses `SONIC_3` with its original S3 resource view.
`new Sonic3kAudioProfile()` and `new Sonic3kSmpsLoader(rom)` preserve the existing
locked-on default. Explicit original profiles reject a mismatched cartridge.

`SONIC_3.musicId(nativeMusicByte)` is `0x100 | nativeMusicByte`, valid for native
`$01..$32`; SFX and commands are never prefixed. Thus title is `0x125`, menu
`0x12D`, arenas `0x120..0x124`; use the native playlist's byte at `$4680 + levelIndex`.
S&K keeps unprefixed IDs (title `0x25`, intro `0x1F`, MHZ1 `0x0F`, MHZ2 `0x10`).
Profile GameMusic cues, overrides, air-reset rules and original-S3 native mailbox
music requests use that same namespace. Program descriptors retain it for restore.

## Validation and limits

Initial workspace `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-9f1ee278`, base
`8945c0a789b27be57b2361c70f561f31872a76ca`, initial dirt empty, own live jobs none.
Branch renamed `bugfix/ai-tas-support-audio-20261028`; hooks installed. Resolver
prerequisite commit `30fb4fae0d684b274df14f566c36275ff2c2e8b7` is separable.

Java 25 build-only command `JAVA_HOME=/usr/lib/jvm/java-25-openjdk
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off test-compile -B`
passed (1m30 Maven-reported execution; no test execution). This compiled the API,
profile/loader WIP and four resolver contracts; it did not qualify audio output.

Commands below use `${HOME}` for the actual user-home prefix observed at execution;
the tests received the existing original ROM through its resolved absolute path.

The first focused command completed on resolver commit `30fb4fae0` plus the
owned profile/constants/loader and regression WIP, with production source held
unchanged through execution:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Ds3k.rom.path=${HOME}/code/projects/OpenGGF/s3k.gen \
  '-Dtest=TestSonic3kAudioResourceProfile,TestSonic3kAudioProfile,TestSonic3kCartridgeResources,TestSonic3kCartridgeAudio,TestS3kLevelEntryAudioDeviceRestore,TestS3kBlueSphereAudioRom,TestSonic3kCoordFlagParity' test -B
```

Result: 78 tests, 1 failure, 0 errors, 0 skips; Maven execution 1m31,
queue wait 2,874s (47m54s), invocation wall approximately 49m25s plus unmeasured
wrapper overhead. Class counts: cartridge audio 29, cartridge resources 4,
profile 3, resolver 4, locked-on blue sphere 8, locked-on device restore 1,
coordination flags 29. All music PCM, restore/reused-boot and native asset checks
passed. The sole failure was the new ring SFX test's admitted-ID assertion:
`zPlaySound_CheckRing` (Sound/Z80 Sound Driver.asm:1919-1929) resolves the first raw
`$33` request to `$34`, matching the existing engine ring-alternation contract.
The test now checks that exact speaker selection and toggle; production code
was not changed. S3/S&K VolEnv_26 lengths (101/99) were also pinned explicitly.

The narrow affected follow-up completed:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Ds3k.rom.path=${HOME}/code/projects/OpenGGF/s3k.gen \
  '-Dtest=TestSonic3kCartridgeAudio#ordinaryCompetitionPlayerItemMenuAndResultsEffectsProduceSound,TestSonic3kCartridgeResources,TestAudioManagerRawRingRequestAlternation' test -B
```

Result: **21 tests passed, 0 failures, 0 errors, 0 skips** (12 SFX output
witnesses, 4 cartridge resource cases, 5 existing ring controls), Maven execution
37.087s, queue wait 317s (5m17s), invocation wall approximately 5m54s plus
unmeasured wrapper overhead. Completed at 2026-10-28 19:48:39 UTC. The first run
completed at 19:41:35 UTC. The unchanged prior passes plus this affected rerun
cover 83 distinct cases across two runs; this is not a single 83-case suite run.
The ring fixture failure is resolved, with the exact native speaker/toggle and
audible PCM now required. No remaining failure or skip was observed.

The delivered production audio subtree is Git tree
`d76a795dbe611cadcfb62dd2059f78af125722f6`
(`src/main/java/com/openggf/game/sonic3k/audio`), against pre-task base
`8945c0a789b27be57b2361c70f561f31872a76ca`. It was unchanged between both commands.
Final affected fixture blobs are `77336d607d4278bf2adb0b8ad8103625b36581b8`
(`TestSonic3kCartridgeAudio.java`) and `0f3eb302cf5edecebad6c83f18e26a8983611059`
(`TestSonic3kCartridgeResources.java`). All other source/test changes were tested
by the first command. This records exact tested source without a pass cache.

The native-resource and no-device output prerequisite is qualified within the
bounds below. Root-module composition, combined smoke and domain gates remain
parent-owned. Existing passed coverage was not repeated as an integration ritual.

Tests require actual native-compatible PCM rendered through the existing accurate
chip synthesis and no-device capture, not acceptance by NullAudioBackend. Twelve 36-frame
song cases cover original S3 title/menu/all five arenas, S&K title/Knuckles/MHZ1/2,
and a locked-on AIZ control. Twelve isolated 30-frame SFX cases cover ordinary player,
item, menu and results sounds. Three cartridge restore tests compare sounding
forward output byte-for-byte and reject a one-frame-shifted control. Reused boot
and original-S3 one-up override controls cover source/profile identity and restoration.

This is bounded resource/output qualification, not a waveform-parity or exhaustive
audio campaign. It adds no CPU/Z80 execution, virtual bus/register/beam/cycle model,
trace hydration or external-emulator runtime dependency. Existing SMPS sequencing
and chip synthesis remain the owners. Original S3 PAL compensation also differs: retail `fix_sndbugs=0` reloads 6,
where S&K reloads 5 (`zVInt`, Sound/Z80 Sound Driver.asm:488-495). The existing
shared `EXTRA_FULL` mode reloads 5, so original-S3 PAL cadence remains an explicit
inherited limit; the focused sound witnesses exercise NTSC.

Original S3 SEGA PCM mailbox timing differs
from S&K's native queue prologue/epilogue (`zPlaySEGAPCM`, Sound/Z80 Sound Driver.asm:
4372-4424); that pre-existing semantic difference is outside title/menu/competition
and MHZ sound qualification here. Its ROM sample binding is shared in format and
offset; this work does not claim exact original-S3 SEGA command/queue parity.
Parent owns combined smoke and the four S3K domain gates; no broad/full-suite run
is submitted by this worker.


## Parent composition correction — 2026-10-28 23:46 UTC

Resumed the clean retained audio branch at `5d57120de` with no live validation
jobs, then fast-forwarded to parent `d904596bb5eb83d042ea02e1bebb686aeec9a4fe`.
There were no conflicts. The parent source includes upstream `e2ff` native-code
support/fallible world retirement and the common DEZ art provenance correction;
those changes are preserved. Only the S3K module audio selection, its precise
composition tests and this appended record are changed here.

The parent's 35,380-test broad run supplies the red evidence: this audio group
contains exactly **16 failed Stageplay cases: 15 errors and 1 assertion failure**.
The affected identities are `TestStageplayAudioLifecycle`'s
`authoredSongsSurviveNativeEntriesInOfflinePcm[6]` and
`nativeS3kCreditsRequestUsesTheCreditsAssetInBaseAndDonorProducers[1]`, eight cases
in `TestStageplayDonorEffectsRom`, two in `TestStageplayLiveAbilityReportingRom`,
and four in `TestStageplayUnifiedWorldRom`. They failed when a detached S3K donor
module tried to classify the ambient S1/S2 ROM. The separate sixteenth broad
error, the S2 common-art parser, is outside this audio group and is not changed.

`Sonic3kGameModule.getAudioProfile()` now checks native root identity through the
existing `NativeGameModuleLineage`. Only the actual current root consumes the
current `WorldSession.getDataSource().rom()`; a detached donor retains its declared
`LOCKED_ON` profile, including when the current world is another S3K module with
an original S3 or S&K cartridge. With no world, only the actual bootstrap root may
resolve the current available ROM. The existing enum cache retains immutable
profiles, not a ROM, world, or previous selection. Actual unknown/malformed root
sources still fail strict `fromRom` validation. There is no permissive catch,
new service, public/Mod API shape, or Stageplay/synth/sequencer change.

The receiving test rotates actual pinned world sources for all three cartridges
and reuses one module across world replacement. Changing ambient `RomManager`
alone cannot replace a pinned source. Additional controls reject an unknown owned
source, exercise pre-world bootstrap selection, and check foreign same-family
donor defaults. Five new no-device PCM cases cover all three owned cartridge roots
with S1 then S2 ambient ROMs, and detached locked-on donors in S1 and S2 worlds.
They require sounding native-compatible PCM, exact snapshot/forward output replay,
correct source descriptors, retained profile identity and scoped donor cleanup.

Both commands used Java 25, Lua 5.4, surfaceless EGL, the existing absolute original
S1/S2/S3K paths, no DISPLAY/WAYLAND_DISPLAY, and a mode-0700 task-owned XDG runtime
directory. HOME notation below expands to the actual original paths used:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  XDG_RUNTIME_DIR=${HOME}/scratch/agent-tmp/codex/tas-engine-support-2026-10-28/audio/runtime \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path=${HOME}/code/projects/OpenGGF/s1.gen \
  -Dsonic2.rom.path=${HOME}/code/projects/OpenGGF/s2.gen \
  -Ds3k.rom.path=${HOME}/code/projects/OpenGGF/s3k.gen \
  '-Dtest=TestSonic3kAudioModuleComposition,TestSonic3kAudioSourceComposition,TestStageplayAudioLifecycle#authoredSongsSurviveNativeEntriesInOfflinePcm+nativeS3kCreditsRequestUsesTheCreditsAssetInBaseAndDonorProducers,TestStageplayDonorEffectsRom,TestStageplayLiveAbilityReportingRom,TestStageplayUnifiedWorldRom' test -B
```

This completed at 23:44:42 UTC: 40 cases, 1 failure, 0 errors, 0 skips.
Queue wait was 28s; Maven execution was 1m34; invocation wall approximately 2m02
plus unmeasured wrapper overhead. The class counts were module composition 4,
source PCM composition 5, AudioLifecycle 10, DonorEffects 9, LiveAbilityReporting 6,
and UnifiedWorld 6. All 31 selected Stageplay cases passed, including the exact
16 parent-red identities, as did all five new PCM controls and three composition
cases. The one failure was a new bootstrap-fixture precondition: `resetAll()`
intentionally creates a world. The fixture now calls the existing
`SessionManager.closeGameplaySession()` before asserting the pre-world state;
no production change was needed.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  XDG_RUNTIME_DIR=${HOME}/scratch/agent-tmp/codex/tas-engine-support-2026-10-28/audio/runtime \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path=${HOME}/code/projects/OpenGGF/s1.gen \
  -Dsonic2.rom.path=${HOME}/code/projects/OpenGGF/s2.gen \
  -Ds3k.rom.path=${HOME}/code/projects/OpenGGF/s3k.gen \
  '-Dtest=TestSonic3kAudioModuleComposition#bootstrapRootResolvesCurrentCartridgeOnlyBeforeAWorldExists' test -B
```

The exact-method follow-up completed at 23:46:08 UTC: **1 passed, 0 failures,
0 errors, 0 skips**. Queue wait was 25s; Maven execution 34.077s; invocation wall
approximately 59.077s plus unmeasured wrapper overhead. Production classes were
reported up to date. The two runs cover 40 distinct cases with the local fixture
failure corrected; this is not a single green 40-case invocation.

Tested source is parent `d904596bb5eb83d042ea02e1bebb686aeec9a4fe` plus these owned
file blobs, delivered together with this record:

- `Sonic3kGameModule.java`: `2cf5d12cf60dc35ad9b596753aed242ee4bd6989`
- `TestSonic3kAudioModuleComposition.java`: `173955d6e0f24b1c4171c6546a4c4bc2f3efc10d`
- `TestSonic3kAudioSourceComposition.java`: `4865013300c17ad98a5dafa7acfc961e9c4e3fb8`

The production module blob was unchanged between the two runs. The original
83 resource/PCM cases above are reused unchanged: the audio-resource subtree is
still `d76a795dbe611cadcfb62dd2059f78af125722f6`. No broad/full-suite run or resource
change was made for this receiving correction. The inherited original-S3 PAL/SEGA
limits above remain. This scoped pass does not clear the parent's separate
stock-art errors or claim that the full integrated broad run is green.
