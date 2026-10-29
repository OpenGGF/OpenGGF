# Create levels with the editor

The editor creates playable levels for Sonic 1, Sonic 2, and Sonic 3 & Knuckles
using assets from your supplied ROMs or an imported level. It supports foreground
and background layouts, block/chunk composition, collision assignments,
object/ring placement, launch properties, project saves, and playtesting.

The project and export workflows below describe the **0.7.prerelease development
line**, including the unpublished candidate Mod API. Use a matching development
build; this guide does not describe the published 0.6 editor's feature set.

Start at **Advanced → Editor projects** on the master title (**More** replaces
**Advanced** on narrow layouts). The same project
commands are available from the editor menu. The editor remains
experimental; pixel-art painting, collision-profile shape drawing, native stock
cutscenes/boss scripts, and campaign packaging are separate workflows.

For authored sprite films and mod cutscenes, press **F4** to open
[OpenGGF Stageplay](stageplay.md). It shares the loaded set with a separate
directing document and preserves the level workspace when you switch back.

## Start a project

1. Choose **New blank level**, then choose the game and source zone/act for its
   ROM asset library. The game must have a configured, accessible ROM.
2. Enter a name, width, and height, then choose **Create level**.
3. Open **Level properties** to set the name, start position, bounds, and music.
   S3K also needs an object family: `S3KL` for Sonic 3 objects or `SKL` for Sonic &
   Knuckles objects. The family selects the meaning of stock object IDs.
4. Use **Save project as** to choose an `.ogfproject` file before your first save.

![The New blank level form with the name Emerald Practice, width 8 blocks, height 4 blocks, and Create level action.](images/level-editor/new-project.png)

*New blank level after choosing Sonic 2 and an Emerald Hill asset library.
Dimensions count map cells; an 8×4 Sonic 2 canvas is 1024×512 pixels.*

To edit a stock layout, enable `debug.flags.editor`, load the zone/act in
gameplay, and press `Shift+Tab`. Native editing preserves ROM collision and
camera semantics, including Sonic 1 loop flags. S3K starts from its complete ROM
asset library rather than transient intro art; this does not import a running
cutscene or event phase. Native playtests use independent snapshots; returning restores
your edits and undo history. `F5` starts at the source act's start, while `Shift+Tab`
starts at a validated cursor. Playtesting does not save native edits or publish a
gameplay sidecar. Save records native edits;
leaving for another project or quitting prompts for unsaved changes. Native edit
files store terrain descriptors and object/ring placements. Palette edits, imported
art and project settings require a portable project; native palette cells are read-only.
S3K native edit files are not automatically reapplied on a fresh ROM load; use a
portable project for a complete reusable authoring document.
Choose **Editor projects → Clone native layout as project** to turn a compatible
stock layout into a portable project. With a project open, **Clone this layout**
makes a named copy of its layout and placements.

Cloning copies level data into an authored level with a fresh, flat runtime.
Native zone events, terrain swaps, animations, water programs, and PLC scripts do
not become an authored campaign. Layouts with native collision remaps, including
Sonic 1 loop flags, fail with an explanation; use **New blank level** with that asset
library instead. An incompatible stock object can also prevent playtesting or
export until it is removed or replaced.

**Open project** reopens a complete `.ogfproject`. **Import exported level** reads a canonical level
export directory containing `level.json` and its binary assets; choose its host
game so native geometry and object semantics can be checked. Import creates an
editable project rather than overwriting the export.

**Advanced → Import content** also accepts `.ogfproject` files, level folders,
`level.json`, and level ZIPs. A level ZIP can hold the exported files at its root
or inside one enclosing folder. Drop one item onto the idle master title or
editor window, or use **Browse files**, then review the detected name, game and
action. **Import level into editor** creates a new editable project;
**Open project in editor** reopens the project file. Both preserve the current document until any required
save/discard/cancel choice is resolved. Invalid content reports an error without
changing the source.

The shared screen also installs mod and mutator packages at the master title.
They remain disabled, and **MODS** opens for enablement, code trust and Apply.
See [installing mods](../../modding/installing-mods.md) for packages and updates.
Finish the current dialog or return from gameplay before dropping content.

![The shared Import content landing page with a drop area for a level, project, mod or mutator and a Browse files action.](images/level-editor/import-content.png)

