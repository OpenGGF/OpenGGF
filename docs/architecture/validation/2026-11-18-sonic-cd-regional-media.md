# Sonic CD regional media

OpenGGF identifies the USA, European and Japanese retail discs by the normalized
identity of all 35 tracks. The recognized releases are Redump 29188, 8302 and
1632, respectively. `CdRetailReleases` holds their content records.
`CdHostedMedia` admits the USA release for gameplay.

## Identical FM bank

The three verified discs contain the same 7,514-byte `MDINIT.MMD` file. Its
SHA-256 is `abd4735cb55aac797402d611b6d732276f02f1394ef39e7e1af6cd9dbd6fdf7e`.
The 6,870-byte installed bank at file offset `0x1DA` has SHA-256
`23f3df846b228c8de5b55cb6503318f05df1a49c5bdfe282ceecf2502ce5bc99`.

`CdFmSoundAssets` requires a whole-disc match, agreement between the disc
header and that match, and the existing file, relocation and bank checks. The
source retains the matching release's identity. The regional tests open the
user's own CUE sheets, resolve the jump effect through `SonicCdAudioProfile`
and measure samples from the shared audio producer. This exercises FM loading
and the existing synthesis path. `CdFmSequencerConfig` still uses the NTSC YM
clock. PAL cadence and native audio parity remain unverified.

## Shared PCM banks and Past playback

Independent `RawIso` reads found matching ISO locations, byte lengths and
whole-file SHA-256 hashes for all nine PCM banks on the three authenticated
discs. These are `SNCBNK1B.BIN`, `SNCBNK3B.BIN` through `SNCBNK8B.BIN`,
`SNCBNKB1.BIN` and `SNCBNKB2.BIN`.

`TestCdAudioLivePath` verifies the whole-disc match and header agreement before
loading Palmtree Panic Past (`R11B`) through `SonicCdAudioProfile`. It checks
that the sub-CPU loads `SNCBNK1B.BIN` and that `SCMD_PASTMUS` produces audible
output with CDDA idle. The test compares 90 frames of stereo samples at 60
presentation frames per second against a separately opened USA disc and a
fresh audio session at the same output sample rate. It requires exact sample
equality and checks that `SCMD_FADEPCM` brings the output below the test's
audibility threshold.

The comparison covers one bank's Past music and fade on the engine's existing
audio path. The other eight banks have byte-level evidence here. Regional
command routing beyond this case, effects, the differing CDDA soundtracks,
PAL cadence and native mixed-audio parity remain outside this qualification.

## What regional boot depends on

The raw ISO observations below were made on the split CUE sets using
`RawIso` in [stage_inventory.py](../../../tools/soniccd/stage_inventory.py).
The probe compared full file SHA-256 hashes, plus byte ranges listed in
[program-assets.tsv](../../../src/main/resources/soniccd/program-assets.tsv).
It read the discs in place and wrote no media.

| Owner | Observation and remaining work |
| --- | --- |
| `CdTitleAssets`, `CdProgramData`, `CdTitleProgram` | None of the 32 USA `TITLEM.MMD` catalogue ranges match Europe or Japan at the USA offsets. `TITLES.BIN` matches both. Region-specific asset tables and title behavior need qualification before even the title preview can use either disc. |
| `CdBackupRamAssets`, `CdInitialSaveAssets`, `CdBackupRamHost` | USA `BRAMINIT.MMD` is 10,998 bytes. Europe is 10,164 and Japan is 10,250. All three `BRAMSUB.BIN` files match, with Europe's file one ISO block earlier. Qualify the initial-save tables and save layout before admitting regional saves. |
| `CdProgramFlow`, `CdStageSelection`, `CdSystemProgramSource` | `IPX___.MMD` and `STSEL_.MMD` match across the three discs. The EU and JP `SPX___.BIN` files match each other and differ from USA. The game flow currently selects a USA catalogue. |
| `CdStageLoader` and the zone providers | All 70 Japan and Europe stage program hashes differ from their USA counterparts. A stage header and filename match before byte decoding gives no assurance that the USA asset offsets or rules apply. |
| `CdTimeWarpPrograms`, `CdWarpAssets`, `CdR3PcmSource`, `CdR4PcmSource` | `WARP__.MMD` and the PCM bank files match on all three discs. The combined warp capability also reads `R11B__.MMD`, which differs. Regional warp commands and timing still need qualification. |
| `CdSpecialStageSonic`, special-stage assets | The USA, EU and JP `SPMM__.MMD` files all differ. Europe's `SPSS__.BIN` differs from the common USA and JP file. The movement owner explicitly implements the non-European arithmetic. |

The existing regional track tests check ISO and CDDA reads.
`TestSonicCdRetailMedia` authenticates the three complete images.
`TestCdDiscRefusal` checks that the launcher still requires USA media and
attaches no saves when it refuses an explicitly selected European disc.

## Reproduce

Set `soniccd.media.root` to the parent directory of the USA, Europe and Japan
CUE folders. Run the focused checks with the queued wrapper.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dsoniccd.media.root=<media-root>" \
  "-Dtest=TestCdFmSoundAssets,TestCdAudioLivePath,TestCdDiscRefusal,TestSonicCdRetailMedia,TestSonicCdDiscMedia,TestSonicCdProgramMedia,TestCdFmPlaySound,TestCdFmTrackUpdate,TestCdFmSoundArbitration" \
  test -B
```
