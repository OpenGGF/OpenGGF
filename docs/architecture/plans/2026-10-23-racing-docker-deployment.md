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

## Checkpoint

- Pre-task destination: `aebb390ed365b20ca0e7cd248ecb5b0761e2d729` (`develop`), fetched
  and fast-forward pull confirmed up to date. Main-workspace dirty reference
  submodules and unrelated untracked files are preserved.
- Task tree: `.worktrees/ai-racing-docker`, branch `feature/ai-racing-docker`.
- Java 25 is installed at `/usr/lib/jvm/java-25-openjdk`. Category preflight passed
  with `JAVA_HOME` set there and `LUA_BIN=/usr/bin/lua5.4`; no tests execute during
  that probe. The initial attempt stopped on the default Lua version.
- Docker tooling was downloaded into an owned external scratch directory. Runtime
  startup is blocked: running CachyOS kernel `7.2.10-1-cachyos` has no corresponding
  installed bridge/veth modules. All attempted daemon sessions exited. The user
  will repair/reboot the host; container runtime validation remains pending.
- Implementation is in the task tree: portable distribution/images, Compose and
  standalone documentation, file secrets/readiness/worker retry and drain, and a
  real TLS/replay deployment witness. No image publication is planned.
- Ten Python packaging regressions passed. Compose schema and two-worker identity
  separation, published ports, TLS alias, health dependency and read-only mounts
  passed CLI validation. Deployment shell examples parse and file links resolve.
- Change-based planning selected all 3,618 smoke classes because `racing/` and
  `CONFIGURATION.md` are unclassified. No engine, POM, gameplay or selection policy
  changed. Focused server/configuration/transport/verifier consumers plus real ROM
  replay and container execution replace that disproportionate broad engine run.
  This is bounded validation, not a full-suite pass.
- First queued regression job (`TestMasterConfig,TestMasterServer`, session 9547)
  acquired admission after 524 seconds. Maven ran 81 seconds and stopped before
  tests on a new witness compile error: `RaceConnection` is not AutoCloseable.
  Explicit `finally` cleanup fixes that mismatch; no baseline attribution is needed.
- Combined focused regression command session 94453 is queued, covering the two
  original classes and 18 master/transport/verifier/witness consumers with absolute
  S1 and S3K ROM properties. Root builds only after the worker releases target.
- Independent review found malformed token characters could reach an HTTP error
  that echoes its header. Sanitized printable-ASCII validation now covers file and
  literal forms and worker tokens before header construction, with regressions in
  the combined run. Witness success is bound to its own slot and requested attempt.
- Actual relocated-runtime, TLS replay and container execution remain pending.

### Reboot handoff

The user has rebooted into `7.3.0-5-cachyos`, with matching modules installed. The
old focused-test PID is absent; its interrupted request is not a passing result.
Main-workspace `develop` advanced to `d7732f78e` and was fetched/fast-forward checked.
Save the implementation locally and merge that upstream state into the task tree
before restarting validation; only the prerelease changelog overlaps task files.
Docker runtime restart is delegated to one bounded task; no system service or
default Docker context is changed.

All source changes are uncommitted on `feature/ai-racing-docker`; nothing has been
integrated or pushed. The current combined focused request (session 94453, last
observed PID 1480777) has waited at least 994 seconds without executing, with memory
admission the last reported blocker (about 4.1 GiB short of its 7 GiB reservation).
The service worker owns the only observer. Keep that request if it is alive; after
a reboot, verify it ended before rerunning the same unfinished coverage. Do not
submit a duplicate while it is merely queued.

Set `REPO` to the main checkout and `TASK_SCRATCH` to the owned external scratch
root (`$HOME/scratch/agent-tmp/codex` on this host). Run from the task worktree:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestMasterConfig,TestMasterServer,TestHttpMasterApi,TestHttpMasterApiAgainstMaster,TestVerifierLoop,TestVerifierMain,TestVerifierShutdown,TestVerifierWorker,TestMasterHttpRoutes,TestMasterHttpAdmission,TestMasterTransportLifecycle,TestMasterEndToEnd,TestVerifiedRoomEndToEnd,TestAttemptReplayHarness,TestAttemptReplayPatchResolution,TestRacingDeploymentSmokeTool,TestRecordingBlobStore,TestVerificationJobQueue,TestVerifierRegistry,TestVerdictPersistence' \
  "-Ds3k.rom.path=${REPO}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${REPO}/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

Both supplied ROMs were checked against the repository's canonical CRC32 and SHA-1
values. No ROM was copied or added to the image context.

After the focused run releases `target/`, build with Java 25 using
`python3 racing/server/build.py --distribution`, then
`python3 racing/deploy/build-images.py --skip-build --tag local --context-only`.
The task-owned scratch directory
`${TASK_SCRATCH}/racing-docker-validation-20261023/` contains a
prepared, syntax-checked `host-smoke.py` diagnostic (not executed). It relocates the
actual distribution, creates an isolated private-CA TLS fixture, starts a master
and two separate workers, requires the real GHZ1 replay/upload/verdict, and tears
down its own processes. Inspect failures in its owned `host-*` directory and remove
consumed diagnostics afterward. It is not a substitute for container validation.

Docker tools/start script and blocked-runtime status are under
`${TASK_SCRATCH}/racing-docker-runtime-20261023/`. All previous
daemon sessions exited; a leftover socket file is not proof of a live daemon.
After the host is repaired, restart only this owned runtime, build both images,
and qualify standalone and Compose bridge-network launches with real TLS/replay,
read-only ROM mounts, distinct worker identities and restart persistence. Then
finish policy/trailer checks, integrate into the unchanged main-workspace branch,
push only `develop`, and account for/remove task-owned worktree and scratch output.
