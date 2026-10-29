"""ROM-free safety and behavior tests. No Git repositories are created."""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

import disasm_storage_layout as layout


class LayoutTest(unittest.TestCase):
    def scan(self, data, **kwargs):
        data = data.encode() if isinstance(data, str) else data
        options = dict(start_line=1, end_line=data.count(b'\n') + (not data.endswith(b'\n')),
                       base=0x100, address_bits=24)
        options.update(kwargs)
        return layout.scan(data, **options)

    def test_literal_widths_and_marker_aliases(self):
        result = self.scan('mark rs.b 0\nbyte rs.b 1\nword rs.w 1\nlong rs.l 2\n rs.b 3\n')
        self.assertEqual(result['status'], 'complete')
        self.assertEqual([(r['symbol'], r['start'], r['end'], r['element_bytes'], r['count'])
                          for r in result['records']],
                         [('mark', 0x100, 0x100, 1, 0), ('byte', 0x100, 0x101, 1, 1),
                          ('word', 0x101, 0x103, 2, 1), ('long', 0x103, 0x10B, 4, 2),
                          (None, 0x10B, 0x10E, 1, 3)])
        self.assertEqual(result['same_address_symbols'], [{'address': 0x100, 'symbols': ['mark', 'byte']}])

    def test_comment_bom_crlf_and_missing_final_lf(self):
        result = self.scan(b'\xef\xbb\xbf; comment\r\n* comment\r\nx: RS.W $2 ; count\r\ny rs.l 0x1')
        self.assertEqual(result['status'], 'complete')
        self.assertEqual([(r['symbol'], r['line'], r['size_bytes']) for r in result['records']],
                         [('x', 3, 4), ('y', 4, 4)])
        self.assertEqual(result['records'][0]['source'], 'x: RS.W $2 ; count')

    def test_explicit_slice_ignores_only_outside_lines(self):
        result = self.scan('include "unknown"\nx rs.w 1\nmacro unknown\n', start_line=2, end_line=2)
        self.assertEqual(result['status'], 'complete')
        self.assertEqual(result['records'][0]['start'], 0x100)
        self.assertEqual(result['records'][0]['line'], 2)

    def test_first_unsupported_stops_and_reports_prefix(self):
        for unsupported in ['if 1', 'else', 'endif', 'rept 2', 'endr', 'include "x"',
                            'x macro', 'x ds.b 1', 'x EQU prior', 'x = 1',
                            'x rs.b 1+1', 'x rs.b Count', 'x rs.b __rs',
                            'x rs.b -1', 'x rs.b 1,2', 'x rs.q 1',
                            'x:', 'rs.b', 'rsset WORKRAM+$700', 'rsreset 1',
                            'if rs.b 1', 'rsset rs.w 1', 'x\u00a0rs.b 1',
                            '\u00a0* unknown', 'x rs.b 1\ry rs.b 1']:
            with self.subTest(unsupported=unsupported):
                result = self.scan('first rs.w 1\n' + unsupported + '\nlater rs.b 1\n')
                self.assertEqual(result['status'], 'incomplete')
                self.assertEqual(result['unsupported']['line'], 2)
                self.assertEqual(result['unsupported']['source'], unsupported)
                self.assertEqual([r['symbol'] for r in result['records']], ['first'])
                self.assertEqual(result['processed_through_line'], 1)

    def test_counter_reset_events_preserve_unmodified_values_and_basis(self):
        result = self.scan('x rs.b 1\n rsset $FFF700\ny rs.w 1\n rsreset\nz rs.l 1\n')
        self.assertEqual(result['status'], 'complete')
        self.assertEqual([(r['start'], r['basis']) for r in result['records']],
                         [(0x100, 'caller_origin'), (0xFFF700, 'literal_rsset'), (0, 'literal_rsreset')])
        self.assertEqual([(e['line'], e['value']) for e in result['counter_events']], [(2, 0xFFF700), (4, 0)])

    def test_casefold_duplicate_is_incomplete_without_advancing(self):
        result = self.scan('Name rs.w 1\nname rs.l 1\nlater rs.b 1\n')
        self.assertEqual(result['status'], 'incomplete')
        self.assertIn('duplicate', result['unsupported']['reason'])
        self.assertEqual(result['end_counter'], 0x102)
        self.assertEqual(len(result['records']), 1)

    def test_zero_count_word_remains_marker_with_declared_width(self):
        result = self.scan('alias rs.w 0\nvalue rs.l 1\n')
        marker = result['records'][0]
        self.assertEqual((marker['size_bytes'], marker['element_bytes'], marker['kind']), (0, 2, 'address_marker'))
        self.assertEqual(marker['start'], result['records'][1]['start'])

    def test_half_open_clear_relations_and_markers(self):
        result = self.scan('marker rs.b 0\nvalue rs.w 1\nnext rs.b 1\n')
        clears = [{'name': 'exact', 'start': 0x100, 'end': 0x102},
                  {'name': 'split', 'start': 0x101, 'end': 0x103},
                  {'name': 'touch', 'start': 0x103, 'end': 0x104}]
        layout.annotate_clears(result, clears)
        self.assertEqual([r['clears'] for r in result['records']],
                         [[{'name': 'exact', 'relation': 'point_inside'}, {'name': 'split', 'relation': 'point_outside'},
                           {'name': 'touch', 'relation': 'point_outside'}],
                          [{'name': 'exact', 'relation': 'contained'}, {'name': 'split', 'relation': 'overlap'},
                           {'name': 'touch', 'relation': 'disjoint'}],
                          [{'name': 'exact', 'relation': 'disjoint'}, {'name': 'split', 'relation': 'contained'},
                           {'name': 'touch', 'relation': 'disjoint'}]])

    def test_overflow_never_masks_or_wraps(self):
        for text in ['x rs.w 1\n', ' rsset $1000000\n', 'x rs.l 4294967295\n']:
            with self.subTest(text=text):
                result = self.scan(text, base=0xFFFFFF)
                self.assertEqual(result['status'], 'incomplete')
                self.assertEqual(result['unsupported']['line'], 1)
                self.assertFalse(result['records'])
        result = self.scan('x rs.b 1\nend rs.b 0\n', base=0xFFFFFF)
        self.assertEqual(result['status'], 'complete')
        self.assertEqual(result['end_counter'], 0x1000000)
        self.assertEqual(self.scan('x rs.w 1\n', base=0xFFFFFF, address_bits=32)['status'], 'complete')

    def test_record_bound_stops_at_first_excess(self):
        result = self.scan('x rs.b 1\ny rs.b 1\nz rs.b 1\n', max_records=1)
        self.assertEqual(result['status'], 'incomplete')
        self.assertEqual(result['unsupported']['line'], 2)
        self.assertIn('record', result['unsupported']['reason'])
        self.assertEqual([r['symbol'] for r in result['records']], ['x'])

    def test_partial_clear_does_not_shrink_word_or_expand_marker(self):
        result = self.scan('point rs.w 0\nword rs.w 1\n')
        layout.annotate_clears(result, [{'name': 'low-byte', 'start': 0x100, 'end': 0x101}])
        self.assertEqual(result['records'][0]['clears'][0]['relation'], 'point_inside')
        self.assertEqual(result['records'][0]['size_bytes'], 0)
        self.assertEqual(result['records'][1]['clears'][0]['relation'], 'overlap')
        self.assertEqual(result['records'][1]['size_bytes'], 2)

    def test_explicit_slice_hash_uses_exact_bytes(self):
        raw = b'outside\r\nx rs.w 1\r\ny rs.b 1\r\nignored'
        result = self.scan(raw, start_line=2, end_line=3)
        selected = b'x rs.w 1\r\ny rs.b 1\r\n'
        self.assertEqual(result['slice'], {'start_line': 2, 'end_line': 3, 'total_lines': 4,
                                          'byte_offset': 9, 'bytes': len(selected),
                                          'sha256': hashlib.sha256(selected).hexdigest()})

    def test_input_and_slice_bounds(self):
        for options in [dict(start_line=0), dict(end_line=0), dict(end_line=2),
                        dict(start_line=2, end_line=1), dict(base=-1), dict(base=0x1000000),
                        dict(address_bits=16), dict(max_records=0), dict(max_records=1025)]:
            with self.subTest(options=options), self.assertRaises(layout.LayoutError):
                self.scan('x rs.b 1\n', **options)
        with mock.patch.object(layout, 'MAX_SLICE_LINES', 1), self.assertRaises(layout.LayoutError):
            self.scan('x rs.b 1\ny rs.b 1\n')
        with mock.patch.object(layout, 'MAX_SOURCE_BYTES', 4), self.assertRaises(layout.LayoutError):
            self.scan('x rs.b 1\n')

    def test_long_line_and_controls_fail_boundedly(self):
        with mock.patch.object(layout, 'MAX_LINE_BYTES', 8):
            result = self.scan('x rs.b 1 ;long\n')
        self.assertEqual(result['status'], 'incomplete')
        self.assertEqual(result['unsupported']['line'], 1)
        self.assertIn('line', result['unsupported']['reason'])
        for data in [b'x rs.b 1\x00\n', b'x rs.b 1\x1b\n', b'x rs.b 1\xff\n', b'']:
            with self.subTest(data=data), self.assertRaises(layout.LayoutError):
                self.scan(data)


class SourceAndCommandTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='storage-layout-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.path = self.root / 'source.inc'
        self.raw = b'first rs.w 1\nmarker rs.b 0\n'
        self.path.write_bytes(self.raw)
        self.sha = hashlib.sha256(self.raw).hexdigest()
        self.git_dir = self.root / 'objects.git'
        self.git_dir.mkdir()
        self.commit = 'a' * 40
        self.blob = hashlib.sha1(b'blob ' + str(len(self.raw)).encode() + b'\0' + self.raw).hexdigest()

    def args(self, *extra):
        return ['--file', str(self.path), '--sha256', self.sha, '--start-line', '1',
                '--end-line', '2', '--base', '$FFF700', '--origin-name', 'cameraX-address',
                '--address-bits', '24', '--clear', 'camera:$FFF700:$FFF800', *extra]

    def invoke(self, args, expected=0):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = layout.main(args)
        self.assertEqual(code, expected, output.getvalue())
        return json.loads(output.getvalue())

    def test_file_hash_provenance_and_deterministic_report(self):
        report = self.invoke(self.args())
        self.assertEqual(report, self.invoke(self.args()))
        self.assertEqual(report['source']['sha256'], self.sha)
        self.assertEqual(report['source']['path'], str(self.path))
        self.assertIsNone(report['source']['blob'])
        self.assertEqual(report['origin'], {'name': 'cameraX-address', 'base': 0xFFF700, 'address_bits': 24})
        self.assertEqual(report['slice']['sha256'], self.sha)
        self.assertEqual(report['slice']['byte_offset'], 0)

    def test_hash_mismatch_and_symlink_rejected(self):
        self.invoke(self.args('--sha256', '0' * 64), expected=2)
        link = self.root / 'link.inc'
        link.symlink_to(self.path)
        self.invoke(self.args('--file', str(link)), expected=2)
        self.invoke(self.args('--file', 'relative.inc'), expected=2)
        directory_link = self.root / 'linked'
        directory_link.symlink_to(self.root, target_is_directory=True)
        self.invoke(self.args('--file', str(directory_link / self.path.name)), expected=2)

    def test_nonregular_file_and_invalid_file_pin_rejected(self):
        for pin in ['', '1' * 40, 'A' * 64, 'HEAD']:
            with self.subTest(pin=pin):
                self.invoke(self.args('--sha256', pin), expected=2)
        self.invoke(self.args('--file', str(self.root)), expected=2)
        fifo = self.root / 'fifo'
        os.mkfifo(fifo)
        self.invoke(self.args('--file', str(fifo)), expected=2)

    def test_source_mode_mixing_and_interval_count_are_errors(self):
        self.invoke(self.args('--rev', self.commit), expected=2)
        for values in [[], ['x:0:1'] * 17]:
            with self.subTest(values=values), self.assertRaises(layout.LayoutError):
                layout.clear_intervals(values, 24)
        for value in ['relative', str(self.git_dir) + '/..', str(self.root / 'absent')]:
            with self.subTest(value=value), self.assertRaises(layout.LayoutError):
                layout.GitSource(value)
        link = self.root / 'git-link'
        link.symlink_to(self.git_dir, target_is_directory=True)
        with self.assertRaises(layout.LayoutError):
            layout.GitSource(str(link))

    def test_file_and_report_size_bounds(self):
        with mock.patch.object(layout, 'MAX_SOURCE_BYTES', 4):
            self.invoke(self.args(), expected=2)
        with mock.patch.object(layout, 'MAX_OUTPUT_BYTES', 128):
            report = self.invoke(self.args(), expected=2)
        self.assertNotIn('records', report)

    def test_cli_incomplete_has_prefix_and_exit_one(self):
        self.path.write_bytes(b'first rs.w 1\nif FLAG\n')
        self.sha = hashlib.sha256(self.path.read_bytes()).hexdigest()
        report = self.invoke(self.args(), expected=1)
        self.assertEqual(report['unsupported']['line'], 2)
        self.assertEqual(len(report['records']), 1)

    def test_invalid_interval_duplicate_name_and_unknown_argument(self):
        for arg in ['x:0:0', 'x:2:1', 'x:-1:2', 'x:0:$1000001', 'bad name:0:1',
                    'camera:0:1', 'x:1+1:3']:
            with self.subTest(arg=arg):
                self.invoke(self.args('--clear', arg), expected=2)
        self.invoke(self.args('--unexpected'), expected=2)

    def fake_git(self, *args, **kwargs):
        if args == ('cat-file', '-t', self.commit):
            return b'commit\n'
        if args == ('ls-tree', '-z', self.commit, '--', 'src/x.inc'):
            return f'100644 blob {self.blob}\tsrc/x.inc\0'.encode()
        if args == ('cat-file', '-t', self.blob):
            return b'blob\n'
        if args == ('cat-file', '-s', self.blob):
            return str(len(self.raw)).encode() + b'\n'
        if args == ('cat-file', 'blob', self.blob):
            return self.raw
        self.fail(f'Unexpected Git read: {args!r}')

    def test_full_commit_path_or_explicit_blob_source(self):
        with mock.patch.object(layout.GitSource, 'run', side_effect=self.fake_git):
            provenance, raw = layout.load_git(str(self.git_dir), revision=self.commit, path='src/x.inc')
            self.assertEqual(raw, self.raw)
            self.assertEqual((provenance['revision'], provenance['blob'], provenance['path']),
                             (self.commit, self.blob, 'src/x.inc'))
            blob_source, raw = layout.load_git(str(self.git_dir), blob=self.blob)
            self.assertEqual(raw, self.raw)
            self.assertIsNone(blob_source['path'])
            self.assertIsNone(blob_source['revision'])

    def test_sha256_blob_identity_and_corrupted_blob_are_checked(self):
        self.blob = hashlib.sha256(b'blob ' + str(len(self.raw)).encode() + b'\0' + self.raw).hexdigest()
        with mock.patch.object(layout.GitSource, 'run', side_effect=self.fake_git):
            provenance, raw = layout.load_git(str(self.git_dir), blob=self.blob)
            self.assertEqual(provenance['blob'], self.blob)
            self.assertEqual(raw, self.raw)
        def corrupt(*args, **kwargs):
            if args == ('cat-file', 'blob', self.blob):
                return b'F' + self.raw[1:]
            return self.fake_git(*args, **kwargs)
        with mock.patch.object(layout.GitSource, 'run', side_effect=corrupt), self.assertRaisesRegex(layout.LayoutError, 'object ID'):
            layout.load_git(str(self.git_dir), blob=self.blob)

    def test_bad_revision_path_and_tree_modes_rejected(self):
        for rev in ['HEAD', self.commit[:12], self.commit + '^', self.commit.upper(), '--all']:
            with self.subTest(rev=rev), self.assertRaises(layout.LayoutError):
                layout.load_git(str(self.git_dir), revision=rev, path='src/x.inc')
        for path in ['../x', '/x', 'src//x', 'src/./x', 'src/../x', 'src/']:
            with self.subTest(path=path), self.assertRaises(layout.LayoutError):
                layout.load_git(str(self.git_dir), revision=self.commit, path=path)
        for mode, kind in [('120000', 'blob'), ('160000', 'commit'), ('040000', 'tree')]:
            def fake(*args, **kwargs):
                if args[0] == 'ls-tree':
                    return f'{mode} {kind} {self.blob}\tsrc/x.inc\0'.encode()
                return self.fake_git(*args, **kwargs)
            with self.subTest(mode=mode), mock.patch.object(layout.GitSource, 'run', side_effect=fake), \
                    self.assertRaises(layout.LayoutError):
                layout.load_git(str(self.git_dir), revision=self.commit, path='src/x.inc')

    def test_wrong_object_type_git_size_and_blob_mismatch(self):
        with mock.patch.object(layout.GitSource, 'run', return_value=b'tree\n'), self.assertRaises(layout.LayoutError):
            layout.load_git(str(self.git_dir), revision=self.commit, path='src/x.inc')
        with mock.patch.object(layout.GitSource, 'run', side_effect=self.fake_git), \
                mock.patch.object(layout, 'MAX_SOURCE_BYTES', 4), self.assertRaises(layout.LayoutError):
            layout.load_git(str(self.git_dir), blob=self.blob)
        def wrong(*args, **kwargs):
            if args == ('ls-tree', '-z', self.commit, '--', 'src/x.inc'):
                return f'100644 blob {self.blob}\tsrc/other.inc\0'.encode()
            return self.fake_git(*args, **kwargs)
        with mock.patch.object(layout.GitSource, 'run', side_effect=wrong), self.assertRaises(layout.LayoutError):
            layout.load_git(str(self.git_dir), revision=self.commit, path='src/x.inc')

    def test_git_environment_and_argv_are_read_only_and_isolated(self):
        with mock.patch.dict(os.environ, {'GIT_DIR': '/unrelated', 'GIT_CONFIG_COUNT': '9',
                                         'GIT_INDEX_FILE': '/unrelated/index'}):
            source = layout.GitSource(str(self.git_dir))
        self.assertNotIn('GIT_DIR', source.env)
        self.assertNotIn('GIT_CONFIG_COUNT', source.env)
        self.assertNotIn('GIT_INDEX_FILE', source.env)
        self.assertEqual(source.env['GIT_NO_LAZY_FETCH'], '1')
        self.assertIn('--no-replace-objects', source.prefix)
        self.assertIn('--literal-pathspecs', source.prefix)

    def test_git_subprocess_bound_timeout_and_failure(self):
        source = layout.GitSource(str(self.git_dir))
        def fake_run(argv, **kwargs):
            kwargs['stdout'].write(b'12345')
            return subprocess.CompletedProcess(argv, 0)
        with mock.patch.object(layout.subprocess, 'run', side_effect=fake_run), self.assertRaises(layout.LayoutError):
            source.run('cat-file', '-t', self.commit, limit=4)
        for effect in [OSError('not found'), subprocess.TimeoutExpired('git', 30)]:
            with self.subTest(effect=effect), mock.patch.object(layout.subprocess, 'run', side_effect=effect), \
                    self.assertRaises(layout.LayoutError):
                source.run('cat-file', '-t', self.commit)
        def failed(argv, **kwargs):
            return subprocess.CompletedProcess(argv, 128)
        with mock.patch.object(layout.subprocess, 'run', side_effect=failed), self.assertRaises(layout.LayoutError):
            source.run('cat-file', '-t', self.commit)


if __name__ == '__main__':
    unittest.main()
