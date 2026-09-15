# S3K Sandopolis Act 1 coverage matrix

Canonical slot: `S3K_SANDOPOLIS_1`; ROM zone `$08`, act index 0, SKL pointer set.
Status: partial bring-up; no full-act, native-parity or visual certification.
Owning [v2 execution plan](../../plans/2026-09-15-soz-methodology-v2.md) and
[placed inventory](../../research/s3k-zones/soz-object-inventory.md).

## Route and configuration obligations

| Route/dimension | Obligation | Current evidence / gap |
| --- | --- | --- |
| Sonic solo / Sonic + Tails | Cold entry, ordinary traversal, checkpoint/death, boss and exit | Full route open; short quicksand checks tracked below |
| Tails solo | Same, including native character branches and flight interactions | Unassessed |
| Knuckles solo | Verify distinct start/capsule/boss/progression branches from ROM | Unassessed; no unsupported classification |
| Mixed / maximum / duplicate followers | Independent held state, authority, release, death and leader chain | Local quicksand participant test only; production breadth open. Resolve current maximum from production team contract |
| Widths 320/400/512/640/800 | Actual camera/render widths; entry/reset × every supported donor; sensitive interactions and rewind | Representative Act 1 quicksand test only; full per-act breadth open |
| Donors off/S1/S2 | Confirm production support, actual movement profile, mandatory mechanics and rewind | Representative Act 1 S1 quicksand test only; remaining per-act breadth open |

Decoded checkpoint placements: `$02` at `($1780,$0708)`, `$01` at `($1A30,$0428)`, `$03` at `($2760,$0228)`, `$04` at `($2C60,$0628)`, `$05` at `($3F00,$06E8)`.
Physical activation, every native team's death/reload and repeated reset are open.

## Behavioral obligations

| Boundary | Implementation / test binding | Reachability | Rewind | Native behavior | Pixels |
| --- | --- | --- | --- | --- | --- |
| ENTRY / LOAD / RESET | Existing ROM loading; SOZ event/scroll owners remain missing at baseline | Seeded FBZ EXIT_READY → fresh SOZ load verified; full incoming route open | `TestFbzSandopolisTimelineHeadless` verifies load reset and destination replay; checkpoint/death breadth open | Unmatched | Unmatched |
| Quicksand entry/held/release | `TestSozQuicksand`: four variant branches, unsigned bounds, input and clock tests | Act 1 short cold route: `TestSozAct1QuicksandRoute`; Act 2 binding/traversal open | Registered acquisition/held/release capture-restore and forward replay twice for the first strip in all four representative configurations; local slide cooldown reconstruction; other variant production spots open | Native Act 1 acquisition/held force observations corroborate source; no full engine sequence match | Invisible owner; terrain/palette presentation unverified |
| Spring-vine acquisition/tension/launch | `SozSpringVineObjectInstance`; native P2-before-P1 tension, pixel slope and eight-piece child | `TestSozAct1SpringVineRoute`: cold first-vine approach/launch at 320/640 widths, S1 donor and extra follower | All registered state restored and replayed twice at acquisition/tension/launch in each configuration; unit child recreation and independent participant state | 473 native slope/child-height observations match the source-derived arithmetic; full trajectory parity open | Native image and engine eight-piece display inspected; short engine capture ends before vine acquisition, no matched pixel certification |
| Other traversal objects / badniks | Inventory lists concrete shared factories versus placeholders | Open | Before/contact/held/release and creation/deletion open | Unmatched | Unmatched |
| CHECKPOINT / DEATH | Five authored checkpoint records; live activation/reload tests needed | Open | Respawn/reset isolation open | Unmatched | Unmatched |
| WORLD / CAMERA / EVENTS | Dedicated coupled owners required | Open | Before/active/after sand rise, camera lock, terrain/palette changes open | Unmatched | Unmatched |
| BOSS / EXIT | Route slice 2–3: miniboss, door and Act 2 entry | Open | Before spawn/attack/hit/defeat/cleanup open | Unmatched | Unmatched |

## Execution evidence

See the execution plan for exact command, commit, configurations, results/skips
and external native capture directory. Pending tests are not passing evidence.
The unit and short-route checks do not satisfy the remaining full act matrix.
