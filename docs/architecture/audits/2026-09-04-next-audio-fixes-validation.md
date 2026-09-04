# Validation of the `next` branch audio fixes against the driver oracles

- **Date:** 2026-09-04
- **Worktree/branch:** `.worktrees/next-audio-validation`,
  `feature/ai-validate-next-audio-fixes`, over `develop` at `373b4376c`.
- **Scope:** every commit on `origin/next` absent from `develop` that touches an
  audio path, validated against the disassemblies (`FixBugs = 0` /
  `fix_sndbugs = 0` path) and the `com.openggf.tools.audio.parity` driver
  oracles.
- **Headline:** both audio fixes on `next` are **already on `develop`**, landed
  together as `86e4c8085`. Nothing was implemented, because there is nothing
  outstanding to implement. Their ROM claims were verified anyway and hold.

## Enumeration

`git log develop..origin/next --format=... -- 'src/main/java/com/openggf/audio'
'src/main/java/com/openggf/game/*/audio' 'src/main/java/com/openggf/game/*/*/audio'`
returns nine non-merge commits. Two are audio fixes, seven are features.

| Commit | Author | Date | Message | Audio files | Claimed changelog line | Decision |
|---|---|---|---|---|---|---|
| `aa3fee58d` | James | 2026-07-30 | fix(audio): model the ROM's single 1-up save slot, not an override stack | `AudioManager`, `GameAudioProfile`, `Sonic1AudioProfile`, both Super state controllers, `AbstractPlayableSprite`, `DrowningController`, `PlayableSpriteRuntimeServices` | `Changelog: updated` | **Already on develop** (`86e4c8085`) |
| `c4af7f072` | James | 2026-07-30 | fix(audio): route override music through the presentation override stack | `AudioManager` | `Changelog: updated` | **Already on develop** (`86e4c8085`) |
| `75758d198` | James | 2026-07-29 | feat(s3k): complete powered form effects | `GameSound` (+`THUMP`), `Sonic3kAudioProfile` (one map entry) | `Changelog: updated` | **Out of scope** — a feature, not an audio fix |
| `a4f0aade4`, `052643e18`, `448355a5b`, `08500042d`, `d6009b2b8`, `8a7b09176` | Farrell | 2026-07-11..13 | streamed music route, one-shot SFX pool, mod audio/session/SDK | various | feature entries | **Out of scope** — feature work |

The 08-27 revert `b4c8fbd8a` is an ancestor of *both* `develop` and `next`, so it
contributes nothing to this diff and is not re-examined here.

## The two fixes already landed

`86e4c8085` "fix(audio): restore level music after 1-up, invincibility and
Super" is the squashed equivalent of `c4af7f072` followed by `aa3fee58d`: same
author, same session id, same ROM argument, and the union of the two file sets
(`AudioManager` +22 = 11 + 11; `TestMusicOverrideRestore` merged).

Comparing the content lines of `git diff c8b1cf4dc aa3fee58d -- src/main`
against `git diff 86e4c8085^ 86e4c8085 -- src/main` leaves exactly one
difference: a 13-line block removed from `Sonic3kSuperStateController`'s
presentation-only lifecycle reset. That block exists only on `next`, added by
the unmerged `75758d198`; `develop`'s copy of the file never had it, and
`develop` has no `endMusicOverride` or `endDonorMusicOverride` call in that
class today. The behavioural outcome is identical. **Nothing is missing from
`develop`.**

## ROM verdict on the claims of `86e4c8085`

Verified against `FixBugs = 0` / `fix_sndbugs = 0`. Every claim is **correct**.

