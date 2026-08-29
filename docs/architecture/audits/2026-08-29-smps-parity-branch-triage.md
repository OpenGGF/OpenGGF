# Triage: `feature/ai-smps-transaction-parity` re-landing audit

- Date: 2026-08-29
- Branch tip: `83723907d5705aa4cab9bda850982f5c434b3ff7` (`83723907d`, "Merge branch 'develop' into feature/ai-smps-transaction-parity")
- Merge base with `develop`: `7039887187948f86089e39a9f0fff0f17b26bdab` (`703988718`)
- `develop` at triage time: `d41de71a0140e82b073b0ddc9adda2095d82bc1b` (`d41de71a0`)
- Audited in worktree `.worktrees/ss-ring-pan` (`bugfix/ai-s3k-ss-ring-pan`, based on
  `feature/ai-audio-mix-calibration`, which carries the Nuked-OPN2 `Ym2612Chip` +
  `NukedOpn2` and the clean-room `PsgChip`).
- Commit count: `git log --oneline develop..feature/ai-smps-transaction-parity` = 95
  (93 non-merge + 2 merges).

The branch was reverted for 0.6 because it produced audible regressions its tests did
not catch. This audit classifies every commit by reading its diff, so the parts that
are still worth having can be re-landed in a deliberate order behind the listening
checklist.

## Classes

| Class | Meaning | Count |
|---|---|---|
| (a) | Chip-level, now moot because the new Nuked-OPN2 `Ym2612Chip` / clean-room `PsgChip` models it natively | **0** (see "Chip-level commits are not moot" below) |
| (b) | Driver / sequencer / presentation level; still relevant, not covered by the chip swap | **23** |
| (c) | Tooling, infra, tests, docs, merges only | **72** |
| (d) | Superseded or reverted wholesale within the branch | **0** whole commits; 6 partial supersessions are noted inline |

Class (c) breaks down as 2 merge commits, 16 docs-only commits (all rewriting the same
two files under `docs/architecture/{designs,plans}/audio/2026-08-13-cross-game-smps-semantic-transaction-parity-*.md`),
and 54 tooling/test commits (BizHawk headless C#/native observer, `gpgx-audio-*`
fixtures, and the Java `com.openggf.tools.audio.completerun` comparator plus its tests).
No (c) commit touches `src/main/java/com/openggf/audio` runtime code; verified by
listing every commit's `src/main` paths outside `com/openggf/tools/audio`.

### Chip-level commits are not moot

Three commits touch `audio/synth`. I checked each claim against the new core in this
worktree rather than assuming the chip swap absorbed them; none is covered.

| Commit | What it did | New-core state (this worktree) | Verdict |
|---|---|---|---|
| `a0e346e41` regional chip clocks | Added `VirtualSynthesizer.ChipClockProfile {NTSC, PAL}` (YM = master/7, PSG = master/15), `Ym2612Chip.setClockRates`, `PsgChip.setInputClock`, snapshot fields; `SmpsDriver.setRegion` switches the profile. | `Ym2612Chip.java:90-93` hard-codes `MASTER_CLOCK_HZ = 7670453.0` and derives `INTERNAL_RATE` from it; `Ym2612Chip.java:183-184` fixes the FM-cycles-per-Z80-cycle ratio to the NTSC `(7670453/6)/3579545`; `PsgChip.java:55-59` hard-codes `INPUT_CLOCK_HZ = 53_693_175/15` and `TICK_RATE_HZ`. `grep ChipClockProfile\|setChipClockProfile src/main` = 0 hits. | **Not moot.** The Nuked core itself is clock-agnostic (it steps internal cycles), so the re-land is smaller than before: only the host resampler rate (`Ym2612Chip.setOutputSampleRate`, `PsgChip` blip rate) and the Z80-cycle DAC period conversion need a PAL variant. Class (b). |
| `8d2f0dcf2` reference chip output defaults | Flipped `audio.dacInterpolate` and `audio.psgNoiseShiftEveryToggle` defaults to `false` (GPGX/libvgm behaviour) in `SonicConfigurationService`, `config.yaml`, `CONFIGURATION.md`, and the chip field defaults. | `SonicConfigurationService.java:672,676` still default both to `true`; `config.yaml:56,58` and `CONFIGURATION.md:278,280` still document `true`; `Ym2612Chip.java:231` `dacInterpolate = true` and the interpolation path still exists at `Ym2612Chip.java:564`; `PsgChip.java:389` `if (rising \|\| noiseShiftOnEveryToggle)` still honours the every-toggle mode. `CONFIGURATION.md:280` now describes `false` as "the documented SN76489 behaviour". | **Not moot.** A pure default flip that changes every PSG noise and DAC drum. Class (b); must be decided jointly with the mix-calibration owner and gated by listening. |
| `4e5e2def3` S3K SEGA PCM through YM DAC | `SampleBackedVoice.rawSegaPcm` gained a `YM2612_DAC` render mode that owns a `VirtualSynthesizer`, writes `0x2B=0x80` then streams each source byte as a `0x2A` write and renders through the chip; snapshot carried `renderMode`/`synthSnapshot`/`lastDacSourceFrame`; also added `equals`/`hashCode` to the old `Ym2612Chip.Snapshot`, `ChannelSnapshot`, `PsgChip`, `BlipDeltaBuffer`, `BlipResampler`. | `SampleBackedVoice.java:53-57` still routes SEGA PCM as a host-linear `oneShot` at `YM_DAC_GAIN_Q16`; no `renderMode`. The new core does route SMPS DAC drums through the chip natively (`Ym2612Chip.java:191 DAC_REGISTER = 0x2A`, DAC stream serviced per cycle at `:546-565`, `write()` handles `0x2A/0x2B` at `:629`), so the *mechanism* exists but SEGA PCM does not use it. `Ym2612Chip.Snapshot` is now a record (`:831`), so the hand-written `equals` hunks are obsolete. | **Not moot** for the PCM routing; the snapshot-equality hunks are obsolete. Class (b), reimplement against `Ym2612Chip`'s existing DAC stream rather than porting the diff. |

