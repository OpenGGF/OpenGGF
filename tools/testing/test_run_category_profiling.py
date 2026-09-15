"""Check profiler aggregation without launching Java or requiring psutil."""
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import profile_maven


class ProfilingTests(unittest.TestCase):
    def test_summary_preserves_cpu_of_exited_children_and_peak_rss(self):
        def child(pid, created, cpu, rss):
            result = Mock()
            result.pid = pid
            result.create_time.return_value = created
            result.oneshot.return_value = unittest.mock.MagicMock()
            result.cpu_times.return_value = SimpleNamespace(user=cpu, system=0)
            result.memory_info.return_value = SimpleNamespace(rss=rss)
            return result

        first = child(123, 1, 5, 100)
        # PID reuse must not overwrite the exited child's accumulated CPU.
        second = child(123, 2, 3, 50)
        parent = Mock()
        parent.children.side_effect = [[first], [second]]
        psutil = SimpleNamespace(Process=lambda pid: parent, Error=RuntimeError,
                                 virtual_memory=lambda: SimpleNamespace(available=1000, total=2000),
                                 cpu_count=lambda: 4)
        process = Mock(pid=1234, returncode=7)
        polls = iter([None, None, 7])
        process.poll.side_effect = lambda: next(polls, 7)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'profile.json'
            with patch.dict(sys.modules, {'psutil': psutil}), \
                    patch.object(sys, 'argv', ['profile_maven.py', '--output', str(output), '--', '--category', 'all', '--run']), \
                    patch.object(profile_maven.subprocess, 'Popen', return_value=process), \
                    patch.object(profile_maven.subprocess, 'check_output', return_value='abc\n'), \
                    patch.object(profile_maven.time, 'sleep'):
                self.assertEqual(7, profile_maven.main())
            summary = json.loads(output.read_text())
            self.assertTrue(summary['complete'])
            self.assertEqual(7, summary['exit_code'])
            self.assertEqual(8, summary['sampled_cpu_seconds'])
            self.assertEqual(100, summary['peak_tree_rss_bytes'])
            self.assertEqual(2, summary['samples'])
            self.assertEqual('abc', summary['commit'])
            self.assertFalse(output.with_suffix('.json.tmp').exists())

    def test_percentile_empty_and_nearest_rank(self):
        self.assertEqual(0, profile_maven.percentile([], .95))
        self.assertEqual(19, profile_maven.percentile(list(range(1, 21)), .95))


if __name__ == '__main__':
    unittest.main()
