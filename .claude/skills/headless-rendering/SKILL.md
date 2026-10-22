---
name: headless-rendering
description: Use when OpenGGF tests, captures, visual probes, or batch tools need real engine rendering without displaying a window. Covers offscreen OpenGL and Linux display-free surfaceless EGL; logic-only headless tests use the headless testing guide.
---

# Headless rendering

Use a real OpenGL context and the production renderer when the work needs drawing,
GPU uploads, framebuffer effects, or pixels. `GraphicsManager.initHeadless()`,
`HeadlessTestFixture`, and `HeadlessTestRunner` support simulation without GL;
adding `-Djava.awt.headless=true` or the EGL property does not turn them into
rendering fixtures.

Run commands from the intended worktree with Java 25. Follow the repository's
Maven queue and validation policy; a rendering check is bounded evidence, not a
full-suite or ROM-parity result.

## Choose the rendering owner

| Need | Existing owner |
| --- | --- |
| Gameplay screenshots or movies | [gameplay-capture](../gameplay-capture/SKILL.md): `GameplayCaptureTool` |
| Stageplay animatics, directed dialogue or staged promos | [stageplay-authoring](../stageplay-authoring/SKILL.md): `tools/stageplay/stageplay.py` and `StageplayCaptureTool`, using detached terrain or an explicitly owned live native world, with offline audio |
| Render an existing trace replay | [trace-capture](../trace-capture/SKILL.md): `TraceCaptureTool`; video completion does not prove parity |
| Rendered gameplay tests or custom route probes | `GameplayCaptureSession`: production boot, stepping, mode-aware drawing, and optional image readback |
| Component, shader, framebuffer, or presentation pixel tests | `PresentationOffscreenTestContext.open(width, height)` in test scope; it owns a core-profile EGL context, not a loaded game or initialized graphics manager |
| A custom production boot host | `HeadlessGameBoot`: GL/services/session foundation; the caller still owns stepping and drawing |
| Physics or state assertions with no rendering requirement | [headless testing](../../../docs/guide/contributing/headless-testing.md) |

Read the relevant owner before extending it. Do not launch interactive `Engine`
just to obtain offscreen frames. `SurfacelessEglContext` is package-private;
reuse the public owners rather than duplicating its JNI setup.

## Keep audio off desktop devices

Offscreen rendering must not play music or sound effects through desktop audio.
EGL, hidden windows, and `java.awt.headless` do not isolate audio. Establish a
known no-device audio backend before anything can start playback; do not open a
live OpenAL/device backend and rely on volume zero or desktop mute.

- For silent rendering, leave `GameplayCaptureSession` audio disabled (its default).
  `HeadlessGameBoot` installs `NullAudioBackend` before level playback, retiring
  any inherited backend even in reused JVMs. Custom hosts that do not use this
  boot owner must establish their own no-device backend before playback.
- For offline audio evidence, keep synthesis enabled with
  `HeadlessSmpsAudioBackend`, whose device hooks are no-ops and whose presentation
  sink is `NoDeviceAudioSink`. `HeadlessGameBoot` installs it before level music
  when `AUDIO_ENABLED=true`; gameplay capture's `--audio` enables that path before
  boot. Drain the production capture lease to the output file using the capture
  tool's existing cadence. Captured PCM does not require speaker playback.
- Component GL contexts do not configure audio. If a component test also boots
  audio/gameplay, apply the same no-device ownership rule. Clean up owned audio
  and capture leases on success and failure; avoid automatic playback of outputs.

Verify the installed backend/sink path, not whether anyone heard sound.
[TestHeadlessGameBootAudioIdentity](../../../src/test/java/com/openggf/tools/TestHeadlessGameBootAudioIdentity.java)
provides ROM-backed backend identity, replacement and offline packet checks;
its disabled-boot case verifies retirement of inherited ownership. The ROM-free
[TestHeadlessAudioOwnership](../../../src/test/java/com/openggf/tools/TestHeadlessAudioOwnership.java)
checks the same installation boundary in ordinary CI. A null-backend fallback is silent but cannot prove successful audio synthesis. Unexpected
speaker output means isolation failed: stop the owned run and fix its audio
owner rather than muting the user's desktop.

## Linux: render without a display connection

Select `-Dopenggf.headless.gl=surfaceless-egl` explicitly and unset `DISPLAY` and
`WAYLAND_DISPLAY` for the launched process. `HeadlessGameBoot` otherwise defaults
to a hidden GLFW window, which can still connect to a desktop. EGL uses a Mesa
surfaceless display and an owned pbuffer; GLFW's null platform supplies timer/input
services. Failure must remain visible; do not retry against the ambient desktop.

Ubuntu prerequisites are `libegl1`, `libegl-mesa0`, `libgl1`, and
`libgl1-mesa-dri`, as described in the headless testing guide. This backend loads
`libEGL.so.1`; it is not a portable macOS/Windows backend.

