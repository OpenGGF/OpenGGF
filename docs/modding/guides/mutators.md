# Build a scoped mutator package

[Mutator Lab](../../../examples/example-mutators/README.md) is a runnable JVM
catalogue for Sonic 1, Sonic 2 and Sonic 3 & Knuckles. Use its source and host UI
to build a small configuration-driven policy package. The
[expansion plan](../../architecture/plans/2026-10-08-mutator-lab-expansion.md)
records current verification; installation refresh and universal art coverage from
the [blueprint](../../architecture/designs/2026-10-07-mutators-blueprint.md)
remain separate work.

## Prepare the package

`MutatorsMod.register` contributes independent definitions through
`ModContext.registerMutator`. The engine derives owner keys from that context,
validates the catalog and pins it for a WorldSession. Creator callbacks return
immutable typed policies; they never mutate the world or publish trusted owners.
A semantic support provider advertises native capabilities and per-option/player
availability; unavailable native features remain visible with a reason. The
common screen implements the existing title provider and level input overlay.
The normal native level loop still owns movement, collision, camera, art and audio.
The host creates one policy owner per world through its engine module service.
Shared catalogs/providers contain no session settings; two worlds have independent
requested, admitted and effective graphs.

A shared compiled package may use `baseGame: any` for a typed mutator catalogue.
Register one concrete `GamePatch` per stock game to provide native title/support
metadata. The host expands the catalogue into three owned plans, filtering each
plan's decorators to its concrete game. Such an `any` transaction cannot register
objects, zones, characters, art overrides or zone-specific gameplay policies.
Pure shared startup scenes keep their existing bounded registration shape.

```java
context.registerMutator(new MutatorDefinition(
    "gravity", "Sonic Gravity", "Dry ordinary air only",
    MutatorScope.LIVE, MutatorScope.LIVE,
    List.of(new MutatorOption.IntegerSlider(
        "percent", "Gravity", "Jump impulse stays native",
        MutatorScope.LIVE, 100, 25, 200, 5, "%")),
    Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
    options -> List.of(new MutatorPolicy.DrySonicGravity(
        options.integer("percent")))));
```

The declared ENABLE and DISABLE scopes are independent. Each slider, checkbox or
choice must declare its own edit scope. IDs/tokens, labels, schema size, integer
range and factory outputs are bounded. Slider maxima/defaults must lie on the
minimum-plus-step lattice. Checkbox values are booleans; choice values are declared
stable tokens. No implicit scope inheritance or creator-specific widget is needed.

The common screen draws on the native 320-pixel grid. Row labels fit about 30
compact characters. The mutator description and each option's help appear in a
two-line detail box of 46 columns, wrapped on whole words; anything longer ends
with an ellipsis. Write help as one or two short sentences, as `Gravity.java`
and `Stealth.java` do, and check them on the options page.

## Admit settings at a real boundary

Requested preferences, admission targets, admitted values and effective immutable
policies serve different purposes. Editing an inactive mutator or toggling it
cannot promote deferred option values. LIVE means explicit configuration Resume
before forward play continues. LOAD means a qualifying full assembly with an
explicit cause; decode-only, preview, editor swap, checkpoint restore and seamless
handoff do not qualify. LAUNCH means opening a new game session, including the
first title-to-play admission, not restarting the operating-system executable.

An atomic group can raise its member edit scopes, but cannot lower them. Local
prerequisite/conflict keys refer to definitions from the same owner. Cross-owner
dependency authoring is outside this prototype. Capabilities constrain scope and
factory policy kind: the complete candidate graph and all prepared results must
validate before one revision publishes. Exceptions cross ModFaultBoundary; owner
quarantine survives rewind and cannot be undone by a historical snapshot.

Valid independent-scope regression: ENABLE LIVE, DISABLE LOAD, one option LAUNCH.
Request that option, disable/re-enable and Resume: the LAUNCH choice remains
pending. A new session admits it. This is tested as synthetic data. The expanded
examples use LIVE for scalar,
presentation and new stage-entry decisions, and LOAD for placement/inventory
removals that must not invalidate an existing world.

Invalid examples (constructor/catalog/preparation rejects them):

```java
// An unaligned maximum cannot be selected by the shared slider.
new MutatorOption.IntegerSlider("p", "Percent", "", LIVE, 25, 25, 200, 30, "%");
// Group scope cannot lower an option declared LOAD.
new MutatorDefinition.AtomicGroup("live", LIVE, Set.of("load-option", "live-option"));
// A factory declaring gravity cannot return a Stealth policy.
options -> List.of(new MutatorPolicy.PlayerStealth(true, true,
    MutatorPolicy.Target.LEADER, false));
```

