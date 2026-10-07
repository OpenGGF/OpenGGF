# Three Openings: isolated game host walkthrough

Build and launch the maintained [Three Openings example](../../../examples/three-openings/README.md).
This is an engine tool, outside creator callbacks and public Mod API registration.
The process boundary provides the initial Game Container: each worker owns one
console, world, mode, renderer, synthesis stream and native poll baseline. The
host owns its window/controller, tuple barrier, composition and single speaker
sink. No active singleton root is swapped between games.

## Human walkthrough

1. Supply the three exact ROM revisions and open the title.
2. Read the input legend; prepare all three. Validation precedes worker launch.
3. At **All Three Ready**, begin the countdown. Worker gameplay/audio clocks
   remain at preparation state until the group starts.
4. Move and jump. The same held directions and distinct A/B/C/Start reach every
   game; each native poll derives its own edges. Watch independent movement,
   title cards, intro, rings, damage and respawns.
5. Select audio focus with Tab or 1/2/3. All worlds continue; focus crossfades
   only device output, with ROM-backed menu feedback.
6. Pause with P, restart with R, and exit to title with Escape. Repeat launch and
   exit; every restart has a new generation and a fresh production boot.

## Agent walkthrough and valid adaptation

Read `ChallengeHost`, `ProcessGameEndpoint`, `ChallengeProtocol`,
`WorkerGameSession` and `ExclusiveLiveGameDriver` before changing ownership.
Author a held-input file, then run the native GPU/PCM probe. Inspect selected
`state.csv` rows and PNGs together. Compare full per-tick RGBA and pre-focus PCM;
CPU positions alone miss palette publication and synthesis leakage.

The fixed prototype roster is Sonic in S1/S2 and Sonic with CPU Tails in S3K,
320×224, native movement, NTSC 60 Hz, REALISTIC live loading, native AIZ intro,
no donor or creator package. `ChallengeHost.Member` is an internal engine record;
a diagnostic may declare one to three identity-pinned members, including two of
the same game. Names are unique stable member IDs; duplicate IDs, unsupported
symbols, wrong ROM revisions and stale media are rejected.

For example, offer `RIGHT+C` at challenge tick 42. If member A polls and member B
has native non-polling lag, A commits the C edge and B retains its earlier poll
baseline. Offer the same held signal at 43: A has no new edge; B derives C's edge
when it next polls. Challenge ordinals never replace V-int or level clocks. Native
pause does poll; host pause simply stops admission. No frame-index condition
sets player coordinates, readiness or progression.

Adapt the source-held program and labels first. New routes/characters/viewports
need real production boot/transition support, explicit end/fault outcomes and
coverage under the [level test standard](../../guide/contributing/level-test-standard.md).
Do not turn the native end of a program into a successful challenge result. A
read-only completion policy and a program completing all three acts belong to
the later MVP; this prototype publishes movement evidence only.

## Trust, transport and lifecycle

The worker launch command is an engine whitelist, never a shell or mod-supplied
executable. Each worker receives only a validated game/ROM identity and generation.
Its owned temporary directory is below the current tree's `target/`; linked user
configuration and stock saves are outside its launch surface. Classpath entries
are absolute before changing cwd. JVM environment injection variables are removed.

Protocol v1 carries START/STEP/CLOSE, generation, ordinal, one held Genesis byte,
fixed native RGBA, bounded stereo PCM and observational native state. The input
channel has no state hydration, readiness schedule, arbitrary config/class or
creator code. One request per endpoint may be in flight. No complete tuple is
published until every member acknowledges the correct generation/sequence.
Stale packets are checked again before host GPU upload.

Preparation validates every ROM, creates every worker and collects all ready
frames before start. No partial run becomes playable. Fault/cancel closes
survivors; normal close asks for shutdown, then uses bounded termination if
needed, closes streams/executors and deletes owned generated directories only
after process death. Parent JVM shutdown closes all acquired workers. App/OS
crash recovery and cross-platform native executables remain unqualified.

A future in-process backend may implement the endpoint concepts after actual
ownership migration. It must preserve exclusive step/input, clocks, complete
native render targets, pre-focus synthesis, generation rejection and hostile
same-game tests. The old guarded `GameRuntime` and active-root swapping remain
rejected; three SessionManager opens destroy predecessors.

## Supported prototype and troubleshooting

Initial support is Java 21 on the observed Linux desktop with OpenGL 3.3 and a
48 kHz OpenAL output device. Standard GLFW pads and keyboard are supported;
controller-only Escape and arbitrary rebinding are not implemented. Other OSes,
headless-only deployment and native-image packaging require separate evidence.

Rewind/checkpoint export, all-act completion, full campaigns, endings, records,
rankings, replay resume, donor rosters and arbitrary creator worker packages are
later stages. There is no durable stock-save promise. Assessed play is the native
opening level/title/continue modes; an unassessed special/bonus/ending branch
stops with a recoverable worker fault instead of a wrong picture.

- **Missing ROM / revision notice:** use absolute original paths and match SHA-1
  from AGENTS. The tool does not rename/copy/link ROMs for you.
- **Display failure:** launch in the same desktop display environment used by the
  main engine. Tool preflight checks Java/Lua/PowerShell, not GPU access.
- **48 kHz notice:** select a 48 kHz output device. This prototype refuses a
  mismatched device rate instead of silently resampling the worker contract.
- **Slow presentation:** observe per-tuple timings and worker RSS in probe
  `budget.txt`. The host keeps ordered input; it does not fabricate catch-up.
- **One world stops:** the entire run freezes and offers fresh restart. Developer
  stderr records the real error; the player notice stays short.
- **Silent title:** title cues require a valid Sonic 1 ROM; gameplay still requires
  all three. Check `menu.wav` AC variance and actual device output, not queued SFX.

The [validation record](../../architecture/validation/2026-10-07-multigame-prototype.md)
is the authority for measured support, budgets and remaining gaps.
