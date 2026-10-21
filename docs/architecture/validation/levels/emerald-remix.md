# Emerald Remix act and route matrix

Emerald Remix ([source](../../../../examples/emerald-remix/README.md)) is an example Sonic 2
patch mod. It rebuilds Emerald Hill Act 1 from the player's ROM using a column-strip recipe,
and it adds one original act, Clover Valley, after Emerald Hill Act 2. This is not a
stock-parity claim. The remixed act keeps stock Act 1 art, collision, zone events and
objects, and the original act uses the flat custom-zone runtime plus a parallax scroll
handler. `TestEmeraldRemix` is the executable oracle.

| Act / route | Obligations | Test | Evidence (2026-10-21) |
| --- | --- | --- | --- |
| Remixed EHZ1, structure | Every column equals its recipe source (rows above the kill plane only). The background layer is Act 1's. Placements are x-sorted with unique identities. Act 1's checkpoints stay in order and Act 2's are dropped. There are four corkscrew objects. The signpost and camera bound move with their strip. The authored monitor is present. | `recipeRebuildsActOneFromStockStripsAndCloverValleyFollowsActTwo` | Passed |
| Remixed EHZ1, Sonic solo, 320 px | Uninterrupted run from the stock start through both new seams, the triple corkscrew and the stock lower route to the signpost; native results load Act 2 | `sonicCompletesTheRemixedActAndCloverValley` (route bot: hold right, jump/spindash on stall, damage off) | Passed |
| Clover Valley, Sonic solo, 320 px | Original floor grounding, authored checkpoint on route, signpost results hand off to CPZ1 | Same test | Passed |
| Clover Valley, slope physics | Sonic takes the eased (0xEC/0x14) and 45° (0xE0/0x20) floor angles on the first hill instead of the cardinal snap a direct flat-to-45° join causes | `cloverValleySlopesTurnSonicThroughEasedAngles` | Passed |
| Progression | EHZ2 → Clover Valley → CPZ1 through the results plan | First test | Passed |
| Editor: Clover Valley | Import the export, clone it, save and reopen the `.ogfproject`, export, re-parse and reload; every placed tile, palette line and collision profile is unchanged; playtest preparation is accepted | `cloverValleyRoundTripsThroughEditorImportProjectExportAndPlaytest` | Passed |
| Editor: remixed EHZ1 | `Shift+Tab` adoption (clone of the live level) shows the remixed layout; playtest preparation and export succeed | `remixedActIsAdoptedByTheEditorAndExportsAsAPlayableLevel` | Passed |

Still open, not certified by these checks: Tails and other characters, teams,
viewports other than 320 px, donors, rewind and restore spots, checkpoint death/respawn,
damage-on runs, and GPU presentation captures. Stock EHZ1 and EHZ2 matrices are separate
inherited obligations.
