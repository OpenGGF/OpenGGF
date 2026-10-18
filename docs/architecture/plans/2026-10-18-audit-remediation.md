# October 18 audit remediation

Base: `8030832d2ab6080e5324cdfe35e971677fd97b41` on `develop`.
Worktree: `.worktrees/ai-audit-remediation`, branch `bugfix/ai-audit-remediation`.
Scope: repair the twenty findings from the read-only audit without changing native
physics, trace authority, public API signatures or the network wire format.

## Decisions

- Block held rewind during append-only input recording. Truncating movie buffers
  would require recording state to participate in world restoration and is unnecessary.
- Retained round results reserve their wire slots until the next round. New members
  use another protocol slot while the configured cap still limits active players.
  Adding participant identity to the wire was rejected to preserve protocol compatibility.
  This bounds distinct retained-result sessions to 256 wire slots; exhausting them
  blocks replacement admissions until the next round, including a returning owner.
  Removing that bound requires a separate participant-identity protocol change.
- Keep verified bests separately from pending improvements; rejection and timeout
  restore the last verified result. Near ghost streams expire after 120 local advances
  without an aggregate and can resume from a new jitter buffer.
- Read legacy saves as before, enumerate both locations, and remove the legacy
  fallback before deleting its canonical replacement. A failed deletion reports false.
- Share catalog metadata compatibility with playback; strict manifest decoding still
  rejects missing identity, while the existing compatibility reader tolerates only that gap.
- Allocate required empty editor terrain before optional spare slots. Object-backed
  rings retain native 12-bit Y; separate authored ring entries retain unsigned 16-bit Y.

## Regression map

| Audit finding | Regression owner |
|---|---|
| 1 Legacy save resurrection | `TestModStorage` |
| 2 Rewind during input recording | `TestGameLoopSpecialStageRewindGate` |
| 3 Negative camera Y painting | `TestEditorMouseTransform` |
| 4 Verified best fallback | `TestHostRoundEngineVerified` |
| 5 Late podium verdicts | `TestClientRaceSession` |
| 6 Reused result slots | `TestRoomHost` |
| 7 Tunnel close cleanup | `TestHostMasterLink` |
| 8 Near-to-far ghost transition | `TestRemoteGhostPlayback` |
| 9 Joining during votes | `TestRoomHostVote` |
| 10 Stereo PCM registration | `TestDecodedPcmCache` |
| 11 Null movie manifest isolation | `TestUserRecordingManifestJson`, `TestUserRecordingCatalog` |
| 12 Missing movie engine identity | `TestUserRecordingSessionLauncher` |
| 13 Duplicate recording control tick | `TestGameLoopTitleCardPause` |
| 14 Null YAML startup | `TestLegacyConfigMigration` |
| 15 Legacy Player 1 Start | `TestConfigMigrationService` |
| 16 Blank-project capacity | `TestEditorProjects` |
| 17 Separate ring height | `TestEditorSpawnFactory` |
| 18 Pending Mod Manager budget | `TestModManagerScreen` |
| 19 Codec placeholder argv | `TestFfmpegCommandTemplate` |
| 20 PNG transparency keys | `TestPngTransparency` |

## Validation checkpoint

The new regressions failed before their corresponding fixes. The first batch
reported five assertion failures and one stereo-registration exception. The second
reported eleven assertion failures and four errors (null manifests have two checks).
After repairs, the combined 21-class focused selection passed 201 tests, zero
failures/errors/skips, using Java 25 and queued Maven `--lean -Dmse=off -Dtest=… test`.

High-reasoning GPT-6.1-Sol reviewed implementation `eb5e3f959` and found one
introduced expiry callback identity error: collected map entries remained live
while fallback restoration replaced their values. A new regression reproduced
attempt 1 being reported instead of expired attempt 2; the repair snapshots the
expired Best before restoration and uses it for both callbacks. The review also
noted the documented wire-slot exhaustion tradeoff and a pre-existing empty
podium when joining ROUND_END before any later delta; that separate join behavior
is outside the twenty audited fixes.

Combined change-based smoke/guards remain pending. The plan selects 3,503 ordinary candidate classes because shared GameLoop
and capture code are touched. No release-only full suite is authorized or needed.
