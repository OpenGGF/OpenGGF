# OpenGGF 0.8.prerelease — Development changelog

Unreleased. Development since the [0.7.20261105](CHANGELOG.0.7.md) release. Add entries
under the matching area heading; see the
[documentation policy](docs/guide/contributing/documentation-policy.md#release-note-destination).

## Gameplay and presentation

- **Sonic 1's Sonic on Sonic 3&K tumble objects:** Sonic 1 has no tumble frames,
  so a donated Sonic 1 Sonic riding a Flying Battery wire cage or another tumble
  object vanished. He now keeps his own walk and run frames there, and a donor's
  own tumble frame base (Sonic 2 Tails') carries into its translated profile.
- **Lightning-shield rings in Sonic 3&K replays and captures:** rings pulled in
  by the lightning shield moved and counted correctly but were never drawn, so
  they vanished from their places instead of flying to Sonic and sparkling.
  An object-system reset (the trace-replay start, among others) dropped the
  ring slots' link to the object pass. The link now survives resets. This
  regressed in 0.7.

- **S3K support notice says beta:** starting Sonic 3 & Knuckles from the hub
  now shows the beta notice, matching the 0.7 release notes. 0.7 shipped with
  the alpha notice. Players who hid the alpha notice see the beta one once.

- **S2 Crawl:** paused proximity includes its zero countdown pass, Super Sonic
  selects ordinary enemy contact, and accepted enemy attacks retire through the
  shared touch owner. The Sonic+Tails chain advances from CNZ2 to HTZ2; later
  route errors and the special-stage return timing differences remain open.

- **Knuckles' lives name in Knuckles in Sonic 2:** without the optional lock-on
  dump, the "K.T.E" name beside his HUD head was green. The stand-in Sonic &
  Knuckles icon was drawn for a different palette line, and its name went
  through the same colour conversion as Knuckles' sprites. The name now takes
  the original game's yellow-orange, white and black, matching the lock-on
  dump's art pixel for pixel. Sonic 2 with the Sonic 3&K donor and Knuckles as
  the lead had the same green name and is fixed too.

- **S2 diagonal launcher springs:** capture both riders before deciding to
  release or decompress. A fresh Tails capture now uses the native launch force
  and can hold Sonic on the spring. The complete-chain CNZ2 segment is green;
  later campaign and art-gap failures remain open.

- **S2 Competition (two-player mode):** Choose 2 PLAYER on the Sonic 2 title. Sonic 2's own two-player mode runs natively, with Sonic on
  controller 1 and Tails on controller 2. Its menu selects Emerald Hill, Mystic
  Cave, Casino Night or the special stage. Each zone plays two acts with
  separate rings, lives, score, time and results, then zone and game results
  with their own music; a tied zone goes to a special-stage tiebreaker.
  EHZ spiral jumps now retain airborne state and upward velocity when releasing
  the invisible path controller, including simultaneous two-human routes.
  A frozen Stageplay EHZ route now races both humans concurrently in each act,
  with opposite act winners, readable results holds and a tied special-stage
  inlet. Eight short frozen Stageplay routes exercise every question-mark monitor
  outcome, with award assertions and six-second neutral holds for native comparison.
  The two-act route is ENGINE-qualified only: cold native replay encounters level lag
  before the first finish and does not reach act 2.
  The screen renders at 320x448 progressive resolution with half-height pixels,
  preserving every row of the native 8x16 cells without interlace combing.
  It splits at progressive line 216 through the shared Competition renderer; each
  half draws its player's planes, sprites and HUD from the ROM's two-player art.
  Title and menu transitions fade, and both halves show the complete title card
  before player control and the level clock start. Stageplay's visible flip checks
  use the same complete native cells as the progressive display. Signposts publish
  their native two-player pole and animated face art; act timers spend the interrupt before a
  finish crossing. Teleport monitors swap the players' positions,
  speeds, cameras and backgrounds. Badnik, bumper, bonus-block and cage
  scores go to the player who earned them, and the Casino Night slot machines
  and cages serve both players. Casino Night round bumpers, bonus blocks, slot
  cages and prizes now enter both sprite pages; plungers retain their moving head
  and fixed base with the ROM two-player art, palette and priority. Enemies whose ROM routine ends in
  `MarkObjGone_P1` retire once both cameras are more than `$300` away and
  reload when revisited; the finish-grace countdown runs before players move;
  GAME OVER and TIME OVER cards appear in both views. The competitive special stage runs both players
  on one halfpipe with its checkpoints and results. Its entry fades the previous
  results to white, holds the shared load delay, and runs startup waits; live
  pacing uses the same lag approximation as the one-player special stage. Held rewind is off in
  Competition; the progressive presentation, seam and lag-frame limits are in
  [known discrepancies](docs/status/known-discrepancies.md#s2-competition-split-screen-native-lag-and-rewind-scope).

- **S2 and KiS2 badnik chains are shared:** one chain counter serves both
  players, as in the ROM, so CPU Tails' kills and landings now continue or reset
  Sonic's chain. Every ROM landing that clears it does so, including Knuckles'
  landings in Knuckles in Sonic 2. Smashable ground and breakable blocks score
  through player 1, and only once their points popup has an object slot.

- **S2 breakable fragments use native slots:** smashable ground, breakable
  blocks and Oil Ocean launchers put their first piece in the parent's slot and
  later pieces after it, stopping at the first full slot. Fragments no longer
  depend on whether their art is ready to draw.

- **S2 points popups draw in front:** the score popup sets the high-priority
  art bit, as `Obj29_Init` does, so it is no longer hidden behind
  high-priority terrain.

- **S2 and KiS2 touch checks reach off-screen objects:** the ROM's touch scan
  has no on-screen test, so objects outside the main camera can now hurt,
  give rings or be destroyed. Sonic 1 keeps its on-screen test. The rule is a
  new `ObjectInteractionRules` component, which changes that record in the
  unpublished 0.7 candidate Mod API.

- **S2 Casino Night conveyor belts carry the sidekick:** `Obj72` moves both
  players, as the ROM does, so a CPU sidekick rides the belts too, in Sonic 2
  and Knuckles in Sonic 2.

- **S2 Casino Night horizontal flippers accept the full landing width:**
  horizontal `Obj86` uses the ROM's `$18` landing half-width instead of 8, so
  players land on the flipper's whole top. Vertical flippers are unchanged.

- **Freed animals keep their points popup across rewind:** a destructive
  rewind restore now keeps how an animal spawns its points child, so the popup
  appears and later objects keep their identities after restore and replay.

- **S2 title cards follow Obj34:** the card's parts enter, wait and leave in the
  ROM's slot order and timing. Native Emerald Hill 1 recordings match its part
  positions on every frame and its pixels at seven sampled frames.

- **S2 signpost sparkles and thrown coconuts keep their native identities:**
  signpost sparkles stay ring-sparkle objects and Coconuts' coconuts stay their
  own child object, so rewind recreates both with their animation.


- **Time Attack Competition mode and the Sonic 3&K Competition arenas:** a
  multiplayer room can now race in Competition mode as well as Time Attack.
  Everyone starts at the same scheduled moment, there are no restarts, and the
  first past the post wins. Placement counts each player's own frames from GO,
  so network lag never changes the order. The host picks the monitor rules
  (normal, randomised, competition items or off) and a cutoff after the first
  finisher. Sonic 2 and Sonic 3&K acts can be raced in either mode.
  - Sonic 3&K's five Competition arenas (Azure Lake, Balloon Park, Chrome
    Gadget, Desert Palace and Endless Mine) are new Competition-only tracks.
    Each player races them alone in one full-screen view, under the shipped
    Match Race rules: five laps, losing a lap for a backwards crossing or a
    death, false starts and the 9'59"99 limit.
  - Lap times, the total and the best lap are shown and ranked.
  - A teleport monitor swaps you with the nearest racer through the host.
  - Rooms without a verifier show the finish order labelled UNVERIFIED.
  - Solo arena runs now swap shoe state with the empty second slot, as the
    ROM does, instead of ignoring the swap item.

## Modding and tools

- **Simpler creator examples:** decoded native-level patches can add registered
  objects with engine-assigned placement identities while retaining native level
  services. Scene menus share the engine's menu theme and public drawing helpers;
  presentation-only object collections reconcile checkpoint and level changes.
  Starpost Valley uses its loaded terrain's palette claims, and Slay the Robotnik
  uses supported immutable card-type enums.

- **Two-player Competition routes in both editors:** the existing Route / Tape
  workspace now authors S2 REV01 title/menu programmes, P1/P2/joint goals and copied
  fact predicates. Isolated workers plan, replay, repair and qualify both pads;
  untouched route pairs keep their exact bytes. Locked inputs and player colours
  are shared across HUD/desktop, unrelated scenery is hidden, and observed native
  anchors use the shared BK2 splice. Provisional exports remain visibly blocked;
  engine qualification does not imply native parity. The Stageplay route planner
  behind them plans two-player goals and repairs or continues a route after a
  frozen prefix. Joint search also combines independent charge, roll and jump
  policies for the two pads, scores damage to either actor, and prunes stalled
  trials. The desktop editor's side of this lives in the separate
  OpenGGF-Editor repository.

- **Recording audio recovery:** viewport recording retains the real audio lease
  when detach is refused, even after switching to silence. Stopping or restarting
  retries that lease before acquiring another, avoiding an orphaned audio tap.
  Closing the controller also ends admission permanently, so a delayed encoder
  finalizer cannot clear resources acquired by a recording started after close.
  S2 music requests now use native ROM mailbox bytes while retaining playback IDs, fixing CNZ 2P audio crashes and music priority/classification.

- **Seeded Data Select captures:** `GameplayCaptureTool --save-slots <file.json>`
  writes declared save slots through the production save manager into the
  capture's private save root before a title-first capture, and `--extra-teams`
  sets the Data Select extra team combos for that capture. The Sonic 3&K hero
  reel uses both to show custom-team saves. Title-first headless boots also run the
  donated Data Select warm-up that Engine runs before its startup mode, so a
  Sonic 1 or Sonic 2 capture with S3K donating shows the generated zone previews
  instead of S3K's placeholders.

## Maintenance and release preparation

- **Engine quality and contributions:** the README and contributor entry point now
  acknowledge maintainability weaknesses and invite focused improvements to object
  lifetime, collision contact, rewind reconstruction, trace diagnosis and navigation.

- **Contributor readability:** live objects, slots and stable identities share
  population storage; actor reconstruction and contact memory have separate owners
  behind the existing runtime entry points. Mutable contact memory is package-private
  beside its collision controller, distinct from the immutable saved contact state.
  Retiring multipart solids expires
  owned contact bits and frame history without releasing player state. Contact
  restore discards uncaptured future observations. Sole spawn-keyed dynamic solids
  now expire contact bits without clearing surviving shared owners. Constructor-child
  rewind adoption uses captured parent families, preserving interleaved structural
  links. Routine-spawned Clamer springs and Orbinaut satellites also resolve their
  captured parent identities; legacy and unknown families retain their existing
  proximity lookup. Independent children can recreate when a parent defers to
  player refresh, and CNZ defeat candidates retain their ship family. Generic recreation installs
  construction services centrally; supported exact-spawn recipes rebuild once,
  avoiding discarded constructor graphs while preserving custom hooks and the
  fallback for declarations without that constructor. Destroyed
  object paths share unload and retirement phases within their existing schedules.
  Ordinary family/reference/coverage guidance points to the actual owners.
  Compact and scalar subclass fields share hierarchy ownership; diagnostic tools
  check actual capture membership and distinguish structural handles from dropped
  state. Pixel-oracle failures retain the rendering mode and captured frame.
  Production badnik and contact witnesses, shared coarse-X arithmetic with
  explicit native/wider bounds, and
  a typed rewind HUD frame make contribution paths easier to follow. HCZ owns
  its next screen-shake value without using the renderer as scratch state.
  Standalone trace replays print their exact test/report/triage route; triage reads
  compressed fixture auxiliaries, preserves pre-trace evidence and distinguishes
  incomplete or unknown completion from a completed comparison. Standalone replay
  method entry retires verified prior evidence for the same invocation and scope
  before loading the fixture; pre-method conditions and extensions remain outside
  that boundary. Triage exposes missing auxiliary references and absent comparison
  coverage without changing the published gate or mismatch totals. Replay guidance
  routes segment diagnosis through the correct lane, movie and report owners.
  Native search hints use each game's behavior routines and mutable layout
  owners; canonical player routine/subpixel fields stay with player physics. Required measurement reading
  is a current rule table and condition finder, with historical cases retained
  for selective lookup.
  Triage selects its first divergence, bootstrap and evidence window from the
  saved verification scope while retaining global counts and legacy global views.
  Replay stopping, release assertions and publication share the verification scope
  resolved before fixture assembly, including subclass-provided scopes.
  Unclassifiable groups fail a narrow diagnosis through both JSON and direct-model
  entry; caller mutation cannot alter the captured evidence.
  Replay completion metadata has one report-owned value and strict wire codec; ordinary publication
  no longer reparses and rewrites its own JSON. Standalone reports retain the
  existing carried-entry evaluation's provenance, disagreements and unresolved
  notes; triage keeps those conditional entry diagnostics separate from mismatch
  and completion decisions. Scene presentation spells out
  capture, restoration and failure cleanup in operation order. Retained raw-field
  diagnostics follow that storage and are exercised on successful test paths.

- **Fresh-level admission:** named phases expose title completion, initial
  boundary publication and player assembly while preserving their existing
  timing, pause and failure behavior. Creator-controlled native destinations
  now finish their normal title and player assembly before admitting gameplay,
  sharing the ordinary host's native title iteration. HOLD and explicit
  presentation omission remain separate choices. In-level modal overlays remain
  visible above a controlled native title in both live and captured frames.

- **Scripted website hero reels:** `tools/media/hero_reels.py` regenerates
  the Sonic 1, Sonic 2 and Sonic 3&K title-card reels from the current engine
  with one command. It replaces the hand-cut 0.6 Resolve edits. Each game's shot
  list is a committed manifest. Each shot replays a committed trace fixture or
  BK2 headlessly, and shots are joined with hard cuts. The build fails on a
  missing ROM, a short, black or frozen shot, or a death inside a shot. It
  writes a poster, a contact sheet and provenance (engine commit, manifest hash,
  frame ranges) beside each reel. The reels keep the 0.6 cut's feature beats as
  well as its zones: live rewind undoing a hit or a fall, sidekick chains (ten
  Sonics in Scrap Brain, a crowd at the Emerald Hill 2 boss), borrowed art and
  Super Sonic in Sonic 1 and Sonic 3&K, Sonic 3&K's Super Sonic in Sonic 2
  with Knuckles following, Knuckles climbing a Hill Top cliff in Sonic 2, Sonic 1
  rolling under the Flying Battery elevator, a Casino Night jackpot,
  custom-team Data Select saves, and every Sonic 3&K zone with its bonus
  and special stages. Shots sit inside their acts, and Super Sonic transforms
  only once the replay has collected 50 rings. Knuckles in Sonic 2 shots read the
  verified lock-on dump, so the HUD shows the chip's own art and colours. The reels render without native
  sprite dropout, as normal play does: `TraceCaptureTool --no-sprite-limits` turns
  off the per-line limits that trace captures otherwise keep for ROM image
  comparison. `GameplayCaptureTool --rewind FRAME:LENGTH`
  holds the live rewind key over a window and draws the rewind plate,
  `--timer FRAMES` sets the level timer a positioned start carried in,
  `--emeralds` also takes Sonic 1 and Sonic 2's six emeralds, and
  `--title-screen` now accepts `--donor`, since boot activates the donor before
  the title. `GameplayInputBranchTool` takes `--input-start`, `--resume`,
  `--resume-from` and `--skip-rows`, so a shot's input can be derived from a
  level inside a complete-run movie, with one change and the recording's own
  rows after it.

  frame ranges) beside each reel.

- **Trace coverage and canonical fixtures:** independent strict replay covers all
  163 segments of the Knuckles super-emerald, Mega, Sonic+Tails and KiS2 chains,
  plus six previously untested standalone slices. Existing canonical identities
  replace 30 duplicate fixture directories. The S1 complete-emerald chain now
  records native PLC readiness and real ending/credits initialization timing,
  clearing its four queue frontiers and terminal art frontier. Ending loads
  resume actual ROM-backed work; credits use their native wait and fade loops.
  Generic ending callers retain their gameplay order, and failed initialization
  preserves the original error while cleaning up its owned continuation.
  Recorded physics, auxiliary observations and movie input are preserved exactly.
  The LRZ title owner submits its three enemy archives in native order,
  preventing a later results-art FIFO overflow. AIZ2 art preparation preserves
  Knuckles' terrain-only branch; Sonic/Tails also submit the battleship object
  art. The boss-glide continuation remains open pending the Knuckles SOZ route.
  Existing animation and route frontiers remain visible, including
  the fresh sanctuary's intermediate load state; its routine comparison does
  not establish player dispatch.
  Special-stage entry honors the recorded team and production lock-on module;
  the single entry-state import consumes S2-family manifest destination rings.
