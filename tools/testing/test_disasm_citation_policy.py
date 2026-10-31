"""Index/HEAD citation regression checks through the installed pre-commit path.

Run: python3 -m unittest discover -s tools/testing -p 'test_disasm_citation_policy.py'
"""
import contextlib
import io
from pathlib import Path
import shutil
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/disasm'))
import disasm_citations as dc
from test_disasm_citations import Fixture, git


class CitationPolicyTest(unittest.TestCase):
    def setUp(self):
        self.fx = Fixture()
        hooks = self.fx.sup / '.githooks'
        hooks.mkdir()
        for path in (ROOT / '.githooks').iterdir():
            if path.is_file():
                shutil.copy2(path, hooks / path.name)
        tool = self.fx.sup / 'tools/disasm/disasm_citations.py'
        tool.parent.mkdir(parents=True)
        shutil.copy2(ROOT / 'tools/disasm/disasm_citations.py', tool)

    def tearDown(self):
        self.fx.close()

    def policy(self):
        return subprocess.run(['sh', '.githooks/pre-commit'], cwd=self.fx.sup,
                              text=True, capture_output=True)

    def test_staged_increase_fails_even_with_valid_working_copy(self):
        path = self.fx.write('src/main/java/M.java', '// Obj_A (game.asm:5-7)\n')
        path.write_text('// Obj_A (game.asm:10-12)\n')
        result = self.policy()
        self.assertNotEqual(result.returncode, 0, result.stderr)
        self.assertIn('increased 0 -> 1', result.stderr)

    def test_existing_debt_and_unstaged_regression_do_not_block(self):
        path = self.fx.write('src/main/java/M.java', '// Obj_A (game.asm:5-7)\n')
        git(self.fx.sup, 'commit', '-qm', 'existing debt')
        self.fx.write('src/main/java/M.java', '// Obj_A (game.asm:5-7)\n// harmless\n')
        self.assertEqual(self.policy().returncode, 0)
        self.fx.write('src/main/java/M.java', '// Obj_A (game.asm:10-12)\n')
        path.write_text('// Obj_A (game.asm:10-12)\n// Obj_A (game.asm:5-7)\n')
        self.assertEqual(self.policy().returncode, 0)

    def test_out_of_range_new_file_and_per_file_budget(self):
        self.fx.write('src/main/java/A.java', '// Obj_A (game.asm:5-7)\n')
        git(self.fx.sup, 'commit', '-qm', 'existing debt')
        self.fx.write('src/main/java/A.java', '// Obj_A (game.asm:10-12)\n')
        self.fx.write('src/main/java/B.java', '// Obj_A (game.asm:900)\n')
        result = self.policy()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('B.java: stale/out-of-range citations increased 0 -> 1', result.stderr)

    def test_deleted_file_and_unstaged_files_are_not_scanned(self):
        self.fx.write('src/main/java/M.java', '// Obj_A (game.asm:5-7)\n')
        git(self.fx.sup, 'commit', '-qm', 'existing debt')
        git(self.fx.sup, 'rm', 'src/main/java/M.java')
        (self.fx.sup / 'src/main/java').mkdir(parents=True, exist_ok=True)
        (self.fx.sup / 'src/main/java/Untracked.java').write_text('// Obj_A (game.asm:900)\n')
        self.assertEqual(self.policy().returncode, 0)

    def test_protected_history_and_crlf_byte_prefix(self):
        first = b'// Obj_A (game.asm:5-7)\r\n'
        self.fx.write('.githooks/machine-local-path-grandfather.sha256',
                      f'# baseline-prefix\t{len(first)}\t{"0" * 64}\tsrc/main/java/M.java\n')
        # Test the guard directly; the resource hook separately authenticates prefix hashes.
        self.fx.write('src/main/java/M.java', first.decode() + '// Obj_A (game.asm:900)\r\n')
        with contextlib.redirect_stderr(io.StringIO()) as err:
            result = dc.main(['--repo', str(self.fx.sup), 'check-staged'])
        self.assertEqual(result, 1)
        self.assertIn('increased 0 -> 1', err.getvalue())


if __name__ == '__main__':
    unittest.main()