| Claim | Verdict | Citation |
|---|---|---|
| S1's `Sound_PlayBGM` backs up `v_1up_ram` and sets `f_1up_playing` for the extra-life id only | Correct | `docs/s1disasm/s1.sounddriver.asm:754-786` — `cmpi.b #bgm_ExtraLife,d7`, the `$220`-byte backup loop to `v_1up_ram_copy`, then `move.b #$80,f_1up_playing` |
| A 1-up during a 1-up does not re-save | Correct | `s1.sounddriver.asm:757-758` — `tst.b f_1up_playing / bne.w .locdblret` |
| Any other music request abandons the save | Correct | `s1.sounddriver.asm:789-790` — `.bgmnot1up: clr.b f_1up_playing` |
| S3K's `zPlayMusic` copies `zTracksStart` to `zTracksSaveStart` and sets `zFadeToPrevFlag`, for the extra-life id only | Correct | `docs/skdisasm/Sound/Z80 Sound Driver.asm:1717-1780` — `cp mus_ExtraLife-mus__First / jp nz, zPlayMusic_DoFade`, the `ldir`, then `ld (zFadeToPrevFlag), a` |
| S3K also declines to re-save during a 1-up | Correct | same file `:1738-1740` — `cp mus_ExtraLife-mus__First / jp z, zBGMLoad` |
| Any other S3K music request abandons the save via `zPlayMusic_DoFade` -> `zStopAllSound`, zeroing `zFadeToPrevFlag`, the bank/tempo/voice saves and the whole backup area | Correct | `:1786-1787` reaches `zStopAllSound` at `:2460`, whose `ldir` zeroes `zTempVariablesStart`..`zTempVariablesEnd`; the RAM map at `:134-215` places `zFadeToPrevFlag`, `zVoiceTblPtrSave`, `zCurrentTempoSave`, `zSongBankSave`, `zTempoSpeedupSave` and all of `zTracksSaveStart`..`zTracksSaveEnd` inside that span. On the shipped ROM (`fix_sndbugs = 0`) the wipe is `34h` bytes *longer* still |
| Invincibility expiry re-issues the level music, gated on a boss fight and on the drowning countdown | Correct | S1 `docs/s1disasm/_incObj/01 Sonic.asm:154-176` — `tst.b (f_lockscreen).w`, `cmpi.w #12,(v_air).w`, then `MusicList2` + `QueueSound1`. S2 `docs/s2disasm/s2.asm:36290-36303` — `tst.b (Current_Boss_ID).w`, `cmpi.b #12,air_left(a0)`, then `move.w (Level_Music).w,d0 / jsr (PlayMusic).l` |
| The Super revert plays no music and defers by setting the invincibility timer to 1 | Correct | S3K `docs/skdisasm/sonic3k.asm:23610-23626` — `.revertToNormal` ... `move.b #1,invincibility_timer(a0)`, no sound call. S2 `docs/s2disasm/s2.asm:37528-37538` — `Sonic_RevertToNormal` ... `move.w #1,invincibility_time(a0)`, no sound call |

One detail the commit message omits, checked and found harmless: S1's
invincibility path indexes its own table `MusicList2` and substitutes music 5
for SBZ3/LZ4 (`01 Sonic.asm:167-171`). That table is byte-identical to the
level-load `MusicList` (`docs/s1disasm/sonic.asm:2685-2692`, and the
disassembly says so itself), and the level-load path applies the same LZ4
substitution (`sonic.asm:2797-2799`). So the engine's
`getCurrentLevelMusicId()` reproduces `MusicList2` for every zone in which
invincibility can expire. `MusicList` additionally handles FZ, which
`MusicList2` has no entry for and which cannot reach this path.

## Oracle evidence

Gate run in this worktree at `373b4376c`, clean `target/test-tmp`:

```
LUA_BIN=lua5.4 mvn -Dmse=off -B \
  "-Dsonic1.rom.path=<S1 REV01>" "-Dsonic2.rom.path=<S2 REV01>" \
  "-Ds3k.rom.path=<S3K locked-on>" \
  "-Ds2.request.bk2.path=<sonic-2-sonic-tails-complete-emeralds.bk2>" \
  "-Dtest=com/openggf/tools/audio/parity/**/*" test
```

Result: `Tests run: 187, Failures: 0, Errors: 0, Skipped: 2`. Every
`MEASUREMENT_ONLY` line reports MATCH — the S1 gameplay oracles, both S1
sound-test oracles, the S2 driver-state windows, and every S2 request window.

**But no oracle exercises the behaviour these two fixes model.** The one oracle
whose subject is exactly the 1-up interrupt-and-resume path,
`TestS1OverrideResumeAudioOracle`, compares nothing: it asserts that
`OverrideResumeReferenceBundle.open` *fails* with
`FRESH_AUTHENTICATED_NATIVE_GPGX_AUTHORITY_UNAVAILABLE`, because the reference
bundle has never been published. It is an honest blocked-task marker, not a
comparison, and it passes in 8 ms. A reader who sees "187 green, all oracles
MATCH" would wrongly conclude the override path is oracle-covered. It is
covered only by the unit tests `TestMusicOverrideRestore` and
`TestPowerUpMusicRestore`.

### The capture that would cover it

