# Mutator Lab

A small, boot-prepared JVM code mod: configure dry Sonic Gravity and selective
Stealth, start real Sonic 2, pause to edit, Resume, restart the act or return to
the game hub. The engine renders native ROM art and synthesizes native music/SFX.
Both effects default off. No ROM bytes or disassembly assets are packaged.

## Build and play

From the repository root, with Java 21 and the user's configured Sonic 2 World
REV01 ROM available:

```bash
python3 examples/example-mutators/build.py --run
```

The script queues Maven compilation, compiles maintained Java sources and runs
`ggfmod package`. Output: `target/example-mutators/example-mutators.jar`.
Choose **Sonic 2**, then the **Mutator Lab** title. Configure the package before
Start or choose How to play. For a package without launching:

```bash
python3 examples/example-mutators/build.py
```

The ordinary JVM Mod Manager can install that jar: enable and trust its exact
hash, restart the engine, then choose Sonic 2 with Sonic as leader. The package
is executable creator code; the engine's native image cannot load it. See the
[trust guide](../../docs/modding/concepts/trust.md). Installing/changing code is
boot-scoped; editing prepared settings never requires restarting the executable.

## Controls and flow

| Context | Keyboard defaults | Controller |
| --- | --- | --- |
| Title/configuration | Arrows choose; Enter select; Esc back | D-pad; displayed confirm/back buttons |
| Normal play | Left/Right move; Space jump | Normal mapped movement and A/B/C |
| Open configuration | Enter (Pause), or Backspace (Start) | Start |
| Apply LIVE edits | Choose Resume play | Choose Resume play |
| Rebuild the act | Choose Restart from act start | Same |
| Leave | Choose Return to game hub | Same |

Your engine bindings override gameplay defaults. Esc in an open configuration
keeps play held; it backs out of option pages. A simultaneous cancel and confirm
backs out. Configuration freezes native ticks and rewind; frame-step and focus
regain cannot release it. Resume explicitly releases an old user pause, while a
window-focus pause still holds play. Menu music/SFX continue during configuration
unless the window or user pause owns an audio pause. A restart/exit requested
during a fade remains visible and queued until that fade completes.

## Supported slice

| Cell | Gravity | Stealth |
| --- | --- | --- |
| S2 World REV01, Emerald Hill Act 1, solo native Sonic, donor off, 320×224 | Dry ordinary air integration, 25–200%, 5% steps | Body and appendage; optional attached effects |
| Other acts, characters, donors or widths | Not qualified | Not qualified |

The observed acceptance slice is the opening run/jump/configuration/restart path;
this does not certify the complete EHZ1 route. The package fixes native width and
suppresses the CPU sidekick. Progression beyond
EHZ1 uses stock effects and shows **Outside supported cell / effects suspended**.
The two Stealth target choices coincide in this solo cell; they demonstrate the
bounded enum authoring contract, not qualified team support. Jump impulse and
release, grounded motion, hurt/water/death/scripted/flight motion are unchanged.
Stealth preserves targeting, collision, sound, HUD and native sprite admission.
Attached effects means shield, invincibility stars and attached spindash dust;
deposited skid puffs remain world effects. Turning Stealth off restores visible
presentation without changing gameplay state.

## Settings and history

The UI shows requested values and pending boundaries separately from admitted
values. Enable, disable and every option have independent scopes. **LIVE** admits
on explicit Resume; **LOAD** requires qualifying full assembly/restart/death/full
stage return; **LAUNCH** requires a new game session. The actual examples use
LIVE only; mixed-scope behavior is covered by synthetic regression tests rather
than invented gameplay options.

Requested preferences are stored in
`<openggf.saveRoot>/mutators/mutator-preferences-s2.json`. The developer script
uses `target/example-mutators/player-settings`; `--save-root /absolute/owned/path`
selects another player root. Ordinary JVM launches default to `saves/`.
Rewind captures admitted/effective policies and recreates bindings. It never
rewrites saved preferences. A restored historical value that differs from the
saved choice says **edit to apply**: Resume alone cannot launder it into history.
Explicit new edits branch future configuration events. A failed automatic LOAD
admission keeps the previous valid graph and reports the refusal when configuration
opens; a rejected menu restart stays in configuration. Owner preparation faults
quarantine the owner, retire the session and recover through the host fault path.

## Troubleshooting and authoring

- Missing/wrong ROM: configure a supplied S2 World REV01 image in the game hub;
  no fallback art or physics is supplied. Retry from the hub after correcting it.
- Package missing: check the builder's successful package result, or the Mod Manager
  diagnostics, enabled state, JVM mode and exact-hash trust. Rebuild after API drift.
- Save failed: the requested draft and visible error remain in configuration.
  Start, Resume, Restart and Return retry persistence before admitting or leaving.
  An unavailable settings directory keeps those actions held; repair the path
  and retry, or close the Engine window to leave the process. Use an
  owned writable directory without symlink ancestors. Secure directory I/O and
  atomic replacement are required; unsupported filesystems/platforms report an
  error instead of unsafe writes. A corrupt/schema-mismatched entry uses defaults
  with a visible error; no automatic migration is claimed.
- No effect: enable the checkbox, choose Resume, and verify the supported cell.
  100% gravity is the identity value. Stealth can hide Sonic while collision remains.
- Recordings: prepared modified sessions cannot create ordinary user recordings.
  The existing recording playback path disables external content for stock replay.
  The input-log capture recipe is an external driver, not a modified recording format.

Continue with the [build-along guide](../../docs/modding/guides/mutators.md) for
schema examples, fault boundaries, verification and the capture recipe. This is
an experimental maintained-source prototype, separate from the eight gallery
samples. It adds no placement filters, reward denial, native-image recipe,
hot reload or additional portfolio mutators.
