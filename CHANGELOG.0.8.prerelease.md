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

- **S2 diagonal launcher springs:** capture both riders before deciding to
  release or decompress. A fresh Tails capture now uses the native launch force
  and can hold Sonic on the spring. The complete-chain CNZ2 segment is green;
  later campaign and art-gap failures remain open.


## Modding and tools

- **MICA, an original character framework example:** optional directional homing
  previews airborne homing targets, locks the reticle during strikes, and draws
  native ROM spindash/skid dust for canonical-sheet characters. A fault-bounded
  `onMovementTick()` character hook supports per-tick preview requests. Directional
  strike sheet poses follow current velocity with phase-preserving turns, normal
  facing mirrors and generic/native fallbacks. The maintained
  `examples/mica-and-talon` demo plays in all three stock games with Sonic's
  single-player physics, native S2/S3K spindash (disabled in S1), character-owned
  homing (including an untargeted forward
  dash), a four-frame HOMING_DIVE pose, and powered Instagib strikes. Its
  rerunnable original sheet has 226 frames, 52 canonical animations, slope cycles,
  a shared palette and a breathing/fidget idle. Canonical sheets preserve native
  partial-loop WAIT tails and finite hold/next endings; MICA and TALON skip
  their idle intros after the first pass. Native object mapping bytes can
  bind semantic sheet poses; MICA supplies three native-reference hang poses for
  vines and grapples, reuses fourteen existing native mapping poses for turns, rolls, riding,
  bar release and crane release. Forty additional native-pose aliases cover early
  S3K vine swings, hollow-tree tumbles, twist front/back views, poles and bars;
  33 generated drawings and seven verified quarter-turns retain the shared palette.
  Other raw poses keep named canonical fallbacks. The
  AIZ intro retains MICA's own art and powered palette. The 36 rotated WALK/RUN poses
  use native phase coordinates and pixel-edited contact edges; all 12 steep
  WALK/RUN hood contours are completed without changing sole support. S3K transformation
  art uses clustered gold scales and a clearer face, and the second dive shell
  has rounded scale rows. Gilded Super/Hyper forms cycle
  jade to gold, retain her sheet, and expose host settings for their gates. S1's
  donor-free six-emerald form is explicitly mod-defined. MICA supplies HUD/name,
  act and special-stage results (including S2's Super announcement, with tally
  logic independent of ROM presentation availability), 1-up faces,
  semantic sign faces/spins/backings, special/bonus-stage player art, continue
  poses, data-select solo/team portraits and counters, thirteen ICZ snowboard
  rider frames, and endings in all three games. S1 uses eight native-timed ending
  poses; S2 retains native monochrome photos and supplies Float2 and normal/Super
  poses; S3K supplies rescue Float2/ride, reunion, transformation, tiny passengers
  and trio-eyecatch art.
  S2 photo replacements belong to the active sidekick; the first panel always
  remains native. Native art references now use the photo scene palette, S1
  continue piece palette lines and S3K transformation palette phases.
  HUD life icons and 1-up faces share her private character palette in all three
  games; save-slot life icons retain their private frontend bank.
  Private palettes preserve native stage colours, text, board/spray, companions
  and vehicles; powered ending poses use her Gilded cycle. Acceptance covers
  launch/save/rewind/disabled-owner fallback and session-owned S2 HUD/1-up art,
  with three-game headless footage
  of movement, springs, normal/powered strikes and a GHZ airborne chain. The
  [tutorial](docs/modding/guides/original-character.md) explains the workflow.
  TALON is planned for phase 2; this is not a released mod or full-route certification.

- **Character API on the 0.7 candidate:** canonical playable animation tables
  share host speed timing, slope cycles, explicit end actions and fallbacks across
  S1/S2/S3K. Owner-bound underwater palette suppliers complement zone-aware water
  derivation. Optional `CharacterPresentation` provides immutable indexed images,
  per-host names and supplementary glyphs, native-frame stage/screen/ending sets,
  and bounded PNG loading. Font readers inventory all three small HUD fonts and
  S3K per-zone title/results letters with pixel-level glyph coverage/composition.
  GGFP presentation sections remain pending. `SuperFormProfile` supplies per-game
  tiers, palettes, physics, animations and rewind state; scene-form operations
  support authored controllers while preserving stock ending dispatch.
  The world-session `HomingAttack` facade exposes per-call homing/Instagib,
  target-only admission, requested reticles and flight divebomb with a steep
  forward-down fallback. Mutator Lab retains its character selection and native
  air-move fallback; characters need no mutator activation.

- **Character routing and presentation:** explicitly loaded development
  characters remain available in headless native Data Select. Custom team
  archetypes use the solo Sonic route without a follower. Stock S2 Tails-alone
  results now select Miles/Tails with the native graphics-region setting.
  Data-select save life counters use the character HUD icon; miniature continue
  counters reuse its continue pose. NO SAVE refreshes custom solo/team portraits
  immediately, and the S2 half-pipe ring HUD composes names from its native
  SONIC/MILES/TAILS alphabet with supplements for missing glyphs. Stock fallbacks
  and native screen/stage/ending choreography remain intact.

- **Seeded Data Select captures:** `GameplayCaptureTool --save-slots <file.json>`
  writes declared save slots through the production save manager into the
  capture's private save root before a title-first capture, and `--extra-teams`
  sets the Data Select extra team combos for that capture. The Sonic 3&K hero
  reel uses both to show custom-team saves. Title-first headless boots also run the
  donated Data Select warm-up that Engine runs before its startup mode, so a
  Sonic 1 or Sonic 2 capture with S3K donating shows the generated zone previews
  instead of S3K's placeholders.

## Maintenance and release preparation

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