## Ring panning / stereo / special-stage ring SFX

**No commit on the branch addresses ring panning, stereo, or special-stage ring SFX.**

- `git log -i --grep='pan\|ring\|stereo\|special' develop..feature/ai-smps-transaction-parity`
  returns all 93 non-merge commits, which is a false positive: with `-i` the pattern
  `ring` matches "during"/"hardening"/"ordering" and `pan` matches "expand" in the
  commit bodies and trailers. Restricting to subjects gives zero ring/pan/stereo hits.
- `git log -S` for `RingSpeaker`, `zRingSpeaker`, `RING_RIGHT`: zero commits.
  `git log -S ringLeft`: `6cd882b00` and `282a0a794` only, and those touch the
  test-side complete-run alias maps (`CompleteRunAudioTrace.NativeSoundIdentity ringLeft`
  in the comparator tests), not the runtime.
- The whole-branch diff of `AudioManager.java` contains no ring-related hunk; the
  only `ringLeft` line in the runtime diff (`AudioVoiceRegistry.reset`, `ringLeft = true;`)
  is unchanged context.
- The branch tip carries the same bypass as develop: `AudioManager.playSfx(GameSound, float)`
  (worktree `AudioManager.java:1718-1731`) alternates `RING_LEFT`/`RING_RIGHT` and toggles
  `ringLeft` only when `sound == GameSound.RING`, while `AudioManager.playSfx(int, float)`
  (`AudioManager.java:1828-1834`) goes straight to `playBaseSfx(baseAudioSource, sfxId, pitch)`
  with no alternation. The ROM toggles `zRingSpeaker` on *every* raw ring request in
  `zPlaySound_CheckRing` (`docs/skdisasm/Sound/Z80 Sound Driver.asm:1919-1925`:
  `sub sfx__First / or a / jp nz,... / ld a,(zRingSpeaker) / xor 1 / ld (zRingSpeaker),a`),
  and resets it to left in `zPlaySoundByIndex`'s music path (`:547`).

Conclusion: re-landing any part of this branch neither fixes nor worsens the SS ring-pan
bug. That fix stays on `bugfix/ai-s3k-ss-ring-pan` and is independent of this triage.

## Per-commit table

Order is newest first (as `git log`). "Gate" = does re-landing change audible output
so the listening checklist must gate it. Line anchors for "verify vs disasm" are in
`docs/skdisasm/Sound/Z80 Sound Driver.asm` (5315 lines, `fix_sndbugs = 0` at line 16)
unless another file is named.