The contract already exists and names its own inventory
(`OverrideResumeReferenceBundle`, `BUNDLE_NAME =
override-resume-first-divergence-v1`): per game, a
`<game>-override-resume-reference.v1.jsonl.gz` stream plus a
`<game>-override-resume-metadata.v1.json`, for S1 and S2, under
`src/test/resources/audio/parity/override-resume-first-divergence-v1/`.

The window to record is a **1-up collected during ordinary zone music**,
epoch-aligned so that it spans the extra-life request, the jingle, the `E4`
fade-in-to-previous, and enough of the resumed zone music to prove the restored
track advances. A second window covering **invincibility expiry** would
discriminate the other half of the fix, since that half asserts the level music
is re-issued rather than restored from a save slot, and the two are
distinguishable in the write stream: a re-issue reloads the voice table and
restarts the song position, a restore does not.

No capture was taken here. The S2 request-window tooling captures a request
window, not this bundle's schema, so this is not the cheap case the task
anticipated.

## Decisions

| Commit | Decision | Reason |
|---|---|---|
| `aa3fee58d` | **Not implemented — already present** | Landed on `develop` as part of `86e4c8085`. ROM claims verified correct. |
| `c4af7f072` | **Not implemented — already present** | Landed on `develop` as part of `86e4c8085`. ROM claims verified correct. |
| `75758d198` | **Not implemented — out of scope** | A 1,700-line S3K powered-form feature. Its audio content is one `GameSound.THUMP` constant and one S3K profile map entry, inseparable from the feature and not an audio fix. |
| mod-audio and streamed-music commits | **Not implemented — out of scope** | Feature work, excluded by the task. |

## Note for the S2 1-up restore lane

Recorded in the audio frontier log as well. The engine-side model of the 1-up
save slot on `develop` is ROM-correct as audited above, and in particular the
single-slot semantics are not an S1 or S3K peculiarity: both drivers decline to
re-save during a 1-up and both discard the save on any other music request. If
an S2 1-up restore bug reproduces on `develop` today, the defect is below the
`isMusicOverride` classification, not in it. `TestS1OverrideResumeAudioOracle`
is not a comparison and will not arbitrate a fix.

---

# Follow-up: making `TestS1OverrideResumeAudioOracle` compare something

Recorded 2026-09-04 on the same branch, after the lead asked for the green
no-op to be replaced by a real comparison or deleted.

## The test is worse than a no-op; it is inverted

It asserts that `OverrideResumeReferenceBundle.open` *fails*. Publishing the
reference it exists to consume would turn it red. Whatever replaces it cannot
be a narrowing of the current assertion; the assertion has to be inverted.

## The reference bundle is not a dead contract, but it is blocked behind machinery the user has since rejected

The bundle is recent and deliberate: `26a443def` and `e28c08137`, both
2026-09-02, by the repository owner. Its own status document,
`docs/architecture/validation/audio/2026-09-01-override-resume-reference-limitation.md`,
records why no capture was ever published. The four blockers are a two-build
observer reproduction gate with no configured inputs, a locked recipe verifier
failing because the host's `/usr/bin/ar` hash does not match a pinned constant,
a reviewed-capability guard rejecting the current source hash, and a stock
BizHawk tree failing installation authentication for a missing `identity.json`.

That is the same class of machinery the owner ruled on the following day, on
the TraceChaser native build: pinned host-toolchain hashes behind chained
recipe locks that turn every system update into a manual identity cascade.
Reviving the bundle as designed means restoring those trust roots. This audit
does not recommend that.

The bundle is also atomic across both games: `open` requires an `s1` *and* an
`s2` member, so no S1-only publication can satisfy it. That makes it
jointly owned with the S2 1-up lane rather than mine to retire alone.

## Two independent blockers found for the replacement

### 1. The per-song window contract that would carry the fixture is unlanded

The lead directed me to the `s1-audio-complete` lane's probes rather than a new
one, which is right: `tools/audio/probes/s1_bgm_window_survey_probe.lua` tiles a
movie by `Sound_PlayBGM` dispatch and `s1_run_window_driver_parity_probe.lua`
captures a window byte-identically to a committed fixture. But the Java capture
kind those produce, `s1_run_song_window_driver_reference`, exists only on
`feature/ai-s1-audio-complete-runs`. The landed `AudioParitySchema` carries
only the GHZ music, sound-test SFX and GHZ1 gameplay kinds. A 1-up window
fixture cannot be published here until that contract lands.

### 2. The engine cannot pass this oracle, because it models the resume in a different layer

