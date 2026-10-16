"""Prepare once without test admission, then execute the unchanged class batches."""
from contextlib import contextmanager
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import run_categories as runner


class PreparationTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.plan = dict(tests=[f'com/openggf/Test{i:03d}.java' for i in range(101)],
                         full=True, guards=True, categories=['common'], inventory_count=101,
                         workers=2)
        self.artifact = self.root / 'target/test-classes/Compiled.class'
        self.clock = 0
        self.state = 'unchanged'
        self.active = False
        self.admissions = []
        self.commands = []
        self.preparation_error = None
        self.preparation_code = 0
        self.preparation_wait = 0
        self.change_during_preparation = False
        for mock in (patch.object(runner, 'tree_state', side_effect=lambda _: self.state),
                     patch.object(runner, 'rom_args', return_value=['-Dsonic1.rom.path=/roms/s1.gen']),
                     patch.object(runner, 'run_logged', side_effect=self.maven),
                     patch.object(runner.time, 'monotonic', side_effect=lambda: self.clock),
                     patch('sys.stdout', new_callable=io.StringIO)):
            mock.start()
            self.addCleanup(mock.stop)

    @contextmanager
    def admission(self, plan, lane):
        self.admissions.append(lane)
        self.clock += 1000
        self.active = True
        try:
            yield 27
        finally:
            self.active = False

    def maven(self, command, cwd, log, temporary, **kwargs):
        self.commands.append(command)
        self.assertEqual(self.root, cwd)
        self.assertTrue(temporary.is_dir())
        if 'test-compile' in command:
            self.assertEqual(['mvn', '-Dmse=off', 'test-compile', '-B'], command)
            self.assertFalse(self.active)
            self.assertEqual(12, kwargs['queue_fd'])
            self.clock += 8 + self.preparation_wait
            kwargs['timings']['build_wait_seconds'] = self.preparation_wait
            log.write_text('preparation diagnostic\n')
            if self.preparation_error:
                raise self.preparation_error
            if self.change_during_preparation:
                self.state = 'edited'
            if not self.preparation_code:
                self.artifact.parent.mkdir(parents=True)
                self.artifact.write_bytes(b'compiled exactly once')
            return self.preparation_code
        self.assertTrue(self.active)
        self.assertEqual(27, kwargs['queue_fd'])
        self.assertIn('surefire:test', command)
        self.assertNotIn('test', command)
        self.assertIn('-Dsonic1.rom.path=/roms/s1.gen', command)
        self.assertEqual(b'compiled exactly once', self.artifact.read_bytes())
        for fork in (1, 2):
            self.assertTrue((temporary / f'fork-{fork}').is_dir())
        reports = Path(next(arg.split('=', 1)[1] for arg in command
                            if arg.startswith('-Dopenggf.surefire.reports=')))
        reports.mkdir()
        (reports / 'TEST-example.xml').write_text(
            '<testsuite tests="1" failures="0" errors="0" skipped="0"/>')
        self.clock += 3
        return 0

    def execute(self, **kwargs):
        return runner.run_plan(self.root, self.plan, admission=self.admission,
                               tree_fd=12, **kwargs)

    def diagnostics(self):
        return next((self.root / 'target/category-tests').iterdir())

    def summary(self, filename):
        return json.loads((self.diagnostics() / filename).read_text())

    def assert_no_test_launch(self):
        self.assertEqual([], self.admissions)
        self.assertEqual(1, len(self.commands))
        self.assertEqual([], self.summary('results.json'))
        self.assertEqual('incomplete', self.summary('status.json')['status'])
        self.assertEqual('preparation diagnostic\n',
                         (self.diagnostics() / 'preparation.log').read_text())
        self.assertFalse(list(self.diagnostics().glob('*-tmp')))

    def test_one_preparation_supplies_every_batch_and_guards_without_admission(self):
        self.assertEqual(0, self.execute())
        self.assertEqual(['ordinary', 'ordinary', 'ordinary', 'guards'], self.admissions)
        self.assertEqual(1, sum('test-compile' in command for command in self.commands))
        self.assertEqual(4, sum('surefire:test' in command for command in self.commands))
        self.assertEqual(8, self.summary('preparation.json')['elapsed_seconds'])
        self.assertEqual('passed', self.summary('preparation.json')['status'])
        self.assertEqual([9, 3], [lane['elapsed_seconds'] for lane in self.summary('results.json')])
        self.assertEqual([3, 1], [lane['tests'] for lane in self.summary('results.json')])
        self.assertEqual('passed', self.summary('status.json')['status'])
        self.assertFalse((self.diagnostics() / 'preparation.log').exists())

    def test_waiting_for_measurement_does_not_consume_invocation_budget(self):
        self.preparation_wait = 3600
        self.assertEqual(0, self.execute(max_minutes=1))
        self.assertEqual(8, self.summary('preparation.json')['elapsed_seconds'])
        self.assertEqual(3600, self.summary('preparation.json')['build_wait_seconds'])
        self.assertEqual('passed', self.summary('status.json')['status'])

    def test_failed_preparation_retains_diagnostics_and_leaves_validation_incomplete(self):
        self.preparation_code = 17
        self.assertEqual(1, self.execute())
        self.assert_no_test_launch()
        self.assertEqual('failed', self.summary('preparation.json')['status'])
        self.assertEqual(17, self.summary('preparation.json')['exit_code'])

    def test_preparation_timeout_retains_diagnostics_and_never_joins_queue(self):
        self.preparation_error = TimeoutError('preparation timeout')
        with self.assertRaisesRegex(TimeoutError, 'preparation timeout'):
            self.execute()
        self.assert_no_test_launch()
        preparation = self.summary('preparation.json')
        self.assertEqual('incomplete', preparation['status'])
        self.assertEqual('preparation timeout', preparation['reason'])
        self.assertEqual(8, preparation['elapsed_seconds'])

    def test_preparation_interruption_removes_only_owned_temporaries(self):
        self.preparation_error = KeyboardInterrupt()
        outside = self.root / 'target/other-build'
        outside.mkdir(parents=True)
        (outside / 'keep').write_text('unrelated')
        with self.assertRaises(KeyboardInterrupt):
            self.execute()
        self.assert_no_test_launch()
        self.assertEqual('unrelated', (outside / 'keep').read_text())

    def test_preparation_changes_stop_before_any_batch(self):
        self.change_during_preparation = True
        with self.assertRaisesRegex(ValueError, 'changed during preparation'):
            self.execute()
        self.assert_no_test_launch()
        self.assertEqual('incomplete', self.summary('preparation.json')['status'])

    def test_preparation_consumes_invocation_budget_before_queueing(self):
        with self.assertRaisesRegex(TimeoutError, 'exhausted before another batch'):
            self.execute(max_minutes=8 / 60)
        self.assertEqual([], self.admissions)
        self.assertEqual(1, len(self.commands))
        self.assertEqual('incomplete', self.summary('status.json')['status'])
        self.assertEqual(0, self.summary('results.json')[0]['batches_completed'])


class PreparationContractTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def pom(self, plugin='', extra=''):
        (self.root / 'pom.xml').write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0">'
            f'<build><plugins>{plugin}</plugins></build>{extra}</project>')

    def execution(self, artifact, body):
        return (f'<plugin><artifactId>{artifact}</artifactId><executions><execution>'
                f'<id>example</id>{body}</execution></executions></plugin>')

    def test_repository_pom_supports_preparation_once(self):
        runner.validate_preparation_contract(runner.ROOT)

    def test_preparation_and_packaging_executions_are_preserved_or_irrelevant(self):
        for phase in ('validate', 'initialize', 'generate-resources', 'compile',
                      'generate-test-sources', 'test-compile', 'prepare-package', 'package', 'verify'):
            with self.subTest(phase=phase):
                self.pom(self.execution('example', f'<phase>{phase}</phase><goals><goal>run</goal></goals>'))
                runner.validate_preparation_contract(self.root)

    def test_os_profile_preparation_stays_in_maven_lifecycle(self):
        plugin = self.execution('example', '<phase>initialize</phase><goals><goal>run</goal></goals>')
        self.pom(extra='<profiles><profile><id>os-settings</id><activation><os><family>mac</family>'
                       '</os></activation><build><plugins>' + plugin + '</plugins></build></profile></profiles>')
        runner.validate_preparation_contract(self.root)

    def test_skipped_or_implicit_execution_phases_are_rejected(self):
        for phase in ('process-test-classes', 'test', '${custom.phase}', ''):
            with self.subTest(phase=phase):
                self.pom(self.execution('custom-plugin', f'<phase>{phase}</phase><goals><goal>run</goal></goals>'))
                with self.assertRaisesRegex(ValueError, 'cannot preserve execution'):
                    runner.validate_preparation_contract(self.root)

    def test_surefire_execution_configuration_is_rejected_in_base_and_profiles(self):
        plugin = self.execution('maven-surefire-plugin', '<configuration><groups>special</groups></configuration>')
        for in_profile in (False, True):
            with self.subTest(in_profile=in_profile):
                self.pom('' if in_profile else plugin,
                         '<profiles><profile><id>extra</id><build><plugins>' + plugin +
                         '</plugins></build></profile></profiles>' if in_profile else '')
                with self.assertRaisesRegex(ValueError, 'Surefire executions'):
                    runner.validate_preparation_contract(self.root)

    def test_lane_profiles_cannot_add_preparation_inputs(self):
        for contents in ('<dependencies/>', '<properties><compiler.option>true</compiler.option></properties>',
                         '<build><resources/></build>',
                         '<build><plugins><plugin><artifactId>compiler</artifactId></plugin></plugins></build>'):
            with self.subTest(contents=contents):
                self.pom(extra='<profiles><profile><id>guards</id>' + contents + '</profile></profiles>')
                with self.assertRaisesRegex(ValueError, 'change only Surefire'):
                    runner.validate_preparation_contract(self.root)

    def test_contract_rejection_precedes_tool_preflight_and_target_mutation(self):
        self.pom(self.execution('custom-plugin', '<phase>test</phase><goals><goal>run</goal></goals>'))
        with patch.object(runner, 'ROOT', self.root), patch.object(runner, 'preflight') as tools, \
                patch.object(runner, 'worktree_build_slot') as lock, \
                patch('sys.stderr', new_callable=io.StringIO):
            self.assertEqual(2, runner.main(['--category', 'common', '--run']))
        tools.assert_not_called()
        lock.assert_not_called()
        self.assertFalse((self.root / 'target').exists())


if __name__ == '__main__':
    unittest.main()
