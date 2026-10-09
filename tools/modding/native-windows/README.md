# Experimental Windows friends ZIP

This separate Windows x64 image uses checksum-pinned GraalVM Community
25.4.4.1.1+1.1 runtime class loading. It packages every maintained example and
sample mod, a shortcut for each, and `s1.gen` / `s2.gen` / `s3k.gen` configuration.
No Java installation or ROM redistribution is needed. Extract the complete ZIP.

The normal Maven native profile and release workflow remain unchanged. Only this
image compiles the extra entry point, hosted feature and field substitution.
The feature requires the builder's actual `RuntimeClassLoading` option and a
builder-only marker; it rejects a changed engine capability field. It redirects
only `Engine.compiledModsSupported`, retaining native detection, ownership,
trust, snapshots, fault boundaries and transactions.

On Windows with Java 21, Python and an MSVC/Windows SDK developer environment:

```powershell
python tools/testing/maven_queue.py -B "-Dmse=off" "-DskipTests" -Puniversal-jar package dependency:build-classpath "-DincludeScope=runtime" "-Dmdep.outputFile=target/native-runtime-classpath.txt"
$engine = (Get-ChildItem target/*-jar-with-dependencies.jar).FullName
$sdk = (Get-ChildItem target/*-openggf-mod-sdk.jar).FullName
python tools/modding/native-windows/build_inputs.py --engine-jar $engine --sdk-jar $sdk --output target/windows-friends-inputs
$env:OPENGGF_MAVEN_QUEUE = 'serial'
python tools/testing/maven_queue.py "-Dmse=off" exec:exec "-Dexec.executable=python" "-Dexec.args=tools/modding/native-windows/build_windows.py --engine-jar `"$engine`" --inputs target/windows-friends-inputs --runtime-classpath target/native-runtime-classpath.txt --output target/windows-friends-native"
```

Output directories must be new; remove only a previous build's owned outputs
before rerunning. Compilation reserves an 8 GiB builder heap and four threads;
local compilation needs an exclusive queue slot. The GitHub workflow performs the same build on Windows,
alongside fresh normal structural guards; both jobs must pass before delivery.
It runs on targeted tool changes to
develop or manual dispatch, uploads the ZIP/checksum for three days and never
publishes a release or tag.

The builder derives exact-class retention from the canonical API, every concrete
`GameModule` implementation (including anonymous wrappers), and all static mod
references. The shared contract generator scans direct JDK member references,
including dormant callbacks, handles and nested constant-dynamic arguments, and
derives the JDK packages to retain. Additional runtime bootstrap, reflection,
stream and charset packages cover interpreter-generated calls. `java.logging`
is explicitly resolved during image building.

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
