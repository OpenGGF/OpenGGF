# Start creating a mod

The current Mod API is the **mutable, unpublished 0.7.0 candidate**. Use matching
artifacts built at the same engine commit; a candidate API range does not promise
binary compatibility between commits. Rebuild code mods when changing candidates.
No Maven repository publication or stable creator baseline is implied.

## Game contract import migration

Replace `import com.openggf.data.Game;` with
`import com.openggf.game.Game;` in custom loaders, factory overrides and callers.
The complete abstract class now belongs beside `GameModule` and `ModGame`; its
methods, exceptions and default behavior are unchanged. There is no forwarding
class at the old name.

Rebuild every compiled mod, donor implementation and caller against the same
updated SDK and engine candidate. `ModGame`'s superclass and factory/getter JVM
return descriptors changed, including for callers whose source uses inferred
types or `StandaloneGameSpec` without naming `Game`. The same `>=0.7.0 <0.8.0`
manifest cannot distinguish these unpublished binary contracts, and the loader
does not automatically reject every old binary. The descriptor remains candidate
`0.7.0` with no published baseline. See the
[compatibility decision](../architecture/mod-api-compatibility.md#game-contract-candidate-migration)
and [standalone guidance](standalone-games.md).

## Player-art provider import migration

The current unpublished candidate intentionally breaks both previous art-provider
imports. Replace:

```java
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.SpindashDustArtProvider;
```

with:

```java
import com.openggf.sprites.art.PlayerSpriteArtProvider;
import com.openggf.sprites.art.SpindashDustArtProvider;
```

Rebuild every compiled mod, donor implementation and caller against the matching
updated engine artifacts. Compiled interface names and donor-factory return
descriptors changed, so an old binary is not compatible even if its manifest's
`engineApiRange` still includes `0.7.0`. The manifest range cannot distinguish
these two unpublished binary contracts. Old candidate binaries are retired;
there are no forwarding interfaces. Provider methods, default palette behavior
and the canonical `Palette`/`SpriteArtSet` models are unchanged. The API remains
an unpublished `0.7.0` candidate with no published baseline; see the
[compatibility decision](../architecture/mod-api-compatibility.md#player-art-provider-candidate-migration).

## Tools and artifacts

Install a Java **25 JDK** and Python 3 for the portable launcher below. Maven
**3.8 or newer** is needed for the advanced Maven workflow and for resolving
JUnit when testing with an uncached console jar. Maven must run on Java 25.
In an engine checkout, use `python3 tools/testing/maven_queue.py -v` for a
leased version probe. The starters pin their compiler plugin.
Music/reskin projects need no Java authoring. Tiled is optional for map editing;
Python 3 is used by the checkout's example launcher and creator-kit builder.

A creator kit contains matching `engine.jar`, `sdk.jar`, `mod-testkit.jar`, exact
API Javadoc in `api-docs.jar`, launchers, handbook and source starters. Its `creator-kit.json`
records engine version, candidate status, originating commit and artifact hashes.
Read that record when reporting a problem. Kit builds do not publish the API.
The kit normalizes jar ZIP ordering/timestamps without changing payload bytes, then
hashes those distributed copies. Rebuild from committed sources; `--allow-dirty`
explicitly labels a local development kit and is not used by candidate artifact CI.

To browse the API reference, unzip `api-docs.jar` into its own directory and open
that directory's `index.html` in a browser. The Java 25 JDK's `jar` tool can extract it:

```sh
mkdir /absolute/path/creator-kit/api-docs
cd /absolute/path/creator-kit/api-docs
jar --extract --file /absolute/path/creator-kit/api-docs.jar
```

Until candidate kits are distributed, build them from an OpenGGF checkout:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests -Puniversal-jar verify
python3 tools/modding/build_creator_kit.py --out /absolute/path/OpenGGF-creator-kit.zip
```

The engine JAR contains API/runtime dependencies; the separate SDK contains the
CLI and converters. Neither a native executable nor the SDK alone is a Java
compile/run classpath. Obtain ROMs yourself; do not package ROMs or exported stock
asset bytes with your mod. Code and data mods run in both the default native
engine and the JVM build; compiling mods still requires the Java toolchain.

## First project

Unpack the creator kit outside the engine checkout. Its `ggfmod`/`ggfmod.ps1`
launcher knows the kit's jars. Create a scene starter from the unpacked kit:

```sh
sh /absolute/path/creator-kit/ggfmod init /absolute/path/my-mod --id my-mod --kind scene --package example.mymod
```

In a source checkout the launcher takes **absolute** engine and SDK paths first:

```sh
sh docs/modding/ggfmod /absolute/path/engine.jar /absolute/path/sdk.jar init /absolute/path/my-mod --id my-mod --kind scene --package example.mymod
```

Choose a purpose using `--kind music|reskin|object|character|zone|scene|standalone`.
Without `--kind`, the backwards-compatible starter contains an object and Sonic 2
zone. The object/zone starter also includes an inactive character source stub;
finish its art and sensors before registering it for gameplay.

Use the existing portable launcher for the shortest build/test/run loop. It works
for generated starters and exported projects; replace the absolute paths below.
From any directory:

```sh
python3 /absolute/path/creator-kit/tools/build_project.py /absolute/path/my-mod --doctor
python3 /absolute/path/creator-kit/tools/build_project.py /absolute/path/my-mod
python3 /absolute/path/creator-kit/tools/build_project.py /absolute/path/my-mod --run --s2 /absolute/path/own-sonic2.gen
```

The scene starter uses Sonic 2. For another game/donor supply `--s1`, `--s2`
or `--s3k` as needed. A standalone starter needs no ROM. The output is
`/absolute/path/my-mod/target/my-mod-mod.jar` (the project directory names the
portable output). The launcher copies main resources, runs an optional trusted
`generate_resources.py`, and converts the SDK starter's `src/main/mod/sample.png`
with `sample-sheet.yaml`, playable `runner.png` with `runner-sheet.yaml`, and
`level-source/` when present. Custom Maven plugin
workflows still use Maven. Test/run are opt-in; build needs neither ROMs nor testkit.

### Optional local paths

To avoid repeating artifact and ROM flags, create `ggfmod.local.json` at the
project root yourself. The launcher never creates or overwrites it. This strict
schema accepts only `schemaVersion`, `engine`, `sdk`, `testkit`, `s1`, `s2`, `s3k`;
path fields are optional nonempty strings, and duplicate/unknown keys fail:

```json
{
  "schemaVersion": 1,
  "engine": "../creator-kit/engine.jar",
  "sdk": "../creator-kit/sdk.jar",
  "testkit": "../creator-kit/mod-testkit.jar",
  "s2": "/absolute/path/own-sonic2.gen"
}
```

Every relative path in this file resolves against the **config file's directory**.
Explicit CLI paths override config and resolve against the current working
directory. Without either, engine/SDK default to the launcher's kit directory;
testkit and ROMs have no default. Missing default config is fine; an explicit
`--config /path/to/local.json` must exist and satisfy the schema. Even an overridden
config field must be valid. No environment expansion or secret copying occurs.
Keep personal paths out of version control and shared source archives. The
portable resource copier and creator-kit exporter exclude `ggfmod.local.json`;
store it at project root so Maven also keeps it outside resources. If choosing a
custom config filename, keep that file outside all source/resource/export trees.

Check before building or testing:

```sh
python3 /absolute/path/creator-kit/tools/build_project.py /absolute/path/my-mod --doctor --test --format json
python3 /absolute/path/creator-kit/tools/build_project.py /absolute/path/my-mod --test
```

Doctor exits nonzero for missing JDK 25 tools, artifact mismatch, missing/empty
manifest, invalid local config, unavailable supplied ROMs or requested test
prerequisites. It never compiles, launches, installs, downloads or writes files;
its only subprocesses are bounded JDK version probes. It does not require optional
ROMs/testkit for ordinary builds. JSON reports have `formatVersion: 1`,
`command: "doctor"`, `result: "pass"|"fail"`, `findings` and `limitations`.
Each finding has stable `code`, `severity`, `message`, `path` (possibly null) and
`guidance` fields. Passing checks availability and candidate identity, not full
manifest semantics, ROM identity, dependency downloads or gameplay correctness.
Run `ggfmod validate` and exercise the mod too.

### Advanced Maven workflow

The generated README and POM retain Maven builds with matching absolute artifacts:

```sh
mvn -f /absolute/path/my-mod/pom.xml package -Dopenggf.engine.jar=/absolute/path/creator-kit/engine.jar -Dopenggf.sdk.jar=/absolute/path/creator-kit/sdk.jar
```

From an OpenGGF worktree, put the Maven command through its queue instead:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -f /absolute/path/my-mod/pom.xml package -Dopenggf.engine.jar=/absolute/path/creator-kit/engine.jar -Dopenggf.sdk.jar=/absolute/path/creator-kit/sdk.jar
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

The portable `--run` command uses only this project's exploded `target/classes`
and generated `target/play/config.yaml`; saves remain under `target/play` too.
It snapshots and trusts only that development mod for that launch. Edit, rebuild
and restart to iterate without changing installed-mod settings. For manual
launches, `ggfmod run /absolute/path/my-mod/target/classes` uses the current working
directory's configuration; choose a dedicated directory and configure your ROMs
there. See [ROM file configuration](../../CONFIGURATION.md#rom-files).

## Verify and share

Validate this project's packaged jar with the kit launcher, from any directory:

```sh
sh /absolute/path/creator-kit/ggfmod validate /absolute/path/my-mod/target/my-mod-mod.jar --format json --warnings allow
```

Review every warning. `--format json` produces structured findings;
`--warnings error` is an explicit CI policy for clean public API consumers.
This prototype permits warnings about intentional internal references with
`--warnings allow`; those findings still need review. Share the validated
`/absolute/path/my-mod/target/my-mod-mod.jar`. The isolated `target/play` directory
contains generated runtime configuration and saves, not the distributable.

Check actual visible/gameplay behavior, rewind and save/load. A validation pass
proves structure, not that your chosen stock art key or animation is used.
[Mod scenes](guides/mod-scenes.md#6-testing-a-scene) describes deterministic
scene tests/captures; [production-backed Jupiter tests](testing.md) explains the
distributed testkit, local matching-artifact setup and its coverage limits.

Share the validated jar, supported engine version/commit, required game/ROM,
dependency versions, license/provenance, short install instructions and a changelog.
Do not distribute generated engine trust/settings or your personal saves. Point
recipients to [installing and updating mods](installing-mods.md).

The portable `build_project.py` build checks supported Mod API consumption by
default, both in attributed Java source and compiled classes. Unsupported engine
symbols fail with a source location and a facade hint. Advanced third-party authors
can explicitly choose `--expert-legacy` to retain the previous warning-based
validation. That expert option gives no compatibility promise for engine internals.
A few official examples still use it while their supported facades are written;
their exported READMEs say so, and new mods should not copy their engine references.
