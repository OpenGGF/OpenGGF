"""Build routing must never admit a command that may execute tests unqueued."""
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import maven_queue


class BuildRoutingTests(unittest.TestCase):
    def test_lifecycle_and_skip_options_distinguish_builds_from_tests(self):
        cases = [
            (['clean', 'compile'], True),
            (['test-compile'], True),
            (['dependency:build-classpath'], True),
            (['-B', '-q', '-Dmse=off', '-DskipTests', 'compile',
              'dependency:build-classpath', '-Dmdep.outputFile=target/examples-classpath.txt'], True),
            (['compile', 'dependency:build-classpath'], True),
            (['-B', '-ntp', '-P', 'smoke', 'compile'], True),
            (['package', '-DskipTests'], True),
            (['clean', 'package', '-Dmaven.test.skip=true'], True),
            (['-Pnative', 'package', '--define', 'skipTests=true'], True),
            (['-T', '2', 'package', '-D', 'skipTests=true'], True),
            (['test', '-DskipTests=true'], True),
            (['package'], False),
            (['-Dtest=Probe', 'test'], False),
            (['-Pguards', 'test'], False),
            (['package', '-DskipTests=false'], False),
            (['package', '-DskipTests', '-DskipTests=false'], False),
            (['package', '-DskipTests=false', '-DskipTests=true'], True),
            (['verify', '-DskipITs'], False),
            (['surefire:test', '-DskipTests'], False),
            (['failsafe:integration-test', '-Dmaven.test.skip=true'], False),
            (['exec:java'], False),
            (['-Pcustom', 'compile'], False),
            (['-f', 'other/pom.xml', 'compile'], False),
            (['--file=other/pom.xml', 'compile'], False),
            (['compile', 'test'], False),
            (['dependency:build-classpath', 'test'], False),
            (['dependency:build-classpath', 'package'], False),
            (['dependency:build-classpath', 'package', '-DskipTests=false'], False),
            (['dependency:build-classpath', 'surefire:test', '-DskipTests'], False),
            (['dependency:build-classpath', 'exec:java', '-DskipTests'], False),
            (['dependency:unpack', '-DskipTests'], False),
            (['-Pcustom', 'compile', 'dependency:build-classpath'], False),
            (['-f', 'other/pom.xml', 'compile', 'dependency:build-classpath'], False),
            (['compile', 'mystery:run', '-DskipTests'], False),
            (['-P'], False),
            (['-D'], False),
        ]
        with patch.dict(os.environ, {}, clear=True):
            for args, expected in cases:
                with self.subTest(args=args):
                    self.assertEqual(expected, maven_queue.build_only(args))

    def test_custom_maven_launch_arguments_cannot_hide_test_goals(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {}, clear=True), \
                patch.object(Path, 'cwd', return_value=Path(temporary)):
            config = Path(temporary) / '.mvn/maven.config'
            config.parent.mkdir()
            config.write_text('-Dmse=relaxed\n')
            self.assertTrue(maven_queue.build_only(['compile']))
            config.write_text('test\n')
            self.assertFalse(maven_queue.build_only(['compile']))
            config.write_text('-Dmse=relaxed\n')
            with patch.dict(os.environ, {'MAVEN_ARGS': 'test'}):
                self.assertFalse(maven_queue.build_only(['compile']))
