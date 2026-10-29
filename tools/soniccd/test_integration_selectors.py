"""Read-only source-selector controls; Java is fixture text, never executed."""
import hashlib
import importlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

import integration_packet as packet

SCRIPT = Path(__file__).with_name('integration_packet.py')


class SelectorFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='selectors-fixture-')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / 'tree'
        self.root.mkdir()
        self.git('init', '-q')
        (self.root / 'src').mkdir()
        self.anchor = self.root / 'src/Anchor.java'
        self.anchor.write_bytes(b'class Anchor {}\n')
        self.git('add', '.')
        self.git('-c', 'user.name=Selector Test', '-c', 'user.email=test@example.invalid',
                 'commit', '-qm', 'Synthetic selector fixture\n\n'
                 'Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>')
        self.head = self.git('rev-parse', 'HEAD').strip()
        self.final = self.base / 'Final.java'
        self.final.write_bytes(self.anchor.read_bytes())
        self.final.chmod(0o444)
        self.spec = self.base / 'handoff.json'
        data = self.final.read_bytes()
        self.value = dict(version=1, id='selector-fixture', packet=dict(version=1,
            worktree_head=self.head, files=[dict(source=str(self.final),
                sha256=hashlib.sha256(data).hexdigest(), bytes=len(data), mode='0444',
                utf8=True, lf=True, destination='src/Anchor.java',
                expected={'sha256': hashlib.sha256(data).hexdigest()})]),
            dependencies=[], api_changes=[], known_defects=[], tests=dict(
                classes=['p.Plain'], properties={}, expected_tests={'p.Plain': 1}, java_major=25))
        self.source = self.write('src/Plain.java', 'package p; import org.junit.jupiter.api.Test; '
                                 'class Plain { @Test void checks() {} }\n')
        self.sources = ['p.Plain=src/Plain.java']
        self.save()

    def git(self, *args):
        env = dict(PATH=os.defpath, HOME='/nonexistent', XDG_CONFIG_HOME='/nonexistent',
                   GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
                   GIT_CONFIG_SYSTEM=os.devnull)
        return subprocess.check_output(['git', *args], cwd=self.root, text=True,
                                       stderr=subprocess.DEVNULL, env=env)

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def save(self):
        self.spec.write_text(json.dumps(self.value) + '\n')

    def module(self):
        try:
            module = importlib.import_module('integration_selectors')
        except ModuleNotFoundError:
            self.fail('Missing read-only source-selector preflight module')
        self.assertTrue(callable(getattr(module, 'selector_summary', None)),
                        'Missing selector_summary API')
        return module

    def invoke(self, **kwargs):
        return self.module().selector_summary(self.root, self.spec,
                                              kwargs.pop('sources', self.sources), **kwargs)

    def cli(self, *extra):
        args = [sys.executable, '-B', str(SCRIPT), 'selectors', '--worktree', str(self.root),
                '--spec', str(self.spec)]
        for source in self.sources:
            args += ['--source', source]
        return subprocess.run(args + list(extra), capture_output=True, text=True)

    def snapshot(self):
        paths = [self.anchor, self.final, self.spec, self.source, self.root / '.git/index']
        return self.git('rev-parse', 'HEAD'), {str(p): (p.read_bytes(), p.stat().st_mode,
                p.stat().st_mtime_ns) for p in paths if p.exists()}


