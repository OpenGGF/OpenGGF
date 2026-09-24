"""Resource admission arithmetic and configuration fail closed."""
from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch

import maven_resources as resources


class ResourceTests(unittest.TestCase):
    def setUp(self):
        self.policy = dict(maxRuns=3, memoryGiB=7, cpuCores=8, headroomGiB=2)

    def test_budgets_include_waiting_job_existing_reservations_and_headroom(self):
        self.assertTrue(resources.admits((16 * resources.GIB, 20, 4), self.policy, 1))
        self.assertFalse(resources.admits((16 * resources.GIB - 1, 20, 4), self.policy, 1))
        self.assertFalse(resources.admits((16 * resources.GIB, 20, 4.01), self.policy, 1))
        self.assertFalse(resources.admits((100 * resources.GIB, 100, 0), self.policy, 3))

    def test_realised_usage_of_active_runs_is_not_counted_twice(self):
        # One active run already using 5 GiB and 3 cores: without credit the
        # 15 GiB left looks too small for a second 7 GiB reservation plus headroom.
        available = 15 * resources.GIB
        self.assertFalse(resources.admits((available, 20, 3), self.policy, 1))
        self.assertTrue(resources.admits((available, 20, 3), self.policy, 1, (5 * resources.GIB, 3)))
        self.assertFalse(resources.admits((available - 5 * resources.GIB, 20, 3), self.policy, 1,
                                          (5 * resources.GIB, 3)))

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
            for args in (['-Dmse=off', 'test'], ['-Psmoke', 'test'], ['-P', 'guards', 'test'],
                         ['-Ptrace-replay', 'test'], ['--activate-profiles=trace-segments,guards']):
                self.assertFalse(needs_exclusive(args), args)
            for args in (['-Ptest-concurrent'], ['-P', 'benchmarks'], ['-Paudio-stress'],
                         ['-Ptracechaser-integration'], ['-P'], ['--activate-profiles=guards,custom'],
                         ['-Dsurefire.forkCount=2'], ['-DargLine=-Xmx8g'], ['-T2'], ['--threads', '2']):
                self.assertTrue(needs_exclusive(args), args)
            with patch.dict(resources.os.environ, {'MAVEN_OPTS': '-Xmx8g'}):
                self.assertTrue(needs_exclusive(['test']))

    def test_shared_profiles_keep_the_measured_single_fork_shape(self):
        """Admitting a profile under the default reservation needs one <=3 GiB fork."""
        import re
        from maven_queue import SHARED_PROFILES
        pom = (Path(__file__).resolve().parents[2] / 'pom.xml').read_text()
        root_properties = pom[pom.index('<properties>'):pom.index('</properties>')]
        self.assertIn('<surefire.forkCount>1</surefire.forkCount>', root_properties)
        self.assertRegex(root_properties, r'<surefire.argLine>[^<]*-Xmx3g</surefire.argLine>')
        bodies = {re.search(r'<id>(.*?)</id>', body).group(1): body
                  for body in re.findall(r'<profile>(.*?)</profile>', pom, re.S)}
        for profile in SHARED_PROFILES:
            with self.subTest(profile=profile):
                body = bodies[profile]
                self.assertNotRegex(body, r'<(parallel|threadCount|forkedProcessExitTimeoutInSeconds)>')
                for value in re.findall(r'<(?:surefire\.)?forkCount>([^<]*)</', body):
                    self.assertIn(value, ('1', '${surefire.forkCount}'))
                for amount, unit in re.findall(r'-Xmx(\d+)([gGmM])', body):
                    self.assertLessEqual(int(amount) * (1024 if unit in 'gG' else 1), 3 * 1024)
                self.assertNotIn('exec-maven-plugin', body)

    @unittest.skipUnless(resources.sys.platform == 'linux', 'Linux /proc')
    def test_tree_usage_sums_descendants_only(self):
        with tempfile.TemporaryDirectory() as temporary:
            proc = Path(temporary)
            def stat(pid, ppid, comm, utime, rss_pages, start=100):
                fields = ['S', str(ppid)] + ['0'] * 9 + [str(utime), '0'] + ['0'] * 6 + [str(start), '0', str(rss_pages)]
                (proc / str(pid)).mkdir()
                (proc / str(pid) / 'stat').write_text(f'{pid} ({comm}) ' + ' '.join(fields) + '\n')
            stat(10, 1, 'python3', 5, 1)
            stat(11, 10, 'java (mvn) x', 200, 1000)
            stat(12, 11, 'java', 300, 2000, start=150)
            stat(13, 1, 'unrelated', 999, 9999)
            (proc / 'self').mkdir()
            usage = resources.tree_usage(10, proc)
            page, ticks = resources.os.sysconf('SC_PAGE_SIZE'), resources.os.sysconf('SC_CLK_TCK')
            self.assertEqual({(11, 100): (1000 * page, 200 / ticks), (12, 150): (2000 * page, 300 / ticks)}, usage)

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
