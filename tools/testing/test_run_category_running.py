"""Running-job usage leases, credit and bounded telemetry, without Maven."""
import json
from pathlib import Path
import tempfile
import time
import unittest

import maven_running as running
from maven_queue import _acquire, _unlock


class RunningLeaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.common = Path(self.temp.name)

    def lease(self, memory=7 * 1024**3, cores=8):
        lease = running.RunningLease(self.common, (memory, cores), _acquire)
        self.addCleanup(lease.remove)
        return lease

    def test_young_job_earns_no_credit_until_measured(self):
        self.lease()
        self.assertEqual((0, 0), running.live_credit(self.common, _acquire))

    def test_credit_is_capped_at_each_jobs_own_reservation(self):
        self.lease().publish(3 * 1024**3, 2.5)
        self.lease(memory=1024**3, cores=1).publish(5 * 1024**3, 4)
        self.assertEqual((4 * 1024**3, 3.5), running.live_credit(self.common, _acquire))

    def test_stale_or_malformed_records_earn_no_credit(self):
        lease = self.lease()
        lease.publish(3 * 1024**3, 2)
        later = time.time() + running.FRESH_SECONDS + 1
        self.assertEqual((0, 0), running.live_credit(self.common, _acquire, now=later))
        with lease.lock:
            lease.stream.seek(1)
            lease.stream.write(b'{"rss": ')
            lease.stream.truncate()
            lease.stream.flush()
        self.assertEqual((0, 0), running.live_credit(self.common, _acquire))
        lease.publish(float('nan'), 2)
        self.assertEqual((0, 0), running.live_credit(self.common, _acquire))

    def test_dead_holder_lease_is_pruned_without_credit(self):
        lease = self.lease()
        lease.publish(3 * 1024**3, 2)
        _unlock(lease.stream)  # The holder process died: its OS lock is gone.
        self.assertEqual((0, 0), running.live_credit(self.common, _acquire))
        self.assertFalse(lease.path.exists())
        unrelated = self.common / 'maven-running' / 'notes.txt'
        unrelated.write_text('kept')
        running.live_credit(self.common, _acquire)
        self.assertTrue(unrelated.exists())

    def test_sampler_keeps_exited_cpu_peak_rss_and_publishes(self):
        lease = self.lease()
        samples = iter([{(1, 1): (100, 2.0), (2, 1): (300, 1.0)}, {(1, 1): (200, 3.0)}])
        sampler = running.UsageSampler(lease, root_pid=0, usage=lambda pid: next(samples))
        sampler.sample()
        sampler.sample()
        self.assertEqual(400, sampler.peak_rss)
        self.assertEqual(4.0, sum(sampler.cpu_seen.values()))
        record = json.loads(lease.path.read_bytes()[1:])
        self.assertEqual(200, record['rss'])
        self.assertGreater(record['cores'], 0)

    def test_log_trims_to_newest_half_and_summarises_by_kind(self):
        for index in range(40):
            running.append_log(self.common, dict(kind='focused' if index % 2 else 'category', waitSeconds=index,
                                                 holdSeconds=10, peakRssGiB=4.5, meanCores=1.5))
        size = (self.common / running.LOG_NAME).stat().st_size
        running.trim_log(self.common, limit=size - 1)
        rows = [json.loads(line) for line in (self.common / running.LOG_NAME).read_text().splitlines()]
        self.assertLess(len(rows), 40)
        self.assertEqual(39, rows[-1]['waitSeconds'])
        summary = running.summarise(self.common)
        self.assertIn('focused', summary)
        self.assertIn('category', summary)
        self.assertEqual('No Maven queue telemetry recorded yet.', running.summarise(self.common / 'missing'))


if __name__ == '__main__':
    unittest.main()
