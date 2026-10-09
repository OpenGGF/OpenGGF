"""Selection and runner safety tests; no Maven or ROMs required."""
import json
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import compare_category_outcomes as comparison
import run_categories as runner


class CategoryPolicyTests(unittest.TestCase):
    def setUp(self):
        self.policy = runner.policy()

    def test_rom_discovery_accepts_original_files_in_an_explicit_directory(self):
        import hashlib
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / 'worktree'
            originals = Path(tmp) / 'original ROMs'
            root.mkdir()
            originals.mkdir()
            path = originals / 'Original game (REV01) [!].gen'
            path.write_bytes(b'original test image')
            digest = hashlib.sha1(path.read_bytes()).hexdigest()
            with patch.dict(runner.ROM_PROPERTIES, {digest: 'sonic2.rom.path'}, clear=True):
                self.assertEqual([f'-Dsonic2.rom.path={path.resolve()}'],
                                 runner.rom_args(root, originals))
            self.assertEqual([], list(root.iterdir()))

    def test_explicit_rom_directory_must_exist_and_be_a_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            file = root / 'not-a-directory'
            file.write_text('data')
            for path in (root / 'missing', file):
                with self.subTest(path=path), self.assertRaises(ValueError):
                    runner.rom_args(root, path)

    def test_explicit_rom_discovery_keeps_root_priority_and_ignores_unknown_images(self):
        import hashlib
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / 'worktree'
            originals = Path(tmp) / 'originals'
            root.mkdir()
            originals.mkdir()
            local = root / 'local.gen'
            local.write_bytes(b'known image')
            (originals / 'same.gen').write_bytes(local.read_bytes())
            (originals / 'unrecognized.gen').write_bytes(b'other image')
            digest = hashlib.sha1(local.read_bytes()).hexdigest()
            with patch.dict(runner.ROM_PROPERTIES, {digest: 'sonic1.rom.path'}, clear=True):
                self.assertEqual([f'-Dsonic1.rom.path={local.resolve()}'],
                                 runner.rom_args(root, originals))

    def test_explicit_rom_directory_is_passed_to_both_fresh_lanes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            originals = root / 'original ROMs'
            originals.mkdir()
            plan = {'tests': ['com/openggf/TestExample.java'], 'full': False,
                    'guards': True, 'categories': ['common'], 'inventory_count': 1}
            summary = {'reports': 1, 'tests': 1, 'skipped': 0, 'errors': 0, 'failures': 0}
            argument = '-Dsonic2.rom.path=/original ROMs/Original [!].gen'
            with patch.object(runner, 'tree_state', return_value='state'), \
                    patch.object(runner, 'rom_args', return_value=[argument]) as discover, \
                    patch.object(runner, 'summarize', side_effect=lambda _: dict(summary)), \
                    patch.object(runner, 'run_logged', return_value=0) as process:
                self.assertEqual(0, runner.run_plan(root, plan, rom_directory=originals))
            self.assertEqual(2, discover.call_count)
            for call in discover.call_args_list:
                self.assertEqual((root, originals), call.args)
            for call in process.call_args_list:
                self.assertIn(argument, call.args[0])
            self.assertEqual([], list(originals.iterdir()))

    def test_physics_changes_keep_dependent_categories_but_not_audio_oracles(self):
        categories, full, _ = runner.select_changes([
            'src/main/java/com/openggf/physics/CollisionSystem.java',
            'CHANGELOG.0.7.md',
        ], self.policy)
        self.assertFalse(full)
        self.assertTrue({'common', 'physics', 'gameplay', 'rewind', 'rendering', 'tooling'} <= categories)
        self.assertNotIn('audio', categories)

    def test_shared_inputs_and_unknown_new_subsystems_fail_broad(self):
        for path in ['pom.xml', 'src/main/java/com/openggf/GameLoop.java',
                     'src/main/java/com/openggf/data/Rom.java',
                     'src/main/java/com/openggf/configuration/SonicConfiguration.java',
                     'src/main/java/com/openggf/newSubsystem/NewThing.java',
                     'src/test/resources/new-fixture.json',
                     'src/test/java/com/openggf/tests/HeadlessTestFixture.java',
                     'tools/testing/test-categories.json']:
            with self.subTest(path=path):
                categories, full, _ = runner.select_changes([path], self.policy)
                self.assertTrue(full)
                self.assertEqual(set(self.policy['categories']), categories)

    def test_cross_category_changes_are_unioned(self):
        categories, full, reasons = runner.select_changes([
            'src/main/java/com/openggf/audio/AudioManager.java',
            'src/main/java/com/openggf/physics/CollisionSystem.java',
        ], self.policy)
        self.assertFalse(full)
        self.assertTrue({'audio', 'physics', 'rewind', 'gameplay'} <= categories)
        self.assertEqual(2, len(reasons))

    def test_legacy_and_cross_cutting_names_have_owners(self):
        self.assertIn('audio', runner.owners('com/openggf/TestGameLoopAudioPresentationModes.java', self.policy))
        self.assertIn('rendering', runner.owners('com/openggf/game/sonic3k/TestPalette.java', self.policy))
        self.assertEqual({'tooling'}, runner.owners('com/openggf/TestTraceSessionLauncherRunBranch.java', self.policy))
        self.assertEqual({'common'}, runner.owners('com/openggf/tests/TestUnknown.java', self.policy))
        self.assertEqual({'gameplay'}, runner.owners('com/openggf/tests/TestFbzCompatibilityMatrix.java', self.policy))
        self.assertEqual({'audio'}, runner.owners('com/openggf/tools/audio/parity/s2/TestS2PublishedRequestWindows.java', self.policy))

    def test_inventory_preserves_default_exclusions_and_slow_audio(self):
        inventory = runner.inventory(runner.ROOT, self.policy)
        self.assertGreater(len(inventory), 2000)
        self.assertFalse(any('/tests/trace/' in p for p in inventory))
        self.assertFalse(any('Guard' in Path(p).name for p in inventory))
        self.assertNotIn('com/openggf/tests/TestGraphicsManagerHeadless.java', inventory)
        self.assertIn('audio', inventory['com/openggf/audio/synth/nuked/TestNukedOpn2BitExactScripts.java'])
        self.assertIn('audio', inventory['com/openggf/tools/audio/parity/s2/TestS2PublishedRequestWindows.java'])
        self.assertTrue(all(labels and labels <= set(self.policy['categories']) for labels in inventory.values()))
        union = set().union(*(set(p for p, labels in inventory.items() if category in labels)
                                  for category in self.policy['categories']))
        self.assertEqual(set(inventory), union)

    def test_ant_glob_matches_direct_and_nested_trace_classes(self):
        self.assertTrue(runner.ant_match('com/openggf/tests/trace/TestFoo.java', '**/tests/trace/**/*.java'))
        self.assertTrue(runner.ant_match('com/openggf/tests/trace/s1/TestFoo.java', '**/tests/trace/**/*.java'))
        self.assertFalse(runner.ant_match('com/openggf/audio/TestFoo.java', '**/tests/trace/**/*.java'))

    def test_explicit_categories_cannot_narrow_detected_changes(self):
        with patch.object(runner, 'changed_files', return_value=('base-sha', ['pom.xml'])):
            plan = runner.make_plan(runner.ROOT, self.policy, 'base', ['physics'])
        self.assertTrue(plan['full'])
        self.assertEqual(plan['inventory_count'], len(plan['tests']))
        self.assertTrue(plan['guards'])

    def test_focused_iteration_does_not_repeat_all_guards(self):
        plan = runner.make_plan(runner.ROOT, self.policy, None, ['physics'])
        self.assertFalse(plan['guards'])
        self.assertFalse(plan['full'])
        self.assertIn('common', plan['categories'])
        with patch.object(runner, 'changed_files', return_value=('base-sha', ['src/main/java/com/openggf/physics/Movement.java'])):
            delivery = runner.make_plan(runner.ROOT, self.policy, 'base', [])
        self.assertTrue(delivery['guards'])
        self.assertTrue(runner.make_plan(runner.ROOT, self.policy, None, ['physics'], guards=True)['guards'])

    def test_default_plan_output_does_not_dump_thousands_of_class_names(self):
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            self.assertEqual(0, runner.main(['--category', 'physics']))
        compact = json.loads(output.getvalue())
        self.assertNotIn('tests', compact)
        self.assertGreater(compact['selected_classes'], 0)
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            self.assertEqual(0, runner.main(['--category', 'physics', '--json']))
        self.assertEqual(compact['selected_classes'], len(json.loads(output.getvalue())['tests']))

    def test_workers_are_opt_in_and_do_not_change_selection(self):
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            self.assertEqual(0, runner.main(['--category', 'physics', '--json']))
        serial = json.loads(output.getvalue())
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            self.assertEqual(0, runner.main(['--category', 'physics', '--workers', '2', '--json']))
        concurrent = json.loads(output.getvalue())
        self.assertEqual(1, serial.pop('workers'))
        self.assertEqual(2, concurrent.pop('workers'))
        self.assertEqual(serial, concurrent)

    def test_invalid_workers_are_rejected_before_execution(self):
        for workers in ('0', '3', '1.5', 'auto'):
            with self.subTest(workers=workers), patch('sys.stderr', new_callable=io.StringIO), patch.object(runner, 'run_plan') as run:
                with self.assertRaises(SystemExit) as error:
                    runner.main(['--category', 'physics', '--workers', workers, '--run'])
                self.assertEqual(2, error.exception.code)
                run.assert_not_called()
        with self.assertRaisesRegex(ValueError, 'workers must be 1 or 2'):
            runner.make_plan(runner.ROOT, self.policy, None, ['physics'], workers=3)

    def test_diff_includes_committed_staged_unstaged_deleted_and_untracked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def git(*args):
                return subprocess.check_output(['git', '-c', 'core.hooksPath=/dev/null', *args], cwd=root, stderr=subprocess.DEVNULL).decode().strip()
            git('init')
            git('config', 'user.email', 'test@example.invalid')
            git('config', 'user.name', 'Category test')
            for name in ['committed', 'staged', 'unstaged', 'deleted', 'renamed']:
                (root / name).write_text('original')
            git('add', '.')
            git('commit', '-m', 'baseline')
            base = git('rev-parse', 'HEAD')
            (root / 'committed').write_text('new')
            git('add', 'committed')
            git('commit', '-m', 'change')
            (root / 'staged').write_text('new')
            git('add', 'staged')
            (root / 'unstaged').write_text('new')
            (root / 'deleted').unlink()
            git('mv', 'renamed', 'new-name')
            (root / 'untracked space').write_text('new')
            actual_base, paths = runner.changed_files(root, base)
            self.assertEqual(base, actual_base)
            self.assertEqual({'committed', 'staged', 'unstaged', 'deleted', 'renamed', 'new-name', 'untracked space'}, set(paths))


