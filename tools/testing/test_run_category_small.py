"""Automatic small jobs keep exact coverage and require positive qualification."""
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import maven_small as small


class SmallJobTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>')
        self.source = self.root / 'src/test/java/com/openggf/version/TestBuildIdentity.java'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('package com.openggf.version; class TestBuildIdentity {}')
        for mock in (patch.dict(os.environ, {}, clear=True),
                     patch.object(small.sys, 'platform', 'linux'),
                     patch.object(small, 'snapshot', return_value=(12 * 1024**3, 32, 2))):
            mock.start()
            self.addCleanup(mock.stop)

    def command(self, *args):
        return small.automatic_lean(['-Dmse=off', *(list(args) or ['-Dtest=TestBuildIdentity', 'test'])], self.root)

    def test_exact_qualified_classes_and_methods_get_bounded_heaps_and_same_selection(self):
        for selector in ('TestBuildIdentity', 'com.openggf.version.TestBuildIdentity',
                         'TestBuildIdentity#one+two'):
            original = ['-Dmse=off', '-Dtest=' + selector, 'test']
            args, environment = small.automatic_lean(original, self.root)
            self.assertEqual(original, args[:len(original)])
            self.assertEqual('-Xmx1g', environment['MAVEN_OPTS'])
            self.assertIn('-Xmx1g', next(a for a in args if a.startswith('-Dsurefire.argLine=')))
            self.assertIn('-Dsurefire.forkCount=1', args)

    def test_unqualified_ambiguous_missing_and_expensive_sources_keep_normal_shape(self):
        for selector in ('TestUnknown', 'TestBuild*', '!TestBuildIdentity',
                         'TestBuildIdentity#*', 'TestBuildIdentity,TestUnknown'):
            self.assertIsNone(self.command('-Dtest=' + selector, 'test'))
        duplicate = self.root / 'src/test/java/other/TestBuildIdentity.java'
        duplicate.parent.mkdir()
        duplicate.write_text('package other; class TestBuildIdentity {}')
        self.assertIsNone(self.command())
        self.assertIsNotNone(self.command('-Dtest=com.openggf.version.TestBuildIdentity', 'test'))
        for text in ('package wrong; class TestBuildIdentity {}',
                     'package com.openggf.version; @Tag("slow-suite") class TestBuildIdentity {}',
                     'package com.openggf.version; class TestBuildIdentity extends HeavyFixture {}'):
            self.source.write_text(text)
            self.assertIsNone(self.command('-Dtest=com.openggf.version.TestBuildIdentity', 'test'))

    def test_custom_invocations_profiles_and_probe_properties_do_not_become_small(self):
        for extra in ('-Pguards', '-Dprobe.enabled=true', '-Dsurefire.argLine=-Xmx8g',
                      '-Dmaven.compiler.fork=true', '--file=alternate.xml', '-Dtest=Other'):
            self.assertIsNone(self.command('-Dtest=TestBuildIdentity', extra, 'test'))
        for goal in ('package', 'surefire:test', 'test-compile'):
            self.assertIsNone(self.command('-Dtest=TestBuildIdentity', goal))
        for key in ('MAVEN_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MAVEN_ARGS'):
            with patch.dict(os.environ, {key: '-Xmx8g'}):
                self.assertIsNone(self.command())
                self.assertIsNone(small.batch_lean(['com/openggf/version/TestBuildIdentity.java'], self.root, workers=2))

    def test_prepared_batch_qualification_does_not_extend_focused_lifecycle_policy(self):
        with patch.object(small, 'QUALIFIED_CLASSES', frozenset()), \
                patch.object(small, 'BATCH_ONLY_CLASSES', frozenset({'com.openggf.version.TestBuildIdentity'})):
            candidates = ['com/openggf/version/TestBuildIdentity.java']
            self.assertIsNone(self.command())
            self.assertEqual(frozenset(candidates), small.qualified_candidates(candidates, self.root))
            self.assertIsNotNone(small.batch_lean(candidates, self.root))
            self.assertIsNotNone(small.batch_lean(candidates, self.root, workers=2))

    def test_caller_source_root_custom_jvm_config_fails_even_from_other_cwd(self):
        configuration = self.root / '.mvn/jvm.config'
        configuration.parent.mkdir()
        configuration.write_text('-Xmx8g')
        candidates = ['com/openggf/version/TestBuildIdentity.java']
        self.assertIsNone(self.command())
        self.assertEqual(frozenset(), small.qualified_candidates(candidates, self.root))
        self.assertIsNone(small.batch_lean(candidates, self.root, workers=2))

    def test_silent_extension_must_be_disabled_explicitly_and_unambiguously(self):
        for flags in ([], ['-Dmse=relaxed'], ['-Dmse=off', '-Dmse=relaxed'], ['-Dmse=off', '-Dmse=off']):
            self.assertIsNone(small.automatic_lean([*flags, '-Dtest=TestBuildIdentity', 'test'], self.root))

    def test_no_automatic_heap_reduction_without_shared_admission(self):
        for platform in ('darwin', 'win32'):
            with patch.object(small.sys, 'platform', platform):
                self.assertIsNone(self.command())
        with patch.dict(os.environ, {'OPENGGF_MAVEN_QUEUE': 'serial'}):
            self.assertIsNone(self.command())
        for resources in (None, (20 * 1024**3, 4, 0)):
            with patch.object(small, 'snapshot', return_value=resources):
                self.assertIsNone(self.command())

    def test_graphics_flag_is_preserved_and_unknown_value_is_not_eligible(self):
        flag = '-Dopenggf.headless.gl=surfaceless-egl'
        with patch.dict(os.environ, {'JDK_JAVA_OPTIONS': flag}):
            args, environment = self.command()
            self.assertEqual(flag, environment['JDK_JAVA_OPTIONS'])
        self.assertIsNotNone(self.command('-Dtest=TestBuildIdentity', flag, 'test'))
        self.assertIsNone(self.command('-Dtest=TestBuildIdentity', '-Dopenggf.headless.gl=unknown', 'test'))

    def test_category_shape_requires_every_candidate_and_one_or_two_workers(self):
        candidates = ['com/openggf/version/TestBuildIdentity.java']
        self.assertIsNotNone(small.batch_lean(candidates, self.root))
        command, environment = small.batch_lean(candidates, self.root, workers=2)
        self.assertIn('-Dsurefire.forkCount=2', command)
        self.assertNotIn('-Dsurefire.forkCount=1', command)
        self.assertEqual(1, command.count('-Ptest-concurrent'))
        self.assertEqual('-Xmx1g', environment['MAVEN_OPTS'])
        self.assertIsNone(small.batch_lean(candidates, self.root, workers=3))
        self.assertIsNone(small.batch_lean(candidates + ['com/openggf/Heavy.java'], self.root))
        self.assertIsNone(small.batch_lean(candidates * 51, self.root))

    def test_qualification_uses_callers_tree_and_missing_data_is_normal(self):
        self.source.unlink()
        self.assertIsNone(self.command())
        (self.root / 'pom.xml').write_text('invalid XML')
        self.assertIsNone(self.command())


if __name__ == '__main__':
    unittest.main()
