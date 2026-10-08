# Build a scoped mutator package

[Mutator Lab](../../../examples/example-mutators/README.md) is a runnable JVM
Sonic 2 patch. Use its source and host UI to build a small configuration-driven
policy package. This guide describes the implemented prototype; the remaining
[blueprint](../../architecture/designs/2026-10-07-mutators-blueprint.md) stages
are future work.

## Prepare the package

`MutatorsMod.register` contributes independent definitions through
`ModContext.registerMutator`. The engine derives owner keys from that context,
validates the catalog and pins it for a WorldSession. Creator callbacks return
immutable typed policies; they never mutate the world or publish trusted owners.
A semantic support provider advertises the qualified zone/act/player cell. The
common screen implements the existing title provider and level input overlay.
The normal native level loop still owns movement, collision, camera, art and audio.
The host creates one policy owner per world through its engine module service.
Shared catalogs/providers contain no session settings; two worlds have independent
requested, admitted and effective graphs.

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
pending. A new session admits it. This is tested as synthetic data; the two
playable examples deliberately need only LIVE.

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

1. Read the example README and source `Gravity.java`, `Stealth.java`,
   `MutatorsMod.java`, and the format-1 manifest. Verify Java 21 and the supplied
   S2 World REV01 ROM. Keep commercial bytes outside Git.
2. Run `python3 examples/example-mutators/build.py` from this checkout. It queues
   the actual Maven build and `ggfmod package`; inspect the result and jar contents.
3. Run the builder with `--run`, or install/enable/trust the jar in the JVM Mod
   Manager and restart. Select Sonic 2 with native solo Sonic. Open How to play,
   configure Gravity at 50%, enable it, then Start Emerald Hill.
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
engine separately instead of calling a black final frame a hub. See the dated implementation
plan/design evidence for the completed commands, observed output and limitations.

For native hub/relaunch and host input evidence, use the maintained
[owned Engine desktop capture](../../../tools/media/README.md) with
`examples/example-mutators/window-walkthrough.jsonl`. It records exact
process/window ownership and cleanup, plus a scoped Pulse monitor WAV. Verify
the screenshots and action timings; its frameless/vblank/preload options are
explicit host workarounds, not default window-manager or physical-speaker proof.

The common title owns Escape through `TitleScreenProvider.ownsEscapeInput()`;
the stock default remains false. Back leaves an option/help page, and Back from
the title saves the requested draft before queuing Return to hub through the
same `LevelInputOverlay` host command owner. Failed persistence keeps the title
and visible error. Accepted title exits hold input throughout the exit fade.
Null overlay commands are treated as `NONE`, never as a restart request.