This is the finding that decides the shape of the work. In the ROM, S1's `E4`
coord flag is a driver-internal restore: `cfFadeInToPrevious` copies the whole
`$220`-byte block back from `v_1up_ram_copy` — every driver variable and every
music track's state — then sets the DAC SFX-overriding bit, applies the
current fade offset to each playing track, re-sends FM voices, key-offs PSG,
triggers the fade-in flag with counter `$28`, and clears `f_1up_playing`
(`s1.sounddriver.asm:2164-2222`). On the shipped `FixBugs = 0` path it also
omits the DAC-mode disable write, so the FM6/DAC restoration bug is live and
any reference records its absence. The bundle schema's `writes_dac_disable_zero`
field was evidently designed around exactly that.

The engine does none of this in the driver. `SmpsSequencer`'s `0xE4` handler
deactivates every track in the current sequence and calls
`MusicRestoreSink.restoreMusic()`, a no-argument, no-return functional
interface whose entire contract is "receives an SMPS request to restore the
music beneath an override". It carries no state, so it cannot restore anything;
it is a signal to the presentation layer to re-issue a song. The parity capture
host makes this concrete: `S1OpenGgfAudioCapture` constructs its sequencer with
`() -> { }` as that sink and with a single song's data. At the `E4` the engine's
nine tracks all go inactive and stay inactive, while the ROM has the
interrupted zone song playing again from restored RAM.

So a driver-level comparison across the resume does not compare an engine
mistake against the ROM. It compares two different mechanisms in two different
layers, and it would diverge at the first tick after the `E4` in essentially
every field. Making it green is not a test change; it is implementing S1's
1-up save slot inside the driver, which is squarely inside the SMPS playback
authenticity programme the owner deferred to 0.7 in `b4c8fbd8a`.

## Recommendation

Do not delete the test and do not make it a hard assertion. Three steps, in
order, none of which this lane can complete alone:

1. **Land the per-song window contract** from the `s1-audio-complete` lane, so
   a mid-movie window fixture has a parser.
2. **Publish the `$88` window as a comparison-only reference** with pinned
   digests, using that lane's capture probe. This is the artefact the
   limitation document says is missing, minus the attestation machinery, and it
   pins the ROM's resume behaviour including the `FixBugs = 0` DAC-write
   omission.
3. **Replace the inverted assertion with a `MEASUREMENT_ONLY` comparison** that
   reports its first divergence, in the shape `TestS2DriverStateOracle` already
   uses, and record the frontier. Include that oracle's negative control: a
   corrupted input must move the verdict, or the new comparison inherits the
   old one's inability to disagree.

The resulting oracle will be red at the `E4`, and that is the correct outcome.
It converts an untested claim into a measured frontier whose cause is known and
whose fix is scheduled for 0.7.

## The per-song window rule already handles this, and the S1 lane got there first

I raised the mid-window song change with the `s1-audio-complete` lane as an
open contract question. It was not open: that lane had already hit it and fixed
it the same day, and the credit is theirs. Their whole-run capture of
`s1-complete-run.bk2` aborted at emulator frame 138,347, 212 frames into
**window 58, whose song is `$88`**, with `ROM sequence pointer is outside the
GHZ asset range`. Windows 0 through 57 completed. A single-song contract that
asserts its own asset range turned an unmodelled ROM epoch into a loud failure
at the exact frame instead of into plausible parity data normalized against the
wrong song.

Their corrected rule closes a window at either ROM epoch that replaces the music
track RAM wholesale: a `Sound_PlayBGM` dispatch, or a `cfFadeInToPrevious`
restore at `$72B14`, verified by opcode rather than label because labels there
have drifted. The window opening at a restore is normalized against the song
being restored.

**This improves the plan rather than complicating it.** Under the corrected
rule the interrupt and the resume are two adjacent windows: the `$88` jingle
window, then a window opened by the restore and carrying the interrupted song's
id. "Compare state and writes across the interrupt and resume" is therefore a
comparison over that adjacent pair, with a ROM-defined boundary between them,
and either window can be published on its own because the capture probe already
writes one file per window.

It also sharpens the expected divergence. At the restore boundary the ROM
continues the interrupted song from restored RAM with a fade-in armed at
counter `$28` and the rest bit set on every playing track. The engine host
would load that window's epoch song fresh from the ROM and begin it at its
start, and its sequencer has in any case deactivated every track at the `E4`.
The second window should therefore diverge from its first tick, in track
state before writes.

One consequence worth carrying: ordinals after a run's first restore shift by
one per restore, so anything citing a window must quote its epoch frame
alongside its ordinal.
