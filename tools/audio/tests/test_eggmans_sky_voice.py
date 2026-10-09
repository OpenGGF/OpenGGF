#!/usr/bin/env python3
"""Offline voice publication checks using generated PCM and real Vorbis encoding.

Origin: Eggman's Sky WAV-to-Vorbis delivery, 2026-10-09. No API requests.
Run with a NumPy/SciPy environment: python -m unittest discover -s tools/audio/tests
-p test_eggmans_sky_voice.py. Requires FFmpeg.
"""
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import numpy as np

SPEC = importlib.util.spec_from_file_location(
    'eggmans_sky_voice', Path(__file__).resolve().parents[1] / 'eggmans_sky_voice.py')
VOICE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VOICE)


class VoicePublication(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        root = Path(self.work.name)
        self.cache = root / 'cache'
        self.output = root / 'audio' / 'voice'
        self.output.mkdir(parents=True)
        folder = self.cache / 'test_line'
        folder.mkdir(parents=True)
        self.master = folder / 'processed.wav'
        self.frames = 9606  # Not an integral 60 Hz queue lease.
        samples = .15 * np.sin(2 * np.pi * 440 * np.arange(self.frames) / VOICE.FINAL_RATE)
        report = VOICE.wav(self.master, samples)
        self.master_bytes = self.master.read_bytes()
        raw = folder / 'raw.mp3'
        raw.write_bytes(b'original-source-identity')
        report.update(source_sha256=hashlib.sha256(raw.read_bytes()).hexdigest(),
                      transcript='Systems online.', matches_words=True)
        self.report = report
        VOICE.write_json(folder / 'verification.json', report)
        VOICE.write_json(folder / 'edit.json', {'whole_utterance': 'Systems online.'})
        # Publication also migrates a legacy shipped WAV after preserving its master.
        shutil.copyfile(self.master, self.output / 'test_line.wav')
        self.enum = root / 'VoiceLine.java'
        self.bank = dict(format_version=1, model='generated-control', voice='test',
                         delivery_tag='', processing=[], seed=0, room_reverb_mix=0,
                         entries=[dict(id='test_line', text='Systems online.',
                                       priority=1, cooldown_ticks=0)])

    def publish(self):
        VOICE.publish(self.bank, self.cache, self.output, self.enum)

    def test_publishes_only_vorbis_with_exact_duration_and_distinct_master_identity(self):
        self.publish()
        asset = self.output / 'test_line.ogg'
        self.assertTrue(asset.exists(), 'publication must ship Vorbis instead of WAV')
        self.assertFalse((self.output / 'test_line.wav').exists())
        self.assertEqual(self.master_bytes, self.master.read_bytes())
        decoded = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(asset),
                                           '-f', 's16le', 'pipe:1'])
        self.assertTrue(any(decoded), 'the encoded control remains audible')
        stream = json.loads(subprocess.check_output(['ffprobe', '-v', 'error',
            '-show_entries', 'stream=duration_ts', '-of', 'json', str(asset)]))['streams'][0]
        self.assertEqual(self.frames, stream['duration_ts'])
        metadata = json.loads((self.output / 'provenance.json').read_text())
        entry = metadata['entries'][0]
        self.assertEqual('vorbis', metadata['output']['codec'])
        self.assertEqual(4, metadata['output']['vorbis_quality'])
        self.assertEqual(self.frames, entry['frames'])
        self.assertEqual(hashlib.sha256(asset.read_bytes()).hexdigest(), entry['sha256'])
        self.assertEqual(self.report['sha256'], entry['processed_wav_sha256'])
        self.assertEqual(entry['processed_wav_sha256'], entry['blind_word_check_sha256'])
        self.assertIn('audio/voice/test_line.ogg', (self.output.parent / 'audio-manifest.yaml').read_text())
        self.assertIn('"voice-test-line", 13,', self.enum.read_text())
        first = asset.read_bytes()
        self.publish()
        self.assertEqual(first, asset.read_bytes(), 'same encoder and master must retain asset identity')

    def test_rejects_changed_master_before_replacing_published_audio(self):
        self.master.write_bytes(self.master_bytes + b'changed')
        with self.assertRaisesRegex(AssertionError, 'asset changed'):
            self.publish()
        self.assertFalse((self.output / 'test_line.ogg').exists())
        self.assertEqual(self.master_bytes, (self.output / 'test_line.wav').read_bytes())

    def test_rejects_unverified_words_without_publishing(self):
        self.report['matches_words'] = False
        VOICE.write_json(self.cache / 'test_line' / 'verification.json', self.report)
        with self.assertRaisesRegex(AssertionError, 'word check not passed'):
            self.publish()
        self.assertFalse((self.output / 'test_line.ogg').exists())

    def test_later_failed_check_preserves_the_entire_existing_bank(self):
        self.bank['entries'].append(dict(self.bank['entries'][0], id='second_line'))
        shutil.copytree(self.cache / 'test_line', self.cache / 'second_line')
        self.report['matches_words'] = False
        VOICE.write_json(self.cache / 'second_line' / 'verification.json', self.report)
        previous = self.output / 'test_line.ogg'
        previous.write_bytes(b'previous-published-identity')
        with self.assertRaisesRegex(AssertionError, 'second_line: word check not passed'):
            self.publish()
        self.assertEqual(b'previous-published-identity', previous.read_bytes())
        self.assertFalse((self.output / 'second_line.ogg').exists())
        self.assertFalse(self.enum.exists())


if __name__ == '__main__':
    unittest.main()
