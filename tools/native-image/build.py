#!/usr/bin/env python3
"""Default native build, originating in the 2026-10-27 native code-mod promotion.

Inputs: Java 25 classes, runtime classpath, bundled mods and the pinned Community
JDK. Generates exact-byte API/service retention, audits it, then builds one image.
Friends packaging calls this same recipe with extra mod-reference inputs.
"""
import argparse
import json
import os
import platform
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
from toolchain import VERSION
IMPLEMENTATION_ROOTS = (
    'com.openggf.game.GameModule',
    'com.openggf.game.mutators.PreparedMutatorConfiguration',
    'com.openggf.game.mutators.TransientMutatorPolicySource',
    'com.openggf.game.session.WorldSessionPolicyProvider',
    'com.openggf.game.session.WorldSessionPolicyState',
)
JDK_PACKAGES = {
    'java.lang', 'java.lang.invoke', 'java.lang.runtime', 'java.lang.reflect',
    'java.util', 'java.util.function', 'java.util.stream', 'java.nio.charset',
    'java.nio.file', 'java.io',
}

def run(command, cwd=ROOT):
    subprocess.run(list(map(str, command)), cwd=cwd, check=True)


def check_toolchain(graal):
    release = dict(line.split('=', 1) for line in (graal / 'release').read_text().splitlines() if '=' in line)
    if (release.get('IMPLEMENTOR', '').strip('"') != 'GraalVM Community'
            or release.get('GRAALVM_VERSION', '').strip('"') != VERSION.split('+')[0]):
        raise ValueError(f'Native mods require GraalVM Community {VERSION}; no closed-world fallback')


def native_dependencies(dependencies):
    # Other LWJGL modules transitively request the ordinary core. Native images
    # use upstream's JNI/Unsafe core; never let the FFM multi-release core win.
    dependency_paths = dependencies.split(os.pathsep)
    unsafe = [p for p in dependency_paths if Path(p).name.startswith('lwjgl-') and Path(p).name.endswith('-unsafe.jar')]
    if len(unsafe) != 1: raise ValueError('Native build requires exactly one upstream LWJGL unsafe core')
    regular = Path(unsafe[0]).name.removesuffix('-unsafe.jar') + '.jar'
    dependency_paths = [p for p in dependency_paths if Path(p).name != regular and p not in unsafe]
    dependencies = os.pathsep.join([*unsafe, *dependency_paths])
    return dependencies


def compile_prepared(graal, output):
    graal, output = graal.resolve(), output.resolve()
    check_toolchain(graal)
    argfile = output / 'native-mods/native-image.args'
    if not argfile.is_file():
        raise ValueError('Prepare the native build before compiling its image')
    run([graal / ('bin/native-image.cmd' if os.name == 'nt' else 'bin/native-image'), '@' + str(argfile)], output)


def compiler_options():
    small_runner = platform.system() == 'Darwin'
    heap = os.environ.get('OPENGGF_NATIVE_IMAGE_HEAP', '5g' if small_runner else '8g')
    return [*(['-Os'] if small_runner else []), '-J-Xmx' + heap,
            '-J-XX:-UseParallelGC', '-J-XX:+UseG1GC',
            '-J-XX:ActiveProcessorCount=3', '--parallelism=2']


