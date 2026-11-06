"""Catalogue parsing and SRV drift detection for racing/deploy/master_channels.py; no network."""
import unittest
from pathlib import Path

import master_channels as mc

CATALOGUE = Path(__file__).resolve().with_name('master-channels.properties')


class MasterChannelTests(unittest.TestCase):
    def setUp(self):
        self.channels = mc.parse_catalogue(CATALOGUE.read_text(encoding='utf-8'))

    def test_committed_catalogue_and_endpoints(self):
        self.assertEqual({'master': ('multi.openggf.com', 27900),
                          'develop': ('multi-dev.openggf.com', 27910),
                          'next': ('multi-next.openggf.com', 27920)}, self.channels)
        self.assertEqual('wss://multi-dev.openggf.com:27910/master', mc.endpoint(self.channels, 'develop'))
        self.assertEqual('wss://multi-next.openggf.com:27920/master', mc.endpoint(self.channels, 'next'))
        with self.assertRaisesRegex(ValueError, 'unknown multiplayer channel'):
            mc.endpoint(self.channels, 'feature/ai-x')

    def test_matching_published_record_has_no_drift(self):
        records = mc.parse_dig('0 0 27910 multi-dev.openggf.com.\n')
        self.assertEqual([(0, 0, 27910, 'multi-dev.openggf.com')], records)
        self.assertEqual([], mc.drift(self.channels, 'develop', records))

    def test_drift_is_reported(self):
        # Broken cases: a mutated port, a foreign target and a missing record all fail.
        self.assertTrue(mc.drift(self.channels, 'develop', mc.parse_dig('0 0 27911 multi-dev.openggf.com.\n')))
        self.assertTrue(mc.drift(self.channels, 'next', mc.parse_dig('0 0 27920 vidya.jamesj999.co.uk.\n')))
        self.assertTrue(mc.drift(self.channels, 'develop', mc.parse_dig('')))
        with self.assertRaisesRegex(ValueError, 'unexpected DNS answer'):
            mc.parse_dig(';; connection timed out; no servers could be reached\n')

    def test_malformed_catalogues_are_rejected(self):
        for text in ('develop=multi-dev.openggf.com\n', 'develop=multi-dev.openggf.com:70000\n',
                     'develop=a.example:1\ndevelop=b.example:2\n', '# empty\n', 'develop=localhost:1\n'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                mc.parse_catalogue(text)


if __name__ == '__main__':
    unittest.main()
