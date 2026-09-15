"""Common capture launch/failure contract, using a fake BizHawk installation."""
import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import capture_native_references as capture
import capture_fbz_visual_references as fbz


class CaptureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.home = self.root / 'hawk'
        (self.home / 'dll').mkdir(parents=True)
        for name in ('EmuHawk.exe', 'dll/BizHawk.Client.Common.dll', 'dll/gpgx.wbx.zst'):
            (self.home / name).write_bytes(name.encode())
        (self.home / 'config.ini').write_text(json.dumps({'PathEntries': {'Paths': [
            {'System': 'GEN', 'Type': 'Save States', 'Path': 'old'},
            {'System': 'Global', 'Type': 'Firmware', 'Path': 'old'}]}}))
        self.rom = self.root / 'source.rom'
        self.rom.write_bytes(b'explicit test ROM identity')
        self.movie = self.root / 'source.bk2'
        self.movie.write_bytes(b'explicit movie')
        self.exporter = self.root / 'exporter.lua'
        self.exporter.write_text('client.exit()')
        self.plan = self.root / 'plan.lua'
        self.plan.write_text('return {zone="soz",start_frame=0}')
        self.output = self.root / 'out'
        self.argv = ['--bizhawk-home', str(self.home), '--rom', str(self.rom),
                     '--rom-sha1', capture.digest(self.rom, 'sha1'), '--movie', str(self.movie),
                     '--exporter', str(self.exporter), '--plan', str(self.plan), '--output', str(self.output)]

    def launch(self, log='', returncode=0, timeout=False, extra=()):
        def run(command, **kwargs):
            self.command, self.env = command, kwargs['env']
            kwargs['stdout'].write(log)
            if timeout:
                raise subprocess.TimeoutExpired(command, 1)
            return subprocess.CompletedProcess(command, returncode)
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()), \
                patch.object(capture.subprocess, 'check_output', return_value='Version: 2.11.0.0\n'), \
                patch.object(capture.subprocess, 'run', side_effect=run):
            code = capture.main(self.argv + list(extra))
        return code, json.loads((self.output / 'host.json').read_text())

    def test_explicit_plan_no_zone_defaults_and_source_config_untouched(self):
        before = (self.home / 'config.ini').read_bytes()
        code, receipt = self.launch()
        self.assertEqual(0, code)
        self.assertEqual(self.plan.read_text(), (self.output / 'plan.lua').read_text())
        self.assertEqual(str(self.output / 'plan.lua'), self.env['OGGF_NATIVE_PLAN'])
        self.assertNotIn('OGGF_FBZ_VISUAL_PLAN', self.env)
        self.assertEqual(capture.digest(self.plan), receipt['plan_sha256'])
        self.assertEqual(before, (self.home / 'config.ini').read_bytes())
        self.assertEqual('pending-independent-pixel-and-state-review', receipt['acceptance'])
        self.assertFalse(json.loads((self.output / 'config.ini').read_text())['DisplayInput'])

    def test_lua_error_with_zero_emulator_exit_is_failure(self):
        code, receipt = self.launch('NLua.Exceptions.LuaScriptException: [string "main"]:5: assertion failed!\n')
        self.assertEqual(1, code)
        self.assertEqual(0, receipt['exit_code'])
        self.assertIn('lua-script-error', receipt['failures'])

    def test_timeout_is_failure(self):
        code, receipt = self.launch(timeout=True)
        self.assertEqual(1, code)
        self.assertTrue(receipt['timeout'])

    def test_process_failure_is_failure(self):
        code, receipt = self.launch(returncode=2)
        self.assertEqual(1, code)
        self.assertIn('emulator-exit-2', receipt['failures'])

    def test_declared_output_is_required(self):
        code, receipt = self.launch(extra=['--require-output', 'observations.csv'])
        self.assertEqual(1, code)
        self.assertIn('missing-or-empty-output:observations.csv', receipt['failures'])

    def test_wrong_rom_is_rejected_before_creating_output(self):
        self.argv[self.argv.index('--rom-sha1') + 1] = '0' * 40
        with self.assertRaises(SystemExit):
            capture.main(self.argv)
        self.assertFalse(self.output.exists())

    def test_existing_output_is_preserved(self):
        self.output.mkdir()
        marker = self.output / 'keep'
        marker.write_text('preserve')
        with self.assertRaises(SystemExit):
            self.launch()
        self.assertEqual('preserve', marker.read_text())

    def test_required_output_cannot_escape_task_directory(self):
        with self.assertRaises(SystemExit):
            capture.main(self.argv + ['--require-output', '../elsewhere'])
        self.assertFalse(self.output.exists())

    def test_fixture_state_identity_and_environment_are_explicit(self):
        state = self.root / 'native.State'
        state.write_bytes(b'state identity')
        code, receipt = self.launch(extra=['--fixture-state', str(state)])
        self.assertEqual(0, code)
        self.assertEqual(str(state), self.env['OGGF_NATIVE_FIXTURE_STATE'])
        self.assertEqual(capture.digest(state), receipt['fixture_state_sha256'])

    def test_missing_fixture_does_not_inherit_an_ambient_state(self):
        with patch.dict(capture.os.environ, {'OGGF_NATIVE_FIXTURE_STATE': '/ambient/state'}):
            self.launch()
        self.assertNotIn('OGGF_NATIVE_FIXTURE_STATE', self.env)

    def test_fbz_compatibility_keeps_recipe_and_old_environment_names(self):
        with patch.object(fbz, 'run_capture', return_value=0) as run:
            self.assertEqual(0, fbz.main(['--start-frame', '250000', '--window', '128',
                                         '--fresh-entry-act', '2']))
        args = run.call_args.args[0]
        self.assertEqual('CFBF98C36C776677290A872547AC47C53D2761D6', args.rom_sha1)
        self.assertEqual('capture_fbz_visual_references.lua', args.exporter.name)
        self.assertIn('bk2_frame=250000', run.call_args.kwargs['plan_text'])
        self.assertIn('fresh_entry_act=2', run.call_args.kwargs['plan_text'])
        self.assertEqual('OGGF_NATIVE_PLAN',
                         run.call_args.kwargs['environment_aliases']['OGGF_FBZ_VISUAL_PLAN'])

    def test_new_command_requires_plan_and_exporter(self):
        for option in ('--plan', '--exporter'):
            args = self.argv.copy()
            index = args.index(option)
            del args[index:index + 2]
            with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()):
                capture.main(args)
            self.assertFalse(self.output.exists())


if __name__ == '__main__':
    unittest.main()
