# `ggfmod` creator CLI

The creator CLI is published separately from the engine APIs. Put both artifacts on
the classpath: the `OpenGGF-0.7.prerelease-jar-with-dependencies.jar` supplies the public mod API
and runtime dependencies; `OpenGGF-0.7.prerelease-openggf-mod-sdk.jar` supplies only
the CLI, converters, packager, and project templates. The SDK classifier is not a
standalone jar.

Run `docs/modding/ggfmod.ps1` on Windows or `docs/modding/ggfmod` on macOS/Linux,
passing the two jar paths followed by a command. For example:

```text
ggfmod.ps1 OpenGGF-0.7.prerelease-jar-with-dependencies.jar OpenGGF-0.7.prerelease-openggf-mod-sdk.jar init my-mod --id my-mod --package example.mymod
```

The generated project targets Mod API 0.7 and contains a canonical manifest, a
compilable namespaced sample badnik, a Phase 3 character stub, a Genesis-exact sample
sheet, and a minimal level source in the editor's exact JSON/binary export format.
The character stub demonstrates owner-scoped registration but deliberately has no
playable art or terrain sensors; complete it from the
[character guide](characters.md) before selecting it in gameplay.

`convert art` has two distinct outputs:

```text
ggfmod convert art --image object.png --sheet object-sheet.yaml --out object.ggfs
ggfmod convert art --playable --image runner.png --sheet runner-sheet.yaml --out runner.ggfp
```

`.ggfs` is the ordinary baked object-sheet container. `.ggfp` is playable container
v2: it adds playable animation, palette, bank metadata, and per-frame DPLC sections.
`--playable` must appear exactly once, immediately after `convert art`. The current
converter emits trivial full-frame DPLC runs and prints their pattern-bank cost, so
the result is correct but may use more VRAM than a hand-authored DPLC layout.

`convert level` accepts either the exact full-level export format or the finite Tiled
domain documented by the level reference:

```text
ggfmod convert level --from-export <dir> --out <dir>
ggfmod convert level --from-tmx <map.tmx> --palette <GPAL> [--solid-tiles <profile-dir>] [--music <owner:localName>] --out <dir>
```

`--music <owner:localName>` declares a namespaced streamed track (the `TrackMusic` shape) instead
of the default `StockMusic(0)` placeholder. Standalone levels must carry a namespaced track owned
by the declaring mod (see `ModZoneLoader#loadStandalone`), so any `--from-tmx` level feeding a
standalone module needs this flag.

`convert audio` validates and copies WAV/OGG bytes without transcoding. `package`
creates a deterministic jar and validates its staging jar before atomic publication.
Use `ggfmod validate <mod.jar>` to print the sorted findings for an existing jar.

`run <build-output>` is the only development-directory entry point. It launches the
engine with `-Dggfmod.dev.modDir=<absolute-build-output>`. The engine snapshots that
directory once into engine-owned immutable storage and never rereads the creator tree
during the session. Merely enabling test mode does not enable directory loading.
The development mod is enabled and trusted for that launch. Mods on the title
screen shows its details and notices in a read-only view; enable/disable, ordering,
and Apply remain available for normally installed mods. Development launches do
not read or write installed-mod settings.

A development run of a patch mod skips the game picker and opens the mod's `baseGame`
directly (its startup scene, if it registers one); holding Escape still returns to the
master title, and deterministic test mode keeps the configured startup.

`sprites <rom> <s1|s2|s3k> <out.png> art=<addr> map=<addr> [...]` draws every mapping frame
of a ROM sprite into one numbered PNG grid, with each frame's origin marked, so you can check a
mod scene's `RomSpriteRequest` before writing code. Options mirror the request: `comp=`
(`NEMESIS`, `KOSINSKI`, `KOSINSKI_MODULED`, `UNCOMPRESSED`), `size=` (uncompressed bytes),
`dplc=` and `layout=` (`OBJECT` or `PLAYER`), `line=` (the palette line), `offset=` (a tile
offset), and `pal=<addr>:<colours>:<firstLine>`, which may repeat. `char=<sonic|tails|knuckles>`
instead boots the game headless, draws a playable character's frames and prints its animation
scripts. See the [mod scene guide](guides/mod-scenes.md#4-art-from-the-players-rom).

```text
ggfmod sprites s3k.gen s3k ring.png art=0x192AEE comp=NEMESIS map=0x01A99A line=1 pal=0x0A8A3C:16:0 pal=0x0A8B7C:48:1
ggfmod sprites s3k.gen s3k sonic-heads.png char=sonic heads=true first=0 count=32 scale=150
```

With `char=sonic heads=true`, `first`/`count` select native mapping frames and `scale`
accepts 101–200 percent. Each 112px review cell shows stock, enlarged head, unchanged body
and composite panels, with the neck anchor in magenta; stdout lists every pose's mask or
explicit stock fallback. Metadata recognizes the reviewed normal Sonic art in S1 REV01,
S2 REV01 and locked-on S3&K. Balls remain stock; unreviewed special poses, powered forms,
donor and custom art do not receive guessed head masks. All displayed pixels come from
the supplied ROM. The tool rejects opaque-pixel cropping rather than silently producing
an incomplete authoring reference.

For complete Mod API 0.7 examples, see [Content mods](content-mods.md),
[Playable characters](characters.md), and [Standalone games](standalone-games.md).