| sha | subject | class | mechanism / justification | gate? |
|---|---|---|---|---|
| `83723907d` | Merge branch 'develop' into feature/ai-smps-transaction-parity | c | Merge commit; no first-parent content. Re-land by cherry-pick, never by merging the branch. | no |
| `6d393dcf7` | fix(audio): harden retail SMPS catalogs | b | Loaders fail closed: S2 Saxman length must be little-endian and in range (`requireSaxmanPayloadLength`), only the four known uncompressed S2 song offsets are accepted, S1 PSG envelopes must end in `$80` (`requirePsgEnvelope`), S3K reads exactly 8 modulation and `$27` PSG envelope pointers (`Z80_MOD_ENVELOPE_COUNT`, `Z80_PSG_ENVELOPE_COUNT`), unreadable DAC tables return `null` instead of empty catalogs. Also removes the `new DacData(..., 295)` fallback from `0b269a9be` (partial supersession). Verify the S3K counts against the pointer tables (the old code cited "Mod. Pointer List: 130E (W, 3C)"; the commit asserts 8). No output change on valid ROMs, but silent fallbacks become hard failures. | no |
| `4e5e2def3` | fix(audio): render S3K SEGA PCM through YM DAC | b | See "Chip-level commits are not moot". Reimplement `SampleBackedVoice` YM-DAC mode on the new `Ym2612Chip` DAC stream; drop the obsolete snapshot `equals` hunks. Depends on `f266983a3` and on `a0e346e41` for "region-clocked". Verify `zPlaySEGAPCM` (`:4372`) timing. | yes |
| `a0e346e41` | fix(audio): use regional Mega Drive chip clocks | b | See "Chip-level commits are not moot". Re-implement as a PAL resampler/DAC-period variant on the new core; snapshot must carry the domain. | yes (PAL only) |
| `1465d9b5e` | fix(audio): preserve Sonic 1 restore DAC bug | b | `MusicOverrideDacRestorePolicy.PRESERVE_OVERRIDE_DAC_MODE` for S1: on 1-up restore, copies the displaced driver's `$2B` DAC-enable into the restored driver instead of restoring the saved chip (`FixBugs=0` omits the `$2B` repair in `cfFadeInToPrevious`, `s1.sounddriver.asm`). Reads `captureSynthSnapshot().ym().dacEnabled()`; re-point at the new core snapshot. Depends on `1ad387c2d`. | yes (S1 1-up) |
| `1ad387c2d` | fix(audio): match retail 1-up SFX lifecycle | b | `stopAllSfxForMusicOverride`: stop all SFX when a 1-up override starts, `sfxBlocked` until the driver-owned restore boundary; `MusicOverridePriorityPolicy` (S1 `CLEAR_BEFORE_SAVE`, S2 `PRESERVE_SAVED_LATCH` — the `FixDriverBugs=0` LDIR copies the stale `zSFXPriorityVal`), `MusicOverrideSfxReleasePolicy` (S3K `ON_RESTORE`, S1/S2 `AFTER_FADE_IN`). Renames `MusicOverrideRestorePolicy.FM_FADE_IN` → `DRIVER_FADE_IN` (partially supersedes `7ac56b1b4`). Depends on `2c0844a9f` (latch) and `7ac56b1b4`. Verify S3K `zFadeInToPrevious` (`:2725-2735`, `zTempoSpeedupSave` at `:2730`). | yes |
| `76855b939` | fix(audio): clock DAC through SMPS pause | b | `SmpsDriver.advancePausedHardware` renders chip frames without a driver VInt while paused; `AudioVoiceRegistry.smpsPaused` + `advancePausedSmpsHardware` called from `AudioPresentationProducer`. Extends `9aa3acc35`. With the new core "render without service" is `Ym2612Chip.renderStereo` continuing the DAC stream; verify the S3K claim that FM6/DAC keep running through `zPauseAudio` (`:2541`). | yes (pause) |
| `9aa3acc35` | fix(audio): apply retail SMPS pause protocols | b | `PausePolicy`: S1 `S1_PAN_KEYOFF` (pan FM1-6 to 0, key off, PSG silence; `PauseMusic` in `s1.sounddriver.asm`), S2 `S2_SILENCE_RELOAD` (destructive `zFMSilenceAll` writes `$FF` to `$30-$8F` on both ports, then `resumeFmAfterPause(reloadVoice=true)` re-sends instruments), S3K `S3K_FM1_TO_5` (mute FM1-5, leave FM6/DAC, redundant `zPSGSilenceAll` at `:2543`). `AudioManager` pause/resume drives `pauseSmpsDrivers`/`resumeSmpsDrivers`. Raw register writes now hit the Nuked bus with busy timing — 192 back-to-back writes for S2 need checking. Verify `zPauseAudio` (`:2541-2560`) and `zPSGSilenceAll` (`:2587`). | yes |
| `f266983a3` | fix(audio): make S3K SEGA PCM exclusive | b | SEGA PCM start calls `stopAndRemoveAllVoices()` (music, overrides, every SFX owner) and clears `pendingRestore`; StopSEGA no longer restores discarded voices (`fix_sndbugs=0`). Extended by `4e5e2def3` (partial supersession, not reversed). Worktree `AudioVoiceRegistry.java:1205` still has the old `rawPcm != null && rawPcm != voice` path. Verify `zPlaySEGAPCM` (`:4372`) and the `zStopAllSound` (`:2460`) tail. | yes (boot chant) |
| `7ac56b1b4` | fix(audio): fade restored S3K music through FM | b | `MusicOverrideRestorePolicy.FM_FADE_IN` + `FadeInChannelPolicy.FM_ONLY` for S3K: restoring a displaced song triggers the `$40`-step FM-only fade-in (PSG skipped, `addPsg = 0`). Enum renamed by `1ad387c2d`. Verify `zFadeInToPrevious` (`:2725`). | yes |
| `4f7e23dd7` | fix(audio): match Sonic 2 spindash rev ladder | b | `SfxRequestTransformPolicy.SONIC2_SPINDASH_REV`: driver-owned `0x3C`-service timeout and saturating 0-11 semitone `keyOffset` ladder applied at admission; `PlayableSpriteMovement` stops deriving pitch from `getSpindashCounter()/2048` and calls `playSfx(SPINDASH_CHARGE)` for *all* games. Snapshot fields added. Verify `zPlaySound_CheckSpindash` in `docs/s2disasm/s2.sounddriver.asm`, and the comment's "S3K E9" claim (S3K's `zPlaySoundByIndex` `:1641`/`:1659` `fix_sndbugs=0` block) before accepting the S3K/S1 pitch removal. | yes |
| `68d0c38fa` | fix(audio): preserve S3K one-up speed state | b | `MusicOverrideSpeedPolicy.NORMAL_DURING_OVERRIDE`: the 1-up jingle runs at speed 1 with speed shoes off while the displaced song keeps its saved state (`zTempoSpeedupSave`, `:161`, saved at `:1751`, restored at `:2730`). | yes (S3K 1-up with speed shoes) |
| `d08af3083` | fix(audio): preserve S3K modulation loop bug | b | `Sonic3kSmpsLoader.loadModEnvelopeFromData`: `$82`/`$84` operands read from the bogus low-memory `BC` address (`z80Driver[len]`), reproducing the `fix_sndbugs=0` `INC BC / LD A,(BC)` path. Verify against `zDoModulation` (`:1279`) and the envelope handler `fix_sndbugs` block at `:1303-1349`. | yes |
| `442c3cd70` | fix(audio): match shipped fade lifecycle | b | `FadeOutChannelPolicy` (S3K `HALT_DAC_AND_PSG_FADE_FM`), `fadeOutClearsSpeedShoes` (S1/S2), `fadeOutStopsSfxImmediately` (S1); terminal count stops all audio via `requestFadeTerminalStopAll` without an extra volume step (`endFadeSequencerService`). Verify `zFadeOutMusic` (`:2307-2330`, `jp zPSGSilenceAll` at `:2323`) and `zDoMusicFadeOut`. Candidate source of audible regressions (early cut-offs). | yes |
| `76f9571e1` | fix(audio): match shipped SFX release state | b | `PsgSfxReleaseMode.REST_UNTIL_NEXT_NOTE` for S1/S2 (restored PSG music track rests until its next note); S2 `FmSfxTakeoverMode.REGISTER_SEQUENCE` (no synthetic FM takeover reset). S3K keeps live-state restore. Verify in `s1.sounddriver.asm`/`s2.sounddriver.asm` SFX-stop paths. | yes |
| `644fb6f68` | fix(audio): preserve Sonic 1 SFX across BGM loads | b | `GameAudioProfile.OrdinaryMusicSfxPolicy` (S1 `PRESERVE_ACTIVE`): `SmpsDriver.adoptActiveSfxFrom` moves live SFX sequencers, locks, continuous state and priority latch into the replacement song's driver via snapshots; `ReplaceMusic` carries the policy. Depends on `2c0844a9f` (`sfxPriorityLatch`) and `9334eaba0` (`palFullUpdateCounter` in the rebuilt snapshot). Verify S1 `Sound_PlayBGM` in `s1.sounddriver.asm`. | yes (S1) |
| `10b27e755` | fix(audio): apply signed S3K modulation envelopes | b | Modulation-envelope bytes `$85-$FF` are signed deltas; only `$80-$84` are commands (`value < 0x80 \|\| value >= 0x85`). Verify `zDoModulation` (`:1279`). Small and self-contained. | yes |
| `8d2f0dcf2` | fix(audio): default to reference chip output | b | See "Chip-level commits are not moot". Default flip only (`audio.dacInterpolate=false`, `audio.psgNoiseShiftEveryToggle=false`). | yes |
| `0b269a9be` | fix(audio): correct Sonic 2 DPCM cadence | b | S2 `DacData.baseCycles` 288 → 295 (`zWriteToDAC` counted as 295 Z80 cycles per two decoded samples, `s2.sounddriver.asm`). Its `new DacData(empty, 295)` fallback is later removed by `6d393dcf7`. Worktree still has 288 (`Sonic2SmpsLoader`, 2 hits). Verify the cycle count in `s2.sounddriver.asm` `zWriteToDAC` / `.dac_playback_loop`. | yes (S2 drums pitch) |
| `556ab16c0` | fix(audio): preserve Sonic 2 spindash transpose bug | b | Removes the engine's `$90 → $10` FM5 transpose patch in `Sonic2SfxData` (shipped `FixMusicAndSFXDataBugs=0`). Worktree still has the patch (1 hit). | yes (S2 spindash release) |
| `4bd00b064` | fix(audio): match S3K SFX service order | b | `DriverServiceOrder.SFX_THEN_MUSIC` for S3K: two-pass service loop; completed SFX release channels before the same VInt's music update; the PAL repeat path keeps the same order. Rewrites the `driverClock` loop introduced by `9334eaba0` (partial supersession). Verify `zUpdateEverything` (`:653-655`: `zPauseUnpause`, `zUpdateSFXTracks`, then `zUpdateMusic`). | yes (S3K) |
| `2c0844a9f` | fix(audio): enforce shipped SFX priority latch | b | `SfxPriorityPolicy.GLOBAL_LATCH` for S1/S2: one rewindable `sfxPriorityLatch` in `SmpsDriver`, evaluated at admission (`evaluateSfxRequest`, `$80` bit keeps the old latch), channels claimed at admission (`claimAdmittedSfxChannels`), latch cleared on SFX track stop (`onSfxTrackStopped`). S3K `NONE`. Adds `sfxPriorityLatch` to `SmpsDriverSnapshot`. Verify `zSFXPriorityVal` handling in `s1.sounddriver.asm`/`s2.sounddriver.asm`. | yes (S1/S2 SFX drops) |
| `9334eaba0` | fix(audio): match S3K PAL driver cadence | b | `PalServicePolicy.FULL_DRIVER_REPEAT_EVERY_SIXTH` for S3K: driver-global `palFullUpdateCounter` (reload 6, repeat `zUpdateEverything`, decrement to 5) in `SmpsDriver`, snapshot field, `alignDriverSamplePhase` for late SFX admission. Replaces the `LEGACY_TEMPO_SCALE` placeholder that `34ebb6653` left for S3K (partial supersession of `34ebb6653`); its own loop is rewritten by `4bd00b064`. Verify `zPalDblUpdCounter` (`:123`, `:485-499`, `:549`). | yes (PAL S3K) |
| `34ebb6653` | fix(audio): match shipped SMPS scheduler cadence | b | Rewrites `SmpsSequencer` tempo for all three games: `duration = 1` seed, tempo accumulator seeded from header tempo, `TempoPhasePolicy` (S1 `RESET_TO_EFFECTIVE_TEMPO`, S2/S3K `PRESERVE`), S2 `serviceS2TempoFrame` (non-carry extends durations, never skips service; `EXTRA_MUSIC_EVERY_FIFTH` PAL repeat), S3K `serviceS3kMusicTempo` + `serviceS3kSpeedTail` (`zDoSpeedUp` shared tail), removes `tempoOnFirstTick`, the `OVERFLOW2` zero-tempo early returns and the "tempo-0 songs need an unconditional first tick (S3K title)" special case, removes the `x1.2` PAL multiplier for S1/S2, `initializeSpeedShoes` must precede service. **Riskiest commit on the branch**: it changed every song's cadence and is the prime suspect for the audible regressions. Verify `TempoWait` (`:2607`), `zTrackUpdLoop` (`:734`), `zSpeedupTimeout` (`:748-757`), and S1 `DOTEMPO` / S2 `TempoWait` in the S1/S2 drivers; specifically re-check tempo-0 songs (S3K title) still start. | yes |
| `0e16ee22f` | fix: preserve ABI5 component order | c | Native observer patch, selftest tables, C# observer; no engine runtime. | no |
| `01bbe7f1d` | feat(audio): authenticate complete-run ROM content | c | `tools/audio/completerun` comparator + 14 test files; no runtime. | no |
| `b08ea1be3` | docs(audio): define ROM-authenticated content resolution | c | Design/plan doc revision. | no |
| `d410f4593` | fix(audio): correct S1 A1 semantic predicates | c | C# semantic evidence + fixtures. | no |
| `8c0e356d0` | fix(audio): correct S1 A1 native selector oracles | c | Native selftest tables. | no |
| `ea565d7fe` | fix(audio): close S3K stop-SEGA service tail | c | Fixtures, selftest, C# profile; no engine runtime (the engine-side StopSEGA behaviour is `f266983a3`). | no |
| `be4883f89` | fix(audio): close Task5C semantic evidence | c | 106k-line fixture regeneration + C# evidence. | no |
| `9a40e41eb` | fix(audio): authenticate Task5C diagnostic core | c | Fixture + C# test. | no |
| `815c48ee4` | fix(audio): bind exact semantic condition authority | c | C# observer/manifest/tests. | no |
| `f24b61eb8` | fix(audio): complete ABI5 typed component transport | c | Native patch, tables, C# observer. | no |
| `1adae1737` | feat(audio): bind S3K pending restore cover | c | `tools/audio/completerun` + tests. | no |
| `50696d1d8` | docs(audio): define S3K pending-cover contract | c | Design/plan doc revision. | no |
| `fddb8b86d` | feat(audio): add typed ABI5 semantic components | c | Native patch, selftests, C#. | no |
| `50d725bfb` | feat(audio): enforce canonical source-causal transactions | c | `tools/audio/completerun` + tests. | no |
| `b42be7234` | docs(audio): bind exact clear variants | c | Doc revision. | no |
| `0aeb16411` | docs(audio): amend SMPS causal projection contract | c | Doc revision. | no |
| `e408b374e` | fix: bind S1 queue scans to concrete request context | c | C# observer/evidence/manifest + fixtures. | no |
| `89ddd23d2` | fix(audio): close SFX request lifecycle transactions | c | Fixtures + C# + completerun tests. | no |
| `abbf3a086` | test(audio): refresh ABI5 source-causal tables | c | Native selftest tables. | no |
| `04599c835` | fix(audio): scope lifecycle epochs by exact owner | c | completerun + test. | no |
| `d38a4b814` | fix(audio): make bounded semantic evidence source causal | c | Fixtures (v2/v4), C#, completerun. | no |
| `46e533aba` | fix(audio): bind S2 one-up activation store | c | 4-line selftest table fix. | no |
| `f51715d53` | fix(audio): correct final source-causal coordinates | c | 4-line selftest table fix. | no |
| `5f3a6b09c` | fix(audio): admit pre-service causal branch evidence | c | Native patch + tables. | no |
| `ba8ec4268` | fix(audio): correct ABI5 source-causal tables | c | Native patch + tables. | no |
| `9fa9ce006` | fix(audio): retain exact terminal validation identity | c | completerun + tests. | no |
| `64841d322` | fix(audio): bound complete validation context | c | completerun + tests. | no |
| `ae19c5cd6` | fix(audio): retain source causal transaction histories | c | completerun + tests. | no |
| `021dd2e9e` | refactor(audio): harden source causal validation | c | completerun + tests. | no |
| `7e1b5ece5` | test(audio): preserve physical resource alias coverage | c | Test only. | no |
| `e95760af6` | refactor(audio): make canonical ledger source causal | c | completerun rewrite + tests. | no |
| `7c213c3dc` | docs(audio): share restore epochs across actions | c | Doc revision. | no |
| `cbf495379` | docs(audio): type lifecycle restore contracts | c | Doc revision. | no |
| `cb417e23d` | docs(audio): close causal plan review gaps | c | Doc revision. | no |
| `7ba13d56f` | docs(audio): make parity plan source causal | c | Doc revision (rewrites the plan; earlier plan states `cf2ed69ea`/`b5376e605` are effectively superseded — re-land the tip file, not the history). | no |
| `71dec5f38` | fix(audio): isolate ABI5 publication epoch | c | C# observer, capture runners, fixtures. | no |
| `0b2c9f1b6` | fix(audio): bind S2 direct requests at the bridge | c | Native patch, fixtures, C#. | no |
| `f708ca8ca` | fix(audio): serialize ABI5 service generations | c | Native patch + selftests. | no |
| `3a6d49d17` | fix(audio): authenticate ABI5 token generations | c | C# + fixtures. | no |
| `f2fd7619b` | fix(audio): authenticate complete semantic instructions | c | C# + fixtures. | no |
| `1fd017a36` | fix(audio): correct authentic ABI5 site bindings | c | Fixtures, tables, C# tests. | no |
| `30c71ec99` | fix(audio): harden ABI5 semantic projection | c | C# observer + tests. | no |
| `fc4d3429d` | feat(audio): project managed semantic ABI5 | c | C# observer/manifest/native binding. | no |
| `c80327dd7` | fix(audio): isolate reset observer staging | c | Native patch + selftests. | no |
| `96e67af6c` | fix(audio): stage reset observer transactions | c | Native patch + selftests. | no |
| `95f9beade` | fix(audio): make semantic ABI5 transactions atomic | c | Native patch + selftests. | no |
| `c56d3291a` | feat(audio): add grouped semantic observer ABI5 | c | Native patch, compiled tables, README. | no |
| `c20ee0519` | fix(audio): preserve compiled ABI metadata | c | C# evidence + tests. | no |
| `b4ae75fb5` | fix(audio): compile full semantic ABI tables | c | C# evidence + fixtures. | no |
| `825ddf5d9` | feat(audio): enrich bounded semantic evidence | c | Fixtures v2/v3 + C#. | no |
| `1784e6b9c` | docs(audio): define grouped semantic ABI | c | Doc revision. | no |
| `4e9f3b9d0` | feat(audio): close bounded selected-run semantics | c | C# projects, fixtures, high-risk cases; 33 files, no runtime. | no |
| `48eaea09a` | test(audio): exercise discovery against patched core | c | C# test. | no |
| `af042df80` | test(audio): verify discovery layouts with native harness | c | Native selftest. | no |
| `ea3950b62` | test(audio): strengthen discovery boundary proof | c | Native selftests. | no |
| `af604ce1b` | fix(audio): lazily bind discovery exports | c | C# discovery adapter. | no |
| `79d5dda03` | feat(audio): add dual CPU discovery plane | c | Native gpgx observer patch, C# discovery, csproj. | no |
| `a895a9e02` | docs(audio): bound SMPS parity to selected runs | c | Doc revision. | no |
| `81df6e44a` | fix(audio): retain producer-neutral cutoff identity | c | completerun + tests. | no |
| `ccc68f367` | fix(audio): scale complete-run proof state | c | completerun + tests. | no |
| `282a0a794` | fix(audio): enforce exact native projection proof | c | completerun + tests (touches the test-side `ringLeft` alias map only). | no |
| `6cd882b00` | fix(audio): enforce exact canonical profile truth | c | completerun + tests (touches the test-side `ringLeft` alias map only). | no |
| `53ae38ba7` | fix(audio): harden canonical transaction contract | c | completerun + tests. | no |
| `9a04410d0` | feat(audio): define canonical SMPS transaction schema | c | New `tools/audio/completerun` classes + tests. | no |
| `c7896f686` | docs(audio): make integration ROM setup self-contained | c | Doc revision. | no |
| `9e436fc2b` | docs(audio): harden ROM discovery and Maven gates | c | Doc revision. | no |
| `dc032441d` | docs(audio): correct shared ROM discovery contract | c | Doc revision. | no |
| `b5376e605` | docs(audio): plan exact SMPS transaction parity | c | Plan doc revision (superseded in content by `7ba13d56f` and later). | no |
| `d4dd50ca7` | Merge remote-tracking branch 'origin/develop' into feature/ai-smps-transaction-parity | c | Merge commit. | no |
| `cf2ed69ea` | docs(audio): plan cross-game SMPS parity proof | c | Initial plan doc (1153 lines; superseded in content by later plan revisions). | no |
| `a6773986a` | docs(audio): design cross-game SMPS parity proof | c | Initial design doc (511 lines; revised by every later docs commit). | no |

