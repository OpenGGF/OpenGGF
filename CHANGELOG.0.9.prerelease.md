# OpenGGF 0.9.prerelease — Development changelog

Unreleased. Work on `next` for the release after 0.8; `develop` work belongs in
[CHANGELOG.0.8.prerelease.md](CHANGELOG.0.8.prerelease.md). Add entries under the
matching area heading; see the
[documentation policy](docs/guide/contributing/documentation-policy.md#release-note-destination).

## Gameplay and presentation

- **Sonic CD development support:** a common programme reader now replaces
  duplicated CCZ reading, decoding and cache algorithms while keeping each act's
  authenticated data independent. R32A declarative layouts use the same reader;
  existing player, terrain, loading and controller contracts remain qualified.
  Original CCZ intro data also uses the common reader through fixed native views,
  preserving the historical odd mapping include and keeping public raw-image access closed.
  R31 and R32 load metadata now share one finite PLC, palette and section-table
  parser. CCZ intro facing clears and Robot Generator conditional reads preserve
  their native callback and fault prefixes;1,155 focused checks pass without skips.
  One source-bound World now replaces the R13-only coordinator and drives both
  R13 and R31 through their existing ordinary owners. Selected original-USA CCZ
  entry now completes1,200 controller services through Ring, the intro family,
  guarded Solid publication, shared native death/Game Over and two genuine
  LevelStart reloads. Ready reenters at541 and1082 through the same World lifetime;
  this verifies repeated-death reloads. A separate ordinary jump recipe preserves
  lives through native intro closure and the following returned service. Its
  oracle requires both original capture-palette publications and Amy's next-pass
  retirement; an early unsupported edge cannot count as success. Four focused
  route/reload checks pass. A following input recipe now traverses the original
  lower passage and genuine RollTunnel boost before its first Time Post.
  Its six route/reload checks pass; the first returned death is checked before
  any pending full/course replay. No terrain or physics change crosses the wall.
  Full/course restore retains
  selected closed state and pixels. Compound Solid preparation faults preserve
  the original native exception; available prefixes publish after preparation;
  the original PlaceBlock prefix bridge is installed, with natural reach still pending.
  All ten TT data profiles now use the same closed definition/reader/parser path,
  including the explicit188-frame Sonic format and retained CCZ consumer/intro
  views. The combined World/data integration passes668 focused checks without
  skips; TT decoder, ordinary-service and live World admission remain separate.
  All419 TT compressed sources now have checked Java products and independent
  finite native-style read contracts through that reader. One animation kernel
  serves the existing pure API and guarded live Checkpoint, Tamabboh and Disc
  callers, replacing their duplicate prefix reconstructions. The combined1,039
  data/animation checks pass without skips; existing Frame and CCZ views remain.
  TT enemy, hazard, route and boss components reuse shared owners. Door crossing
  retains the native BYTE comparison; BreakPole preserves timer/controller/target
  read order and raw self-link clamps. Guarded Hurt uses the original shared
  allocator return target and sound prefixes without replacing an original fault
  through self-suppression. The combined409 component checks pass without skips;
  actual TT World/Frame/source/art admission remains separate.
  TT solid/mover components retain signed WORD displacements and staged stores
  when an original callback throws Error. The combined CCZ/TT integration passes
  1,140 focused checks without skips; TT live World admission remains separate.
  All ten QQZ and ten Wacky Workbench DATA profiles use the same reader. The
  common section parser retains authentic independent columns and unused words;
  Present/Past metadata leaves retained good-future progress unconstrained.
  The combined CCZ publication/DATA/parser batch passes1,033 focused checks
  without skips. New zones' live terrain, player and World admission remains separate.
  All ten Stardust Speedway and ten Metallic Madness DATA profiles also use that
  reader/parser. Unavailable load or visual formats retain explicit selected-access
  boundaries; real no-section and unavailable-palette metadata does not invent work.
  R13 and R31 now share death-camera stores and lifecycle preparation. The combined
  1,460 checks pass without failures, errors or skips, including315 direct Body cases;
  natural CCZ transition and regional parity remain separate qualifications.
  QQZ1 Present/Past terrain and physical R6/R7 placement DATA now use the same
  shared readers and decoders. Metallic Madness's compact188-frame visual DATA
  uses the existing Sonic decoder without padding or a second format algorithm.
  The combined1,100 checks pass without failures, errors or skips; the existing
  sky-colour regression and selected CCZ replay remain qualified. These DATA
  bindings alone do not admit later-zone gameplay.
  Stardust Speedway's ten DATA profiles now parse all authenticated load formats,
  retaining the genuine unavailable palette slot, real RTS section alternatives
  and canonical act-gated cycle data. One cached shared Game Over component uses
  original R31/R13 publications and dirty-slot allocation semantics. The combined
  1,008 World, component and DATA checks pass without failures, errors or skips;
  live later-zone palette/section service remains separate.
  Wacky Workbench1 Present/Past and all ten Metallic Madness initial terrain DATA
  profiles use that same decoder, cache and query machinery, retaining raw unknown
  staging references and native collision bounds. Across the terrain cohort and
  its test-only fixture recheck,457 distinct focused identities are qualified;
  live R6/R8 player and World admission remain unsupported.
  All ten Wacky Workbench initial terrain DATA profiles now use the shared
  mechanism. The reload/terrain checkpoint qualifies1,235 distinct focused cases
  across two source-identical runs, without failures or skips in executed cases;
  the original incomplete selection receipt remains explicit.
  All ten Tidal Tempest and all ten Quartz Quadrant initial terrain DATA
  profiles also use the same machinery. Their641 focused checks pass without
  failures, errors or skips, preserving typed missing block/source boundaries
  and keeping water service, live projection and World admission separate.
  All ten Stardust Speedway initial terrain DATA profiles now use those shared
  owners as well. Their329 focused checks pass on the queued lean lane, retaining
  the R73C missing block and separate actor/priority/scroll admission boundaries.
  Original selected R11 entry consumes its real save/slot/Boot continuation,
  runs shared Start and LevelStart sequences, and retains its actual cleared
  Frame, saved-flag reset and CPU palette prefix through NormalLoad. R31 uses
  those same sequences and palette operation; their displaced duplicate code
  is removed. Full/course restore retains the reached loading cut and its real
  Frame, source, thread, context and observed manager/load ownership. Actual
  manager absence stays absent. NewGame retains the original cleared lives
  until the native reset writes them. One335-case focused cohort passes with
  zero failures, errors or skips after reproducing and correcting that early
  lives read; all15 prior failures now pass. Original failed receipts remain.
  R11 stops before the first selector-C selected-display/current-art service;
  completing loading, Ready, ordinary service and retail gameplay remains next.
  The remaining eight CCZ programme DATA profiles now reuse the same reader and
  parser, preserving both original Present producers. Native null-placement
  ranges stop at their six-byte sentinel;295 affected checks pass after an
  eight-case boundary regression. Live player/World admission remains separate.
  Cached CCZ KamaKama and Sickle receivers now join the existing physical World
  pass through original Frame, terrain, child, Draw and sound publications. The
  same source reader supplies their finite mapping and animation views. Across
  the focused cohort and one corrected test-hook rerun,206 distinct cases are
  qualified without errors or skips; the original failed result remains explicit.
  Constructed World vectors verify binding and restore, with natural reach,
  completed rewards and current enemy pixels still separate. Cached Explosion and
  Points now use that same physical pass, original child/Draw receipts and shared
  kernels. Their focused cohort plus two corrected test classes qualifies121
  distinct cases; native dirty-slot reuse retains its deferred mapping failure.
  Explosion's actual Flower conversion returns without redispatch. Flower now
  joins the existing physical pass on its next visit through the unchanged shared
  kernel, genuine current terrain, angle, count, saved-bank and Draw owners.
  Its finite original-source views add no art residency or reward allocator.
  Source-coherent qualification covers185 distinct cases across three runs after
  a stale Points-mapper assertion and explicit nested-report inventory correction;
  the original incomplete/failed receipts remain. The public-World Seed check now
  completes its ordered X/Y position stores after landing, angle and count writes.
  One immutable768-byte knowledge value receives only the original clear/stores
  and participates in existing full/course restore. After a test-only full-state
  clone migration,310 distinct cases qualify across the focused cohort and one
  corrected loading-class rerun, with no errors or skips. The original failed
  receipts remain; natural Seed birth/rewards and later routes remain separate.
  Bumper now uses a primary-only path through
  the same pinball kernel, retaining its legacy paired API without constructing a
  secondary actor. Original Frame, saved-bank, score and Draw publications share
  the existing owners; selected-anchor despawn replaces duplicate period logic.
  The213-case component/World/legacy/route cohort passes without failures, errors
  or skips after a test-only name-shadow correction. Natural contact, selected
  Bumper pixels and completed reward/act/regional coverage remain separate.
  Cached Time Post now joins that same physical pass through its original
  operation, direction-byte, saved-bank, sound and Draw publications. The lower
  route requires its returned Init before classifying another frontier. One
  1,072-case run passes without failures, errors or skips, including815 existing
  Frame cases and the protected Bumper, enemy, effect, intro and reload controls.
  Original missing-owner RED remains recorded; no new World or sound owner is
  added. Completed time warp, audible output and full-act/regional coverage remain
  separate.
  Cached native Spring now uses that physical pass and the same shared Spring,
  Solid, animation, Frame and sound owners. Canonical199-byte mappings retain
  their three actual roots without legacy padding. Bottom-solid prefixes use
  the existing cursor; reached native stops keep precedence over later faults,
  and reached Solid checks current terrain ownership. Parent/Draw/delete and
  prepared launch receipts retain their original ordering. One1,419-case cohort
  passes with zero failures/errors/skips, including the unchanged controller
  requiring Spring Init after Time Post. Original RED/incomplete results remain
  recorded. A five-case observer/filesystem check confirms returned Spring Init
  at1131 and1,158 recipe returns before unsupported object$2E/whole54 on attempt
  1159. One actual healthy ENGINE image retains its old selected-CPU provenance;
  diagnostic success does not claim the failed attempt returned, parity or an act.
  CCZ ordinary scheduling now preserves native VBlank/Main order, the old
  selected sprite list and full-word time-stop gating. Private HUD adoption
  retains the real prefix across typed callback failures;175 focused checks
  pass after a reproduced error-path correction.
  CCZ pinball/player-capture components now preserve native contact, child and
  raw-link behavior with43 focused checks; their live World binding remains separate.
  Private CCZ integration now has source-bound Frame publication, native HUD
  metadata and sparse residency, real ordinary Core PLC/section access, and a
  detached level view. Focused Frame/HUD regressions and exact fixture repairs
  are qualified; remaining CCZ object bindings and full-act gameplay remain separate.
  Fresh selected-profile boot now performs source-ordered SPX/MDINIT/BRAM
  initialization and real serialized storage-backend setup, preserving legacy
  entry behavior and unknown save tails. Its147 focused startup checks retain
  explicit nested evidence reconciliation, without a complete gameplay or
  native-driver claim.
  An experimental Palmtree Panic player, terrain and native-object runtime uses
  authenticated user-disc sources, current-art loading, checkpoint lifecycle and
  rewind checks. Pre-save time
  warp now creates native history-driven stars through the existing art service,
  including visible controller-route and state/pixel rewind checks. Native
  object-controlled Sonic rejoins the common player tail without running ordinary
  movement. Native time-warp saving preserves its distinct saved bytes, current
  scroll lock and destination marker. The native restart-byte write and ordered
  music-fade request finish the old object pass before its loop exit, with
  controller replay to the explicit program-transition boundary.
  Stock preparation now retains authenticated USA WARP, Past-stage program and
  original PCM-driver source data after closing the disc, with exact finite art-reader checks and a native
  full-palette colour-step component. Native palette cycles and the old level's
  22-step white fade now use the existing V-int and rewind owners, retaining the
  last level picture until the next program boundary. The detached WARP scene
  now follows native object, scroll, palette and input algorithms through its
  return, with real selected-pixel and full/course rewind checks in the existing
  programme lifetime. Distinct saved-data restoration and target palette fading
  now prepare the native destination entry. Authenticated Past assets now feed a
  separate static terrain view, with original-disc load metadata and pixel checks;
  ordinary destination gameplay remains an explicit boundary.
  Foreground events now use the actual complete time-zone byte while retaining
  the bound background program; separate Past scroll arithmetic preserves native
  fixed-point cameras, deformation and unwritten scroll-buffer tails.
  Past background name tables now use their finite native bands through the
  existing drawing algorithm, preserving strict Present bindings and rewind data.
  Shared sparse-pattern and PLC data algorithms preserve the existing Present
  interfaces while preparing a distinct Past art profile for source-ordered
  destination loading.
  Read-only player-source bindings keep the actual Present and Past data distinct.
  Named Past player, Frame and native-slot construction now use those sources,
  preserving existing typed getters, art ownership and rewind while retaining
  native period-specific air steering and music requests. The destination's
  ordinary gameplay lifetime remains explicitly gated. Finite Past
  collision inputs and fixed entry-value operations preserve native field
  boundaries without priming player art. Addressed time-warp player stores retain
  coordinate fractions and shared nonwrites; the native entry counter clear uses
  the same Frame and rewind owner rather than reconstructing the visit.
  Separate programme-load and initialization values retain native request order,
  source-backed driver data, conditional knowledge and complete replay equality;
  actual driver execution remains explicitly unbound.
  The camera-region clear now updates only its represented player fields on
  the same Frame, retaining history/art bytes, controller latches and clocks.
  A separately named earlier development entry now executes source-backed
  programme and finite display initialization before controller setup, carrying
  those facts through rewind and same-program reload without replaying startup.
  The existing later development cut and legacy unknown-history entry stay distinct.
  Past loading presentation now preserves addressed camera restoration and native
  scroll/background service order on the same player owner.
  Its native foreground name-table component preserves independent draw flags,
  inherited blank-tile metadata and reached failure prefixes without hardware
  emulation. A source-staged same-World Past load now executes the native loading
  services, saved-state restoration, foreground-before-background production and
  reserved initial stores, with complete full/course replay and strict failure
  boundaries. Its actual from-white loading fade now performs all22 palette
  services, preserving selected-versus-CPU order and complete rewind state.
  Genuine Past Sonic-art residency and native HUD updates now run before ordinary
  V-int Finish, followed by source-ordered pause/music requests and the finite
  Main frame increment. The exact saved-object bank now survives programme
  retirement into authenticated Past placement; native spawning and Sonic Init
  execute before the genuine reserved HUD/icon bodies. Their actual physical
  references now enter the shared draw queue without publishing a premature CPU
  picture; direction-zero time icons legitimately do not draw. Full/course replay
  restores the complete bank, pool, current tiles and paused state. The Past Ring
  body now uses authenticated destination views and the existing native
  algorithm in the live physical object walk. Complete pool, saved-bank and
  pending-queue checks cover its first body and healthy restore/forward replay.
  Past Spring initialization now follows it through the same live walk, using
  its own finite mapping roots and genuine tile-table extent. The shared table
  lookup preserves Present's bounds; existing Solid/player ports carry later
  Spring publication without introducing another physics owner. Native Platforms
  now follow in the same physical walk, using original primary mappings and raw
  width/range views, prepared real Solid prefixes and existing angle stores.
  The shared Tunnel Door now follows with actual primary/secondary Top calls,
  fresh real reserved-slot data and genuine stopped-prefix transport, retaining
  the legacy bank path. Past Monitors and Time Posts now borrow their existing
  art/mappings with the complete native animation include, genuine Solid/floor
  prefixes and ordered direction/sound ports. The actual first dynamic walk now
  completes all thirty bodies. Its first Main now follows native demo/restart
  guards, Past camera feedback, live sprite-queue drain, palette/PLC preparation
  and complete global animations. The unchanged route returns7775 with26 CPU
  pieces; complete state and full/course restore-forward agree with detached
  native models. Past Sonic Main now borrows the actual object, support, touch
  and warp contexts through the existing player owner, with ordered external
  publication and a live primary sprite-queue entry. Actual controller7776 and
  full/course restore-forward pass; the unchanged route is observed through7807,
  with natural Sonic SAT pieces from7777. Past Float Blocks now reuse the shared
  body and genuine Solid prefixes with their original B metadata: the actual
  incomplete7808 pass completes four bodies before the next Animal dispatcher.
  All286 distinct affected cases are covered across an initial286 run and a
  test-only27-case repair run; no completed7808, new picture or parity claim.
  Past now retains the actual outgoing future/projector pair through loading
  and rewind. The existing Animal presence/despawn body uses conditional source
  reads over the live pool: actual7808 returns Animal62 and Spring63 before the
  next unowned PataBata64. Full/course loading and a constructed presence scene
  replay completely. A333-case run had one new width-fixture failure; its
  test-only correction passes16 checks, retaining317 unchanged affected checks.
  Pata-Bata now borrows its authenticated Past animation/mapping views through
  the unchanged shared body. The actual controller7808 now completes Main after
  Pata64, with natural full/course7807 restore-before-forward equality. The
  affected391-case run exposed two superseded expected-stop tests; their test-only
  migration passes10 cases, retaining381 unchanged cases from that run. No single
  all-green391 run or new rendered Past picture is claimed. Current CPU sprites
  remain distinct from the genuine prior SAT selected at V-int.
  Past Tamabboh now runs through its named shared-body factory and actual cached
  World invocation, with synchronous floor-angle/player reads, typed FM metadata,
  native missile allocation and cleared-ID draw handling. The unchanged controller
  route now executes this Past encounter; full/course captures preserve its complete state.
  Past rendering now retains the genuine prior SAT selected at V-int, joins it to
  resident art, complete planes, palette and scroll, then draws through the shared
  native-priority renderer. Full/course state and full pixel replay are checked;
  actual selected Sonic pixels and owned GPU retirement are covered in EGL.
  Native state rejects capture, restore, service and recursive draw reentry from
  queued graphics commands without closing the healthy producer.
  Past Collapse Platforms and Boulders now reuse their shared bodies with genuine
  Solid/Frame publication and stopped-prefix handling. Fixed standing, breakup,
  carry and full/course replay checks cover the bindings. Past Mosqui, Spikes and
  Anton now use authenticated shared-body factories and cached World invocations,
  including synchronous floor/FM, real Solid prefixes and prepared Frame adoption.
  The unchanged controller continues through the Mosqui encounter to the next
  Robot Generator fence, with complete full/course state replay. Fixed Spikes
  support/protection bypass and Anton Init/Place are checked; live direct Hurt and
  grounded Anton patrol remain separate World coverage gaps.
  Selected Robot Generator and Past Explosion components now share the existing
  Solid, animation and current-art owners. A pure selected AddPoints entry reuses
  the original score arithmetic and ordered one-up request, preserving existing
  touch-response behavior. Cached Past World bodies now publish immediate good-future,
  score, Solid and ordered explosion-child effects through the existing owners.
  An input-only run-up, mature spindash and Spring-avoidance jump now clear that
  terrain and reach the native Goal Post, Signpost and low-ring Big Ring path.
  Complete full/course replay is checked through9856 and the genuine Results
  tally10394. Goal9598 and Results10394 have real selected-pixel and resource-retirement
  checks. Results now publishes its native next-level scalar, saved-bank, sound and
  draw prefix before the programme boundary, after the complete physical walk.
  Retained independent water colours and guarded old-programme Frame services now
  carry the controller route through all22 black waits, the final clear, programme
  return and IPX blank. Complete full/course replay reaches10419 on the same retained
  World. Actual programme return now admits the retained original-USA Act2 pair
  through a new same-World FULL load. Native stage/standard art loading, black
  fade, reserved Title Card and the complete first physical walk return without
  a synthetic actor or queue seed. The controller reaches Act2 Main10445 and
  three following returns with complete full/course replay. Real selected Sonic
  pixels, renderer-only omission, queued reentry rejection and owned upload
  retirement are checked in EGL. The real Title slide-out queues and services its
  art before releasing controls. Cached Monitor/Time Post bodies now use genuine
  shared Solid, floor-angle, direction and ordered voice publications; ordinary
  Right returns through10866 with full/course replay across natural post, Boulder
  and Anton admissions. Cached Boulder, Platform, Anton and Pata-Bata bodies share
  existing art, math, terrain and Frame owners. Exact pending-call identities guard
  synchronous publications, including unchanged values and throwing wrappers; Pata
  retains reached animation-fault writes without duplicating successful transitions.
  Actual later-route EGL pictures now retain selected Sonic pixels and full/course
  pixel replay through the later ordinary route. Cached Tamabboh and FloatBlock now share existing
  art, terrain, Solid and Frame owners; completed producer prefixes and ordinals
  guard the next publication, including skipped no-op and throwing callbacks.
  Ordinary Right extends through their native entry with full/course replay to
  the final successful cut. Checkpoint retains its existing saved bytes and
  knowledge across programme loads, then uses the real global/save/parent-read
  owners. Cached Breakable Walls use genuine plain Solid, prepared velocity
  publication and native dirty-fragment order. Collapse Platform views retain
  Act 2's distinct frame and size tables; its World binding and later traversal
  remain open.
  Selected Goal Post now preserves native raw tile selection, conditional player
  reads, PLC requests and despawn behavior. The same Frame can prepare its exact
  goal-bound and warp clears. Live Signpost and Results bindings preserve native
  physical child order, BYTE/WORD timers, screen coordinates, score, PLC and sound
  requests through existing owners. Original Projector metadata reuses existing art.
  Original-USA Act2 terrain and player factories now retain their own source bytes
  and immutable binding. Stock USA preparation detaches both products before closing
  its existing CUE owner; JP/EU retain explicit absence. Act2 now has explicit
  same-source player/Frame component entry, native PPZ2 foreground events,
  three-cloud background deformation and Plane B binding. Its lazy raw/visual
  resources and sparse current patterns reuse existing decoders; section loading
  uses the shared queued PLC kernel. Reserved Title Card parent and fresh dynamic
  children retain native routines, allocation, timed slides, locks and deferred
  PLC2 request. Cold start now records the authenticated MAINVARS palette-clear
  bit writer before PCM rather than inferring a default. Native signed-piece Y
  WORDs remain exact in CPU SAT; normal sprite projection uses their nine-bit
  position without discarding offscreen Title Card pieces.
  Authenticated Act2 placement and Ring bindings retain the actual carried saved
  bank and layout without changing their algorithms. A typed Act2 HUD facade
  shares the existing digit, time and failure-prefix kernel; selected-range black
  fade uses native whole-WORD arithmetic without assuming a clear or owning waits.
  A source-bound Breakable Wall component now reuses shared Solid and Platform art,
  preserving parent-plus-five fragmentation, failed allocations and native gravity.
  Its fixed player X-velocity halve prepares only the existing Frame value pair;
  actual Act2 dispatch also binds collapse platforms, Log Shadow and Mosqui using
  private callback receipts before native continuation. Natural controller steps,
  floor/sound publication and complete replay now cross the horizontal Door
  through actual primary/secondary Solid publication, with twelve selected-Sonic
  pixel cuts and natural Explosion/Points work. Actual Flower floor/angle,
  growth and draw publication now continue to the Tunnel Path boundary, retaining
  explicit persistent-flower storage limits. Authenticated Tunnel Path speed,
  coordinates and raw Init-pointer views now bind its actual player-publication
  bridge without admitting a Powerup art owner. Native Init/Main, prepared player
  stores and ordered FM retain private invocation receipts and full-owner checks;
  controller traversal now reaches the next reserved time-warp-star dependency.
  Authenticated Powerup scripts and overlapping raw mappings now support the
  existing star kernel. A raw-backed reserved-slot facade borrows the existing
  captured storage without clearing, mirroring or creating another state owner;
  its live Act2 dispatch now publishes actual primary patches and same-pass
  TimeStar Init/active visits with full/course replay. Native history, Powerup
  stores and reserved Draw retain private completion receipts. The actual normal
  Main clear now establishes the complete CPU art window; source-ordered star-art
  service copies authenticated raw bytes, preserves its unwritten tail and
  publishes current tiles through the existing captured owner. Controller replay
  reaches13126, with real star pixels at12887 and Shield pixels at13098, each
  checked by renderer-only omission. Complete acts remain separate dependencies.
  Its eight exact static icon views now derive from the retained monitor source.
  Opt-in typed Shield awards and a reserved whole6 Shield kernel now preserve
  native store order, conditional reads and private completed-operation receipts;
  Draw visibility permission ends at primitive exit, including failure paths.
  The live Act2 binding awards the external contact byte, reserved Shield ID and
  ordered FM request through existing owners; its earlier physical slot waits for
  the next pass. Native Init, active animation, subsequent art service and signed
  Item retirement retain full/course replay and selected current-pixel checks.
  A short controller backtrack and jump now reaches the lower tunnel approach.
  Ring monitors award ten rings through the same ring, life and HUD state, with
  native one-up thresholds and ordered sound requests. Door-created splashes now
  retain native Init, sound, animation, Draw and deletion order over the existing
  art owner. The ordinary route leaves Tunnel Path control. Act2 Robot Generator
  Init, Draw and conditional despawn now reuse its existing component, authenticated
  tables and Withered art owner; active Past, explosion and rebuild actions remain
  outside this Act2 binding. Moving Spring now allocates its ordinary Spring child,
  aligns to the real floor and moves through the existing terrain, angle, animation
  and art owners. Swing now allocates its multipart children and visits both the
  primary and raw reserved secondary through the same shared Solid kernel, with
  prepared same-Frame publications and source-ordered failure prefixes. Existing
  art and checkpoint owners remain unchanged. The native timer-210 time-warp save
  now publishes its saved state, marked period and camera lock through the existing
  Frame and World owners. The timer-230 restart now finishes its physical object
  pass before the old programme prelude, queue clear, conditional white fade and
  return. Full/course controller replay covers that return while retaining all
  twenty-three prior selected Sonic render checkpoints. Swing parts remain
  offscreen at the checked route cuts. Authenticated Act2 Past terrain and all
  thirty-seven finite section-art readers now join an authenticated Act2 Past
  player source, explicit Frame ingress, native background scroll, sparse current
  art and shared HUD services. Stock preparation retains the actual target pair
  before closing the disc. The old Act2 programme runs the existing WARP scene,
  then performs the real same-World R12B FULL load. Source-ordered saved stores,
  section PLCs, scroll/planes and white fade now complete with selected terrain,
  full/course replay and real renderer checks. A batched metadata owner supplies
  twelve initial object kinds without relabelling Present tables. Actual-source
  body factories now bind those kinds; later finite views retain forty-two counted
  roots plus eight monitor icons, with legacy constructors preserving their contracts. The first unpaused
  R12B physical walk now runs actual Sonic Init, reserved HUD and live dynamic
  slots through camera, CPU SAT and the ordinary tail, with full/course replay
  in the constructed exit fixture. Genuine Main/touch publication now continues
  the fixed controller through 1,782 target services, with selected Sonic
  provenance and full/course rewind. The target Boulder, Breakable Wall and Floating Block
  bind their actual mappings and shared Solid publication. Wall FM and signed
  velocity publication retain native order; Floating Block threads its two or
  four original Top-Solid calls through the same Frame. The natural controller
  now has sixteen selected-display pixel/replay cuts, eight with visible Sonic;
  Wall visibility remains unmeasured. Retained level pictures also keep their
  native selected backdrop palette entry, restoring the green Past sky while
  WARP retains its separate entry-zero backdrop. Restoring R12B projection rebuilds stored
  output without consuming a pending scroll-initialisation dispatch. Spikes and
  Log Shadow now bind their actual B metadata and private Solid/Y/Hurt/Draw
  publication through existing owners. A fixed jump clears the earlier Platform
  obstruction. Pata-Bata now reuses authenticated normal/damaged views and the
  existing shared Math through an original Draw receipt; a second fixed input
  segment reaches the native Projector and its four later physical children.
  Projector, active animals, Scenery and Springboard now retain their original
  source-ordered stores, PLC requests and shared player/terrain publications;
  Explosion, Points, Flower, LostRing, Monitor Item and Mosqui reuse their source-bound
  bodies and existing art owners. Genuine Hurt now rejoins the same ordinary Frame
  publication without running Main's movement/touch tail. The controller recovers
  from real damage, lands and passes the next obstruction with a fixed jump.
  Spinning Disc, both Tunnel Doors and Spin Tunnel now use genuine shared Solid
  results and prepared player publications; the real Door Splash child follows
  its parent's sound request. GoalPost, Signpost and Results now retain native
  field, bonus, score, saved-bank and sound order, including completed PLC failure
  prefixes. Big Ring and Flash now preserve original conditional reads, physical
  child order, prepared player stores and programme/scroll/sound ownership.
  Results completes the full physical walk before the native old-programme
  fade, clear, return and IPX blank. The Past controller route clears the diagonal
  Spring and Boulder, triggers the Goal Post and returns through that exit with
  complete full/course restore-forward checks. Natural Projector and Goal Post
  pictures have selected-current-art, pixel-replay and renderer-retirement checks.
  Act3 Good/Bad Future data retains its original167-frame Sonic format, complete
  art and native placement order, including the shipped X-order reversal. Stock
  USA preparation retains both authenticated future programmes after disc close;
  the later native programme owner selects from its carried flag. Both terrain
  geometries, startup object and boss metadata, scrolling and palette components
  reuse existing decoders and arithmetic. Shared queued/immediate/RAM/life art
  loading preserves earlier-act behavior and native empty section routines.
  The actual Act2/IPX return now installs Act3 Bad Future in the same World,
  with a fresh native Frame, retained programme data and source-ordered loading,
  fades and deferred Title art. Its original physical startup walk reuses the
  shared object bodies; current planes, prior CPU sprites and Sonic art render
  through the existing renderer. Fixed-controller state and selected-pixel
  replay cover the first visible Sonic, completed Title art service and an actual
  rendered Boss fight reached with fixed controller input. Boss-only renderer
  omission and complete restore/current-picture checks preserve the same owners.
  A further fixed-input route defeats the native Boss, replays the health/escape/
  removal cuts and renders its selected defeat picture. The fixed controller now
  activates the actual Capsule, runs Results and reaches the old-programme/IPX
  return, with twelve full/course edges and selected Capsule/Results pixel
  omission checks; subsequent IPX progress and save admission remain separate.
  Native Dead/Restart now rejoin the actual
  caller with paired death-camera bounds, original no-draw restart and complete
  restore checks. Cached native GameOver now preserves original sound, allocation,
  text mappings and deletion order; active text art and reset remain explicit
  owners. Host
  course reset publishes Act3 consistently to the manager and World so the
  act-scoped rewind allowance refills once. Cached Boss/limb/bumper, Capsule and
  Results owners now publish through the actual physical walk and same Frame.
  The retained Act3 exit preserves22 black waits, final clear, RTS and coherent
  IPX blank with full/course replay, stopping honestly at missing persistent
  programme state. Original-USA Collision Chaos data and517 native placements
  detach through the same CUE, preserving the saved bank and spawner algorithms;
  common Sonic data keeps the absent PPZ ramp explicit. An authenticated R31
  Frame entry now runs genuine normal/shrunk Init with native raw zone gates;
  normal MODE uses real CCZ terrain. A distinct pure normal Main/Hurt entry stages
  original block, child, scalar and sound work privately, including Hurt collision
  against the resulting chunk image; ordinary Frame/World admission stays gated.
  Complete R31 PLC/section/residency components and
  exact-source raw2C touch/response views retain their native distinctions without
  granting World admission. Source-bound Collision Chaos background drawing now
  uses its exact50/16-byte tables and3F0 mask; a pure scroll/deformation/water owner
  retains native fractions, flags, wraps and finite tails. Existing PPZ drawing
  and movement algorithms remain unchanged; live R31 loading stays separate.
  Same-source R31 HUD, Monitor and Title views now reuse their original counted
  mappings and genuine even-byte reader extents. A finite original-USA system
  programme source retains the authenticated SPX image and its initialization
  clear description without executing that clear or creating boot authority.
  Stock USA preparation retains that system source before its same CUE closes;
  legacy internal constructors and regional absences remain explicit. A pure
  source-bound CCZ event component now preserves target and bottom-shift stores,
  WORD wrap, native overshoot and untouched fields without creating Frame or
  World publication authority.
  A package-private R31 loading component now preserves native Start, the first
  selector-C wait and flower-counter reset, real PLCs, CCZ scroll and ordered
  normal/water fades. Selector12 enables display in initial fades and retains
  the palette actually published by its original Host callback. Boot, World and
  ordinary-loop admission remain separate. A retained loading Host now preserves
  complete physical images and original palette publication, checks every Module
  callback against the borrowed native owners, and clears the actual separate
  Sonic resident bank at InitVDP while preserving CPU/cue/dirty state. Actual fresh
  World and ordinary gameplay admission remain separate. Shared reserved HUD reads
  the absolute counter only at native ring flashing, preserving earlier stores on
  missing input. The original pending primary call now stages genuine Draw bit7
  changes and deferred sprite construction rereads live primary state, with full
  placement nonwrites checked around coupled publication.
  Selected horizontal-scroll image copying and raw carried-word packing now share
  one internal implementation across the R13 and R31 Hosts, preserving the native
  896-byte prefix and genuine 128-byte selected tail. The reuse decision keeps
  authentic programme data separate while consolidating lifecycle algorithms.
  R13 and R31 now use one internal ordinary scheduler with the original Frame,
  selected old CPU, full-WORD palette gate and typed native-transfer outcome.
  Invalid held input is rejected before borrowing or retiring a healthy loop.
  A finite IPX progress
  component preserves unknown-byte and save-field alias semantics. Its actual
  boss-return join publishes saved-zone/checkpoint state once and retains it
  through existing capture lifetimes. The authenticated, once-only cold MAINVARS
  clear now establishes its covered progress facts before the following fallible
  PCM read; plain later development entry retains unknown history. Original
  RunR1 uses the same progress kernel and private current-call authority for
  ordered unlock/title/checkpoint/projector/live-flag/history publication,
  preserving reached prefixes and existing full/course capture ownership.
  Component World controls reach the explicit RunR3 save boundary; the real
  selected-slot/save context and a fresh natural continuation remain separate.
  Original initial-save sources retain complete data and shorter native copy
  extents, detached from the same authenticated stock disc before it closes.
  Legacy constructors and regional absences remain explicit. An internal selected-slot codec/bootstrap distinguishes genuine absence
  from corrupt, quarantined or unknown history and uses the existing save writer
  for exclusive fresh publication in the canonical CD namespace. Historical values, unknown native tails,
  conflicts and cleanup/completion failures stay distinct; published SaveManager
  signatures and legacy behavior remain unchanged. A fresh selected-slot Module
  now owns the real World/slot-bound initialization cache, separate IPX reads,
  engine-menu intent and synchronous save prefixes, stopping before programme
  loading without a DEV seed or Frame. Native Title and live loading remain
  separate; initialization-local buffer
  commands are distinguished from later IPX progress calls. Data custody
  grants no next-zone gameplay. The exact original-USA R3PCM file is retained
  before the same CUE closes, with legacy omissions and public source APIs intact.
  Its finite loader source/initial-variable selection remains distinct from R1, preserving
  matching-instance reuse and reached UNKNOWN prefixes; PCM driver execution,
  output and live loading admission stay separate. Original R31 music selection
  now consumes its own authenticated28-byte table and complete58-byte consumer,
  retaining raw period/time-attack/future and wrapped index semantics as command
  data only. Collision Chaos gameplay, broader act coverage
  and regional parity remain separate.
  CD player headless boot loads the fixed
  roster before rewind controller creation while keeping no-device audio before
  playback. A separate real-World entry fixture retains its own visible-Sonic
  checks. No full-act or native/regional parity is claimed.
  Authenticated Act2 Spikes mapping and a named retained-source prefix factory
  reuse the existing common body and Std art owner; live Spikes dispatch remains
  separate from the qualified route.
  Actual Explosion and Points
  children now reuse the existing physical pass and current art, preserving native
  allocation, deletion, score-before-child ordering and full/course replay. Flower
  source views remain preparatory; no new art owner is added.
  Natural wall contact, complete acts and regional coverage remain separate.
  Terrain-only USA Good Future and Bad Future profiles decode their original
  disc-backed layouts, collision, blocks and patterns through existing loaders;
  they add no Future gameplay, PLC service or World admission. Native visual
  comparison, complete acts and audible synthesis remain open.
  Authenticated HUD glyph/mapping views and sparse current-pattern destinations
  now preserve existing source identities; fixed loading-value writers keep native
  field lifetimes on the same Frame without certifying a completed destination.
  A distinct Past art adapter now reuses the queued algorithm while keeping
  immediate art, shared decode-table residue, RAM blocks and life-icon copies
  source-ordered component operations. Common terrain queries preserve existing
  collision arithmetic and failure prefixes without relabelling Present assets.
  The default
  static entry and explicit playable development entry remain distinct; complete
  acts, native audio and all-region gameplay are still in development. Game
  modules consume session data sources without requiring a cartridge ROM.

## Modding and tools

- **Sonic CD integration checks:** opt-in import preflight now reports likely
  missing ordinary imports of selected nonstatic member types after static-star
  imports, preserving unresolved receiver and anonymous-scope uncertainty. It
  keeps the existing default checks and budgets unchanged. The declaration census
  can compare a direct literal public-method inventory with written declarations,
  preserving overload multiplicity and Java lexical order. Shadowed, generic or
  unsupported scopes remain incomplete; output and reread budgets stay bounded.
  A focused category now includes the CD source
  package and its shared dependencies while preserving existing gameplay coverage.
  The matching policy and runner fail closed with an older reader. Candidate
  reduction is measured as coverage selection, not a claimed runtime speedup.
  The integration runner can also shorten Maven progress separately from its
  structured result, retaining complete source and fresh JUnit verification.
  Explicit JUnit evidence export saves complete bounded case identities, outcomes,
  environment and report hashes to a new external artifact, with XML observation
  kept separate from command completion and tested-source attribution. Optional
  exact nested-report groups retain actual ancestor/child case identities,
  validate empty-parent topology and keep bounded diagnostics consistent while
  preserving strict flat defaults and original failed/incomplete receipts.
  Mixed literal-method and whole-root requests now include only explicitly
  declared nested binary selectors, preserving the exact method selection and
  preventing Surefire's global method filter from omitting nested cases.
  Optional terminal run receipts retain the full source-bound result and exact
  cases before compact output, including failed or incomplete checks. They use
  the same bounded exclusive external writer and never delete reports or act
  as a reusable pass cache.
  Focused lean checks compile with normal Maven memory before bounded execution,
  rechecking source and external packet inputs after admission so preparation
  reports cannot stand in for tests.
  A bounded source census also flags stale simple JUnit test counts before an
  expensive run, leaving parameterized or unresolved discovery explicitly unknown.
  Pinned native-storage queries also expose declared field widths and clear-range
  overlaps, stopping explicitly at unsupported source syntax.
  An opt-in project-import preflight checks explicitly pinned Java type sources
  and flags possible missing imports while retaining lexical ambiguity and bounded
  header checks. It does not replace compilation or classpath resolution.
  Explicit known-enum selections now flag absent member names, including nested
  asset enums, before a costly compile. Imported qualifier shadows and annotated
  generic parameter scopes retain incomplete results; shared budgets and immutable
  source provenance remain unchanged.
  Source-census and import-preflight dependency pins now match their approved
  sibling helpers while retaining exact byte budgets, hash checks and explicit
  incomplete results for unsupported Java scopes.
  The census can also require exact direct ordinary test names before a costly
  run, preserving requested order and reporting missing names only for complete
  supported inventories. Unsupported Java scopes remain explicitly incomplete.
  An optional immutable-source preflight now checks conventional JUnit roots,
  literal method selections and explicit nested report groups. Split header
  identifiers and unresolved source scopes remain incomplete; it grants no
  discovery, invocation-count or execution authority. Its27 focused tooling
  tests pass without skips, and Maven admission remains with the existing queue.

- **Source research and integration tools:** immutable disassembly label lookup,
  frozen-source packet imports and byte-preservation checks, fresh JUnit report
  attribution, structured dependency handoffs, source-bound focused queued runs
  with exact method and case inventories, optional compact summaries that retain
  complete verification and integrity errors, exact-identity matched JUnit
  comparisons, reference-checkout/resource-link custody checks, static native span/address
  recipe checks (including a bounded original-CUE/named-MMD front door without
  extraction), and bounded Java enum/declaration audits have matching reusable
  agent skills. Enum total checks also flag stale intermediate literals when
  several append-only migrations occurred after the selected baseline.
  These tools report evidence and limits rather than certifying
  native parity or whole-game completion.

## Maintenance and release preparation