class SelectorTests(SelectorFixture):
    def test_one_explicit_owner_and_ordinary_count_are_read_only(self):
        before = self.snapshot()
        report = self.invoke()
        self.assertEqual(report['exit_code'], 0, report)
        self.assertTrue(report['ready'])
        self.assertEqual(report['status'], 'source_owners_observed')
        row = report['selectors'][0]
        self.assertEqual(row['status'], 'owner_matched')
        self.assertEqual(row['class'], 'p.Plain')
        self.assertEqual(row['declaration']['kind'], 'class')
        self.assertEqual(row['source']['sha256'], hashlib.sha256(self.source.read_bytes()).hexdigest())
        self.assertEqual(row['test_inventory']['proposed_count'], 1)
        self.assertTrue(row['expected_count_check']['matches'])
        self.assertFalse(report['coverage']['complete_test_proof'])
        self.assertTrue(report['coverage']['stable_named_sources'])
        self.assertEqual(self.snapshot(), before)

    def test_explicit_source_can_have_an_unrelated_filename(self):
        odd = self.write('selected/odd-name.java', self.source.read_text())
        self.sources = ['p.Plain=selected/odd-name.java']
        report = self.invoke()
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['selectors'][0]['source']['path'], str(odd))

    def test_both_realistic_misnamed_classes_are_reported_before_any_execution(self):
        names = ['p.TestSonicCdPastFadeWorld', 'p.TestSonicCdPastFirstServiceWorld']
        self.value['tests'].update(classes=names, expected_tests=dict(zip(names, [2, 3])))
        self.sources = [name + '=src/' + name.rsplit('.', 1)[1] + '.java' for name in names]
        self.save()
        report = self.invoke()
        self.assertEqual(report['exit_code'], 1, report)
        self.assertEqual([r['status'] for r in report['selectors']], ['source_missing'] * 2)
        self.assertEqual([r['class'] for r in report['selectors']], names)
        self.assertFalse(report['ready'])

    def test_existing_file_with_wrong_package_or_owner_is_not_accepted(self):
        for text in ('package other; class Plain {}', 'package p; class Actual {}'):
            with self.subTest(text=text):
                self.source.write_text(text)
                report = self.invoke()
                self.assertEqual(report['exit_code'], 1, report)
                self.assertEqual(report['selectors'][0]['status'], 'owner_not_observed')

    def test_duplicate_lexical_owner_is_explicitly_incomplete(self):
        self.source.write_text('package p; class Plain {} class Plain {}')
        report = self.invoke()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(report['selectors'][0]['status'], 'source_incomplete')
        self.assertTrue(report['selectors'][0]['reason'])

    def test_spec_budget_is_checked_before_any_spec_snapshot(self):
        self.spec.write_bytes(b' ' * (4 * 1024 * 1024 + 1))
        with mock.patch.object(packet, 'snapshot', side_effect=AssertionError('Spec read before its budget guard')):
            with self.assertRaises(packet.PacketError):
                self.invoke()

    def test_isolated_and_member_types_do_not_require_complete_junit_inventory(self):
        self.source.write_text('package p; import org.junit.jupiter.api.Test; '
            'import org.junit.jupiter.api.parallel.Isolated; @Isolated class Plain { '
            'record Detail(int n) {} @Test void a() {} @Test void b() {} }')
        self.value['tests']['expected_tests']['p.Plain'] = 2
        self.save()
        report = self.invoke()
        self.assertEqual(report['exit_code'], 0, report)
        row = report['selectors'][0]
        self.assertEqual(row['status'], 'owner_matched')
        self.assertFalse(row['test_inventory']['complete'])
        self.assertIsNone(row['test_inventory']['proposed_count'])
        self.assertIsNone(row['expected_count_check']['matches'])
        self.assertFalse(row['expected_count_check']['applicable'])

    def test_inheritance_and_abstract_headers_remain_owner_observations_only(self):
        for header in ('class Plain extends Base', 'abstract class Plain'):
            with self.subTest(header=header):
                self.source.write_text('package p; ' + header + ' {}')
                report = self.invoke()
                self.assertEqual(report['exit_code'], 0, report)
                self.assertEqual(report['selectors'][0]['status'], 'owner_matched')
                self.assertFalse(report['selectors'][0]['test_inventory']['complete'])

    def test_parameterized_inventory_is_unknown_not_guessed_or_rejected(self):
        self.source.write_text('package p; import org.junit.jupiter.params.ParameterizedTest; '
            'class Plain { @ParameterizedTest void a(int n) {} }')
        report = self.invoke()
        self.assertEqual(report['exit_code'], 0, report)
        self.assertIsNone(report['selectors'][0]['test_inventory']['proposed_count'])

    def test_member_lexical_owner_needs_an_explicit_source_and_no_binary_name_rewrite(self):
        self.source.write_text('package p; class Outer { class Inner {} }')
        self.value['tests'].update(classes=['p.Outer.Inner'], expected_tests={'p.Outer.Inner': 1})
        self.save()
        report = self.invoke(sources=['p.Outer.Inner=src/Plain.java'])
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['selectors'][0]['declaration']['owner'], 'p.Outer')
        self.value['tests'].update(classes=['p.Outer$Inner'], expected_tests={'p.Outer$Inner': 1})
        self.save()
        report = self.invoke(sources=['p.Outer$Inner=src/Plain.java'])
        self.assertEqual(report['exit_code'], 1, report)
        self.assertEqual(report['selectors'][0]['status'], 'owner_not_observed')

    def test_complete_whole_class_count_mismatch_is_review_not_a_rewrite(self):
        self.value['tests']['expected_tests']['p.Plain'] = 2
        self.save()
        before = self.spec.read_bytes()
        report = self.invoke()
        self.assertEqual(report['exit_code'], 1, report)
        self.assertFalse(report['selectors'][0]['expected_count_check']['matches'])
        self.assertEqual(self.spec.read_bytes(), before)

    def test_method_selection_never_compares_subset_count_to_whole_class(self):
        self.source.write_text('package p; import org.junit.jupiter.api.Test; '
                              'class Plain { @Test void a() {} @Test void b() {} }')
        self.value['tests'].update(methods={'p.Plain': ['a']}, expected_cases={'p.Plain': ['p.Plain#a()']})
        self.save()
        report = self.invoke()
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['selectors'][0]['method_selection'], ['a'])
        self.assertFalse(report['selectors'][0]['expected_count_check']['applicable'])

    def test_method_names_do_not_gain_inherited_or_invocation_authority(self):
        self.value['tests'].update(methods={'p.Plain': ['inherited']},
                                  expected_cases={'p.Plain': ['p.Plain#inherited()']})
        self.save()
        report = self.invoke()
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['selectors'][0]['method_selection'], ['inherited'])
        self.assertFalse(report['coverage']['method_resolution_proved'])

    def test_maps_require_exact_unique_class_inventory(self):
        for sources in ([], ['p.Plain=src/Plain.java'] * 2, ['p.Other=src/Plain.java'],
                        ['p.Plain=src/Plain.java', 'p.Other=src/Plain.java']):
            with self.subTest(sources=sources):
                with self.assertRaises(packet.PacketError):
                    self.invoke(sources=sources)

    def test_maps_require_bounded_plain_exact_mapping_strings(self):
        for value in ('p.Plain', '=src/Plain.java', 'p.Plain=', 42, 'p.Plain=src/Plain.java\n'):
            with self.subTest(value=value):
                with self.assertRaises(packet.PacketError):
                    self.invoke(sources=[value])

    def test_source_paths_reject_traversal_absolute_metadata_alias_and_wrong_suffix(self):
        for relative in ('../Final.java', '/tmp/Plain.java', 'src/../src/Plain.java',
                         '.git/Plain.java', 'src\\Plain.java', 'src/Plain.txt'):
            with self.subTest(relative=relative):
                with self.assertRaises(packet.PacketError):
                    self.invoke(sources=['p.Plain=' + relative])

    def test_source_symlink_and_symlink_parent_are_rejected(self):
        self.source.unlink()
        self.source.symlink_to(self.final)
        with self.assertRaises(packet.PacketError):
            self.invoke()
        self.source.unlink()
        link = self.root / 'linked'
        link.symlink_to(self.base, target_is_directory=True)
        with self.assertRaises(packet.PacketError):
            self.invoke(sources=['p.Plain=linked/Final.java'])

    def test_distinct_hardlink_source_names_are_rejected(self):
        second = self.root / 'src/Alias.java'
        os.link(self.source, second)
        self.value['tests'].update(classes=['p.Plain', 'p.Other'],
                                  expected_tests={'p.Plain': 1, 'p.Other': 1})
        self.save()
        with self.assertRaises(packet.PacketError):
            self.invoke(sources=self.sources + ['p.Other=src/Alias.java'])

    def test_exact_same_source_for_distinct_lexical_types_is_observed_once(self):
        self.source.write_text('package p; class Plain {} class Other {}')
        self.value['tests'].update(classes=['p.Plain', 'p.Other'],
                                  expected_tests={'p.Plain': 1, 'p.Other': 1})
        self.save()
        report = self.invoke(sources=self.sources + ['p.Other=src/Plain.java'])
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['source_count'], 1)

    def test_large_source_is_explicitly_incomplete_without_relaxing_census_limit(self):
        self.source.write_bytes(b' ' * (1024 * 1024 + 1))
        report = self.invoke()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(report['selectors'][0]['status'], 'source_incomplete')
        self.assertIsNone(report['selectors'][0]['source']['sha256'])

    def test_aggregate_budget_is_incomplete_and_keeps_requested_inventory(self):
        with mock.patch.object(self.module(), 'MAX_SELECTED_BYTES', 4):
            report = self.invoke()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual([r['class'] for r in report['selectors']], ['p.Plain'])

    def test_existing_token_budget_is_not_relaxed_for_a_small_byte_source(self):
        self.source.write_text('package p; class Plain {' + 'int field;' * 34000 + '}')
        report = self.invoke()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(report['selectors'][0]['status'], 'source_incomplete')
        self.assertIn('budget', report['selectors'][0]['reason'].lower())

    def test_unrecognized_source_grammar_is_incomplete_not_absent(self):
        self.source.write_text('package p; class Plain { void a() { class Local {} } }')
        report = self.invoke()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(report['selectors'][0]['status'], 'source_incomplete')

    def changed_at_recheck(self, action):
        module = self.module()
        original = module._source_unchanged
        attacked = False
        def changed(saved, *args, **kwargs):
            nonlocal attacked
            if saved.path == self.source and not attacked:
                attacked = True
                action()
            return original(saved, *args, **kwargs)
        with mock.patch.object(module, '_source_unchanged', changed):
            result = self.invoke()
        self.assertTrue(attacked)
        self.assertEqual(result['exit_code'], 3, result)
        self.assertFalse(result['ready'])
        return result

    def test_source_content_change_is_incomplete(self):
        self.changed_at_recheck(lambda: self.source.write_text('package p; class Changed {}'))

    def test_source_disappearance_is_incomplete(self):
        self.changed_at_recheck(self.source.unlink)

    def test_source_mode_change_is_incomplete(self):
        self.changed_at_recheck(lambda: self.source.chmod(0o400))

    def test_missing_source_appearance_is_incomplete(self):
        self.source.unlink()
        self.changed_at_recheck(lambda: self.source.write_text('package p; class Plain {}'))

    def test_replaced_source_ancestor_is_incomplete_even_with_same_source_inode(self):
        nested = self.write('src/kept/Plain.java', self.source.read_text())
        self.source.unlink()
        self.source = nested
        self.sources = ['p.Plain=src/kept/Plain.java']
        def replace_parent():
            old = self.root / 'old-src'
            (self.root / 'src').rename(old)
            (self.root / 'src').mkdir()
            (old / 'kept').rename(self.root / 'src/kept')
            (old / 'Anchor.java').rename(self.anchor)
        self.changed_at_recheck(replace_parent)

    def test_spec_change_is_incomplete(self):
        self.changed_at_recheck(lambda: self.spec.write_text('{}\n'))

    def test_head_change_is_incomplete(self):
        self.changed_at_recheck(lambda: self.git('-c', 'user.name=Selector Test',
            '-c', 'user.email=test@example.invalid', 'commit', '--allow-empty', '-qm',
            'Synthetic changed HEAD\n\nCo-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>'))

    def test_blocked_handoff_is_not_overridden_by_a_matching_class(self):
        self.final.chmod(0o644)
        report = self.invoke()
        self.assertEqual(report['exit_code'], 1, report)
        self.assertEqual(report['selectors'][0]['status'], 'owner_matched')
        self.assertFalse(report['handoff']['ready'])

    def test_installed_option_retains_the_existing_handoff_stage(self):
        report = self.invoke(installed=True)
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['handoff']['stage'], 'installed')
        with self.assertRaises(packet.PacketError):
            self.invoke(installed=1)

    def test_cli_json_help_and_no_mutating_options(self):
        before = self.snapshot()
        result = self.cli('--installed')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)['exit_code'], 0)
        self.assertEqual(self.snapshot(), before)
        for option in ('--apply', '--execute', '--maven-terminal', '--output', '--inst'):
            with self.subTest(option=option):
                result = self.cli(option)
                self.assertEqual(result.returncode, 2, result.stderr)
        help_result = subprocess.run([sys.executable, '-B', str(SCRIPT), 'selectors', '--help'],
                                     capture_output=True, text=True)
        self.assertEqual(help_result.returncode, 0)
        self.assertIn('--source', help_result.stdout)

    def test_preflight_never_runs_or_imports_the_runner_or_writes_reports(self):
        module = self.module()
        before = self.snapshot()
        with (mock.patch.object(packet, 'apply_packet', side_effect=AssertionError('apply')),
              mock.patch.object(packet, 'worktree_build_slot', side_effect=AssertionError('lock'))):
            report = self.invoke()
        self.assertEqual(report['exit_code'], 0)
        self.assertNotIn('integration_run', sys.modules)
        self.assertEqual(self.snapshot(), before)
        self.assertFalse((self.root / 'target').exists())


