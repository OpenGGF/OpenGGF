# Phase2 Sample

This project uses both OpenGGF artifacts: the engine jar supplies the public mod API,
and the `openggf-mod-sdk` classifier supplies `ggfmod` and its templates.

This checked Phase 2 compatibility sample targets Mod API 1.1 and includes a sample object,
a Sonic 2 level export, and a Phase 3 character stub. The stub demonstrates registration and
identity only: add playable art and terrain sensors before enabling it in gameplay.

1. Convert ordinary object art with `ggfmod convert art`; `.ggfs` is an object sheet.
2. Convert playable art with `ggfmod convert art --playable`; `.ggfp` includes the
   playable animation and generated DPLC sections.
3. Convert `src/main/mod/level-source` with `ggfmod convert level`.
4. Compile classes and package the build output with `ggfmod package`.
5. Launch an exploded build explicitly with `ggfmod run`.

See `docs/modding/content-mods.md`, `docs/modding/characters.md`, and
`docs/modding/standalone-games.md` in the OpenGGF source tree for the complete
creator contracts and checked-in acceptance samples.
