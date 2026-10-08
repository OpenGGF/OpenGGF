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
python tools/modding/native-windows/build_windows.py --engine-jar $engine --inputs target/windows-friends-inputs --runtime-classpath target/native-runtime-classpath.txt --output target/windows-friends-native
```

Output directories must be new; remove only a previous build's owned outputs
before rerunning. The GitHub workflow performs the same build on Windows,
alongside fresh normal structural guards; both jobs must pass before delivery.
It runs on targeted tool changes to
develop or manual dispatch, uploads the ZIP/checksum for three days and never
publishes a release or tag.

The builder derives exact-class retention from the canonical API and all static
mod references, audits types/fields/methods before execution, rejects deliberate
missing fields and methods with ordinary exit 1, compares JVM/native registration
for each mod, then exercises Engine's real development-source boot for every code
mod. Every probe runs in a new process. It uses the proper Maven dependency JARs,
including SQLite's native feature, rather than the diagnostic-only shaded-JAR
SQLite configuration exclusion. After checking, it reconstructs the distribution
from immutable inputs to exclude every probe-created mutable file.

Archive checks reject ROMs, traversal, wrong-architecture binaries and changed
mod hashes. The ZIP retains engine/third-party licences and the pinned CE legal
notices, with exact source links and build evidence. These checks qualify
packaging and startup registration. They do not certify rendering, complete
gameplay, rewind, saves or performance on this experimental VM; those still need
the gameplay qualification described in the
[study](../../../docs/architecture/research/2026-10-08-graalvm-native-mod-feasibility.md).

Portable packaging regression controls:

```bash
python3 -m unittest discover -s tools/modding/native-windows -p 'test_*.py' -v
```