Partial supersessions (no whole commit is reversed):

1. `34ebb6653` S3K `LEGACY_TEMPO_SCALE` placeholder → replaced by `9334eaba0` `FULL_DRIVER_REPEAT_EVERY_SIXTH`.
2. `9334eaba0` single-pass `driverClock` service loop → rewritten by `4bd00b064` two-pass loop.
3. `7ac56b1b4` `MusicOverrideRestorePolicy.FM_FADE_IN` → renamed `DRIVER_FADE_IN` by `1ad387c2d`.
4. `0b269a9be` empty-`DacData` 295 fallback → removed (`return null`) by `6d393dcf7`.
5. `f266983a3` host-linear exclusive PCM → routed through the YM DAC by `4e5e2def3`.
6. `4e5e2def3` old-core `Snapshot.equals`/`hashCode` hunks → obsolete now that `Ym2612Chip.Snapshot` is a record (`Ym2612Chip.java:831`); not a branch-internal supersession but dead on arrival.

None of the 23 (b) features is present in this worktree: `grep` for `PalServicePolicy`,
`sfxPriorityLatch`, `DriverServiceOrder`, `initializeSpeedShoes`, `loadModEnvelopeFromData`,
`OrdinaryMusicSfxPolicy`, `PsgSfxReleaseMode`, `FadeOutChannelPolicy`, `MusicOverrideSpeedPolicy`,
`SfxRequestTransformPolicy`, `FadeInChannelPolicy`, `PausePolicy`, `advancePausedHardware`,
`MusicOverridePriorityPolicy`, `MusicOverrideDacRestorePolicy`, `requireSaxmanPayloadLength`,
`requirePsgEnvelope`, `Z80_MOD_ENVELOPE_COUNT`, `adoptActiveSfxFrom`, `ChipClockProfile`
all return 0 hits under `src/main`; `tempoOnFirstTick` (9 hits), the `$90→$10` transpose
patch, `getSpindashCounter() / 2048`, and `DacData(..., 288)` are all still the pre-branch
code.

