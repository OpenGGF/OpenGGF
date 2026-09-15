# S3K Sandopolis Act 2 coverage matrix

Canonical slot: `S3K_SANDOPOLIS_2`; ROM zone `$08`, act index 1, SKL pointer set.
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

Decoded checkpoint placements: `$02` at `($0860,$05C8)`, `$03` at `($13F0,$0428)`, `$04` at `($1F00,$0108)`, `$05` at `($3280,$01A8)`, `$06` at `($4EC0,$04A8)`.
Physical activation, every native team's death/reload and repeated reset are open.

## Behavioral obligations

| Boundary | Implementation / test binding | Reachability | Rewind | Native behavior | Pixels |
| --- | --- | --- | --- | --- | --- |
| PARALLAX / BACKGROUND MODES | Dedicated event-selected owner missing; generic fallback is not coverage | Entry pyramid, outdoor section, sand rise and boss mode boundaries open | Each mode/vertical-wrap transition and forward replay open | `sub_566D2`, `sub_566E8`, `sub_56706`, `SOZ2_BGDrawArray` | Standard/wide scroll bands, seams and native sequence comparison open |
| ANIMATED TORCH TILES / PALETTE | Custom torch path and live fade-accumulator coupling remain open; generic AniPLC alone is insufficient | Light pull across darkening/brightening cadence open | Timer/frame/fade, art restore, boss inhibition and reload open | `AnimateTiles_SOZ2`: old-byte frame sequence 0/1/2, eight-pass period, six-tile DMA; pinned intensity branch | Torch pixels must agree with palette at adjacent steps; GPU visibility open |
| ENTRY / LOAD / RESET | Existing ROM loading; SOZ event/scroll owners remain missing at baseline | Full entry product open | Load timeline/reset isolation open | Unmatched | Unmatched |
| Quicksand entry/held/release | `TestSozQuicksand`: four variant branches, unsigned bounds, input and clock tests | Act 1 short cold route: `TestSozAct1QuicksandRoute`; Act 2 binding/traversal open | Local slide cooldown reconstruction; full registered-state before/contact/release spots open | Native Act 1 acquisition/held force observations corroborate source; no full engine sequence match | Invisible owner; terrain/palette presentation unverified |
| Sand-rock rolling landing / breakup / removal | `SozBreakableSandRockObjectInstance`; saved animation and owner standing latch | `TestSozSandRockProduction`: positioned first-rock spot; cold reachability open | All registered state restored and replayed twice at break, phase 6 and phase 24 removal | Source-derived; mixed-rider and offscreen retained-latch unit checks; native trajectory unmatched | ROM mapping/art checks pass; Act 1 shares the inspected mapping; Act 2 visual comparison open |
| Pushable rock / edge fall / track ride / stop | `SozPushableRockObjectInstance`; ROM track and native push priority | `TestSozPushableRockProduction`: first-rock positioned push, board/brake and complete ride at 320; cold reachability open | All registered state restored/replayed twice at push, initial fall, horizontal start and terminal; boarding also covered | Source-reviewed and real rider carry tested; native trajectory open; subtype `$87` coupling is covered by the connected-mechanism continuation | ROM mapping/art checks pass; shared Act 1 display inspected; Act 2 pixel comparison open |
| Other traversal objects / badniks | Inventory lists concrete shared factories versus placeholders | Open | Before/contact/held/release and creation/deletion open | Unmatched | Unmatched |
| CHECKPOINT / DEATH | Five authored checkpoint records; live activation/reload tests needed | Open | Respawn/reset isolation open | Unmatched | Unmatched |
| WORLD / CAMERA / EVENTS | Dedicated coupled owners required | Open | Before/active/after sand rise, camera lock, terrain/palette changes open | Unmatched | Unmatched |
| BOSS / EXIT | Route slice 6: end boss, wall reconstruction and outgoing transition | Open | Before spawn/attack/hit/defeat/cleanup open | Unmatched | Unmatched |
| Darkness / switch / ghosts / torches | Native pilot only; engine owners remain missing | Native ordinary-input Tails switch pull observed; engine route open | Before/at fade steps, grab/pull/release and capsule/checkpoint ghost state open | 900-frame darkness and four-frame palette cadence observed; independent P2 switch ownership proven | Native PNGs inspected; engine comparison open |
| Vertical wrap / rising sand | Catalogue identifies extended wrap and special-event collision | Open | Cross-wrap and moving collision replay open | Unmatched | Unmatched |

## Spring-vine continuation

All five placed `$3F` vines now bind to the shared SOZ spring-vine implementation
and ROM-backed art. The Act 1 first-vine route supplies representative local
mechanic/rewind evidence; it does not establish ordinary reachability, respawn,
phase coverage or rendered parity for these Act 2 placements. Those remain open.

## Execution evidence

See the execution plan for exact command, commit, configurations, results/skips
and external native capture directory. Pending tests are not passing evidence.
The unit and short-route checks do not satisfy the remaining full act matrix.

## Loop-exit and solid-sprite continuation

`TestSozRouteControllersProduction` exercises positioned landings on both `$49`
shapes in both acts, with whole-registry restoration and forward replay twice at
landing. Representative width 640, S1 donor and mixed followers supplement native
320 checks. Static dimensions are viewport-independent; no donor movement branch
exists in either new owner. Full width/donor/team and cold-route coverage remain open.

Act 2's first `$3B` loop exit has independent positioned capture, held movement and
release checks for Sonic, Tails and Knuckles, plus Sonic at width 640. The captured
player bypasses ordinary movement using the existing native full-control contract.
Local tests cover speed/bounds/routine gates, literal radii, fixed-point fractions,
release equality, subtype bit 7 masking and independent participant restoration.
No full route or matched native trajectory/pixel certification is implied.

## Connected mechanism continuation

All 41 placed `$42` pillars, 19 `$45` push switches and 20 `$46` doors have
concrete ROM-backed owners. `TestSozMechanismsProduction` starts before the switch
at `($2630,$1B0)`, charges shared channel 8, jumps across the switch and downstream
door at `($268C,$1C0)`, then continues until the switch becomes an invisible decay
owner. Whole-registry restore/replay spots cover charge, opening, traversal and
retention. Cases cover Sonic/Tails at 320/640, solo Tails, solo Knuckles, mixed
followers and an S1-donor Sonic/Tails route; each verifies the configured roster,
width and donor movement capability. This is representative breadth, not the full
viewport × donor × character product.

A separate placed horizontal pillar at `($303F,$2C0)` covers landing and carried
movement with whole-registry rewind. Local mechanism checks cover both door
orientations/signs, shared trigger interoperability, exact decay boundaries,
retained replacement, native slot invalidation and upper/lower spiked faces.
The special rock at `($4770,$5B5)` publishes `_unkF7C4` for the switch at
`($4830,$5B0)`; the consumer is `SOZPushSwitch.sub_41AA8`, correcting the earlier
attribution to the door. Its positioned production spot covers link publication and invalidation on
fall, independently of the ordinary channel-8 puzzle. Positive contact is covered
locally; full positive placed-puzzle reachability remains open. Exact outcomes
belong to the execution plan.

Native channel-8 observations corroborate charging, door displacement and passage.
They are not matched engine trajectories or pixel certification. Cold reachability,
all puzzle placements, full load/death/checkpoint breadth, lighting/ghost events
and boss/exit obligations remain open.

Push-switch visual follow-up: the fixed frame-0 track is painted before the
frame-1 moving body, matching native main-before-child SAT precedence. The
connected-mechanism capture is repeated at the same entry/input for overlap QA;
this local correction does not close the inherited whole-act visual gaps.