The group is rejected when its owning definition is validated; the last factory
is rejected within its owner's fault boundary. Invalid new graphs leave the old
revision intact. Menu restart preview never invokes creators; a rejected automatic
LOAD retains prior settings and its pending draft. Actual preparation happens only
at admission, including before dependent full assembly.

## Connect native owners

Gravity lives in PlayableSpriteMovement's ordinary dry-air integration. The native
old velocity, position integration, truncation/overflow and jump/death/scripted
branches remain owners. PhysicsProfile has no gravity field and was not extended.
A session-injected PlayableMutatorPolicySource applies to existing/new/recreated
sprites and replays effective configuration events before the next native tick.

Stealth annotates immutable presentation with body/appendage/attached-effect
subjects. Native producers still prepare pieces, art and SAT/mask admission.
Filtering occurs only at emission; `setHidden` is never used. Independent body
and appendage flags remain separate. Shields, attached spindash dust and
invincibility stars are selectable; deposited skid puffs are world presentation.
The roster injection and structural source binding are outside sprite snapshot
values; admitted/effective state is registered before sprite recreation.

The remaining policies use their native owners: Ringfall latches a bounded scatter
plan before slot allocation (`Ringfall(percent, hardCap, true)` lifts the native
32-ring ceiling; the original two-argument constructor keeps it, and extra rings
are slotless Obj37 continuations rather than claimed SST slots); the monitor/ring/checkpoint filters decide admission
without reindexing native placements; stage-entry permits retain an already
captured transition; Game Speed schedules complete presentation-loop ticks; and
Violent Explosions amplifies only the resolved native badnik rebound. Big Head
composes reviewed anatomical masks after native SAT admission, with native
fallbacks for ball, powered, donor/custom and unreviewed poses. Do not scale the
entire player quad or use `setHidden` to fake an appearance-only effect.
`BigHead(percent, target, true)` scales with the target's live native ring count:
normal size at zero rings, `percent` at 100 rings and above. The host reads the
count at presentation, so pickup, hurt, reloads and rewind need no copied state.
A saved entry that predates a newly added option takes its declared default, so
an added checkbox should default to the old behaviour.

Game Speed covers interactive native level, special and bonus play. Stage setup,
results and exits remain under their native presentation owners. A provider being
installed is not proof that it is active or interactive. Creator-owned stage
callbacks keep canonical scheduling; the internal native pacing SPI is not a
creator opt-in. Live rewind records the admitted logical player sample and the
post-tick pacing state separately from physical BK2 input, so a short press and
fractional remainder survive older-keyframe replay without trace-state authority.

Saved requested preferences are bounded, schema-versioned, profile-namespaced
JSON. The host selects the root; creators cannot supply paths. Secure directory
streams, directory/file identity checks and atomic replacement protect byte
publication. Empty host directory provisioning is path-based and is not claimed
to resist a concurrent ancestor substitution; the store subsequently rejects
unsafe identity/path changes and never publishes preference bytes through them.
Changed schemas require an explicit future migration recipe; this prototype
rejects incompatible entries with a visible default/error outcome.
Before Start or Resume publishes any settings, the host must successfully save
the requested draft. Restart and Return also wait for successful persistence
before queueing a command. A failure keeps the previous admitted/effective
revision and the visible draft/error; retry the same action after repairing the
settings path. Closing the Engine window remains available if the path stays
unavailable.

## Human and agent walkthrough

1. Read the example README, one class per effect, `NativeLabProfile.java`,
   `MutatorsMod.java` and the format-1 manifest. Verify Java 21 and the original
   ROMs for the games you will launch. Keep commercial bytes outside Git.
2. Run `python3 examples/example-mutators/build.py` from this checkout. It queues
   the actual Maven build and `ggfmod package`; inspect the result and jar contents.
3. Run the builder with `--run`, or install/enable/trust the jar in the JVM Mod
   Manager and restart. Select a native game with Sonic as leader. Open How to
   play, configure Gravity at 50%, enable it, then start that game's opening act.
4. Jump/run through real terrain; Pause, enable Stealth, and Resume. Collision,
   rings, targeting and sound remain. Pause, turn effects off, Resume; restart
   clears the checkpoint and uses a full assembly. Return to the hub and relaunch.
