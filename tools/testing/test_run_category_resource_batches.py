"""Resource grouping must preserve selection and fail closed before lean execution."""
from collections import Counter
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import maven_small as small
import run_categories as runner
from maven_resources import GIB


class ResourceBatchTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.pom = self.root / 'pom.xml'
        self.pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>')
        self.candidate = 'com/openggf/version/TestBuildIdentity.java'
        self.source = self.root / 'src/test/java' / self.candidate
        self.source.parent.mkdir(parents=True)
        self.source.write_text('package com.openggf.version; class TestBuildIdentity {}')
        self.normal = 'com/openggf/Heavy.java'
        for mock in (patch.dict(os.environ, {}, clear=True),
                     patch.object(small.sys, 'platform', 'linux'),
                     patch.object(small, 'snapshot', return_value=(12 * GIB, 32, 2))):
            mock.start()
            self.addCleanup(mock.stop)

    def test_stable_pools_preserve_exact_multiset_with_duplicates_and_fifty_class_bound(self):
        selection = [self.normal, self.candidate, self.normal, self.candidate] * 31
        with patch.object(small, '_source_inventory', wraps=small._source_inventory) as inventory:
            batches = runner.ordinary_batches(selection, self.root)
        inventory.assert_called_once_with(self.root)
        self.assertEqual([50, 12, 50, 12], [len(batch) for batch in batches])
        flattened = sum(batches, [])
        self.assertEqual(Counter(selection), Counter(flattened))
        self.assertEqual([self.candidate] * 62 + [self.normal] * 62, flattened)
        self.assertEqual(batches, runner.ordinary_batches(selection, self.root))
        self.assertEqual([], runner.ordinary_batches([], self.root))

    def test_source_changed_after_classification_falls_back_to_normal_heap(self):
        self.assertEqual({self.candidate}, small.qualified_candidates([self.candidate], self.root))
        self.source.write_text('package com.openggf.version; @Tag("slow-suite") class TestBuildIdentity {}')
        self.assertIsNone(small.batch_lean([self.candidate], self.root))
        self.assertIsNone(small.batch_lean([self.candidate], self.root, workers=2))
        self.assertEqual(frozenset(), small.qualified_candidates([self.candidate], self.root))

    def test_environment_rejection_keeps_original_normal_batches(self):
        selection = [self.normal, self.candidate] * 27
        original = runner.ordinary_batches(selection)
        environments = [{'OPENGGF_MAVEN_QUEUE': 'serial'}, {'MAVEN_OPTS': '-Xmx8g'},
                        {'JAVA_TOOL_OPTIONS': '-Xmx8g'}, {'JDK_JAVA_OPTIONS': '-Xmx8g'},
                        {'MAVEN_ARGS': '-Pcustom'}, {'_JAVA_OPTIONS': '-Xmx8g'}]
        for environment in environments:
            with self.subTest(environment=environment), patch.dict(os.environ, environment):
                self.assertEqual(original, runner.ordinary_batches(selection, self.root))
                self.assertIsNone(small.batch_lean([self.candidate], self.root, workers=2))
        for resources in (None, (12 * GIB, 4, 0)):
            with patch.object(small, 'snapshot', return_value=resources):
                self.assertEqual(original, runner.ordinary_batches(selection, self.root))
        with patch.object(small.sys, 'platform', 'darwin'):
            self.assertEqual(original, runner.ordinary_batches(selection, self.root))

    def test_package_inheritance_symlink_and_missing_pom_remain_unqualified(self):
        for text in ('package wrong; class TestBuildIdentity {}',
                     'package com.openggf.version; class TestBuildIdentity extends Heavy {}',
                     'package com.openggf.version; @RequiresRom(Game.S1) class TestBuildIdentity {}',
                     'package com.openggf.version; @RequiresGameModule class TestBuildIdentity {}'):
            with self.subTest(text=text):
                self.source.write_text(text)
                self.assertFalse(small.qualified_candidates([self.candidate], self.root))
        target = self.root / 'actual.java'
        target.write_text('package com.openggf.version; class TestBuildIdentity {}')
        self.source.unlink()
        self.source.symlink_to(target)
        self.assertFalse(small.qualified_candidates([self.candidate], self.root))
        self.pom.unlink()
        self.assertFalse(small.qualified_candidates([self.candidate], self.root))

    def test_duplicate_fqcn_across_source_roots_is_not_small(self):
        extra = self.root / 'other-tests' / self.candidate
        extra.parent.mkdir(parents=True)
        extra.write_text(self.source.read_text())
        self.pom.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><build><plugins>
          <plugin><groupId>org.codehaus.mojo</groupId><artifactId>build-helper-maven-plugin</artifactId>
          <executions><execution><goals><goal>add-test-source</goal></goals><configuration>
          <sources><source>other-tests</source></sources></configuration></execution></executions>
          </plugin></plugins></build></project>''')
        self.assertFalse(small.qualified_candidates([self.candidate], self.root))

    def test_batch_only_heap_qualification_does_not_grant_short_backfill(self):
        with patch.object(small, 'QUALIFIED_CLASSES', frozenset()), \
                patch.object(small, 'BATCH_ONLY_CLASSES', frozenset({'com.openggf.version.TestBuildIdentity'})):
            self.assertIsNotNone(small.batch_lean([self.candidate], self.root))
            self.assertFalse(small.batch_backfill_eligible([self.candidate]))
            with patch.object(runner, 'needs_exclusive', return_value=False):
                options = runner.batch_admission_options(
                    dict(workers=1, low_heap=True, small_job=False), 'ordinary')
            self.assertEqual((4 * GIB, 4), options['reservation'])
            self.assertFalse(options['small_job'])
            self.assertFalse(options['exclusive'])
        self.assertTrue(small.batch_backfill_eligible([self.candidate]))
        self.assertFalse(small.batch_backfill_eligible([self.candidate, self.normal]))

    def test_qualified_pairs_share_reviewed_budget_without_backfill_and_guards_stay_normal(self):
        for workers in (1, 2):
            for low_heap in (False, True):
                plan = dict(workers=workers, low_heap=low_heap, small_job=low_heap and workers == 1)
                with patch.object(runner, 'needs_exclusive', return_value=False):
                    ordinary = runner.batch_admission_options(plan, 'ordinary')
                    guards = runner.batch_admission_options(plan, 'guards')
                small_job = low_heap and workers == 1
                self.assertEqual(small_job, ordinary['small_job'])
                self.assertEqual(((4 * GIB, 4) if workers == 1 else (5 * GIB, 8))
                                 if low_heap else None, ordinary['reservation'])
                self.assertEqual(workers == 2 and not low_heap, ordinary['exclusive'])
                self.assertFalse(guards['small_job'])
                self.assertIsNone(guards['reservation'])
                self.assertFalse(guards['exclusive'])
        with patch.object(runner, 'needs_exclusive', return_value=True):
            self.assertTrue(runner.batch_admission_options(
                dict(workers=1, small_job=True), 'ordinary')['exclusive'])

    def test_unknown_jvm_environment_keeps_qualified_pairs_conservative(self):
        with patch.dict(os.environ, {'_JAVA_OPTIONS': '-Xmx8g'}):
            options = runner.batch_admission_options(
                dict(workers=2, low_heap=True, small_job=False), 'ordinary')
        self.assertTrue(options['exclusive'])
        self.assertIsNone(options['reservation'])
        self.assertFalse(options['small_job'])

    def test_each_shared_pair_is_charged_its_full_reservation_with_existing_headroom(self):
        from maven_resources import admission_reason
        with patch.object(runner, 'needs_exclusive', return_value=False):
            requested = runner.batch_admission_options(
                dict(workers=2, low_heap=True, small_job=False), 'ordinary')['reservation']
        config = dict(memoryGiB=7, cpuCores=8, headroomGiB=2, maxRuns=3)
        self.assertEqual((5 * GIB, 8), requested)
        for available, expected in ((12 * GIB, 'ready'), (11 * GIB, 'memory')):
            result = admission_reason((available, 32, 0), config, 1,
                reservation=requested, reserved=(5 * GIB, 8))
            self.assertEqual(expected, result['reason'])
        result = admission_reason((16 * GIB, 32, 0), config, 2,
            reservation=requested, reserved=(10 * GIB, 16))
        self.assertEqual(GIB, result['memoryShortfallBytes'])
        self.assertEqual('memory', result['reason'])
        result = admission_reason((16 * GIB, 32, 0), config, 2,
            reservation=requested, reserved=(10 * GIB, 16), credit=(GIB, 0))
        self.assertEqual('ready', result['reason'])
        self.assertEqual('slots', admission_reason((100 * GIB, 64, 0), config, 3,
            reservation=requested, reserved=(15 * GIB, 24))['reason'])


if __name__ == '__main__':
    unittest.main()
