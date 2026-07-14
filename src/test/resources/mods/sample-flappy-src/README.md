# Sample Flappy mod source

`project/` is the checked-in output of `ggfmod init --id sample-flappy --package
example.flappysample`, hand-adapted into the gallery's sixth sample: an additive
Sonic 2 patch that seizes the player at a fixed point after Emerald Hill Zone Act 2,
hides the native sprite, and flies a Tails-shaped "bird" (materialized from the
player's own ROM at launch via `ModContext.registerRomObjectArt`, Mod API 2.1.0) under
one-button gravity/flap control through a 42-pipe corridor, scoring rings for cleared
gaps and handing off to the engine's own hurt/death/respawn on contact. It contains
only original/generated test assets (the pipe sprite and the level layout); the bird
art is never checked in because it is decoded from the player's ROM, not shipped.

For a narrated, chapter-by-chapter walkthrough of how this project is built, see
[`docs/modding/guides/flappy-remix.md`](../../../../../docs/modding/guides/flappy-remix.md)
in the OpenGGF source tree.

## Build

The scripts take three positional arguments: an OpenGGF engine jar, the matching
`openggf-mod-sdk` classifier jar, and a new output directory. They copy `project/`,
decode the checked-in base64 level binaries, then run Maven.

PowerShell:

```powershell
./build.ps1 C:\path\OpenGGF-0.6.prerelease-jar-with-dependencies.jar C:\path\OpenGGF-0.6.prerelease-openggf-mod-sdk.jar C:\temp\sample-flappy
```

POSIX shell:

```sh
./build.sh /path/OpenGGF-0.6.prerelease-jar-with-dependencies.jar /path/OpenGGF-0.6.prerelease-openggf-mod-sdk.jar /tmp/sample-flappy
```

The packed output is:

```text
<output>/target/sample-flappy-mod.jar
```

The Maven lifecycle runs the real `ggfmod convert art` and `ggfmod convert level`
commands during `generate-resources` (baking `target/classes/art/pipe.ggfs` and
`target/classes/levels/flappy/`), then packages `target/classes` with
`ggfmod package` during `prepare-package`.

## What acceptance proves

`TestSampleFlappyIntegration` (ROM-gated on `s2.gen`) builds and validates this
project from source, loads it through a real owner class loader, resolves the
`flappy-garden` zone, and drives real frames to verify:

- the ROM-baked `sample-flappy:bird` sheet resolves to a renderer and a materialized
  sheet that actually contains fly frames 94/95, each with real mapping pieces;
- the controller seizes and hides the native player within a few frames of level
  entry;
- unflapped flight sinks under gravity, and a jump edge reverses that trend;
- the camera force-scrolls monotonically forward every frame, driven by the
  controller's constant forward speed;
- clearing the first pipe's gap scores exactly one ring;
- an unavoided pipe or kill-bound contact releases object control, unhides the
  player, and hands off to the engine's own hurt/death; and
- the engine's own pit-death flow reaches a real respawn request, after which the
  controller re-seizes and re-hides the player.

`TestSampleModsPackage` builds this project alongside the other five gallery sources
as one repository and confirms it validates with zero findings. No built jar is
checked in.