## Recommended re-landing order for class (b)

Cherry-pick per batch onto a fresh branch from the mix-calibration base; run the
listening checklist after each batch, not once at the end — the original branch's
failure mode was 23 behavioural changes landing under one green test suite.

**Batch 1 — ROM-data corrections (lowest risk, each independently verifiable against
the ROM bytes).**
`556ab16c0` (S2 `$90` transpose), `0b269a9be` (S2 DPCM 295 cycles), `10b27e755` (S3K
signed modulation bytes), `d08af3083` (S3K bogus-`BC` mod loop), then `6d393dcf7`
(catalog hardening) last because it converts silent fallbacks into exceptions and
removes the fallback `0b269a9be` added. Run the three ROM-backed catalog sweeps that
`6d393dcf7` added. Verify `d08af3083` and `10b27e755` against `zDoModulation`
(`:1279-1349`) before landing; `6d393dcf7`'s "exactly 8 modulation pointers" contradicts
the previous code's `0x3C` comment and must be settled from the pointer table.

**Batch 2 — scheduler cadence (highest risk; prime suspect for the audible regressions).**
`34ebb6653` → `9334eaba0` → `4bd00b064`, in that order (each depends on the previous:
`9334eaba0` replaces the S3K placeholder and adds the snapshot field; `4bd00b064`
rewrites `9334eaba0`'s loop). Do **not** land `34ebb6653` without the other two, or S3K
PAL regresses to the `x1.2` approximation. Before landing, re-derive from the disassembly:
`TempoWait` (`:2607`), `zUpdateEverything`/`zUpdateMusic` (`:653-700`),
`zUpdateSFXTracks`/`zTrackUpdLoop` (`:727-734`), `zSpeedupTimeout` (`:748-757`),
`zPalDblUpdCounter` (`:485-499`, `:549`), and the S1 `DOTEMPO` / S2 `TempoWait`
equivalents. Specifically re-check the two behaviours the commit deleted: the
`OVERFLOW2` zero-tempo early return and the "tempo-0 song needs an unconditional first
tick so `FF 00` can set the real tempo" path (S3K title screen). Listening gate: every
song's tempo in all three games, S3K title, S2 PAL, speed shoes in S3K.

**Batch 3 — SFX admission and release (depends on batch 2 snapshot fields).**
`2c0844a9f` (global priority latch), `76f9571e1` (PSG rest-until-next-note, S2
register-sequence takeover), `644fb6f68` (S1 SFX carried across BGM loads; needs both
`sfxPriorityLatch` and `palFullUpdateCounter` in `SmpsDriverSnapshot`). Verify
`zSFXPriorityVal` semantics in `s1.sounddriver.asm`/`s2.sounddriver.asm` (the `$80`
"keep latch" bit and clear-on-track-stop). Listening gate: S1/S2 dense SFX scenes
(ring loss, spring + jump), S1 act transitions with a live SFX.

**Batch 4 — fades and 1-up override lifecycle (depends on batch 3 latch).**
`442c3cd70` (fade lifecycle) → `68d0c38fa` (S3K 1-up speed) → `7ac56b1b4` (S3K FM-only
restore fade) → `1ad387c2d` (1-up SFX gate/priority restore; renames the enum from
`7ac56b1b4`) → `1465d9b5e` (S1 restore DAC bug; re-point `captureSynthSnapshot().ym().dacEnabled()`
at the new `Ym2612Chip.Snapshot` record). Verify `zFadeOutMusic` (`:2307`),
`zDoMusicFadeOut`/`zDoMusicFadeIn`, `zFadeInToPrevious` (`:2725`), `zTempoSpeedupSave`
(`:1751`, `:2730`). Listening gate: fade-outs at act end in all three games (early cut
is the regression to listen for), 1-up during speed shoes in S3K, 1-up in S1 with a DAC
drum playing.

**Batch 5 — spindash ladder.**
`4f7e23dd7`. Depends on the snapshot constructor chain from batches 2-4. It removes the
gameplay-derived pitch for **all** games, so verify the S2 claim (`zPlaySound_CheckSpindash`,
`s2.sounddriver.asm`) and the S3K claim ("E9", `zPlaySoundByIndex` `:1641` and its
`fix_sndbugs=0` block at `:1659`) separately; S1 has no spindash. Listening gate: S2
and S3K spindash charge in isolation.

**Batch 6 — pause protocols.**
`9aa3acc35` → `76855b939`. These issue raw register writes through `super.writeFm`
which now go through the Nuked bus with busy timing (`Ym2612Chip.java:121-135`);
S2's destructive silencer is 192 writes per pause and must be confirmed to complete
before resume re-sends instruments. Verify `zPauseAudio` (`:2541-2560`),
`zPauseUnpause` (`:2232`), `zPSGSilenceAll` (`:2587`), S1 `PauseMusic`, S2
`zFMSilenceAll`. Listening gate: pause/resume mid-song in all three games, pause during
a DAC drum in S3K.

**Batch 7 — chip-adjacent, reimplemented rather than cherry-picked.**
`a0e346e41` (PAL clock domain: add a PAL host-resample rate and Z80-cycle DAC period to
`Ym2612Chip`/`PsgChip`, snapshot the domain; the cycle-stepped core needs nothing else),
then `f266983a3` → `4e5e2def3` (SEGA PCM exclusivity, then a `SampleBackedVoice`
YM-DAC mode built on `Ym2612Chip`'s existing `0x2A` stream; drop the hand-written
snapshot `equals` hunks). `8d2f0dcf2` (default flip) goes last and only with the
mix-calibration owner's agreement, since the calibration work on this base was done
against the current `true` defaults. Listening gate: PAL pitch for all three games,
S3K boot chant, PSG noise character and DAC drums after the default flip.

Rationale for the dependency order: `SmpsDriverSnapshot` and `SmpsDriver.State`/`Token`
gain fields in `9334eaba0` (`palFullUpdateCounter`), `2c0844a9f` (`sfxPriorityLatch`),
`4f7e23dd7` (`spindashRev*`), and `4e5e2def3`/`a0e346e41` (synth domain), and later
commits construct those snapshots positionally (`644fb6f68`, `1ad387c2d`). Cherry-picking
out of order produces constructor conflicts that are easy to resolve wrongly.

## (b) commits that must be re-verified against the disassembly before re-landing

All S3K-facing (b) commits should be re-read against
`docs/skdisasm/Sound/Z80 Sound Driver.asm` (the
worktree's `docs/skdisasm` is empty); the ones below carry a specific claim that the
commit message asserts but that this audit could not confirm from the diff alone:

| Commit | Claim to verify | Anchor |
|---|---|---|
| `34ebb6653` | Carry semantics of `TempoWait` (extend durations vs skip service) for S3K and S2; seeding `tempoAccumulator = tempoWeight`; `duration = 1` seed; deleted tempo-0 first-tick path | `TempoWait :2607`, `zTrackUpdLoop :734`, `zUpdateMusic :658` |
| `9334eaba0` | Check-before-decrement, reload 6, repeat, decrement to 5 | `:485-499`, `:549` |
| `4bd00b064` | SFX serviced before music; completed SFX release channels in the same VInt | `zUpdateEverything :653-655`, `zUpdateSFXTracks :727` |
| `10b27e755` | `$85-$FF` are signed deltas; only `$80-$84` are commands | `zDoModulation :1279`, `fix_sndbugs` blocks `:1303`, `:1349` |
| `d08af3083` | `$82`/`$84` operand read via bogus `BC` (index+1 into driver RAM) | same as above |
| `442c3cd70` | S3K halts DAC and PSG immediately, fades FM; terminal count stops without an extra volume step | `zFadeOutMusic :2307-2330` |
| `68d0c38fa`, `1ad387c2d`, `7ac56b1b4` | `zTempoSpeedupSave` save/restore; `$40`-step FM-only fade-in; SFX reopened on restore | `:1751`, `zFadeInToPrevious :2725-2735` |
| `9aa3acc35`, `76855b939` | FM1-5 muted, FM6/DAC left running, redundant PSG silence | `zPauseAudio :2541-2560`, `zPauseUnpause :2232` |
| `f266983a3`, `4e5e2def3` | Exclusive PCM ownership and StopSEGA leaving the driver silent | `zPlaySEGAPCM :4372`, `zStopAllSound :2460` |
| `4f7e23dd7` | The "S3K E9" driver-side spindash ladder claim | `zPlaySoundByIndex :1641`, `:1659` |
| `6d393dcf7` | Exactly 8 modulation envelope pointers and `$27` PSG envelope pointers | pointer tables near the `zID_*` table indices used by `zPlaySound_Bankswitch :1928` |

S1/S2-facing commits (`2c0844a9f`, `76f9571e1`, `644fb6f68`, `1465d9b5e`, `0b269a9be`,
`556ab16c0`, S1/S2 halves of `442c3cd70`, `9aa3acc35`, `1ad387c2d`) cite
`docs/s1disasm/s1.sounddriver.asm` and `docs/s2disasm/s2.sounddriver.asm` (both present
in the main checkout) and need the same treatment there.
