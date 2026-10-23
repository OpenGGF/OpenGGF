# Racing Docker deployment

## Agreed scope and repository decision

Keep service source, packaging and deployment examples in this repository under
`racing/`. The master consumes shared networking and the verifier embeds the engine;
both must retain the client build identity. A separate GitHub repository would add
cross-repository version coordination without an independent release contract.
Images can be stored in an OCI registry independently of source-repository layout.

Deliver separate images for the existing combined master/relay process and the
verifier. Both support independent container launches; Compose is an optional
single-Linux-host example using the same images and interfaces. A standalone relay
worker, relay registration protocol, durable job recovery and automatic regional
allocation are separate work. No images or public deployment are published by this
task.

## Design

- Produce a relocatable distribution with engine/server classes, resources and
  dependency jars. Preserve the build's `version.properties`; no runtime Git
  checkout or Maven cache is required. Image builds consume this distribution,
  with a shared Java 25 runtime layer and distinct service entrypoints.
- Exclude ROMs, local configuration, secrets and development/reference trees from
  image build context. ROMs enter only as read-only verifier mounts. Initial
  qualification is Linux x86-64; extra architectures need their own native checks.
- Master YAML continues to configure its listeners and persistent data lives
  beneath `--data`. Add file-backed admin/registration secrets without removing
  existing literal-secret support. A verifier accepts a registration-token file,
  explicit HTTPS master URL, repeated ROM paths and an independent identity dir.
- Preserve normal TLS validation. Compose resolves a certificate-covered hostname
  to the master internally; public upload URLs remain externally reachable.
  Publish only the master's public port. Admin stays on container loopback.
- Add authenticated, non-mutating master readiness and a small Java healthcheck
  command. Workers retry initial registration as well as later transport failures.
  Graceful worker shutdown stops new leases and lets an active cycle finish within
  a bounded shutdown grace. Heartbeats still do not extend job leases.
- Use explicit verifier services/volumes in the Compose example. Sharing one
  identity volume across replicas is unsupported. Supply heap/resource limits,
  non-root execution, runtime secrets, restart policy and bounded logs.
- Document standalone and Compose setup, TLS/identity handling, backup, updates,
  and restart limitations. Containerization does not make rooms/jobs durable.

## Implementation and validation

1. Portable distribution and Docker packaging: `racing/server/build.py`, packaging
   regression tests, `racing/deploy/` images/build script/Compose configuration.
   Verify relocation, packaged build identity and exclusion of private inputs.
2. Service configuration/lifecycle: master secret loading and health endpoint;
   verifier token file, initial registration retry and bounded drain; focused Java
   tests for conflicts, unreadable/empty secrets, readiness, retry and stop states.
3. Container witness and documentation: real TLS master connection, requested
   recording upload and ROM-backed worker replay/verdict in standalone and Compose
   launches. Use task-owned runtime resources and existing absolute ROM paths.
   Verify persistent identities and two workers with distinct data directories.
4. Inspect the category plan against the pinned pre-task destination SHA. Run
   affected focused tests and proportionate change-based smoke coverage once;
   attribute disputed failures using matched baseline/current selectors. No
   release-only full suite. Review the combined change before integration.
5. Integrate into the main workspace's current `develop`, push only `develop`,
   and remove the accounted-for task worktree/branch after verification.

## Delivery evidence

The pre-task `develop` was `aebb390ed365b20ca0e7cd248ecb5b0761e2d729`.
Implementation was saved locally as `8c4e27b93`, then current `develop` at
`1f1649e86` was merged into the task branch as `97eed0f04`. The prerelease
changelog merged automatically; no conflict resolution or gameplay change was
needed. Main-workspace dirty reference submodules and unrelated files were
preserved. The service sources and packaging stay in this repository; no separate
GitHub project, registry publication or production deployment was created.

### Validation

- Tool preflight passed with Java 25 and `LUA_BIN=/usr/bin/lua5.4`. S1 REV01 and
  S3K ROMs matched the repository's canonical CRC32 and SHA-1 values. Existing
  absolute ROM paths were used; no ROM was copied into the distribution or image.
- `python3 -m unittest discover -s racing/server -p 'test_*.py' -v`: **10 passed**.
  Covers relocation, original identity/resources/dependency preservation,
  generated-context exclusion, tamper/platform rejection and permissions under a
  restrictive umask. Runtime relocation was also exercised with the real JVM.
- Compose CLI configuration validated both workers, separate identity volumes,
  only one published port, certificate-hostname alias, readiness dependency and
  read-only bind mounts. Shell examples parsed and documentation links resolved.
- The focused command below passed on `97eed0f04`: **113 tests, zero failures,
  errors or skips**, across 20 classes, including S3K ROM-backed replay. After the
  reboot it acquired admission immediately; Maven took 1 minute 25 seconds.
