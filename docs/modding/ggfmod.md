# `ggfmod` creator CLI

The creator CLI is packaged separately from the engine APIs. The mutable 0.7 candidate
is unpublished; use matching engine and SDK artifacts from the same commit, as described
in [getting started](getting-started.md). Put both artifacts on
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
compilable namespaced sample badnik, an inactive character source stub, a Genesis-exact sample
sheet, and a minimal level source in the editor's exact JSON/binary export format.
The character stub is deliberately not registered and has no
playable art or terrain sensors; complete it from the
[character guide](characters.md) before selecting it in gameplay.

`init <project> --id <id> [--package <java.package>] --kind <kind>` selects a complete
starter for `music`, `reskin`, `object`, `character`, `zone`, `scene`, or `standalone`.
Music and reskin projects use original generated assets and need no Java authoring.
Object and zone projects include an object placed in a small playable test zone.
Character and standalone projects contain the maintained playable fixtures, including
art and sensors. Scene projects own the screen through a startup scene. Without
`--kind`, `init` preserves the original object/zone starter. Existing outputs are
never replaced; generate into a new directory.

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

Both `package --input <classes> --out <mod.jar>` and `validate <mod.jar>` accept
`--format text|json` and `--warnings allow|error`. Successful packaging prints all
warnings. JSON uses format version 1, structural `valid`, warning count/policy,
and the same sorted finding records as text. Each finding preserves `severity`,
`code`, `location`, `member`, and `message`, with additive `guidance` metadata:
`documentation` is a repository-relative reference, `nextAction` is a safe suggested
step, and optional `supportedReplacement` names a known supported API alternative.
Unknown codes and internal references receive honest fallback guidance without a
guessed replacement. Text reports include the suggested next action too.
Consumers should ignore unknown metadata fields. For these two commands,
`--format json` also emits a single v1 report on invalid inputs or operational
failure, with an error finding and nonzero exit, without text/stack traces or a
package-success message. Such failures use `CLI_INPUT_INVALID`, `COMMAND_FAILED`,
or `VALIDATION_FAILED` and empty location/member when no source symbol is known.
`valid` describes structural findings, so it remains true for warning-only reports
even when strict warning policy makes the command fail. The default allows warnings; explicit
`--warnings error` returns failure on warnings and prevents package publication.
Internal API references can intentionally warn in experimental examples; review the
actual findings before selecting your CI policy. A successful structural validation
does not prove runtime behavior.

`art-keys [--game s1|s2|s3k|any]` lists the known stock object-art identifiers.
Use the game's catalogue: Sonic 2 requests `signpost`, whereas Sonic 3&K also has
`EndSign`. For the Sonic 2 reskin preserve its six consumer frames and the host
palette indices. Keys outside the manifest's game inventory produce
`UNKNOWN_ART_OVERRIDE_KEY` warnings. A known key can still be absent in the
selected game/zone; this list is a catalogue of identifiers, not a loading promise.

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


## Data-only editor campaigns

The editor's **Export playable campaign** emits a normal mod JAR with optional
manifest `campaign: campaign.json`, an explicit `baseGame` (`s1`, `s2`, `s3k`),
patch kind and no Java entrypoint. `insertAfter` is forbidden for these terminal
campaigns. The strict JSON declaration has this shape:

```json
{"name":"Two acts","game":"s2","localKey":"campaign","levels":[
  {"id":"act-1","level":"levels/act-1/level.json","next":"act-2"},
  {"id":"act-2","level":"levels/act-2/level.json","next":null}
]}
```

There are 1–256 unique IDs and normalized baked-definition paths. Every next link
must name the following entry and the last must be null. Unknown fields, mixed
hosts and different required teams are errors. Normal mod scanning validates the
assets and registers the existing multi-act contribution in the owner's transaction;
no code trust or generated Java is needed. Completion ends at credits. Built-in
host gizmos and bounded Stageplay dependencies are included; external creator
object/track dependencies still require a separately authored package.
