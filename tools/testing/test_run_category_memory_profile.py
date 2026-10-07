"""Per-test profiler aggregation and real JUnit lifecycle/retention controls."""
import json
import os
from contextlib import nullcontext
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import profile_test_memory as profile


class MemoryProfileTests(unittest.TestCase):
    def test_aggregation_never_calls_uncollected_heap_a_retained_heap_measurement(self):
        events = [dict(type='test', run=1, id='case', className='Example', methodName='allocates',
                       displayName='allocates', status='SUCCESSFUL', elapsedSeconds=1,
                       threadAllocatedBytes=1024, peakHeapBytes=2048, peakRssBytes=4096,
                       heapDeltaBytes=512, postGcHeapDeltaBytes=None),
                  dict(type='run', run=1, beforeHeapBytes=100, afterHeapBytes=300, gcObserved=False),
                  dict(type='finished', runs=1, failures=0, executed=1, skipped=0)]
        result = profile.analyse(events)
        self.assertTrue(result['complete'])
        self.assertEqual(2048, result['tests'][0]['peakHeapBytes'])
        self.assertEqual([], result['tests'][0]['postGcHeapDeltaBytes'])
        self.assertFalse(result['runFloors'][0]['gcObserved'])

    def test_partial_and_skipped_results_do_not_become_a_green_census(self):
        result = profile.analyse([dict(type='skipped', scope='class', id='disabled', reason='no ROM')])
        self.assertFalse(result['complete'])
        self.assertEqual([], result['tests'])
        self.assertEqual('no ROM', result['skipped'][0]['reason'])

    def test_unknown_selectors_fail_before_building(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, 'No test source'):
                profile.selectors(Path(temporary), ['Missing'])
            with self.assertRaises(ValueError):
                profile.selectors(Path(temporary), ['Test*'])

    def test_exact_selectors_are_qualified_and_ambiguity_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            a = root / 'src/test/java/a/TestSample.java'
            a.parent.mkdir(parents=True)
            a.write_text('package a; class TestSample {}')
            self.assertEqual(['a.TestSample#sample'], profile.selectors(root, ['TestSample#sample']))
            b = root / 'src/test/java/b/TestSample.java'
            b.parent.mkdir(parents=True)
            b.write_text('package b; class TestSample {}')
            with self.assertRaisesRegex(ValueError, 'ambiguous'):
                profile.selectors(root, ['TestSample'])
            self.assertEqual(['a.TestSample'], profile.selectors(root, ['a.TestSample']))

    def test_diagnostic_classpath_and_native_paths_are_worktree_local(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            diagnostic = root / 'target/profile'
            repository = root / 'repo/org/junit/platform'
            engine = repository / 'junit-platform-engine/1.10.3/junit-platform-engine-1.10.3.jar'
            launcher = repository / 'junit-platform-launcher/1.10.3/junit-platform-launcher-1.10.3.jar'
            launcher.parent.mkdir(parents=True)
            launcher.touch()
            mockito = root / 'mockito-core-5.0.jar'
            command, resolved = profile.diagnostic_command(root, diagnostic,
                    os.pathsep.join(map(str, [engine, mockito])), 'test', 3, 100, ['a.TestSample'])
            self.assertEqual(launcher, resolved)
            self.assertIn('-Xmx3g', command)
            self.assertIn('-Xshare:off', command)
            self.assertIn('-javaagent:' + str(mockito), command)
            self.assertIn('-Djava.io.tmpdir=' + str(diagnostic), command)
            cp = command[command.index('-cp') + 1].split(os.pathsep)
            self.assertIn(str(diagnostic / 'classes'), cp)
            self.assertIn(str(root / 'target/test-classes'), cp)
            self.assertEqual('a.TestSample', command[-1])

    def test_build_failure_replaces_stale_report_with_incomplete_and_cleans_temporary_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'target').mkdir()
            report = root / 'target/test-memory-report.json'
            report.write_text('{"complete":true}')
            with patch.object(profile.Path, 'cwd', return_value=root), \
                    patch.object(profile, 'selectors', return_value=['a.TestSample']), \
                    patch.object(profile, 'handle_termination', return_value=nullcontext()), \
                    patch.object(profile, 'maven_slot', return_value=nullcontext(42)), \
                    patch.object(profile, 'run_logged', return_value=7), \
                    patch.object(profile.subprocess, 'check_output', side_effect=['abc', '']):
                self.assertEqual(1, profile.main(['TestSample']))
            result = json.loads(report.read_text())
            self.assertFalse(result['complete'])
            self.assertEqual(1, result['commandExitCode'])
            self.assertEqual([], [p for p in (root / 'target').glob('test-memory-*') if p.is_dir()])

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK needed for diagnostic-tool control')
    def test_real_junit_reports_dynamic_skipped_and_deliberately_retained_memory(self):
        jars = list((Path.home() / '.m2/repository/org/junit/platform/junit-platform-console-standalone').glob(
            '*/junit-platform-console-standalone-*.jar'))
        if not jars:
            self.skipTest('Cached JUnit console jar needed for standalone control')
        # Use the version matching this project, not lexicographic jar ordering.
        jar = next((p for p in jars if p.parent.name == '1.10.3'), jars[0])
        source = Path(__file__).parent / 'java/com/openggf/tools/MemoryProfileLauncher.java'
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = root / 'MemoryFixture.java'
            fixture.write_text('''
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
public class MemoryFixture {
    static final List<byte[]> retained = new ArrayList<>();
    @Test void retainsOneMegabyte() { retained.add(new byte[1024 * 1024]); }
    @Test void temporaryAllocation() {
        byte[] allocation = new byte[2 * 1024 * 1024];
        Assertions.assertEquals(0, allocation[allocation.length - 1]);
    }
    @TestFactory Stream<DynamicTest> dynamicChildren() {
        return Stream.of(DynamicTest.dynamicTest("quoted \\" name", () -> Assertions.assertTrue(true)));
    }
    @Disabled("control skip") @Test void skipped() {}
}
''')
            compiled = subprocess.run(['javac', '--release', '21', '-cp', str(jar), '-d', str(root),
                                       str(source), str(fixture)], text=True, capture_output=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            output = root / 'events.jsonl'
            ran = subprocess.run(['java', '-Xmx128m', '-cp', str(root) + ':' + str(jar),
                                  'com.openggf.tools.MemoryProfileLauncher', str(output), 'test', '3', '50',
                                  'MemoryFixture'], text=True, capture_output=True, timeout=40)
            self.assertEqual(0, ran.returncode, ran.stdout + ran.stderr)
            events = [json.loads(line) for line in output.read_text().splitlines()]
            result = profile.analyse(events)
            self.assertTrue(result['complete'])
            self.assertEqual(3, result['summary']['runs'])
            self.assertEqual(9, result['summary']['executed'])
            self.assertEqual(3, result['summary']['skipped'])
            leak = next(t for t in result['tests'] if t['methodName'] == 'retainsOneMegabyte')
            self.assertGreaterEqual(min(leak['threadAllocatedBytes']), 1024 * 1024)
            self.assertTrue(all(r['gcObserved'] for r in result['runFloors']))
            floors = [r['afterHeapBytes'] for r in result['runFloors']]
            self.assertGreater(floors[-1] - floors[-2], 800 * 1024)
            self.assertTrue(any(t['displayName'] == 'quoted " name' for t in result['tests']))


if __name__ == '__main__':
    unittest.main()