def build(graal, classes, dependencies, output, mods=None, compile_image=True):
    graal, classes, output = graal.resolve(), classes.resolve(), output.resolve()
    check_toolchain(graal)
    work = output / 'native-mods'
    if work.exists(): shutil.rmtree(work)
    work.mkdir(parents=True)
    suffix = '.exe' if os.name == 'nt' else ''
    java, javac = (graal / ('bin/' + tool + suffix) for tool in ('java', 'javac'))
    sdk = work / 'hosted-sdk.jar'
    with zipfile.ZipFile(graal / 'jmods/org.graalvm.nativeimage.jmod') as module, zipfile.ZipFile(sdk, 'w') as jar:
        for name in module.namelist():
            if name.startswith('classes/') and name.endswith('.class'):
                jar.writestr(name.removeprefix('classes/'), module.read(name))
    engine = work / 'engine-input.jar'
    with zipfile.ZipFile(engine, 'w', zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob('*')):
            if path.is_file(): jar.write(path, path.relative_to(classes).as_posix())
    host = work / 'host-classes'
    host.mkdir()
    sources = sorted(Path(__file__).parent.glob('*.java'))
    sources += [ROOT / 'tools/modding/native-feasibility/NativeModRegistrationProbe.java',
                ROOT / 'tools/modding/native-windows/ExperimentalEngineBootCheck.java',
                ROOT / 'tools/modding/native-linux/NativeGameplayCheck.java',
                ROOT / 'tools/modding/native-linux/NativeSceneInspection.java']
    dependencies = native_dependencies(dependencies)
    cp = os.pathsep.join((str(classes), dependencies))
    javac_args = ['--release', '25', '-cp', cp + os.pathsep + str(sdk), '-d', str(host), *map(str, sources)]
    javac_file = work / 'javac.args'
    javac_file.write_text('\n'.join(json.dumps(a) for a in javac_args), encoding='utf-8')
    run([javac, '@' + str(javac_file)])
    host_cp = str(host) + os.pathsep + cp
    launch = [java, '--enable-native-access=ALL-UNNAMED', '-cp', host_cp]
    run([*launch, 'com.openggf.tools.NativeModMemberContractTest', work / 'fixtures'])
    scan = work / 'mods'
    scan.mkdir()
    for directory in (output / 'bundled', mods):
        if directory is not None and directory.is_dir():
            for jar in directory.glob('*.jar'): shutil.copy2(jar, scan / jar.name)
    contract = work / 'contract'
    run([*launch, 'com.openggf.tools.NativeModMemberContract', engine, scan, contract,
         *('--implementation-root=' + root for root in IMPLEMENTATION_ROOTS), '--service-factories', '--jdk-members'])
    run([*launch, 'com.openggf.tools.NativeModMemberAudit', contract / 'members.tsv'])
    shutil.copy2(contract / 'members.tsv', host / 'native-mod-members.tsv')
    shutil.copy2(contract / 'members.tsv', output / 'native-mod-members.tsv')
    metadata = work / 'metadata'
    metadata.mkdir()
    (metadata / 'reflect-config.json').write_text('[{"name":"sun.net.www.protocol.jar.Handler","allDeclaredConstructors":true}]\n')
    packages = JDK_PACKAGES | set((contract / 'jdk-preserve-packages.txt').read_text().splitlines())
    retained_paths = [str(contract / 'preserved-engine.jar'),
                      *(contract / 'dependency-preserve-paths.txt').read_text().splitlines()]
    preserve = ','.join('path=' + p for p in retained_paths) + ',' + ','.join('package=' + p for p in sorted(packages))
    # Keep the prepared image input immutable across the separate CI stages.
    native_cp = os.pathsep.join((str(host), str(contract / 'preserved-engine.jar'), str(engine), dependencies))
    # Preserve requests are resolved before ordinary reachability resolves modules.
    # Include the owning JDK modules (Time Attack also consumes java.net.http).
    modules = {'java.logging'}
    requested_paths = {p.replace('.', '/') for p in packages}
    for module in (graal / 'jmods').glob('java.*.jmod'):
        with zipfile.ZipFile(module) as contents:
            if any(name.startswith('classes/') and name.endswith('.class')
                   and name.removeprefix('classes/').rsplit('/', 1)[0] in requested_paths
                   for name in contents.namelist()):
                modules.add(module.stem)
    # macos-15 has 7 GiB: retain its qualified small-runner recipe. The
    # 16 GiB Linux/Windows runners can afford default -O2 and an 8 GiB heap.
    # Two workers / three active processors bound compiler and G1 overhead;
    # Linux -O2 qualification with these limits peaked at 9.14 GiB RSS.
    arguments = ['--enable-native-access=ALL-UNNAMED', '-march=compatibility',
        *compiler_options(),
        '--add-modules=' + ','.join(sorted(modules)), '--initialize-at-run-time=org.lwjgl,java.awt,javax.swing,sun.awt,sun.java2d',
        # Preserve Java members, without blanket JNI exports of every member.
        # Actual JNI users (including LWJGL) retain their normal JNI metadata.
        '-H:+UnlockExperimentalVMOptions', '-H:+RuntimeClassLoading', '-H:-PreserveIncludesJNI', '-H:Preserve=' + preserve,
        r'-H:IncludeResources=com/openggf/.*\.class|META-INF/openggf/bundled-mods.json|native-mod-members.tsv',
        '-H:ConfigurationFileDirectories=' + str(metadata),
        '-J--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.hub=ALL-UNNAMED',
        '-J--add-exports=org.graalvm.nativeimage.shared/com.oracle.svm.shared.option=ALL-UNNAMED',
        '--features=com.openggf.tools.nativeimage.NativeModFeature', '-cp', native_cp,
        'com.openggf.tools.nativeimage.NativeEngine', str(output / 'OpenGGF')]
    argfile = work / 'native-image.args'
    argfile.write_text('\n'.join(json.dumps(a) for a in arguments), encoding='utf-8')
    notices = output / 'native-licenses/GraalVM'
    if notices.exists(): shutil.rmtree(notices)
    notices.mkdir(parents=True)
    for name in ('LICENSE.txt', 'THIRD_PARTY_LICENSE.txt'): shutil.copy2(graal / name, notices / name)
    shutil.copytree(graal / 'legal', notices / 'legal')
    (notices / 'SOURCE.txt').write_text('GraalVM Community ' + VERSION + '\nhttps://github.com/oracle/graal/tree/graal-25.4.4.1.1\nhttps://github.com/graalvm/graalvm-ce-builds/releases/tag/graal-25.4.4.1.1\n')
    if compile_image:
        compile_prepared(graal, output)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--graal', type=Path, default=Path(os.environ.get('JAVA_HOME', '')))
    parser.add_argument('--classes', type=Path, default=ROOT / 'target/classes')
    parser.add_argument('--classpath', type=Path, default=ROOT / 'target/native-runtime-classpath.txt')
    parser.add_argument('--output', type=Path, default=ROOT / 'target')
    parser.add_argument('--mods', type=Path)
    stages = parser.add_mutually_exclusive_group()
    stages.add_argument('--stage', choices=('all', 'prepare', 'compile'), default='all')
    stages.add_argument('--prepare-only', action='store_true', help='compatibility alias for --stage prepare')
    args = parser.parse_args()
    if args.stage == 'compile':
        compile_prepared(args.graal, args.output)
    else:
        build(args.graal, args.classes, args.classpath.read_text().strip(), args.output, args.mods,
              args.stage == 'all' and not args.prepare_only)
