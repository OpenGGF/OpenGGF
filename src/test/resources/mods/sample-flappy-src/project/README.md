# Sample Flappy

Use a Java 21 JDK and matching engine/SDK artifacts from one commit of the mutable,
unpublished candidate. The default Maven profile uses local jars; it does not
resolve an unpublished API from a Maven repository:

```sh
mvn package -Dopenggf.engine.jar=/absolute/path/engine.jar -Dopenggf.sdk.jar=/absolute/path/sdk.jar
```

After editing source, repeat the same command. The lifecycle removes only generated
outputs before reconverting current assets; authored sources stay intact.

This project uses both OpenGGF artifacts: the engine jar supplies the public mod
API, and the `openggf-mod-sdk` classifier supplies `ggfmod` and its templates.

The sample targets Mod API 0.7 as a Sonic 3 & Knuckles patch. It demonstrates an
anchorless game-start contribution with a Tails-only launch team, deterministic
input filtering, a custom HUD profile, an exact v2 S3K level source, and a native
Tails controller. The controller fixes Tails' horizontal position while preserving
his built-in vertical flight mechanics and refilling the normal flight timer each
frame. The mod ships only generated sky and pipe assets; it consumes no ROM art.

See the maintained
[`docs/modding/guides/native-tails-flappy.md`](../../../../../../docs/modding/guides/native-tails-flappy.md)
for the complete architecture, palette, rewind, build, and presentation trade-offs.

1. Convert ordinary object art with `ggfmod convert art`; `.ggfs` is an object sheet.
2. Convert `src/main/mod/level-source` with `ggfmod convert level`.
3. Compile classes and package the build output with `ggfmod package`.
4. Launch an exploded build explicitly with `ggfmod run`.

See `docs/modding/content-mods.md` and `docs/architecture/mod-api-compatibility.md`
in the OpenGGF source tree for the complete creator contracts and checked-in
acceptance samples.
