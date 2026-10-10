# Starpost Valley Real Ruins route matrix

Origin: phase 3 lane, 2026-10-10; base `3da4cd930`, local branch
`feature/ai-starpost-realruins`. Canonical destination
`starpost-valley:ruins`, act 0; generated chamber number is retained scene state.
S3K receives S1 Marble (1–15), Labyrinth (16–30) and Scrap Brain (31–40) kits.
This partial example route does not certify the full level-testing standard.

| Band / chamber | Sonic | Tails | Knuckles | Independent obligations |
| --- | --- | --- | --- | --- |
| Marble / 1 | native solo, 400 | native solo, 400 | native solo, 400 | ENTRY, hit/scatter + result, faint + item loss, native-input shaft descent, whole-registry rewind |
| Labyrinth / 16 | native solo, 400 | native solo, 400 | native solo, 400 | same; flooded chamber25 independently checks native drowning/Bubble Shield |
| Scrap Brain / 31 | native solo, 400 | native solo, 400 | native solo, 400 | same |

`TestStarpostRealRuins.nativeMovementHitReturnAndRewind` checks native act type,
resolved viewport, supplied-ROM packaging, live Game/PlayScreen identity, native
LostRing admission, zero-ring result once, and capture/restore/20-frame full
registry replay. `nativeFaintRunsExistingRules` checks all nine independent
routes. `reachesGeneratedExitWithNativeInput` drives the native pad to the
shaft and checks the next chamber load. `labyrinthUsesNativeDrowningAndBubbleShield`
checks 1,900 underwater frames with Bubble Shield, then native air exhaustion
and FAINTED for each farmer. Set-up positions for drowning are explicit; the
exit routes traverse from the normal spawn with native movement.

Short independent checks cover generated find lifetime/rewind/inventory reload,
day-end TIME_UP and the town's actual Ruins doorway → shaft of light → same
door for all three farmers. Door checks place the native player at the decoded
door floor then use the production town interaction; they are not a walked
farm-to-door route. All forty generated chambers also load through the production additive decoder,
with a same-day/different-morning generation check. Existing creator tests cover deterministic/reachable chamber
generation and landmark/yield rules; they do not substitute for forty native
traversals. No bosses are authored in these chamber bands.

Remaining obligations: widths320/512/640/800, donors, native/mixed/max/duplicate
teams, all forty chambers and seed/day sweeps in the engine, camera boundary
rendering, checkpoint respawn, repeated restart, elevator/menu breadth,
badnik attack/hit/child phases and ore/Record/world-effect replay, exact underwater
palette and full ROM parity. The engine timeline intentionally resets at scene
handoffs; captured creator state remains within each act timeline.

See [design §24](../../designs/2026-10-09-starpost-valley.md#24-the-ruins-on-real-levels-lane)
for the engine seam, rejected approaches and final command/results evidence.
