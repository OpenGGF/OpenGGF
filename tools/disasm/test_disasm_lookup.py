"""Immutable source lookup safety tests; only tiny temporary Git fixtures.

Run: python3 -B -m unittest discover -s tools/disasm -p 'test_*.py'
Origin: 2026-10-26 private integration source-lookup task.
"""
import argparse
import base64
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).with_name('disasm_citations.py').resolve()
sys.path.insert(0, str(SCRIPT.parent))
import disasm_citations as citations
import disasm_lookup as lookup
GIT = ['git', '-c', 'user.name=t', '-c', 'user.email=t@example.invalid',
       '-c', 'core.hooksPath=/dev/null', '-c', 'commit.gpgsign=false']


def git(cwd, *args, data=None):
    return subprocess.run(GIT + list(args), cwd=cwd, input=data,
                          capture_output=True, check=True).stdout.strip().decode()


class LookupTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / 'source'
        self.repo.mkdir()
        git(self.repo, 'init', '-q')
        self.path = "src/odd: space ' $(touch UNWANTED)\tname.asm"
        self.source = (b'; Target: only a comment\r\n'
                       b'Owner:\r\n'
                       b'\tbra.s .loop\r\n'
                       b'.loop:\trts\r\n'
                       b'Target:\tnop ; Target is not a second reference\r\n'
                       b'\tdc.b "Target; .loop"\r\n'
                       b'\tjsr Target\r\n'
                       b'Other:\n'
                       b'.loop:\trts\n'
                       b'\tbra.s .loop\n'
                       b'\tjsr TargetExtra\n'
                       b'\tjsr Other.Target\n'
                       b'\tjsr Target')
        files = {self.path: self.source,
                 'src/second.asm': b'Duplicate:\trts\n',
                 'src/first.asm': b'Duplicate:\trts\n',
                 'src/orphan.asm': b'.orphan:\trts\n',
                 'src/data.bin': b'Target\x00binary',
                 'elsewhere.asm': b'Target:\trts\n'}
        for name, data in files.items():
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        (self.repo / 'src/link.asm').symlink_to('../../outside.asm')
        git(self.repo, 'add', '.')
        git(self.repo, 'commit', '-qm', 'original')
        self.rev = git(self.repo, 'rev-parse', 'HEAD')
        self.gitdir = self.root / 'objects.git'
        git(self.root, 'clone', '-q', '--bare', str(self.repo), str(self.gitdir))
        (self.repo / self.path).write_text('Target:\nChanged:\n')
        git(self.repo, 'commit', '-qam', 'moving checkout')

    def invoke(self, label='Target', *, expected=0, rev=None, root='src',
               extra=(), env=None, gitdir=None, cwd=None):
        options = []
        index = 0
        while index < len(extra):
            if '=' in extra[index]:
                options.append(extra[index])
                index += 1
            else:
                options.append(extra[index] + '=' + extra[index + 1])
                index += 2
        argv = [sys.executable, '-B', str(SCRIPT), 'lookup',
                '--git-dir=' + str(gitdir or self.gitdir), '--rev=' + (rev or self.rev),
                '--root=' + root, '--label=' + label, '--json', *options]
        p = subprocess.run(argv, cwd=cwd or self.root, capture_output=True, text=True, env=env)
        self.assertEqual(p.returncode, expected, (p.stdout, p.stderr))
        report = json.loads(p.stdout)
        self.assertFalse(p.stderr)
        return report

    def test_exact_definition_references_and_raw_provenance(self):
        report = self.invoke(extra=('--context', '1'))
        self.assertEqual(report['status'], 'found')
        self.assertEqual(report['revision'], self.rev)
        self.assertEqual(report['counts'], {'definition': 1, 'reference': 2})
        hits = report['hits']
        self.assertEqual([(h['path'], h['line'], h['kind']) for h in hits],
                         [(self.path, 5, 'definition'), (self.path, 7, 'reference'),
                          (self.path, 13, 'reference')])
        for hit in hits:
            self.assertEqual(hit['file_bytes'], len(self.source))
            self.assertEqual(hit['file_sha256'], hashlib.sha256(self.source).hexdigest())
            self.assertEqual(git(self.repo, 'rev-parse', f'{self.rev}:{self.path}'), hit['blob'])
            window = hit['context']
            raw = window['text'].encode('utf-8')
            self.assertEqual(raw, self.source[window['byte_offset']:window['byte_offset'] + window['bytes']])
            self.assertEqual(window['sha256'], hashlib.sha256(raw).hexdigest())
            self.assertEqual(window['total_lines'], 13)
            self.assertLessEqual(window['end_line'] - window['start_line'], 2)
            command = hit['git_show']
            self.assertEqual(shlex.split(command['shell']), command['argv'])
            shown = subprocess.run(command['argv'], cwd=self.root, check=True, capture_output=True).stdout
            self.assertEqual(shown, self.source)
        self.assertFalse((self.root / 'UNWANTED').exists())

    def test_local_scopes_are_distinct_and_filterable(self):
        report = self.invoke('.loop', expected=1)
        self.assertEqual(report['status'], 'ambiguous')
        definitions = [h for h in report['hits'] if h['kind'] == 'definition']
        self.assertEqual([h['scope'] for h in definitions],
                         [{'label': 'Owner', 'line': 2}, {'label': 'Other', 'line': 8}])
        scoped = self.invoke('.loop', extra=('--scope', 'Owner', '--path', self.path))
        self.assertEqual([(h['line'], h['kind']) for h in scoped['hits']],
                         [(3, 'reference'), (4, 'definition')])

    def test_unknown_include_scope_is_not_claimed_unique(self):
        report = self.invoke('.orphan', expected=1)
        self.assertEqual(report['status'], 'unresolved_scope')
        self.assertIsNone(report['hits'][0]['scope'])

    def test_duplicate_globals_and_path_disambiguation(self):
        report = self.invoke('Duplicate', expected=1)
        self.assertEqual(report['status'], 'ambiguous')
        self.assertEqual([h['path'] for h in report['hits']], ['src/first.asm', 'src/second.asm'])
        self.assertEqual(self.invoke('Duplicate', extra=('--path', 'src/first.asm'))['status'], 'found')

    def test_root_is_literal_and_cannot_escape_to_other_definition(self):
        self.assertEqual(self.invoke(root='.', expected=1)['status'], 'ambiguous')
        self.invoke(root='src', extra=('--path', 'elsewhere.asm'), expected=2)

    def test_references_only_and_no_match(self):
        self.assertEqual(self.invoke('TargetExtra', expected=1)['status'], 'references_only')
        self.assertEqual(self.invoke('Missing', expected=1)['status'], 'no_match')

    def test_immutable_revision_and_no_checkout_requirement(self):
        before = git(self.gitdir, 'rev-parse', 'HEAD')
        self.assertEqual(self.invoke()['hits'][0]['line'], 5)
        self.assertEqual(git(self.gitdir, 'rev-parse', 'HEAD'), before)
        self.assertFalse((self.gitdir / 'src').exists())

    def test_nonbare_repository_root_is_independent_of_callers_subdirectory(self):
        report = self.invoke(gitdir=self.repo / '.git', cwd=self.repo / 'src')
        self.assertEqual(report['hits'][0]['path'], self.path)
        shown = subprocess.run(report['hits'][0]['git_show']['argv'], cwd=self.repo / 'src',
                               capture_output=True, check=True).stdout
        self.assertEqual(shown, self.source)

    def test_revision_must_be_full_commit_object_not_blob_tree_or_expression(self):
        blob = git(self.repo, 'rev-parse', f'{self.rev}:{self.path}')
        tree = git(self.repo, 'rev-parse', f'{self.rev}^{{tree}}')
        for value in ['HEAD', self.rev[:12], self.rev + '^', '--all', '0' * 40, blob, tree]:
            with self.subTest(value=value):
                self.assertEqual(self.invoke(rev=value, expected=2)['status'], 'error')

    def test_invalid_paths_labels_and_bounds_are_rejected(self):
        cases = [('--root', '../src'), ('--root', '/src'), ('--root', 'src/./x'),
                 ('--root', 'src//x'), ('--root', 'src/..'), ('--root', 'missing'),
                 ('--root', 'src/link.asm'), ('--path', 'src/link.asm'),
                 ('--path', 'src/../elsewhere.asm'), ('--path', ':src'),
                 ('--label', 'Target;touch UNWANTED'), ('--label', '--all'),
                 ('--label', ''), ('--scope', 'Owner'),
                 ('--context', '-1'), ('--context', '21'),
                 ('--max-hits', '0'), ('--max-hits', '101')]
        for pair in cases:
            with self.subTest(pair=pair):
                self.assertEqual(self.invoke(extra=pair, expected=2)['status'], 'error')
        self.assertFalse((self.root / 'UNWANTED').exists())

    def test_result_limit_is_explicit_and_never_partial_success(self):
        report = self.invoke(expected=2, extra=('--max-hits', '1'))
        self.assertEqual(report['status'], 'error')
        self.assertIn('max-hits', report['error'])
        self.assertNotIn('hits', report)

    def budget_command(self, revision, maximum):
        args = argparse.Namespace(git_dir=str(self.gitdir), rev=revision, root='src',
                                  path=None, label='Budget', scope=None, context=3,
                                  max_hits=maximum, json=True)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(lookup.command(args, citations.LABEL_DEF), 2)
        report = json.loads(out.getvalue())
        self.assertEqual(set(report), {'version', 'status', 'error'})
        self.assertEqual(report['status'], 'error')
        self.assertIn(f'--max-hits={maximum}', report['error'])

    def test_hit_budget_stops_lexical_scan_on_first_excess_hit(self):
        revision = self.commit_file('src/budget.asm', b'Budget:\n' * 64)
        with mock.patch.object(lookup, 'code_only', wraps=lookup.code_only) as scan:
            self.budget_command(revision, 1)
        self.assertEqual(scan.call_count, 2, 'scan must stop at the first excess hit')

    def test_hit_budget_does_not_split_or_read_the_remaining_lines(self):
        class GuardedBytes(bytes):
            def split(self, *args, **kwargs):
                raise AssertionError('eager whole-blob line split before enforcing the budget')

            splitlines = split

            def __getitem__(self, key):
                if isinstance(key, slice) and (key.stop is None or key.stop > 16):
                    raise AssertionError('read beyond the first excess hit')
                return super().__getitem__(key)

        # Only 512 bytes, with a read guard after the second eight-byte line.
        # The real scanner must reject before splitting or slicing the tail.
        data = GuardedBytes(b'Budget:\n' * 64)
        with mock.patch.object(lookup.GitSource, 'candidates', return_value=['src/budget.asm']), \
             mock.patch.object(lookup.GitSource, 'blob', return_value=('a' * 40, data)):
            self.budget_command(self.rev, 1)

    def test_remaining_budget_counts_prior_files_and_both_same_line_kinds(self):
        self.commit_file('src/budget-a.asm', b'Budget:\n')
        revision = self.commit_file('src/budget-b.asm', b'Budget:\tjsr Budget\n' + b'\tnop\n' * 64)
        with mock.patch.object(lookup, 'code_only', wraps=lookup.code_only) as scan:
            self.budget_command(revision, 2)
        self.assertEqual(scan.call_count, 2, 'one prior hit leaves room for only one line/kind')

    def test_context_base64_preserves_crlf_bom_and_final_line_bytes(self):
        cases = [b'\xef\xbb\xbfBudget:\r\n\tjsr Budget\r\n',
                 b'; lead\r\nBudget:\r\n\tjsr Budget',
                 b'\xef\xbb\xbfBudget:\n\tjsr Budget', b'Budget:\n', b'Budget:']
        for data in cases:
            with self.subTest(data=data):
                revision = self.commit_file('src/context.asm', data)
                report = self.invoke('Budget', rev=revision, extra=('--context', '1'))
                self.assertEqual(report['version'], 1)
                lines = data.splitlines(keepends=True)
                for hit in report['hits']:
                    window = hit['context']
                    self.assertIn('base64', window, 'Root-selected additive byte-consumer field')
                    raw = base64.b64decode(window['base64'], validate=True)
                    start = max(1, hit['line'] - 1)
                    end = min(len(lines), hit['line'] + 1)
                    self.assertEqual((window['start_line'], window['end_line'], window['total_lines']),
                                     (start, end, len(lines)))
                    self.assertEqual(window['byte_offset'], sum(map(len, lines[:start - 1])))
                    self.assertEqual(raw, b''.join(lines[start - 1:end]))
                    self.assertEqual(raw, data[window['byte_offset']:window['byte_offset'] + window['bytes']])
                    self.assertEqual(raw, window['text'].encode('utf-8'))
                    self.assertEqual(len(raw), window['bytes'])
                    self.assertEqual(hashlib.sha256(raw).hexdigest(), window['sha256'])

    def commit_file(self, path, data):
        (self.repo / path).write_bytes(data)
        git(self.repo, 'add', '--', path)
        git(self.repo, 'commit', '-qm', 'fixture')
        revision = git(self.repo, 'rev-parse', 'HEAD')
        self.clone_objects(revision)
        return revision

    def clone_objects(self, revision):
        self.gitdir = self.root / (revision + '.git')
        git(self.root, 'clone', '-q', '--bare', str(self.repo), str(self.gitdir))

    def test_binary_or_non_utf8_source_rejected_instead_of_replaced(self):
        for data in [b'Target:\x00\n', b'Target:\xff\n', b'Target:\x01\n']:
            with self.subTest(data=data):
                rev = self.commit_file('src/binary.asm', data)
                report = self.invoke(rev=rev, expected=2)
                self.assertIn('text', report['error'])

    def test_symlinks_are_not_searched_and_explicit_symlink_is_rejected(self):
        path = self.repo / 'src/Target.asm'
        path.symlink_to('Target')
        git(self.repo, 'add', '--', 'src/Target.asm')
        git(self.repo, 'commit', '-qm', 'symlink candidate')
        rev = git(self.repo, 'rev-parse', 'HEAD')
        self.clone_objects(rev)
        report = self.invoke(rev=rev)
        self.assertEqual([h['path'] for h in report['hits']], [self.path])
        report = self.invoke(rev=rev, extra=('--path', 'src/Target.asm'), expected=2)
        self.assertIn('regular', report['error'])

    def test_include_invalidates_unknown_local_scope_until_next_global(self):
        rev = self.commit_file('src/include.asm', b'Owner:\n\tinclude "other.asm"\n'
                               b'.later:\trts\nNext:\n.later:\trts\n')
        report = self.invoke('.later', rev=rev, expected=1)
        self.assertEqual(report['status'], 'ambiguous')
        self.assertEqual([h['scope'] for h in report['hits']],
                         [None, {'label': 'Next', 'line': 4}])
        self.assertEqual(self.invoke('.later', rev=rev, extra=('--scope', 'Owner'), expected=1)['status'], 'no_match')

    def test_repeated_global_scope_definitions_are_not_collapsed(self):
        rev = self.commit_file('src/repeated.asm', b'Again:\n.loop:\nAgain:\n.loop:\n')
        report = self.invoke('.loop', rev=rev, extra=('--path', 'src/repeated.asm', '--scope', 'Again'), expected=1)
        self.assertEqual(report['status'], 'ambiguous')
        self.assertEqual([h['scope']['line'] for h in report['hits']], [1, 3])

    def test_star_comments_strings_and_at_locals(self):
        rev = self.commit_file('src/at.asm', b'* AtLabel:\n; AtLabel:\nOwner:\n'
                               b'\tdc.b "AtLabel",\'AtLabel\'\n@again:\tbra.s @again\n')
        self.assertEqual(self.invoke('AtLabel', rev=rev, expected=1)['status'], 'no_match')
        report = self.invoke('@again', rev=rev)
        self.assertEqual(report['counts'], {'definition': 1, 'reference': 1})
        self.assertEqual([h['line'] for h in report['hits']], [5, 5])

    def test_numeric_or_dollar_token_suffix_is_not_an_exact_reference(self):
        rev = self.commit_file('src/tokens.asm', b'\tdc.l 123Thing,$Thing,Thing$,ThingExtra\n')
        self.assertEqual(self.invoke('Thing', rev=rev, expected=1)['status'], 'no_match')

    def test_labeled_include_and_bom_preserve_bytes_without_invented_scope(self):
        data = b'\xef\xbb\xbfBomLabel:\n.local:\nIncluded: include "child.asm"\n.unknown:\n'
        rev = self.commit_file('src/bom.asm', data)
        self.assertEqual(self.invoke('BomLabel', rev=rev)['status'], 'found')
        self.assertEqual(self.invoke('.local', rev=rev)['hits'][0]['scope']['label'], 'BomLabel')
        self.assertEqual(self.invoke('.unknown', rev=rev, expected=1)['status'], 'unresolved_scope')

    def test_resource_limits_fail_without_partial_results(self):
        args = argparse.Namespace(git_dir=str(self.gitdir), rev=self.rev, root='src',
                                  path=None, label='Target', scope=None, context=3,
                                  max_hits=20, json=True)
        # Reduced bounds exercise real Git I/O, source reads and serialization
        # without constructing large repositories or keeping diagnostic output.
        for constant, limit, reason in [('MAX_BLOB', 8, 'blob exceeds'),
                                        ('MAX_SOURCE', 8, 'candidate source exceeds'),
                                        ('MAX_OUTPUT', 128, 'report exceeds')]:
            with self.subTest(constant=constant):
                out = io.StringIO()
                with mock.patch.object(lookup, constant, limit), contextlib.redirect_stdout(out):
                    self.assertEqual(lookup.command(args, citations.LABEL_DEF), 2)
                report = json.loads(out.getvalue())
                self.assertIn(reason, report['error'])
                self.assertNotIn('hits', report)

    def test_textconv_driver_is_never_executed(self):
        # Git grep/cat-file/show must all read original object bytes, even when
        # a repository's attributes/config request an executable text filter.
        rev = self.commit_file('.gitattributes', b'*.asm diff=source\n')
        git(self.gitdir, 'config', 'diff.source.textconv', 'touch UNWANTED')
        report = self.invoke(rev=rev)
        subprocess.run(report['hits'][0]['git_show']['argv'], cwd=self.root,
                       capture_output=True, check=True)
        self.assertFalse((self.root / 'UNWANTED').exists())

    def test_full_sha256_commit_is_supported(self):
        sha_repo = self.root / 'sha256-source'
        sha_repo.mkdir()
        git(sha_repo, 'init', '-q', '--object-format=sha256')
        (sha_repo / 'main.asm').write_bytes(b'ShaLabel:\trts\n')
        git(sha_repo, 'add', '.')
        git(sha_repo, 'commit', '-qm', 'sha256')
        rev = git(sha_repo, 'rev-parse', 'HEAD')
        self.assertEqual(len(rev), 64)
        report = self.invoke('ShaLabel', gitdir=sha_repo / '.git', rev=rev, root='.')
        self.assertEqual(len(report['hits'][0]['blob']), 64)

    def test_git_replace_objects_do_not_change_commit_source(self):
        old = git(self.repo, 'rev-parse', f'{self.rev}:{self.path}')
        new = git(self.gitdir, 'hash-object', '-w', '--stdin', data=b'Target:\nREPLACED:\n')
        git(self.gitdir, 'replace', old, new)
        self.assertEqual(self.invoke()['hits'][0]['file_sha256'], hashlib.sha256(self.source).hexdigest())

    def test_literal_pathspec_characters_and_leading_options(self):
        path = 'src/[special]*:name\n.asm'
        rev = self.commit_file(path, b'Unusual:\trts\n')
        self.assertEqual(self.invoke('Unusual', rev=rev, extra=('--path', path))['hits'][0]['path'], path)
        self.invoke('Unusual', rev=rev, extra=('--path', 'src/*'), expected=2)
        rev = self.commit_file('-option.asm', b'Option:\trts\n')
        self.assertEqual(self.invoke('Option', rev=rev, root='.', extra=('--path=-option.asm',))['hits'][0]['path'], '-option.asm')

    def test_git_environment_cannot_redirect_source(self):
        env = dict(os.environ, GIT_DIR=str(self.repo / '.git'), GIT_WORK_TREE=str(self.repo),
                   GIT_INDEX_FILE=str(self.root / 'foreign-index'), GIT_GLOB_PATHSPECS='1',
                   GIT_CONFIG_COUNT='1', GIT_CONFIG_KEY_0='core.pager',
                   GIT_CONFIG_VALUE_0='touch UNWANTED')
        self.assertEqual(self.invoke(env=env)['hits'][0]['line'], 5)
        self.assertFalse((self.root / 'foreign-index').exists())
        self.assertFalse((self.root / 'UNWANTED').exists())

    def test_gitdir_symlink_and_missing_gitdir_rejected(self):
        linked = self.root / 'linked.git'
        linked.symlink_to(self.gitdir, target_is_directory=True)
        self.invoke(gitdir=linked, expected=2)
        self.invoke(gitdir=self.root / 'missing.git', expected=2)

    def test_output_is_deterministic(self):
        self.assertEqual(self.invoke(), self.invoke())


