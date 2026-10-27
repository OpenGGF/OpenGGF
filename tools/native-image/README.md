# Default native build

`mvn -Pnative package -Dmaven.test.skip=true` builds code-mod-capable images using
**GraalVM Community 25.4.4.1.1+1.1**, Python 3.12+ and the platform C toolchain.
The builder checks the Community release identity and its actual runtime-loading
option; unsupported toolchains fail instead of producing a closed-world image.
Windows x64, Linux x64 and macOS ARM64 archives and SHA-256 digests are pinned in
`toolchain.py`, from the [official Community release](https://github.com/graalvm/graalvm-ce-builds/releases/tag/graal-25.4.4.1.1).
Use `-Dnative.python=python` on Windows if `python3` is unavailable.

The common recipe uses a 5 GiB compiler heap, G1 collection, two compiler workers
and size optimization. The qualified Linux build peaked at 6.03 GiB RSS, down
from 9.49 GiB with the original 8 GiB configuration. Keep native builds in the
exclusive lane, including their non-heap overhead.
The outer `exec:exec` queues the complete build, including its native compiler;
a skipped-test package command by itself bypasses test admission:

```bash
python3 tools/native-image/toolchain.py --output target/community-toolchain
export JAVA_HOME="$PWD/target/community-toolchain/graalvm-community-25.4.4.1.1+1.1"
OPENGGF_MAVEN_QUEUE=serial python3 tools/testing/maven_queue.py -Dmse=off exec:exec \
  -Dexec.executable=mvn '-Dexec.args=-Dmse=off -Pnative -Dmaven.test.skip=true package -B'
./target/OpenGGF --audit
```

macOS uses the downloaded JDK's `Contents/Home`. CI runs the same Maven profile
on each target and audits the finished image. It prepares with
`-Dnative.stage=prepare -Dmaven.test.skip=true -Dnative.macos.package.skip=true`,
then runs `python tools/native-image/build.py --stage compile` after Maven exits,
so the two heaps do not overlap. The upstream release test job remains mandatory.
The standard `macos-15` runner stays selected; its actual ARM64 memory peak is
unverified locally. No paid runner or older-toolchain fallback is selected.
Larger reference sets can set `OPENGGF_NATIVE_IMAGE_HEAP=8g` without changing the
recipe. The audit itself needs no ROMs. Release preparation retains its separate full-suite gates.

The generated contract starts with the complete annotated Mod API and signature
closure. It retains concrete GameModule, mutator and world-policy implementations,
then follows erased Class-keyed service factories. Bundled mod bytecode adds
static engine/JDK references. Referenced runtime dependency jars are preserved
as whole jars, including methods not reachable from engine code (for example,
Jackson calls made directly by Time Attack). Creator jars stay off the image classpath. The image retains original engine class resources
for structural validation and JDK bootstrap, reflection, charset, stream and file
helpers for external code. `-Dnative.mod.references=/absolute/mod-jar-directory`
adds reference inputs without putting those mod classes on the image classpath;
friends packaging uses this same recipe for its examples.

Preservation keeps the Java reflection/member contract, but does not also export
every preserved member through JNI (`-H:-PreserveIncludesJNI`). Real JNI uses,
including LWJGL callbacks, retain their normal reachability metadata. Custom JNI
libraries still need appropriate JNI metadata; blanket exports are not provided.

The launcher audits the embedded member contract before creator execution. It
uses the same Engine boot, trust prompts, immutable snapshots, validation and
fault boundaries as the JVM. First-party jars remain separate, under `bundled/`
next to the executable, and their build-pinned manifest is embedded in the image.
Retain that folder, native support libraries and `native-licenses/GraalVM` when
packaging. The release jobs copy the notices into the package's `LICENSES`.

Runtime loading is an experimental VM feature. Retention and member lookup cannot
prove every invocation, reflection target, generated class or external library.
Rendered qualification and runtime-loaded bootstrap controls supplement the audit;
they do not certify all mods, drivers, routes or performance. The [feasibility record](../../docs/architecture/research/2026-10-08-graalvm-native-mod-feasibility.md#default-native-promotion-2026-10-27)
documents observed failures and coverage. No code-mod kind is rejected
solely because the application is native.

Qualification commands on the actual image are `--check-mods [owner-id]`,
`--check-engine owner-id` (normal boot, or a selected development directory), and
`--check-gameplay slug output s1-rom s2-rom s3k-rom [scene-command ...]`.
They explicitly execute trusted local mods; use disposable working/save directories.
`--check-mods` reads `mods/` beside the executable; `--check-gameplay` reads it from
the working directory. The latter uses the existing friends probe and original ROMs, with no-device audio.
Title-entry mods exercise their menu callbacks and reopen through the production
scene host and launcher; their hosted gameplay runs are outside this bounded check.
On Linux unset DISPLAY/WAYLAND_DISPLAY and pass
`-Dopenggf.headless.gl=surfaceless-egl` for display-free rendering.
`--audit /path/to/contract.tsv` adds an audit for deliberate missing-member controls.

```bash
python3 -m unittest discover -s tools/native-image -p 'test_*.py' -v
```

Native image analysis uses LWJGL 3.4.1's upstream `unsafe` core classifier.
Its default Java 25 FFM callback generator fails before GL initialization in the
pinned native runtime. Other LWJGL modules' transitive ordinary-core jar is removed
from the native analysis classpath. Normal JVM builds retain the FFM backend.

`--benchmark-gameplay rom bk2 json traversal-frames measured-frames discarded-passes`
executes a level-local AIZ1 movie through `GameplayCaptureSession` and the real
`GameLoop`, rendering and completing every frame without pacing or readback.
It reports both elapsed frame time (including GPU waits) and all-thread process
CPU time from Linux `clock_gettime(CLOCK_PROCESS_CPUTIME_ID)`, using the existing
nearest-rank benchmark statistics. The CPU clock requires 64-bit glibc Linux;
this qualification command deliberately rejects other platforms. A first-frame
stdout marker lets the external driver include process launch and the native
retention audit in startup time. The final trajectory/object/pixel controls must
match before comparing runtimes. Audio and rewind are disabled; this is bounded
rendered gameplay evidence. On Linux unset `DISPLAY` and `WAYLAND_DISPLAY`, select
`-Dopenggf.headless.gl=surfaceless-egl`, and run the prebuilt matrix with
`tools/performance/run_isolated.py --audit-report target/native-benchmark/audit.json -- python3 tools/performance/native_matrix.py ...`.
The JVM launcher targets `com.openggf.tools.nativeimage.GameplayBenchmark` with
`target/native-mods/host-classes`, `target/classes` and the runtime dependencies
on its classpath; the matrix discards two complete JVM passes before its final
traversal and measured window. See the dated feasibility record for exact commands.