class SelectorReviewRegressionTests(SelectorFixture):
    def test_safe_missing_parent_reports_missing_source_at_cli(self):
        self.sources = ['p.Plain=uncreated/sub/Plain.java']
        result = self.cli()
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertEqual(json.loads(result.stdout)['selectors'][0]['status'], 'source_missing')
        self.assertFalse((self.root / 'uncreated').exists())

    def test_early_real_spec_byte_change_is_incomplete_at_cli(self):
        import contextlib
        import io
        module = self.module()
        original = module.handoff_summary
        attacked = False
        def change(*args, **kwargs):
            nonlocal attacked
            result = original(*args, **kwargs)
            if not attacked:
                attacked = True
                self.spec.write_bytes(self.spec.read_bytes() + b'\n')
            return result
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(module, 'handoff_summary', change), contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = packet.main(['selectors', '--worktree', str(self.root), '--spec', str(self.spec), '--source', self.sources[0]])
        self.assertTrue(attacked)
        self.assertEqual(code, 3, err.getvalue())
        self.assertEqual(json.loads(out.getvalue())['status'], 'incomplete')

    def test_growth_after_initial_lstat_is_metadata_only_with_no_hash(self):
        original = Path.lstat
        attacked = False
        def grow(path, *args, **kwargs):
            nonlocal attacked
            info = original(path, *args, **kwargs)
            if path == self.source and not attacked:
                attacked = True
                self.source.write_bytes(b' ' * (1024 * 1024 + 1))
            return info
        with mock.patch.object(Path, 'lstat', grow):
            report = self.invoke()
        self.assertTrue(attacked)
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(report['selectors'][0]['status'], 'source_incomplete')
        self.assertIsNone(report['selectors'][0]['source']['sha256'])
        self.assertEqual(report['selected_bytes_read'], 0)

    def test_source_growth_before_descriptor_check_does_not_escape_as_unsafe(self):
        original = os.open
        attacked = False
        def grow(path, flags, *args, **kwargs):
            nonlocal attacked
            fd = original(path, flags, *args, **kwargs)
            if Path(path) == self.source and not attacked:
                attacked = True
                self.source.write_bytes(b' ' * (1024 * 1024 + 1))
            return fd
        with mock.patch.object(os, 'open', grow):
            report = self.invoke()
        self.assertTrue(attacked)
        self.assertEqual(report['exit_code'], 3, report)
        self.assertIsNone(report['selectors'][0]['source']['sha256'])


    def source_reads(self, action=None):
        """Observe descriptor read requests, not a substitute file reader."""
        descriptors, requests = set(), []
        original_open, original_fdopen = os.open, os.fdopen
        fixture = self
        class ObservedStream:
            def __init__(self, stream): self.stream = stream
            def __enter__(self): return self
            def __exit__(self, *args): return self.stream.__exit__(*args)
            def fileno(self): return self.stream.fileno()
            def read(self, size=-1):
                fixture.assertGreaterEqual(size, 0)
                fixture.assertLessEqual(size, 1024 * 1024)
                requests.append(size)
                if action is not None: action()
                return self.stream.read(size)
        def opened(path, *args, **kwargs):
            fd = original_open(path, *args, **kwargs)
            if Path(path) == self.source: descriptors.add(fd)
            return fd
        def streamed(fd, *args, **kwargs):
            stream = original_fdopen(fd, *args, **kwargs)
            selected = fd in descriptors
            descriptors.discard(fd)
            return ObservedStream(stream) if selected else stream
        return mock.patch.object(os, 'open', opened), mock.patch.object(os, 'fdopen', streamed), requests

    def test_growth_before_final_recheck_is_not_read_again(self):
        module = self.module()
        original = module._source_unchanged
        before_bytes = len(self.source.read_bytes())
        def change(saved, *args, **kwargs):
            if saved.path == self.source: self.source.write_bytes(b' ' * (1024 * 1024 + 1))
            return original(saved, *args, **kwargs)
        opened, streamed, requests = self.source_reads()
        with opened, streamed, mock.patch.object(module, '_source_unchanged', change):
            report = self.invoke()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(requests, [before_bytes])
        self.assertFalse(report['coverage']['stable_named_sources'])

    def test_growth_during_descriptor_read_stays_bounded_and_unhashed(self):
        before_bytes = len(self.source.read_bytes())
        opened, streamed, requests = self.source_reads(lambda: self.source.write_bytes(b' ' * (1024 * 1024 + 1)))
        with opened, streamed: report = self.invoke()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(requests, [before_bytes])
        self.assertIsNone(report['selectors'][0]['source']['sha256'])
        self.assertFalse(report['coverage']['stable_named_sources'])

    def test_changed_spec_that_breaks_initial_handoff_is_incomplete(self):
        module = self.module()
        original = module.handoff_summary
        def change(*args, **kwargs):
            self.spec.write_text('{}\n')
            return original(*args, **kwargs)
        with mock.patch.object(module, 'handoff_summary', change): report = self.invoke()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(report['status'], 'incomplete')
        self.assertFalse(report['ready'])

    def test_initially_invalid_unchanged_spec_remains_invalid_exit_two(self):
        self.spec.write_text('{}\n')
        result = self.cli()
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertFalse(result.stdout)


if __name__ == '__main__':
    unittest.main()