A small ROM-free component rendering check (Linux only):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestBackgroundRendererStateRestoration,TestBackgroundPlaneSpriteOcclusion' test
```

For a neutral three-frame gameplay capture, replace the ROM placeholder with an
existing absolute S3&K ROM path; do not create ROM links or copies:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl compile exec:java \
  -Dexec.mainClass=com.openggf.tools.GameplayCaptureTool \
  "-Dexec.args=--game s3k --rom '/absolute/existing-s3k.gen' --zone aiz --act 1 --frames 3 --no-video --out-dir target/capture/headless-check"
```

`exec:java` alone does not compile changed sources. For routes, input movies,
positioning, presentation pacing, audio, or video encoding, follow the chosen
capture skill. Durable captures belong in an explicit task directory outside the
repository; `target/` is disposable.

Pass the graphics property on Maven's command line so it reaches Surefire tests.
`MAVEN_OPTS` alone does not establish propagation to the fork.
`JAVA_TOOL_OPTIONS`/`JDK_JAVA_OPTIONS` can propagate it, but add launcher notices
that affect child-stderr assertions. A reused test JVM can also lose the property:
`TestSurfacelessEglContext` clears it during teardown. Inspect the actual failing
owner and run order; preserve required graphics settings. See the
[measurement hazards](../../../docs/agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output).

## Draw, read back, and release

For full gameplay, follow `TestGameplayCaptureSmoke` and
`GameplayCaptureSession` in `com.openggf.tools`. `Settings` selects viewport,
characters, donor, and positioned setup; `boot(romPath, zone, act, settings)` uses
zero-based zone/act numbers, whereas the capture CLI's act is one-based.
Use try-with-resources and keep GL creation, drawing, readback, and cleanup on the
owning thread; do not overlap sessions in a JVM with shared engine/GLFW globals.

- `session.step(input)` advances gameplay (`null` is neutral); it does not draw.
- `session.renderFrame()` draws the complete current frame and completes GPU work
  without reading pixels back. Use it when rendering is required but images are discarded.
- `session.render()` draws and returns an `RgbaImage` for screenshots or pixel assertions.
  `render(false)` supplies a tiles-only comparison. Bare
  `LevelFrameTestStep` + `drawWithSpritePriority` omits the playable sprite and is
  unsuitable as a complete gameplay screenshot path.

Do not replace required drawing with no-op headless mode. Existing route tests
may omit drawing between certified checkpoints; that optimization does not prove
intermediate-frame rendering. For every-frame investigations in adopted route
checks, use `-Dopenggf.tests.drawEveryFrame=true` (see the headless testing guide).
Do not separately advance fades already owned by the runtime `GameLoop`.

For component tests, `PresentationOffscreenTestContext` creates the context and
GL capabilities only. Initialize the renderer, viewport, projection, and render
target needed by the test. Dispose owned GL resources while the context is still
current, then close the context. For production sessions, let `close()` perform
its teardown; retaining shader/atlas handles after context destruction can make
the next session render black. Keep test reset/property restoration scoped to
the test, including failure paths.

`HeadlessGameBoot` alone is a foundation: it closes its session/ROM/context but
leaves process-owned graphics caches to its caller. A custom host or test that
initializes `GraphicsManager` must retire those owned resources while EGL is still
current, after its world/private textures close and before `boot.close()`.
`initHeadless()` is a logic-mode reset, not GL resource cleanup. Otherwise a second
context can reuse stale ArenaMaskRenderer program/VAO handles even when two pictures
compare equal. Higher-level capture sessions own their documented teardown.

## Verify the evidence

Check executed tests and skips, not just exit status: `@RequiresRom` and some GL
probes can skip successfully when prerequisites are absent. The component check
above requires EGL and fails if context creation is unavailable on Linux;
`TestSurfacelessEglContext` alone can skip its readback case.

For gameplay captures, inspect `state.csv` before selecting frames; for trace
captures, inspect the selected clip and replay reports using `trace-capture`.
For Stageplay, inspect the screenplay, labelled authored/simulated JSONL and moving
footage using `stageplay-authoring`. Legacy terrain `simulate` resolves native puppet
physics against detached terrain without GL. Current LIVE and TERRAIN_ONLY simulation
advance one shared production object/ring/player world; TERRAIN_ONLY clears initial
placements and rings; its `sample.world` contains native states/events.
Pure `timeline` remains authored and leaves puppets `UNRESOLVED`. A selected scene
simulation is bounded evidence, not certification of a full level or ROM parity. Confirm dimensions, scene/mode, nonblank pixels, and the playable
sprite when expected. Compare relevant pixels or a deliberately cleared target to reject stale framebuffer
success; a PNG file's existence is insufficient. Distinguish capture correctness
from gameplay/trace parity and from coverage of other modes or routes.

On macOS/Windows, the production default is a hidden GLFW context with native
window-system prerequisites. On macOS run capture tools in a standalone
`java -XstartOnFirstThread` process with the compiled dependency classpath;
`exec:java` cannot provide the first thread. Do not claim these hidden-window
paths are display-free. Linux EGL commands above do not certify other platforms.
For actual focus/close/input diagnostics, use an owned isolated display and the
repository's diagnostic workflow; never automate the user's shared desktop.
