# Start creating a mod

The current Mod API is the **mutable, unpublished 0.7.0 candidate**. Use matching
artifacts built at the same engine commit; a candidate API range does not promise
binary compatibility between commits. Rebuild code mods when changing candidates.
No Maven repository publication or stable creator baseline is implied.

## Tools and artifacts

Install a Java **21 JDK** and Maven **3.8 or newer**. Check `java -version`,
`javac -version` and `mvn -v`; Maven must run on Java 21. The starters pin their
compiler plugin instead of depending on a Maven installation's default.
Music/reskin projects need no Java authoring. Tiled is optional for map editing;
Python 3 is used by the checkout's example launcher and creator-kit builder.

A creator kit contains matching `engine.jar`, `sdk.jar`, `mod-testkit.jar`, exact
API Javadoc, launchers, handbook and source starters. Its `creator-kit.json`
records engine version, candidate status, originating commit and artifact hashes.
Read that record when reporting a problem. Kit builds do not publish the API.
The kit normalizes jar ZIP ordering/timestamps without changing payload bytes, then
hashes those distributed copies. Rebuild from committed sources; `--allow-dirty`
explicitly labels a local development kit and is not used by candidate artifact CI.

Until candidate kits are distributed, build them from an OpenGGF checkout:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests -Puniversal-jar verify
python3 tools/modding/build_creator_kit.py --out /absolute/path/OpenGGF-creator-kit.zip
```

The engine JAR contains API/runtime dependencies; the separate SDK contains the
CLI and converters. Neither a native executable nor the SDK alone is a Java
compile/run classpath. Obtain ROMs yourself; do not package ROMs or exported stock
asset bytes with your mod. Code mods require the JVM engine; data-only music and
object reskins also work on native builds.

## First project

Unpack the creator kit outside the engine checkout. Its `ggfmod`/`ggfmod.ps1`
launcher knows the kit's jars. In a source checkout the launcher takes **absolute**
engine and SDK paths first:

```sh
sh docs/modding/ggfmod /absolute/path/engine.jar /absolute/path/sdk.jar init /absolute/path/my-mod --id my-mod --package example.mymod
```

Choose a purpose using `--kind music|reskin|object|character|zone|scene|standalone`.
Without `--kind`, the backwards-compatible starter contains an object and Sonic 2
zone. The object/zone starter also includes an inactive character source stub;
finish its art and sensors before registering it for gameplay.

The generated README states the chosen scope and build output. For any starter,
build with the two matching absolute artifact paths:

```sh
mvn -f /absolute/path/my-mod/pom.xml package -Dopenggf.engine.jar=/absolute/path/engine.jar -Dopenggf.sdk.jar=/absolute/path/sdk.jar
```

From an OpenGGF worktree, put the Maven command through its queue instead:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -f /absolute/path/my-mod/pom.xml package -Dopenggf.engine.jar=/absolute/path/engine.jar -Dopenggf.sdk.jar=/absolute/path/sdk.jar
```

Each build removes only its generated outputs and reconverts current source art.
Edit a source/PNG, run the same build again, and validate the new jar. Do not point
converter outputs at authored source files: converters intentionally never clobber.
The authoritative distributable is the `*-mod.jar` produced by `ggfmod package`.

For a worked campaign, the kit's `examples/tide-circuit` contains two hosted acts,
all original binary assets and their reproducible Python generator. Follow its
portable README and the [two-act campaign guide](guides/two-act-campaign.md).
Compile/package without a ROM; playing requires your own Sonic 2 World REV01 ROM.
This maintained example complements the seven starter purposes.

Launch the project's exploded `target/classes` with `ggfmod run`. This snapshots
and trusts only that development mod for that launch; edit/build/restart to iterate.
It does not overwrite installed-mod settings. Configure your own ROM paths in the
launch working directory's `config.yaml`; the portable example launcher accepts
explicit `--s1`, `--s2` and `--s3k` paths. A standalone starter needs no ROM.

## Verify and share

Run `ggfmod validate <mod.jar>` and review every warning. `--format json` produces
structured findings; `--warnings error` is an explicit CI policy for clean public
API consumers. Warnings about intentional internal references remain useful even
when a prototype opts into `--warnings allow`.

Check actual visible/gameplay behavior, rewind and save/load. A validation pass
proves structure, not that your chosen stock art key or animation is used.
[Mod scenes](guides/mod-scenes.md#6-testing-a-scene) describes deterministic
scene tests/captures; [production-backed Jupiter tests](testing.md) explains the
distributed testkit, local matching-artifact setup and its coverage limits.

Share the validated jar, supported engine version/commit, required game/ROM,
dependency versions, license/provenance, short install instructions and a changelog.
Do not distribute generated engine trust/settings or your personal saves. Point
recipients to [installing and updating mods](installing-mods.md).
