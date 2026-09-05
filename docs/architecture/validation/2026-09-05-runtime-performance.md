# Runtime performance audit remediation

Base: `ce3b9e291` on `develop`. Implementation worktree:
`.worktrees/ai-runtime-performance`, branch `feature/ai-runtime-performance`.

## Changes and limits

- Audio session transactions use reusable private YM/PSG core, queue and
  resampler backups. Registry and override arrays are also reused. Durable
  rewind snapshots and public token validation remain independent. Logical
  sequencer snapshots and fresh transaction tokens still allocate; this is
  not a claim of allocation-free audio presentation.
- Live capture reuses up to queue capacity + two RGBA heap arrays. Two GPU
  pixel-pack buffers overlap readback with the following presentation. PCM
  remains paired to the original video frame. The final pending image is read
  on the GL thread; encoder submission/finalization run in the finalizer.
  Public CapturedFrame construction still defensively copies. Encoder implementations
  must consume pooled pixels before returning from encode.
- Lossless BLOCK policy remains. GPU mapping and sustained encoder overload can
  still stall. Queue metrics now include waits shorter than 50 ms and the first
  50 ms of longer waits; graceful-stop timeout covers poison insertion too.
- Live gameplay checkpoints occur every 10 ticks, with audio checkpoints still
  every 60. This bounds cold backward replay to nine gameplay ticks, not nine
  milliseconds. Gameplay captures and retained checkpoint count increase roughly
  sixfold. History remains bounded by the existing history-seconds setting;
  audio pruning retains the base preceding intermediate gameplay checkpoints.
- Input history uses a circular buffer with absolute frame numbers. Pruning
  releases removed rows without shifting the retained history.
- Gumball updates reuse scratch and batch atlas uploads; HCZ2 uses reusable
  deformation scratch excluded from rewind state. Tile contents/timing remain
  unchanged.
- Display shader passes cache the context's texture-unit limit at activation,
  avoid a duplicate texture binding query on unit zero, and avoid an unnecessary
  active-texture query during restore. Remaining queries preserve arbitrary
  caller GL state; no global cache assumes ownership of external state.

## Focused evidence

Java: OpenJDK 21.0.11. Maven: 3.9.16. All Maven runs use `-Dmse=off`.
ROM-backed runs use explicit absolute root ROM paths.

- Baseline ordinary suite: 16,482 tests, zero failures/errors, 40 skips.
- Baseline guards: `LUA_BIN=/usr/bin/lua5.4 mvn -Dmse=off -Pguards test -B`:
  609 tests, zero failures/errors/skips.
- Audio focused run selected `TestSynthMutationBackup,TestSmpsPhysicalDevice,
  TestSmpsSessionDiagnostics,TestSmpsSessionTransitionMatrix,TestAudioPresentation*,
  TestAudioVoiceRegistry*`: 218 tests, zero failures/errors/skips.
- Real GL shader checks ran with local display access: all 16 passed. Restricted
  sandbox runs skip these checks because they cannot open the context.
- Isolated warmed physical backup allocation probe: reusable 0 B/capture versus
  immutable 14,184 B/capture. This excludes session/sequencer/token allocations.

`TestLiveRewindCheckpointCost` is opt-in:

```bash
mvn -Dmse=off -Dtest=TestLiveRewindCheckpointCost \
  -Dopenggf.checkpoint.measure=true \
  '-Dsonic2.rom.path=/absolute/path/to/the/rev01/rom.gen' test -B
```

On the recorded S2 EHZ1 route, 1,800 frames per arm, discarding the first 300
from timing/allocation measurement:

| Gameplay interval | Checkpoint work/frame | Checkpoint allocation/frame | Retained snapshots | Estimated retained snapshot bytes |
|---|---:|---:|---:|---:|
| 60 | 7,129 ns | 284 B | 31 | 256,728 |
| 10 | 18,400 ns | 1,726 B | 181 | 1,411,424 |

Timing covers external checkpoint recording only, not complete game/render/audio
frames. Retained sizes use the existing structural estimator with shared identity
tracking, not a heap dump. These results are one headless route on this machine,
not a cross-zone maximum or a statistically controlled frame-time benchmark.

At a 1280×896 viewport, removing the RGBA clone avoids 4,587,520 bytes per
captured frame, or 262.5 MiB/s at 60 FPS. This is calculated image payload volume;
small frame wrappers and PCM ownership copies remain.
