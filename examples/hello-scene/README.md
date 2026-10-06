# Hello Scene: the smallest useful mod scene

A starting point for your own **mod scene**, a mod that owns the whole screen. When you choose
Sonic 3 & Knuckles on the master title, the engine opens this scene instead of the stock title.
Sonic runs and jumps over Angel Island's background, collecting rings. Everything on screen
and every sound comes from your own ROM at runtime; the mod holds only code.

It is two classes and a manifest:

```
src/main/resources/META-INF/openggf-mod.yaml   who the mod is: id, base game (s3k), entry point
src/main/java/hello/HelloSceneMod.java         the entry point: registers the startup scene
src/main/java/hello/HelloScene.java            the scene: enter, update, draw
```

`HelloScene` uses each part of the scene API once: a zone background, a playable character
and a ROM sprite by address, the controller and the mouse, music and sound effects, saved data
(the best ring count), text and plain fills, and leaving for the stock game.

## Build and run

You need Java 21 and Maven as for the engine, and your Sonic 3 & Knuckles ROM set up for
OpenGGF in the repository root.

```bash
python3 examples/build_example.py hello-scene --run   # build the engine and the mod, then play
python3 examples/build_example.py hello-scene         # just package target/examples/hello-scene/hello-scene.jar
```

`--run` starts the engine with the mod as a development mod. To install the jar instead,
copy it into `mods/` and enable it in the Mod Manager. Holding Escape returns to the master
title, as everywhere else.

## Make it yours

1. Copy `examples/hello-scene` to `examples/<your-mod>`.
2. In the manifest, change `id`, `name`, `description` and `entrypoint`, and rename the
   `hello` package to match.
3. Build it with `python3 examples/build_example.py <your-mod> --run`.

Then read the [mod scene guide](../../docs/modding/guides/mod-scenes.md) for the whole API
(ROM sprites and palettes, level pictures, title cards, input, storage, testing) and the
[Slay the Robotnik example](../slay-the-robotnik/README.md) for a complete game built on it.

Things that trip people up:

- **No static state.** The mod validator rejects enums, static collections and static
  initialisers. Keep state in fields of your scene; `static final` numbers and strings are
  fine.
- **Change state only in `update`.** The engine may skip or repeat `draw` (headless capture
  draws only some frames), so `draw` should only read your fields.
- **ROM addresses** come from the disassembly's listing file (`sonic3k.lst`), not from line
  numbers. `ggfmod sprites` draws every frame of a sprite at those addresses so you can check
  them before writing code.
