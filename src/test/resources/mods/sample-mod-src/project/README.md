# Phase2 Sample

This project uses both OpenGGF artifacts: the engine jar supplies the public mod API,
and the `openggf-mod-sdk` classifier supplies `ggfmod` and its templates.

The generated patch targets Mod API 0.7 and includes a sample object, a Sonic 2
level export, and a Phase 3 character stub. The stub demonstrates character identity
and is deliberately not registered: add playable art and terrain sensors
before registering it in gameplay.

1. Edit `SampleBadnik`, `src/main/mod/sample.png`, or the level source under
   `src/main/mod/level-source` in this generated object/zone project.
2. Run the Maven package command below; it converts art/levels, compiles Java, and
   packages the validated mod automatically.
3. Validate the resulting `target/phase2-sample-mod.jar` and review its findings.
4. Follow the handbook's ROM configuration and isolated runtime-directory setup,
   then launch the exploded `target/classes` with the kit's `ggfmod` launcher.

Manual converter commands require fresh output paths. The normal Maven lifecycle
removes its own generated outputs before conversion, so it can be repeated after edits.

See `docs/modding/content-mods.md`, `docs/modding/characters.md`, and
`docs/modding/standalone-games.md` in the OpenGGF source tree for the complete
creator contracts and checked-in acceptance samples.

Use Java 21 and Maven 3.8+. The current 0.7 API is unpublished and mutable; the
engine and SDK jars must come from the same candidate commit. Build using absolute paths:

```sh
mvn package -Dopenggf.engine.jar=/absolute/path/engine.jar -Dopenggf.sdk.jar=/absolute/path/sdk.jar
```

The validated output is `target/phase2-sample-mod.jar`; the ordinary Maven jar is not
the distribution boundary. Rebuild with the same command after source/art edits.
Generated outputs are removed automatically; authored sources are preserved.
Run `ggfmod run target/classes` for an isolated development launch and
`ggfmod validate target/phase2-sample-mod.jar --warnings error` for clean public-API CI.
The SDK and engine alone are sufficient; no unpublished Maven coordinates need resolving.
