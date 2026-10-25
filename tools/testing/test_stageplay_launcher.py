"""Exercise Stageplay executable paths without launching a JVM or encoder."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.name == 'posix', 'Executable Python stubs require POSIX')
class StageplayLauncherTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='stageplay launcher ')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.project = self.root / 'repository'
        self.caller = self.root / 'caller outside repository/working directory'
        self.binary = self.root / 'jdk/bin'
        self.encoders = self.root / 'path encoders'
        for directory in (self.project / 'tools/stageplay', self.project / 'tools/testing',
                          self.caller, self.binary, self.encoders):
            directory.mkdir(parents=True)
        for relative in ('tools/stageplay/stageplay.py', 'tools/testing/maven_activity.py',
                         'tools/testing/maven_queue.py'):
            shutil.copy2(ROOT / relative, self.project / relative)
        subprocess.run(['git', 'init', '-q', str(self.project)], check=True)
        self.jar = self.project / 'engine.jar'
        self.jar.touch()
        self._executable(self.binary / 'java', '''
import json
from pathlib import Path
import subprocess
import sys

executable = sys.argv[sys.argv.index('--ffmpeg') + 1]
encoder = subprocess.run([executable, '-version'], check=True, capture_output=True, text=True)
print(json.dumps({'cwd': str(Path.cwd()), 'ffmpeg': executable, 'encoder': encoder.stdout.strip()}))
''')

    def _executable(self, path, body):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f'#!{sys.executable}\n' + body, encoding='utf-8')
        path.chmod(0o755)

    def _launch(self, ffmpeg):
        env = dict(os.environ, JAVA_HOME=str(self.binary.parent), PYTHONDONTWRITEBYTECODE='1',
                   PATH=str(self.encoders) + os.pathsep + os.environ['PATH'])
        result = subprocess.run(
            [sys.executable, '-B', str(self.project / 'tools/stageplay/stageplay.py'),
             '--jar', str(self.jar), 'render', '--scene', 'film.stageplay', '--out', 'film.mkv',
             '--ffmpeg', ffmpeg],
            cwd=self.caller, env=env, capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        payload = json.loads(result.stdout)
        self.assertEqual(payload['cwd'], str(self.project))
        return payload

    def test_relative_ffmpeg_path_uses_caller_directory(self):
        executable = self.caller / 'encoder tools/ffmpeg'
        self._executable(executable, "print('caller encoder')\n")
        self._executable(self.project / 'encoder tools/ffmpeg', "print('repository decoy')\n")

        payload = self._launch('encoder tools/ffmpeg')

        self.assertEqual(payload['encoder'], 'caller encoder')
        self.assertEqual(payload['ffmpeg'], str(executable))

    def test_dot_relative_ffmpeg_path_uses_caller_directory(self):
        executable = self.caller / 'ffmpeg'
        self._executable(executable, "print('caller encoder')\n")
        self._executable(self.project / 'ffmpeg', "print('repository decoy')\n")

        payload = self._launch('./ffmpeg')

        self.assertEqual(payload['encoder'], 'caller encoder')
        self.assertEqual(payload['ffmpeg'], str(executable))

    def test_parent_relative_ffmpeg_path_uses_caller_directory(self):
        executable = self.caller.parent / 'encoder tools/ffmpeg'
        self._executable(executable, "print('parent encoder')\n")

        payload = self._launch('../encoder tools/ffmpeg')

        self.assertEqual(payload['encoder'], 'parent encoder')
        self.assertEqual(payload['ffmpeg'], str(executable))

    def test_absolute_ffmpeg_path_remains_usable(self):
        executable = self.root / 'absolute encoder/ffmpeg'
        self._executable(executable, "print('absolute encoder')\n")

        payload = self._launch(str(executable))

        self.assertEqual(payload['encoder'], 'absolute encoder')
        self.assertEqual(payload['ffmpeg'], str(executable))

    def test_bare_ffmpeg_name_retains_path_lookup(self):
        self._executable(self.encoders / 'ffmpeg', "print('PATH encoder')\n")
        self._executable(self.caller / 'ffmpeg', "print('caller decoy')\n")
        self._executable(self.project / 'ffmpeg', "print('repository decoy')\n")

        payload = self._launch('ffmpeg')

        self.assertEqual(payload['encoder'], 'PATH encoder')
        self.assertEqual(payload['ffmpeg'], 'ffmpeg')


if __name__ == '__main__':
    unittest.main()
