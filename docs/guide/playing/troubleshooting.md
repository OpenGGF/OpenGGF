# Troubleshooting

## Black screen or crash on startup

**Wrong ROM revision.** The engine is verified against specific ROM revisions. Using a
different revision (e.g., Sonic 2 REV00 instead of REV01) will cause incorrect ROM address
lookups, which can result in crashes, garbled graphics, or a black screen.

Check that your ROM files match these revisions:

| Game | Required Revision |
|------|-------------------|
| Sonic 1 | World, REV01 |
| Sonic 2 | World, REV01 |
| Sonic 3&K | World, lock-on combined |

**Missing ROM file.** Current development builds keep missing games on the hub
and offer **Locate ROM** or **Add your games → Scan a folder**. Files may remain
in their original locations. The default scan reads the working directory;
`roms.directory` changes it. A non-default explicit path must exist, so correct
or clear a stale override in **Settings → Files** or `config.yaml`. See
[Configuration](configuration.md#how-do-i-use-different-rom-filenames).

**Java version too old.** JAR builds require Java 25 or later; native release
executables do not need a separate Java installation. Run `java -version` to
check the runtime used for a JAR. On macOS, launch it with
`java -XstartOnFirstThread -jar <your-jar-file>`.

**OpenGL version too low.** The engine requires an OpenGL 4.1 core context.
Support depends on the GPU and operating-system driver, including remote or
virtual display drivers. Check that your driver provides that context. On Linux, ensure you have the correct Mesa or
proprietary driver installed.

## No sound

**Audio disabled in config.** Check that `audio.enabled` is `true` (or absent, since
`true` is the default) in `config.yaml`.

**System audio driver issues.** The engine uses LWJGL's OpenAL backend. If your system
audio is routed through an unusual driver or virtual device, playback may fail silently.
Try updating your audio driver or switching to a different output device.

**Audio internal rate mismatch.** If `audio.internalRateOutput` is set to `true`, the
engine outputs at the YM2612 internal sample rate (~53 kHz), which some audio drivers
cannot handle. Set it to `false` (the default).

## Game runs too fast or too slow

**FPS setting.** `display.fps` limits window rendering. Lower or higher settings
leave gameplay and audio at the region cadence; VSync may limit actual window FPS.

**Display pacing.** With `display.pacing: "AUTO"` (the default) on a 59.94 Hz display,
the game follows the display and runs 0.1% slow so motion stays even. Set
`display.pacing: "EXACT"` to keep nominal 60 Hz or 50 Hz speed; a picture may then
repeat or be skipped every few seconds.

**Region setting.** PAL mode (`audio.region: "PAL"`) runs at 50 Hz, which makes the game
at a lower update rate than the default NTSC setting. Simulation and audio stay at 50 Hz
even if `display.fps` is different. Region support is not a claim of complete
PAL hardware parity.

**System performance.** If simulation alone cannot sustain the region rate, the game will run
slowly. Check that your GPU driver supports OpenGL 4.1 and that no other application is
consuming GPU resources.

## Garbled graphics or wrong colors

**GPU driver issues.** Update your graphics driver to the latest version. The engine uses
OpenGL 4.1 core profile features that may not work correctly with outdated drivers.

**Window sizing.** If `display.windowAutosize` is disabled and `debug.window.width` /
`debug.window.height` are set to unusual values, the output may look incorrect. Re-enable
autosize or reset the debug window dimensions.

**Wrong ROM revision.** Some graphics data is loaded from specific ROM offsets. A different
ROM revision will cause art to be loaded from wrong addresses, producing garbled tiles.

## "Object registry missing id 0xNN" warnings

These log messages appear when the engine encounters an object in a level's placement data
that it does not have an implementation for. This is normal for zones or games that are
still being developed. The unimplemented object will simply not appear in the level.

Check the object checklists to see whether the type is registered. A registered
implementation can still have behavior or route gaps:

- [Sonic 2 Object Checklist](../../../OBJECT_CHECKLIST.md)
- [Sonic 1 Object Checklist](../../../S1_OBJECT_CHECKLIST.md)
- [Sonic 3&K Object Checklist](../../../S3K_OBJECT_CHECKLIST.md)

## Special stage issues

**Entering special stages:** In Sonic 2, special stages are entered through checkpoints
with 50+ rings. In Sonic 1, they are entered through giant rings at the end of an act.

**Visual differences:** Special stage rendering is functional but may not be pixel-perfect
compared to the original. This is a known area for improvement.

## An S3K route or late-game sequence fails

The 0.7 development branch includes late zones and native ending flows. A failure
beyond Angel Island is not automatically an unimplemented zone. Check
[Game Status](game-status.md) and the [S3K bug list](../../status/s3k-known-bugs.md)
for the affected route. When reporting a problem, include the build/commit, ROM
identity, zone/act, character and sidekick, donor, aspect ratio, entry path
(new game, checkpoint or side-stage return), and steps or a recording to reproduce
it. Published 0.6 builds have a different feature set.