*Open **Advanced → Import content** at the master title, or **Import content** in the
editor menu. Drop one project, level folder/ZIP, or mod/mutator package
onto the window, or use **Browse files**. Review the detected content before
applying it.*

## Edit the level

The hierarchy is **World → Block → Chunk**. World cells select blocks, block
cells select 16×16 chunks, and chunk cells select 8×8 patterns. Select a library
entry as the brush, focus the destination canvas, and place it. `Enter` descends
into the selected content and `Escape` returns to its parent. The active layer
is shown as FG or BG.

### Controller HUD

The editor draws its interface at window resolution around the level, sized for a
gamepad on a couch as well as a keyboard: a top bar with the view and layer, a
ten-slot brush hotbar along the bottom and button prompts that follow the pad you
are holding (Xbox, PlayStation or keyboard keycaps). The level is never covered by
a panel.

![The Emerald Practice course in World view with the controller HUD: a gold block cursor over the level, the ten-slot block hotbar along the bottom and Xbox button prompts below it.](images/level-editor/hud-world.png)

*Steer with the left stick (the right stick moves faster; the D-pad steps whole
blocks), hold South (A / Cross) and steer to paint a stroke that undoes as one
edit, and pick with West (X / Square). LB/RB change brush along the hotbar.*

![The library sheet open over the World view, showing ROM-decoded block thumbnails in a grid with the highlighted block enlarged and its details and usage count on the right.](images/level-editor/hud-library.png)

*North (Y / Triangle) or `Tab` opens the library for the current brush kind;
choosing an entry puts it on the hotbar. RT edits the block under the cursor and
then its chunk; LT or East goes back up. Hold View (or press `Q`) for a wheel of
tools for the current view, and press Menu (or `F1`) for the command sheet: every
editor command once, grouped as Editor (the HUD's own pages and the Stageplay
workspace), Edit (Undo and Redo first), Region, Pattern and tile, Objects, View,
Playtest and Project, each with the keyboard shortcut it really has (or, on a pad,
the pad control). Panning is a held control (Ctrl+arrows, or LB+RB with the left stick), so
it has no menu row.*

