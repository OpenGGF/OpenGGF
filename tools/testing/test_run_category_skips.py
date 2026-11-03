"""Category verdicts classify every skip with the release skip policy; no Maven or ROMs."""
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import category_artifacts as artifacts
import classify_surefire_skips as csk
import run_categories as runner

OPT_IN = 'com.openggf.game.rewind.TestRewindTorture#tortureProgressiveLongRewinds'
GL = 'com.openggf.capture.GlReadPixelsGrabberTest#pboReadbackPreservesBothFramesAndExternalPackBinding'
BK2 = 'com.openggf.tools.audio.parity.s2.TestS2DriverStateOracle#driverStateComparesAcrossTheWidenedSpan'
ROM = 'com.openggf.tests.TestSonic3kLevelLoading#loadsAiz1'
HUD = 'com.openggf.editor.project.TestStageplayHudPictures#captureDayoffAudioCue'
NEW_OPT_IN = [
    *(f'com.openggf.editor.project.TestStageplayHudPictures#{name}' for name in (
        'captureDayoffAudioCue', 'captureReadabilityCheckpoint', 'captureCompleteMenuSurvey',
        'captureCollisionRouteAndVisualFootprintForms', 'captureScreenplayImportRefineAndExportForms',
        'captureAnimatedCameraFormForNativeWorld', 'captureObjectCastArenaRestrictionAndModeForm',
        'captureStageplayCastTimelineAndCommands')),
    'com.openggf.editor.project.TestEditorHudPictures#captureControllerHud',
    'com.openggf.editor.project.TestStageplayRouteOverlayPictures#captureProposedFailedAndAppliedRoutePlayback',
    'com.openggf.game.sonic3k.objects.TestS3kAiz1CompatibilityRoutes#axisRouteCompletes(int, String)',
    'com.openggf.game.sonic3k.objects.TestS3kAiz1SpringRecovery'
    '#liveSpringJumpCrossesAndReplaysWhileWalkingIsRejected(int, String)',
]
HEADLESS = {'DISPLAY': '', 'WAYLAND_DISPLAY': '', 'JAVA_TOOL_OPTIONS': '', 'JDK_JAVA_OPTIONS': '',
            '_JAVA_OPTIONS': '', 'MAVEN_ARGS': ''}


class SkipVerdictTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True, capture_output=True)
        self.plan = dict(full=False, tests=['com/openggf/TestExample.java'], guards=False,
                         categories=['common'], inventory_count=1, head='0123456789abcdef', mode='focused')
        # Each Maven batch writes the listed (identity, outcome) cases.
        self.batches = [[('com.openggf.TestExample#ran', 'pass'), (OPT_IN, 'skip')]]
        self.roms = []
        self.stdout = io.StringIO()
        for mock in (patch.object(runner, 'tree_state', return_value='f' * 64),
                     patch.object(runner, 'rom_args', side_effect=lambda *args: list(self.roms)),
                     patch.object(runner, 'run_logged', side_effect=self.maven),
                     patch.dict(os.environ, HEADLESS),
                     patch('sys.stdout', self.stdout)):
            mock.start()
            self.addCleanup(mock.stop)

    def maven(self, command, cwd, log, temporary, **kwargs):
        if 'test-compile' in command:
            return 0
        reports = Path(next(arg.split('=', 1)[1] for arg in command
                            if arg.startswith('-Dopenggf.surefire.reports=')))
        reports.mkdir()
        cases = self.batches.pop(0)
        failures = sum(outcome == 'fail' for _, outcome in cases)
        suite = ET.Element('testsuite', name='batch', tests=str(len(cases)), errors='0',
                           failures=str(failures),
                           skipped=str(sum(outcome == 'skip' for _, outcome in cases)))
        for identity, outcome in cases:
            classname, name = identity.split('#', 1)
            case = ET.SubElement(suite, 'testcase', classname=classname, name=name, time='0.1')
            if outcome == 'skip':
                ET.SubElement(case, 'skipped', message='Assumption failed: not supplied')
            elif outcome == 'fail':
                ET.SubElement(case, 'failure', message='assertion failed')
        ET.ElementTree(suite).write(reports / 'TEST-batch.xml')
        return int(bool(failures))

    def execute(self, **kwargs):
        return runner.run_plan(self.root, self.plan, **kwargs)

    def runs(self):
        base = self.root / 'target/category-tests'
        return sorted(base.iterdir()) if base.exists() else []

    def results(self):
        run, = self.runs()
        return json.loads((run / 'results.json').read_text())

    def status(self):
        run, = self.runs()
        return json.loads((run / 'status.json').read_text())['status']

    def verdict(self):
        lines = [line for line in self.stdout.getvalue().splitlines() if line.startswith('VERDICT ')]
        self.assertTrue(lines, self.stdout.getvalue())
        return lines[-1]

    def test_policy_allowed_skip_passes_and_cli_mode_removes_the_run(self):
        self.assertEqual(0, self.execute(clean_passed=True))
        self.assertEqual([], self.runs())
        self.assertFalse((self.root / 'target/category-tests').exists())
        line = self.verdict()
        for text in ('VERDICT PASSED', 'head=0123456789ab', 'source=ffffffffffff', '1/1 candidate classes',
                     'ordinary 1/1 batches', 'skipped=1 (allowed=1 unexplained=0)', 'not a full-suite pass'):
            self.assertIn(text, line)
        self.assertIn('Passing diagnostics removed.', self.stdout.getvalue())
        self.assertNotIn('--acknowledge', self.stdout.getvalue())

    def test_passing_run_is_retained_on_request_or_for_direct_callers(self):
        for options in (dict(clean_passed=True, keep_diagnostics=True), {}):
            with self.subTest(options=options):
                self.batches = [[('com.openggf.TestExample#ran', 'pass'), (OPT_IN, 'skip')]]
                self.assertEqual(0, self.execute(**options))
                run, = self.runs()
                self.assertEqual('passed', self.status())
                self.assertEqual(1, self.results()[0]['skip_classification']['allowed'])
                self.assertIn(f'--acknowledge {run.name}', self.stdout.getvalue())
                artifacts.acknowledge_run(self.root, run.name)

    def test_unknown_skip_is_incomplete_retained_and_acknowledgeable(self):
        self.batches = [[('com.openggf.TestExample#ran', 'pass'), ('com.openggf.TestNew#later', 'skip')]]
        self.assertEqual(1, self.execute(clean_passed=True))
        self.assertEqual('incomplete', self.status())
        lane, = self.results()
        self.assertEqual('incomplete', lane['status'])
        self.assertEqual(1, lane['skip_classification']['unclassified'])
        self.assertEqual('com.openggf.TestNew#later', lane['unexplained_skips'][0]['identity'])
        self.assertIn('VERDICT INCOMPLETE', self.verdict())
        self.assertIn('unexplained=1', self.verdict())
        self.assertIn('Unexplained ordinary skip [unclassified]: com.openggf.TestNew#later',
                      self.stdout.getvalue())
        run, = self.runs()
        self.assertIn(f'--acknowledge {run.name}', self.stdout.getvalue())
        artifacts.acknowledge_run(self.root, run.name)
        self.assertEqual([], self.runs())

    def test_missing_rom_skip_is_incomplete_and_names_missing_properties_only(self):
        self.roms = ['-Dsonic1.rom.path=/private/roms/secret name.gen']
        self.batches = [[('com.openggf.TestExample#ran', 'pass'), (ROM, 'skip')]]
        self.assertEqual(1, self.execute(clean_passed=True))
        self.assertEqual('incomplete', self.status())
        line = self.verdict()
        self.assertIn('missing ROM properties=', line)
        self.assertIn('s3k.rom.path', line)
        self.assertNotIn('sonic1.rom.path,', line.split('missing ROM properties=')[1].split(';')[0] + ',')
        self.assertNotIn('/private/roms', line)
        run, = self.runs()
        plan = json.loads((run / 'plan.json').read_text())
        self.assertEqual(['sonic1.rom.path'], plan['rom_properties']['found'])
        self.assertIn('s3k.rom.path', plan['rom_properties']['missing'])

    def test_declared_display_makes_a_gl_skip_required(self):
        self.batches = [[('com.openggf.TestExample#ran', 'pass'), (GL, 'skip')]]
        self.assertEqual(0, self.execute())
        artifacts.acknowledge_run(self.root, self.runs()[0].name)
        self.batches = [[('com.openggf.TestExample#ran', 'pass'), (GL, 'skip')]]
        with patch.dict(os.environ, {'WAYLAND_DISPLAY': 'wayland-1'}):
            self.assertEqual(1, self.execute())
        lane, = self.results()
        self.assertEqual(1, lane['skip_classification']['required_skipped'])
        self.assertEqual('gl', lane['unexplained_skips'][0]['input'])
        self.assertIn('gl=true', self.verdict())

    def test_property_input_supplied_through_jvm_options_is_required(self):
        policy = csk.load_policy(runner.SKIP_POLICY)
        self.assertFalse(runner.skip_capabilities(policy, HEADLESS)['s2_bk2'])
        supplied = dict(HEADLESS, JAVA_TOOL_OPTIONS='-Dopenggf.headless.gl=surfaceless-egl '
                                                    '-Ds2.request.bk2.path=/movies/s2.bk2')
        self.assertTrue(runner.skip_capabilities(policy, supplied)['s2_bk2'])
        self.assertFalse(runner.skip_capabilities(policy, supplied)['gl'])
        # Unparseable options and unknown inputs stay undeclared, so their skips fail closed.
        broken = runner.skip_capabilities(policy, dict(HEADLESS, MAVEN_ARGS='"unterminated'))
        self.assertNotIn('s2_bk2', broken)
        unknown = dict(policy, inputs=dict(policy['inputs'], new_fixture='future input'))
        self.assertNotIn('new_fixture', runner.skip_capabilities(unknown, HEADLESS))
        self.batches = [[('com.openggf.TestExample#ran', 'pass'), (BK2, 'skip')]]
        with patch.dict(os.environ, {'MAVEN_ARGS': '"unterminated'}):
            self.assertEqual(1, self.execute())
        self.assertEqual(1, self.results()[0]['skip_classification']['undeclared_input'])

    def test_batches_aggregate_counts_and_cap_unexplained_identities(self):
        self.plan['tests'] = [f'com/openggf/Test{index:03d}.java' for index in range(101)]
        self.plan['inventory_count'] = 101
        self.batches = [
            [('com.openggf.TestA#ran', 'pass'), (OPT_IN, 'skip'), ('com.openggf.TestA#one', 'skip')],
            [('com.openggf.TestB#ran', 'pass'), ('com.openggf.TestB#two', 'skip'),
             ('com.openggf.TestB#three', 'skip')],
            [('com.openggf.TestC#ran', 'pass'), (GL, 'skip')],
        ]
        with patch.object(runner, 'CASE_LIMIT', 2):
            self.assertEqual(1, self.execute())
        lane, = self.results()
        self.assertEqual(3, lane['batches_completed'])
        self.assertEqual({'allowed': 2, 'unclassified': 3, 'required_skipped': 0,
                          'undeclared_input': 0, 'unidentified': 0}, lane['skip_classification'])
        self.assertEqual(['com.openggf.TestA#one', 'com.openggf.TestB#three'],
                         [skip['identity'] for skip in lane['unexplained_skips']])
        self.assertEqual(1, lane['unexplained_skips_omitted'])
        self.assertIn('ordinary 3/3 batches', self.verdict())
        self.assertIn('skipped=5 (allowed=2 unexplained=3)', self.verdict())

    def test_skip_count_without_report_identities_is_unexplained(self):
        summary = dict(reports=1, tests=2, failures=0, errors=0, skipped=1, skipped_cases=[])
        self.batches = [[('com.openggf.TestExample#ran', 'pass')]]
        with patch.object(runner, 'summarize', return_value=summary):
            self.assertEqual(1, self.execute())
        self.assertEqual(1, self.results()[0]['skip_classification']['unidentified'])

    def test_failures_take_precedence_over_unexplained_skips(self):
        self.plan['tests'] = [f'com/openggf/Test{index:03d}.java' for index in range(51)]
        self.batches = [[('com.openggf.TestA#broken', 'fail'), ('com.openggf.TestA#ran', 'pass')],
                        [('com.openggf.TestB#ran', 'pass'), ('com.openggf.TestB#unknown', 'skip')]]
        self.assertEqual(1, self.execute(clean_passed=True))
        self.assertEqual('failed', self.status())
        self.assertEqual('failed', self.results()[0]['status'])
        self.assertIn('VERDICT FAILED', self.verdict())

    def test_entirely_skipped_lane_fails_even_when_every_skip_is_allowed(self):
        self.batches = [[(OPT_IN, 'skip')]]
        self.assertEqual(1, self.execute(clean_passed=True))
        self.assertEqual('failed', self.status())
        self.assertEqual(1, self.results()[0]['skip_classification']['allowed'])

    def test_malformed_policy_stops_before_any_launch(self):
        bad = self.root / 'bad-policy.json'
        bad.write_text(json.dumps({'version': 1, 'rules': [{'match': 'Wild*#card', 'category': 'opt-in-gate',
                                                            'evidence': 'x', 'allowed_when_absent': None}]}))
        with patch.object(runner, 'SKIP_POLICY', bad):
            with self.assertRaises(csk.PolicyError):
                self.execute()
            self.assertFalse((self.root / 'target').exists())
            with patch.object(runner, 'ROOT', self.root), patch.object(runner, 'policy', return_value={'categories': {'common': ''}}), \
                    patch.object(runner, 'make_plan', return_value=dict(self.plan)), \
                    patch.object(runner, 'preflight') as preflight, \
                    patch.object(runner, 'worktree_build_slot') as lock, \
                    patch('sys.stderr', new_callable=io.StringIO) as error:
                for mode in ('--run', '--preflight'):
                    self.assertEqual(2, runner.main(['--category', 'common', mode]))
            preflight.assert_not_called()
            lock.assert_not_called()
            runner.run_logged.assert_not_called()
            self.assertIn('rule match must be an exact', error.getvalue())

    def test_preflight_reports_policy_inputs_and_rom_properties(self):
        self.roms = ['-Ds3k.rom.path=/private/roms/s3k.gen']
        with patch.object(runner, 'ROOT', self.root), patch.object(runner, 'policy', return_value={'categories': {'common': ''}}), \
                patch.object(runner, 'make_plan', return_value=dict(self.plan)), \
                patch.object(runner, 'preflight') as preflight:
            self.assertEqual(0, runner.main(['--category', 'common', '--preflight']))
        preflight.assert_called_once()
        output = self.stdout.getvalue()
        self.assertIn('gl=false', output)
        self.assertIn('found=s3k.rom.path', output)
        self.assertNotIn('/private/roms', output)
        runner.run_logged.assert_not_called()

    def test_removing_a_reviewed_opt_in_rule_turns_the_same_run_incomplete(self):
        policy = csk.load_policy(runner.SKIP_POLICY)
        self.batches = [[('com.openggf.TestExample#ran', 'pass'), (HUD, 'skip')]]
        self.assertEqual(0, self.execute(skip_policy=policy, clean_passed=True))
        removed = dict(policy, rules=[rule for rule in policy['rules'] if rule['match'] != HUD])
        self.assertEqual(len(policy['rules']) - 1, len(removed['rules']))
        self.batches = [[('com.openggf.TestExample#ran', 'pass'), (HUD, 'skip')]]
        self.assertEqual(1, self.execute(skip_policy=removed, clean_passed=True))
        self.assertEqual(HUD, self.results()[0]['unexplained_skips'][0]['identity'])