- `JAVA_HOME=/path/to/jdk-25 python3 racing/server/build.py --distribution` passed.
  The generated image context contained 80 runtime artifacts (about 78.5 MB),
  without operator files or ROMs.
- `python3 racing/deploy/build-images.py --skip-build --tag qualification` built
  both images using the owned Docker CLI/context. The Java base used was
  `eclipse-temurin:25-jre-noble` at
  `sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5`.
- A relocated distribution, standalone containers and the committed Compose file
  each passed a real private-CA TLS session, paced relay stream, authenticated
  upload and independently signed worker verdict. Each completed the natural
  GHZ1 input route: **3,230 frames simulated, 3,220 timed frames**, ghost hash
  `443a5f624161920d75e298f95766cf8a3c755ceb9a902839c3a239b560b079fa`.
  These runs used `97eed0f04` plus the witness keepalive correction described below;
  the packaged engine fingerprint retained its `-dirty` marker.
- Both container modes ran the master and two workers on an ordinary bridge
  network. Actual worker cgroup files confirmed 4 GiB memory, two CPUs and 256
  PIDs. UID/GID 10001, read-only roots/ROM mounts and separate persistent public
  identities were checked. Workers stopped within their Docker grace period;
  master/worker restarts preserved worker identities and restored master health.
  No display, GPU or audio device was attached.

Run the focused selection from the task tree, with `REPO` pointing to the main
checkout's existing ROM files:

```bash
JAVA_HOME=/path/to/jdk-25 LUA_BIN=/path/to/lua5.4 \
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestMasterConfig,TestMasterServer,TestHttpMasterApi,TestHttpMasterApiAgainstMaster,TestVerifierLoop,TestVerifierMain,TestVerifierShutdown,TestVerifierWorker,TestMasterHttpRoutes,TestMasterHttpAdmission,TestMasterTransportLifecycle,TestMasterEndToEnd,TestVerifiedRoomEndToEnd,TestAttemptReplayHarness,TestAttemptReplayPatchResolution,TestRacingDeploymentSmokeTool,TestRecordingBlobStore,TestVerificationJobQueue,TestVerifierRegistry,TestVerdictPersistence' \
  "-Ds3k.rom.path=${REPO}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${REPO}/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

The initial change-based plan selected all 3,618 smoke classes because racing paths
and the configuration reference were unclassified. The task changes no engine
algorithm, POM or test-selection policy. Focused service/transport/replay consumers,
packaging checks and actual container runs replaced that disproportionate engine
selection. This is bounded validation, not a full-suite or all-game/architecture
certification. S1 completed in containers; S3K replay ran in the focused JVM tests.

### Findings and corrections

- The first pre-reboot test request waited 524 seconds, then compilation rejected
  the witness's try-with-resources use of `RaceConnection` (not AutoCloseable).
  Explicit `finally` cleanup corrected it. No tests had run in that attempt.
- A second request waited at least 994 seconds for memory admission and was ended
  by reboot. It was not counted as a pass or duplicated while still alive.
- Review found malformed secret characters could reach JDK HTTP exceptions that
  echo a header. Printable non-space ASCII validation now rejects them with a
  sanitized error before header construction. File/literal and returned-worker
  token regressions passed. Witness acceptance also checks its own player slot,
  attempt ID and recording hash.
- The first real replay reached natural completion but disconnected while pacing:
  the probe sent broker heartbeats after relay handoff, carrying the browser token
  into room traffic. `MasterClient.sendControl` and the room's token-strike check
  explained the rejection. The probe now sends room-authenticated pings and
  ignores their unsolicited pongs. All three real replay runs passed afterward;
  server admission rules were not relaxed to make the probe pass.
- The original running kernel lacked matching bridge/veth modules. The reboot
  supplied kernel `7.3.0-5-cachyos`; loading its installed `br_netfilter` module
  allowed the owned rootless daemon to start and create/remove a bridge. No global
  Docker service/default context was changed. Docker 29.9.2, Compose 5.6.1,
  Buildx 0.38.1, cgroups v2 and fuse-overlayfs were used for qualification.
- Isolated verified-room fixtures seed only a new client's account trust before
  master startup. Copying that fixture DB into the non-root Compose volume needed
  a temporary bootstrap container with copy/chown capabilities; the application
  services retained their capability drop. No live production database was used.

The reusable witness and operator procedure are in
[the deployment guide](../../../racing/deploy/README.md#deployment-witness).
Temporary local orchestration, private test certificates, logs, fixtures and Docker
resources are task-owned diagnostics; remove them after inspection rather than
keeping validation receipts.
