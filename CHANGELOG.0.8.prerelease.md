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

- Added the optional character presentation data contract and pixel-level name
  composition foundation. The HUD life icon/name, act and special-stage results
  names, 1-up faces and signposts (face, spin and backing) consume it in all three
  hosts, and MICA packages her indexed presentation art. Continue screens now
  accept optional native-frame poses with a separate character palette, retaining
  native timing, departure movement, text and stock Tails.

- Added the maintained MICA character skeleton for all three stock games, with
  headless launch/save/rewind/fallback acceptance and a rerunnable 96-frame original
  playable sheet with 17 canonical animations, shared palette and slope cycles.
  Headless native Data Select retains explicitly loaded development characters.
  Custom team archetypes now use the solo Sonic route without a sidekick.
  S1 HUD life icons/names, act/special-stage results names and 1-up monitor faces
  now consume character presentation slots while retaining native Sonic fallbacks.
  Presentation font inventories now include the native small HUD letters in all
  three hosts and S3K per-zone title/results-name letters. Stock S2 Tails-alone results
  now show the region-selected Miles/Tails name instead of Sonic.
  S2 HUD icons/names, act and special-stage results names, and 1-up faces now consume character presentations.
  MICA's Gilded Super/Hyper forms use owner-bound profiles and her jade-to-gold
  palette cycles; host settings configure the gates. S1's donor-free six-emerald
  form is a mod-defined rule. Powered-form state and palettes participate in rewind.
- Character definitions can supply an authored underwater palette through the
  owner fault boundary; absent suppliers retain zone-aware host water derivation.
- Playable mod sheets now declare canonical animation tables shared by Sonic 1,
  Sonic 2 and Sonic 3 & Knuckles, with native speed timing, slope cycles,
  explicit end actions and host animation fallbacks.
- **Character-owned homing:** the 0.7 candidate Mod API now exposes homing and
  Instagib strikes with per-call modes, an explicitly requested target reticle,
  and a flight divebomb with a steep forward-down fallback. Runtime and rewind
  ownership live in every world session; Mutator Lab retains its native-air-move
  fallback and character selection.

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