class ReviewedOptInRuleTests(unittest.TestCase):
    def test_explicit_property_opt_ins_are_allowed_for_runner_and_release(self):
        policy = csk.load_policy(runner.SKIP_POLICY)
        rules = {rule['match']: rule for rule in policy['rules']}
        release = {'gl': True, 's2_bk2': False, 's3k_observations': False,
                   's1_bizhawk_reference': False, 'audio_reference_files': False}
        for capabilities in (runner.skip_capabilities(policy, HEADLESS), release):
            result = csk.classify(dict.fromkeys(NEW_OPT_IN, ''), policy, capabilities)
            self.assertEqual(len(NEW_OPT_IN), len(result['allowed']))
        root = runner.ROOT
        for identity in NEW_OPT_IN:
            rule = rules[identity]
            self.assertIsNone(rule['allowed_when_absent'])
            self.assertIn(rule['category'], ('opt-in-capture', 'opt-in-gate'))
            source, line = rule['evidence'].rsplit(':', 1)
            text = (root / source).read_text().splitlines()[int(line) - 1]
            # Each rule cites the explicit property gate, never a ROM or display condition.
            self.assertRegex(text, r'Boolean\.getBoolean\("openggf\.|@EnabledIfSystemProperty\(named = "openggf\.')


if __name__ == '__main__':
    unittest.main()
