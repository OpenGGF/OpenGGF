"""Controls for the actual-Surefire class-boundary memory observer."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import profile_ordinary_memory as profile


class OrdinaryMemoryTests(unittest.TestCase):
    def test_shell_exec_into_maven_updates_process_role_without_losing_its_peak(self):
        observer=profile.ProcessObserver()
        observer.record_process(42,100,'other',300,0)
        observer.record_process(42,100,'maven',200,10)
        row=observer.processes['42:100']
        self.assertEqual('maven',row['kind'])
        self.assertEqual(300,row['peakRssBytes'])
        self.assertEqual(10,row['peakSwapBytes'])

    def test_cold_compile_preserves_reports_and_rejects_linked_outputs_before_deletion(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);target=root/'target';target.mkdir()
            (target/'classes').mkdir();(target/'classes'/'Old.class').touch()
            (target/'report.json').write_text('evidence')
            (target/'test-classes').symlink_to(root, target_is_directory=True)
            with self.assertRaises(RuntimeError): profile.clear_compiled_outputs(root)
            self.assertTrue((target/'classes'/'Old.class').exists())
            (target/'test-classes').unlink()
            profile.clear_compiled_outputs(root)
            self.assertFalse((target/'classes').exists())
            self.assertEqual('evidence',(target/'report.json').read_text())

    def test_gc_evidence_keeps_post_collection_peak_separate_from_pre_collection_churn(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'test-gc.log').write_text('[1.2s][info][gc] GC(7) Pause Young (Normal) (G1 Evacuation Pause) 2800M->30M(3072M) 2.5ms\n'
                                          '[1.3s][info][gc] GC(8) Pause Full (System.gc()) 40M->20M(100M) 12.0ms\n')
            result = profile.gc_summary(root)
            self.assertEqual(2, result['recordedCollections'])
            self.assertEqual(30*1024**2, result['highestAfterGc'][0]['afterBytes'])
            self.assertEqual(12, result['longestPauses'][0]['pauseMillis'])

    def test_growth_is_attributed_to_the_previous_class_and_partial_events_stay_partial(self):
        rows = [dict(type='boundary', afterClass='A', heapBytes=100, rssBytes=500, gcObserved=True),
                dict(type='boundary', afterClass='B', heapBytes=200, rssBytes=800, gcObserved=True)]
        result = profile.analyse(rows)
        self.assertEqual('B', result['largestHeapGrowth'][0]['afterClass'])
        self.assertEqual(100, result['largestHeapGrowth'][0]['heapGrowthBytes'])
        self.assertFalse(result['observerFinished'])

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK control')
    def test_real_junit_service_observes_between_class_floors_and_retained_memory(self):
        jar = Path.home() / '.m2/repository/org/junit/platform/junit-platform-console-standalone/1.10.3/junit-platform-console-standalone-1.10.3.jar'
        if not jar.is_file():
            self.skipTest('Cached JUnit console control required')
        source = Path(__file__).parent / 'java/com/openggf/tools/SuiteMemoryListener.java'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = root / 'Fixture.java'
            fixture.write_text('''
import java.util.*;
import org.junit.jupiter.api.*;
class Holder { static List<byte[]> keep = new ArrayList<>(); }
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AFirst { byte[] fixture = new byte[20 * 1024 * 1024];
    @Test void retains() { Holder.keep.add(new byte[1024 * 1024]); }
}
class BSecond { byte[] fixture = new byte[20 * 1024 * 1024];
    @Test void retains() { Assertions.assertEquals(20 * 1024 * 1024, fixture.length);
        Holder.keep.add(new byte[1024 * 1024]); }
}
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CThird { byte[] fixture = new byte[20 * 1024 * 1024];
    @Test void transientAllocation() { byte[] a = new byte[1024 * 1024]; Assertions.assertEquals(0, a[0]); }
}
''')
            compile = subprocess.run(['javac','--release','21','-cp',str(jar),'-d',str(root),str(source),
                                      str(source.with_name('MemoryProfileLauncher.java')),str(fixture)],capture_output=True,text=True)
            self.assertEqual(0,compile.returncode,compile.stderr)
            service = root / 'META-INF/services/org.junit.platform.launcher.TestExecutionListener'
            service.parent.mkdir(parents=True)
            service.write_text('com.openggf.tools.SuiteMemoryListener\n')
            events = root / 'events.jsonl'
            run = subprocess.run(['java','-Xmx128m','-Dopenggf.memory.events='+str(events),'-cp',str(root)+os.pathsep+str(jar),
                                  'org.junit.platform.console.ConsoleLauncher','execute','--disable-banner',
                                  '--select-class=AFirst','--select-class=BSecond','--select-class=CThird'],capture_output=True,text=True,timeout=40)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr)
            report = profile.analyse(json.loads(line) for line in events.read_text().splitlines())
            self.assertTrue(report['observerFinished'])
            self.assertEqual(3, len(report['classes']))
            self.assertEqual(3, len(report['tests']))
            boundaries=report['boundaries']
            self.assertGreater(boundaries[-1]['heapBytes']-boundaries[0]['heapBytes'],800*1024)
            self.assertGreater(boundaries[1]['heapBytes']-boundaries[0]['heapBytes'],20*1024**2,
                               'JUnit retains PER_CLASS fixtures inside the active plan')
            self.assertFalse(boundaries[1]['allPlansFinished'])
            self.assertLess(boundaries[2]['heapBytes']-boundaries[1]['heapBytes'],5*1024**2,
                            'Normal PER_METHOD fixtures should release before the next class')
            self.assertTrue(boundaries[-1]['allPlansFinished'])
            self.assertLess(boundaries[-1]['heapBytes']-boundaries[0]['heapBytes'],5*1024**2,
                            'Final post-plan floor must release fixtures but preserve static retained arrays')
            self.assertTrue(all(b['gcObserved'] for b in boundaries))

if __name__ == '__main__':
    unittest.main()
