# Build an owned two-act campaign

[Tide Circuit's complete source](../../../src/test/resources/mods/sample-two-act-campaign-src/project/)
is a small Sonic 2 patch with two original exported acts. The user's Sonic 2 World
REV01 ROM supplies the native player, rings, signpost, music and results services;
the jar contains only original bounded level data and creator code.

Build the project with Java 21 and the candidate engine/SDK jars:

```sh
mvn -f project/pom.xml package \
  -Dopenggf.engine.jar=/absolute/path/OpenGGF-jar-with-dependencies.jar \
  -Dopenggf.sdk.jar=/absolute/path/OpenGGF-openggf-mod-sdk.jar
```

The portable modder kit includes this project at `examples/tide-circuit`.
Inside an unpacked kit, follow `examples/tide-circuit/README.md` and use its
Python build launcher; the Maven command above uses checkout project paths.

The normal SDK package step validates the jar. Enable the result in the JVM Mod
Manager, restart, and complete EHZ Act 2 to enter Tide Circuit. The two acts follow
one tagged destination `sample-tide-circuit:tide-circuit`; their `64`/`1024` export
numbers can be reused by another mod and are never saved as global identity.

`TideCircuitMod.register` is the complete registration path. `multiAct` takes the
ordered exports and inserts one results-driven zone after EHZ2. Act 1
uses the native signpost and receiving-game tally to enter Act 2. Native S2
signposts intentionally retire in later acts, so Act 2 registers an original
`finish-gate` object. Its captured ring bonus and two-second results hold finish
before `ObjectServices.advanceToNextLevel()` follows the authored stock successor. Changing `gameStart` to
`true` would choose Act 1 for a new game under the documented composition policy.

Each act contains a native checkpoint, and the route check performs its production
death reload before continuing to its native signpost or original finish gate.
The original gate reuses ROM-backed signpost art, freezes player controls during
its own tally, and recreates through the registered owner factory on load/rewind. Original binary
assets can be reproduced without a ROM using `tools/generate_assets.py --check`
and `tools/generate_assets.py` in the project.

The runtime factory receives the actual act and decoded level. It creates a fresh
state, dynamic water provider, half-speed background scroll handler, animated tile
channel, palette cycle and staged color beacon. Those callbacks are consumed by
the ordinary water/frame/render services. The state serializes a fixed set of
counters through `ZoneRuntimeState`; the event handler independently captures its
tick count through `RewindableZoneEvents`. Neither implementation reports its owner
or chooses a registry key. New loads recreate the handlers; rewind restores the
current handler's captured state before reconciliation.

Useful changes to try are a distinct Act 2 water level, different palette colors,
an additional animation channel targeting a separate tile, or a longer original
foreground. Keep destination identity stable when replacing level data. Attach
adapters for any additional mutable state, and retain the supplied logical zone/act
when implementing runtime state metadata. Use owner storage for persistent settings,
not rewind payloads or engine runtime ids.

`TestTwoActModCampaign` compiles the external source, packages through the SDK,
scans/validates the exact jar, trusts its hash, publishes one transaction, resolves
with STANDARD policy, and then exercises native floor movement, runtime consumers,
rewind, tagged save/load, missing-owner recovery, checkpoint death reload and
physical native-signpost/custom-gate handoff. A separate short Act 2 regression
reaches the original gate, restores and replays its ring tally, reloads fresh, and
executes final progression without depending on the long Act 1 route. Focused runs
must pass an absolute S2 REV01 path; otherwise the ROM-backed check is skipped.
The [per-act route matrix](../../architecture/validation/levels/tide-circuit.md)
tracks supported breadth and remaining checks. Loading alone does not certify a
playable campaign.
