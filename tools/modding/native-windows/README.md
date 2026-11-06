# Native Windows friends ZIP

This Windows x64 friends package uses the [default native build recipe](../../native-image/README.md)
and its checksum-pinned GraalVM Community 25.4.4.1.1+1.1 runtime class loading.
It adds every maintained example and sample mod, a shortcut for each, and
`s1.gen` / `s2.gen` / `s3k.gen` configuration. No Java installation or ROM
redistribution is needed. Extract the complete ZIP.

The shared recipe owns the entry point, hosted runtime-loading checks, API/service
retention and native flags. There is no friends-only capability substitution.
Trust, snapshots, fault boundaries and transactions use production code.

On Windows with Java 25, Python and an MSVC/Windows SDK developer environment:

```powershell
python tools/testing/maven_queue.py -B "-Dmse=off" "-DskipTests" "-Dlwjgl.core.classifier=unsafe" -Puniversal-jar package dependency:build-classpath "-DincludeScope=runtime" "-Dmdep.outputFile=target/native-runtime-classpath.txt"
$engine = (Get-ChildItem target/*-jar-with-dependencies.jar).FullName
$sdk = (Get-ChildItem target/*-openggf-mod-sdk.jar).FullName
python tools/modding/native-windows/build_inputs.py --engine-jar $engine --sdk-jar $sdk --output target/windows-friends-inputs
$env:OPENGGF_MAVEN_QUEUE = 'serial'
python tools/testing/maven_queue.py "-Dmse=off" exec:exec "-Dexec.executable=python" "-Dexec.args=tools/modding/native-windows/build_windows.py --engine-jar `"$engine`" --inputs target/windows-friends-inputs --runtime-classpath target/native-runtime-classpath.txt --output target/windows-friends-native"
```

Output directories must be new; remove only a previous build's owned outputs
before rerunning. Compilation uses the shared default optimization (`-O2`),
8 GiB G1 compiler heap, three active processors and two compiler workers;
local compilation needs an exclusive queue slot. The GitHub workflow performs the same build on Windows,
alongside fresh normal structural guards; both jobs must pass before delivery.
It runs on targeted tool changes to
develop or manual dispatch, uploads the ZIP/checksum for three days and never
publishes a release or tag.

The builder derives exact-class retention from the canonical API, every concrete
`GameModule` implementation (including anonymous wrappers), engine-created
prepared/transient mutator services and world-policy providers/states, and all static mod
references. The shared contract generator scans direct JDK member references,
including dormant callbacks, handles and nested constant-dynamic arguments, and
derives the JDK packages to retain. The separately compiled runtime bootstrap
control also enters that scan, so its callable JDK bodies remain available even
when maintained mods stop referencing a package. Its classes stay outside image
analysis and the shipped mod set. Additional runtime bootstrap, reflection,
stream and charset packages cover interpreter-generated calls. `java.logging`
is explicitly resolved during image building.

Class-keyed service factories erase their return type to `Object`. The default
`--service-factories` scan follows those methods' actual bytecode in retained
engine classes, repeating for newly discovered service-bearing classes. This
retains returned anonymous implementations such as the HUD warning policy, without
adding each concrete class to a list. A matched bytecode fixture checks discovery,
exact class bytes and exclusion of unrelated classes; the runtime control invokes
the actual catalog and HUD projections. String-based arbitrary reflection still
requires explicit seams and execution coverage.

It audits types/fields/methods before execution, rejects deliberate
missing fields and methods with ordinary exit 1, compares JVM/native registration
results exactly for each mod, then exercises Engine's real development-source
boot through every code-mod `.bat` shortcut. Data-mod shortcuts and the normal
launcher also receive diagnostic checks from a different working directory.
Shortcuts forward additional command-line arguments; set `OPENGGF_NO_PAUSE=1`
for unattended negative diagnostics.

A separate runtime-loaded fixture exercises records, pattern switches, generated
proxies, UTF-8, writers, file copy/atomic replacement and primitive/reference
streams on the actual Windows executable. Its source is shared with Linux,
compiled separately, absent from the image classpath and removed before shipping.
This catches bootstrap/virtual-dispatch omissions a metadata audit cannot prove.
Every probe runs in a new process. It uses the proper Maven dependency JARs,
including SQLite's native feature, rather than the diagnostic-only shaded-JAR
SQLite configuration exclusion. After checking, it reconstructs the distribution
from immutable inputs to exclude every probe-created mutable file.

Archive checks reject ROMs, traversal, wrong-architecture binaries and changed
mod hashes. The ZIP retains engine/third-party licences and the pinned CE legal
notices, with exact source links and build evidence. These checks qualify
packaging, bootstrap behavior and startup registration on Microsoft Windows.
The executable's `--check-gameplay` command reuses the platform-neutral OpenGL
probe introduced for Linux, with original user ROM paths. Local Wine gameplay
checks are separate compatibility evidence; they do not certify every route,
multiplayer session, physical audio device, GPU or Microsoft Windows version.
For mods declaring one-shot samples, the probe uses the production prepared
audio session wrapper and checks every sample's final mixed PCM. It writes
`sfx-pcm.csv` with decoded frame counts, rate, channels, peak and signal range;
opening a decoder or private cursor alone cannot pass this check.
See the source-attributed results in the
[study](../../../docs/architecture/research/2026-10-08-graalvm-native-mod-feasibility.md).

The Linux desktop follow-up consumes the immutable Windows artifact, uses a
private Wine prefix, verifies the original ROM hashes and runs every extracted
`.bat` with the shared gameplay recipes. It reconstructs a clean ZIP with the
Wine evidence labelled separately from actual Windows-runner checks:

```bash
python3 tools/modding/native-windows/qualify_wine.py --input "$windows_zip" --work target/windows-wine-check --captures "$capture_root" --output "$refreshed_zip" --roms "$original_s1" "$original_s2" "$original_s3k"
```

All output paths must be new and task-owned. Keep rendered captures outside the
repository; consume and remove temporary runtime/log/prefix files after checking.

Portable packaging regression controls:

```bash
python3 -m unittest discover -s tools/modding/native-windows -p 'test_*.py' -v
```

Native images use LWJGL's official `unsafe` core classifier for JNI/Unsafe callbacks; the normal JVM build keeps its Java 25 FFM backend.

Linux and Windows share the default `-O2`, 8 GiB G1 compiler heap, three active
processors and two-worker recipe for their 16 GiB standard runners; macOS alone
uses `-Os` and 5 GiB on `macos-15`. `OPENGGF_NATIVE_IMAGE_HEAP` can override the
heap limit for larger reference sets in the exclusive queue lane. Platform
selection and flags live only in `tools/native-image/build.py`.
