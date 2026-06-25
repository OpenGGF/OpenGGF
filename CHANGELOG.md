# Changelog

All notable changes to the OpenGGF project are documented in this file.

## Unreleased
- **S3K AIZ intro glow rewind:** AIZ intro emerald glow helpers now expose a generic recreate hook with live-plane relinking, clearing stale recreate/final-scalar coverage gaps.
- **S3K AIZ emerald-scatter rewind:** AIZ Emerald Scatter now restores through spawn-based generic recreate, clearing its stale recreate coverage gap.
- **S3K LBZ trigger-bridge rewind:** LBZ Trigger Bridge now restores through spawn-based generic recreate, clearing its stale recreate coverage gap.
- **S3K CNZ teleporter rewind:** CNZ Teleporter now restores through generic recreate, with its live beam link captured and relinked to the restored beam.
- **Shared box-object rewind:** BoxObjectInstance now restores through exact-class generic recreate, preserving subclass restore behavior while clearing its stale recreate gap.
- **S3K ICZ snow rewind:** ICZ snowdust particles and the snowboard intro controller now restore through generic recreate with restored emitter links.
- **S3K ICZ support rewind:** ICZ ice spikes, tension-platform supports, and crushing-column bottom decorations now restore through generic recreate with restored parent links.
- **S3K pachinko energy-trap rewind:** Energy-trap column and beam children now restore through generic recreate with parent links relinked to the restored trap.
- **S3K gumball-machine rewind:** Gumball machine roots and bonus-stage children now restore through generic recreate with restored parent/dispenser/spring links.
- **S3K destructible fragment rewind:** Breakable Wall, Cork Floor, and Collapsing Platform fragments now restore through generic recreate with captured fragment render state preserved.
- **S3K Tension Bridge graph rewind:** Tension Bridge roots and falling fragments now restore through generic recreate with fragment render state preserved.
- **S3K Collapsing Bridge graph rewind:** Collapsing Bridge roots, wave fragments, and MGZ stomp debris now restore through generic recreate with fragment render state preserved.
- **S3K Tunnelbot graph rewind:** Tunnelbot parents, arm collision proxies, and debris now restore through generic recreate with restored arm slots and debris state preserved.
- **S3K Caterkiller Jr graph rewind:** Caterkiller Jr heads now restore through generic recreate, with restored body segments relinked into the head's runtime segment list.
- **S3K SnaleBlaster graph rewind:** SnaleBlaster parents, covers, and shooters now restore through generic recreate with restored parent caches relinked to restored children.
- **S3K Ribot visual rewind:** Ribot visual trail children now restore through generic recreate by relinking to restored active children, clearing their stale recreate coverage gap.
- **S3K Mantis graph rewind:** Mantis parents and visual children now restore through generic recreate with captured parent/child links, deleting their stale recreate coverage gaps.
- **S3K Mushmeanie graph rewind:** Mushmeanie parents and shell children now restore through generic recreate with captured parent/shell links, deleting their stale recreate coverage gaps.
- **S3K Cluckoid arrow rewind:** Cluckoid arrow children now restore through generic recreate, relinking to the restored layout-slot parent without a private codec.
- **S3K MegaChopper rewind:** MegaChopper now restores through the generic spawn recreate path, clearing its stale recreate coverage-baseline gap.
- **S3K Turbo Spiker graph rewind:** Turbo Spiker parents, trail emitters, and hidden waterfall overlays now restore through generic recreate with shell and parent links relinked to restored objects.
- **S3K Spiker graph rewind:** Spiker parents and side launchers now restore through generic recreate with parent child slots relinked to restored children.
- **S3K Dragonfly graph rewind:** Dragonfly parents and wing children now restore through generic recreate with wing parent links relinked to restored instances.
- **S3K AIZ/LRZ rock rewind:** The AIZ/LRZ rock now restores through the generic spawn recreate path, clearing its stale coverage-baseline recreate gap.
- **S3K AIZ draw bridge rewind:** The draw-bridge parent now restores through the generic spawn recreate path, clearing its stale coverage-baseline recreate gap.
- **S2 Tornado rewind cleanup:** Tornado subtype state now compact-restores through the existing graph-tested generic recreate path without a stale final-scalar baseline key.
- **S2 spiral graph rewind:** EHZ spiral/cylinder helpers now restore through generic recreate while rider and cylinder-angle player links relink to current live player instances.
- **S2 flipper graph rewind:** CNZ flippers now restore through generic recreate while per-player cooldown, flipper-state, and control-suppression maps relink to current live player instances.
- **S2 launcher graph rewind:** launcher balls, launcher springs, OOZ launchers, and OOZ launcher fragments now restore through generic recreate while player-state maps relink to the current live player instances.
- **S1 Scrap Eggman graph rewind:** SBZ2 Scrap Eggman and its button child now restore through generic recreate with child-first restore ordering relinked to restored instances.
- **S2 DEZ Eggman graph rewind:** the transition Eggman root and barrier-wall child now restore through generic recreate with the wall link relinked to restored instances.
- **S2 MTZ boss graph rewind:** MTZ boss roots, laser shooter children, and orbiting shield orbs now restore through generic recreate with child references relinked to restored boss instances.
- **S2 Death Egg Robot graph rewind:** Death Egg Robot roots, structural body-part children, and runtime targeting sensors now restore through generic recreate with live parent/child references relinked to restored instances.
- **S2 Mecha Sonic graph rewind:** Mecha Sonic and its DEZ window, LED, targeting sensor, and spikeball children now restore through generic recreate, with the runtime spikeball preserving constructor velocity/frame state.
- **S2 Tornado graph rewind:** Tornado parents and thruster children now restore through generic recreate with the thruster follower link and child parent pointer relinked to restored instances.
- **S3K ICZ freezer graph rewind:** freezer roots, capture clouds, and frozen-player blocks now restore through generic recreate with cloud/block/player references relinked from the rewind identity table.
- **MHZ2 lift-child rewind:** the Knuckles lift carrier now restores through generic recreate with its live player reference relinked from the rewind identity table.
- **Madmole side-drill rewind:** the side-drill child now has an explicit captured-player rewind policy and graph proof, removing its stale object-ref coverage gap.
- **Player-reference graph rewind:** S2 Grabber plus S3K MHZ mushroom parachutes and sticky vines now restore live player references through the rewind identity table instead of stale object-ref/recreate baseline gaps.
- **S3K ICZ end-boss graph rewind:** the snowdust emitter link now restores through generic recreate with a restored child reference instead of stale object-ref/recreate baseline gaps.
- **S3K CNZ traversal player-reference rewind:** cannon and cylinder release handoffs now have explicit audit policies and graph tests proving player refs restore through the live identity table.
- **S3K CNZ end-boss graph rewind:** the post-defeat cannon handoff now restores through generic recreate with a restored end-cannon reference instead of stale object-ref/recreate baseline gaps.
- **S3K LBZ miniboss graph rewind:** Knuckles-route miniboss pairs now restore through generic recreate with restored box-parent references instead of stale coverage-baseline gaps.
- **S3K ICZ segment-column graph rewind:** column child segments now restore through generic graph recreate with restored root/previous links instead of stale coverage-baseline gaps.
- **S3K SnaleBlaster rewind cleanup:** the transient cover cache no longer carries a stale object-ref coverage-baseline key.
- **S3K Dragonfly graph rewind cleanup:** linked body follow-anchor references are now guarded as captured rewind identities, removing a stale object-ref baseline key while preserving loud missing-target failures.
- **S3K Clamer graph rewind cleanup:** Clamer parents now capture their spring-child slot by rewind identity, keeping the existing graph restore proof without a stale object-ref baseline key.
- **S3K MHZ1 cutscene graph rewind:** the MHZ1 cutscene button, spawned Knuckles actor, and peering child now restore through generic graph recreate while preserving their parent/back-reference links.
- **S3K CNZ2 Knuckles cutscene graph rewind:** the first CNZ2 Knuckles cutscene parent now restores through generic graph recreate, preserves its blocking-wall link, and no longer requires stale object-ref/recreate baseline keys.
- **S3K CNZ water-level cork-floor graph rewind:** the water helper and cork floor now restore through generic graph recreate, preserve the helper-to-floor link, and no longer require stale object-ref/recreate baseline keys.
- **S3K CNZ2 cutscene-button graph rewind:** cutscene buttons now restore through generic graph recreate, preserve the spawned lights-flash link, and no longer require stale object-ref/recreate baseline keys.
- **S3K monitor graph rewind:** monitor contents slots now restore through generic graph recreate, relink to the restored monitor, and no longer require stale scalar/object-ref/recreate baseline keys.
- **S3K Clamer graph rewind:** Clamer spring children now restore through generic graph recreate, relink to the restored parent, and no longer require stale scalar/recreate baseline keys.
- **S3K MGZ pulley graph rewind:** pulley chain children now restore through generic graph recreate, relink to the restored pulley, and no longer require stale recreate baseline keys.
- **S3K HCZ water-rush graph rewind:** water-rush blocks now restore through generic graph recreate, keep captured block state, rerun the parent constructor side effect, and no longer require stale recreate baseline keys.
- **S3K HCZ/CGZ fan graph rewind:** fan platforms and bubbles now restore through generic graph recreate, preserving captured child state without stale parent links or duplicate children.
- **S3K HCZ hand-launcher graph rewind:** hand-launcher arms now restore through generic graph recreate, relink to the restored launcher, and no longer require stale recreate baseline keys.
- **S3K LBZ cup-elevator graph rewind:** cup elevator attachment/base children now restore through generic graph recreate, relink to the restored parent, and no longer require stale recreate baseline keys.
- **S3K LBZ tube-elevator graph rewind:** tube elevator overlays now restore through generic graph recreate, relink to the restored parent, and no longer require stale coverage-baseline keys.
- **S3K LBZ player-launcher graph rewind:** launcher-arm children now restore through generic graph recreate, relink to the restored parent, and no longer need stale scalar/recreate baseline keys.
- **S3K MGZ pulley scalar rewind cleanup:** pulley anchor, facing, and extension scalars now compact-restore without stale coverage-baseline keys.
- **S3K mechanism-root scalar rewind cleanup:** MGZ top-launcher facing and tension-bridge constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K mechanism graph-scalar rewind cleanup:** ICZ segment-column roots, ICZ tension-platform supports, MGZ2 collapse solids, and HCZ end-boss turbine offsets now compact-restore without stale coverage-baseline keys.
- **S3K graph-helper scalar rewind cleanup:** LBZ1 cutscene helpers, starpost orbit stars, and MHZ end-boss weather visual scalars now compact-restore without stale coverage-baseline keys.
- **S3K gumball/MGZ helper scalar rewind cleanup:** gumball ejection/platform/body-overlay children plus MGZ miniboss fragment and camera helper scalars now compact-restore without stale coverage-baseline keys.
- **S3K bridge/particle scalar rewind cleanup:** LBZ trigger bridge, HCZ vortex bubble, ICZ snowdust, collapsing-platform fragment, and tension-bridge fragment scalars now compact-restore without stale coverage-baseline keys.
- **Effect/fragment scalar rewind cleanup:** shared breathing bubbles and splashes plus S3K breakable-wall fragments, ICZ end-boss debris, and HCZ egg capsule scalars now compact-restore without stale coverage-baseline keys.
- **S3K mechanism scalar rewind cleanup:** HCZ hand launcher, ICZ ice spikes, and LBZ cup elevator constructor scalars now compact-restore without stale coverage-baseline keys.
- **S1/S2 object scalar rewind cleanup:** Sonic 1 Bomb fuse plus Sonic 2 flipper, launcher ball, and OOZ launcher constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K controller/platform scalar rewind cleanup:** MHZ1 Knuckles, miniboss tree chips, defeat signpost flow, cutscene button, collapsing platform, starpost, and shared skid-dust constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K object scalar rewind cleanup:** ship controller, hidden monitor, sinking mud, SS entry ring, spring, twisted ramp, and updraft constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K badnik/cutscene scalar rewind cleanup:** Blastoid, Madmole, Monkey Dude, and MHZ2 Knuckles leaf-particle constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K MHZ mechanism scalar rewind cleanup:** curled vine, mushroom cap/catapult/platform, ship propeller, and twisted vine constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K MGZ swing/loop/debris scalar rewind cleanup:** swinging platform, twisting loop, and end-boss defeat debris constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K MGZ trigger/platform scalar rewind cleanup:** dash trigger, head trigger, head projectile, smashing pillar, and moving spike platform constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K LBZ mechanism scalar rewind cleanup:** player launcher, ride grapple, and rolling drum constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K ICZ/LBZ scalar rewind cleanup:** ICZ tension-platform root scalars plus LBZ alarm, pole, trigger, and flame-thrower constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K ICZ snow-object scalar rewind cleanup:** snow-pile, snow-pile debris, and snowboard-dust constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K ICZ platform-hazard scalar rewind cleanup:** crushing-column, path-follow-platform, and stalagtite constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K ICZ ice-object scalar rewind cleanup:** breakable-wall, harmful-ice, ice-block, and ice-cube constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K HCZ2 cutscene-button scalar rewind cleanup:** cutscene-button constructor coordinates now compact-restore without stale coverage-baseline keys.
- **S3K HCZ twisting-loop scalar rewind cleanup:** twisting-loop constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K HCZ water-effect scalar rewind cleanup:** water-drop and water-splash constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K HCZ water-wall scalar rewind cleanup:** water-wall and splash/spray child constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K HCZ spinning-column scalar rewind cleanup:** spinning-column constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K button scalar rewind cleanup:** button constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K MGZ swinging spike-ball scalar rewind cleanup:** spike-ball constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K ICZ swinging platform scalar rewind cleanup:** swinging-platform constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K LBZ moving platform scalar rewind cleanup:** moving-platform constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K MGZ trigger platform scalar rewind cleanup:** trigger platform constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K collapsing bridge child scalar rewind cleanup:** bridge fragments and MGZ stomp debris now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K cork floor scalar rewind cleanup:** cork-floor and fragment constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K ICZ freezer scalar rewind cleanup:** freezer, capture-cloud, and frost-puff constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K HCZ/CGZ fan scalar rewind cleanup:** fan, platform, and bubble constructor scalars now compact-restore without stale coverage-baseline keys.
- **S3K gravity debris scalar rewind cleanup:** shared gravity-driven debris now compact-restores its constructor gravity without stale coverage-baseline keys.
- **S3K CNZ scalar rewind cleanup:** CNZ cutscene buttons, cannon puffs, cylinders, teleporter/capsule helpers, and miniboss debris now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K badnik child scalar rewind cleanup:** badnik debris, visual, launcher, arm, and TurboSpiker particle helpers now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K badnik scalar rewind cleanup:** shared S3K badnik render/collision metadata and TurboSpiker particle priority now compact-restore without stale coverage-baseline keys.
- **S3K HCZ conveyor scalar rewind cleanup:** HCZ conveyor belts, conveyor spikes, large fans, and snake blocks now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K HCZ breakable-bar scalar rewind cleanup:** HCZ breakable bars and debris now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K AIZ/LRZ mechanism scalar rewind cleanup:** draw bridges, emerald scatter objects, and AIZ/LRZ rocks now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K shared mechanism scalar rewind cleanup:** doors, floating platforms, gumball items, and HCZ blocks now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K CNZ mechanism scalar rewind cleanup:** CNZ balloons, barber poles, bumpers, wheels, hover fans, teleporter beams, triangle bumpers, vacuum tubes, water buttons, and wire cages now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K mechanism scalar rewind cleanup:** animated stills, auto-spinners, automatic tunnels, breakable walls, and bubblers now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K AIZ vine/tree scalar rewind cleanup:** foreground plants, ride vines, hollow trees, miniboss impact flames, ship bombs, and spiked logs now compact-restore constructor scalars without stale coverage-baseline keys.
- **S3K AIZ rewind scalar cleanup:** AIZ bridge, floor, falling-log, flipping-bridge, and bomb-explosion constructor scalars now compact-restore without stale coverage-baseline keys.
- **S2 launcher rewind cleanup:** Speed Launcher and Small Metal Platform child constructor scalars now compact-restore without stale final-scalar coverage-baseline keys.

### v0.6.prerelease (Current development snapshot)

The active 0.6 prerelease line is focused on S3K vertical-slice parity, trace-driven ROM accuracy, release hardening, and gameplay-scoped rewind reliability. Detailed per-frontier notes were moved out of this top-level changelog so it stays readable; see [docs/TRACE_FRONTIER_LOG.md](docs/TRACE_FRONTIER_LOG.md) for frame-by-frame trace evidence and [docs/changelog/v0.6-prerelease-detailed.md](docs/changelog/v0.6-prerelease-detailed.md) for the previous verbose merge ledger.

