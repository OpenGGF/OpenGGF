# TALON ending art


## Ending ownership (2026-11-10)

`TalonEndings` supplies eight S2 ending object poses, three monochrome search
photos, five 24×16 goggles-down cockpit crops, and S3K reunion selectors 3/4.
Built-in GPT Image transformed real-palette native references against the approved
model and WAIT. Native decoded pixels and raw candidates remain in external
scratch; provenance and indexed authored images live in `art/talon/endings/`.
The first configured sidekick owns photos and the new optional
`S2_TORNADO_PILOT` set. The ending and SCZ/WFZ renderers replace only the cockpit
piece, keeping native sequence clocks, vehicle pieces and DPLC decisions.
Missing sidekick/slot retains native art. The unpublished API remains 0.7.0.

Ending object/reunion sets apply to TALON's solo Tails route; MICA's native S3K
reunion does not include a sidekick portrait. S1 credits demos use the configured
team's ordinary playable sheet. S2 credits are text slides, and S3K scrolls ROM
text (`S3kCreditsState`); neither has a credits gameplay bank to redraw.
Presentation-lane merge must add `TalonEndings.load(assets)` to its endings map;
this lane leaves the character definition and TalonPresentation untouched.

Native selectors/hotspots and independent opaque-area measurements are in
`endings.json`; `provenance.json` records candidate hashes and selection.
Review inputs and raw images: `$HOME/scratch/agent-tmp/talon-endings-20261110/`.

The S3K reunion plane also has a separate sidekick pilot (native selector 12).
`S3K_TORNADO_PILOT` aliases the generated S2 side-view cockpit with hotspot
(12,8). Native alpha geometry is identical; native palettes differ. This keeps
TALON visible in MICA's team reunion without adding a reunion portrait actor.

## Verification

The bounded Java selection covers 181 test cases: 180 passed together, then the
corrected positioned S3K capture passed separately; zero skips. It includes MICA
integration, ROM art audit, candidate API pins, native Tornado/SCZ/ending update
and rewind checks, sidekick fallback/ownership checks and the four required S3K
regressions. Python modding discovery: 52 passed. This is not a full-suite run.
The capture fixture enters native `beginReunion` through its real SSZ handoff,
then waits through the native credit scroll to selectors 3/4; it is labelled as a
positioned debug start. Team pilot, solo portraits and native comparisons were
visually inspected.

| Set | sqrt(mean authored opaque area / mean native opaque area) |
| --- | ---: |
| S2 ending objects | 1.079 |
| S2 search photos | 1.000 |
| S3K reunion poses | 1.100 |
| Cockpit crops | 1.095 |

Final TALON audit authored / alias / fallback / N/A: S1 **0/1/2/2**,
S2 **45/1/27/52**, S3K **42/1/69/78**. S3K gains one newly discovered pilot
obligation. All requested ending/credits rows are authored, aliases or N/A;
other lanes own the remaining fallback rows. The production presentation map
still needs the lead's merge wiring.
