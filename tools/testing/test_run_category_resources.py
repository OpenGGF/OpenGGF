"""Resource admission arithmetic and configuration fail closed."""
from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch

import maven_resources as resources


class ResourceTests(unittest.TestCase):
    def setUp(self):
        self.policy = dict(maxRuns=2, memoryGiB=7, cpuCores=8, headroomGiB=2)

    def test_budgets_include_waiting_job_existing_reservations_and_headroom(self):
        self.assertTrue(resources.admits((16 * resources.GIB, 20, 4), self.policy, 1))
        self.assertFalse(resources.admits((16 * resources.GIB - 1, 20, 4), self.policy, 1))
        self.assertFalse(resources.admits((16 * resources.GIB, 20, 4.01), self.policy, 1))
        self.assertFalse(resources.admits((100 * resources.GIB, 100, 0), self.policy, 2))

    def test_unsupported_platform_and_missing_counters_fall_back(self):
        with patch.object(resources.sys, 'platform', 'win32'):
            self.assertIsNone(resources.snapshot())
        with patch.object(Path, 'read_text', side_effect=OSError('unavailable')):
            self.assertIsNone(resources.snapshot())

    @unittest.skipUnless(resources.sys.platform == 'linux', 'Linux cgroup counters')
    def test_cgroup_ancestor_limits_and_affinity_bound_host_resources(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            proc, cgroup = root / 'proc', root / 'cgroup'
            (proc / 'self').mkdir(parents=True)
            (proc / 'meminfo').write_text('MemAvailable: 10000000 kB\n')
            (proc / 'self/cgroup').write_text('0::/group/leaf\n')
            leaf = cgroup / 'group/leaf'
            leaf.mkdir(parents=True)
            (cgroup / 'cgroup.controllers').touch()
            (cgroup / 'group/memory.max').write_text('1000000')
            (cgroup / 'group/memory.current').write_text('400000')
            (cgroup / 'group/cpu.max').write_text('150000 100000')
            (leaf / 'memory.max').write_text('max')
            (leaf / 'cpu.max').write_text('max 100000')
            with patch.object(resources.os, 'sched_getaffinity', return_value={0, 1, 2, 3}), \
                    patch.object(resources.os, 'getloadavg', return_value=(.5, 0, 0)):
                self.assertEqual((600000, 1.5, .5), resources.snapshot(proc, cgroup))
            (proc / 'self/cgroup').write_text('0::/invisible\n')
            self.assertIsNone(resources.snapshot(proc, cgroup))
            (proc / 'self/cgroup').write_text('1:memory:/legacy\n')
            self.assertIsNone(resources.snapshot(proc, cgroup))

    def test_invalid_policy_does_not_silently_admit(self):
        for value in ('nan', 'inf', '-1', '0', 'invalid', '1.5', '65'):
            with self.subTest(value=value), patch.object(resources.subprocess, 'run') as run:
                run.return_value.returncode = 0
                run.return_value.stdout = value
                with self.assertRaises(ValueError):
                    resources.policy(Path.cwd())

    def test_unmeasured_maven_shapes_are_exclusive(self):
        from maven_queue import needs_exclusive
        with patch.dict(resources.os.environ, {}, clear=True):
            for args in (['-Dmse=off', 'test'], ['-Psmoke', 'test'], ['-P', 'guards', 'test']):
                self.assertFalse(needs_exclusive(args), args)
            for args in (['-Ptest-concurrent'], ['-P', 'trace-replay'], ['--activate-profiles=guards,custom'],
                         ['-Dsurefire.forkCount=2'], ['-DargLine=-Xmx8g'], ['-T2'], ['--threads', '2']):
                self.assertTrue(needs_exclusive(args), args)
            with patch.dict(resources.os.environ, {'MAVEN_OPTS': '-Xmx8g'}):
                self.assertTrue(needs_exclusive(['test']))

    def test_policy_defaults_and_git_errors(self):
        with patch.object(resources.subprocess, 'run') as run:
            run.return_value.returncode = 1
            self.assertEqual(self.policy, resources.policy(Path.cwd()))
            run.return_value.returncode = 128
            run.return_value.stderr = 'broken config'
            with self.assertRaisesRegex(ValueError, 'broken config'):
                resources.policy(Path.cwd())


if __name__ == '__main__':
    unittest.main()