5. Verify schema/invalid graphs and mixed scopes with `TestMutatorSessionState`,
   `TestMutatorPreferenceStore`, `TestMutatorRegistration`; verify native hooks
   with `TestPlayableMutatorGravity` and `TestPlayableMutatorPresentation`.
   `TestMutatorOverlayRouting` and `TestMutatorWorldIntegration` exercise the host
   pause/fade/session/fault seams. Queue every Maven invocation.
6. Run combined change-based validation against the pinned pre-task destination,
   relevant trace fixtures and S3K guard obligations. This touches shared physics,
   session and API contracts; a tiny-mod exemption does not apply. Inspect skips
   and consumed diagnostic summaries. Observe GPU output and actual PCM separately
   before claiming audiovisual qualification.

The example's checked-in `capture.script` is compiled by InputLogAuthorTool and
round-tripped through Bk2MovieLoader. GameplayCaptureTool accepts `--title-screen`
to initialize the production title before first level assembly, and `--audio` to
capture the same final SMPS presentation packets as the speaker path. Use one
capture per JVM, a fresh empty player-settings root for scripted default toggles,
and an outside-repository task directory:

```bash
OPENGGF_MUTATORS_ROOT="$(pwd)"
java -cp "$OPENGGF_MUTATORS_ROOT/target/classes:$(cat target/mutators-classpath.txt)" \
  com.openggf.tools.InputLogAuthorTool --script examples/example-mutators/capture.script \
  --out /absolute/task/captures/lab.txt --game s2
mkdir -p /absolute/task/captures/config
java -Duser.dir=/absolute/task/captures/config \
  -Dopenggf.saveRoot=/absolute/task/captures/player-settings \
  -cp "$OPENGGF_MUTATORS_ROOT/target/classes:$(cat target/mutators-classpath.txt)" \
  com.openggf.tools.GameplayCaptureTool --game s2 --rom /absolute/user/s2-rev01.gen \
  --zone ehz --act 1 --mod "$OPENGGF_MUTATORS_ROOT/target/example-mutators/example-mutators.jar" \
  --title-screen --title-card --audio --input /absolute/task/captures/lab.txt \
  --out-dir /absolute/task/captures/lab
```

Inspect synchronized `state.csv` (including pause and effective revision) before
selecting PNGs. `audio.wav` contains actual clocked final stereo PCM; the tool's
video remains silent unless muxed with that WAV. Audio requires every frame,
capture-from zero and matching 60-Hz presentation. Title-first captures reject
positioned/donor/clock seeds and use the first act. The capture host does not
install the interactive engine's hub callback; verify exit/relaunch with the JVM
engine separately instead of calling a black final frame a hub. This canonical-tick
capture does not demonstrate Game Speed: record the real `Engine.loop` presentation
path for slow/fast play and compare its native tick/input behavior separately. See the dated implementation
plan/design evidence for the completed commands, observed output and limitations.

For native hub/relaunch and host input evidence, use the maintained
[owned Engine desktop capture](../../../tools/media/README.md) with
`examples/example-mutators/window-walkthrough.jsonl`. It records exact
process/window ownership and cleanup, plus a scoped Pulse monitor WAV. Verify
the screenshots and action timings; its frameless/vblank/preload options are
explicit host workarounds, not default window-manager or physical-speaker proof. The S1/S3K
variants are `window-walkthrough-s1.jsonl` and `window-walkthrough-s3k.jsonl`;
all supply real key edges, enter the shared catalogue from the hub and demonstrate
slow/fast native presentation. Recipe labels are intended actions, not observed
acceptance; inspect actual pages, state and PCM before reporting a successful run.

The common title owns Escape through `TitleScreenProvider.ownsEscapeInput()`;
the stock default remains false. Back leaves an option/help page, and Back from
the title saves the requested draft before queuing Return to hub through the
same `LevelInputOverlay` host command owner. Failed persistence keeps the title
and visible error. Accepted title exits hold input throughout the exit fade.
Null overlay commands are treated as `NONE`, never as a restart request.

The native Lab builds its common title/settings screen with
`MutatorConfigurationScreen.forCurrentWorld(title, inheritedTitle, navigateId,
confirmId, startId, errorId)`. The host pins the prepared world and copies the four
native ROM sound IDs; the example receives no global session or audio manager.
Game-specific support reads configuration from the `PatchContext` passed to its
`apply` method. Development capture applies only the concrete game's eligible
decorators, including the requested team's activation predicate.
