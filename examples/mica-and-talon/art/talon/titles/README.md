# TALON native title mascot banks

Original built-in GPT Image native-pose transformations, reduced against the
native title palette renders enlarged 8× nearest-neighbour. `prompts.json` and
the three `*-provenance.json` files record generation and size measurements.
Raw attempts and native renders stay in
`$HOME/scratch/agent-tmp/mt-titles-20261110/`; no decoded native art is packaged.

- S1: eight entrance/finger frames, native mapping origins and screen-plane mask.
- S2: body selectors 0–7/18 and separate hands 9/19. Lead uses 5–7/18 and 9;
  sidekick uses 0–4 and 19. The native controllers retain every delay and pass.
- S3K: thirteen 320×224 plane frames, split into 160×224 halves, binary native
  mascot clear masks, finger selectors 0/1, a split resting hand at selector 4, and wink selectors
  2/3. Selector 4 composes the hand which native art keeps in its plane. The final emblem mask is independent of the palette;
  matching pixels in the preceding mascot plane identify its original portrait.

All images use the approved character palette; the engine reserves a separate
palette context for each role. `TitlePresentation.java` binds native selectors
and hotspots through the official `CharacterPresentation.titleMascots` API.
`manifest.json` is the audit's complete packaged-bank inventory.

Native object origins remain unchanged. TALON's S2 wings are connected to his
body and replace the separate fox-tail motion. No rotor/tail sprite is supplied.

The frame-D plane excludes the hand patch. Selector 4 restores its resting pose;
selectors 0/1 replace it at the same native object origin. This prevents a wag
from leaving a second hand in the plane. Patch dimensions retain the native
48×56 box; the source-local hotspot follows the authored wrist.