class RunnerTests(unittest.TestCase):
    def test_failed_or_empty_run_stops_before_guards(self):
        for code in (0, 1):
            with self.subTest(code=code), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                plan = {'tests': ['com/openggf/TestExample.java'], 'full': False, 'guards': True, 'categories': ['common'], 'inventory_count': 1}
                with patch.object(runner, 'tree_state', return_value='state'), patch.object(runner, 'rom_args', return_value=[]), patch.object(runner, 'run_logged', return_value=code) as process:
                    self.assertEqual(1, runner.run_plan(root, plan))
                    self.assertEqual(1, process.call_count)
                    self.assertIn('-Dmse=off', process.call_args.args[0])
                    self.assertTrue(any(arg.startswith('-Dsurefire.includesFile=') for arg in process.call_args.args[0]))

    def test_guards_use_fresh_command_and_reports_and_no_category_filter(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plan = {'tests': ['com/openggf/TestExample.java'], 'full': False, 'guards': True, 'categories': ['common'], 'inventory_count': 1}
            summary = {'reports': 1, 'tests': 2, 'skipped': 0, 'errors': 0, 'failures': 0, 'skipped_cases': []}
            with patch.object(runner, 'tree_state', return_value='state'), patch.object(runner, 'rom_args', return_value=[]), patch.object(runner, 'summarize', side_effect=lambda _: dict(summary)), patch.object(runner, 'run_logged', return_value=0) as process:
                self.assertEqual(0, runner.run_plan(root, plan))
            ordinary, guards = [call.args[0] for call in process.call_args_list]
            self.assertIn('-Pguards', guards)
            self.assertFalse(any('includesFile' in arg for arg in guards))
            self.assertNotEqual([arg for arg in ordinary if 'surefire.reports' in arg], [arg for arg in guards if 'surefire.reports' in arg])

    def test_worker_profile_and_results_keep_guards_serial_for_full_and_partial_runs(self):
        for workers in (1, 2):
            for full in (False, True):
                with self.subTest(workers=workers, full=full), tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    plan = {'tests': ['com/openggf/TestExample.java'], 'full': full,
                            'guards': True, 'categories': ['common'], 'inventory_count': 1,
                            'workers': workers}
                    summary = {'reports': 1, 'tests': 2, 'skipped': 0, 'errors': 0, 'failures': 0}
                    with patch.object(runner, 'tree_state', return_value='state'), patch.object(runner, 'rom_args', return_value=['-Dsonic1.rom.path=/roms/s1.gen']), patch.object(runner, 'summarize', side_effect=lambda _: dict(summary)), patch.object(runner, 'run_logged', return_value=0) as process:
                        self.assertEqual(0, runner.run_plan(root, plan))
                    ordinary, guards = [call.args[0] for call in process.call_args_list]
                    self.assertEqual(workers == 2, '-Ptest-concurrent' in ordinary)
                    self.assertEqual(not full, any('includesFile=' in arg for arg in ordinary))
                    self.assertNotIn('-Ptest-concurrent', guards)
                    self.assertIn('-Pguards', guards)
                    self.assertFalse(any('includesFile=' in arg for arg in guards))
                    for command in (ordinary, guards):
                        self.assertIn('-Dsonic1.rom.path=/roms/s1.gen', command)
                    run = next((root / 'target/category-tests').iterdir())
                    self.assertEqual(workers, json.loads((run / 'plan.json').read_text())['workers'])
                    self.assertEqual([workers, 1], [result['workers'] for result in json.loads((run / 'results.json').read_text())])

    def test_all_skips_are_retained_with_reasons(self):
        with tempfile.TemporaryDirectory() as tmp:
            reports = Path(tmp)
            (reports / 'TEST-example.xml').write_text('<testsuite tests="2" skipped="1" failures="0" errors="0"><testcase classname="Example" name="needsRom"><skipped message="Missing ROM"/></testcase></testsuite>')
            summary = runner.summarize(reports)
            self.assertEqual(1, summary['skipped'])
            self.assertEqual('Missing ROM', summary['skipped_cases'][0]['reason'])

    def summarize_skip(self, reason, use_text=False):
        with tempfile.TemporaryDirectory() as tmp:
            reports = Path(tmp)
            suite = ET.Element('testsuite', tests='1', skipped='1', failures='0', errors='0')
            case = ET.SubElement(suite, 'testcase', classname='Example', name='needsRom')
            skipped = ET.SubElement(case, 'skipped')
            if use_text:
                skipped.text = reason
            else:
                skipped.set('message', reason)
            ET.ElementTree(suite).write(reports / 'TEST-example.xml', encoding='unicode')
            return runner.summarize(reports)['skipped_cases'][0]

    def test_long_complete_abort_stack_preserves_full_cause_before_transport_cap(self):
        cause = 'org.opentest4j.TestAbortedException: Assumption failed: act places no platforms'
        frames = ['\tat org.junit.jupiter.engine.Probe.invoke(Probe.java:1544)'] * 100
        reason = cause + '\n' + '\n'.join(frames) + '\n'
        self.assertGreater(len(reason), 4096)
        for use_text in (False, True):
            with self.subTest(use_text=use_text):
                case = self.summarize_skip(reason, use_text)
                self.assertEqual(cause, case['reason'])
                self.assertEqual('validated-junit-abort-cause', case['reason_projection'])
                self.assertEqual(len(reason), case['reason_source_chars'])
                self.assertEqual(100, case['reason_stack_frames'])
                self.assertEqual(cause, comparison.skip_cause(case['reason'], 'skip cause'))

    def test_projection_does_not_hide_unrecognized_tail_beyond_transport_cap(self):
        cause = 'org.opentest4j.TestAbortedException: Assumption failed: act places no platforms'
        stack = cause + '\n' + '\n'.join(['\tat pkg.Probe.invoke(Probe.java:1544)'] * 150)
        for tail in ('Caused by: java.io.IOException: different failure',
                     'another causal assertion', '\tat pkg.Probe.invoke(Probe.java:'):
            with self.subTest(tail=tail):
                case = self.summarize_skip(stack + '\n' + tail)
                self.assertEqual(4096, len(case['reason']))
                self.assertNotIn('reason_projection', case)
                with self.assertRaises(comparison.EvidenceError):
                    comparison.skip_cause(case['reason'], 'skip cause')

    def test_projection_requires_junit_abort_and_a_bounded_complete_causal_line(self):
        for cause in ('java.io.IOException: different failure',
                      'org.opentest4j.TestAbortedException: ' + 'x' * 4096):
            with self.subTest(cause=cause[:60]):
                reason = cause + '\n' + '\n'.join(['\tat pkg.Probe.invoke(Probe.java:1544)'] * 150)
                case = self.summarize_skip(reason)
                self.assertEqual(4096, len(case['reason']))
                self.assertNotIn('reason_projection', case)
                with self.assertRaises(comparison.EvidenceError):
                    comparison.skip_cause(case['reason'], 'skip cause')

    def test_failed_tests_still_collect_guard_results_but_never_report_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plan = {'tests': ['com/openggf/TestExample.java'], 'full': False, 'guards': True, 'categories': ['common'], 'inventory_count': 1}
            summaries = [
                {'reports': 1, 'tests': 2, 'skipped': 0, 'errors': 0, 'failures': 1},
                {'reports': 1, 'tests': 2, 'skipped': 0, 'errors': 0, 'failures': 0},
            ]
            with patch.object(runner, 'tree_state', return_value='state'), patch.object(runner, 'rom_args', return_value=[]), patch.object(runner, 'summarize', side_effect=summaries), patch.object(runner, 'run_logged', side_effect=[1, 0]) as process:
                self.assertEqual(1, runner.run_plan(root, plan))
                self.assertEqual(2, process.call_count)

    def test_interruption_cleans_owned_temp_and_preserves_other_target_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'target/test-tmp').mkdir(parents=True)
            (root / 'target/test-tmp/keep').write_text('other Maven output')
            plan = {'tests': ['com/openggf/TestExample.java'], 'full': False, 'guards': False, 'categories': ['common'], 'inventory_count': 1}
            def interrupted(command, cwd, log_path, temporary, **kwargs):
                temporary = Path(next(arg.split('=', 1)[1] for arg in command
                                      if arg.startswith('-Dopenggf.test.tmpdir=')))
                (temporary / 'large-fixture').write_text('temporary')
                raise KeyboardInterrupt
            with patch.object(runner, 'tree_state', return_value='state'), patch.object(runner, 'rom_args', return_value=[]), patch.object(runner, 'run_logged', side_effect=interrupted):
                with self.assertRaises(KeyboardInterrupt):
                    runner.run_plan(root, plan)
            self.assertFalse(list((root / 'target/category-tests').glob('*/ordinary-tmp')))
            self.assertEqual('other Maven output', (root / 'target/test-tmp/keep').read_text())

    def test_changed_tree_cannot_report_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plan = {'tests': ['com/openggf/TestExample.java'], 'full': False, 'guards': False, 'categories': ['common'], 'inventory_count': 1}
            summary = {'reports': 1, 'tests': 1, 'skipped': 0, 'errors': 0, 'failures': 0}
            with patch.object(runner, 'tree_state', side_effect=['before', 'after']), patch.object(runner, 'rom_args', return_value=[]), patch.object(runner, 'summarize', return_value=summary), patch.object(runner, 'run_logged', return_value=0):
                with self.assertRaisesRegex(ValueError, 'Working tree changed'):
                    runner.run_plan(root, plan)


if __name__ == '__main__':
    unittest.main()
