# Sample Flappy

This project uses both OpenGGF artifacts: the engine jar supplies the public mod API,
and the `openggf-mod-sdk` classifier supplies `ggfmod` and its templates.

This gallery sample targets Mod API 2.1 and demonstrates additive-zone content built
entirely from a repeating traversal object plus a ROM-materialized playable-adjacent
bird sprite: a pipe badnik/obstacle placed across a Sonic 2 level export, a controller
object, and object art sourced from the user's own Sonic 2 ROM at gameplay launch
(Tails' flight frames) via `ModContext#registerRomObjectArt`.

1. Convert ordinary object art with `ggfmod convert art`; `.ggfs` is an object sheet.
2. Convert `src/main/mod/level-source` with `ggfmod convert level`.
3. Compile classes and package the build output with `ggfmod package`.
4. Launch an exploded build explicitly with `ggfmod run`.

See `docs/modding/content-mods.md` and `docs/architecture/mod-api-compatibility.md`
in the OpenGGF source tree for the complete creator contracts and checked-in
acceptance samples.
