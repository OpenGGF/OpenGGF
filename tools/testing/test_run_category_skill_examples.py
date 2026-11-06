"""Enforce queued Maven examples and the generated skill mirror through Python CI."""
from pathlib import Path
import importlib.util
import shlex
import tempfile
import unittest
from unittest.mock import patch

from maven_queue import build_only

ROOT = Path(__file__).resolve().parents[2]
_SPEC = importlib.util.spec_from_file_location('sync_skills', ROOT / 'tools/git/sync_skills.py')
sync_skills = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(sync_skills)


def unqueued_examples(text):
    """Return unsafe direct Maven lines from shell fences, ignoring prose/quotes.

    This checks the simple command-per-line examples maintained in these skills,
    including backslash continuations. It is deliberately not a shell parser.
    """
    shell = False
    fenced = False
    command = ''
    start = 0
    failures = []
    for number, line in enumerate(text.splitlines(), 1):
        if line.startswith('```'):
            shell = not fenced and line.strip() in {'```bash', '```sh', '```shell'}
            fenced = not fenced
            command = ''
            continue
        if not shell:
            continue
        if not command:
            start = number
        command += line.rstrip().removesuffix('\\') + ' '
        if line.rstrip().endswith('\\'):
            continue
        tokens = shlex.split(command, comments=True)
        if tokens and Path(tokens[0]).name in {'mvn', 'mvnw', 'mvn.cmd', 'mvnw.cmd'}:
            if not build_only(tokens[1:]):
                failures.append(start)
        command = ''
    return failures


class TraceSkillExampleTests(unittest.TestCase):
    def setUp(self):
        env = patch.dict('os.environ', {'MAVEN_ARGS': ''})
        env.start()
        cwd = patch('maven_queue.Path.cwd', return_value=ROOT)
        cwd.start()
        self.addCleanup(cwd.stop)
        self.addCleanup(env.stop)

    def test_direct_test_commands_rejected(self):
        for command in ['mvn test', 'mvn -Ptrace-replay "-Dtest=$TRACE_TEST" test',
                        './mvnw verify', 'mvn package', 'mvn surefire:test',
                        'mvn -DskipTests=false package',
                        'mvn -DskipTests=true -DskipTests=false package',
                        'mvn -Dmse=off \\\n  "-Ds3k.rom.path=/absolute/game.gen" test']:
            with self.subTest(command=command):
                self.assertEqual([2], unqueued_examples('```bash\n' + command + '\n```'))

    def test_queued_and_build_only_examples_allowed(self):
        for command in ['python3 tools/testing/maven_queue.py -Ptrace-replay test',
                        'python3 /absolute/Main/tools/testing/maven_queue.py test',
                        'mvn compile', 'mvn test-compile', 'mvn -DskipTests package',
                        'mvn package -Dmaven.test.skip=true',
                        '# mvn test', 'echo "mvn test"']:
            with self.subTest(command=command):
                self.assertEqual([], unqueued_examples('```bash\n' + command + '\n```'))

    def test_explanatory_quotes_ignored(self):
        self.assertEqual([], unqueued_examples(
            'Use `mvn test` as a negative example.\n> ```bash\n> mvn test\n> ```\n'
            '```text\nmvn test\n```'))

    def test_maintained_trace_skill_examples(self):
        skills = sorted((ROOT / '.agents/skills').glob('*trace*/SKILL.md'))
        self.assertGreaterEqual(len(skills), 2)
        for skill in skills:
            with self.subTest(skill=str(skill.relative_to(ROOT))):
                self.assertEqual([], unqueued_examples(skill.read_text()),
                                 'Use python3 tools/testing/maven_queue.py for tests')


class SkillMirrorTest(unittest.TestCase):
    def test_committed_mirror_matches_canonical_tree(self):
        self.assertEqual([], sync_skills.drift(ROOT),
                         'run python3 tools/git/sync_skills.py')

    def test_sync_repairs_every_drift_kind(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / '.agents/skills/a').mkdir(parents=True)
            (root / '.agents/skills/a/SKILL.md').write_text('new')
            (root / '.agents/skills/a/ref.md').write_text('ref')
            (root / '.claude/skills/a').mkdir(parents=True)
            (root / '.claude/skills/a/SKILL.md').write_text('old')
            (root / '.claude/skills/stale').mkdir()
            (root / '.claude/skills/stale/SKILL.md').write_text('gone')
            self.assertEqual(['missing .claude/skills/a/ref.md',
                              'extra .claude/skills/stale/SKILL.md',
                              'differs .claude/skills/a/SKILL.md'],
                             sync_skills.drift(root))
            self.assertEqual(1, sync_skills.main(['--check', '--root', str(root)]))
            sync_skills.sync(root)
            self.assertEqual([], sync_skills.drift(root))
            self.assertEqual('new', (root / '.claude/skills/a/SKILL.md').read_text())
            self.assertFalse((root / '.claude/skills/stale').exists())

    def test_symlinked_mirror_is_drift(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / '.agents/skills/a').mkdir(parents=True)
            (root / '.agents/skills/a/SKILL.md').write_text('x')
            (root / '.claude').mkdir()
            (root / '.claude/skills').symlink_to('../.agents/skills')
            self.assertEqual(1, len(sync_skills.drift(root)))
            sync_skills.sync(root)
            self.assertFalse((root / '.claude/skills').is_symlink())
            self.assertEqual([], sync_skills.drift(root))


if __name__ == '__main__':
    unittest.main()