The full pad table is in the [configuration guide](../../../CONFIGURATION.md#level-editor-experimental). The
keyboard shortcuts below apply to the HUD and world editing canvas.

![The Emerald Practice course in World view, framed by the HUD hotbar and controller prompts.](images/level-editor/world.png)

*World view of a Sonic 2 project with a sample floor assembled from decoded
Emerald Hill art. Choose a block from the hotbar or open the library with North
or Tab, then paint on the world canvas. These pictures show the full 1600×900
window with the game scaled by an integer factor.*

Erase chooses actual transparent terrain with no collision; it does not assume
library entry zero is empty. If the library lacks an appropriate empty definition,
the action reports the problem and leaves the level intact.

For a first route, place a solid floor below the start, add a few rings, and
place a supported goal along the path. Check the collision overlay before
playtesting so a visually convincing floor also has the intended collision.

Local composition edits derive definitions for the selected placement instead
of changing every placement that shares a block or chunk. The editor reports
capacity exhaustion when it cannot allocate a free definition. Undo the change,
reuse an existing definition, or start from a library with spare capacity.

![Block view with the HUD chunk hotbar and workbench, including the highlighted chunk cell.](images/level-editor/block.png)

*Press Enter from World to edit a block. Its 8×8 chunk cells appear in the
workbench; the hotbar and library supply the 16×16 chunks used to compose it.*

![Chunk view with the HUD pattern hotbar and enlarged four-pattern workbench.](images/level-editor/chunk.png)

*Press Enter again to edit a chunk's four 8×8 patterns. Use G for its palette,
H/J for flips, and T for priority. Escape returns to Block, then World.*

| Input | Action |
|---|---|
| Arrows / menu directions | Move the canvas cursor or selection in an open library |
| `Tab` | Open the library sheet |
| `Page Up` / `Page Down` | Move through pages in an open library |
| `Enter` / South in a library | Select the highlighted brush |
| `Space` / left click | Place or apply the brush |
| Left drag in World | Paint terrain as one undoable stroke |
| `E` / right click | Pick content near the cursor |
| `Delete` | Delete the selected region; otherwise erase terrain at the selected cell, or delete the object at the cursor in object mode |
| `L` | Switch FG / BG |
| `Enter` / `Escape` | Descend / ascend the hierarchy |
| `O` | Cycle terrain, objects, and rings |
| `H` / `J` | Toggle horizontal / vertical flip on a selected block/chunk cell or object |
| `G` in Chunk view | Cycle the selected pattern descriptor's palette line |
| `T` in Chunk view | Toggle the selected pattern descriptor's priority |
| `C` / `P` | Toggle collision overlay / select primary or secondary path |
| `V` in Block view | Cycle the selected cell's collision mode |
| `[` / `]` | Decrease / increase the selected chunk's collision-profile index |
| `=` / `-` with an object selected | Increase / decrease that object's subtype |
| `R` with an object selected | Toggle its respawn tracking |
| `M` with a spawn selected | Move it to the world cursor |
| `Ctrl+Z` / `Ctrl+Y` | Undo / redo |
| `Ctrl+=` / `Ctrl+-` / `Ctrl+0` | Zoom in / out / reset (Ctrl+0 never picks hotbar slot 10) |
| `Ctrl+S` / `Ctrl+Shift+E` | Save the project / export the level |
| `F1` | Open the editor menu |

Picking an existing object lets you edit its subtype, flips, and respawn
tracking without changing the brush for future placements. In spawn mode the HUD
places with South, picks with West and deletes from the tools wheel. The visible
prompts adapt to the controller family.

Pointer clicks on the HUD belong to its controls and do not paint through
menus or chrome. Library sheets and modal forms suppress underlying editing
shortcuts. Save, export, history, layer changes, and playtest boundaries
finish the current edit transaction.

Sonic 1 has one collision-profile path. Its primary and secondary assignments
must agree for a playable export. S2/S3K have separate paths. Assigning a profile
uses existing collision data; this tool does not draw new height/width profiles.

## Select, copy and fill regions

In World view, **Shift + left drag** selects a rectangle of map cells. For a
keyboard or controller, move to the first corner, choose **Set corner / finish
region** (keyboard **F6**), move to the opposite corner, then choose it again.
The rectangle covers terrain on the active FG/BG layer and object/ring centres
inside it. Native object-backed ring groups are indivisible: selecting any member
includes the whole group, even if other members cross the rectangle edge.

**Ctrl+C**, **Ctrl+X**, and **Ctrl+V** copy, cut, and open a paste preview. The desktop
World inspector and HUD editor menu expose the same actions, plus **Delete region**
(`Delete`), **Fill region with brush**, **Flood fill at cursor**, and **Clear region**
(`Escape`, which also cancels an open paste preview).
Choose a block brush before filling. Flood fill uses four-neighbour connectivity
on the active layer; rectangle fill changes terrain only. Delete/cut use actual
transparent, noncolliding terrain, and report an error if none exists.

Move the translucent terrain-and-spawn preview to the destination, then press
**Enter / South** or click to paste. **Escape / East** cancels. Out-of-bounds or
invalid native placements cannot be committed; no partial paste is published.
Each cut, paste, delete, rectangle fill or flood fill is one undo step. Copy keeps
an immutable snapshot, so overlapping pastes are safe. A clipboard belongs to
one document/library and clears when another level is attached. Shared block and
chunk definitions stay unchanged; subsequent local composition edits retain the
existing derived-definition behavior.

### Canvas navigation

Middle-drag pans independently of the editing cursor. The mouse wheel zooms around
the pointer in integer steps from **1× to 8×**. Keyboard **Ctrl+arrows** pans,
**Ctrl+= / Ctrl+-** zooms, and **Ctrl+0** resets zoom. Every navigation command is
also in the command menu. In the HUD, hold **LB+RB**, steer the left stick to pan,
and use D-pad up/down to zoom. Navigation does not enter undo history or change
playtest bounds. **1× is the minimum**: zoom magnifies the native world render;
zooming out to show more terrain is not yet implemented.

## Named object properties

Pick an object with right click / West, then use **Selected placement** in the
desktop inspector. Springs expose orientation and strength, monitors expose
contents; spikes, checkpoints, platform types and badnik variants have per-game fields.
The current inventory covers 52 IDs (19 Sonic 1, 20 Sonic 2, 13 Sonic 3&K).
Each typed edit is one undo step. Inspection preserves every subtype bit, including
unknown values. **Raw subtype** remains available for every object and preserves
unsupported or unusual data. Flips and respawn tracking remain separate controls;
changing a placement leaves the future-placement brush unchanged.

In the HUD, press **F2** or choose **Object properties** from the editor menu or
**Properties** from the selected object's tools wheel. Up/down chooses a field;
left/right changes its value. East / Escape closes the list. Enums cycle through
named choices; an imported unknown value is shown without changing it until edited.
The [coverage inventory](../../status/editor-object-property-coverage.md) lists every
numeric ID and identifies raw fallback. A named schema does not imply that a stock
object is portable to a flat authored level.

## Properties and resizing

Start X/Y and object/ring positions use their centers in level pixels. Bounds
limit the gameplay camera; the editor can navigate the entire canvas. New levels
reserve a native 320×224 final viewport beyond those limits. X uses the native unsigned 16-bit range; Y uses
the native signed 16-bit range for bounds and the start. The start must lie inside
ordered bounds and the bounds must fit the map.

**Resize level** uses map cells, not pixels:

| Game | One map cell | Block composition |
|---|---|---|
| Sonic 1 | 256×256 pixels | 16×16 chunks |
| Sonic 2 / Sonic 3&K | 128×128 pixels | 8×8 chunks |

Growing keeps the overlap in both layers and adds empty terrain. Shrinking
requires an explicit crop choice. Cropping removes outside placements; an
object-backed ring group is removed together if any part is cropped. Resizing
keeps the start inside the resulting bounds by clamping it when necessary. It
establishes a new editing checkpoint, so save a copy first if you need the older
map dimensions and history. Resizing preserves camera margins while they fit.
If a map is reduced below the native viewport, growing it again restores the
standard 320×224 margins; review any custom camera bounds afterward. Changing
bounds in **Level properties** also starts
a new undo checkpoint; name, start, and music changes remain undoable.

The music field accepts `stock:<decimal id>` (for example, `stock:129`) or an
owned track such as `owner:localName`. S3K projects must deliberately choose the object family that
matches their stock objects. Changing the family can make existing object IDs
mean different things; validate the result with a fresh playtest.

![The Level properties form for Emerald Practice, showing Name, Start X and Y, four camera bounds, and the stock music field.](images/level-editor/properties.png)

*Level properties keeps the authored start, camera bounds, and music together.
Choose a row to edit it, then scroll to **Apply properties**. S3K projects also
show the object-family field.*

### Palette colors

The desktop inspector's **Level palette / Genesis 9-bit** section shows four
palette lines and sixteen colors per line. Choose a swatch, then select exact
red, green and blue values: **0, 36, 73, 109, 146, 182, 219, 255**. Each color
change is one undo step; preview textures refresh on apply, undo and redo.

The HUD editor menu exposes **Level palette (Genesis colors)**. LB/RB (Page
Up/Down) chooses a color, up/down chooses the RGB channel, and left/right changes
its native value. All three channels of an edited cell become Genesis colors;
legacy arbitrary RGB remains untouched until that cell is edited, and undo
restores its original bytes. Character line 0 is host-owned in all games; S3K
also locks its reserved HUD cells. Project saves retain palette edits and the
existing v1/v2 exporter carries them through its strict palette validation.

### Environment and allowed team

Open **Level settings** in the desktop inspector, or **F3 / Level settings** in
the HUD. Up/down selects a row and left/right changes it. Each change has one
undo step. Choose Flat or a stock background scroll source, enable water and set
its signed height, and choose an unrestricted or required playable team. The
properties form also edits the title, music, start and camera bounds. Settings
persist in the project, baked export and campaign; playtest applies them without
rewriting gameplay saves or user configuration. Unsupported required sidekicks
are rejected; their presets are omitted for hosts that cannot create them.

The provider catalogue intentionally lists handlers that can run independently:
Sonic 1 Green Hill acts and Final Zone, Sonic 2 Emerald Hill acts, and Sonic 3&K
Flying Battery indoor bands / Death Egg acts. These reuse native scroll handlers
with the selected stock act. They scroll your background layout; choosing a source
does not import that zone's art. Water uses existing ROM-backed underwater palette
providers with a fixed authored surface, without stock-zone water events.

### Browse and place by picture

Both editors show objects using ROM-loaded mapping frames and the level's current palette.
The desktop library pairs thumbnails with readable names; the HUD hotbar and **Tab** library
use the same art. The default library shows placeable objects with available source art and
authoring gizmos. Use **All / internal IDs** on desktop, or **X** in the HUD object library,
to reveal system IDs and objects whose art needs a different level or live runtime state.

Placements keep their native origins, subtype pose and flips. A translucent brush shows where
an object or ring will land; selection and hover outlines identify existing sprites. Camera, music,
warp and Stageplay regions use translucent bounds and small labels that stay legible at every
zoom. Logic-only placements use compact named markers. The desktop canvas fits between its
panels, including narrow docks; nearest-neighbour sampling keeps sprite edges clear.

Source art is detached during the normal ROM load and retained with the editor's library.
AIZ1 uses its ROM main-level palette for the initial library instead of the intro's
black palette; saved palette edits take precedence when a project reopens.
Viewing thumbnails or moving a ghost never creates gameplay objects or changes undo/rewind
state. Reopening a saved project reloads its source ROM library for these transient previews;
projects and exports contain no bundled stock sprite images.

### Semantic gizmos

Place `openggf:authored-goal` to finish any authored act through its native results
flow, including later campaign acts where stock signposts retire.

In the object library select an `openggf:` camera-zone, music-zone, warp or
stageplay-trigger. Place its origin, pick it and edit the typed inspector or F2
property list. Regions appear as outlines. HUD text fields open the shared
controller keyboard; desktop fields use the same project form and path browser.

Camera zones set bounds until leaving the region or crossing a configured X/Y
threshold. Music zones select a stock ID or mod track and can fire once. Place a
Warp source, then a second Warp for its exit; both share a link, and two-way mode
updates the pair in one undo step. A pasted complete pair receives a fresh link.
An incomplete pair must be repaired before playtest/export.

Stageplay triggers reference a project-relative `.stageplay` resource, activate on
entry or a fresh **Up** press, and can fire once or consume themselves. Playback
uses the public registered Stageplay player and participates in rewind. Its current
public API supports blocking playback only; non-blocking requests are rejected.
Campaign export includes bounded referenced scenes/assets. No Stageplay workspace
or package changes are required.

### Campaigns and art import

Open **Campaign levels** from the settings panel or project menu. Append relative
`.ogfproject` files, then move or remove entries to set their order. Include the
current project explicitly when the list is nonempty. An empty list exports this
level alone. **Export playable campaign** saves this document and packages the
saved sequence into a mod JAR, with no Java entrypoint. All levels must use the same
game and required team; the last level ends the campaign. Native title/results
art supports the original act glyphs (1–3); longer logical sequences retain their
progression but do not gain new number artwork. Campaign acts also share the
native zone title; individual saved project names remain in their exports.

**Import art** opens PNG and sheet-YAML path fields with a file browser. Save the
project to establish an asset folder, or choose a separate import folder; keep both inputs inside that folder. Conversion uses the existing
`ggfmod convert art` path and bounded typed asset loading. Tile dimensions, colour
counts, free pattern slots and palette ownership are checked before applying one
undoable command. If copied terrain references art whose slots have since been reused, paste fails
before changing the level; restore that art or copy again. Imported art occupies
blank unreferenced slots; transparent
imports reserve their slots too. Optional palette installation obeys host locks.
Choose the imported patterns in Chunk view to compose your terrain. Projects retain
imported pixels and reservations, and exports contain the resulting native art.

## Save and reopen safely

`Ctrl+S` saves the current project; **Save project as** chooses a new path.
A project includes the full art library, layouts, collision profiles, palettes,
objects, rings and backing groups, plus its game/source identity and launch
properties. The project file is separate from ROMs, stock gameplay saves, and
legacy editor sidecars.

Saves validate a complete staged file before atomically replacing the previous
project. A failed save leaves the previous file intact and the document open for
retry. Opening malformed, oversized, unsupported-version, or checksum-damaged
projects reports an error without rewriting or quarantining the file.

The unsaved marker compares the complete document with its savepoint. Undoing
back to saved content clears it. An unavailable keyed object or music dependency
stays recorded in the project; reopening or saving never silently removes it.
Install/enable the matching dependency or explicitly edit the placement. Playtest
refuses unavailable object keys and leaves the document intact.

A stock asset library can contain shared definitions whose art belongs to another
zone or a later native art upload. New blank projects include the available
definitions and report any omitted chunks/blocks; they never invent replacement
art. Choose another source library for unavailable definitions. A full layout
clone never changes what its layout shows. A placed chunk with unavailable art
fails the clone. An unplaced library chunk keeps its index and collision, and
its unavailable tiles draw transparent (stock Sonic 2 Emerald Hill has 32 such
references).

**Close project** asks about unsaved changes. **Save and continue** saves before
closing; **Discard changes and continue** closes without saving; **Continue
editing** keeps the document open. Replacing a project uses the same choices.
Application quit and the window close button also offer **Save and quit**,
**Quit without saving**, and **Cancel quit**. A failed save keeps the application
open; cancelling restores the editor or active playtest.

## Playtest and return

Choose **Playtest from level start** (`F5`) for the authored start or **Playtest
from cursor** (`Shift+Tab`) for a validated cursor position; the menu's Playtest
group lists both, for projects and native layouts alike. Project launches use
its authored bounds and body-clearance checks, so the final runway beyond Camera
maximum X cannot be a launch point. Native cursor launches use the loaded terrain
bounds and native collision mapping; native camera-origin limits do not bound
player positions. `F5` starts a fresh playtest from the project's authored start
or the native source act's start. `Shift+Tab` switches between the editor and its
playtest; when entering from ordinary gameplay, enable `debug.flags.editor` first.

Each playtest builds a fresh runtime snapshot. Rings collected, objects destroyed,
terrain changed by gameplay, score/timer/checkpoint state, and other runtime
changes do not alter the authored document. Returning restores the document,
selection, and undo history. Starting a playtest does not save the project or
write stock gameplay saves. Missing dependencies or unsupported host features
produce a visible error and keep the project in the editor.

For an S3K finish, choose the engine-provided `openggf:authored-goal` from the
object library. It triggers the native signpost/results flow in an
authored level without assigning a new meaning to a stock numeric object ID. It
does not require a separate mod installation. S1/S2 use their supported stock
signpost objects.

Place a native finish in the final camera view and keep its floor through the
right edge of the map. S1/S2 signposts lock the camera at **Camera maximum X**
and finish when the player's center reaches that maximum plus 296 pixels. For
example, a 1024-pixel wide course can use camera maximum X=704, a goal around X=920,
a finish threshold at X=1000, and floor through X=1023. Keep that runway when changing bounds; moving only the signpost earlier
does not move the native camera limit.

## Export a playable level

Choose **Export level** or press `Ctrl+Shift+E`, then choose a new destination directory.
Export never overwrites an existing directory. Use another destination for a
later revision; keep `.ogfproject` as the editable source.

| Host game | Export format | Additional checks |
|---|---|---|
| Sonic 1 | `ModLevelDefinition` v1, 16×16 blocks | Native object/ring encoding, supported flat-host objects, one collision path |
| Sonic 2 | `ModLevelDefinition` v1, 8×8 blocks | Native object encoding and host-level validation |
| Sonic 3&K | `ModLevelDefinition` v2, 8×8 blocks | Explicit `S3KL`/`SKL`, sparse palette ownership and supported custom-zone objects |

Export uses the project's name, start, bounds, and music. Ring backing objects
are converted into individual ring entries. The exporter checks references,
reads back the staged directory through the strict parser, and validates the
selected game host before publishing it.

Projects retain exact RGB data and can store a larger native asset library than
a portable export permits. Playtest and export require exact Genesis-representable
colors and the format's asset limits. For S3K, reachable terrain art must use creator
palette cells: character line 0 and HUD-reserved cells belong to the host. An
export error identifies the incompatible data; it does not change the project
or leave a partially published destination.

To package the exported directory into a mod, continue with the
[creator handbook](../../modding/index.md),
[level formats](../../modding/formats/level-definition.md), and
[`ggfmod` conversion/validation](../../modding/ggfmod.md). Exported ROM-derived
assets remain subject to their original rights; exporting them does not grant
permission to redistribute them.
