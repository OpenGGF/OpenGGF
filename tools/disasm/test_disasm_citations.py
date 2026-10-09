"""Behavioral tests for disasm_citations.py against throwaway git repositories.

Run: python3 -m unittest discover -s tools/disasm -p 'test_*.py'
"""
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import disasm_citations as dc  # noqa: E402

GIT = ['git', '-c', 'user.name=t', '-c', 'user.email=t@example.invalid', '-c', 'core.hooksPath=/dev/null',
       '-c', 'init.defaultBranch=main', '-c', 'commit.gpgsign=false']


def git(cwd, *args):
    return subprocess.run(GIT + list(args), cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def numbered(n, labels):
    """n lines; labels maps line number -> label definition text."""
    return ''.join((labels[i] if i in labels else f'\t\tnop\t; line {i}') + '\n' for i in range(1, n + 1))


class Fixture:
    """A superproject with one disassembly submodule ``docs/fakedisasm`` and three revisions.

    A: game.asm 60 lines, labels Obj_A@10, loc_1234@20, Alias@30 (with "; loc_ABCD").
    B: three comment lines inserted after line 2, line 25 edited -> A's 10 is B's 13.
    C: obj/01 Thing.asm renamed to obj/01 Thing Renamed.asm.
    """

    def __init__(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.dis = root / 'disasm-origin'
        self.dis.mkdir()
        git(self.dis, 'init', '-q')
        labels = {10: 'Obj_A:', 20: 'loc_1234:', 30: 'Alias:\t; loc_ABCD'}
        (self.dis / 'game.asm').write_text(numbered(60, labels))
        (self.dis / 'obj').mkdir()
        (self.dis / 'obj' / '01 Thing.asm').write_text(numbered(12, {3: 'Thing_Main:'}))
        (self.dis / 'maps').mkdir()
        (self.dis / 'maps' / 'Thing.asm').write_text(numbered(4, {}))
        git(self.dis, 'add', '-A')
        git(self.dis, 'commit', '-qm', 'A')
        self.A = git(self.dis, 'rev-parse', 'HEAD')
        lines = (self.dis / 'game.asm').read_text().splitlines(keepends=True)
        lines[24] = '\t\tmove.w\t#1,d0\t; edited\n'
        lines[2:2] = ['; comment\n'] * 3
        (self.dis / 'game.asm').write_text(''.join(lines))
        git(self.dis, 'commit', '-qam', 'B')
        self.B = git(self.dis, 'rev-parse', 'HEAD')
        git(self.dis, 'mv', 'obj/01 Thing.asm', 'obj/01 Thing Renamed.asm')
        git(self.dis, 'commit', '-qm', 'C')
        self.C = git(self.dis, 'rev-parse', 'HEAD')

        self.sup = root / 'super'
        self.sup.mkdir()
        git(self.sup, 'init', '-q')
        (self.sup / '.gitmodules').write_text('[submodule "docs/fakedisasm"]\n\tpath = docs/fakedisasm\n'
                                               f'\turl = {self.dis}\n')
        git(root, 'clone', '-q', str(self.dis), str(self.sup / 'docs' / 'fakedisasm'))
        git(self.sup / 'docs' / 'fakedisasm', 'checkout', '-q', self.A)
        git(self.sup, 'update-index', '--add', '--cacheinfo', f'160000,{self.A},docs/fakedisasm')
        (self.sup / 'src').mkdir()
        git(self.sup, 'add', '.gitmodules')
        git(self.sup, 'commit', '-qm', 'pin A')

    def write(self, rel, text, newline='\n'):
        path = self.sup / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.replace('\n', newline).encode())
        git(self.sup, 'add', rel)
        return path

    def check(self, *extra):
        import contextlib
        import io
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
            code = dc.main(['--repo', str(self.sup), 'check', '--json', *extra])
        import json
        return code, json.loads(out.getvalue())

    def close(self):
        self.tmp.cleanup()


class ParseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fx = Fixture()
        cls.sp = dc.Superproject(cls.fx.sup)
        cls.resolve = dc.Resolver(cls.sp, {'fakedisasm': cls.fx.A})

    @classmethod
    def tearDownClass(cls):
        cls.fx.close()

    def parse(self, name, text):
        return dc.parse_file(name, text, self.resolve)

    def test_formats(self):
        cites = self.parse('src/Foo.java', '\n'.join([
            '// Obj_A (game.asm:10-12) and lines 14, 16-17 via game.asm:14, 16-17',
            '/** ROM reference: game.asm lines 20-22 */',
            '// later, bare (:30) inherits game.asm',
            '// listing citation sonic3k.lst:1234',
            'String s = "game.asm:5";   // code, not a comment: ignored',
        ]))
        got = [(c.kind, c.rel, c.a, c.b) for c in cites]
        self.assertIn(('explicit', 'game.asm', 10, 12), got)
        self.assertIn(('explicit', 'game.asm', 14, 14), got)
        self.assertIn(('explicit', 'game.asm', 16, 17), got)
        self.assertIn(('explicit', 'game.asm', 20, 22), got)
        self.assertIn(('bare', 'game.asm', 30, 30), got)
        self.assertIn('generated', [c.kind for c in cites])
        self.assertNotIn(5, [c.a for c in cites])

    def test_json_takes_explicit_citations_only(self):
        cites = self.parse('evidence.json', '{"source": "game.asm:10-12", "x": 1234, "note": "then :20"}')
        self.assertEqual([(c.kind, c.a, c.b) for c in cites], [('explicit', 10, 12)])

    def test_markdown_section_scopes_bare_citations(self):
        cites = self.parse('notes.md', 'game.asm:10\n\n:20 same section\n# Next\n:30 new section\n')
        self.assertEqual([(c.kind, c.a) for c in cites], [('explicit', 10), ('bare', 20)])

    def test_s1_names_with_spaces_and_prefixes(self):
        cites = self.parse('src/Foo.java', '// see docs/fakedisasm/obj/01 Thing.asm:3-4 and also 01 Thing.asm:5\n'
                                           '// From game.asm:7')
        self.assertEqual([(c.sub, c.rel, c.a) for c in cites],
                         [('fakedisasm', 'obj/01 Thing.asm', 3), ('fakedisasm', 'obj/01 Thing.asm', 5),
                          ('fakedisasm', 'game.asm', 7)])

    def test_ambiguous_basename_stays_unresolved(self):
        # "Thing.asm" is maps/Thing.asm exactly, but "01 Thing.asm" exists too: never guess.
        (cite,) = self.parse('src/Foo.java', '// Widget Thing.asm:3')
        self.assertIsNone(cite.sub)


class LineMapTest(unittest.TestCase):
    def test_insert_delete_and_change(self):
        # insert 3 after line 2; replace 25 (old) with 1 line; delete old 40-41
        lm = dc.LineMap([(2, 0, 3, 3), (25, 1, 28, 1), (40, 2, 42, 0)])
        self.assertEqual(lm.map(1), 1)
        self.assertEqual(lm.map(2), 2)
        self.assertEqual(lm.map(3), 6)
        self.assertEqual(lm.map(24), 27)
        self.assertIsNone(lm.map(25))
        self.assertEqual(lm.map(26), 29)
        self.assertIsNone(lm.map(40))
        self.assertIsNone(lm.map(41))
        self.assertEqual(lm.map(42), 43)


class InPlaceEditTest(unittest.TestCase):
    def test_carry_edited_accepts_renames_and_refuses_new_code(self):
        old = ['Obj:', '\tbtst\t#1,status(a1)', '\tbne.s\tObj', '\trts']
        renamed = ['Obj:', '\tbtst\t#Status_InAir,status(a1)', '\tbne.s\tObj', '\trts']
        rewritten = ['Obj:', '\tmove.w\td0,d1', '\tbne.s\tObj', '\trts']
        hunk = [(2, 1, 2, 1)]
        self.assertIsNone(dc.LineMap(hunk, old, renamed).map_range(1, 3))
        self.assertEqual(dc.LineMap(hunk, old, renamed).map_range(1, 3, carry_edited=True), (1, 3))
        self.assertIsNone(dc.LineMap(hunk, old, rewritten).map_range(1, 3, carry_edited=True))
        self.assertIsNone(dc.LineMap([(2, 0, 3, 1)], old, old + ['x']).map_range(1, 3, carry_edited=True))


class CheckAndRemapTest(unittest.TestCase):
    def setUp(self):
        self.fx = Fixture()

    def tearDown(self):
        self.fx.close()

    def statuses(self, *extra):
        code, report = self.fx.check(*extra)
        return code, report

    def test_correct_citations_pass_and_a_shifted_one_is_stale(self):
        self.fx.write('src/main/java/Ok.java', '// Obj_A (game.asm:10-12)\n// Alias (game.asm:30)\n// loc_ABCD (game.asm:30)\n')
        code, report = self.statuses()
        self.assertEqual(code, 0)
        self.assertEqual(report['counts'].get('ok'), 3)
        # break it on purpose: lines inserted above a label leave the citation before it
        self.fx.write('src/main/java/Ok.java', '// Obj_A (game.asm:5-7)\n')
        code, report = self.statuses()
        self.assertEqual(report['counts'].get('stale'), 1)
        self.assertEqual(code, 0)
        code, _ = self.statuses('--strict')
        self.assertEqual(code, 1)

    def test_range_inside_the_routine_is_near_not_ok(self):
        self.fx.write('src/main/java/Near.java', '// Obj_A (game.asm:15-17)\n')
        _, report = self.statuses()
        self.assertEqual(report['counts'].get('near'), 1)

    def test_hard_errors(self):
        self.fx.write('src/main/java/Bad.java', '// Obj_A (game.asm:59-70)\n// docs/fakedisasm/obj/missing.asm:3\n')
        code, report = self.statuses()
        self.assertEqual(code, 1)
        kinds = sorted(f['kind'] for f in report['findings'])
        self.assertEqual(kinds, ['missing-file', 'out-of-range'])

    def test_unresolved_address_label_is_not_judged_by_a_farther_label(self):
        self.fx.write('src/main/java/Far.java', '// Obj_A then loc_9999 (game.asm:12)\n')
        _, report = self.statuses()
        self.assertEqual(report['counts'].get('unverifiable'), 1)

    def test_rename_hint_and_rename_reporting(self):
        self.fx.write('src/main/java/R.java', '// Thing_Main (docs/fakedisasm/obj/01 Thing.asm:3)\n')
        _, report = self.statuses('--rev', self.fx.C)
        (finding,) = report['findings']
        self.assertEqual(finding['kind'], 'missing-file')
        self.assertIn('01 Thing Renamed.asm', finding['detail'])

    def test_remap_carries_numbers_and_preserves_bytes(self):
        path = self.fx.write('src/main/java/M.java', 'class M {\n    // Obj_A (game.asm:10-12) then (:20)\n'
                                           '    // edited line game.asm:24-26\n}\n', newline='\r\n')
        before = path.read_bytes()
        import contextlib
        import io
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            dc.main(['--repo', str(self.fx.sup), 'remap', 'fakedisasm', self.fx.A, self.fx.B, '--write'])
        after = path.read_bytes()
        self.assertEqual(after, before.replace(b'game.asm:10-12', b'game.asm:13-15').replace(b'(:20)', b'(:23)'))
        self.assertIn(b'\r\n', after)
        self.assertIn('needs review', out.getvalue())   # 24-26 touches the edited line 25
        self.assertIn('moved=2', out.getvalue())

    def test_remap_leaves_hook_protected_history_alone(self):
        first = '// Obj_A (game.asm:10-12)\n'
        path = self.fx.write('src/main/java/H.java', first + '// Obj_A again (game.asm:10-12)\n')
        self.fx.write('.githooks/machine-local-path-grandfather.sha256',
                      f'# baseline-prefix\t{len(first)}\t{"0" * 64}\tsrc/main/java/H.java\n')
        import contextlib
        import io
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            dc.main(['--repo', str(self.fx.sup), 'remap', 'fakedisasm', self.fx.A, self.fx.B, '--write'])
        self.assertEqual(path.read_text(), first + '// Obj_A again (game.asm:13-15)\n')
        self.assertIn('protected-history', out.getvalue())

    def test_remap_never_rewrites_json_evidence(self):
        path = self.fx.write('docs/evidence.json', '{"source": "game.asm:10-12"}\n')
        import contextlib
        import io
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            dc.main(['--repo', str(self.fx.sup), 'remap', 'fakedisasm', self.fx.A, self.fx.B, '--write'])
        self.assertEqual(path.read_text(), '{"source": "game.asm:10-12"}\n')
        self.assertIn('json-evidence', out.getvalue())

    def test_remap_reports_renamed_files(self):
        self.fx.write('src/main/java/R.java', '// Thing_Main (docs/fakedisasm/obj/01 Thing.asm:3)\n')
        import contextlib
        import io
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            dc.main(['--repo', str(self.fx.sup), 'remap', 'fakedisasm', self.fx.B, self.fx.C])
        self.assertIn('renamed to obj/01 Thing Renamed.asm', out.getvalue())


if __name__ == '__main__':
    unittest.main()
