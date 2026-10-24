#!/usr/bin/env python3
"""Launch the Stageplay agent CLI with owned headless rendering and JSON results.

Inputs: shot-local screenplay, .stageplay scene, user-supplied ROMs, optional engine jar.
Screenplay v6 supports lossless canonical decompilation and editor round trips.
Object casts use per-shot objects/objectActions and worldCommands
(CLEAR_BADNIKS, CLEAR_OBJECTS, REMOVE_OBJECT). Compile uses the engine's strict schema. Route plans emit existing native input
intents or explicitly labelled visual object movement; supplied ROMs are required.
Origin: OpenGGF Stageplay directing extension, 2026-10-21.
"""
import argparse
from contextlib import redirect_stdout
import json
import os
from pathlib import Path
import signal
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools' / 'testing'))
from maven_activity import build_activity_slot
from maven_queue import worktree_build_slot, inherited_slot, handle_termination


def run_owned(command, *, env, leases=None, stdout=None):
    """Cancellation retires this invocation's process group, including its encoder."""
    process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=stdout,
                               start_new_session=os.name == 'posix', **(leases or {}))
    try:
        return process.wait()
    except (KeyboardInterrupt, SystemExit):
        try:
            if os.name == 'posix':
                os.killpg(process.pid, signal.SIGTERM)
            else:
                process.terminate()
        except ProcessLookupError:
            pass  # The owned child may finish between interruption and retirement.
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            try:
                if os.name == 'posix':
                    os.killpg(process.pid, signal.SIGKILL)
                else:
                    process.kill()
            except ProcessLookupError:
                pass
            process.wait()
        raise


class JsonArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def main(argv=None):
    parser = JsonArgumentParser(description=__doc__)
    parser.add_argument('--build', action='store_true', help='Package current sources without running tests first')
    parser.add_argument('--jar', type=Path, help='Use an explicitly supplied packaged engine jar')
    parser.add_argument('command', nargs='?', default='help')
    parser.add_argument('arguments', nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    env = os.environ.copy()
    if args.build:
        code = run_owned([sys.executable, str(ROOT / 'tools/testing/maven_queue.py'),
                          '-Dmse=off', '-DskipTests', 'package'], env=env, stdout=sys.stderr)
        if code:
            raise ValueError(f"Engine build failed (exit {code}); see stderr diagnostics")
    jar = args.jar
    if jar is None:
        metadata = ROOT / 'target/openggf-artifact.properties'
        if not metadata.is_file():
            raise ValueError('No packaged engine: pass --build once (and after source changes), or --jar PATH')
        properties = dict(line.split('=', 1) for line in metadata.read_text().splitlines() if '=' in line)
        jar = ROOT / 'target' / (properties['finalName'] + '-jar-with-dependencies.jar')
    jar = jar.resolve()
    if not jar.is_file():
        raise ValueError(f'Engine jar not found: {jar}')
    java = str(Path(env['JAVA_HOME']) / 'bin/java') if env.get('JAVA_HOME') else 'java'
    command = [java, '-Xmx4g', '--enable-native-access=ALL-UNNAMED', '-Djava.awt.headless=true']
    if sys.platform.startswith('linux'):
        env.pop('DISPLAY', None)
        env.pop('WAYLAND_DISPLAY', None)
        command.append('-Dopenggf.headless.gl=surfaceless-egl')
    elif sys.platform == 'darwin':
        command.append('-XstartOnFirstThread')
    command += ['-cp', str(jar), 'com.openggf.tools.StageplayTool', args.command, *args.arguments]
    # CLI paths are interpreted relative to the caller, not the checkout used for leases.
    for index, item in enumerate(command[:-1]):
        if item in {'--scene', '--script', '--out', '--rom', '--s1-rom', '--s2-rom', '--s3k-rom', '--staging-dir', '--observations', '--request', '--queries', '--rom-descriptor', '--asset-descriptor'}:
            command[index + 1] = str(Path(command[index + 1]).resolve())
    with redirect_stdout(sys.stderr), worktree_build_slot(ROOT) as tree_fd, build_activity_slot(ROOT) as activity:
        return run_owned(command, env=env, leases=inherited_slot(tree_fd, activity))


if __name__ == '__main__':
    try:
        with handle_termination():
            raise SystemExit(main())
    except (ValueError, OSError) as error:
        print(json.dumps({'ok': False, 'error': str(error)}))
        raise SystemExit(2)
    except KeyboardInterrupt:
        print(json.dumps({'ok': False, 'error': 'Cancelled'}))
        raise SystemExit(130)
