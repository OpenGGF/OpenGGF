# S3K bonus-stage load and return matrix

Qualification is limited to production boundary pilots on b930df4868 plus
`TestS3kBonusStageReturnProducts`. Request12506 completes2026-10-14 15:17:45 UTC:
all five cases pass, no errors/skips. Exact command and test hashes are in the
[completion plan](../../plans/2026-10-13-s3k-playable-completion.md#ssz-cold-boundaries-and-bonus-return-pilots-2026-10-14).

| Stage | Main / ordered followers | Width / donor | Interior replay | Returned origin replay |
|---|---|---|---|---|
| Gumball | Sonic / Tails |320 / off|Two18-tick whole-world cycles|Two45-tick whole-world cycles|
| Pachinko (Glowing Sphere) |Knuckles / Tails,Sonic|352 / off|Two18-tick whole-world cycles|Two45-tick whole-world cycles|
| Slot Machine |Tails / Sonic,Sonic|400 / off|18 ordinary ticks; whole-stage rewind unsupported|Two45-tick whole-world cycles|
| Gumball |Tails / Sonic,Sonic|800 / S2|Two18-tick whole-world cycles|Two45-tick whole-world cycles|
| Slot Machine |Sonic / Sonic,Sonic|800 / S1|18 ordinary ticks; whole-stage rewind unsupported|Two45-tick whole-world cycles|

Each case boots SOZ1, makes the declared logical stage-entry request and waits
for the actual GameLoop load/title/fade release. It checks coordinator type,
saved origin, fresh object manager, main identity, width and lives. Slot owns
the interior player and removes active followers. A declared requestExit
stimulates completion; the actual load returns to the saved origin, restores
the ordered roster, clears Slot runtime and retires both outgoing timelines.
Full-world comparisons retain every registry key, with both restore directions
and two forward replays. No raw restore crosses a load.

These pilots do not certify cold traversal of the starpost portal, physical
entry, ring/reward/outcome mechanisms, the broader stage mechanisms. The full finite load/return product is qualified
below, including528px and all admitted native/donor main/roster branches. Whole Slot rewind remains an
explicit unsupported contract, not implied by a controller-only snapshot.
Broader stage mechanisms, actual exit causes, load/respawn boundaries and
matched native screenshots/audio remain open.


## Finite load and return products (2026-10-14)

Request70993 completes terminal0 at19:17:41 UTC on coherent
69e4f51d3a37d9517cec3340588daca68b41c1c6 plus the final test draft.
425/0/0/0 passes (4:51 Maven,218.0s test execution;733s queue waiting is separate):
five independent pilots precede420 actual boundary products. The existing
`CanonicalEntryMatrixSupport.configurations()` supplies140 distinct supported
products, deduplicating explicit donor substitutions; this generator is reused
without a new participant cap or unsupported donor combination.

| Axis | Actual product coverage |
|---|---|
| Stage |Gumball, Pachinko/Glowing Sphere, Slot Machine|
| Viewport |320,352,400,528,800|
| Main/donor |Native Sonic/Tails/Knuckles; S1 Sonic; S2 Sonic/Tails|
| Ordered roster |Solo, single, mixed, duplicate and seven-followers; donor-compatible deduplication|
| Entry/return |Production logical request; actual title/fade/load; fresh managers; both history floors|
| Returned world |Exact main/camera identity, ordered CPU leader chain, independent duplicate actors, ROM renderer/animation resources, two45-tick all-key restore/replay cycles|
| Interior |Gumball/Pachinko two18-tick all-key cycles; Slot18 ordinary ticks and its explicitly unsupported whole-stage rewind contract|

Configuration overrides and global session/donor state are restored between
cases. These are actual logical-entry/declared-exit boundary products, not
physical portal approach, winning/reward, whole-interior native picture/audio
or all-mechanism certification. Those obligations remain open. Exact command
and source hash are recorded in the [completion plan](../../plans/2026-10-13-s3k-playable-completion.md#finite-bonus-boundary-qualification-2026-10-14).


## Real portal and exit-mechanism products (2026-10-14)

On coherente46f61820069f0d959e59220a46fba9a80aa4426 plus the test draft,
`TestS3kBonusPortalMechanismProducts` qualifies425 distinct cases across two
runs:2258 short pilots5/0/0/0 at20:46:52 UTC (28.313s Maven/5.876s class,
0s queue), then41801 finite products420/0/0/0 at20:54:42 UTC
(5:06 Maven/280.8s class/69s queue). The finite140 supported configurations
and three stage branches are the same admitted product set as above.

Every case starts at a declared SOZ1 local inlet outside the real placed
post1, walks into it with ordinary controller input and seeds only the declared
carried ring count20/35/50. The actual post creates four orbital stars. Their
ordinary lifetime reaches collision enable; a declared local approach targets
the observed live orbit position without writing its angle/lifetime/request.
Actual overlap consumes the checkpoint portal and the real GameLoop load follows.
Gumball's placed exit range, Pachinko's live trap capture/countdown and Slot's
actual loaded goal-cell collision own the exits; no requestExit/startGoal hook
is called by this test. Local free-movement exit inlets are declared scenarios,
not cold whole-stage routes or reward/win certifications.

Actual stage loads and returned origin loads retain fresh managers, retired
history, exact ordered/configured main/roster/camera identity, width, lives and
usable native/donor resources. Gumball/Pachinko retain two18-tick all-key world
replay cycles and each returned origin retains two45-tick cycles. Slot still
performs18 ordinary interior ticks and asserts its current unsupported whole-
stage rewind contract. Those interior limitations remain active work.

The Slot board, player start, prize/target/reel and cell-animation assets now
load from an immutable `S3kSlotResources` bank over the actual runtime ROM.
Instruction-owned geometry and timer constants remain separate. Original-ROM
byte matches identify layout1E4084 and start1E4080; alternate declared banks
reach real layout/reel/prize/animation consumers and malformed banks fail
before bootstrap mutations. Complete Slot snapshot/recreation remains open,
alongside other reward/stage mechanisms, cold traversal and matched native
images/audio. Exact commands/source hash are
in the [completion plan](../../plans/2026-10-13-s3k-playable-completion.md#bonus-portal-and-mechanism-qualification-2026-10-14).

## Slot ROM bank qualification — 2026-10-14

Root0a05aa118 plus the resource candidate replaces every consumed copied
board/reward/reel/animation table with ROM reads. The explicit pure mechanics
fixture is test-only and never a runtime fallback. `TestS3kSlotRomData` checks
alternate start/layout bytes, signed prize words, target rows, three real reel
consumers, cell animation publication/first-zero termination, malformed banks,
defensive copies and the verified original layout hash. Warmed panel draws
use package-owned read-only reel views; public array reads retain copies.

Build23604 passes test-compile at21:26:54 UTC,1:09,zero tests. First focused
5254 completes166/1 failure/0 errors/skips at21:27:47 UTC,28.461s,zero wait:
165 checks pass and the warmed panel allocation oracle catches defensive
copies on every draw. Corrected14603 passes27/0/0/0 at21:29:53 UTC,1:18,
zero wait, including that independent allocation gate. Actual normal-lane
28066 passes75/0/0/0 at21:32:10 UTC,1:20,zero wait: five physical portal/exit
pilots, five resource contracts, five legacy cell-animation consumers and
60 mandatory S3K loading/assembly/decode neighbors with all three absolute
original-ROM properties. Final first-zero parser71315 passes13/0/0/0 at
21:35:24 UTC,1:14,zero wait. These scoped invocations overlap; their totals
are not added as one green run. Current combined smoke remains pending.

Whole Slot interior rewind remains unsupported in the portal fixture. The
ROM-bank repair does not certify reward mechanisms, cold traversal, native
pixels or PCM. Those obligations stay active.


## Actual Slot mechanisms and live history — 2026-10-14 candidate

The later complete-runtime candidate on Root3d633ffe5 passes all140 finite
Slot products in request58926,225.1s test execution with zero failures/errors/
skips for that method. Native/S1/S2 donor-compatible products span320,352,400,
528,800 and solo, mixed, duplicate and seven-follower configurations. Actual
placed SOZ post/stars, controller approach and normal title/fade/load own entry.
Declared local interaction inlets exercise loaded ring, bumper, spike and
wall cells; cage/reel resolution, natural rewards, removal-and-restoration
recreation and payout expiry; goal wind-down/fade and the real origin return.
The installed live controller verifies the load floor, held backward/forward
and seek, with all registry keys compared in both restore directions and two
replays. These are finite mechanism/history products, not cold interior routes.

The previous unsupported Slot rows above describe their original source.
The current candidate advertises complete within-stage rewind and captures
its stage RAM, both layout/render buffers, animators, completed native scroll
output, cage/reward identities and movement/exit phases. The runtime bank also
reads the selected background deformation599BE and band rows599DE from ROM.
The enclosing319-test run is red: one independent SSZ body-pose assertion,
six synthetic coordinator ROM prerequisites and two new shared-helper
null-context regressions. The latter source repairs await positive focused
checks. A read-only audit also found a stale Tails tail parent; its new actual
coordinator/late-dispatch/render regression is prepared but unrun. Accordingly
this finite pass does not certify Slot visual/native parity or delivery.
Matched native screenshots/audio, cold traversal, goal-fade DPLC attribution,
composed smoke/guards and integration/push remain open. Exact invocation and
source scope are in the [completion plan](../../plans/2026-10-13-s3k-playable-completion.md#complete-slot-runtime-candidate-and-native-ssz-parent-header-2026-10-14).

## Native Slot sprite banks and complete rewind — 2026-10-15

This supersedes the pending items of the 2026-10-14 candidate above. The
dedicated Slot sprite now owns its native host presentation
(`S3kSlotPlayerArt`): `loc_4B9E8` installs host `Map_Sonic`/`Map_Tails`/
`Map_Knuckles` and scripts by `Player_mode`, `ArtTile_Player_1` (Tails alone:
`ArtTile_Player_2` plus a fresh `Obj_Tails_Tail` parented to the Slot sprite),
whatever donor the ordinary player uses. After each Animate the owner runs
`Perform_Player_DPLC`: `character_id` selects `Sonic_Load_PLC2`/`Knuckles_Load_PLC2`
(shared `Player_prev_frame`, D000) or `Tails_Load_PLC2` (`Player_prev_frame_P2`,
D400), each storing the frame before its empty-cue check. The goal write
`move.w #60,$38(a0)` zeroes `character_id`, so the fade submits Sonic cues: Knuckles
keeps `Map_Knuckles` over Sonic's roll tiles at `$96`-`$9A`, while Tails' D400 body
keeps its last tiles and its tail continues on its own D600 path. Level's
`clearRAM` zeroes the previous-frame bytes on entry. Rewind restores both resident
banks (CPU and GPU), both previous-frame bytes and the selected `character_id`
without animating or submitting cues. The configured player's renderer, scripts
and tail return untouched; the shared atlas indices are re-published on return.
Renderers stay Mod-API-neutral through the internal `PlayerResidentBanks` bridge.

Qualification on Root `3d633ffe5` plus the uncommitted Slot candidate, normal lane,
unset displays, EGL, null audio and the three original ROMs:

| Selection | Finished (UTC) | Tests/failures/errors/skips |
|---|---|---|
| Slot coordinator, live-stepper lifecycle, bonus priority (fresh tail, cleared dust/Super, shared replay priority) | 00:47:46 | 16/0/0/0 |
| Three actual-portal Slot bank pilots plus Slot graph/codec/scroll/ROM/rewind-capability neighbours and the Mod API signature surface | 01:00:51 | 59/0/0/0 |
| `finiteSlotMechanicsAndLiveHistoryProducts` | 01:14:27 | 140/0/0/0 |
| `TestS3kSlotsBonusTraceReplay` Knuckles (1024 frames) and Sonic+Tails (5259 frames) | 01:20:59, 01:26:02 | 1/0/0/0 and 1/0/0/0, zero errors/warnings |

The three pilots were the 79598 reds (Tails400 D400 mutated in the fade; S1-donor800
and Knuckles320 restored banks differing). They now also assert host Sonic PLC bytes
for every changed fade mapping on Sonic/Knuckles mains, Tails tail cues resident for
the current tail map, host mapping counts inside the stage and the configured
donor's mapping count/bank on return. Opt-in `TestS3kSlotsGlassNative` stays red 5/5
(overlap 0) with and without the new owner in a matched control; its capture shows
Sonic correctly behind the glass, so its priority-toggle premise is an inherited,
separately attributed limit. Native Slot pictures, cold traversal and PCM remain open.
