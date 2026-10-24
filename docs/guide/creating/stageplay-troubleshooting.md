# Stageplay troubleshooting and glossary

These are the surprises people meet most often, with what causes them and what
to do. In either editor, **Direction details** shows the full status, preview
problems and the last route's diagnosis; in any form, **F1** explains every
field and the complete error.

- [Routes](#routes)
- [Floors and positions](#floors-and-positions)
- [Moving players](#moving-players)
- [Objects](#objects)
- [Camera and picture](#camera-and-picture)
- [Dialogue](#dialogue)
- [Sound](#sound)
- [Files and export](#files-and-export)
- [Glossary](#glossary)

## Routes

### What does "needs Run" mean?

When a route with **Speed: Walk** fails, the planner rehearses the same
terrain at a run before it gives up. If running gets there, the route's full
diagnosis (**F1**, or **Direction details**) says *terrain near x=…: needs Run*.
The number is where the walking attempt stalled, usually at the foot of a slope
or loop that a stroll cannot climb. Change **Speed** to **Run** and plan again.

The rehearsal may take longer than the route was allowed. Then the advice says
*needs Run and more Attempt frames* (or just *needs more Attempt frames*): also
raise **Frames** under **Advanced**, which sets how long the route may take.
The rehearsal runs within the default limits, so the advice appears without
changing any settings first; it never applies a route by itself.

### Why did my route fail?

| Message | Meaning and fix |
| --- | --- |
| Search limit reached. | The search ran out of frames, search nodes or time. This is not proof that the destination is unreachable. Read the advice in **F1**; otherwise choose a nearer destination, use Run, or raise **Frames** and **Search nodes** under **Advanced**. |
| No route found in this search. | No combination it tried got there. Try a destination on the far side of the obstacle, a Jump start, or Run. |
| Playback could not confirm the route. | A route was found, but playing it back from the start of the shot ended somewhere else: a fall, a hit, or a stop that overshot. Try **Gentle** or **Hard** stop, or move the destination. |
| Scene or assets changed; plan again. | The scene, the ROM or a level file changed while it planned. Plan again. |
| This route is unsupported; open Details. | The request cannot be planned, for example a Spindash start for a character without one. |

### "Players start at a shot entry"

Player routes are planned from the first frame of a shot. To start a route
part-way through, add a scene change (a **Cut** to the same set) where the route
should begin; that starts a new shot.

### The route worked, but the character overshoots or stops early in playback

The route was checked from the start of the shot as it was when you applied
it. Anything you change afterwards (a key, an earlier action, a cleared object,
another character in the way) can change the outcome. Play the shot and plan
again if needed. Moving platforms and other objects are not part of the plan.

### A route can't go up or around something

Each route goes to one destination. Planned walking objects follow the floor
and steps of up to 8 pixels only. For a longer journey, split it into shots, or
add actions by hand (a **Jump** at the right frame, then another **Move to
destination**).

## Floors and positions

### My character starts in the air, or falls through the floor

A Puppet attaches to a floor within 14 pixels of its feet at the start of each
shot. Further away, it falls. Use the picker with floor snap on (**S**; the tag
reads `Snap` and a height), or **Place on floor**. If you want the fall, turn
off **Start on floor** in **Actor properties**.

### The picker says "No floor" or "Snap off"

`Snap off`: press **S** to turn snapping back on. Placement pickers start with
snap on; camera framing starts with it off. `No floor`: there is no terrain
below the crosshair within reach; move it over solid ground.

### Place on floor shows "Refused floor"

That surface cannot be used to stand on as it is, for example because it needs
wall or ceiling running, or is on the background layer. **F1** gives the reason.

### The floor is on a moving platform

Floor searches see terrain only, not objects. Put the character on solid
ground and use **Move to destination** aimed at the platform (set its placement
under **Advanced**), with a **Wait** until on the object.

### Players can only be placed at the start of a shot

A Puppet's position is set where each shot starts; after that, only its actions
move it. Move the playhead to the shot's first frame, or use **Move to
destination** inside the shot.

## Moving players

### "Goals are attempts, not teleports"

Every action is a controller input. If Sonic hits a wall, he stops; if there is
a gap, he falls. A **Wait** never moves anyone either. Watch the shot, then add
a jump, change the destination or use **Plan route**.

### My character never reaches the destination

Its status shows `BLOCKED` when it stops making progress. Check what is in the
way, and remember that the destination's height is not a climb: a character
will not go up a cliff unless it jumps.

### "Native flight requires S3K Tails" / "Native glide requires S3K Knuckles"

Flying and hovering exist only for Sonic 3 & Knuckles Tails; gliding only for
Sonic 3 & Knuckles Knuckles. Set the character's game to Sonic 3 & K.

### Knuckles keeps gliding after arriving

A glide continues until it is ended. Follow **Glide toward target** with
**Release**, then **Wait** until grounded, then **Stop**.

### "Physics puppets require held unit-scale keys" or "position/facing keys are only allowed at shot boundaries"

A Puppet's position, facing and animation come from the game. Inside a shot you
can only change its visibility. Use actions to move it, or make it a Poser.

## Objects

### My object won't move

Only a **Puppet** object follows **Move or stop**. A **Free** object goes where
its own behaviour takes it, and a **Pose** object goes where its keys put it.
Add a Puppet key before the move.

### A boss ignores where I put it

Some bosses always appear at their arena position while **Free**. Use the
boss's own level, or switch it to Puppet or Pose to place it.

### Switching to Puppet or Pose says the picture is unavailable

Some objects only build their picture once their behaviour has started. Start
them **Free** for a moment in their own level, then add the Puppet or Pose key.

### A frozen object leaves a stray part behind

Parts such as exhaust smoke are frozen with the object. Freeze it at a moment
when the part is not showing, for example while a Motobug waits at a ledge.

### A shot says it has run out of object space

All performers share the game's object slots. Clear objects you don't need
(**Clear objects** or **Remove from world**) or split the shot.

## Camera and picture

### Zoom won't go below 1×

**Q** and **E** step whole-number zooms from 1 to 64. Type a smaller or
fractional zoom in the **Camera key** form.

### The picture shimmers during a zoom

Fractional zooms can shimmer while the camera moves. Use a whole-number zoom for
held shots, and watch moves at full speed before export.

### The first frame of a shot is black

A fade or wipe is fully covered at the change frame. Step a few frames into the
shot, or use a **Cut**.

### "Preview needs attention; open Direction details."

The preview could not draw this frame. **Direction details** has the full
message. Check that a ROM is configured for every game the scene uses.

## Dialogue

### "Dialogue panel exceeds the viewport" or a line does not fit

The panel must fit inside the film and hold the whole wrapped text and the
name. Make the panel bigger under **Advanced**, shorten the text or split it into
two lines.

### Text finishes too late or too soon

The whole line must type out before the line ends. Make the line longer, use a
faster typer, or set **Custom reveal timing**.

### I can't hear voices while scrubbing

Voices, like all sound, play only during playback.

## Sound

### No music when I press play

Cues play only when playback passes them. Start from frame 0, or before the cue.

### The level's own music stopped

A scene with any music cue takes over music for the whole scene. Add a music cue
to each shot that needs music.

### A song number plays the wrong song

Music and sound numbers are the ones each game itself uses, as listed in its
disassembly: Sonic 2's Emerald Hill is `$82` (130), not 129. Pick songs by name
in the editor, or write `"music": "EMERALD_HILL"` in a screenplay; the
`catalog` command lists every song's name and number. Scenes made with older
development builds that used other numbers must be updated by hand.

### "ROM cue gain must be 1"

Volume applies to your own recordings only; game music and sound effects always
play at their own level.

## Files and export

### Export says it will not overwrite

Every export, compile and decompile writes a new file. Choose a new name for each
take.

### Export needs a saved scene

**Export film** saves first. If the scene has never been saved, it asks where.

### Export fails straight away

Check that FFmpeg is installed and that a ROM is configured for every game in
the scene. The status line and **Direction details** give the message.

### Moving a scene breaks its images or sounds

Asset paths are relative to the scene's folder. Move the folder as a whole.
**Save as** refuses to move a scene away from assets it uses.

### My scene from an earlier build won't open

The scene and screenplay formats are unpublished and change between
development builds. Only the current version opens; older files are not
converted.

## Glossary

| Term | Meaning |
| --- | --- |
| Action | A controller input for a Puppet player, such as *move to*, *jump* or *wait*, starting at a frame. |
| Advanced | The collapsed part of a form that holds less common settings. Its values are kept while it is closed. |
| Asset | A file the scene uses besides the ROMs: a dialogue image, a recording or a saved level. |
| Cast | The scene's players and objects. |
| Catalog | The terminal command that lists a game's zones, music and characters. |
| Cue | An event at a frame: a caption, dialogue line, sound or camera shake. |
| Easing | How a key moves to the next: Hold (jump at the next key), Linear (steady) or Smooth (ease in and out). |
| Effort | How fast a Puppet tries to move: the share of its top speed, up to 1. Called `urgency` in screenplays. |
| Facing | Left or Right as seen on screen. |
| Film format | The film's size in pixels. |
| Floor snap | Attaching a position to the terrain below it. |
| Free | An object running its own game behaviour. |
| Frame | One sixtieth of a second. Frame 0 is the start. |
| Key | A position, pose, camera framing or visibility at a frame. |
| Loaded object | An object already placed in the level, which you can take over. |
| Mode | Puppet or Poser for players; Free, Puppet or Pose for objects. |
| Placement | An object's entry in the level's object list, identified by its index, object number and subtype. |
| Playhead | The current frame in the editor. |
| Pose (object) | An object placed exactly at each key, with a chosen sprite frame. |
| Poser (player) | A player placed by hand at each key. |
| Preset | A named speaker voice that you can copy and adjust. |
| Puppet (player) | A player moved by the game's physics through actions. |
| Puppet (object) | An object frozen in place and slid by move commands. |
| Route | A path planned under the game's physics and applied as actions. |
| Scene | A film or cutscene, saved as a `.stageplay` file. |
| Scene change | The start of a new shot, with a transition. |
| Screenplay | The scene written as JSON, shot by shot. |
| Set | The level a shot takes place in. |
| Shot | A continuous part of a scene in one set. Each shot starts a fresh copy of its level. |
| Style | Reusable dialogue panel colours and decoration (`dialogueSkins` in screenplays). |
| Take | One try at a scene or a recording; export and compile never overwrite earlier takes. |
| Transition | Cut, fade or wipe between shots. |
| Typer | Reusable dialogue typing speed and pauses. |
| Voice | The chip blips a speaker makes as their text types out. |
| World command | Clear badniks, clear objects or remove one object. |