class ContextBudgetTest(unittest.TestCase):
    def test_oversize_window_rejects_before_encoding(self):
        data = b'A:\n' + b'x' * 64 + b'\n'
        with mock.patch.object(lookup, 'MAX_OUTPUT', 16), mock.patch.object(
                lookup.base64, 'b64encode', side_effect=AssertionError('encoded over-budget context')):
            with self.assertRaisesRegex(lookup.LookupError, 'context.*bound'):
                lookup.context_window(data, {'line': 1}, 0, 2, 1)

    def test_context_budget_is_shared_across_hits_before_encoding(self):
        source = mock.Mock(prefix=['git'], revision='a' * 40)
        source.candidates.return_value = ['src/x.asm']
        source.blob.return_value = ('b' * 40, b'A:\n\tjsr A\n\tjsr A\n')
        args = argparse.Namespace(root='src', path=None, label='A', scope=None,
                                  context=0, max_hits=10, git_dir='/example', rev='a' * 40)
        encode = base64.b64encode
        with mock.patch.object(lookup, 'GitSource', return_value=source), mock.patch.object(
                lookup, 'MAX_OUTPUT', 10), mock.patch.object(
                lookup.base64, 'b64encode', wraps=encode) as encoded:
            with self.assertRaisesRegex(lookup.LookupError, 'context.*bound'):
                lookup.lookup(args, re.compile(r'^([A-Za-z_][A-Za-z0-9_]*):'))
            self.assertEqual(2, encoded.call_count, 'the third context must not be encoded')


if __name__ == '__main__':
    unittest.main()
