"""Shared ROM discovery for category and focused queue launches; no Maven or ROMs required."""
from contextlib import nullcontext, redirect_stdout
import hashlib
from io import StringIO
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parent


def image(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return hashlib.sha1(content).hexdigest()


class Fixture(unittest.TestCase):
    def setUp(self):
        import rom_inputs
        self.roms = rom_inputs
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.tmp = Path(temp.name)
        # Git identity of a private repository with one linked worktree.
        self.main = self.tmp / 'main checkout'
        self.main.mkdir()
        self.git(self.main, 'init', '-q')
        self.git(self.main, 'commit', '-q', '--allow-empty', '-m', 'base')
        self.linked = self.tmp / 'linked worktree'
        self.git(self.main, 'worktree', 'add', '-q', '--detach', str(self.linked))
        self.other = self.tmp / 'other worktree'
        self.git(self.main, 'worktree', 'add', '-q', '--detach', str(self.other))

    def git(self, root, *args):
        subprocess.run(['git', '-c', 'user.name=t', '-c', 'user.email=t@example.invalid', *args],
                       cwd=root, check=True, capture_output=True)

    def table(self, **digests):
        # Fixture digests join the real table, so every real property name stays known.
        return patch.dict(self.roms.ROM_PROPERTIES, {d: k.replace('_', '.') for k, d in digests.items()})


class RomDiscoveryTests(Fixture):
    def test_runner_reexports_the_single_owner(self):
        import run_categories as runner
        for name in ('ROM_PROPERTIES', 'rom_args', 'rom_properties'):
            self.assertIs(getattr(self.roms, name), getattr(runner, name))
        # The shared owner imports neither launcher, so neither can form a cycle.
        source = (TOOLS / 'rom_inputs.py').read_text()
        for module in ('run_categories', 'maven_queue', 'maven_small'):
            self.assertNotIn(module, source)

    def test_main_checkout_is_the_only_automatic_fallback(self):
        s1 = image(self.main / 'Original S1 [!].gen', b'main s1')
        image(self.other / 'other.gen', b'other s2')
        s2 = hashlib.sha1(b'other s2').hexdigest()
        with self.table(sonic1_rom_path=s1, sonic2_rom_path=s2):
            self.assertEqual([f"-Dsonic1.rom.path={(self.main / 'Original S1 [!].gen').resolve()}"],
                             self.roms.rom_args(self.linked))
            self.assertEqual([f"-Dsonic1.rom.path={(self.main / 'Original S1 [!].gen').resolve()}"],
                             self.roms.rom_args(self.main))
        self.assertEqual(self.main.resolve(), self.roms.main_checkout(self.linked))
        self.assertIsNone(self.roms.main_checkout(self.main))
        self.assertIsNone(self.roms.main_checkout(self.tmp / 'not-a-repo'))

    def test_current_root_then_explicit_directory_then_main(self):
        explicit = self.tmp / 'explicit ROMs'
        s1 = image(self.linked / 'local.gen', b'one')
        image(explicit / 'same.gen', b'one')
        image(self.main / 'same.gen', b'one')
        s2 = image(explicit / 'two.gen', b'two')
        image(self.main / 'two.gen', b'two')
        s3k = image(self.main / 's3k.gen', b'three')
        image(self.linked / 'unknown.gen', b'unknown')
        with self.table(sonic1_rom_path=s1, sonic2_rom_path=s2, s3k_rom_path=s3k):
            self.assertEqual([f"-Ds3k.rom.path={(self.main / 's3k.gen').resolve()}",
                              f"-Dsonic1.rom.path={(self.linked / 'local.gen').resolve()}",
                              f"-Dsonic2.rom.path={(explicit / 'two.gen').resolve()}"],
                             self.roms.rom_args(self.linked, explicit))
        for path in (self.linked, explicit, self.main):
            self.assertEqual(sorted(path.glob('*.gen')),
                             sorted(p for p in path.iterdir() if p.suffix == '.gen'))
        self.assertFalse(any(p.is_symlink() for p in self.linked.iterdir()))

    def test_existing_links_resolve_to_one_original_without_rehashing(self):
        s3k = image(self.main / 's3k.gen', b'linked image')
        (self.linked / 's3k.gen').symlink_to(self.main / 's3k.gen')
        opened = []
        original = Path.open

        def counting(path, *args, **kwargs):
            if path.suffix == '.gen':
                opened.append(path.resolve())
            return original(path, *args, **kwargs)
        with self.table(s3k_rom_path=s3k), patch.object(Path, 'open', counting):
            self.assertEqual([f"-Ds3k.rom.path={(self.main / 's3k.gen').resolve()}"],
                             self.roms.rom_args(self.linked))
        self.assertEqual([(self.main / 's3k.gen').resolve()], opened)

    def test_caller_properties_cover_command_environment_and_launch_config(self):
        root = self.linked
        cases = [
            ((['-Ds3k.rom.path='], {}), {'s3k.rom.path'}),
            ((['-Dsurefire.argLine=-Dsonic1.rom.path=/x'], {}), {'sonic1.rom.path'}),
            (([], {'JAVA_TOOL_OPTIONS': '-Dsonic2.rom.path="/a b.gen'}), {'sonic2.rom.path'}),
            (([], {'MAVEN_OPTS': '-Dkis2.rom.path=/k'}), {'kis2.rom.path'}),
            (([], {'SONIC_3K_ROM_PATH': ''}), {'s3k.rom.path'}),
            (([], {'SONIC_1_ROM_PATH': '/s1'}), {'sonic1.rom.path'}),
            (([], {'JAVA_TOOL_OPTIONS': '-Dopenggf.headless.gl=surfaceless-egl'}), set()),
            (([], {'MAVEN_OPTS': '-Dsonic1.rev00.rom.path=/r0'}), {'sonic1.rev00.rom.path'}),
        ]
        for (args, environment), expected in cases:
            with self.subTest(args=args, environment=environment):
                self.assertEqual(expected, self.roms.caller_rom_properties(args, root, environment))
        (root / '.mvn').mkdir()
        (root / '.mvn/jvm.config').write_text('-Ds3k.rom.path=/configured\n')
        self.assertEqual({'s3k.rom.path'}, self.roms.caller_rom_properties([], root, {}))
        report = self.roms.rom_properties(['-Dsonic1.rom.path=/s1'], {'s3k.rom.path'})
        self.assertEqual(['sonic1.rom.path'], report['found'])
        self.assertEqual(['s3k.rom.path'], report['caller'])
        self.assertNotIn('s3k.rom.path', report['missing'])
        self.assertIn('sonic2.rom.path', report['missing'])


class LaunchTests(Fixture):
    """Assert the argv each route actually launches."""

    def setUp(self):
        super().setUp()
        self.s1_path = self.main / 'Sonic (W) (REV01) [!].gen'
        self.s3k_path = self.main / 'S3 & K [!].gen'
        self.digests = dict(sonic1_rom_path=image(self.s1_path, b'fixture s1'),
                            s3k_rom_path=image(self.s3k_path, b'fixture s3k'))
        self.discovered = [f'-Ds3k.rom.path={self.s3k_path.resolve()}',
                           f'-Dsonic1.rom.path={self.s1_path.resolve()}']
        self.environment = patch.dict(os.environ, {'OPENGGF_MAVEN_QUEUE': 'serial'})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        for key in ('MAVEN_ARGS', 'MAVEN_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS',
                    'SONIC_1_ROM_PATH', 'SONIC_2_ROM_PATH', 'SONIC_3K_ROM_PATH'):
            os.environ.pop(key, None)
        cwd = os.getcwd()
        os.chdir(self.linked)
        self.addCleanup(os.chdir, cwd)

    def launch(self, args, *, automatic=None):
        import maven_queue as queue
        import maven_activity
        import maven_small
        import run_categories
        output = StringIO()
        with self.table(**self.digests), \
                patch.object(run_categories, 'tree_state', return_value='unchanged'), \
                patch.object(run_categories, 'validate_preparation_contract'), \
                patch.object(queue, 'maven_slot', return_value=nullcontext(None)) as slot, \
                patch.object(queue, 'worktree_build_slot', return_value=nullcontext(None)) as build, \
                patch.object(maven_activity, 'build_activity_slot', return_value=nullcontext(())), \
                patch.object(maven_small, 'automatic_lean', return_value=automatic) as lean, \
                patch('category_artifacts.stop_process_tree'), \
                patch.object(queue.subprocess, 'Popen') as popen, redirect_stdout(output):
            popen.return_value.__enter__.return_value.wait.return_value = 0
            self.assertEqual(0, queue.main(list(args)))
        self.commands = [call.args[0] for call in popen.call_args_list]
        return popen.call_args.args[0], slot, build, lean, output.getvalue()

    def test_focused_test_adds_discovered_roms_after_caller_tokens(self):
        args = ['-Dmse=off', '-Dtest=TestOne,TestTwo#case', 'test']
        argv, slot, build, lean, output = self.launch(args)
        self.assertEqual(['mvn', *args, *self.discovered], argv)
        lean.assert_called_once_with(args, root=Path.cwd())
        # Admission shape comes from the caller's command, not from ROM paths.
        self.assertEqual('focused', slot.call_args.kwargs['kind'])
        self.assertFalse(slot.call_args.kwargs['exclusive'])
        build.assert_not_called()
        self.assertIn('found=s3k.rom.path,sonic1.rom.path caller=none', output)
        self.assertIn('missing=kis2.rom.path,', output)

    def test_paths_that_look_like_heap_or_fork_overrides_do_not_change_admission(self):
        moved = self.main / 'S3K -Xmx64g argLine forkCount -Dtest=X.gen'
        self.s3k_path.rename(moved)
        argv, slot, *_ = self.launch(['-Dtest=TestOne', 'test'])
        self.assertIn(f'-Ds3k.rom.path={moved.resolve()}', argv)
        self.assertFalse(slot.call_args.kwargs['exclusive'])

    def test_qualified_lean_command_is_classified_before_roms_are_added(self):
        bounded = (['-Dmse=off', '-Dtest=TestOne', 'test', '-Dsurefire.argLine=-Xmx1g'], {'SMALL': '1'})
        argv, slot, _, lean, _ = self.launch(['-Dmse=off', '-Dtest=TestOne', 'test'], automatic=bounded)
        # Normal-memory compilation needs no ROMs; the prepared lean run gets them unchanged.
        self.assertEqual([['mvn', '-Dmse=off', '-Dtest=TestOne', 'test-compile'],
                          ['mvn', '-Dmse=off', '-Dtest=TestOne', 'surefire:test', '-Dsurefire.argLine=-Xmx1g',
                           *self.discovered]], self.commands)
        lean.assert_called_once_with(['-Dmse=off', '-Dtest=TestOne', 'test'], root=Path.cwd())
        self.assertTrue(slot.call_args.kwargs['small_job'])

    def test_current_worktree_original_wins_over_main(self):
        local = self.linked / 'local copy.gen'
        local.write_bytes(b'fixture s3k')
        argv, *_ = self.launch(['-Dtest=TestOne', 'test'])
        self.assertIn(f'-Ds3k.rom.path={local.resolve()}', argv)
        self.assertNotIn(self.discovered[0], argv)

    def test_command_naming_any_rom_property_runs_exactly_as_written(self):
        for args in (['-Ds3k.rom.path=', '-Dtest=TestOne', 'test'],
                     ['-Dtest=TestOne', '-Dsonic2.rom.path=/my roms/s2 [!].gen', 'test'],
                     ['-Dsurefire.argLine=-Dkis2.rom.path=/k', '-Dtest=TestOne', 'test']):
            with self.subTest(args=args):
                argv, *_, output = self.launch(args)
                self.assertEqual(['mvn', *args], argv)
                self.assertIn('found=none', output)

    def test_environment_choices_keep_their_property(self):
        cases = {'JAVA_TOOL_OPTIONS': '-Ds3k.rom.path=/env/s3k.gen', 'SONIC_3K_ROM_PATH': '/env/s3k.gen'}
        for key, value in cases.items():
            with self.subTest(variable=key), patch.dict(os.environ, {key: value}):
                argv, *_, output = self.launch(['-Dtest=TestOne', 'test'])
                self.assertEqual(['mvn', '-Dtest=TestOne', 'test', self.discovered[1]], argv)
                self.assertIn('caller=s3k.rom.path', output)

    def test_build_probe_and_custom_shapes_are_not_mutated(self):
        shapes = [
            ['-Dmse=off', 'compile'], ['-DskipTests', 'package'], ['test-compile'],
            ['-Dmaven.test.skip=true', 'verify'],
            ['-Dtest=TestOne', 'surefire:test'], ['compile', 'exec:java', '-Dexec.mainClass=X'],
            ['-f', 'other/pom.xml', 'test'], ['--file=other/pom.xml', 'test'], ['-fother.xml', 'test'],
            ['-pl', 'module', 'test'], ['-pl=module', 'test'], ['--projects=module', 'test'],
            ['-ssettings.xml', 'test'], ['--settings=settings.xml', 'test'],
            ['-Dtest=TestOne', 'test', 'install'], ['-v'], ['-D'],
        ]
        for args in shapes:
            with self.subTest(args=args):
                argv, *_, output = self.launch(args)
                self.assertEqual(['mvn', *args], argv)
                self.assertNotIn('ROM properties', output)
        with patch.dict(os.environ, {'MAVEN_ARGS': '-B'}):
            argv, *_ = self.launch(['-Dtest=TestOne', 'test'])
            self.assertEqual(['mvn', '-Dtest=TestOne', 'test'], argv)

    def test_custom_maven_config_does_not_trigger_discovery(self):
        config = self.linked / '.mvn/maven.config'
        config.parent.mkdir()
        for content in ('-f other/pom.xml', '--file=other/pom.xml', '-pl module',
                        '--projects=module', '-Dmse=relaxed\nverify'):
            with self.subTest(config=content):
                config.write_text(content)
                with patch.object(self.roms, 'rom_args', side_effect=AssertionError('scanned')):
                    argv, *_, output = self.launch(['-Dtest=TestOne', 'test'])
                self.assertEqual(['mvn', '-Dtest=TestOne', 'test'], argv)
                self.assertNotIn('ROM properties', output)
        config.write_text('-Dmse=relaxed\n')
        argv, *_ = self.launch(['-Dtest=TestOne', 'test'])
        self.assertEqual(['mvn', '-Dtest=TestOne', 'test', *self.discovered], argv)

    def test_lifecycle_and_profile_shapes_that_run_surefire_get_discovery(self):
        for args in (['-Psmoke', 'test', '-B'], ['clean', '-fae', 'verify'],
                     ['-P', 'trace-replay', '-Dtest=TestOne', 'test'], ['-T', '1', 'package']):
            with self.subTest(args=args):
                argv, *_ = self.launch(args)
                self.assertEqual(['mvn', *args, *self.discovered], argv)

    def test_stats_and_help_never_scan_roms(self):
        import maven_queue as queue
        import rom_inputs
        with patch.object(rom_inputs, 'rom_args', side_effect=AssertionError('scanned')), \
                patch('maven_running.summarise', return_value='none'), redirect_stdout(StringIO()):
            self.assertEqual(0, queue.main(['--help']))
            self.assertEqual(0, queue.main(['--stats']))

    def test_category_batches_use_the_same_discovery_and_respect_environment(self):
        import run_categories as runner
        plan = {'tests': ['com/openggf/TestExample.java'], 'full': False,
                'guards': False, 'categories': ['common'], 'inventory_count': 1}
        summary = {'reports': 1, 'tests': 1, 'skipped': 0, 'errors': 0, 'failures': 0}
        for environment, expected in (({}, self.discovered),
                                      ({'MAVEN_OPTS': '-Dsonic1.rom.path=/env'}, self.discovered[:1])):
            with self.subTest(environment=environment), self.table(**self.digests), \
                    patch.dict(os.environ, environment), \
                    patch.object(runner, 'tree_state', return_value='state'), \
                    patch.object(runner, 'summarize', side_effect=lambda _: dict(summary)), \
                    patch.object(runner, 'run_logged', return_value=0) as process, \
                    redirect_stdout(StringIO()):
                self.assertEqual(0, runner.run_plan(self.linked, dict(plan)))
                command = process.call_args_list[-1].args[0]
                self.assertEqual(expected, [arg for arg in command if '.rom.path=' in arg])
                # The focused route discovers exactly the same arguments.
                self.assertEqual(self.discovered, self.roms.rom_args(self.linked))


if __name__ == '__main__':
    unittest.main()
