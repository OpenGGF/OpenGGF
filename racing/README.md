# Racing: the Time Attack mod and the racing server

Time Attack and multiplayer racing are not engine code. They live here:

```
racing/time-attack/   the bundled Time Attack mod (openggf.timeattack) and the engine-free
                      racing library it carries (openggf.racing: protocol, hub, client,
                      identity, JDK room host, ghost codec)
racing/server/        master server, Netty dedicated host, attempt verifier and the bot,
                      load-test and track-profile tools (openggf.racing.server)
```

The mod reaches the engine only through the public Mod API: a master-title entry, scene-launched
gameplay runs and the run host (see
[Title entries and gameplay runs](../docs/modding/guides/gameplay-runs.md)). Each run is a stock,
non-saving session with no mod content, so ghost times stay comparable across players.

## Build and test

Both projects compile and run their tests with the engine's test classpath, so the ordinary
suite covers them (`python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=openggf/racing/**/Test*,openggf/timeattack/**/Test*" test`).
`mvn package` packages `racing/time-attack` as the bundled mod (`openggf.bundled.mods` in
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