- **S2 MTZ long-platform cog graph rewind coverage:** MTZ long-platform cogs now restore through generic rewind recreate with child-parent relinking, standalone-mode preservation, and missing-parent drop coverage.
- **S2 checkpoint child rewind cleanup:** Checkpoint dongle/star children now compact-restore their captured orbit centers without stale constructor-scalar coverage baselines.
- **S2 breakable-block fragment rewind coverage:** Breakable-block fragments now restore through generic rewind recreate with compact-restored physics and frame state.
- **S2 collapsing-platform fragment rewind coverage:** Collapsing-platform fragments now restore through generic rewind recreate with graph-tested identity preservation and compact-restored visual metadata.
- **S2 conveyor/capsule scalar rewind cleanup:** MTZ conveyors and destroyed Egg Prison visuals now compact-restore constructor-derived position/flip scalars without stale coverage-baseline keys.
- **Falling-fragment scalar rewind cleanup:** shared collapsing-floor/platform fragments now compact-restore constructor-derived position/priority scalars without stale coverage-baseline keys.
- **S2 transient scalar rewind cleanup:** monitor contents, steam puffs, spin tubes, vertical lasers, badnik projectiles, and Spiker drills now compact-restore constructor scalars without stale coverage-baseline keys.
- **S1 graph scalar rewind cleanup:** Egg Prison buttons, MZ glass reflections, junctions, lava geysers, and lava walls now compact-restore graph constructor scalars without stale coverage-baseline keys.
- **S2 CPZ Spin Tube rewind coverage:** CPZ spin tubes now restore through generic rewind recreate while compact restore preserves constructor-derived collision distance and frame-timer state.
- **S2 collapsing-platform parent rewind cleanup:** OOZ/MCZ/ARZ collapsing-platform parents now compact-restore captured flip flags without stale constructor-scalar coverage baselines.
- **S2 Swinging Platform graph rewind coverage:** OOZ/ARZ/MCZ swinging platforms now restore through generic rewind recreate with display-child relinking and compact configuration restore.
- **S2 Cog graph rewind coverage:** MTZ cog parents now restore through generic rewind recreate with slot-child graph relinking and compact base/rotation restore.
- **S2 Falling Pillar graph rewind coverage:** ARZ falling pillars now restore through generic rewind recreate with captured lower-section child links and compact child-mode restore.
- **S2 Sideways Platform graph rewind coverage:** CPZ/MCZ sideways platform pairs now restore through generic rewind recreate with captured sibling links and required identity checks.
- **S2 MCZ rotating-platform graph rewind coverage:** MCZ rotating platforms now restore through generic rewind recreate with graph-tested parent child-list relinking and required child identity checks.
- **S2 Rivet rewind coverage:** WFZ rivets now restore through generic rewind recreate while treating their fallback main-player cache as transient.
- **S2 Breakable Plating rewind coverage:** WFZ breakable plating now restores through generic rewind recreate, keeps fragment coordinates compact-restorable, and treats the fallback player cache as transient.
- **S3K AIZ/HCZ/MGZ object parent rewind coverage:** AIZ ride vines and hollow-tree controllers, generic S3K breakable walls, HCZ breakable bars, and MGZ head triggers/swingers now restore through spawn-based generic rewind recreate.
- **S3K ICZ/static object parent rewind coverage:** ICZ crushing columns, segmented columns, tension platforms, collapsing platforms, and S3K starposts now restore through spawn-based generic rewind recreate.
- **S3K ICZ debris rewind coverage:** ICZ breakable-wall, harmful-ice, ice-cube, segmented-column, and stalagtite debris children now restore through spawn-based generic rewind recreate.
- **S3K dynamic child rewind coverage:** ICZ snow-pile debris, ICZ freezer ice debris, and Clamer auto-close projectiles now restore through spawn-based generic rewind recreate.
- **S3K debris particle rewind coverage:** HCZ breakable-bar debris, Cluckoid breath debris, and Penguinator snowdust now restore through spawn-based generic rewind recreate.
- **S3K HCZ water-wall child rewind coverage:** Water-wall debris, spray, and splash particles now restore through spawn-encoded generic rewind recreate.
- **S3K particle child rewind coverage:** CNZ cannon launch puffs, ICZ snowboard dust, and HCZ end-boss geyser debris now restore through spawn-encoded generic rewind recreate.
- **S3K Turbo Spiker particle rewind coverage:** Shell-drip, water-splash, and shared animated particle effects now restore through spawn-encoded generic rewind recreate.
- **S3K MHZ tree-chip rewind coverage:** MHZ miniboss tree chips now restore through spawn-encoded generic rewind recreate.
- **S3K MHZ2 leaf-particle rewind coverage:** MHZ2 Knuckles cutscene leaf particles now restore through spawn-encoded generic rewind recreate.
- **S3K ICZ frost-puff rewind coverage:** ICZ freezer frost puffs now restore through spawn-encoded generic rewind recreate.
- **S3K AIZ draw-bridge segment rewind coverage:** AIZ draw-bridge falling segments now restore through spawn-encoded generic rewind recreate.
- **S3K Sparkle child rewind coverage:** Sparkle warning and projectile children now restore through spawn-encoded generic rewind recreate.
- **S3K MHZ swing-bar rewind coverage:** Horizontal and vertical swing bars now restore through spawn-encoded generic rewind recreate.
- **S3K MHZ mechanism rewind coverage:** Curled vines, mushroom caps, and mushroom platforms now restore through spawn-encoded generic rewind recreate.
- **S3K mechanism rewind coverage:** CNZ wire cages, gumball items, MHZ mushroom catapults, and S3K springs now restore through spawn-encoded generic rewind recreate.
- **S3K LBZ miniboss-box rewind coverage:** LBZ Sonic/Tails and Knuckles miniboss box controllers now restore through spawn-encoded generic rewind recreate.
- **S3K MHZ/MGZ mechanism rewind coverage:** MHZ miniboss trees, MHZ ship propellers, and MGZ end-boss debris now restore through spawn-encoded generic rewind recreate.
- **S3K Corkey nozzle graph rewind coverage:** Corkey nozzles now restore through graph-aware generic rewind recreate, relinking to the restored Corkey parent without stale references or duplicate children.
- **S1 MZ glass graph rewind coverage:** MZ glass blocks now restore through generic rewind recreate with reflection back-references captured and graph-tested for fresh two-way relinking.
- **S1 SBZ junction graph rewind coverage:** SBZ junction parents now restore through generic rewind recreate with display-child back-references captured and graph-tested to avoid duplicate children.
- **S1 MZ lava wall graph rewind coverage:** MZ lava wall main/trail pairs now restore through graph-aware generic rewind recreate with captured main-wall references and duplicate-trail regression coverage.
- **S1 MZ lava geyser graph rewind coverage:** MZ lava geyser makers and lavafall head/body/third-piece families now restore through graph-aware generic rewind recreate with captured maker/head references and duplicate-child regression coverage.
- **S1 destruction-fragment graph rewind coverage:** Breakable-wall and MZ smash-block fragments now restore through graph-tested generic rewind recreate with spawn-encoded render-piece metadata and compact motion-state restore.
- **S1 spiked-ball chain graph rewind coverage:** Obj57 chain children now restore through graph-aware generic rewind recreate with spawn-encoded child metadata and parent-slot relinking.
- **S1 collapsing-fragment rewind coverage:** Collapsing floor and ledge fragments now round-trip through spawn-based generic rewind recreate with encoded render-piece metadata.
- **S1 Egg Prison button graph rewind coverage:** Egg Prison buttons now restore through generic rewind recreate, relink to the nearest restored capsule body, and preserve the required parent identity invariant.
- **S3K HCZ/LBZ/MGZ object parent rewind coverage:** HCZ snake blocks and water walls, LBZ cup-elevator poles, flame children, player launchers, ride grapples, and MGZ moving/trigger platforms now restore through spawn-based generic rewind recreate.
- **S3K LBZ/MGZ object parent rewind coverage:** LBZ exploding triggers, flame throwers, moving platforms, rolling drums, MGZ dash triggers, and MGZ/LBZ smashing pillars now restore through spawn-based generic rewind recreate.
- **S1 runtime spawn-recreate rewind coverage:** Bubbles, bumpers, running discs, and teleporters now rely on spawn-based generic rewind recreate without stale final-scalar or frame-local reference coverage gaps.
- **S3K badnik parent rewind coverage:** Monkey Dude, Orbinaut, Penguinator, Poindexter, Rhinobot, Ribot, Sparkle, and Star Pointer now restore through spawn-based generic rewind recreate while inherited constructor-scalar baselines remain explicit.
- **S3K badnik parent rewind coverage:** Buggernaut, Cluckoid, Corkey, Flybot 767, Jawz, and Madmole now restore through spawn-based generic rewind recreate while inherited constructor-scalar baselines remain explicit.
- **S3K badnik parent rewind coverage:** Batbot, Blastoid, Bloominator, Bubbles, and Butterdroid now restore through spawn-based generic rewind recreate while inherited constructor-scalar baselines remain explicit.
- **S2 boss controller rewind coverage:** CNZ and MCZ boss parents now restore through generic spawn-based rewind recreate without stale recreate baselines.
- **S3K stage-controller rewind coverage:** AIZ transition floor, CNZ capsule, HCZ2 wall, and ICZ post-boss palette controllers now restore through generic rewind recreate without stale recreate baselines.
- **S1 boss controller rewind coverage:** False-floor, LZ boss, and MZ boss controllers now restore through generic rewind recreate without stale recreate baselines.
- **S3K Knuckles cutscene rewind coverage:** AIZ2, CNZ2B, HCZ2, LBZ1, and MHZ2 Knuckles cutscene controllers now restore through spawn-based generic rewind recreate without stale recreate baselines.
- **S3K standalone cutscene rewind coverage:** AIZ miniboss, MHZ1/SK intro, LBZ1 launch/Robotnik, and cutscene button controllers now restore through spawn-based generic rewind recreate without stale recreate baselines.
- **S1 junction child rewind coverage:** The SBZ rotating-junction display child now restores through spawn-based generic rewind recreate instead of relying on an accept-drop baseline.
- **Shared placeholder rewind coverage:** Unmapped placeholder objects now restore through generic rewind recreate, keeping fallback object probes from dropping across rewind.
- **Sonic 2 collapsing-platform rewind coverage:** OOZ/MCZ/ARZ collapsing-platform parents now restore through generic rewind recreate while fragment children remain graph-scoped follow-up work.
- **Sonic 2 MTZ Long Platform rewind coverage:** MTZ long platforms now restore through generic rewind recreate while compact restore preserves movement trigger and distance state.
- **Sonic 2 MTZ Spin Tube rewind coverage:** MTZ spin tube transport objects now restore through generic rewind recreate while compact restore preserves per-player tube traversal state.
- **Sonic 2 Speed Launcher rewind coverage:** WFZ speed launcher objects now restore through generic rewind recreate, preserving their spawn-derived launcher state without a private restore path.
- **S1 scalar spawn-recreate rewind coverage:** Big spiked balls, buttons, conveyors, edge walls, and electrocuters now restore constructor-derived scalars through generic spawn recreate instead of stale final-scalar baselines.
- **S1 hazard/platform spawn-recreate rewind coverage:** Fans, flamethrowers, gargoyles, girder blocks, LZ conveyors, and labyrinth blocks now restore spawn-derived scalar state through generic spawn recreate without stale final-scalar coverage gaps.
- **S1 platform/hazard spawn-recreate rewind coverage:** Elevators, floating blocks, lampposts, lava-ball makers, lava tags, and moving blocks now restore constructor-derived scalar state through generic recreate without stale final-scalar coverage gaps.
- **S1 hazard/platform scalar rewind coverage:** Lava balls, platforms, saws, scenery, small doors, spikes, and springs now replay constructor-derived scalar state through generic recreate without stale final-scalar baselines.
- **S1 SBZ platform and badnik scalar rewind coverage:** Spin conveyors, spinning platforms, staircases, vanishing platforms, Choppers, Jaws, and Newtrons now replay constructor-derived scalar state through generic recreate without stale final-scalar baselines.
- **S1 collapsing/smashable object scalar rewind coverage:** Breakable walls, chained stompers, collapsing floors/ledges, monitors, monitor power-ups, and smash blocks now replay constructor-derived scalar state through generic recreate without stale final-scalar baselines.
- **S1 push/chain/door scalar rewind coverage:** Push blocks, spiked-ball chains, spiked-pole helixes, and stomper doors now replay constructor-derived scalar state through generic recreate without stale final-scalar baselines.
- **S1 explosion/swing scalar rewind coverage:** Badnik replacement explosions, Buzz Bomber missile dissolve effects, and swinging platforms now replay captured scalar anchor/state fields through generic recreate without stale final-scalar baselines.
- **S1 projectile scalar rewind coverage:** Crabmeat and Newtron projectiles now replay shared projectile collision/gravity/offscreen scalar state through generic recreate without stale final-scalar baselines.
- **S1 circling-platform/checkpoint scalar rewind coverage:** SLZ circling platforms now use spawn-based generic rewind recreate, and S1 checkpoint twirl centers now replay through compact scalar restore without stale final-scalar baselines.
- **S1 grass-fire platform rewind coverage:** MZ large grassy platforms now use spawn-based generic rewind recreate, preserving the existing Grass Fire graph restore proof while shedding stale constructor-scalar baselines.
- **S1 animal rewind coverage:** Enemy-spawned and ending animals now use spawn-based generic rewind recreate with compact restore for subtype and score-chain state.
- **S1 collapsing fragment rewind coverage:** Collapsing floor and ledge fragments now rebuild through generic rewind recreate and replay captured fragment mapping/flip state through compact restore instead of stale baseline gaps.
- **S1 badnik graph parent rewind coverage:** Caterkiller and Orbinaut parents now rebuild through generic rewind recreate while the S1 badnik graph harness proves their fuse/body/spike children relink to restored parents.
- **S1/S2 seesaw graph parent rewind coverage:** Seesaw parents now rebuild through generic rewind recreate while the seesaw graph harness keeps their spikeball children relinked to restored parents.
- **S1 boss graph parent rewind coverage:** GHZ, SLZ, SYZ, and FZ boss parents now rebuild through generic rewind recreate while their graph harnesses keep restored child references pointed at restored bosses.
- **S2 Egg Prison graph parent rewind coverage:** Egg Prison parents now rebuild through generic rewind recreate while the button graph harness keeps restored button backrefs and nullable parent links intact.
- **S2 ARZ/OOZ graph parent rewind coverage:** ARZ boss and OOZ popping-platform parents now rebuild through generic rewind recreate while their arrow/eyes and burner-flame graph harnesses keep restored child links pointed at restored parents.
- **S2 boss graph parent rewind coverage:** CPZ, EHZ, and HTZ boss parents now rebuild through generic rewind recreate while their graph harnesses keep restored child references pointed at restored bosses.
- **S2 WFZ boss graph parent rewind coverage:** The Wing Fortress boss parent now rebuilds through generic rewind recreate while the WFZ graph harness keeps its restored walls, platform, laser, and Robotnik links pointed at restored instances.
- **S3K AIZ miniboss graph parent rewind coverage:** The AIZ miniboss parent now rebuilds through generic rewind recreate while its graph harness keeps restored body, arm, napalm, barrel, shot, and flare links pointed at restored instances.
- **S3K AIZ intro graph parent rewind coverage:** The AIZ intro parent now rebuilds through generic rewind recreate while the intro graph harness keeps restored plane and wave children linked to the restored active parent.
- **S3K AIZ spiked-log parent rewind coverage:** The AIZ spiked-log parent now uses generic rewind recreate while the graph harness keeps restored spike children linked to their restored log parents.
- **S3K AIZ falling-log parent rewind coverage:** The AIZ falling-log spawner now uses generic rewind recreate while the existing log/splash graph harness keeps paired children relinked exactly after restore.
- **S3K AIZ disappearing-floor rewind coverage:** The AIZ disappearing-floor parent and border child now restore through the generic rewind recreate path, backed by a graph harness that keeps the child linked to the restored parent.
- **S3K AIZ collapsing-log bridge rewind coverage:** The AIZ collapsing-log bridge parent and falling segment now restore through generic rewind recreate while graph coverage preserves fire-bridge segment construction state.
- **S3K AIZ flipping-bridge rewind coverage:** The AIZ flipping bridge now restores through generic rewind recreate while object-manager coverage preserves spawn-derived animation setup and live segment frames.
- **S3K AIZ1 static-scenery rewind coverage:** The AIZ1 tree and zipline peg now restore through generic rewind recreate while object-manager coverage preserves their captured spawns without drops or duplicates.
- **S3K decorative-object rewind coverage:** AIZ foreground plants and animated still sprites now restore through generic rewind recreate while object-manager coverage preserves captured spawns and animation frame state.
- **S3K button/path-swap rewind coverage:** Floor buttons and path-swap slot markers now restore through generic rewind recreate with object-manager coverage for trigger latches and captured spawns.
- **S3K utility-object rewind coverage:** Hidden monitors, sinking mud, and SS-entry rings now restore through generic rewind recreate with object-manager coverage for captured spawn and mutable utility state.
- **S3K CNZ local-mechanics rewind coverage:** CNZ balloons, rising platforms, light bulbs, and barber poles now restore through generic rewind recreate with object-manager coverage for captured spawns and constructor-derived local state.
- **S3K CNZ mechanism rewind coverage:** CNZ giant wheels, hover fans, spiral tubes, teleporter beams, trap doors, triangle bumpers, vacuum tubes, and water-level buttons now restore through generic rewind recreate under object-manager coverage.
- **S3K ICZ ice-object rewind coverage:** ICZ breakable walls, harmful ice, ice blocks, and ice cubes now restore through generic rewind recreate under object-manager coverage.
- **S3K HCZ mechanism rewind coverage:** HCZ blocks, conveyor spikes, large fans, spinning columns, and water splashes now restore through generic rewind recreate under object-manager coverage.
- **S3K ICZ platform/hazard rewind coverage:** ICZ path-follow platforms, swinging platforms, stalagtites, and snow piles now restore through generic rewind recreate under object-manager coverage.
- **S3K utility/motion rewind coverage:** Automatic tunnels, auto-spin triggers, bubblers, and doors now restore through generic rewind recreate under object-manager coverage.
- **S3K pachinko/standalone rewind coverage:** Floating platforms, gumball triangle bumpers, pachinko bumpers, magnet orbs, platforms, and triangle bumpers now restore through generic rewind recreate under object-manager coverage.
- **S3K controller rewind coverage:** Twisted ramps, updrafts, HCZ/MGZ twisting loops, and MHZ twisted vines now restore through generic rewind recreate under object-manager coverage.
- **S2/S3K standalone controller rewind coverage:** MTZ nuts, LBZ barriers/alarms, HCZ water-drop spawners, MHZ pollen spawners, and MGZ post-boss controllers now restore through generic rewind recreate under object-manager coverage.
- **S1 false-floor fragment rewind coverage:** SBZ2 false-floor falling fragments now rebuild through generic rewind recreate while object-manager coverage preserves captured motion and mapping state.
- **S2 Moving Vine rewind coverage:** MCZ/WFZ moving vines now restore through generic rewind recreate while object-manager coverage preserves extension and grab-state scalars.
- **S2 Point Pokey rewind coverage:** CNZ Point Pokey cages now restore through generic rewind recreate while object-manager coverage preserves occupied cage and prize-spawn state.
- **S2 scalar named-object rewind coverage:** Arrow shooters and one-way barriers now preserve registry object names while rebuilding through generic rewind recreate, raising the round-trip ratchet without explicit dynamic codecs.
- **S2 CNZ scalar rewind coverage:** Round bumpers, hex bumpers, bonus blocks, bubble generators, and Sky Chase clouds now rebuild through generic rewind recreate, raising the round-trip ratchet without explicit dynamic codecs.
- **S2 utility scalar rewind coverage:** Buttons, Clucker bases, fans, lava markers, bridge stakes, and EHZ waterfalls now rebuild through generic rewind recreate, tightening constructor-scalar baselines without explicit dynamic codecs.
- **S2 platform/visual rewind coverage:** CPZ pylons, WFZ lasers and wall turrets, MTZ lava bubbles, CPZ platforms, and MTZ platforms now rebuild through generic rewind recreate without explicit dynamic codecs.
- **S2 mechanism scalar rewind coverage:** ARZ platforms, ARZ rotating platforms, CPZ blue balls, CPZ staircases, WFZ horizontal propellers, ARZ rising pillars, WFZ small-metal-platform spawners, and S2 layer switchers now rebuild through generic rewind recreate without stale constructor-scalar baselines.
- **S2 box/solid scalar rewind coverage:** Bridges, checkpoints, forced-spin triggers, pipe-exit springs, standard platforms, and springs now rebuild through generic rewind recreate, while shared box-object constructor scalars are replayable through compact restore.
- **S2 box-solid tail rewind coverage:** Breakable blocks, CNZ big blocks, CNZ rectangular blocks, and CNZ elevators now rebuild through generic rewind recreate without stale constructor-scalar baselines.
- **S2 trigger/motion scalar rewind coverage:** CNZ conveyor belts, MTZ floor spikes, HTZ lifts, ARZ leaf generators, and WFZ palette switchers now rebuild through generic rewind recreate without stale constructor-scalar baselines.
- **S2 trigger/platform scalar rewind coverage:** Lateral cannons, MTZ spring walls, sliding spikes, speed boosters, standard spikes, stompers, tipping floors, and vine switches now rebuild through generic rewind recreate without stale constructor-scalar baselines.
- **S2 platform-mechanism scalar rewind coverage:** Large rotating platforms, MCZ bricks/bridges/drawbridges, MTZ twin stompers, HTZ rising lava, WFZ vertical propellers, and WFZ ship fire now rebuild through generic rewind recreate without stale constructor-scalar baselines.
- **S2 top-level badnik scalar rewind coverage:** Asterons, Chop Chops, Cluckers, Coconuts, Crawltons, Flashers, Mashers, Nebulas, Octuses, Spikers, Spinies, wall Spinies, WFZ stick/unknown badniks, and Whisps now rebuild through generic rewind recreate without stale constructor-scalar baselines.
- **S2 Crawl rewind coverage:** Crawl badniks now rebuild through generic rewind recreate without their stale recreate baseline.
- **S2 badnik parent graph rewind coverage:** Balkiry, Rexon, Shellcracker, Slicer, and Sol parent badniks now rebuild through generic rewind recreate under graph/session restore coverage.
- **S2 Turtloid graph rewind coverage:** Turtloid parents now rebuild through generic rewind recreate while the badnik graph harness proves rider and jet children relink to restored parents without drops or duplicates.
- **S2 Aquis graph rewind coverage:** Aquis parents and private wing children now rebuild through generic rewind recreate while the badnik graph harness proves restored wing links point at restored parents.
- **S2 badnik graph coverage cleanup:** Buzzer flame children and Sol parent badniks now replay their remaining constructor-derived rewind scalars through restored mutable state, removing stale final-scalar coverage baselines.
- **S2 misc scalar rewind coverage:** HTZ fire shooters, signposts, steam springs, tornado smoke, and WFZ wheels now rebuild through generic rewind recreate without stale constructor-scalar baselines.
- **S2 mechanism-tail rewind coverage:** Monitors, spiky blocks, springboards, and Buzzer bodies now rebuild through generic rewind recreate without stale constructor-scalar baselines.
- **S2 fragment-parent rewind coverage:** Smashable ground and WFZ tilting-platform parent objects now rebuild through generic rewind recreate without stale constructor-scalar baselines.
- **S2 interaction scalar rewind coverage:** Grab points and ARZ swinging platforms now rebuild through generic rewind recreate without stale recreate baselines.
- **S1 effect scalar rewind coverage:** Water splashes and Motobug smoke now rebuild through generic rewind recreate without stale effect-scalar baselines.
- **S1 boss-fire scalar rewind coverage:** Marble Zone boss fire now rebuilds through generic rewind recreate without its stale recreate baseline.
- **S1 Gargoyle fireball rewind restore coverage:** Gargoyle fireballs now restore through generic spawn-based rewind recreate, preserving captured direction/speed state without an explicit dynamic codec and tightening the coverage baseline.
- **S2 DEZ Eggman exhaust-puff rewind restore coverage:** DEZ Eggman exhaust puffs now restore through generic spawn-based rewind recreate, preserving captured scalar motion/frame state without an explicit dynamic codec and tightening the coverage baseline.
- **S2 HTZ boss smoke rewind restore coverage:** HTZ boss defeat smoke particles now restore through generic spawn-based rewind recreate and are included in the HTZ boss graph restore proof alongside the flame and lava hazards.
- **S1 Seesaw tilt target computed post-player, atomic with the frame advance (SLZ):** the SLZ Seesaw (Obj 5E, `Sonic1SeesawObjectInstance`) now computes its tilt target (`See_ChkSide`) inside `update()` immediately before the `See_ChgFrame` mapping-frame advance, instead of latching it in `onSolidContact`. ROM `See_Slope2` (routine 4, `docs/s1disasm/_incObj/5E SLZ Seesaw.asm:71-118`) runs `See_ChkSide` (sets `see_frame` from the player's current x, then falls into `See_ChgFrame`) entirely inside `ExecuteObjects`, AFTER the player slot has moved. The engine runs S1 objects after player physics (`objectsExecuteAfterPlayerPhysics=true`), so `update()` already observes Sonic's post-move x — but the target was being latched in `onSolidContact`, which fires during the player's solid pass (BEFORE the post-physics object update), so `See_ChgFrame` advanced `obFrame` using the PREVIOUS frame's target. As the player rocked across the seesaw, the engine's tilt flip (`obFrame` 0x02→0x01 when the rocking player crossed within 8px of centre) lagged ROM by a frame, re-seating the rider on the stale (tilted) slope: at SLZ3 trace f745 ROM's seesaw is flat (`obj_frame=0x01`, confirmed by the v3.5 recorder `object_near` aux) and seats Sonic at y=0x02D8, while the engine still had it tilted (`0x02`) and seated him 3px low at 0x02DB; the lag also shifted the spikeball launch (f786). Moving `See_ChkSide` into `update()` (gated on the standing bit, which `onSolidContact` still maintains) keeps the ROM `ChkSide→ChgFrame` order atomic and post-move. Advances the S1 SLZ3 complete-run frontier **f745 -> f814** (resolving the f745/f756 tilt re-seat blips AND the f786 spikeball-launch cascade; the new f814 root is a deeper spikeball launch `y_speed` divergence). Object-local to Obj 5E (SLZ-only spawn). Regression-free: SLZ1 (f2872) and SLZ2 (f1714) byte-identical, MZ3 (f2079) unchanged, GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, and `TestSeesawBallGraphRewind`, `TestS1SlzBossSpikeballGraphRewind`, `TestSonic1LargeGrassyPlatformObjectInstance`, `TestCollisionLogic` all pass.
- **S1 Vanishing Platform reads v_framecount for its cycle gate + PlatformObject landing family (SBZ):** two stacked fixes to the SBZ vanishing platform (Obj 6C, `Sonic1VanishingPlatformObjectInstance`). (1) *Cycle-phase gate:* `updateIdle` now reads `v_framecount` (`levelManager.getFrameCounter()+1`) for the routine 6->2 transition gate `(v_framecount - objoff_36) & objoff_38 == 0` instead of the `update()` `frameCounter` parameter. ROM `VanP_Sync` reads `(v_framecount).w` (`docs/s1disasm/_incObj/6C SBZ Vanishing Platforms.asm:51-56`), but the engine's `frameCounter` param is the VBla clock for S1 objects (ObjectManager passes vblaCounter), which runs out of phase with `v_framecount`. The platform therefore entered its vanish/appear cycle at the wrong absolute frame (SBZ1 trace: VBla `0x8500` multiple at vfc 2170 vs ROM's `v_framecount` `0x800` multiple at vfc 2048 — a 122-frame phase error), leaving the `@0BB0,0648` platform half a cycle out of phase (engine vanished while ROM solid) so Sonic fell through. Same fix class as the SBZ Electrocuter vfc gate. The v3.5 trace's `object_near` `obj_frame` confirmed the target platform is solid (obj_frame 0) at f2268 in both ROM and the engine after this fix. (2) *Landing-surface family:* the platform now opts into the PlatformObject landing-surface family — `HALF_HEIGHT` 8 -> **9** (`MvSonicOnPtfm2 subi.w #9`, `docs/s1disasm/_incObj/sub MvSonicOnPtfm.asm:18-41`), `usesCollisionHalfWidthForTopLanding()`, `rejectsZeroDistanceTopSolidLanding()`, and `getTopLandingSnapAdjustment()=-1` (PlatformObject's obY-8 first-landing detection, `docs/s1disasm/_incObj/sub PlatformObject.asm:37-38`). ROM `VanP` lands the first contact via `PlatformObject` (routine 2) and continued riding via `MvSonicOnPtfm2` (routine 4) (`6C SBZ Vanishing Platforms.asm:82-93`) — the same top-solid family as Obj 18 (`Sonic1PlatformObjectInstance`). Without these the now-solid platform still failed to catch the falling player on the ROM frame (engine `y=0x062F` airborne vs ROM `y=0x062C` landed). Together the two fixes land Sonic on the re-appeared platform exactly like ROM. Advances the S1 SBZ1 complete-run frontier **f2268 -> f3971** (805 -> 560 errors). Object-local to Obj 6C (SBZ-only). Regression-free: GHZ1/GHZ2/SBZ3/SYZ2 stay GREEN, every other S1 complete-run frontier byte-identical (SBZ2 f1395, MZ2 f2819, SLZ1 f2872, LZ1 f5745), and `CollisionSystemTest` (54/0), `TestSolidRoutineProfiles` (13/0), `TestSonic1PlatformObjectInstanceRespawn` (1/0) pass.
- **S1 Seesaw uses absolute slope values for landing (SLZ):** the SLZ Seesaw (Obj 5E, `Sonic1SeesawObjectInstance`) now returns `getSlopeBaseline()=0` instead of `COLLISION_HEIGHT` (8). ROM `See_Slope` (routine 2, `docs/s1disasm/_incObj/5E SLZ Seesaw.asm:67`) lands the falling player via `SlopeObject`, which computes the surface as an ABSOLUTE value — `d0 = obY(a0) - heightmapByte` with no baseline subtraction (`docs/s1disasm/_incObj/sub PlatformObject.asm:150-152`). The non-zero baseline pushed the engine's sampled seesaw top surface 8px below ROM's, so a player falling onto the seesaw fell ~8px further before the engine registered the top-solid landing, landing one frame late: at SLZ3 trace f718 ROM snaps `y_speed` to 0 and seats the player on the seesaw (status bit 3, `g_speed=x_speed=-0168`), while the engine kept falling (`y_speed=0x0610`, airborne) until f719. Matching the sibling `SlopeObject` user (`Sonic1CollapsingLedgeObjectInstance`, which already returns baseline 0 for the same ROM reason) realigns the landing frame. Advances the S1 SLZ3 complete-run frontier **f718 -> f745** (1073 -> 916 errors; a residual seesaw-tilt re-seat 1-frame phase blip at f745/f756 then a deeper player-physics root at f786 are the new frontier). Object-local to Obj 5E (SLZ-only spawn). Regression-free: SLZ1 (f2872) and SLZ2 (f1714) byte-identical, GHZ1/GHZ2/SYZ2 stay GREEN, and `TestSonic1LargeGrassyPlatformObjectInstance`, `TestSeesawBallGraphRewind`, `TestS1SlzBossSpikeballGraphRewind`, `TestCollisionLogic` all pass.
- **S1 LR spring control lock counts down only on grounded frames (SLZ/SYZ):** the S1 horizontal spring (Obj 41, `Sonic1SpringObjectInstance.applyHorizontalSpring`) now drives the 15-frame D-pad control lock through the player's `moveLockTimer` instead of the bespoke `springingFrames` countdown. ROM `Spring_BounceLR` writes `move.w #15,locktime(a1)` (`docs/s1disasm/_incObj/41 Springs.asm:145`) — `locktime` (objoff_3E) is the same RAM word S2's horizontal spring writes as `move_lock` (`docs/s2disasm/s2.asm:34031`, `loc_18B1C`). ROM only decrements `locktime` on grounded frames, inside `Sonic_SlopeRepel` (`docs/s1disasm/_incObj/01 Sonic.asm:1383,1410`), which `Sonic_MdNormal`/`Sonic_MdRoll` call but the airborne modes (`Sonic_MdJump`/`MdJump2`) do not — so while Sonic is airborne the lock is **frozen**. The engine models `locktime` as `moveLockTimer` (likewise decremented only in grounded `doSlopeRepel()`), but the S1 spring set `springingFrames`, which `tickStatus()` decrements every frame including airborne. When an LR spring launches Sonic off a ledge into the air the bespoke lock expired several frames early, so the engine started applying D-pad deceleration before ROM did. At S1 SLZ2 trace f1714 the LR spring fired at f06A2 (lock=15) and Sonic was airborne for 6 frames (f06AA-f06AF) that must freeze the lock; ROM still had the lock active at f06B2 and ignored the just-pressed Right input (inertia held at F06C), while the engine's lock had already expired and applied the 0x80 Right-deceleration (F06C->F0EC, x 0x15F2 vs 0x15F3). Driving the lock through `moveLockTimer` restores the grounded-only decrement so the post-launch lock window matches ROM frame-for-frame; `springing` is kept for the air-spring animation / carry marker consumed elsewhere. Advances the S1 SLZ2 complete-run frontier **f1714 -> f2552** (215 -> 137 errors). Object-local to Obj 41's horizontal-spring path (only `Spring_LR` sets `locktime`; vertical/down springs and S2/S3K springs, which write `move_lock` directly, are untouched). Regression-free: GHZ1/GHZ2/SBZ3/SYZ2 stay GREEN, every other S1 complete-run frontier frame/field is byte-identical (SYZ1 stays at f816, +9 downstream-cascade errors in an already-red trace), `TestSonic1SpringObjectInstance` (2/0) and `TestSpringObjectInstance` (10/0) pass, and the S3K must-keep-green tests (`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`) pass.
- **S1 type-03 platform fall-timer starts on the landing frame (GHZ/SLZ/SYZ):** the Obj 18 fall-on-stand platform (type 03, `Sonic1PlatformObjectInstance`) now starts its 30-frame countdown on the frame Sonic first lands, matching ROM. ROM `Plat_Solid` (routine 2) calls `PlatformObject` which sets the standing bit (`bset #3,obStatus(a0)`) then falls through to `Plat_Action`, which calls `Plat_Move`; `.type03` (`docs/s1disasm/_incObj/18 Platforms.asm:201-216`) reads the just-set standing bit and writes `objoff_3A=30` on that same frame. The engine's `moveFallOnStand()` read the PREVIOUS frame's `playerStanding` value (false on the landing frame, because `checkpointAll()` hadn't run yet), so the timer was initialised one frame late, the platform transitioned to falling one frame late, and MvSonicOnPtfm2 held Sonic one frame too long on the surface — in GHZ1 the platform at `x=0x13A0, baseY=0x0188, subtype=03` held Sonic 1px above his ROM Y at trace f3246. Fix: after `checkpointAll()` updates `playerStanding`, detect the fresh first-time standing and set `timer = FALL_STAND_DELAY` if not already set. Greens the S1 GHZ1 complete-run (255 -> 0 errors); zero regressions across all other S1 complete-runs (GHZ2/SYZ2 stay GREEN, SLZ2 stays at f1493, all remaining traces unchanged). Object-local to Obj 18 (shared across GHZ/SLZ/SYZ acts).
- **S1 SpikedPoleHelix animCounter phase and hurt-direction source X (GHZ):** two fixes to the GHZ Spiked Pole Helix (Obj 17, `Sonic1SpikedPoleHelixObjectInstance`). (1) *Phase fix (f4650 -> f5043):* derives `v_ani0_frame` from the trace-seeded gfc each update instead of a per-object unseeded counter. ROM `Hel_RotateSpikes` (`docs/s1disasm/_incObj/17 GHZ Spiked Pole Helix.asm:95-105`) reads the GLOBAL `v_ani0_frame`. (2) *animCounter off-by-one (f5043 -> f6464):* ROM `Level_MainLoop` runs `ExecuteObjects` BEFORE `SynchroAnimate` (`docs/s1disasm/sonic.asm:2988 vs 3010`), so objects at gfc=N read the value from after N-1 SynchroAnimate calls. The correct formula is `(-((gfc+10)/12)) & 7` (not `+11`); the old formula applied one extra tick at multiples of 12, marking a spike harmful 1 trace-frame early. (3) *Hurt-direction source X:* ROM `HurtSonic.checkDirection` (`docs/s1disasm/_incObj/Sonic ReactToItem.asm:402-405`) compares Sonic's X against the individual child spike's `obX(a2)`, not the parent helix X. Engine `applyHurt` was using `instance.getX()` (parent at 0x1688 instead of spike at 0x16C8), reversing the bounce direction. Fix: `TouchResponseResult` now carries a `regionX` from `processMultiRegionTouch`; `applyHurt` uses it when `hasRegionX()`. Cumulatively advances the S1 GHZ3 complete-run frontier **f4650 -> f6464**; object-local to Obj 17 (GHZ-only). Regression-free: GHZ1/GHZ2/SYZ2 stay GREEN, all unit tests pass.
- **S1 SmashableWall smash x-adjust preserves sub-pixel (GHZ/SLZ):** the GHZ/SLZ smashable wall (Obj 3C, `Sonic1BreakableWallObjectInstance`) now applies its post-smash ±4px x adjustment via `player.shiftX(±4)` instead of `player.setCentreX(value)`. ROM `Smash_Solid` (`docs/s1disasm/_incObj/3C GHZ, SLZ Smashable Wall.asm` lines 57-64) adjusts the player position with `addq.w #4,obX(a1)` / `subq.w #8,obX(a1)` — 68000 word writes that modify ONLY `obX` (the integer pixel word), leaving `obSubX` (sub-pixel) intact. `setCentreX(short)` clears the sub-pixel to 0, while `shiftX(int)` adds to the pixel integer only, preserving the fractional accumulation (per `AbstractSprite.shiftX`). At GHZ3 trace f2691 (the smash frame), Sonic's sub-pixel was 0x1200 after the `SolidObject` snap; the engine zeroed it to 0x0000. Over the next two frames the missing 0x1200 sub-pixel meant the carry bit didn't fire at f2693 (engine sub 0x7C00 + 0x7600 = 0xF200 no-carry vs ROM 0x8E00 + 0x7600 = 0x10400 carry), leaving the integer x 1px short (0x083B vs ROM 0x083C). Advances the S1 GHZ3 complete-run frontier **f2693 -> f4650** (477 -> 363 errors); object-local to Obj 3C. Regression-free: GHZ1/GHZ2/SYZ2 stay GREEN, SLZ2 stays at f1493, all other S1 frontiers byte-identical. `TestSonic1BreakableWallObjectInstance` 2/0 pass.
- **S1 Staircase 1px seat-height + timer countdown fidelity (SLZ):** riding the SLZ Staircase (Obj 5B) now seats Sonic at the correct Y. Three bugs stacked on the staircase interaction. (1) `Stair_Type00`/`Stair_Type02` (`docs/s1disasm/_incObj/5B SLZ Staircase.asm:104-119, 122-137`): when the timer is first set the ROM routine falls through to `locret_10FBE` (rts) without decrementing — the engine ran both SET and DECREMENT in the same `update()` call, advancing the countdown 1 frame early. Fixed by `return` after `timer = DELAY` in `Sonic1StaircaseObjectInstance.updateType00` and `updateType02`. (2) Non-riding sibling pieces were applying `Solid_Landed` Y snaps for overlapping X ranges, overriding the ridden-piece re-seat. In ROM, each piece occupies a separate SST slot; the ridden piece (highest slot) runs last and its `MvSonicOnPtfm`/`SolidObject` result is authoritative. Fixed by tracking `ridingCentreYToRestore` from `processInlineRidingObject` and restoring it after the sibling-piece pass in `ObjectSolidContactController.processMultiPieceCollision`. (3) `processMultiPieceCollision` used `groundHalfHeight=17` for grounded players in the Y bounding-box check, but ROM `SolidObject_cont` (`docs/s1disasm/_incObj/sub SolidObject.asm:170-176`; S2 equivalent `s2.asm:35361-35373`) always uses `d2 = airHalfHeight = 16` for the Y detection window (`add.w y_radius, d2`). Using `groundHalfHeight` inflated `maxTop` by 1, giving `distY=4` (engine) vs `distY=3` (ROM) and snapping Sonic 1px too low. Fixed by using `params.airHalfHeight()` unconditionally in `processMultiPieceCollision` (correct for S1/S2/S3K — all three ROM `SolidObject` variants pass the top half-height in d2 for Y detection; `groundHalfHeight`/d3 is only for `MvSonicOnPtfm` continued-ride re-seat). Advances S1 SLZ1 frontier **f933 -> f2872** (246 -> 164 errors). GHZ2 and SYZ2 stay green; all other S1 frontiers byte-identical. Object-local staircase changes; `processMultiPieceCollision` fix is shared but regression-free across S1/S2/S3K multi-piece objects.
- **S1 Orbinaut satellite orbit uses ROM CalcSine integer arithmetic (LZ/SLZ/SBZ):** the Orbinaut satellite spike (Obj 60, `OrbSpikeObjectInstance`) now computes its orbit position via `TrigLookupTable.sinHex`/`cosHex` with an arithmetic right-shift of 4 (`>> 4`), matching ROM `Orb_CircleSpikeball` (`docs/s1disasm/_incObj/60 Badnik - Orbinaut.asm:181-191`): `jsr CalcSine` → `asr.w #4,d1` (cosine component) → `add.w obX(a1),d1` → `obX = d1`; same for sine/Y. The engine previously used `Math.round(Math.cos(radians) * 16.0)` — floating-point rounds 254/16 = 15.875 up to 16, placing a satellite 1 px lower than ROM's `254 >> 4 = 15`. At SLZ2 trace f1016, this 1px Y difference moved the satellite's bottom edge from ROM's 0x00BB to the engine's 0x00BC, aligning it with the player's touch-box top edge at 0x00BC and triggering a premature HURT touch that ROM avoided entirely. Advances the S1 SLZ2 complete-run frontier **f1016 -> f1493** (221 errors before; 277 errors after — different cascade). Object-local to `OrbSpikeObjectInstance`; S1-only (S3K's Orbinaut is a distinct class that already uses integer trig). Regression-free: SYZ2 and GHZ2 stay GREEN, all LZ/SBZ complete-run frontier frames unchanged (LZ1 f5745, LZ2 f1068, LZ3 f6517, SBZ1 f2268, SBZ2 f1395), and `TestSonic1CaterkillerBodyChaining`, `CollisionSystemTest`, `TestSolidRoutineProfiles`, `TestSonic1SpringObjectInstance`, `TestOrbinautBadnikInstance` (S3K) all pass.
- **S1 platform walk-off Y re-seat (SYZ/all moving-platform zones):** when Sonic walks off an S1 moving platform (Obj 18), moving block (Obj 52), or SLZ elevator (Obj 59), the engine now re-seats his Y to the platform's post-move surface on the exit frame, matching ROM. ROM Obj 18 routine 4 (`Plat_Action2`) runs `ExitPlatform` (clears the on-object bit) and then **unconditionally** `MvSonicOnPtfm2` (`docs/s1disasm/_incObj/18 Platforms.asm:74-87`), which sets both Sonic's X (the post-move carry) **and** his Y (`obY = platform_Y - 9 - obHeight`, `sub MvSonicOnPtfm.asm:18-41`). The engine's `ObjectSolidContactController` exit block (`carriesAirborneRiderAfterExitPlatform`) already carried X but skipped the Y re-seat, so when a platform moved vertically on the exit frame (e.g. the Obj 18 `Plat_Nudge` bob) the rider held the pre-move surface Y and ended up 1px off ROM. S1 SYZ3 f3476: the platform bobbed 02DC->02DD on the walk-off frame; ROM re-seated the rider to centre 0x02C1, the engine kept 0x02C0. Adding the flat-surface exit re-seat (guarded so the existing sloped-exit re-seat still owns the slope case) advances the S1 SYZ3 complete-run frontier **f3476 -> f6065** (485 -> 483 errors) and greens the S1 SYZ2 complete-run (which was blocked at f6845 by the same exit-frame fidelity gap). Shared `ObjectSolidContactController` change but gated to the three objects that opt into `carriesAirborneRiderAfterExitPlatform` (all of whose ROM equivalents call `MvSonicOnPtfm2` unconditionally on exit). Regression-free: every other S1 complete-run byte-identical, GHZ2/SBZ3 stay green, S2 EHZ1 + S3K AIZ unchanged, and `CollisionSystemTest`/`TestSolidRoutineProfiles` plus the S1 platform/moving-block/elevator unit tests (`TestSonic1PlatformObjectInstanceRespawn`, `TestSonic1LargeGrassyPlatformObjectInstance`, `TestS1SwingingPlatformSurfaceRegression`, `TestSonic1MovingBlockObjectInstance`, `TestSonic1ElevatorObjectInstance`, `TestS1JumpFromElevator`) all pass.
- **S1 LR spring right-edge solidity (SYZ/all zones):** the S1 spring (Obj 41, `Sonic1SpringObjectInstance`) now uses an inclusive right edge in its `SolidRoutineProfile` (`fullSolid(sticky, inclusiveRightEdge=true, false)`). ROM spring routines call `SolidObject`, whose x-range check `Solid_ChkCollision` (`docs/s1disasm/_incObj/sub SolidObject.asm:160-166`) rejects only when `d0 > 2*halfWidth` (`cmp.w d3,d0; bhi.w Solid_NoCollision`), so the right edge — Sonic's solid body exactly flush against the object's right face — still collides. The engine's contact x-range gate excluded that boundary (`relX >= 2*halfWidth` rejects), so a Sonic falling flush against the right side of a horizontal (LR) spring was treated as out of range, the spring's side contact never fired, and `Spring_LR` never set the push bit to bounce him — Sonic fell to the terrain below instead of launching. S1 SYZ1 f502: the LR spring at `@0218` has its right solid edge at `0218 + 19 = 022B`, exactly Sonic's centre x, so `relX == 2*halfWidth` was rejected. The `inclusiveRightEdge` flag already exists (used by `Sonic1GirderBlock`/`Junction`/`PushBlock`/`InvisibleBarrier`); the spring used the single-arg `fullSolid` which defaults it false. Advances the S1 SYZ1 complete-run frontier **f502 -> f816** (484 -> 351 errors); object-local to Obj 41. Regression-free: every other S1 complete-run is byte-identical, GHZ2/SBZ3 stay green, S2 EHZ1 + S3K AIZ unchanged, and `CollisionSystemTest`/`TestSolidRoutineProfiles`/`TestHtzSpringLoop`/`TestSonic1SpringObjectInstance` (assertion updated for the new inclusive edge) all pass.
- **S1 Electrocuter zap frame-counter source (SBZ):** the SBZ Electrocuter (Obj 6E, `Sonic1ElectrocuterObjectInstance`) now reads ROM `v_framecount` for its `(v_framecount & elec_freq) == 0` zap gate from the trace-seeded `Level_frame_counter` (`LevelManager.frameCounter + 1`) instead of `ObjectManager.getFrameCounter()`. ROM `Elec_Shock` (`docs/s1disasm/_incObj/6E SBZ Electrocuter.asm`) only sets the `col_144x16|col_hurt` ($A4) HURT box on the zap animation's frame 4. The engine's elec timing was correct relative to `v_framecount`, but `ObjectManager`'s own frame counter is free-running and is not seeded from the trace on replay (only `LevelManager`/`Sprites` are), so it ran one ahead of ROM `v_framecount` — making the frame-4 hurt fire one trace-frame early and zapping the rolling player at SBZ1 trace f1925 (ROM zaps him a frame later at f1926). `LevelManager.frameCounter` is the engine's canonical `Level_frame_counter`; objects execute with `frameCounter + 1` (pre-increment), so `getFrameCounter() + 1` is the current frame's `v_framecount`. (The elec's `update(frameCounter, …)` argument is the VBla clock for S1 objects and cannot be used.) Advances the S1 SBZ1 complete-run frontier **f1925 -> f2268** (997 -> 805 errors); object-local to Obj 6E (SBZ-only). Regression-free: SBZ2 (f1395) byte-identical, SBZ3 green, GHZ1/GHZ2/MZ1/SLZ1 and S2 EHZ1 + S3K AIZ unchanged.
- **S1 off-screen self-delete badnik respawn (MZ/SYZ):** an S1 object that owns its `out_of_range` tail (sets `usesCustomOutOfRangeCheck()` with a no-op `isCustomOutOfRange`) and deletes itself off-screen via `setDestroyedByOffscreen()` — e.g. the Caterkiller (Obj 0x78) — now clears its counter-based respawn-table bit so the placement cursor re-spawns it when the player returns, matching ROM. `ObjectManager`'s destroyed-removal path previously only called `placement.clearStayActive(spawn)` and left the spawn's respawn counter latched, so such a badnik never came back: once its head walked off-screen it was gone permanently. The new `resetRespawnStateForOffscreenSelfDelete` helper mirrors ROM `Cat_Despawn` (`docs/s1disasm/_incObj/78 Badnik - Caterkiller.asm:139-148` `bclr #7,2(a2,d0.w)` into the `v_objstate` respawn table) by calling `placement.clearCounterForSpawn` + `markDormant` — exactly what `unloadCounterBasedOutOfRange` already does for the standard path — but only for off-screen self-deletes (`isDestroyedRespawnable()`); player kills keep the bit set and must not respawn. In MZ2 the ROM respawns the Caterkiller (head walking left to x=0x0414) as Sonic returns and rolls into it; ROM `React_BadnikHit` then negates the falling rolling player's y_vel (`neg.w obVelY` -> -0x568) to bounce him up, but the engine had no badnik there (it never respawned after despawning ~f1065) so Sonic fell through to terrain. Verified by BizHawk (`tools/bizhawk/mz2_cat_scan.lua`: ROM Caterkiller slots 0x37-0x3A present and walking at f2578; engine had zero objects near Sonic). Gated on `placement.isCounterBasedRespawn()` so it is S1-only (S2/S3K use the non-counter placement path and are untouched). Advances the S1 MZ2 complete-run frontier **f2578 -> f2819**; all other S1 complete-runs (GHZ1/GHZ3/MZ1/MZ3/LZ1/SBZ1/SYZ1) byte-identical, GHZ2 stays GREEN, and the placement/respawn/Caterkiller/solid-routine unit tests pass.
- **S1 GHZ collapsing-ledge top-landing width (GHZ):** the GHZ collapsing ledge (Obj 1A, `Sonic1CollapsingLedgeObjectInstance`) now opts into `usesCollisionHalfWidthForTopLanding()`, so a player falling onto it lands across the ledge's full `PLATFORM_HALF_WIDTH` (0x30) rather than the generic `SolidObjectFull` `-$B`-narrowed width (0x25). ROM `Ledge_ChkTouch` passes `#96/2` (= 0x30) directly as `SlopeObject`'s `d1` (`docs/s1disasm/_incObj/1A, 53 Collapsing Ledges and Floors.asm:31-33`), and `SlopeObject` runs its X-range check on that `d1` with no narrowing (`docs/s1disasm/_incObj/sub PlatformObject.asm:133-139`); the engine was applying the S2 `Solid_Landed` `obActWid` narrowing that does not exist on this S1 path. Without the override, a player falling onto the ledge near its left edge was rejected as out-of-landing-width for several frames and overshot the landing: s1_ghz1 f2790 (Sonic at relX=2, distY=6 already inside the land band) was rejected until relX=12 at f2793, so the engine dropped Sonic airborne 3 frames longer than ROM (which lands him on the second collapsing ledge at f2790), cascading a player-trajectory divergence forward. Matches the sibling collapsing FLOOR (`Sonic1CollapsingFloorObjectInstance`) which already opts in for the same ROM reason. Advances the S1 GHZ1 complete-run frontier **f2790 -> f3246** (436 -> 255 errors); object-local to Obj1A (GHZ-only spawn). Regression-free: GHZ2 stays GREEN, every other S1 complete-run + credits, S2 EHZ1/CPZ, and S3K AIZ/ICZ/LBZ are byte-identical, and the camera unit tests (TestCamera/TestLookScrollDelay/TestCameraRewindSnapshot/TestSonic3kCnzScroll) pass.
- **S1 Walking Bomb fuse spawn-frame timer (SLZ/SBZ):** the S1 Walking Bomb fuse (Obj5F sub4, `Sonic1BombFuseInstance`) no longer decrements its `bom_time` countdown on the frame it is spawned. ROM `Bom_CheckStartFuse` creates the fuse via `FindNextFreeObj` and sets `bom_time=143`, but the new slot is not run by `ExecuteObjects` on its creation frame, so the fuse holds `143` that frame (BizHawk: SLZ1 bk2 f137203 ends with `bom_time=143`) and only begins counting down the next frame, expiring when `0 -> -1` (`subq.w #1,bom_time; bmi`, `docs/s1disasm/_incObj/5F Badnik - Walking Bomb.asm`). The engine spawned the fuse through the same-frame-exec path, so it decremented `143 -> 142` on its creation frame and expired one frame early, spawning the 4 shrapnel one frame early; the whole ~38-frame shrapnel flight shifted back one frame and hurt the (ducking) player at SLZ1 trace f723 instead of ROM's f724. Marking the fuse `skipsSameFrameUpdateAfterSpawn()` defers its first update so it holds the spawn-frame `bom_time`, matching ROM. Advances the S1 SLZ1 complete-run frontier **f723 -> f933** (661 -> 246 errors); SBZ2 (which also uses the Walking Bomb) improves 1035 -> 1000 errors at the same first-error frame; all other S1 complete-runs, S2 EHZ1, and S3K AIZ are byte-identical, GHZ2/SBZ3 stay green. Object-local to the S1 Walking Bomb fuse.
- **DEZ Death Egg Robot group-animation end-marker frame (DEZ):** the Death Egg Robot (Obj C7) boss group-animation player (`stepGroupAnimation`) now spends the ROM `$C0` end-marker frame before reporting a script complete. ROM `ObjC7_GroupAni` / `loc_3E1AA` reads `anim_frame` at the start of each frame: it plays the last real keyframe's final substep on frame N (advancing `anim_frame` to the `$C0` entry), then on frame N+1 reads `$C0`, runs the end handler (`loc_3E23E` -> `loc_3E27E` -> `loc_3E236` `clr anim_frame` / `moveq #1,d1`) and returns done without applying deltas — so completion costs one extra frame after the last keyframe's substeps finish (e.g. `off_3E3D0` crouch = `0,1,2,$C0` is 41 frames, not 40). The engine's keyframe sequences omit the `$C0` marker and returned done on the same frame the last keyframe finished, so every group-anim-gated attack-phase transition (crouch, walk-punch, stand-up walk) advanced one frame early, drifting the whole DEZ attack clock — by the f4007 jet-stomp the targeting-sensor lock-on snapped 7-8 frames early, so the descent stomped ~7 px right of ROM and the player's roll-up missed the boss-body bounce box. Modeling the end-marker frame realigns the attack clock: the f4007 jet-stomp bounce now connects (player x_vel/y_vel negate to match ROM). Object-local to `Sonic2DeathEggRobotInstance` (boss spawns only in DEZ). Collapses the S2 DEZ1 trace from 127 errors -> 98 and advances its frontier f4007 -> f4933 (a later attack still carries a residual 1-frame sensor-report drift); no other trace is affected.
- **DEZ Silver Sonic does not clear Current_Boss_ID (DEZ):** the DEZ Silver Sonic / Mecha Sonic (Obj AF) no longer clears `Current_Boss_ID` to 0 on defeat. ROM `loc_397BA` sets `Current_Boss_ID=9` when the Silver Sonic fight locks its arena (`s2.asm:77528`) and **no S2 boss ever writes `move.b #0,(Current_Boss_ID).w`** — it stays 9 through the Death Egg Robot fight that follows in the same act, so `Sonic_LevelBound` keeps the boss-strict right player boundary `Camera_Max_X + $128` with no `+$40` lenient extension (`s2.asm:37244-37251`). The engine cleared the boss id on Silver Sonic defeat, reverting the player's right boundary to the lenient `+$40` (0x8A8 vs ROM's 0x868) for the entire Death Egg Robot fight, so the player ran past the DEZ arena's right edge instead of stopping. Removing the premature clear restores the strict boundary; advances the S2 DEZ1 frontier f4933 (`x_speed`, player not clamped at 0x868) -> f5261 (roots 13 -> 5, all remaining a residual boss-bounce sensor-report drift). DEZ-only object; the ending walk (`Camera_Max_X` opens to 0x1000) stays unblocked (target 0xEC0 < 0x1000+$128).
- **DEZ Death Egg Robot jet-stomp reads the targeting sensor one frame late (DEZ):** the Death Egg Robot jet-stomp now observes the targeting sensor's reported X one frame after the sensor produces it, matching ROM object-slot ordering. ROM `ObjC7` `loc_3D784` reads the body's `objoff_28` (the sensor target) at the start of the body's frame, but `ChildObjC7_TargettingSensor` is a separate object in a higher RAM slot, so it runs AFTER the body in `ExecuteObjects` and writes `objoff_28` only on its lock-on-report frame (`loc_3DE62`) — the body sees the report the NEXT frame. The engine updated the sensor child inline inside the body's wait phase and read `targetedPlayerX` the same frame, so the descent began one frame early, draining a frame from every jet-stomp's lock-on wait and re-drifting the DEZ attack clock after the `$C0` end-marker fix. Capturing `targetedPlayerX` before advancing the sensor (then advancing it for next frame) restores the one-frame report latency. Advances the S2 DEZ1 frontier f5261 -> f5952 and collapses errors 98 -> 46 (roots 5 -> 3); object-local to `Sonic2DeathEggRobotInstance` (DEZ-only). A small residual attack-clock drift still misses a later jet-stomp bounce (f5952).
- **Sonic 2 sidekick chaining:** trailing sidekicks now keep following the root leader while a freshly landed direct Sonic sidekick warms its delayed follow-history ring, and S2 Tails fly-in completion no longer carries the approach timer into NORMAL as a false Player 2 manual-control pause.
- **Sidekick CPU control-counter parity:** the CPU sidekick now models ROM `Tails_control_counter` ($F702) strictly as the manual-control timer (set to 600 on Player 2 input and counted down, never incremented per frame), separated from the engine's internal multi-sidekick approach/spawn frame counter. This removes the spurious per-frame counter increment during CPU fly-in, clearing the S2 ARZ1 trace and advancing the MTZ1/HTZ1/MTZ3/CNZ1 frontiers.
- **CPZ Spiny spike timing:** the Spiny (Obj A5) spike projectile now spends one extra init-phase stationary frame so its trajectory aligns with ROM `Obj98_SpinyShotFall` frame-for-frame in the engine's object-execution phase, instead of arriving one frame early and hurting the co-located CPU sidekick a frame before ROM. Advances the S2 CPZ1 frontier substantially.
- **HCZ end-boss rewind recreate construction context:** `HczEndBossBladeSplash` and `HczEndBossBladeWaterChute` `recreateForRewind()` now create their instances through `ObjectConstructionContext.construct(ctx.objectServices(), ...)` (matching `AizEndBossInstance`), so `ObjectServices` is available when their constructors run during rewind restore. Fixes a `TestNoServicesInObjectConstructors` guard violation left by the HCZ end-boss child-codec deletion refactor.
- **S1 vertical-wrap player Y mask (LZ3/SBZ2):** the per-frame `Screen_Y_wrap_value` mask on the player's `y_pos` is now applied only for games that actually have a `Screen_Y_wrap_value` (S3K). S1/S2 have none — their LZ3/SBZ2 vertical wrap masks Sonic's Y only in the same frame the camera crosses the wrap boundary (ROM `ScrollVertical` `SV_BottomBoundary`/`SV_TopBoundary`), which the camera already mirrors. The previous unconditional `y & 0x7FF` wrongly wrapped Sonic at `y=0x800` long before the camera reached the boundary (S1 LZ3 f466 `y` 0x0807→0x0007). Advances the S1 LZ3 frontier ~949 frames with no other trace regressions.
- **S1 LZConveyor platform PlatformObject contact uses pre-move position (LZ):** the LZ Conveyor platform (Obj 63, `Sonic1LZConveyorObjectInstance`) now checks player contact at the platform's pre-move position, matching ROM. ROM `LCon_Platform` (routine 2, `docs/s1disasm/_incObj/63 LZ Conveyor.asm:149-153`) calls `PlatformObject` **before** `LCon_Platform_Update` (which calls `SpeedToPos`), so the contact check always sees the platform's position from before movement. The engine called `applyConveyorMovement()` first, then relied on `ObjectSolidContactController` for contact; without `usesPreUpdatePositionForSolidContact()=true` the contact was checked at the post-move position (1 px below ROM's pre-move y). At S1 LZ3 trace f6517, a group-4 platform at pre-move y=0x2BE (post-move y=0x2BD) correctly landed Sonic at y=0x2A2 in ROM (`2BE - 28 = 2A2`), but the engine reported `no-touch` at y=0x2BD (1 px short), leaving Sonic airborne with y_speed=0x0860. Also adds the full `PlatformObject`+`MvSonicOnPtfm2` solid profile to match `Sonic1PlatformObjectInstance`: `HALF_HEIGHT=9` (`MvSonicOnPtfm2 subi.w #9,d0`; `docs/s1disasm/_incObj/sub MvSonicOnPtfm.asm:18-41`), `getTopLandingSnapAdjustment()=-1` (detect at obY-8; `docs/s1disasm/_incObj/sub PlatformObject.asm:17`), `rejectsZeroDistanceTopSolidLanding()` (UNSIGNED `blo #-16`; `asm:21-22`), `usesCollisionHalfWidthForTopLanding()` (`LCon_Platform` passes `obActWid` directly to `PlatformObject` as d1; `asm:150-152`), and `carriesAirborneRiderAfterExitPlatform()` (`LCon_OnPlatform` calls `MvSonicOnPtfm2` unconditionally; `asm:157-164`). Advances the S1 LZ3 complete-run frontier **f6517 -> f7952** (1726 -> 1778 errors at a different root); GHZ2 and SYZ2 stay GREEN, all other S1 complete-runs byte-identical.
- **S3K lightning-shield attracted-ring give timing (MGZ):** an attracted ring (lightning shield magnetism) now tests its give-ring overlap against its position from before the current frame's `AttractedRing_Move`, matching ROM `Obj_Attracted_Ring` (sonic3k.asm loc_1A88C/loc_1A8C6): the ring moves and adds itself to the collision-response list, which the player's touch pass processes the *next* frame, so the ring is given based on its previous-frame position. The engine had tested the overlap after the move, collecting one frame early. Fixes the S3K MGZ +1-ring divergence (rings collected a frame early), advancing the MGZ complete-run and level-select rings frontier by tens of thousands of frames (MGZ level-select f539 -> f33271, now blocked by a pre-existing trace input-alignment limit) with no other S3K (or S1/S2) trace regressions; inert for S1/S2 which have no lightning shield.
- **S1 collapsing-ledge exact-touch landing (GHZ):** the GHZ collapsing ledge (Obj 1A) now rejects a zero-distance (exact-touch) top-solid landing, matching ROM `Plat_NoXCheck_AltY` (sonic.lst 0x7B00-0x7B0A). The land band uses the unsigned `cmpi.w #-16,d0` / `blo` trick, which excludes `d0=0` — so ROM lands only on strict penetration `d0 in [-16,-1]`, not at the exact touch frame. Verified by BizHawk capture (GHZ1-CR BK2 3361 `d0=0` keeps falling; BK2 3362 `d0=-9` lands); the engine had been landing one frame early. Advances the S1 GHZ1 complete-run frontier f2573->f2790 with no GHZ2/GHZ3/MZ1 trace regressions.
- **S1 platform new-landing detection surface (GHZ/SLZ/SYZ):** the generic platform (Obj 18) new-landing detection now uses ROM `Plat_NoXCheck`'s entry surface `obY-8` (`subq #8`) instead of the riding surface `obY-9`. The detection band in `ObjectSolidContactController` now applies `getTopLandingSnapAdjustment` to new-landing (sticky=false) `distY` so the detect surface matches the snap surface, while continued riding keeps `obY-9` (MvSonicOnPtfm2) — previously the engine snapped to the right position but detected the landing one frame early. With the strict-penetration reject (unsigned `blo #-16`), the platform now lands on ROM's frame. Verified by BizHawk capture (GHZ2-CR BK2 8991 `d0=0` keeps falling; BK2 8992 `d0=-5` lands). Advances S1 GHZ2 complete-run f2369->f2591 with no GHZ3 (or other S1) trace regressions; change is S1-UNIFIED-gated so S2/S3K are unaffected.
- **S1 moving-platform pre-move walk-off bounds + exit-frame carry (SYZ2 green):** the inline platform-ride walk-off bounds check now references the platform's PRE-move x_pos (the stored ride baseline `ridingX`) instead of its post-move position, for objects that opt into `usesPreUpdatePositionForSolidContact()`. ROM `ExitPlatform` (S1 Obj18 routine 4 `Plat_Action2`, `docs/s1disasm/_incObj/18 Platforms.asm:74-87`; `docs/s1disasm/_incObj/sub ExitPlatform.asm:20-27`) runs BEFORE `Plat_Move`, so its `obX(a1)-obX(a0)+width` window observes the platform where it was at the start of the frame; the rider is then carried by the post-move delta via the unconditional `MvSonicOnPtfm2` (`docs/s1disasm/_incObj/sub MvSonicOnPtfm.asm:18-41`). The engine had used the post-move x for the bounds, so a horizontally-oscillating platform sliding left under a rider dropped him one frame early (s1_syz2 f6845: SonicX 0x211C vs platform pre-move 0x20FD gives relX 0x3F < 0x40 = stays; post-move 0x20FB gives relX 0x41 = spurious exit), skipping the -2px carry and sending Sonic airborne a frame ahead of ROM. A second fix applies the final `MvSonicOnPtfm2` carry on the out-of-bounds exit frame for objects with `carriesAirborneRiderAfterExitPlatform()` (S1 Obj18/Obj52/Obj59), since ROM's `MvSonicOnPtfm2` does not test the on-object bit and still pulls the just-exited rider by the platform delta (s1_syz2 f6846: ROM x 0x211F vs un-carried 0x2120). **Greens the S1 SYZ2 complete-run trace** (`TestS1Syz2CompleteRunTraceReplay`, 55 errors -> 0). Verified regression-free: GHZ2 stays green, every other S1 complete-run (GHZ1/GHZ3/LZ1-3/MZ1-3/SBZ1-2/SLZ1-3/SYZ1/SYZ3) and the affected S3K objects' traces (CNZ-CR f1846, MGZ-CR f866) are byte-identical clean-vs-fixed. Both fixes are gated on existing provider predicates so unrelated solids are unchanged; shared S1/S2/S3K solid-contact code, no zone/game branch.
- **Headroom ceiling-probe double-flip (SYZ2 jump-under-overhang):** the player jump headroom check (`CalcRoomOverHead` quadrant 0x80, `CollisionSystem.describeCalcRoomOverHeadProbes`) no longer pre-applies the `eori.w #$F` ceiling Y-flip as a probe Y offset. ROM `Sonic_FindCeiling` (`docs/s1disasm/_incObj/sub FindNearestTile & FindFloor & FindWall.asm:361-403`) flips the top-edge probe Y once and then `FindFloor` returns a single distance `15 - (metric + (probeY & $F))` for both floor and ceiling (the `eor.w d6($1000)`/`neg.w` negates the negative ceiling height into the floor form). The engine's `Direction.UP` `scanVertical`/`calculateVerticalDistance` path already models that flip, so the probe additionally passing the flip as a `dy` offset double-applied it and computed the wrong ceiling distance. BizHawk capture (S1 SYZ2 bk2 f1088, `Sonic_FindCeiling` hook at 0x156CE/0x156D2): ROM obX=0x074F, obWidth=9, left probe column 6, **leftDist=8** for ceiling tile 0x0093 (col-6 height -2) — but the engine computed 3, so its headroom (3) fell below the 6px `Sonic_Jump` gate and blocked a jump ROM performs (headroom 8). Dropping the pre-flip makes the engine's UP path return 8, matching ROM. Advances the S1 SYZ2 complete-run frontier **f1088 -> f6845** (311 -> 55 errors), as a side effect reduces GHZ3's error count (492 -> 477, first error frame unchanged at f2693), and leaves GHZ1/LZ1/SLZ1/SLZ2/SLZ3 and S2 EHZ1/ARZ/CPZ + S3K AIZ/ICZ/LBZ + GHZ2 (stays green) byte-identical; MZ1's first-error frame is unchanged at f2089 (its total count shifts 185 -> 205 entirely within its pre-existing f4230+ physics cascade, a region already fully divergent). Shared S1/S2/S3K ceiling-probe code; game-agnostic, no zone/game branch.
- **Camera bottom-boundary follow on a non-scrolling frame (GHZ2 green):** the vertical camera now re-clamps to the bottom level boundary whenever that boundary is actively moving (`maxYChanging`), not only on frames where the grounded/airborne vertical scroll already moved the camera. ROM `ScrollVertical`'s `SV_OnGround` path consults `f_bgscrollvert` (`docs/s1disasm/_inc/ScrollHoriz & ScrollVertical.asm:148-149,157-158`): when the bottom boundary is moving this frame it branches to `SV_BottomBoundaryMoving` (line 210), which forces `d0=0` and falls through `SV_SweetSpot` -> `SV_BottomBoundary` (line 259), clamping the camera to the freshly-moved `v_limitbtm2` *even when Sonic is exactly at the sweet spot and the normal scroll produced no movement*. `DynamicLevelEvents` (`docs/s1disasm/_inc/DynamicLevelEvents.asm:5-49`) steps `v_limitbtm2` toward `v_limitbtm1` and sets `f_bgscrollvert=1` *before* `ScrollVertical` runs, so the camera follows the moving boundary on the same frame. The engine mirrors `f_bgscrollvert` with `maxYChanging` (set by `updateBoundaryEasing`, called before `updatePosition`), but its boundary clamp was gated only on `y != yBeforeVerticalScroll`, so a sweet-spot frame whose scroll produced no movement skipped the clamp and the camera lagged the rising bottom boundary by one frame. Verified by BizHawk capture (GHZ2-CR: bottom boundary eases `0x0400 -> 0x0300` under a grounded fast roll-land; ROM camera reaches `0x034A` at f3349 while the engine held `0x034C`). This is shared S1/S2/S3K `ScrollVertical` behaviour (game-agnostic, no zone/game branch). **Greens the S1 GHZ2 complete-run trace** (`TestS1Ghz2CompleteRunTraceReplay`, 2 errors -> 0) with no S1/S2/S3K trace regressions (GHZ1/GHZ3/MZ1/LZ1/SLZ1/SLZ3, S2 EHZ1/ARZ/CPZ, S3K AIZ/ICZ/LBZ complete-runs and the S3K must-keep-green suite all byte-identical or still green; camera unit tests pass).
- **S1 platform jump-off post-move re-seat (GHZ/SLZ/SYZ):** the generic platform (Obj 18) now carries an airborne rider after `ExitPlatform` on the jump-off frame, matching ROM `Plat_Action2` (routine 4, `18 Platforms.asm:74-87`) which runs `ExitPlatform` -> `Plat_Move`/`Plat_Nudge` -> unconditionally `MvSonicOnPtfm2`. `MvSonicOnPtfm2` (`sub MvSonicOnPtfm.asm:18`) does not check the rider's velocity, so on the launch frame it still pulls the player's `y_pos` to `platformY-9-obHeight` using the platform's post-move position, overwriting the `Sonic_Jump` rolling-radius adjust (`sonic.asm:1228` `addq.w #sonic_height-sonic_roll_height,obY(a0)`). `Sonic1PlatformObjectInstance` now opts into `carriesAirborneRiderAfterExitPlatform()` (matching Obj52 `Sonic1MovingBlockObjectInstance` / Obj59 `Sonic1ElevatorObjectInstance`) and resolves a single post-move solid checkpoint (mirroring the moving block) so the airborne-rider carry re-seats to the platform's new `y` rather than its pre-move `y`. Verified by BizHawk capture (GHZ2-CR: platform `obY` 0x026E->0x0270, height 0x13->0x0E, player `y_pos` 0x0252 -> 0x0257 (+5 jump adjust) -> 0x0259 (`MvSonicOnPtfm2`)); the engine had stopped at the +5 adjust, leaving the player 2 px high. Advances the S1 GHZ2 complete-run frontier f2591->f3349 (next divergence is the unrelated shared-camera vertical-settle `camera_y` cluster) and, as side effects of the same Obj18 carry, advances GHZ3 (first error f1246 `y` -> f2693 `x`) and reduces GHZ1's error count (537 -> 436, first error frame unchanged at f2790) with no other S1 trace regressions (SLZ1/SLZ3/MZ1/LZ1 byte-identical). GHZ2 is **not** green yet: two residual 2 px `camera_y` divergences (f3349/f3366) are a separate pre-existing shared-`Camera.ScrollVertical` vertical-settle-timing bug (a fast roll-land convergence rounding) that also dominates GHZ1's remaining errors.
- **S1 right-wall odd-angle snap (LZ3/CNZ):** the player ground-angle selection (`CollisionSystem.selectSensorWithAngle`) no longer keeps a non-ROM cross-frame "pending odd-sensor fallback" angle. ROM `Sonic_Angle` (docs/s1disasm/_incObj/Sonic AnglePos.asm:186-208) snaps an odd selected sensor angle straight to `(angle+0x20)&0xC0` from the *current* angle, with no cross-frame alternate-angle cache; the engine had resurrected a stale `0xD0` when the RIGHTWALL ground sensor returned zero distance + flagged angle `0xFF`, momentarily reverting the player angle one frame before the cardinal snap caught up. Removing the `pendingOddSensorFallbackAngles` cache and restoring the plain `applyAngleFromSensor` snap advances the S1 LZ3 complete-run frontier f1415 (`angle` 0x00C0 vs 0x00D0) -> f6517 (next divergence is an unrelated LZ-conveyor landing) with no S1 trace regressions; this is the same shared S1/S3K right-wall path that clears the S3K CNZ `angle` divergence (duplicate of the off-develop sibling commit `1f4fb901f`).
- **S1 collapsing-floor landing surface (MZ/SLZ/SBZ):** the collapsing floor (Obj 0x53, `Sonic1CollapsingFloorObjectInstance`) now models ROM `CFlo_ChkTouch`'s `PlatformObject` entry surface `obY-8` for new landings while keeping `CFlo_WalkOff`'s `MvSonicOnPtfm2` riding surface `obY-9` (docs/s1disasm/_incObj/1A, 53 Collapsing Ledges and Floors.asm:176-216). It opts into `getTopLandingSnapAdjustment() = -1` (recovering the `obY-8` detect/snap surface from the `obY-9` solid params), `rejectsZeroDistanceTopSolidLanding()` (ROM `Plat_NoXCheck_AltY` gates the band with the unsigned `cmpi.w #-16,d0` / `blo`, excluding the exact-touch `d0=0`), and `usesCollisionHalfWidthForTopLanding()` (`CFlo_ChkTouch` passes `#64/2` directly as PlatformObject's `d1`, so no generic `+$B` narrowing). Previously the engine modeled both surfaces at `obY-9`, so it detected the landing one frame late: ROM lands rolling on the floor then re-seats to the standing `obY-9` surface the next frame, but the engine's late landing left the player 1 px high for that frame. Verified by BizHawk capture (MZ3 BK2 45145 ROM lands at `obY=0x0491` rolling, BK2 45146 `MvSonicOnPtfm2` re-seats to `0x048C` standing; the engine landed a frame later). Advances the S1 MZ3 complete-run frontier f1702 (`y` 0x048C vs 0x048B) -> f2079 (next divergence is an unrelated jump-release `y_speed` cluster) and, as a side effect of the same landing-surface fix, advances S1 SLZ2 f651 (`g_speed`) -> f1016 with no MZ1/MZ2/SLZ3/SBZ1 (or other S1) trace regressions; the object only spawns in MZ/SLZ/SBZ.
- **CPZ spin-tube re-capture timing:** when a CPZ spin tube (Obj 1E) checks entry collision against a player already mid-traversal of another tube (`obj_control=$81`), it now reads the player's frame-start (pre-physics) centre instead of the position the owning tube already advanced this frame. This mirrors ROM `Obj1E_Main` slot ordering (s2.asm:48447-48457): the lower-slot capturing tube runs before the owning tube and so never re-captures a player on the same frame the owner steps it. Fixes the CPU sidekick double-stepping a tube frame (S2 CPZ2 f2888 `tails_x` -16 vs ROM -8), advancing the frontier with no other trace regressions.
- **S2 Rexon head oscillation stagger (HTZ):** each Rexon head (Obj97) now seeds its oscillation frame counter (ROM `objoff_39`) with its head number instead of 0, matching `Obj97_Init` (s2.asm:74316-74318). The phase/oscillation update only runs when `(objoff_39 + 1) & 3 == 0` (Obj97_Normal, s2.asm:74407-74414), so each head must advance its part of the snake wave on a different frame of the 4-frame cycle. Starting all heads at 0 collapsed that stagger and left the heads a couple of pixels off the ROM, which nudged the attackable tip head (collision_flags `0x0B`) just outside Sonic's 16x16 touch band and made the engine miss the rolling kill bounce the ROM lands (`Touch_KillEnemy` `neg.w y_vel`, s2.asm:85385). Advances the S2 HTZ2 frontier f1078 -> f1343 (`y_speed` now flips +0568 -> -0568) with HTZ1 held at its f6114 baseline and no other S2 trace regressions; the Rexon is HTZ-only.
- **CPU sidekick push-bypass auto-jump re-trigger (HTZ2):** the CPU sidekick auto-jump trigger gate now fires on its `$3F` cadence frame while pushing even when the `Tails_CPU_jumping` latch is still held, matching ROM. ROM reaches the trigger gate (`loc_13E9C` / `TailsCPU_Normal_FilterAction_Part2`) along two routes: the normal route runs the latch carry/clear (`loc_13E64` / `FilterAction`) first and is gated on the latch being absent, but the push-bypass route (Tails pushing AND delayed-Sonic not pushing) branches **directly** to the trigger gate, skipping the latch carry/clear entirely (S3K `loc_13DD0` `btst #Status_Push;beq / btst #5,d4;beq.w loc_13E9C`, sonic3k.asm:26702-26705; S2 `btst #pushing,status;beq + / btst #pushing,d4;beq.w TailsCPU_Normal_FilterAction_Part2`, s2.asm:39297-39300). The trigger gate itself never consults the latch, so on the push-bypass route it must press jump on the cadence frame regardless of latch state. The engine previously guarded the gate on `!jumpingFlag`, so a grounded sidekick pushing a solid object (e.g. the HTZ breakable block) whose latch stuck set from an earlier `$3F` jump never re-jumped, while ROM did (S2 HTZ2 f1343: dy=-10 so only the push-bypass passes the height gate). Advances the S2 HTZ2 frontier f1343 -> f3315 (next divergence is an unrelated rising-lava-platform ride) with no other S2, S3K (AIZ + complete-run byte-identical), or S1 trace regressions; the gate is shared S2/S3K CPU sidekick code driven by real push state, never a zone carve-out.
- **S3K LBZ rolling-drum chain handoff (LBZ):** entering a `LbzRollingDrum` while already riding a different drum now clears the previous drum's per-player standing flag before installing the new ride, mirroring ROM `RideObject_SetRide` (sonic3k.asm:42027) which does `bclr d6,status(a3)` on the player's prior interact object. Without this, the just-vacated drum still ran its release path the same frame, leaving its old horizontal window and forcing the player airborne — clobbering the new drum's seamless ride. Fixes the LBZ rolling-drum-chain handoff so the player stays grounded across drum-to-drum transfers, advancing the S3K LBZ complete-run frontier f1694->f1950 (`air`/`status_byte`; next divergence is an unrelated wall-push `Status_Pushing` while riding) with no AIZ (or other S3K/S1/S2) trace regressions. LBZ-only object, so no cross-zone exposure.
- **S3K MGZ/LBZ Smashing Pillar flush-edge push (LBZ/MGZ):** the MGZ/LBZ Smashing Pillar (`Obj_MGZLBZSmashingPillar`) now opts into the ROM-accurate inclusive right edge for its `SolidObjectFull` body (`MGZLBZSmashingPillarObjectInstance.usesInclusiveRightEdge()`). ROM `SolidObjectFull_1P` -> `SolidObject_cont` rejects the X bounding box with `bhi` (unsigned strictly-greater): `cmp.w d3,d0 / bhi.w loc_1E0A2` (sonic3k.asm:41405), so a grounded player shoved flush against the pillar (`d0 == width*2`) stays a live SIDE contact and the pillar re-sets `Status_Push` (loc_1E06E `bset #Status_Push`, sonic3k.asm:41500) every frame the player holds against it. The engine's default exclusive (`>=`) X gate dropped the contact at the flush edge, so the pillar never re-set push and the player's standing-still push-clear cleared it a frame later. Advances the S3K LBZ complete-run frontier f1950 -> f2270 (`status_byte`; next divergence is an unrelated downstream sidekick `tails_x` delta) with the MGZ frontiers held exactly at their develop baselines (complete-run f866, level-select f523) and no other S3K (or S1/S2) trace regressions; the object only spawns in MGZ/LBZ.
- **S3K ICZ path-follow platform balance width (ICZ):** the ICZ path-follow platform (Obj `0x89F38`) now reports its ROM `width_pixels` of `$20` for the player on-object balance check, instead of the shared 16 px default. ROM `Sonic_Move` reads `width_pixels(a1)` (sonic3k.asm:22455) for the `d1 = player_x + width - object_x` balance window, which differs from the platform's `$2B` `SolidObjectFull` X-collision half-width. The 16 px default shifted the balance window inward by 16 px, placing a still, RIGHT-facing rider on the platform's left edge and spuriously flipping its facing to LEFT (`status` bit0). Advances the S3K ICZ complete-run frontier f3116->f3139 (the next divergence is the unrelated sinking-platform `Status_Push` interaction) with no other S3K (or S1/S2) trace regressions. ROM `Sonic_Move` reads `width_pixels(a1)` (sonic3k.asm:22455) for the `d1 = player_x + width - object_x` balance window, which differs from the platform's `$2B` `SolidObjectFull` X-collision half-width. The 16 px default shifted the balance window inward by 16 px, placing a still, RIGHT-facing rider on the platform's left edge and spuriously flipping its facing to LEFT (`status` bit0). Advances the S3K ICZ complete-run frontier f3116->f3139 (the next divergence is the unrelated sinking-platform `Status_Push` interaction) with no other S3K (or S1/S2) trace regressions.
- **Playable sprite slope rendering:** slow steep-slope turnarounds now refresh the displayed slope walk frame immediately when facing changes, preventing a one-frame mismatch between vertical flip flags and the previous slope frame set.
- **S3K playable route coverage:** expanded AIZ, HCZ, CNZ, MGZ, ICZ, MHZ, and LBZ coverage with route objects, badniks, bosses/minibosses, events, camera locks, scroll/parallax, animated tiles, palette cycling, PLC state, seamless transitions, and visual/rendering fixes. AIZ through HCZ remains the primary release slice, with later zones prioritized by route blockers and trace frontiers.
- **Trace replay parity and diagnostics:** added and refined complete-run and level-select trace suites across S1, S2, and S3K; moved many first-divergence frontiers forward by modelling ROM object state, sidekick CPU state, object load/unload cadence, solid-contact behavior, pause/title-card setup, RNG/bootstrap state, and trace-entry capabilities. Trace reports now default to frontier-focused, divergent-column diagnostics with detailed evidence kept in [docs/TRACE_FRONTIER_LOG.md](docs/TRACE_FRONTIER_LOG.md).
- **Gameplay rewind:** added gameplay-scoped rewind infrastructure and expanded object restore coverage across bosses, hazards, traversal objects, cutscenes, bonus-stage objects, particles, and end-of-act flows. Rewind restore now has stronger identity handling, construction-child adoption, generic recreate hooks, coverage analysis, and round-trip guards for captured objects.
- **Lost-ring rewind generic restore:** spilled lost-ring dynamic objects now restore through `RewindRecreatable` generic recreate while preserving their shared spill-animation owner, deleting the dedicated shared lost-ring dynamic codec.
- **Shield rewind generic restore:** basic shield dynamics now restore through the generic player-bound `RewindRecreatable` path, deleting the final shared dynamic codec helper.
- **S2 CPZ rewind graph restore:** CPZ boss child graphs now restore through generic recreate with graph-level tests for identity, counts, and parent/sibling references.
- **S2 DEZ bomb rewind graph restore:** Death Egg Robot bombs now restore through graph-tested generic recreate with nearest live boss relinks while deleting the explicit bomb dynamic codec.
- **S2 ARZ arrow rewind graph restore:** ARZ boss arrows and eyes now restore through graph-tested generic recreate while deleting the explicit arrow dynamic codec.
- **S2 WFZ rewind graph restore:** WFZ boss laser walls, floating platforms, and platform hurt children now restore through graph-tested generic recreate while deleting their explicit dynamic codecs.
- **S2 HTZ rewind graph restore:** HTZ boss flamethrower and lava-ball hazards now restore through graph-tested generic recreate while deleting their explicit dynamic codecs.
- **S1/S2 seesaw ball rewind graph restore:** Seesaw ball children now restore through graph-tested parent relinks while deleting their explicit dynamic codecs.
- **S2 badnik child rewind graph restore:** Grounder, Balkiry, Rexon, Shellcracker, Slicer, and Sol dynamic child graphs now restore through generic recreate with parent/sibling relinks while deleting their explicit dynamic codecs.
- **Checkpoint/starpost rewind graph restore:** Sonic 1 lamppost twirls, Sonic 2 checkpoint dongles/stars, and S3K starpost star children now restore through generic recreate with live parent relinks while deleting their explicit dynamic codecs.
- **S1 ring flash rewind graph restore:** Giant-ring flash effects now restore through graph-tested generic recreate, preserving null-parent behavior while deleting the explicit dynamic codec.
- **S1 MZ glass reflection rewind graph restore:** MZ glass-block reflection shines now restore through generic recreate with live parent relinks while deleting their explicit dynamic codec.
- **S1 FZ boss rewind graph restore:** Final Zone cylinders, plasma launcher, and plasma balls now restore through graph-tested generic recreate with boss/launcher relinks while deleting their explicit dynamic codecs.
- **S1 GHZ boss rewind graph restore:** GHZ boss wrecking balls now restore through graph-tested generic recreate with live boss relinks while deleting their explicit dynamic codec.
- **S1 SLZ boss spikeball rewind graph restore:** SLZ boss spikeballs and fragments now restore through graph-tested generic recreate while deleting their explicit dynamic codec.
- **S1 ending Sonic rewind graph restore:** ending Sonic now restores through graph-tested generic recreate with captured emerald-family references while deleting its explicit dynamic codec.
- **S1 Grass Fire rewind graph restore:** MZ grass fire walkers and stationary child flames now restore through captured platform/fire graph references while deleting the final S1 dynamic codec.
- **S1 SYZ boss block rewind graph restore:** SYZ boss blocks now restore through graph-tested generic recreate with live boss relinks while deleting their explicit dynamic codec.
- **S1 badnik child rewind graph restore:** Bomb fuses, Caterkiller body segments, and Orbinaut spike children now restore through graph-tested generic recreate with live parent relinks while deleting their explicit dynamic codecs.
- **S2 detached badnik child rewind restore:** Slicer pincers and Sol fireballs now survive rewind restore after their parent unloads, preserving detached falling/flying behavior through generic recreate.
- **S3K AIZ miniboss rewind graph restore:** AIZ miniboss body, arm, napalm, flame barrel, flame, barrel-shot, and flare children now restore through graph/session-first generic recreate, preserving multi-barrel parent/sibling links while deleting their live-reference dynamic codecs.
- **S3K AIZ end-boss rewind graph restore:** AIZ end-boss machine, ship, arm, propeller, flame, bomb, and smoke objects now restore through graph/session-first generic recreate while deleting their live-reference dynamic codecs.
- **S3K AIZ ship-bomb rewind graph restore:** AIZ2 battleship bombs now restore through graph-tested generic recreate while deleting their live-reference dynamic codec.
- **S3K AIZ spiked-log rewind graph restore:** spiked-log spike collision children now restore through graph-tested generic recreate with exact parent identity while deleting their dynamic codec.
- **S3K AIZ falling-log rewind graph restore:** falling-log log/splash pairs now restore through graph-tested generic recreate with exact bidirectional relinks while deleting the log dynamic codec.
- **S3K HCZ end-boss rewind graph restore:** HCZ end-boss ship, turbine, blade, splash, water chute, and water column children now restore through graph/session-first generic recreate while deleting their live-reference dynamic codecs.
- **S3K AIZ intro rewind graph restore:** AIZ intro biplane and wave children now restore through graph-tested generic recreate while deleting their live-reference dynamic codecs.
- **S3K badnik child rewind graph restore:** Dragonfly linked bodies, Spiker top spikes, and Turbo Spiker shells now restore through graph-tested generic recreate while deleting their dynamic codecs.
- **S3K signpost rewind graph restore:** signpost stub children now restore through graph-tested generic recreate while deleting their dynamic codec.
- **S3K LBZ1 cutscene rewind graph restore:** Knuckles range and collapse helpers now restore through graph-tested generic recreate while deleting their dynamic codecs.
- **S3K MHZ cutscene rewind graph restore:** MHZ1 door/P2 stopper and MHZ2 route-switch helpers now restore through graph-tested generic recreate with exact parent/owner relinks while deleting their dynamic codecs.
- **S3K MHZ miniboss flame rewind graph restore:** MHZ miniboss flame children now restore through graph-tested generic recreate with exact parent relinks while deleting their dynamic codec.
- **S3K MHZ miniboss escape-shard rewind graph restore:** MHZ miniboss escape shards now restore through graph-tested generic recreate with exact/compact parent relinks while deleting their dynamic codec.
- **S3K SS-entry flash rewind graph restore:** special-stage entry flash effects now restore through graph-tested generic recreate with exact parent-ring relinks while deleting their dynamic codec.
- **S3K nested hurtbox rewind graph restore:** MGZ miniboss drill arms and ICZ ice-spike hurt children now restore through graph-tested generic recreate with exact parent relinks while deleting their dynamic codecs.
- **S3K cutscene Knuckles rewind graph restore:** AIZ rock children and CNZ2 blocking walls now restore through graph-tested generic recreate with exact parent/owner relinks while deleting their dynamic codecs.
- **S3K invisible block rewind restore:** invisible blocks and invisible hurt blocks now restore through spawn-based generic recreate, clearing their rewind coverage gaps.
- **S1 invisible barrier rewind restore:** invisible barriers now restore through spawn-based generic recreate, clearing their rewind coverage gaps.
- **S1 badnik rewind restore:** Ball Hog, Batbrain, Burrobot, and Crabmeat now restore through spawn-based generic recreate, moving them onto the rewind round-trip ratchet.
- **S1 badnik rewind restore:** Bomb, Buzz Bomber, Motobug, Roller, and Yadrin now restore through spawn-based generic recreate, moving them onto the rewind round-trip ratchet.
- **S1 badnik rewind restore:** Chopper, Jaws, and Newtron now restore through spawn-based generic recreate, moving them onto the rewind round-trip ratchet.
- **S1 static object rewind restore:** Flapping Door, Pylon, Purple Rock, Spinning Light, Waterfall, and Waterfall Sound now restore through spawn-based generic recreate coverage.
- **S1 simple object rewind restore:** Harpoon, Hidden Bonus, MZ Brick, Pole That Breaks, and Scenery now restore through spawn-based generic recreate coverage.
- **S1 hazard/platform rewind restore:** Big Spiked Ball, Electrocuter, Saw, Small Door, and Vanishing Platform now restore through spawn-based generic recreate coverage.
- **S1 hazard/conveyor rewind restore:** Conveyor Belt, Lava Ball, Lava Tag, Spikes, and Spiked Pole Helix now restore through shared spawn-based generic recreate coverage.
- **S1 utility/hazard rewind restore:** Fan, Giant Ring, Girder Block, Lamppost, and Spring now restore through shared spawn-based generic recreate coverage.
- **S1 platform/hazard rewind restore:** Button, Elevator, Flamethrower, Labyrinth Block, and Moving Block now restore through shared spawn-based generic recreate coverage.
- **S1 platform-family rewind restore:** Platform, Spin Platform, Staircase, and Swinging Platform now restore through shared spawn-based generic recreate coverage.
- **S1 edge/monitor/conveyor rewind restore:** Edge Walls, Monitor, LZ Conveyor, and Spin Conveyor now restore through shared spawn-based generic recreate coverage.
- **S1 bridge/signpost trap rewind restore:** Bridge, Gargoyle, Lava Ball Maker, and Signpost now restore through shared spawn-based generic recreate coverage.
- **S1 destructible/spawner rewind restore:** Breakable Wall, Bubbles, Collapsing Ledge, and Smash Block parents now restore through shared spawn-based generic recreate coverage.
- **S1 stomper/push/ending rewind restore:** Chained Stomper, Push Block, and ending STH text now restore through generic recreate coverage.
- **Shared helper rewind restore:** animal, explosion, and skid-dust dynamic objects now restore through generic recreate with restore-time services, deleting their shared private dynamic codec helpers.
- **Shared signpost sparkle rewind restore:** signpost sparkle dynamics now restore through generic recreate with compact scalar position restore, deleting the shared exact-spawn dynamic codec helper path.
- **S2 invisible block rewind restore:** invisible blocks now restore through spawn-based generic recreate, moving them onto the rewind round-trip ratchet.
- **S2 Egg Prison button rewind graph restore:** Egg-prison buttons now restore through graph-tested generic recreate with exact parent/button relinks while deleting their dynamic codec.
- **S2 OOZ burner flame rewind graph restore:** burner flame children now restore through graph-tested generic recreate with exact platform relinks while deleting their dynamic codec.
- **S3K static hazard rewind restore:** still sprites and S3K spike objects now restore through generic recreate with object-manager coverage for captured spawns and spike movement state.
- **Gameplay rewind performance:** reduced multi-sidekick keyframe capture cost by omitting unused terminal sidekick follow-history arrays and empty sidekick touch-overlap snapshots, with a deterministic 20-sidekick rewind/replay performance trace.
- **Runtime-owned framework stack:** continued moving zone behavior onto shared runtime-owned systems for typed zone state, palette ownership, animated tile channels, live layout mutation, scroll composition, staged render effects, and frame-level render-mode overrides. Older zone-local paths remain where migration has not yet paid for itself.
- **Release hardening and architecture guards:** tightened branch/release policy hooks, trace/rewind invariants, object-service boundaries, ROM-only runtime asset rules, singleton lifecycle checks, architecture source guards, and assertion-quality tests. Test-suite cleanup replaced diagnostic-only or tautological tests with behavioral oracles where they protect real release risk.
- **Player-facing systems:** added or improved S3K data select/save support, cross-game donation paths, ROM-derived master-title previews, legal-disclaimer startup flow, display shader support, pause/HUD presentation fixes, multi-sidekick behavior, level-editor plumbing, and user-facing configuration/docs.
- **S1/S2 parity uplift:** closed selected Sonic 1 and Sonic 2 trace/object/boss gaps where fixes reduced active release risk or shared-code duplication, including sidekick CPU behavior, object-slot lifetime, solid/contact parity, boss/end-sequence behavior, and level-specific traversal objects.
- **S2 SCZ/WFZ visual parity:** restored Sky Chase Tornado rider art, moved stage rings into their ROM sprite-priority bucket, applied Wing Fortress object priority bits, and corrected WFZ underside flame flicker cadence.
- **S2 CPZ spin tube control:** CPZ spin tubes now use player-local `obj_control` without asserting global `Control_Locked`, keeping logical input refresh aligned with the ROM while the tube owns movement.
- **S2 Crawl contact parity:** walking into an attacking Crawl now routes through the ROM hurt collision flags instead of silently ignoring non-rolling contact.
- **S2 Casino Night slot machine:** restored the ROM packed-target reel order so the stopped slot faces line up with the reward paid out by linked Point Pokey cages.

## v0.5.20260411 (Released 2026-04-11)

Analysis range: `v0.4.20260304..v0.5.20260411` on `develop` (`2479` commits, `2298` non-merge commits,
`1588` files changed, `477351` insertions, `28266` deletions). Net code growth is ~449,100 lines.

A primarily architectural release. The engine internals have been restructured to prepare for level
editor support, safe runtime teardown, and future multi-instance play-testing. Sonic 3 & Knuckles
gameplay coverage has advanced significantly across Angel Island and Hydrocity: the AIZ2 Flying
Battery bombing sequence, AIZ2 end boss, post-boss capsule/cutscene flow, AIZ-to-HCZ transition,
HCZ1 miniboss, and HCZ1-to-HCZ2 transition are now represented, alongside all three S3K bonus-stage
families in active implementation.

### Architectural Overhaul: Two-Tier Service Architecture

The engine's object model has been fundamentally restructured from direct singleton access to a
two-tier dependency injection pattern.

- **GameServices** (global tier): facade over ROM, graphics, audio, camera, level, fade, and
  configuration singletons. Accessed anywhere via `GameServices.camera()`, `GameServices.audio()`, etc.
- **ObjectServices** (context tier): injected into every game object at spawn time via
  `ObjectManager`. Provides camera, game state, zone features, sidekick access, and level queries
  scoped to the object's lifecycle. Accessed via `services()` within any `AbstractObjectInstance`.
- **ThreadLocal construction context**: `ObjectServices` is available during object construction
  without requiring constructor parameters, via a `ThreadLocal` injection pattern.
- **Migration scope**: 105 Sonic 2 object files, 50 Sonic 1 object files, 25 Sonic 3K object files,
  and 6 game-agnostic base classes migrated from `getInstance()` / `LevelManager.getInstance()` to
  `services()` or `GameServices` as appropriate. All singleton `.getInstance()` calls removed from
  object classes.
- **NoOp sentinels**: null-returning provider methods replaced with NoOp sentinel objects across
  the provider interfaces (zone features, physics, water, scroll handlers), eliminating null checks
  throughout the object layer.
- **GameId enum**: replaced string-based game identification with type-safe `GameId` enum throughout
  `CrossGameFeatureProvider` and module detection.

### Architectural Overhaul: GameRuntime and Singleton Lifecycle

- **GameRuntime**: introduced `GameRuntime` as the explicit owner of all mutable gameplay state.
  `ObjectServices` is backed by the runtime instance rather than global singletons.
- **resetState() lifecycle**: all singletons (`Camera`, `RomManager`, `GraphicsManager`,
  `AudioManager`, `CollisionSystem`, `CrossGameFeatureProvider`, `DebugOverlayManager`,
  `SonicConfigurationService`, `Sonic1ConveyorState`, `Sonic1SwitchManager`,
  `TerrainCollisionManager`) now implement `resetState()` for clean teardown without destroying
  the singleton instance. `resetInstance()` deprecated across the board.
- **Generation counter**: `GameContext` tracks a generation counter for stale reference detection.
- **SingletonResetExtension**: JUnit 5 extension with `@FullReset` annotation for automated
  per-test singleton reset, replacing manual `resetInstance()` boilerplate across 35+ test classes.
- **GameRuntime lifecycle wired into test harness**: `resetPerTest()` now creates/destroys
  `GameRuntime` for CI stability.

### Architectural Overhaul: LevelManager Decomposition

`LevelManager` (previously the largest class in the engine) has been broken into focused components:

- **LevelTilemapManager**: extracted ~18 methods and ~22 fields for tilemap rendering, chunk
  lookup, and tile-level queries.
- **LevelTransitionCoordinator**: extracted ~43 methods and ~25 fields for act transitions,
  seamless level mutation, title cards, results screens, and game mode flow.
- **LevelDebugRenderer**: extracted ~12 methods and ~14 fields for collision overlay, sensor
  display, camera bounds, and other debug visualizations.
- **LevelGeometry** and **LevelDebugContext** records introduced as data carriers between the
  decomposed components.
- Game-specific art dispatching extracted from `LevelManager` into per-game modules.

### Architectural Overhaul: Cross-Game Abstraction Hardening

Systematic removal of game-specific coupling from the engine core:

- **PlayableEntity interface**: extracted from `AbstractPlayableSprite` to decouple `level.objects`
  from `sprites.playable`. Includes `isOnObject()`, `getAnimationId()`, and all methods needed
  by game objects to interact with the player.
- **PowerUpSpawner interface**: breaks `sprites.playable` dependency on `level.objects` for
  monitor/power-up spawning.
- **DamageCause**, **GroundMode**, **ShieldType** relocated from `sprites.playable` to `game`
  package for cross-game reuse.
- **SecondaryAbility enum**: replaced `instanceof Tails` checks throughout the codebase.
- **CanonicalAnimation enum** and **AnimationTranslator**: cross-game animation vocabulary
  enabling bidirectional sprite donation between any pair of games.
- **DonorCapabilities interface**: each `GameModule` declares its donation capabilities (S1, S2,
  S3K all implemented), replacing hardcoded branches in `CrossGameFeatureProvider`.
- S1 wired as donor for forward donation into S2/S3K games.
- CNZ slot machine renderer moved to `ZoneFeatureProvider`; seamless mutation moved to
  `GameModule`; Tails tail art loading moved to `GameModule`; sidekick zone suppression moved
  from hardcoded S2 IDs to `GameModule`.
- 11 cross-game classes relocated from `sonic2` to generic packages; 5 cross-game dependency
  classes decoupled.

### Common Code Extraction (Phase 1-5)

A systematic 5-phase refactoring pass eliminated structural duplication across all three games:

- **Phase 1 — Common utilities**: `SubpixelMotion` (16.16 fixed-point gravity helpers),
  `AnimationTimer` (cyclic frame animation), `FboHelper` (centralised FBO creation),
  `PatternDecompressor.nemesis()` (eliminated private Nemesis copies), `refreshDynamicSpawn()`
  extracted into `AbstractObjectInstance`, `isOnScreen(margin)` guard migrated across all objects,
  `buildSpawnAt()` helper and `getRenderer()` helper inherited by all object classes.
- **Phase 2 — Base class extraction**: `AbstractMonitorObjectInstance`, `AbstractSpikeObjectInstance`
  (S2/S3K), `AbstractProjectileInstance` (S1 missiles), `AbstractPointsObjectInstance`,
  `AbstractFallingFragment` (collapsing platforms), `AbstractSoundTestCatalog`,
  `AbstractAudioProfile`, `AbstractObjectRegistry`, `AbstractZoneRegistry`,
  `AbstractZoneScrollHandler` (~20 scroll handlers migrated), `AbstractLevelInitProfile`
  (with `buildCoreSteps()`), `AniPlcScriptState` and `AniPlcParser` extracted from pattern
  animators.
- **Phase 3 — Behavior helpers**: S1 badnik migration to shared destruction config, shared ring/object
  placement record parsers, shared title card sprite rendering utility, shared S1 Eggman boss
  methods extracted into base class.
- **Phase 4 — Gravity and debris**: `GravityDebrisChild`, `PlatformBobHelper` (3 platform objects
  migrated), `ObjectFall()` method in `SubpixelMotion`.
- **Phase 5 — Cleanup**: shared constants, `loadArtTiles` path, shader path standardization,
  `ParallaxShaderProgram` extends `ShaderProgram` (deleted lifecycle duplication).
- **Debug render migration**: all S1, S2, and S3K objects migrated from legacy
  `appendDebug`/`appendLine` API to `DebugRenderContext`.

### MutableLevel (Level Editor Foundation)

- **MutableLevel**: a new level data abstraction supporting snapshot, mutation, and dirty-region
  tracking. Wraps the read-only level data and provides `setChunkDesc()`, `getGridSide()`,
  `saveState()`/`restoreState()` for undo/redo support.
- **Block**: added `saveState()`/`restoreState()` and `setChunkDesc()` for chunk-level mutation.
- **Dirty-region processing pipeline**: `processDirtyRegions()` wired into `LevelFrameStep` for
  efficient per-frame GPU updates of only the modified tile regions.
- MutableLevel preserves game-specific overrides and `ringSpriteSheet` across mutations.
- Round-trip and integration tests verify snapshot fidelity and mutation correctness.

### Sonic 3 & Knuckles Expansion

#### Knuckles: Playable Character

Knuckles is now a fully playable character with his complete S3K ability set, working natively
in S3K and via cross-game donation into S1 and S2.

- **Glide state machine**: glide activation on jump re-press with ROM-accurate turn physics
  (sine/cosine velocity from `doubleJumpProperty` angle, gravity balance). Direct mapping frame
  control using `RawAni_Knuckles_GlideTurn` table (frames 0xC0–0xC4).
- **Floor landing and sliding**: flat surfaces enter sliding state with deceleration (0x20/frame
  while jump held, matching ROM's `.continueSliding` routine). Slide follows terrain via
  `ObjectTerrainUtils` floor probing, snapping Y position to surface with correct angle. Ledge
  detection enters fall state when floor distance >= 14.
- **Wall grab and climbing**: wall grab with climbing animation cycling (frames 0xB7–0xBC every
  4 frames). Ledge climb using `Knuckles_ClimbLedge_Frames` table (4 keyframes with x/y deltas).
  Wall jump away with facing flip and normal jump animation.
- **Fall-from-glide landing**: ROM-accurate crouch pose with 15-frame `move_lock`.
- **ROM-accurate jump height**: `PhysicsProfile.SONIC_3K_KNUCKLES` with jump velocity 0x600
  (vs Sonic's 0x680), water jump 0x300 (vs 0x380), matching `Knux_Jump` in disassembly.
- **Shield ability gating**: fire/lightning/bubble shield abilities gated to Sonic only per ROM;
  Knuckles gets passive shield protection with glide as his secondary ability. Bubble shield
  bounce correctly suppressed on glide landing (gates on `SecondaryAbility.INSTA_SHIELD`, not
  `doubleJumpFlag` value).
- **Knuckles palette**: `Pal_Knuckles` (0x0A8AFC) loaded for both native S3K and cross-game
  donation. Cross-game palette fix ensures correct palette is loaded based on character config.
- **Life icon art**: `ArtNem_KnucklesLifeIcon` (0x190E4C) with character-specific rendering.
- **Sound effects**: GRAB and GLIDE_LAND SFX registered in S3K audio profile.
- **Character detection fix**: `Sonic3kLevelEventManager.getPlayerCharacter()` now resolves from
  config (was hardcoded to `SONIC_AND_TAILS`), enabling all character-gated object behaviour.
- **AIZ rock breaking**: knucklesOnly rocks (subtype bit 7) now trigger on airborne side contacts
  (jumping/gliding into them), not just grounded push.

#### S2 Cross-Game Knuckles Support

- **Lock-on palette**: "Knuckles in Sonic 2" palette loaded from S3K ROM at 0x060BEA. Only
  indices 2–5 differ from S2's `Pal_SonicTails` (Knuckles' reds vs Sonic's blues); title cards,
  badniks, and rings are unaffected. HUD text index 4 tweaked (green→orange) for readability.
- **Lives icon**: `ArtNem_KnucklesLifeIcon` decompressed from S3K donor ROM with pixel index
  remap from S3K palette layout to S2-compatible layout (`S3K_TO_S2_PALETTE_REMAP`).
- **HUD rendering**: lives name tiles use palette 0 (no flash cycling) when donor art is active,
  via `livesNameUsesIconPalette` flag in `HudRenderManager`.
- **Palette utility**: `Palette.mergeColorsFrom()` added for targeted color range copying.

#### Title Screen

- Full S3 title screen implemented with 6-phase state machine: SEGA logo with palette fade,
  12-frame Sonic morphing animation, white flash transition, and interactive menu with banner
  bounce physics, sprite animations, and menu selection.
- ROM data loading for 7 Kosinski art sets, 4 Nemesis sprite sets, 14 Enigma plane mappings.
- Hardcoded sprite mapping frames for banner, &Knuckles text, menu text, Sonic finger/wink,
  and Tails plane sprites (`Sonic3kTitleScreenMappings`).
- FadeManager transition fix: title screen exit now renders fade-to-black internally to avoid
  `GameLoop`/`UiRenderPipeline` FadeManager instance mismatch after `RuntimeManager` migration.

#### Level Select Screen

- ROM-accurate S3K level select matching the S3 disassembly menu infrastructure.
- `Sonic3kLevelSelectConstants`: data tables (level order, mark table, switch table, icon table,
  zone text, mapping offsets) from `s3.asm` with S&K zones replacing disabled/competition entries.
- `Sonic3kLevelSelectDataLoader`: loads Nemesis art (font, menu box, icons), Enigma mappings
  (screen layout, background, icons), uncompressed SONICMILES animation art, and palettes from ROM.
  Builds screen layout in-memory with S3K zone names via the LEVELSELECT codepage.
- `Sonic3kLevelSelectManager`: two-layer rendering (Plane B SONICMILES background + Plane A
  foreground), input navigation with disabled-entry skipping, sound test (0x00–0xFF), selection
  highlight, and zone icons.

#### Special Stage Character Support

- S3K Blue Ball special stages now dynamically resolve `PlayerCharacter` from config: Sonic &
  Tails (with AI sidekick), Sonic alone, Tails alone (with spinning tails appendage), and
  Knuckles (with correct palette patch to colors 8–15 per ROM's `Pal_SStage_Knux`).

#### AIZ Object Lifecycle Fixes

- **Vine dismount**: suppressed stale jump press on release to prevent immediate insta-shield
  (Sonic) or glide (Knuckles) activation. Added edge detection so holding jump from the vine-
  reaching jump doesn't cause immediate dismount.
- **Vine respawn**: removed self-destruct cull checks from both vine objects. The vine's coarse
  range was narrower than the Placement window, causing permanent respawn prevention via the
  `destroyedInWindow` latch.
- **Collapsing platform respawn**: removed `markRemembered()` call — ROM uses
  `Delete_Current_Sprite` (allows respawn), not `Remember_Sprite`. Platforms now correctly
  respawn when the player scrolls away and returns.
- **Breakable boulders**: preserved rolling state when smashing AIZ/LRZ rocks from the side,
  matching `SolidObjectFull` behaviour.
- **Special stage return**: restored saved centre coordinates correctly on Blue Ball exit,
  preventing the player from being embedded in the floor after returning from the big ring.
- **Results screen spawn path**: signpost flow now uses `spawnChild()` for the results object,
  preserving `ObjectServices` context and fixing the end-of-act bubble monitor crash.

#### AIZ Miniboss Completion
- AIZ miniboss defeat flow fully implemented: `S3kBossDefeatSignpostFlow` reusable sequence,
  staggered explosions with `S3kBossExplosionController`, per-explosion `sfx_Explode`.
- Knuckles napalm attack: `AizMinibossNapalmController` and `AizMinibossNapalmProjectile` with
  launch/drop/explode lifecycle, gated to Knuckles-only appearance.
- AIZ2 dynamic resize state machine for correct camera boundaries during miniboss spawn.

#### AIZ2 Boss and Transition Progress
- AIZ2 Flying Battery bombing sequence implemented with battleship overlay rendering, ship-relative
  bomb placement, explosion children, background tree spawners, and object-art loading for the
  bombership / small Robotnik craft frames.
- AIZ2 end boss implemented with Robotnik ship/head overlays, arm/propeller/flame/bomb/smoke child
  systems, camera scripting, boss state flow, and regression coverage for ship bomb timing.
- Post-boss capsule/cutscene flow now includes the AIZ2 egg capsule release path and handoff toward
  the Hydrocity transition. Follow-up fixes restore AIZ transition zone-feature state and prevent
  bombership art regressions after act-transition reinitialization.

#### Signpost and Results Screen
- `S3kSignpostInstance` with 5-state machine (idle/spin/slowdown/sparkle/done), stub and sparkle
  children, `PLC_EndSignStuff` art loading from ROM.
- `S3kHiddenMonitorInstance` with signpost interaction.
- Results screen: full state machine with tally, element system rendering, art loading from ROM,
  act display via `Apparent_act`, exit timing, control lock, victory pose, and Tails-specific
  victory animation.
- End-of-level flag and `endOfLevelActive` state wired through defeat flow.

#### Blue Ball Special Stages (WIP)
- Blue Ball special stage implemented (work in progress): gameplay, rendering, HUD, banner,
  ring animation, emerald collection, exit sequence.
- `SSEntryRing` art, animation, and special stage entry sequence from giant rings.
- Special stage results screen with art loading.
- Tails P2 support in special stages with tails sprite and delayed jump.
- Player returns to big ring location after special stage (not checkpoint).

#### S3K Bonus Stages: Slots, Gumball, and Glowing Sphere (WIP)
- `Sonic3kBonusStageCoordinator` now implements the S3K ring-threshold selection formula and
  zone/music routing for the three lock-on bonus stages: Slots, Glowing Sphere (Pachinko), and
  Gumball. StarPost bonus-star entry and saved-state return are wired into the S3K bonus-stage
  lifecycle.
- Bonus-stage title card support added to `Sonic3kTitleCardManager` and mappings, including the
  dedicated `BONUS STAGE` layout and bonus-specific fade timing.
- **Gumball stage bring-up:** `GumballMachineObjectInstance`, `GumballItemObjectInstance`, and
  `GumballTriangleBumperObjectInstance` implemented with ROM-driven machine state, dispenser /
  container / exit-trigger child chains, machine Y drift and slot tracking, subtype-specific item
  behavior, spring bounce/crumble parity, shield persistence, sidekick safety, and dedicated
  `SwScrlGumball` scrolling.
- **Glowing Sphere / Pachinko bring-up:** `PachinkoFlipperObjectInstance`,
  `PachinkoTriangleBumperObjectInstance`, `PachinkoBumperObjectInstance`,
  `PachinkoPlatformObjectInstance`, `PachinkoItemOrbObjectInstance`,
  `PachinkoMagnetOrbObjectInstance`, and `PachinkoEnergyTrapObjectInstance` implemented, with
  stage entry/return flow, top-exit handling, and dedicated `SwScrlPachinko` scrolling.
- **Zone animation support:** `Sonic3kPatternAnimator` and `Sonic3kPaletteCycler` now cover the
  bonus-stage-specific Gumball direct-DMA tile animation plus Pachinko animated tiles, DMA-driven
  background strips, and palette cycling.
- **Render-path parity for the gumball machine:** per-piece VDP priority from ROM mapping data,
  SAT-style sprite-mask post-processing, and replay-role metadata now preserve the intended glass /
  shell / interior pile layering for mixed-priority machine frames.
- Pachinko energy trap bootstrap now stays persistent like the ROM object, keeps its spawned
  column/beam children alive until scripted teardown, and force-releases players from competing
  magnet orbs before trap capture. Capture now zeros X/Y/G speed and cleanly holds the player on
  the beam.
- Bonus-stage title card exit no longer freezes the pachinko trap update loop. Persistent power-up
  re-registration now clears stale object slots before `ObjectManager` rebuilds, preventing slot
  aliasing during bonus-stage entry and post-title-card resume.
- **Slot Machine stage bring-up:** `S3kSlotRomData`, `S3kSlotStageController`,
  `S3kSlotStageState`, `S3kSlotCollisionSystem`, `S3kSlotPlayerRuntime`,
  `S3kSlotOptionCycleSystem`, `S3kSlotPrizeCalculator`, and reward/cage object runtime wiring now
  cover ROM table loading, rotating-stage movement, projected ground/air physics, grid collision,
  tile interactions, reel option cycling, match detection, cage capture/release, interpolated ring
  and spike rewards, exit wind-down/fade, and slot-specific sound effects.
- **Slot Machine rendering:** `S3kSlotLayoutRenderer`, `S3kSlotLayoutAnimator`,
  `S3kSlotMachineRenderer`, `S3kSlotMachinePanelAnimator`, `S3kSlotMachineDisplayState`,
  `SwScrlSlots`, and `shader_s3k_slots.glsl` implement layout animation, palette cycling,
  goal/peppermint/reel display updates, background row refresh, debug visibility, and FG glass /
  player priority ordering.
- **Slot Machine remediation:** state ownership was moved into the slot runtime with `ObjectManager`
  rendering for cage/reward objects, preserved special collision bits across probes, authoritative
  follow-up state, persistent wall animation state, capture-cycle restart coverage, and fixes for
  player swap focus, title-card bootstrap, runtime ownership, launch physics, spike reward ring
  drain, reel display, and exit fade.
- Added regression coverage for coordinator lifecycle, bonus title card mappings/flow, gumball
  machine drift and priority diagnostics, sprite-mask helper consumption and replay ordering,
  pachinko palette/pattern animation, slot ROM data, slot collision/player/runtime/rendering/reward
  systems, registry wiring, and live trap/orb/title-card integration.

#### Per-Character Physics
- Per-character physics profiles for Sonic, Tails, and Knuckles (speed, acceleration, jump height).
- Super spindash speed table and slope sprite selection fixes.
- Ducking while moving at slow speeds (S3K-specific behavior).

#### Palette and Visual Systems
- Palette cycling implemented for all remaining zones: HCZ, CNZ, ICZ, LBZ, LRZ, BPZ, CGZ, EMZ
  (plus existing AIZ).
- Per-frame palette mutation system for AIZ1 hollow tree reveal (`palette[2][15]`).
- AIZ fire curtain overlay with cached BG descriptors and fire palette fixes, looping linger and
  graceful scroll-off.
- Heat haze deformation applied to AIZ2 background layer.
- HUD text loaded from ROM; digit rendering uses mapping frames (not tile indices).

#### New Objects and Badniks
- `BreakableWall` (0x0D), `CorkFloor`, `FloatingPlatform`, `CaterkillerJr` (with body segment
  despawn), `AutoSpin`, `Falling Log`, `InvisibleBlock`, `StarPost`, `TwistedRamp`,
  `AIZCollapsingLogBridge` (0x2C), `AIZSpikedLog` (0x2E), `AIZFlippingBridge` (0x2B), and the
  zone-specific `Button` object (0x33).
- HCZ expansion: water surface, water rush sequence (`HCZBreakableBarObjectInstance`,
  `HCZWaterRushObjectInstance`, `HCZWaterWallObjectInstance`, `HCZWaterTunnelHandler`),
  `HCZConveyorBeltObjectInstance`, `HCZCGZFanObjectInstance`, `HCZHandLauncherObjectInstance`,
  `HCZLargeFanObjectInstance`, `HCZBlockObjectInstance`, `HCZConveyorSpikeObjectInstance`,
  `HCZTwistingLoopObjectInstance`, `HczMinibossInstance`, and `DoorObjectInstance` for HCZ/CNZ/DEZ.
- Additional S3K objects and badniks: `CollapsingBridgeObjectInstance`, `BubblerObjectInstance`,
  `Sonic3kInvisibleHurtBlockHObjectInstance`, `MegaChopperBadnikInstance`,
  `PoindexterBadnikInstance`, `BlastoidBadnikInstance`, `BuggernautBadnikInstance` /
  `BuggernautBabyInstance`, and `TurboSpikerBadnikInstance`.
- `Sonic3kLevelTriggerManager` added for AIZ trigger state such as boss-driven burn activation.
- All zone badnik entries populated in `Sonic3kPlcArtRegistry`.
- Initial badnik implementations wired into object system.
- **Badnik destruction effects**: destroying S3K badniks now spawns animals and floating points
  popups, matching S1/S2 behavior. Zone-specific animal pairs loaded from ROM per
  `PLCLoad_Animals_Index` (all 13 zones mapped). Enemy score art parsed from `Map_EnemyScore`
  (shared `ArtNem_EnemyPtsStarPost` blob). `Sonic3kPointsObjectInstance` provides S3K-specific
  score-to-frame mapping.

#### Spindash Dust
- **S3K spindash dust**: implemented native `SpindashDustArtProvider` for Sonic 3&K. Art loaded
  from ROM (`ArtUnc_DashDust` at 0x18A604, `Map_DashDust` at 0x18DF4, `DPLC_DashSplashDrown` at
  0x18EE2). Uses virtual pattern base 0x34000 to avoid collision with ring tiles in the atlas.
- **Multi-character dust isolation**: sidekick dust renderers now get isolated DPLC banks
  (shifted into `SIDEKICK_PATTERN_BASE + 0x2000` range), preventing atlas corruption when
  multiple characters spindash simultaneously.

#### Invincibility Stars
- **S3K invincibility stars**: `Sonic3kInvincibilityStarsObjectInstance` implements ROM-accurate
  Obj_Invincibility (sonic3k.asm:33751) with 1 parent group + 3 trailing child groups.
  Each group renders 2 sub-sprites at opposite positions on a 32-entry circular orbit table
  (`byte_189A0`). Children trail via `PlayableEntity.getCentreX/Y(framesAgo)` at 3/6/9 frames
  behind the player; parent orbits fast (9 entries/frame), children orbit slow (1 entry/frame).
  Rotation reverses when facing left. Art loaded from ROM (`ArtUnc_Invincibility` at 0x18A204,
  `Map_Invincibility` at 0x018AEA). `DefaultPowerUpSpawner` branches on `Sonic3kGameModule`
  to create the S3K variant; S1/S2 `InvincibilityStarsObjectInstance` remains unchanged.

#### Audio
- Music tempo scaling and all-spheres SFX fix.
- Ring collection sound alternates left/right channels.
- Correct SFX: `sfx_Death` for normal hurt (not `sfx_SpikeHit`), jump SFX fix.
- S3K tumble frame base corrected to `0x31` (not S2's `0x5F`).

#### Miscellaneous S3K Fixes
- VDP priority bit correctly extracted in S3K sprite mapping loader.
- Collapsing platforms stay solid during fragment phase (S2/S3K).
- Shield re-registration after act transition; StarPost bonus stage routing fix.
- AIZ1 level bounds use normal `LevelSizes` entry.
- Prevented OOM in S3K DPLC frame loading by parsing only 1P entries (combined mapping table fix).
- SONIC art address corrected; camera bounds restored after transition.
- Lightning shield sparks rendered directly instead of via DPLC.
- Save/restore `Dynamic_resize_routine` across big ring special stage transitions (ROM: `Saved2_dynamic_resize_routine`). Without this, the resize state machine restarted from routine 0 on return, rapidly re-processing boundary thresholds and causing incorrect camera locks in AIZ Act 2.
- Title card showed wrong act after AIZ mid-act fire transition. Death or special stage return displayed "Act 2" instead of "Act 1" because the engine lacked the ROM's `Apparent_zone_and_act` variable. Added `apparentAct` tracking to `LevelManager`: seamless transitions (fire) only update `currentAct`, normal act changes update both, title card requests read `apparentAct`. Results screen exit sets `apparentAct = 1` matching ROM's `move.b #1,(Apparent_act).w`.
- AIZ2 water incorrectly enabled for Knuckles on level select load. `LevelManager.initWater()` hardcoded `SONIC_AND_TAILS` instead of resolving the actual player character from the level event manager. ROM `CheckLevelForWater` (sonic3k.asm:9754-9759) gates AIZ2 water on `Player_mode` and `Apparent_zone_and_act`, disabling it for Knuckles on direct load but enabling it during seamless AIZ1→AIZ2 transitions. Both cases now handled correctly via a `seamlessTransition` flag threaded through `WaterDataProvider`.

### Insta-Shield Implementation

Full S3K insta-shield ability implemented with ROM parity:
- ROM constants, art key, and art loading (including cross-game donation path).
- Activation via `tryShieldAbility()` with character gating (Sonic only, not Tails/Knuckles).
- Hitbox expansion in `TouchResponses` for the active insta-shield frames.
- Persistent `InstaShieldObjectInstance` lifecycle (survives level transitions).
- DPLC cache invalidation on seamless level transitions.
- Lazy art initialization to handle sprite-before-level-load ordering.
- Half-arc animation bug fix (prevented double-update per frame).

### Multi-Sidekick System

- Comma-separated sidekick config enables spawning multiple sidekicks (e.g. `"sonic,tails"`).
- `SidekickRespawnStrategy` interface extracted with `TailsRespawnStrategy` and per-character
  `requiresPhysics()` (Sonic walk-in vs Tails fly-in).
- Parallel sidekick respawn via effective leader reference.
- Virtual pattern ID range validation in `PatternAtlas` for safe multi-bank allocation.
- Sidekick DPLC banks placed in dedicated `0x30000+` range, capped at `0x800` limit with bank
  sharing on overflow.
- Sidekick rendered behind main player to match VDP sprite priority order.
- Leader reference preserved across `reset()` — sidekicks no longer become permanently idle.
- Directional input maintained during approach phase.
- Slot reclamation added to `PatternAtlas` for efficient VRAM management.

#### S3K Sidekick Knuckles Fixes

- **VRAM isolation**: every sidekick now unconditionally gets its own isolated pattern bank in the
  `SIDEKICK_PATTERN_BASE` (0x38000) range, eliminating sprite corruption when characters share
  the same ART_TILE base (Knuckles and Sonic both use 0x0680 in S3K). Removed the name-based
  `computeVramSlots` optimization that missed this collision.
- **Palette isolation**: per-sidekick `RenderContext` palette blocks loaded via
  `PlayerSpriteArtProvider.loadCharacterPalette()`. When a sidekick uses a different palette
  than the main character (e.g. Knuckles' `Pal_Knuckles` vs Sonic's `Pal_SonicTails`), a
  dedicated palette context is created so the sidekick renders with correct colors. Propagated
  to spindash dust and Tails tail appendage sub-renderers.
- **Knuckles glide-in respawn**: `KnucklesRespawnStrategy.requiresPhysics()` now returns
  `true` during the drop phase so the physics pipeline applies gravity. Previously Knuckles
  would hang in mid-air after the glide because `SpriteManager` skipped physics for all
  `APPROACHING` strategies. `GLIDE_DROP` animation set during the glide approach phase.
- **Palette texture resize safety**: `GraphicsManager.cachePaletteTexture()` now preserves
  existing palette data when the texture grows to accommodate new contexts, preventing level
  palette corruption on resize.

#### S3K Zone Bring-Up Skill System

A 7-skill agentic system for systematic, per-zone implementation of S3K visual and behavioural
features (events, parallax, animated tiles, palette cycling). Designed for agent-driven analysis
of the disassembly followed by parallel feature implementation across worktrees.

- **s3k-zone-analysis**: reads the S3K disassembly and produces a structured zone feature spec
  covering events, parallax, animated tiles, palette cycling, notable objects, and cross-cutting
  concerns. Includes Phase 4 shared state trace for cross-category dependency detection (VRAM
  ownership conflicts, palette mutation vs cycling overlaps, event flag gating).
- **s3k-zone-events**: implements `Sonic3kZoneEvents` subclasses porting `Dynamic_Resize` routines
  from the disassembly — camera locks, boss arenas, cutscenes, act transitions, palette mutations.
- **s3k-animated-tiles**: implements AniPLC script triggers in `Sonic3kPatternAnimator` with
  zone-specific gating conditions and dynamic art overrides.
- **s3k-palette-cycling**: implements or validates `AnPal` handlers in `Sonic3kPaletteCycler` using
  the counter/step/limit pattern. Supports both new implementation and validation of existing zones.
- **s3k-parallax** *(updated)*: now accepts a zone analysis spec as optional input to accelerate
  deform routine discovery.
- **s3k-zone-bring-up**: orchestrator that dispatches zone analysis, parallel feature agents in
  worktrees, merge reconciliation, build verification, and validation.
- **s3k-zone-validate**: visual validation via stable-retro reference screenshots compared against
  engine output using agent image recognition (feature presence, not pixel-perfect diffing).
- All skills published in dual format (`.claude/skills/` + `.agent/skills/`) for agent-agnostic use.
- YAML frontmatter standardised across all 20 `.claude/skills/` and 8 `.agent/skills/` files.
- HCZ zone analysis spec produced as smoke test (`docs/s3k-zones/hcz-analysis.md`).
- AIZ zone analysis cross-validated against engine implementation: events and palette cycling
  matched byte-for-byte; parallax matched 13/14 checks; animated tiles revealed 3 cross-category
  gating omissions that motivated the Phase 4 shared state trace addition.

### Tails AI Improvements

- Comprehensive Tails CPU AI rework:
  - WFZ/DEZ/SCZ now suppress the CPU sidekick in gameplay and rendering.
  - Tails switches to FLYING when Sonic dies instead of despawning.
  - Respawn uses ROM's 64-frame gate plus A/B/C/Start bypass, blocking on object-control,
    air, roll-jump, underwater, and prevent-respawn conditions.
  - Manual P2 override for gameplay and special stages.
  - PANIC mode reworked to use `move_lock` + frame-counter timing.
  - Flying/despawn reworked with on-screen checks, water clamp, exact landing criteria.
  - Boss/event updates wired for EHZ2, HTZ2, MCZ2, CNZ2, CPZ2, ARZ2, and MTZ3.
  - Special-stage Tails uses its own replay buffer + P2 takeover path.

### Rendering Pipeline Improvements

- **PatternAtlas slot reclamation**: freed VRAM slots can be reused by new pattern uploads.
- **Batched DPLC atlas updates**: `DynamicPatternBank` batches multiple pattern updates per frame
  instead of individual uploads.
- **Virtual pattern ID validation**: range checks prevent silent VRAM corruption from out-of-bounds
  pattern references.
- **FboHelper**: centralised FBO creation utility, migrated 4 renderer files.
- **writeQuad()** extracted from `BatchedPatternRenderer` for reuse.
- Fail-fast on shader compilation/linking errors with GL resource cleanup.

### Logging and Error Handling

- 22 `e.printStackTrace()` calls migrated to structured `java.util.logging`.
- 28 swallowed exceptions in S3K code replaced with `LOG.fine()`.
- Production `System.out.println` calls replaced with `LOGGER.fine()`.
- Remaining logging gaps fixed across 6 files.

### Performance

- Batched DPLC atlas updates in `DynamicPatternBank`.
- Cached `LevelManager` reference in `DefaultObjectServices` (eliminates per-call singleton lookup).
- Per-frame `ObjectSpawn` allocation eliminated in `AbstractBadnikInstance`.
- Pre-allocated debug overlay lists, collision/sensor/camera bounds command lists.
- Reduced per-frame allocations in collision, rendering, and audio hot paths.
- Batched glyph rendering for debug text.

### BizHawk Trace Replay Testing

A new automated accuracy verification system that records per-frame physics state from the real ROM
running in BizHawk emulator, then replays the same inputs through the engine and compares every
field frame-by-frame.

- **Lua trace recorder** (`tools/bizhawk/`): BizHawk Lua script that captures player position,
  speed, angle, ground mode, air/rolling flags, and controller input every frame during a BK2
  movie playback. Outputs `metadata.json`, `physics.csv`, and `aux_state.jsonl`.
- **stable-retro trace recorder** (`tools/retro/`): cross-platform Python equivalent of the
  BizHawk Lua recorder, using stable-retro (Genesis Plus GX) for headless emulation. Produces
  byte-identical output format (same CSV, JSONL, and metadata.json) consumed by the same Java
  test infrastructure. Supports stable-retro BK2 replay, BizHawk BK2 parsing, savestate boot,
  and credits demo recording. Enables trace generation on macOS and Linux without BizHawk.
  Verified byte-for-byte output match against BizHawk reference traces for first 2100+ frames
  of GHZ1 before GPGX version-specific lag frames diverge the runs.
- **stable-retro BK2 alignment** (`--bk2-offset`): replays BizHawk BK2 movies through
  stable-retro by shifting BK2 inputs to the emulator's gameplay start frame. Handles GPGX
  byte-swap, exact-0x0C game_mode detection, and `|system|P1|P2|` BK2 group parsing.
- **Lag frame handling in credits demo tests**: `AbstractCreditsDemoTraceReplayTest` now
  detects lag frames (identical physics state on consecutive frames with non-zero speed) and
  skips both engine physics and demo input advancement on those frames. Reduced MZ2 credits
  divergences from 28 errors/131 warnings to 10 errors/57 warnings. Remaining errors are
  genuine engine divergences (missed bounces, slope collision, object timing).
- **Trace replay test infrastructure** (`tests.trace` package): `TraceData` loader, `TraceFrame`
  parser, `TraceBinder` per-frame comparator with configurable tolerances, `DivergenceReport`
  with JSON output and context windows, lag frame detection for VBlank sync.
- **`AbstractTraceReplayTest`**: base class for trace replay tests with graceful skip when ROM,
  BK2, or trace data files are absent. Subclasses only specify game/zone/act/path.
- **First trace: S1 GHZ1** full-run recording (3,905 frames): passes with 0 errors, 6 warnings.
- **Second trace: S1 MZ1** full-run recording (7,936 frames): baseline added with
  `TestS1Mz1TraceReplay`, regenerated GHZ1 traces, and ROM-verified zone/act metadata.
- **Recorder/diagnostics upgrades**: trace format now captures subpixel position, player routine,
  camera state, ring count, raw status, `v_framecount`, `standOnObj`, slot dumps, routine-change
  events, and ObjPosLoad cursor state for direct ROM/engine comparison.
- **Engine-side context windows**: divergence reports now include ROM and engine routine/object
  diagnostics, riding-object context, and placement cursor counters to narrow parity failures.
- **Buzz Bomber proximity fix**: removed overcorrecting player position prediction from the
  proximity detection check. The engine's 1-frame late spawn (pre-camera X vs ROM's post-camera X)
  and the pre-physics player position naturally cancel, placing the Buzz Bomber at the correct
  stop position without prediction.
- **Post-camera object placement sync**: `LevelFrameStep` now runs a post-camera placement
  catch-up pass after the camera update, closing the spawn timing gap when the camera crosses a
  chunk boundary between object placement (step 2) and camera update (step 5).
- **Placement parity narrowing**: S1 `out_of_range` timing, dormant-spawn handling, and
  ObjPosLoad callback groundwork reduced the remaining MZ1 investigation to a terrain /
  solid-contact parity problem rather than cursor drift.

#### Physics Accuracy Fixes (discovered via trace replay)

- **16:16 fixed-point subpixel positions**: `AbstractSprite.move()` upgraded from 16:8 to 16:16
  fixed-point arithmetic, matching the ROM's 32-bit `move.l obX(a0),d2` / `asl.l #8,d0` /
  `add.l d0,d2` position update. `xSubpixel`/`ySubpixel` widened from `byte` to `short`.
  `setX()`/`setY()` no longer zero the subpixel fraction (ROM's `move.w` to x_pos doesn't
  touch x_sub). Collision adjustments use new `shiftX()`/`shiftY()` to preserve subpixel.
- **GroundMode enum order fix**: `LEFTWALL` and `RIGHTWALL` were swapped; corrected to match
  ROM's quadrant assignment (0x40 = LEFTWALL, 0xC0 = RIGHTWALL).
- **CalcRoomInFront probe quadrant**: wall probe now uses `anglePosQuadrant()` (asymmetric
  rounding matching ROM's AnglePos dispatch) instead of `(angle+0x20)&0xC0`. Fixes false wall
  detections at steep slope angles (e.g. rotated angle 0xA0).
- **CalcRoomInFront 32-bit prediction**: probe prediction uses full 16-bit subpixel, matching
  ROM's 32-bit position arithmetic.
- **Air collision landing split**: separated `doTerrainCollisionAirDirect()` for movement
  quadrants 0x40/0xC0 (land immediately when floor detected, no speed threshold) from
  quadrant 0x00 (speed-dependent threshold). Matches ROM's per-quadrant landing logic.
- **Double ground mode update**: second `updateGroundMode()` after `selectSensorWithAngle()`
  uses the new angle from terrain probes, matching ROM's end-of-frame ground mode calculation.
- **Arithmetic right shift for air drag**: `xSpeed / 32` changed to `xSpeed >> 5` to match
  68000's `asr.w #5,d1` which rounds toward negative infinity (Java `/` truncates toward zero).
- **Jump transition defers air physics**: on jump, air physics are deferred to the next frame
  (ROM's `addq.l #4,sp` pops the return address, skipping the rest of ground movement).
  `sprite.setOnObject(false)` now called before jump to match `bclr #sta_onObj`.
- **BCC carry flag parity**: spindash release speed clamp `gSpeed > 0` changed to `gSpeed >= 0`
  to match 68000's carry flag behavior (carry SET on unsigned overflow, BCC NOT taken for zero).
- **`groundWallCollisionEnabled` feature flag**: new `PhysicsFeatureSet` field. S1 does not
  call CalcRoomInFront during ground movement (no equivalent in `Sonic_MdNormal`); S2/S3K do.
- **Air-control superspeed preservation**: S3K now preserves airborne speeds already above
  `topSpeed` after ramps and springs, while S1/S2 retain the original hard cap. `TwistedRamp`
  tumble frames now remain visible while rolling.

#### Object System Fixes (discovered via trace replay)

- **Deterministic object iteration order**: active objects now sorted by spawn X position,
  matching ROM's slot-order correlation with spawn-window entry order.
- **Touch response timing**: `runTouchResponsesForPlayer()` extracted and called during the
  player physics tick (after `handleMovement()`, before solid contacts), matching ROM's
  ReactToItem timing within Sonic's ExecuteObjects slot.
- **S1 UNIFIED collision model in SpriteManager**: pre-movement solid pass skipped for S1
  (ROM processes all solid objects after Sonic's movement); post-movement pass with
  `postMovement=true` disables velocity classification adjustment.
- **SolidContacts post-movement parameter**: `updateSolidContacts()` gains `postMovement` and
  `deferSideToPostMovement` flags to support the S1/S2 collision timing difference.
- **ROM-accurate `out_of_range` semantics**: `AbstractObjectInstance.isInRange()` now matches the
  ROM's chunk-aligned X-only range check with 16-bit wraparound, and S1 now performs
  out-of-range deletion during object execution rather than before it.
- **Dormant spawn tracking**: objects deleted by S1 `out_of_range` stay dormant between ObjPosLoad
  cursors until the cursor naturally re-processes them, preventing premature or missing reloads
  during camera backtracking.
- **Standing/contact parity fixes**: `MvSonicOnPtfm` now uses `groundHalfHeight` (`d3`) for
  standing Y, HURT touch responses now remain continuous after invulnerability expires, and
  staircase / MTZ platform / nut / button / elevator contact state now uses ROM-style boolean
  latches instead of diverging frame counters.

### Object Lifecycle Safety

- Removed constructor-time `services()` usage from 38 object classes; all affected objects now
  lazily initialize renderer and service-dependent state after `ObjectServices` injection.
- `TestNoServicesInObjectConstructors` now hard-fails constructor-time service access, unsafe
  `addDynamicObject(new X(...))` patterns, and pre-registration method calls that transitively
  depend on injected services.
- Sonic 1 lava geysers now defer initialization until first update, preventing pre-registration
  crashes; the lavafall third piece also no longer cascade-spawns infinite children.

### Sonic 1 Fixes

- Drowning visuals: breathing air bubble animation frames and countdown number positioning corrected.
- LZ credits demo spike collision fix and frame tick ordering unification.
- Yadrin top-hit behaviour and underwater palette/animation fixes for LZ.
- Minor LZ fix for jumping while sliding.
- GHZ bridge collision fix with corresponding tests.
- Monitor collision fix (particularly when in a tree).
- Bubble breathing now uses the fallback animation chain correctly, so grabbing an air bubble shows
  the intended breathing animation instead of preserving the rolling/spinning pose.
- SLZ staircase activation now uses ROM-style per-frame contact latches and has dedicated headless
  regression coverage.
- Bubble makers, push blocks, and related S1 objects now use ROM-accurate range semantics; spike
  standing dimensions now match the ROM's `d2`/`d3` values, including sideways spike extension.

### Sonic 2 Fixes

- HTZ water configuration corrected (Hill Top Zone no longer reports water).
- Collapsing platforms in MCZ stay solid during fragment phase.
- Special stage results screen decoupled from object system.
- S2/S3K collapsing platforms remain solid during fragment phase.
- CPZ staircase, MTZ platforms, nuts, buttons, and elevators now use boolean contact latches
  instead of frame-counter comparisons, fixing activation regressions during title cards and
  multi-sprite updates.
- Invincibility stars (Obj35) rewritten to match s2disasm: star 0 orbits at player's current
  position with fast rotation ($12/frame), stars 1-3 trail behind via position history buffer
  (3/6/9 frames behind) with slow rotation ($02/frame). Each star renders 2 sub-sprites at
  180 degrees apart. Corrected orbit offset table (7 entries had wrong X values), animation
  tables (parent uses byte_1DB82; trailing stars use per-star primary/secondary tables), and
  direction-aware rotation (angle negated when facing left).
- RNG parity paths tightened through shared `GameRng` coverage and `Sonic2Rng` regression tests,
  including CNZ slot-machine consumers and S2 object/boss call sites.

### Cross-Game Feature Donation Enhancements

- S1 wired as donor for forward donation into S2/S3K (previously only S2/S3K could donate).
- `DonorCapabilities` interface replaces hardcoded game-specific branches.
- `CanonicalAnimation` enum provides a game-neutral animation vocabulary for cross-game translation.
- `AnimationTranslator` handles bidirectional profile translation between any pair of games.
- Spindash speed table sourced from donor `PhysicsFeatureSet`.
- Cross-game art keys promoted to `ObjectArtKeys` for game-agnostic constant references.
- Import leak cleanup: removed cross-game S2 animation ID imports from game-agnostic sidekick code.

### Test and Quality

- `SingletonResetExtension` and `@FullReset` for automated per-test singleton lifecycle.
- `GameRuntime` lifecycle wired into 35 test classes with optimized Surefire configuration.
- Multi-sidekick integration smoke tests.
- Insta-shield test suite: gating, hitbox expansion, and visual frame-by-frame capture.
- MutableLevel round-trip and integration tests.
- S3K results screen tally mechanics unit tests.
- S3K registry coverage tests for all zones.
- Per-character respawn strategy unit tests.
- Migration guard scanner for detecting `getInstance()` / `GameServices` violations in object code.
- Annotated guard tests for services() migration completeness.
- AudioManager.resetState() field-clearing verification.
- Added `TestS1Mz1TraceReplay`, `TestSonic1StaircaseActivation`, `TestAbstractObjectInstanceRange`,
  and expanded lava geyser / constructor-safety guard coverage.
- Fixed 7 test failures caused by leaked runtime state: updated S3K Knuckles physics assertion
  to expect `SONIC_3K_KNUCKLES` profile (jump=0x600), saved/restored `RuntimeManager` in render
  tests, guarded teardown camera calls with null checks, used `destroyForReinit()` for
  `TestGraphicsManagerHeadless`.

#### Test Suite Cleanup

Systematic audit and remediation of the test suite. Net result: +34 passing tests, 36→0 skipped,
no new failures.

- **Stale @Ignored stubs replaced with real tests**: `TestTodo14` (PlayerCharacter ordinals),
  `TestTodo13` (19 SBZ/FZ event routine tests), `TestTodo17` (boss flag gating), `TestTodo19`
  (rock debris table parity), `TestTodo34` (water slide chunk detection).
- **Broken live tests fixed**: `TestTodo3` (MonitorType reflection instead of test-local enum copy),
  `TestTodo37` (ROM-vs-engine constant parity via reflection).
- **Dead test files deleted**: 8 fully-@Ignored TestTodo stubs for unimplemented features (Yadrin
  spiky-top, Knuckles monitor, Super transform, rock width, rock push, ChopChop bubbles, control
  lockout, SBZ2 transition); 8 zero-assertion diagnostic dumps; `TestTodo29` (SCALE no-op).
- **Low-value tests pruned**: constant-equals-itself assertions (Knuckles cutscene timers, emerald
  scatter constants), ROM-only checks with no engine cross-reference (angle table size, CNZ
  romDataPresent), duplicate coverage (edge balance constants, water provider hasWater), test
  infrastructure self-tests (SharedLevel, InitStep fields).
- **Test uplifts**: `TestTodo1` cross-references ROM water heights against `Sonic2WaterDataProvider`;
  `TestTodo31` adds real end-game zone boundary assertions; 7 S3K palette cycling test files (AIZ2,
  CNZ, EMZ, HCZ, ICZ, LBZ, LRZ) strengthened from "color changed" to specific RGB value assertions
  using `Sonic3kPaletteCycler` with StubLevel; water data provider tests deduplicated between
  provider and handler files.
- **Integration gaps closed**: removed blocked @Ignored stubs from `TestGameLoop` (special stage
  mode) and `TestTodo4` (MCZ boss collision boxes); removed reference-file-dependent test from
  `TestSonic3kVoiceData`; removed diagnostic dump stubs from `TestS3kSonicSpriteDiag`.

### Documentation

- Comprehensive user guide for three audiences (players, developers, contributors).
- OpenSMPSDeck music tracker design spec and implementation plan.
- Rendering pipeline improvements spec and plan.
- Unified execution roadmap and Phase 0+1 implementation plan.
- GameRuntime architecture spec and implementation plan.
- Two-tier service architecture design spec and implementation plan.
- MutableLevel (Phase 3) spec and implementation plan.
- Insta-shield design spec and implementation plan.
- Multi-sidekick daisy chain design spec and implementation plan.
- Cross-game bidirectional animation donation design spec and implementation plan.
- Game-specific leak fixes spec and plan.
- Services migration cleanup design spec and implementation plan.
- Architectural fixes design spec, implementation plan, and review passes.
- Singleton lifecycle documentation.
- Phase 4 common refactoring design spec (5 phases, 25 patterns) and implementation plan (21 tasks).
- Virtual pattern IDs and multi-sidekick system documented in AGENTS.md.
- Known discrepancies documentation for multi-sidekick rendering.
- Added the `s1-trace-replay` skill and refreshed skill descriptions for the parity-driven
  object/boss/disassembly workflow docs.

## v0.4.20260304 (Released 2026-03-04)

Analysis range: `v0.3.20260206..v0.4.20260304` on `develop` (`1790` commits, `1589` non-merge commits,
`2040` files changed, `218141` insertions, `195996` deletions).

> Note: the large deletion count reflects the package rename from `uk.co.jamesj999.sonic` to
> `com.openggf`, which deleted and recreated most source files. Net code growth is ~22,100 lines.

### Sonic 1 Expansion and Content Completion

- Added full Sonic 1 title screen pipeline and title-screen-to-level-select flow
  (`Sonic1TitleScreenManager`, loader, mappings, transition handling).
- Implemented Sonic 1 rings and lamppost/checkpoint behavior.
- Implemented Sonic 1 special stage gameplay and integration:
  - Game-agnostic special stage provider refactor.
  - `Sonic1SpecialStageManager`, renderer/background renderer/data loader, block types, and results screen.
  - Giant Ring route from normal gameplay into special stage flow.
- Introduced per-zone event coverage for Sonic 1 with zone-specific managers/events for GHZ, MZ, SYZ,
  LZ (including water events), SLZ, SBZ, and ending/FZ handling.
- Major object implementation wave for Sonic 1: `117` new object-related classes
  (`78` general objects, `23` badnik classes, `16` boss-related classes).
- Boss coverage expanded to GHZ, MZ, SYZ, LZ, SLZ, and FZ with child objects/projectiles and event integration.
- Added/finished LZ water behavior and bubble systems, including per-ROM drowning music selection.
- Added ending/outro flow updates and initial credits sequence implementation.
- Added SBZ2 post-level-end sequence.
- Fixed S1 physics regressions with test coverage (multiple passes).

### Sonic 2 Gameplay Additions

- Added Sonic 2 title screen architecture and title-screen audio regression coverage.
- Added major object coverage passes:
  - Metropolis Zone object set (`16` objects) and engine crush detection.
  - Sky Chase/Tornado object set and spawn path integration.
  - Wing Fortress object set and supporting hazards/platforms.
  - Oil Ocean object and oil-surface behavior improvements.
- Added MCZ boss implementation (`Sonic2MCZBossInstance` + falling debris support) with follow-up fixes.
- Added MTZ Boss (Obj54) with S2 boss event stubs.
- Added WFZ Boss (ObjC5) with laser platform attack cycle, plus ROM-accuracy pass (17 issues).
- Added DEZ Mecha Sonic boss (ObjAF) with full state machine, plus ROM-accuracy pass (17 issues).
- Added DEZ Death Egg Robot (ObjC7) — final S2 boss, plus ROM-accuracy pass (12 issues).
- Added Robotnik escape sequence between DEZ boss fights (ObjC6).
- Six passes of DEZ boss ROM-accuracy corrections: Silver Sonic facing direction, LED overlay,
  animation phase gating, Egg Robo collision/render priorities, Death Egg Robot child systems.
- Added `61` new Sonic 2 object-related files (`45` general objects, `14` badnik classes, `2` boss files),
  including additional SCZ/WFZ/MTZ/OOZ badnik/object coverage.
- Refactored and expanded Sonic 2 zone events (`Sonic2LevelEventManager` + per-zone event classes).
- Implemented Sonic 2 credits and ending system:
  - `EndingPhase` enum, `EndingProvider` interface, and `ENDING_CUTSCENE` GameMode.
  - `Sonic2CreditsTextRenderer`, `Sonic2CreditsMappings`, `Sonic2CreditsData` with timing constants.
  - `Sonic2EndingCutsceneManager` and `Sonic2EndingArt` with DEZ star field background rendering.
  - `Sonic2LogoFlashManager` with ROM-accurate palette strobe.
  - `Sonic2EndingProvider` wired to DEZ boss ending trigger.
  - Rewritten for ROM parity with ObjCA/ObjCC, DPLC player sprites, tornado visibility.
  - `Sonic1EndingProvider` refactored to use shared `EndingProvider` interface.
- Added demo playback functionality with enhancements and routing to objects.
- Systematic TODO resolution pass: water heights, monitor effects, distortion table, sliding spikes,
  dual collision, Yadrin spiky-top collision, water slide control lockout, LZ rumbling SFX,
  boss flag wiring to AIZ pattern animations, plus TODO/FIXME coverage tests with disassembly validation.
- Various object fixes: PointPokey positioning, MCZRotPlatforms child accumulation, signpost/screen
  locking, object loading improvements, ROM-accurate bumper/bonus block/rising pillar/diagonal spring physics.

### Super Sonic and Per-Game Physics

- Added cross-game physics abstraction:
  - `PhysicsProfile`, `PhysicsFeatureSet`, `PhysicsModifiers`, `PhysicsProvider`, and `CollisionModel`.
  - Validation tests for profile behavior, collision model differences, spindash gating, and speed capping.
- Implemented Sonic 2 Super Sonic flow:
  - Base state machine via `SuperState`/`SuperStateController`.
  - Integration into playable sprite/game loop/module plumbing.
  - ROM-based animation loading, ROM-exact palette cycling, and S2 constants wiring.
  - Invulnerability/enemy-destruction behavior and shield/power-up interaction guards.
  - Debug toggle support and Super Sonic stars object support.
- Added Sonic 3K Super Sonic controller stub/hook points for future parity work.
- Added cross-game Super Sonic delegation to S1 and S2 game modules via `CrossGameFeatureProvider`,
  including palette, audio, and renderer integration, invincibility, and S3K slope animation offset.

### Sonic 3K Bring-Up (AIZ-Focused)

- Extended Sonic 3K bootstrap/audio readiness (voice/sfx index fixes, ROM loading fixes, SoundTestApp support).
- Implemented Angel Island intro cinematic pipeline:
  - AIZ event wiring and intro state-machine objects (`AizPlaneIntroInstance`, Knuckles cutscene objects,
    emerald scatter, wave/plane/glow/booster children).
  - Intro art loading/caching and terrain swap integration.
- Added AIZ gameplay object work with parity-focused fixes:
  - Ride vines and giant ride vines.
  - Hollow tree traversal and reveal/tilemap support.
  - Multiple parity fixes (angle bytes, state retention, endianness, momentum, despawn guards) plus regressions.
- Added AIZ miniboss object set and child components.
- Added initial S3K badnik framework and first wired badnik implementations.
- Added S3K shield object implementations and fixed deferred PLC loading after AIZ intro.
- Added Sonic 3K title card manager/mappings and S3K pattern/palette animation work.
- Implemented S3K water system:
  - Game-agnostic `WaterDataProvider` and `DynamicWaterHandler` interfaces.
  - `ThresholdTableWaterHandler` for table-driven water zones.
  - `Sonic3kWaterDataProvider` with static heights, dynamic handlers, and underwater palette loading.
  - `Sonic1WaterDataProvider` migration to the new provider architecture.
  - Wired into LevelManager and S3K zone features, deprecated game-specific water loading methods.
  - Correct water threshold tables, `setMeanDirect`, zone scope, and starting heights matching ROM.
  - S3K water locked flag, shake timer, LBZ2 pipe plug handler.
  - AIZ2 Knuckles water exclusion, raise speed inheritance, `update()` overshoot fixes.
- Implemented seamless AIZ fire transition flow (`S3kSeamlessMutationExecutor`).
- AIZ miniboss cutscene and barrel shot child updates.
- Expanded AIZ scroll handler work (`SwScrlAiz`).

### PLC, Art Loading, and Tooling

- Major PLC and sprite-pattern refactor across S1/S2/S3K pipelines.
- Added/expanded PLC systems:
  - `Sonic2PlcLoader`, `Sonic2PlcArtRegistry`, and broader S3K PLC loading paths.
  - Shared sprite/mapping loader use (`S1SpriteDataLoader`, `S2SpriteDataLoader`, `S3kSpriteDataLoader`).
- Expanded ROM/disassembly tooling:
  - Object profile abstractions per game (`Sonic1ObjectProfile`, `Sonic2ObjectProfile`, `Sonic3kObjectProfile`).
  - Shared-ID handling in S3K object checklist generation.
  - PLC cross-referencing in `RomOffsetFinder`/`DisassemblySearchTool` and `ObjectDiscoveryTool`.

### Audio, Stability, and Engine Hardening

- Audio updates:
  - Music/SFX catalog refactor to enum-driven paths.
  - PSG GPGX hybrid parity work and tests.
  - S3K pitch wrapping and SFX index fixes.
  - YM2612/SMPS fixes (including SSG-EG active-count leak and loop counter bounds).
  - Thread-safety fixes in SMPS/audio backend paths and output mixing saturation safeguards.
- Engine hardening and safety:
  - ROM read synchronization and bounds checks.
  - Kosinski/resource loading safety limits.
  - Graphics cleanup fixes (resource leaks, reset-state gaps, allocation reductions).
  - Additional stability fixes across water/drowning handling, invulnerability timing, and debug movement modifiers.
- Performance passes across level/render/audio hot paths and internal debug profiling updates.
- Fixed SFX channel replacement: kill old SFX track on shared channel to prevent priority lock.
- Synth-core review fixes: resource safety, encapsulation, dead code cleanup.
- HTZ earthquake fixes: descending through floor, tile display, rising lava subtype 4 hurt behaviour.
- Consolidated duplicate sine/cosine tables to `TrigLookupTable`.
- Fixed cross-game features breaking layer switchers.
- Fixed special stage transition softlocks and S1 results fade type.

### Test and Quality Coverage

- Added `83` new test files across this range, including:
  - Sonic 1 special stage, object, badnik, boss, and routing regressions.
  - Sonic 3K AIZ intro/state timeline/hollow tree traversal parity regressions.
  - Title screen audio regression coverage.
  - PSG/YM2612 and per-game physics/profile parity checks.
- Expanded headless and subsystem-focused tests in support of object/event/audio refactors.
- Added 21 headless bug reproduction tests for 17 reported S1/S2 bugs.
- JUnit 5 migration: deleted 54 self-verifying tests, replaced with parameterized tests.
- Parallelized test execution with 8 forked JVMs.
- Test grouping by level: merged headless tests sharing the same level load into groups
  (EHZ1: 4→1, ARZ1: 3→1, CNZ1: 3→1, HTZ1: 2→1, AIZ1: 2→1, GHZ1: 6→1), eliminating 14 redundant
  level loads.
- Added TODO/FIXME coverage tests with disassembly validation.

### Cross-Game Feature Donation

Implemented cross-game feature donation system: a donor game (S2 or S3K) provides player sprites,
spindash dust, physics, palettes, and SFX while the base game (e.g. S1) handles levels, collision,
objects, and music. Enabled via `CROSS_GAME_FEATURES_ENABLED` and `CROSS_GAME_SOURCE` config keys.

- `CrossGameFeatureProvider` singleton: opens donor ROM as secondary ROM (no module detection
  side-effect), creates game-specific art loaders (`Sonic2PlayerArt`/`Sonic3kPlayerArt`,
  `Sonic2DustArt`), builds hybrid `PhysicsFeatureSet` (spindash from donor, everything else S1),
  loads donor character palette, initializes donor audio.
- `RenderContext` palette isolation: base game occupies palette lines 0-3, each donor gets its own
  block of 4 lines (4-7, 8-11, etc.) via static registry with `getOrCreateDonor()`.
  `uploadDonorPalettes()` pushes donor palettes to GPU. `getDonorContexts()` for iteration.
- `GameId` enum with `fromCode()` for type-safe donor identification.
- `RomManager.getSecondaryRom()` opens donor ROM without triggering game module detection.
- `LevelManager` art loading paths (`initPlayerSpriteArt`, `initSpindashDust`, `initTailsTails`)
  check `CrossGameFeatureProvider.isActive()` and delegate to donor art providers, attaching
  donor `RenderContext` to each `PlayerSpriteRenderer`.
- `Engine` initialization gates sidekick spawning on `GameModule.supportsSidekick()` or
  `CrossGameFeatureProvider.isActive()`, with cleanup on shutdown.
- GPU palette texture dynamically resized via `RenderContext.getTotalPaletteLines()`. All shaders
  (`shader_the_hedgehog`, `shader_tilemap`, `shader_water`, `shader_sprite_priority`,
  `shader_instanced_priority`, `shader_cnz_slots`) updated from hardcoded `/4.0` to
  `/TotalPaletteLines` uniform.
- Underwater palette derivation for donor sprites:
  - `RenderContext.deriveUnderwaterPalette()` synthesizes donor underwater colors using the base
    game's global average per-channel color shift ratio (not per-index, which would mismatch
    palette layouts across games).
  - `GraphicsManager.cacheUnderwaterPaletteTexture()` extended to populate donor palette rows
    automatically from the base game's normal-to-underwater shift.
- Donor SMPS driver config for correct SFX playback:
  - `SmpsSequencerConfig` threaded through `AudioManager.registerDonorLoader()` (4-arg overload),
    stored per donor game in `donorConfigs` map.
  - `AudioBackend.playSfxSmps()` 4-arg overload accepting explicit config; `LWJGLAudioBackend`
    uses donor config when provided, falling back to base game config.
  - `CrossGameFeatureProvider.initializeDonorAudio()` passes `donorProfile.getSequencerConfig()`.
- Donor audio overlay in `AudioManager`: `donorLoaders`, `donorDacData`, `donorSoundBindings` maps;
  `playSfx()` falls through to donor path when base game sound map has no entry.
- S3K Tails tail appendage support: `CrossGameFeatureProvider.hasSeparateTailsTailArt()` and
  `loadTailsTailArt()` delegate to donor's `Sonic3kPlayerArt` for separate Obj05 tail art.
  `LevelManager.initTailsTails()` checks donor game module when cross-game is active, selecting
  correct art loading path and `ANI_SELECTION_S3K` animation tables.
- SFX re-trigger fix in `SmpsDriver`: re-triggering the same SFX ID now replaces the old sequencer
  instead of competing for the same FM/PSG channels (prevents priority lock ping-pong with S1/S2
  jump SFX priority 0x80).
- Tests: `TestRenderContext` (9 tests covering palette isolation, line allocation, reset,
  underwater palette derivation), `TestDonorAudioRouting` (donor SFX routing and sequencer config),
  `TestGameId`, `TestHybridPhysicsFeatureSet`, `TestSidekickGating`.

### Master Title Screen

- Implemented `MasterTitleScreen` (404 lines): engine-wide title screen displayed on startup before
  entering game-specific title flow. PNG-based background, animated clouds, title emblem, and game
  selection text rendered via `TexturedQuadRenderer` and `PixelFont`.
- New rendering infrastructure: `PngTextureLoader` (85 lines), `TexturedQuadRenderer` (139 lines),
  `PixelFont` (144 lines), `shader_rgba_texture` vertex/fragment shaders.
- Configurable via `TITLE_SCREEN_ON_STARTUP` config key (default: enabled).

### Sonic 1 Fixes and Improvements

- Fixed Sonic spawning 5px underneath terrain on level reset by restoring standing radii in
  `AbstractPlayableSprite` respawn path (ROM: `Obj01_Init` unconditionally sets `y_radius=$13`).
- Object collision fixes: `ObjectManager` solid overlap test now always uses `airHalfHeight`
  matching ROM behaviour (d3 is overwritten by playerYRadius before read). Added
  `Sonic1ButtonObjectInstance` and `Sonic1MzBrickObjectInstance` collision support.
  `TestHeadlessSonic1ObjectCollision` (291 lines) regression test added.
- Fixed edge balance mode for S1 (single balance state, force face edge) while preserving S2's
  4-state extended balance. `PhysicsFeatureSet.extendedEdgeBalance` gates behaviour.
  `TestEdgeBalance` (91 lines) and `TestHeadlessSonic1EdgeBalance` (369 lines) added.
- Fixed MZ2 push block: longer blocks no longer get pushed "out of the way" when Sonic pushes them
  against walls. `SolidContact` improvements. `TestHeadlessMZ2PushBlockGap` (132 lines) added.
- SBZ fixes: Flamethrower positioning corrected for vflip/hflip variants. StomperDoor objects fixed.
  Junction now locks the player correctly. SBZ3 water oscillation implemented.
- LZ fixes: Wind tunnels now play correct player animation. Breakable poles play correct animation.
  Water splash effect implemented (`Sonic1SplashObjectInstance`).
- Demo playback now sent to objects (`AbstractPlayableSprite` demo input routing).
- Push stability fixes for solid objects. `TestHeadlessSonic1PushStability` (220 lines) added.
- Outro/credits improvements (`Sonic1CreditsManager`, `FadeManager` enhancements).
- `TestSbz1CreditsDemoBug` (162 lines) and `TestS1FlamethrowerObjectRendering` (58 lines) added.
- S1 "fast" mode SMPS sequencer support.
- S1 outro improvements: disable control on outro, change 'back to main menu' key.
- S1 ending sequence flowers fix.
- S1 object collision fixes.

### Sonic 2 Fixes

- Fixed badnik palette lines (Spiny now uses palette line 1 matching `make_art_tile`), signpost
  frame order corrected to match `obj0D_a.asm` ROM mapping order, CPZ stair block / MTZ platform
  art sheet rebuilt with hand-crafted mappings (ROM mappings reference level art tiles).
- Swinging platform art loading fix for non-S2 games.
- S2 ending cutscene parity: DEZ white fade (not black), star field background, pilot visibility,
  BG scroll compensation, DPLC player sprites, tornado visibility, falling timing.
- Prevented DEZ Robot despawn during defeat ending sequence.
- Fixed DEZ boss visual and collision issues (multiple passes).
- Fixed S2 credits visual accuracy: ROM-correct font, mappings, and player detection.
- Fixed S2 `Sonic2LevelEventManager` zone constants alignment with `ZoneRegistry`.

### Physics and Collision Fixes

- Fixed solid object edge jitter: `SolidContacts` snaps player to resolved edge on static solids
  to prevent subpixel accumulation. Push-driven objects opt in to ROM-style subpixel preservation
  via `SolidObjectProvider.preservesEdgeSubpixelMotion()`.
- S1 slope crest sensor guard: prefer floor-class probe over wall-class probe at crest transitions,
  preventing one-frame wall/air mode flips.
  `TestHeadlessStaticObjectPushStability` (208 lines) and
  `TestSonic1GhzSlopeTopDiagnostic` (519 lines) added.
- Sonic no longer jumps if the player holds jump while airborne via a non-jump (spring, slope
  launch, etc.).
- Various physics tweaks aimed at S1: physics modifiers cleanup, `FadeManager` fade-to-black
  transitions no longer flash back to "off" briefly before fade-in begins.
- Fixed results screen rendering issue for both S1 and S2.

### Package Rename

- Renamed root package from `uk.co.jamesj999.sonic` to `com.openggf` across the entire codebase.
  All source files, test files, and references updated.

### Profile-Driven Level Loading

- Introduced `LevelInitProfile` abstraction with `InitStep` and `StaticFixup` primitives for
  declarative, ROM-aligned level loading.
- Implemented per-game profiles (`Sonic1LevelInitProfile`, `Sonic2LevelInitProfile`,
  `Sonic3kLevelInitProfile`) with 13 finer-grained ROM-aligned steps each.
- `LevelLoadContext` provides shared state across load steps.
- `LevelManager.loadLevel()` routed through profile steps; old fallback path removed.
- Per-step timing and logging for load diagnostics.
- Profile-driven teardown and per-test reset replaces `TestEnvironment` and `GameContext.forTesting()`.
- `CHARACTER_APPEAR` phase uses `Map_Sonic`/`Map_Tails` Float2 animation.

### Testability Refactor

- `GameContext` holder with `production()` and `forTesting()` factories for singleton lifecycle.
- `SharedLevel` for reusable level loading across test classes.
- `HeadlessTestFixture` builder pattern for test setup, with 14 test classes converted.
- `TestEnvironment.resetAll()` delegates to `GameContext.forTesting()` for consistent teardown.

### Docs and Planning

- Added release-planning/implementation docs for unified level events, Super Sonic, and AIZ intro work.
- Added cross-game donation fixes design doc and implementation plan.
- Added `docs/CONFIGURATION.md` with full config key reference.
- Expanded disassembly/reference and skill documentation used for parity-driven object/boss implementation workflows.
- Added DEZ boss fixes design and implementation plans.
- Added Sonic 2 credits and ending sequence design and implementation plans.
- Added cross-game Super Sonic design and implementation plan.
- Added S3K water system design and implementation plan.
- Added testability improvement design (GameContext + HeadlessTestFixture) and implementation plan.
- Added headless test level grouping design and implementation plan.
- Added profile-driven level loading plans (Phase 3 and Phase 4).
- Added ending parallax background design and implementation plan.
- Added level editor design and implementation plan.
- Added ROM-driven init profiles design and implementation plan.

## v0.3.20260206

366 commits, 541 files changed, ~99,000 lines added.

### Multi-Game Architecture

- Complete engine refactor to support multiple Sonic games through a provider-based abstraction layer
  - `GameModule` interface defines 15+ provider methods for all game-specific behaviour
  - `GameModuleRegistry` singleton holds the active game module
  - `RomDetectionService` auto-detects ROM type via registered `RomDetector` implementations
- New provider interfaces: `ZoneRegistry`, `ObjectRegistry`, `ObjectArtProvider`, `ZoneArtProvider`,
  `ScrollHandlerProvider`, `ZoneFeatureProvider`, `RomOffsetProvider`, `SpecialStageProvider`,
  `BonusStageProvider`, `DebugModeProvider`, `DebugOverlayProvider`, `TitleCardProvider`,
  `LevelEventProvider`, `ResultsScreen`, `MiniGameProvider`
- `GameServices` facade for centralised access to `gameState()`, `timers()`, `rom()`, `debugOverlay()`
- NoOp implementations for optional providers (`NoOpBonusStageProvider`, `NoOpSpecialStageProvider`, etc.)
- Sonic 2 fully migrated to provider architecture (`Sonic2GameModule` and all provider implementations)
- `Sonic2Constants.java` expanded by 663+ lines of ROM offset constants
- `Sonic2ObjectIds.java` expanded with 118 new object type ID constants

### Tails (Miles Prower) - Playable Character

- `Tails.java` playable sprite: shorter height (30px vs Sonic's 32px), adjusted sensor offsets (±15px vs ±19px), otherwise identical physics
- `TailsCpuController.java` ROM-accurate AI follower with 5-state machine: `INIT`, `NORMAL` (input replay), `FLYING` (helicopter chase), `PANIC` (spindash escape), `SPAWNING` (respawn wait)
- Input replay system: Tails replays Sonic's recorded inputs from 17 frames ago via position/status history buffer
- AI overrides: direction correction when >16px off, forced jumps when Sonic is 32+ pixels above, spindash escape every 128 frames when stuck >120 frames
- Despawn after 300 frames off-screen, respawn 192 pixels above Sonic when safe
- `TailsTailsController.java` (Obj05): separate rotating tails animation with 10 states (Blank, Swish, Flick, Directional, Spindash, Skidding, Pushing, Hanging)
- Art loaded from ROM at `0x64320` (uncompressed, `0xB8C0` bytes) with separate mappings and reversed mappings
- Configurable via `SIDEKICK_CHARACTER_CODE` in config.json: `"tails"` (default), `""` to disable, `"sonic"` for Sonic clone
- Can be spawned as main player character or as CPU-controlled sidekick
- Flying mode bypasses normal physics, using direct position updates for aerial chase
- Per-player riding state: solid object contacts refactored to `IdentityHashMap` so Sonic and Tails can independently ride different platforms (13 files updated)
- Test: `TestTailsCpuController` covering state transitions, input replay, distance gating, despawn/respawn

### Sonic 1 Initial Support (23 new files, 3,729 lines)

- ROM auto-detection via `Sonic1RomDetector` (header-based)
- `Sonic1.java` game entry point with level loading and data decompression from S1 ROM
- `Sonic1Level.java` implementing S1-specific level data format (different structure from S2)
- `Sonic1ZoneRegistry` covering all 7 zones: Green Hill, Marble, Spring Yard, Labyrinth, Star Light, Scrap Brain, Final
- `Sonic1Constants.java` with verified ROM addresses for S1 REV01
- `Sonic1PlayerArt.java` loading player sprites with S1-specific mapping format
- Parallax scroll handlers for all 7 zones (`SwScrlGhz`, `SwScrlMz`, `SwScrlSyz`, `SwScrlLz`, `SwScrlSlz`, `SwScrlSbz`, `SwScrlFz`)
- `Sonic1PatternAnimator` for S1 tile animation scripts (waterfall, flowers, lava, conveyors)
- `Sonic1PaletteCycler` for S1 zone-specific palette cycling
- `Sonic1AudioProfile` and `Sonic1SmpsData` for S1 ROM audio playback via SMPS driver
- `Sonic1ObjectRegistry` and `Sonic1ObjectPlacement` stubs for S1 object format parsing
- `Sonic1LevelSelectManager` (394 lines): 21-item vertical menu with zone/act selection, wrap-around navigation, sound test
- `Sonic1LevelSelectDataLoader` and `Sonic1LevelSelectConstants` for ROM-based graphics and layout
- `LevelSelectProvider` interface extracted for game-agnostic level select support
- `Sonic1TitleCardManager` (468 lines), `Sonic1TitleCardMappings` (306 lines): S1-specific title card rendering
- `Sonic1ObjectArtProvider` for S1 HUD rendering (life icons, ring display)
- Tests: `TestGhzChunkDiagnostic` (GHZ chunk loading), `Sonic1PlayerArtTest` (player sprite loading)

### Physics Engine

#### Core Physics Rewrite
- Complete physics rewrite in `PlayableSpriteMovement` (1,814 lines, replacing 1,134-line predecessor)
- Movement modes now explicitly mirror ROM state machine: `Obj01_MdNormal` (ground walking), `Obj01_MdRoll` (ground rolling), `Obj01_MdAir`/`MdJump` (airborne)
- ROM-accurate slope resistance/repulsion formulas with correct angle offset (0x20) and mask (0xC0)
- Slope repel minimum speed threshold (0x280) matching ROM `Sonic_SlopeRepel`
- Rolling physics: dedicated roll deceleration (0x20), controlled roll constants, minimum start roll speed gating
- Spindash fully reimplemented using ROM speed table (`s2.asm:37294`) indexed by `spindash_counter >> 8`
- Spindash counter charging/decay logic matching ROM `Sonic_UpdateSpindash`
- Fixed subpixel accuracy: subpixels were not being used correctly in velocity/position calculations
- Near-apex air drag implemented (when -1024 <= ySpeed < 0), matching ROM `Sonic_MdJump` behaviour
- Upward velocity cap added at -0xFC0
- Roll height adjustment fixed (5px to 10px) for all roll-mode transitions, preventing visual "fall" on transition
- ROM-identical angle/quadrant selection table in `TrigLookupTable` (256 entries from `misc/angles.bin`)
- `calcAngle()` method exactly matching ROM `CalcAngle` routine (s2.asm:4033-4076)
- Jump angle calculation and slope angle assist/repel gating adjustments

#### Player Mechanics
- Pinball mode flag (`pinballMode`) preventing rolling from being cleared on landing; gives boost instead of stopping at speed 0. Used by CNZ tubes, blue balls, launcher springs. Preserved through launcher spring bounces
- Ledge balance animation with 4 balance states matching ROM (BALANCE through BALANCE4) based on proximity and facing direction (s2.asm:36246-36373)
- Look up/down delay counter (`lookDelayCounter`) matching ROM `Sonic_Look_delay_counter` timing
- Spring control lock fixed to only apply when grounded (was incorrectly locking controls in air)
- Run animation starts the moment left/right are pressed
- Three distinct control lock types matching ROM: `objectControlled` (blocks all input), `moveLocked` (blocks directional but allows jump), `springing` (blocks grounded directional)
- Signpost walk-off fix: control lock no longer cancels forced input, allowing Sonic to properly walk off-screen after act end
- Position history buffer (64 entries) for camera lag and spindash compensation

#### Collision System
- New unified `CollisionSystem` (214 lines) orchestrating a 3-phase pipeline:
  1. Terrain probes (ground/ceiling/wall sensors via `TerrainCollisionManager`)
  2. Solid object resolution (platforms, moving solids via `ObjectManager.SolidContacts`)
  3. Post-resolution adjustments (ground mode, headroom checks)
- Supports trace recording via `CollisionTrace` interface for debugging and testing
- `GroundSensor` rewrite (437 lines): separated vertical scanning (floor/ceiling) from horizontal scanning (walls), ROM-accurate negative metric handling, full-tile edge detection with previous-tile lookback, horizontal wall scanning with regress/extend states
- Collision order fix: solid objects now processed before terrain, preventing objects from being overridden
- Sensor adjustment timing changed to earlier in the tick
- Collision path reset on level switch to prevent falling through levels on wrong layer
- Ceiling collision improvements: better ceiling sensors on walls/ceilings, angle-based landing detection, ceiling mode (0x80) correctly adjusts only Y velocity
- Wall pushing fix
- Solid object landing now resets ground mode and angle (matching solid tile landing)
- New `ObjectTerrainUtils` (296 lines) for game object terrain collision (floor, ceiling, left wall, right wall), mirroring ROM `ObjCheckFloorDist`

### Camera

- Complete vertical scroll rewrite matching ROM behaviour:
  - Y position bias system (`Camera_Y_pos_bias`) with default value of 96
  - Look up bias (200) with gradual 2px/frame increment
  - Look down bias (8) with gradual 2px/frame decrement
  - Bias easing back to default at 2px/frame
  - Grounded scroll speed cap: 2px (looking), 6px (normal), 16px (fast, inertia >= 0x800)
- Airborne camera uses +/-32px window around current bias matching ROM `ScrollVerti` airborne path
- Horizontal scroll delay (`horizScrollDelayFrames`) replaces old `framesBehind` system. Matches ROM where `ScrollHoriz` checks `Horiz_scroll_delay_val` but `ScrollVerti` does not
- Rolling height compensation: camera subtracts 5px from Y delta when rolling (1px for Tails)
- Spindash camera fixed to use horizontal scroll delay rather than full camera freeze
- Screen shake system: `shakeOffsetX`/`shakeOffsetY` with `getXWithShake()`/`getYWithShake()` for rendering (used by HTZ earthquake)
- Boundary clamping to `minX`/`minY` (was only clamping to 0)
- Full freeze (death/cutscenes) now separate from horizontal scroll delay

### Water System

- Complete `WaterSystem` (462 lines): water level loaded from ROM at correct height, water oscillation in CPZ2 via `OscillationManager`, water surface sprites rendering in front of solid tiles
- `WaterSurfaceManager` (282 lines) for surface sprite management
- Water surface sprites appear for CPZ2, ARZ1, and ARZ2
- Water entry/exit detection based on player centre Y vs water surface
- Underwater physics: speed halving on water entry (xSpeed/2, ySpeed/4), halved acceleration/deceleration/max speed, corrected jump height, corrected hurt gravity and launch amount
- `DrowningController` (289 lines): 30-second air timer with frame-accurate countdown, warning chimes at air levels 25/20/15, drowning countdown music at air level 12, countdown number bubbles (5/4/3/2/1/0), breathing bubble spawning, music restart on water exit or air replenishment, air bubble collection with 35-frame control lock
- Water collision aligned with oscillating visual position in CPZ2
- HUD text no longer turns red on water levels
- Special Stage results no longer overwrite water surface sprite

### Boss Fights

#### Boss Framework (game-agnostic)
- `AbstractBossInstance` (530 lines) base class: hit points, invincibility frames, state machine, defeat sequences, camera locking, explosion cascades
- `AbstractBossChild` (109 lines) base class for multi-component boss sub-objects
- `BossChildComponent` interface (45 lines) and `BossStateContext` (72 lines) for shared state
- `BossExplosionObjectInstance` for shared boss explosion effects
- `CameraBounds` for boss arena camera locking

#### Implemented Bosses
- **EHZ Boss** (Drill Car, Obj56) - `Sonic2EHZBossInstance` with 6 child components: ground vehicle, propeller, spike drill, vehicle top, wheels, animations helper
- **CPZ Boss** (Water Dropper, Obj5D) - `Sonic2CPZBossInstance` with 14 child components: container (extend, floor), dripper, falling parts, flame, gunk hazard, pipes (pump, segment), pump, Robotnik sprite, smoke puffs, animations helper
- **HTZ Boss** (Lava Flamethrower, Obj52) - `Sonic2HTZBossInstance` with flamethrower, lava ball projectiles, smoke particles. Lava bubble spawned on ground impact
- **CNZ Boss** (Electricity, Obj51) - `Sonic2CNZBossInstance` with electric ball projectiles, animations helper
- **ARZ Boss** (Hammer/Arrow, Obj89) - `Sonic2ARZBossInstance` with arrow projectiles, eye tracking component, destructible pillars
- **Egg Prison** (Obj3E) - End-of-act capsule with button, animal escape sequence, and destruction

### Badniks (15+ New Enemies)

#### CPZ
- **Spiny** (Obj A5) - Wall-crawling spike enemy
- **Spiny on Wall** (Obj A6) - Ceiling variant
- **Grabber** (Obj A7) - Descends to capture player

#### ARZ
- **ChopChop** (Obj 91) - Piranha fish that lunges at player
- **Whisp** (Obj 8C) - Floating dragonfly enemy
- **Grounder** (Obj 8D/8E) - Mole that hides behind breakable wall, throws rock projectiles
  - GrounderWallInstance (Obj 8F) - Breakable wall
  - GrounderRockProjectile (Obj 90) - Rock projectiles

#### HTZ
- **Rexon** (Obj 94/96) - Multi-segment lava-dwelling serpent
  - RexonHeadObjectInstance (Obj 97) - Shootable head segment
- **Sol** (Obj 95) - Fireball-shooting enemy with SolFireballObjectInstance
- **Spiker** (Obj 92) - Drill badnik with SpikerDrillObjectInstance (Obj 93) projectile

#### CNZ
- **Crawl** (Obj C8) - Bouncing boxing glove enemy

#### MCZ
- **Crawlton** (Obj 9E) - Snake that lunges with trailing body segments
- **Flasher** (Obj A3) - Firefly that flashes invulnerability

#### Badnik Framework
- Enhanced `AbstractBadnikInstance` base class
- Improved `AnimalObjectInstance` escape behaviour
- Enhanced `BadnikProjectileInstance` framework
- `PointsObjectInstance` moved to objects package (score popup display)

### Game Objects (50+ New)

#### Platforms and Moving Objects
- **SwingingPlatformObjectInstance** (Obj15) - Chain-suspended pendulum platform (OOZ, ARZ, MCZ)
- **SwingingPformObjectInstance** (Obj82) - ARZ swinging vine platform
- **CPZPlatformObjectInstance** (Obj19) - CPZ rotating/moving platforms
- **ARZPlatformObjectInstance** (Obj18) - ARZ-specific platform
- **MTZPlatformObjectInstance** (Obj6B) - Multi-purpose platform with 12 movement subtypes
- **SidewaysPformObjectInstance** (Obj7A) - CPZ/MCZ horizontal moving platform
- **MCZRotPformsObjectInstance** (Obj6A) - MCZ wooden crate / MTZ rotating platforms
- **ARZRotPformsObjectInstance** (Obj83) - 3 platforms orbiting centre
- **CollapsingPlatformObjectInstance** (Obj1F) - OOZ/MCZ/ARZ collapsing platform
- **SeesawObjectInstance** (Obj14) + **SeesawBallObjectInstance** - HTZ catapult seesaw with ball physics
- **HTZLiftObjectInstance** (Obj16) - HTZ zipline/diagonal lift
- **ElevatorObjectInstance** (ObjD5) - CNZ vertical moving elevator
- **CNZBigBlockObjectInstance** (ObjD4) - CNZ 64x64 oscillating platform
- **CNZRectBlocksObjectInstance** (ObjD2) - CNZ flashing "caterpillar" blocks
- **InvisibleBlockObjectInstance** (Obj74) - Invisible solid block

#### Hazards and Traps
- **RisingLavaObjectInstance** (Obj30) - HTZ invisible solid lava platform during earthquakes
- **LavaMarkerObjectInstance** (Obj31) - HTZ/MTZ invisible lava hazard collision zone
- **LavaBubbleObjectInstance** (Obj20) - Lava bubble visual effects
- **SmashableGroundObjectInstance** (Obj2F) - HTZ breakable rock platform
- **FallingPillarObjectInstance** (Obj23) - ARZ pillar that drops lower section
- **RisingPillarObjectInstance** (Obj2B) - ARZ pillar that rises and launches player
- **ArrowShooterObjectInstance** (Obj22) + **ArrowProjectileInstance** - ARZ arrow shooter trap
- **StomperObjectInstance** (Obj2A) - MCZ ceiling crusher
- **MCZBrickObjectInstance** (Obj75) - MCZ pushable/breakable brick
- **SlidingSpikesObjectInstance** (Obj76) - MCZ spike block sliding from wall
- **TippingFloorObjectInstance** (Obj0B) - CPZ tipping floor
- **BreakableBlockObjectInstance** (Obj32) - CPZ metal blocks / HTZ breakable rocks
- **BlueBallsObjectInstance** (Obj1D) - CPZ chemical droplet hazard
- **BombPrizeObjectInstance** (ObjD3) - CNZ slot machine bomb/spike penalty

#### Interactive Objects
- **SpringboardObjectInstance** (Obj40) - Pressure/lever spring (CPZ, ARZ, MCZ)
- **SpringHelper** - Shared spring velocity calculations
- **SpeedBoosterObjectInstance** (Obj1B) - CPZ/CNZ speed booster pad
- **ForcedSpinObjectInstance** (Obj84) - CNZ/HTZ forced spin (pinball mode trigger)
- **LauncherSpringObjectInstance** (Obj85) - CNZ pressure launcher spring
- **PipeExitSpringObjectInstance** (Obj7B) - CPZ warp tube exit spring
- **BarrierObjectInstance** (Obj2D) - One-way rising barrier (CPZ/HTZ/MTZ/ARZ/DEZ)
- **EggPrisonObjectInstance** (Obj3E) - End-of-act capsule with button, animal escape, destruction
- **SkidDustObjectInstance** - Skid dust particles
- **SplashObjectInstance** - Water splash effect

#### CPZ-Specific Objects
- **CPZSpinTubeObjectInstance** (Obj1E, 895 lines) - Full tube transport system
- **CPZStaircaseObjectInstance** (Obj78) - 4-piece triggered elevator platform
- **CPZPylonObjectInstance** (Obj7C) - Decorative background pylon

#### CNZ-Specific Objects
- **BumperObjectInstance** (Obj44) - Standard round bumper
- **HexBumperObjectInstance** (ObjD7) - Hexagonal bumper
- **BonusBlockObjectInstance** (ObjD8) - Drop target / bonus block (colour-changing, scoring)
- **FlipperObjectInstance** (Obj86) - Pinball flipper
- **CNZConveyorBeltObjectInstance** (Obj72) - Invisible velocity conveyor zone
- **PointPokeyObjectInstance** (ObjD6) - Cage that captures player and awards points
- **RingPrizeObjectInstance** (ObjDC) - Slot machine ring reward
- **CNZBumperManager** (574 lines) - Full bumper system with ROM-accurate bounce physics, 6 bumper types
- **CNZSlotMachineManager** (608 lines) + **CNZSlotMachineRenderer** (549 lines) - Complete slot machine system

#### ARZ-Specific Objects
- **BubbleGeneratorObjectInstance** (Obj24) - Spawns breathable bubbles underwater
- **BubbleObjectInstance** / **BreathingBubbleInstance** - Rising and breathable air bubbles
- **LeavesGeneratorObjectInstance** (Obj2C) + **LeafParticleObjectInstance** - Falling leaves on contact

#### MCZ-Specific Objects
- **VineSwitchObjectInstance** (Obj7F) - Pull switch triggering ButtonVine
- **MovingVineObjectInstance** (Obj80) - Vine pulley transport
- **MCZDrawbridgeObjectInstance** (Obj81) - Rotatable drawbridge triggered by VineSwitch
- **ButtonVineTriggerManager** - MCZ-specific vine routing

### Zone Improvements

#### Emerald Hill Zone (EHZ)
- Full boss fight (Act 2) with multi-component child objects
- Art used as base for HTZ overlay system

#### Chemical Plant Zone (CPZ)
- Full water implementation with oscillation in CPZ2
- Cycling palette implementation (water shimmer, chemical bubbles)
- Spin tubes, staircase platforms, blue balls, speed boosters, breakable blocks
- Spiny, Grabber, and Crawl badniks
- Full boss fight with multi-component gunk dropper
- Multiple collision and positioning fixes (tubes, staircases, platforms, blue balls)

#### Aquatic Ruin Zone (ARZ)
- Water surface sprites for ARZ1 and ARZ2
- Arrow shooters, swinging platforms, rotating platforms, rising/falling pillars
- ChopChop, Whisp, and Grounder badniks
- Leaves generator and leaf particle objects
- Full boss fight with hammer, arrows, and destructible pillars
- Collision fix: boss no longer attackable from the floor

#### Casino Night Zone (CNZ)
- Full bumper system with 6 bumper types and ROM-accurate bounce physics
- Complete slot machine system with shader rendering
- Flipper system (multiple rounds of fixes)
- New parallax scroll handler
- Conveyor belts, elevators, big blocks, rect blocks, point pokey, bonus blocks
- Crawl badnik
- Full boss fight with electric balls
- Physics fix: slopes no longer get stuck

#### Hill Top Zone (HTZ)
- Level resource overlay system: loads EHZ base data with HTZ-specific pattern overlays at byte offset 0x3F80 and block overlays at 0x0980. Shared chunks and collision indices
- Full earthquake system: dual architecture with `Camera_BG_Y_offset` (224-320) for BG vertical scroll and `SwScrl_RippleData` (0-3px) for screen jitter
- Earthquake trigger coordinates: Act 1 camera X >= 0x1800, Y >= 0x400; Act 2 camera X >= 0x14C0
- Rising lava with invisible solid platform, lava markers, lava bubble effects
- HTZ dynamic art loaded from ROM instead of disassembly files
- New parallax scroll handler with correct BG rendering
- Seesaws with ball physics, smashable ground, lifts, launcher springs, barriers
- Rexon, Sol, and Spiker badniks
- Full boss fight with lava flamethrower

#### Mystic Cave Zone (MCZ)
- Crawlton and Flasher badniks
- Bricks, drawbridges, vine switches, moving vines, rotating platforms, stompers, sliding spikes

#### Sky Chase Zone (SCZ)
- `SwScrlScz` (207 lines): ROM-accurate scroll handler with Tornado-driven camera movement
- BG X advances at 0.5px/frame via 16.16 fixed-point accumulator, BG Y always 0
- Act 1 phase system: fly right → descend → resume right, triggered by camera position thresholds

#### Oil Ocean Zone (OOZ)
- Full multi-layer parallax background with oil surface effects (`SwScrlOoz`, 395 lines)

#### Metropolis Zone (MTZ)
- MTZ platform with 12 movement subtypes

#### General Level System
- `LevelEventManager` massively expanded (+1,026 lines): dynamic camera boundaries, boss arenas, zone-specific event triggers, HTZ earthquake coordination
- `OscillationManager` extracted into proper abstraction (drives water oscillation, platform cycles)
- `ParallaxManager` expanded (+249 lines) with enhanced scroll offset calculations
- `BackgroundRenderer` reworked (+324 lines)
- Palette cycling system rewritten: `Sonic2PaletteCycler` (578 lines) with per-zone scripts from ROM
- Tile animation system rewritten: `Sonic2PatternAnimator` (343 lines) with ROM-based scripts
- `Sonic2LevelAnimationManager` consolidating both pattern animation and palette cycling

### Level Resource Overlay System (New)

- `LevelResourcePlan` (221 lines) - Declarative resource loading with overlay composition
- `LoadOp` (49 lines) - Individual load operations with ROM address, compression type, destination offset
- `ResourceLoader` (175 lines) - Performs loading with copy-on-write overlay pattern
- `CompressionType` enum - Nemesis, Kosinski, Enigma, Saxman, Uncompressed
- `Sonic2LevelResourcePlans` (108 lines) - Factory for zone-specific resource plans
- Overlays never mutate cached data (copy-on-write pattern)
- Tests: `LevelResourceOverlayTest` (333 lines)

### Graphics and Rendering

#### Backend Migration
- Complete migration from JOGL to LWJGL for both graphics and audio backends (multiple commits)
- GLFW window management replaces previous windowing system
- Initially tried OpenGL 4.1 core profile, settled on OpenGL 2.1 compatibility profile for broader hardware support
- Fixed shader loading when packaged as JAR
- DPI-aware window scaling via `GLFW_SCALE_TO_MONITOR`

#### GPU Rendering Pipeline
- **Pattern atlas system**: all 8x8 tile patterns uploaded to a single GPU texture (`PatternAtlas`, 326 lines) with multi-atlas fallback and buffer pooling
- **GPU tilemap renderer** (`TilemapGpuRenderer`, 198 lines): dedicated `TilemapShaderProgram` (172 lines) and `TilemapTexture` (78 lines) for GPU-side tile lookup. Covers background, water, and foreground layers. Configurable fallback to CPU rendering
- **Instanced sprite batching** (`InstancedPatternRenderer`, 696 lines): per-instance attributes with `glDrawArraysInstanced`. Enabled by default when supported, automatic fallback to existing batcher. Includes instanced water shader sync
- **Shared fullscreen quad VBO** (`QuadRenderer`, 50 lines): replaces all immediate-mode fullscreen quads across tilemap, parallax, fade, and special stage renderers
- **Priority rendering** (`TilePriorityFBO`, 177 lines): framebuffer object for tile priority bit rendering, enabling correct sprite-behind-tile ordering via GPU
- **Pattern lookup buffer** (`PatternLookupBuffer`, 70 lines): GPU-side pattern index lookup for tilemap shader

#### New Shaders
- `shader_tilemap.glsl` (132 lines) - GPU tilemap lookup and rendering
- `shader_water.glsl` (105 lines) - Water surface effects with palette-based tinting
- `shader_instanced.vert` (27 lines) - Instanced sprite vertex shader
- `shader_instanced_priority.glsl` (93 lines) - Instanced rendering with priority bit
- `shader_sprite_priority.glsl` (91 lines) - Sprite-behind-tile priority rendering
- `shader_cnz_slots.glsl` (131 lines) - CNZ slot machine display
- `shader_debug_text.frag`/`shader_debug_text.vert` (69 lines) - Debug text glyph rendering
- `shader_debug_color.vert`, `shader_basic.vert`, `shader_fullscreen.vert` - Utility shaders

#### UI Render Pipeline
- `UiRenderPipeline` (104 lines): ordered rendering phases (Scene, HUD Overlay, Fade pass)
- `RenderPhase` enum, `RenderCommand` interface, `RenderOrderRecorder` for testing

#### Debug Overlay Rendering
- Batched glyph rendering using GPU-accelerated glyph atlas texture
- Multi-size fonts with smooth anti-aliased outlines
- Proper viewport-space projection and DPI scaling
- Crisp texture filtering, correct Y-flip orientation
- Glyph atlas size increased to 1024x1024
- Bold font for SMALL and MEDIUM debug text sizes, capped at 32pt maximum
- `DebugPrimitiveRenderer` (72 lines) with `DebugColorShaderProgram` for collision/sensor overlays
- Collision overlay accessible via backtick key

#### Other Rendering Changes
- `FadeManager` rewritten for LWJGL compatibility
- `ScreenshotCapture` (231 lines) for visual regression testing
- Slot machine rendering moved from CPU to shader
- VBO sprite rendering

### Audio Engine

#### YM2612 FM Synthesis
- Complete rewrite based on Genesis-Plus-GX (GPGX) reference: SIN_HBITS/ENV_HBITS changed from 12 to 10, LFO changed from 1024-step sine to 128-step inverted triangle, TL table restructured, output clipping changed to asymmetric GPGX-style (+8191/-8192)
- `ENV_QUIET` threshold: when envelope exceeds threshold, operator output forced to 0, causing feedback buffer to naturally decay (matching real hardware)
- SSG-EG (SSG envelope generator) support
- Phase generator detune overflow matching Nemesis-verified real hardware behaviour (DT_BITS = 17)
- Internal sample rate output: YM2612 can output at CLOCK/144 (~53267 Hz) with proper band-limited resampling via `BlipDeltaBuffer` (330 lines) and `BlipResampler` (200 lines)
- Fixed operator routing order and TL position in voice format
- Fixed voice format parsing that was causing corruption and muted instruments
- Fixed low output volume
- Multiple rounds of accuracy improvements (5+ commits)

#### PSG (SN76489)
- New Experimental (Off by Default) PSG implementation with anti-aliasing
- `PsgChipGPGX` (378 lines) added as alternate implementation based on Genesis-Plus-GX (reference for future noise channel work)
- Clock divider fixed to 32.0, Noise Mode 3 corrected
- Clock speed and period calculation fixed
- Extensive spindash release SFX fixes (PSG modulation, note-off, tone bleed, noise channel)
- Default to original PSG after noise channel issues, keeping GPGX as reference

#### SMPS Driver
- Fixed frequency wrapping for high notes
- Fixed E7 command handling
- Fixed octave shifts during modulation/detune
- Fixed fill/gate time logic causing audio desync
- Fixed missing noise channel
- Refactored driver locking to prevent concurrent modification between SFX and music
- `SmpsSequencerConfig` abstraction: configurable per-game (Sonic 1 vs Sonic 2 differences in instrument loading, pitch offsets, noise channel handling)
- Sonic 1 SMPS driver accuracy fixes:
  - PSG envelope 1-based indexing: S1 `subq.w #1,d0` before table lookup; VoiceIndex=0 means no envelope
  - FM voice operator order conversion: S1 (Op4,Op3,Op2,Op1) swapped to engine's S2 format (Op4,Op2,Op3,Op1) on load
  - PC-relative pointer addressing: S1 F6/F7/F8 commands use `dc.w loc-*-1` offsets vs S2 absolute Z80 addresses
  - TIMEOUT tempo mode: S1 uses countdown-based tempo (extend durations on wrap) vs S2 accumulator overflow
  - PSG base note: S1 PSGSetFreq subtracts 0x81 (table starts at C), so `getPsgBaseNoteOffset()` returns 0
  - SFX tempo bypass: S1 SFX have normalTempo=0; skip duration extension in TIMEOUT mode when sfxMode=true
  - First-frame tempo processing matching S1 DOTEMPO behaviour

#### Sound Effects and Music
- Fixed extra life music restore (multiple playbacks no longer break original music)
- Fixed SFX-over-music priority and channel management
- Spindash release SFX: extensive multi-commit effort (14+ commits) fixing looping, noise timing, tone bleed, modulation enable, artifact prevention, overlapping playback. Invalid FM transpose value patched
- Level select: music fade on transitions, ring sound removed, double-fade fixed
- Gloop sound toggle moved from Z80 driver to `BlueBallsObjectInstance`

#### Audio Backend
- Migrated from JOAL to LWJGL OpenAL (`LWJGLAudioBackend`, 427 lines). Includes `WavDecoder` for WAV file support
- Fixed audio quality degradation in LWJGL backend
- Audio latency reduced to 16ms (one frame)
- Window minimize/restore handling: pauses audio so music doesn't play in background

#### Audio Performance
- Eliminated per-sample allocations in VirtualSynthesizer, SmpsSequencer, SmpsDriver scratch buffers
- Audio engine performance optimisations verified via regression tests

### Manager Consolidation Refactor

#### ObjectManager
- `ObjectManager.java` grew from ~200 to ~1,917 lines, absorbing 4 removed managers as inner classes:
  - `ObjectManager.Placement` (was `ObjectPlacementManager`) - Spawn windowing, remembered objects
  - `ObjectManager.SolidContacts` (was `SolidObjectManager`, -455 lines) - Riding, landing, ceiling, side collision
  - `ObjectManager.TouchResponses` (was `TouchResponseManager`, -195 lines) - Enemy bounce, hurt, category detection
  - `ObjectManager.PlaneSwitchers` (was `PlaneSwitcherManager`, -143 lines) - Plane switching logic

#### RingManager
- Consolidated from 3 separate managers as inner classes:
  - `RingManager.RingPlacement` (was `RingPlacementManager`, -93 lines) - Collection state, sparkle animation
  - `RingManager.RingRenderer` (was `RingRenderManager`, -114 lines) - Ring rendering with cached patterns
  - `RingManager.LostRingPool` (was `LostRingManager`, -304 lines) - Lost ring physics, object pooling

#### PlayableSprite Controller
- `PlayableSpriteController` (38 lines) coordinator owned by `AbstractPlayableSprite`:
  - `PlayableSpriteMovement` (1,814 lines, replaces `PlayableSpriteMovementManager`)
  - `PlayableSpriteAnimation` (renamed from `PlayableSpriteAnimationManager`)
  - `SpindashDustController` (renamed from `SpindashDustManager`)
  - `DrowningController` (289 lines, new)
- Removed `SpriteCollisionManager` (-131 lines)

#### CollisionSystem
- `CollisionSystem` (214 lines) unifying terrain probes and solid object collision
- `CollisionTrace` (40 lines), `RecordingCollisionTrace` (121 lines), `NoOpCollisionTrace` (25 lines), `CollisionEvent` (44 lines)

#### Animation System
- `Sonic2LevelAnimationManager` consolidating `AnimatedPatternManager` and `AnimatedPaletteManager`
- `Sonic2PatternAnimator` renamed from `Sonic2AnimatedPatternManager`
- `Sonic2PaletteCycler` (578 lines) replacing `Sonic2PaletteCycleManager` (-143 lines)

### Object System Framework

- `ObjectArtKeys` - Game-agnostic art key constants
- `MultiPieceSolidProvider` interface for objects with multiple solid collision pieces
- `SlopedSolidProvider` interface for sloped solid objects
- Enhanced `AbstractObjectInstance` (+55 lines), `ObjectInstance` interface (+34 lines)
- `ObjectRenderManager` significantly enhanced rendering pipeline
- `ObjectArtData` enhanced art loading (+169 lines)
- `HudRenderManager` enhanced HUD rendering (+155 lines)
- `SolidObjectProvider` extended interface
- Game-specific art loading pattern: `Sonic2ObjectArt`, `Sonic2ObjectArtProvider`, `Sonic2ObjectArtKeys`
- LayerSwitcher (Obj03) handled by PlaneSwitchers subsystem, not as rendered object

### Level Select

- `LevelSelectManager` (762 lines) - Full level select screen with keyboard navigation, zone/act selection, music playback
- `LevelSelectDataLoader` (485 lines) - Loads graphics, fonts, and preview images from ROM
- `LevelSelectConstants` (240 lines) - ROM addresses and layout data
- Palette loaded from ROM
- Menu background: `MenuBackgroundAnimator`, `MenuBackgroundDataLoader`, `MenuBackgroundRenderer` (292 lines total)
- Sound test integration via shared `Sonic2SoundTestCatalog`
- Configurable via `LEVEL_SELECT_ENABLED` config key
- Palette reset on returning to level select from gameplay
- Music fade on level select transitions

### Testing Infrastructure

#### HeadlessTestRunner
- `HeadlessTestRunner` (137 lines): physics/collision integration tests without OpenGL context
- `stepFrame(up, down, left, right, jump)` to simulate one frame with input
- `stepIdleFrames(n)` for stepping multiple idle frames
- Calls `Camera.updatePosition()`, `LevelEventManager.update()`, `ParallaxManager.update()` each frame

#### Physics and Collision Tests
- `TestHeadlessWallCollision` (133 lines) - Ground collision and walking physics
- `TestPlayableSpriteMovement` (1,483 lines) - Comprehensive movement physics tests
- `CollisionSystemTest` (460 lines) - Unified collision pipeline
- `WaterPhysicsTest` (250 lines) - Underwater physics
- `WaterSystemTest` (178 lines) - Water level system

#### Zone-Specific Tests
- `TestCNZCeilingStateExit` (403 lines), `TestCNZFlipperLaunch` (190 lines), `TestCNZForcedSpinTunnel` (193 lines), `SwScrlCnzTest` (454 lines)
- `TestHTZBossArtPalette`, `TestHTZBossChildObjects` (181 lines), `TestHTZBossEventRoutine9`, `TestHTZBossTouchResponse` (133 lines)
- `TestHTZInvisibleWallBug` (731 lines), `TestHTZRisingLavaDisassemblyParity` (115 lines), `TestSwScrlHtzEarthquakeMode` (86 lines), `TestHtzSpringLoop` (197 lines)
- `SwScrlOozTest` (487 lines), `TestOozAnimation` (248 lines), `TestPaletteCycling` (101 lines)

#### Visual Regression Tests
- `VisualRegressionTest` (393 lines) - Screenshot comparison testing
- `VisualReferenceGenerator` (265 lines) - Generate reference screenshots
- `ScreenshotCapture` (231 lines) - Headless screenshot capture
- Reference images for EHZ, CPZ, CNZ, HTZ, MCZ

#### Audio Regression Tests
- `AudioRegressionTest` (370 lines) - Audio output comparison
- `AudioReferenceGenerator` (302 lines) - Generate reference audio
- `AudioBenchmark` (109 lines) - Audio performance benchmarks
- Reference WAVs for EHZ/CPZ/HTZ music, jump/ring/spring/spindash SFX
- `TestSmpsSequencerInstrumentLoading` - SMPS instrument loading verification

#### Other Tests
- `TestSignpostWalkOff` - Signpost walk-off regression test
- `TestTailsCpuController` - Tails AI state machine and input replay
- `TestObjectManagerLifecycle` (108 lines), `TestObjectPlacementManager` (40 lines), `TestSolidObjectManager` (148 lines)
- `BossStateContextTest` (220 lines), `FadeManagerTest` (535 lines), `RenderOrderTest` (181 lines)
- `PatternAtlasFallbackTest` (33 lines), `TestSpriteManagerRender` (211 lines)
- `LevelResourceOverlayTest` (333 lines)

#### Test Annotation Framework
- `@RequiresRom(SonicGame.SONIC_1)` annotation for tests needing a real ROM file
- `@RequiresGameModule(SonicGame.SONIC_1)` annotation for tests needing a game module without ROM
- `RequiresRomRule` JUnit rule with per-game ROM resolution and auto-detection
- `SonicGame` enum: `SONIC_1`, `SONIC_2`, `SONIC_3K`
- `RomCache` for shared ROM instances across test classes

### Performance Optimisations

- Pre-allocated command lists in `LevelManager` (collisionCommands, sensorCommands, cameraBoundsCommands) using `.clear()` instead of `new ArrayList<>()` each frame
- ObjectManager and SpriteManager pre-bucketing with dirty flag to avoid re-sorting every frame
- `Sonic2SpecialStageRenderer` PatternDesc reuse instead of per-frame allocation
- Lost ring object pooling to reduce allocations during ring scatter
- Reduced per-frame allocations in collision, rendering, and audio hot paths
- Debug overlay buffer reuse
- Per-sample allocation elimination in audio synthesis pipeline
- `PerformanceProfiler` with memory stats (GC and allocation timers), Ctrl+P copies all stats to clipboard
- General memory allocation reduction passes across the engine

### GraalVM Native Build Support

- GraalVM Native Image plugin configuration in `pom.xml`
- GitHub Actions release workflow (`.github/workflows/release.yml`, 128 lines) and CI workflow
- GraalVM configuration files: `native-image.properties`, `reflect-config.json`, `resource-config.json`, `jni-config.json`
- LWJGL migration (both graphics and audio) as prerequisite for GraalVM compatibility
- `run.cmd` for Windows execution

### Tooling

#### RomOffsetFinder Enhancements
- `verify <label>` command - Verifies calculated offset against actual ROM data
- `verify-batch [type]` command - Batch verify all items of a type (shows [OK], [!!] mismatch, [??] not found)
- `export <type> [prefix]` command - Export verified offsets as Java constants
- Offset validation for searched items
- Multi-game support via `--game` flag: `--game s1`, `--game s2` (default), `--game s3k`
- `GameProfile` with per-game anchor offsets, label prefixes, ROM filenames, and disasm paths
- Auto-detection from disassembly path (`s1disasm` → S1, `skdisasm` → S3K)
- Expanded anchor offsets in `RomOffsetCalculator` for improved accuracy
- `CompressionTestTool` auto-detect compression type at offset
- Kosinski Moduled (KosM) decompression support in `KosinskiReader.decompressModuled()` — container format wrapping multiple standard Kosinski modules with 16-byte aligned padding, used extensively by Sonic 3&K art assets
- Palette macro parsing support from disassembly

#### New Tools
- `WaterHeightFinder` (114 lines) - Finds water height data in ROM
- `AudioSfxExporter` (261 lines) - Exports SFX audio data
- `SoundTestApp` refactored to use shared `Sonic2SoundTestCatalog` with channel mute/solo support

#### Claude Skills
- `.claude/skills/implement-object/SKILL.md` (410 lines) - Guided object implementation workflow
- `.claude/skills/implement-boss/skill.md` (332 lines) - Boss implementation workflow
- `.claude/skills/s2disasm-guide/skill.md` (302 lines) - Disassembly reference guide

### Other Changes

- Per-game ROM configuration: `SONIC_1_ROM`, `SONIC_2_ROM`, `SONIC_3K_ROM` config keys with `DEFAULT_ROM` selector; `ROM_FILENAME` removed entirely
- Pause functionality (default key: Enter) and frame step when paused (default key: Q)
- `ConfigMigrationService` (95 lines) for evolving configuration format
- `GameStateManager` (104 lines) for score, lives, emeralds state management
- Window minimize/restore: pauses audio and rendering to prevent background playback and catch-up
- `docs/KNOWN_DISCREPANCIES.md` (161 lines) documenting intentional divergences from original ROM
- Old markdown docs archived to `docs/archive/`
- `AGENTS.md` and `CLAUDE.md` extensively updated with architecture documentation

---

## v0.2.20260117

Improvements and fixes across the board. Special stages are now implemented, feature complete with
a few known issues. Physics have been improved, parallax backgrounds implemented and complete for
EHZ, CPZ, ARZ and MCZ. Some sound improvements, title cards, level 'outros' etc.

## v0.1.20260110

Now vaguely resembles the actual Sonic 2 game. Real collision and graphics data is loaded from the
Sonic 2 ROM and rendered on screen. The majority of the physics are in place, although it is far
from perfect. A system for loading game objects has been created, along with an implementation for
most of the objects and Badniks in Emerald Hill Zone. Rings are implemented, life and score tracking
is implemented. SoundFX and music are implemented. Everything has room for improvement, but this
now resembles a playable game.

## V0.05 (2015-04-09)

Little more than a tech demo. Sonic is able to run and jump and collide with terrain in a reasonably
correct way. No graphics have yet been implemented so it's a moving white box on a black background.

## V0.01 (Pre-Alpha) (Unreleased; first documented 2013-05-22)

A moving black box. This version will be complete when we have an unskinned box that can traverse
terrain in the same way Sonic would in the original game.
