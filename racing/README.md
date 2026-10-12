# Racing: the Time Attack mod and the racing server

Time Attack is a mod; reusable networking stays in the engine:

```
src/main/java/com/openggf/net/   shared protocol, room services, clients, identity,
                               JDK room host and ghost codec
racing/time-attack/             Time Attack rules, menus, settings, ghosts and race flow
                               (openggf.timeattack)
racing/server/                  master server, Netty dedicated host, attempt verifier,
                               load-test and track-profile tools (openggf.racing.server)
```

The networking contracts marked `@ModApi` are available to any mod without installing
Time Attack. Internal handshake and WebSocket parsers remain implementation details.
The core dependency fence prevents networking from calling gameplay owners or mod code.
For custom messages and rules, scene mods can use the existing owner-scoped `SceneNetwork`
API (used by Sitar Hero). Putt Putt Paradise's existing direct TCP implementation is
unchanged; it can adopt shared transport separately from Time Attack's racing protocol.

The mod reaches the engine only through the public Mod API: a master-title entry, scene-launched
gameplay runs and the run host (see
[Title entries and gameplay runs](../docs/modding/guides/gameplay-runs.md)). Each run is a stock,
non-saving session with no mod content, so ghost times stay comparable across players.

## Playing and existing data

Open **Extras > Time Attack**. The same mod provides Solo, Host LAN, Join LAN and Browse.
**R** retries an act by default; **Settings > Retry key** captures another keyboard key.
Best and imported ghosts remain in `ghosts/`, and player identity remains in `identity/`.

On first opening, if the mod has no settings file, it imports the old engine's retry,
network and minimap preferences from `config.yaml` or `config.json` and their `.bak` or
numbered backups. Live configuration values take precedence over backup values. It writes
`settings.txt` in the mod's owner storage without changing the source files or replacing
existing mod settings. Configuration files are limited to 1 MiB for this import.

## Build and test

Both projects compile and run their tests with the engine's test classpath, so the ordinary
suite covers them along with the shared networking tests:

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=com/openggf/net/**/Test*,openggf/racing/**/Test*,openggf/timeattack/**/Test*" test
```

`python3 tools/testing/maven_queue.py -Dmse=off package` packages `racing/time-attack` as the bundled mod (`openggf.bundled.mods` in
`pom.xml`) through `ggfmod package` with warnings as errors; `TestTimeAttackModPackage` checks the
same thing in the suite. Native builds do not load code mods, so Time Attack is JVM-only.

## Running the server tools

```bash
python3 racing/server/build.py
target/racing-server/racing-server master --config master.yaml
target/racing-server/racing-server verifier --master https://host:27900 --registration-token <token> --rom s3k.gen --data ./verifier-data
target/racing-server/racing-server load-test --n 256 --duration 30 --mix adversarial
```

The verifier embeds the engine to replay attempts, so build it from the same engine commit as the
clients it verifies. Master settings are documented in
[CONFIGURATION.md](../CONFIGURATION.md#racing-server-master-verifier-settings).
