# OpenGGF Stageplay

Stageplay is OpenGGF's film and cutscene director. You cast characters and
objects from Sonic 1, Sonic 2 and Sonic 3 & Knuckles, put them in any of their
levels, and direct them: where they go, when they jump, what they say, where
the camera looks and what music plays. Stageplay then plays the scene, exports
it as a film, or hands it to a mod as an in-game cutscene.

![Day Off, a short film made with Stageplay: Sonic 1's Motobug says "Thirty-five years" on a Green Hill ledge.](images/stageplay/day-off-motobug.png)

*A frame from [Day Off](stageplay-day-off.md), made entirely in Stageplay.*

The characters are not animated by hand. A Puppet character runs on the game's
own physics, so you give it directions and it walks, jumps, skids and lands the
way it does in the game. You can also pose characters exactly, frame by frame,
when a moment calls for it.

This guide follows the **0.7.prerelease development line**. Stageplay is not
part of the published 0.6 release; use a current development build of the
engine and, if you want it, the desktop editor.

## What you need

- A development build of OpenGGF (0.7.prerelease).
- Your own ROMs for each game you use. OpenGGF reads all art, music and
  sounds from them; scene files contain none.
- For films: FFmpeg on your `PATH`.
- Optional: [OpenGGF Editor](https://github.com/OpenGGF/OpenGGF-Editor), the
  desktop app. It edits the same scene files as the editor built into the engine.

## Ways to work

You can direct a scene in three places, and switch between them at any time,
because they all read and write the same `.stageplay` file.

- **The in-engine editor.** Press **F4** in the level editor. It works with a
  keyboard or a controller.
- **OpenGGF Editor.** The desktop app shows the same scene in docked panels,
  with mouse picking in the preview.
- **A screenplay.** A JSON file written shot by shot, which you can edit by hand
  or generate with a script. The terminal tools compile it, check it and render
  it without opening an editor.

## Learn Stageplay

| If you want to… | Read |
| --- | --- |
| Make a first short film, step by step | [Your first film](stageplay-first-film.md) |
| Do one particular thing: cast, routes, objects, camera, dialogue, music, scene changes, export, mods | [Directing guide](stageplay-directing.md) |
| See how a finished film was made | [How Day Off was made](stageplay-day-off.md) |
| Fix a problem or understand a message | [Troubleshooting and glossary](stageplay-troubleshooting.md) |
| Look up a field, key, command or limit | [Reference](stageplay-reference.md) |
| Work from a terminal or with an agent | [Stageplay from the terminal](stageplay-authoring.md) |
| Copy working scenes | [Examples](../../../examples/stageplay-demo/README.md) |

## The ideas in one minute

- A **scene** is a film or cutscene. It is made of **shots**. Each shot takes
  place in one **set**: a level from one of the three games.
- The **cast** are the players (Sonic, Tails and Knuckles) and **objects**
  (badniks, animals, bosses, platforms and other level objects).
- A player is a **Puppet** or a **Poser**. You give a Puppet **actions** such as
  *move to*, *jump* or *wait*, and the game's physics moves it. You place a
  Poser with **keys**: exact positions and poses at chosen frames.
- An object is **Free** (it runs its own game behaviour), **Puppet** (frozen in
  place so you can slide it around) or **Pose** (placed exactly at each key). An
  object can switch modes during a shot.
- **Facing** is simply Left or Right as you see it on screen. The game owns how
  each sprite is drawn when it turns.
- The **camera** has its own keys, with pans, zooms, following and shake.
- **Dialogue** lines name a speaker, type out on screen and give each speaker a
  voice made from the game chip's sounds.
- **Audio cues** start music, sound effects or your own recordings.
- A **scene change** starts a new shot, in the same level or another game, with
  a cut, fade or wipe.

Every shot starts a fresh copy of its level, so what happens in one shot never
leaks into the next.

## Status and limits

Stageplay is new and still changing. In this version:

- Players can be Sonic (all three games), Tails (Sonic 2 and 3 & Knuckles) and
  Knuckles (Sonic 3 & Knuckles).
- Route planning starts at the beginning of a shot and plans one destination at
  a time. It may report that it ran out of search time without proving a place
  is unreachable.
- Floors found by **Place on floor** and the picker are terrain only; moving
  platforms are not included.
- Branching dialogue, reusable shot libraries, lip sync and shared editing are
  not supported.

The [troubleshooting page](stageplay-troubleshooting.md) covers the common
surprises.
