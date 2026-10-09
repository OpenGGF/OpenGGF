#!/usr/bin/env python3
"""Offline safety checks for the Typing of the Hedgehog voice generator (voice lane, 2026-10-09).

No network, credential or DSP: catalogue validation, literal word checks, take selection,
the merge-safe manifest block and publication from a synthetic verified cache.
Run with python3 -m unittest discover -s tools/audio/tests -p test_typing_hedgehog_voice.py.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
import wave

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location('typing_hedgehog_voice', ROOT/'tools/audio/typing_hedgehog_voice.py')
VOICE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = VOICE
SPEC.loader.exec_module(VOICE)
CATALOGUE = ROOT/'examples/typing-of-the-hedgehog/voice-bank.json'


def bank():
    return json.loads(CATALOGUE.read_text(encoding='utf-8'))


class CatalogueChecks(unittest.TestCase):
    def test_shipped_catalogue_validates_and_taunts_match_the_word_table(self):
        catalogue = bank()
        VOICE.validate(catalogue, CATALOGUE)
        taunts = [e for e in catalogue['entries'] if 'taunt_tag' in e]
        self.assertGreaterEqual(len(taunts), 25)
        self.assertTrue(all(e['speaker'] == 'robotnik' and e['taunt_tag'] in ('any', 's1', 's2', 's3k') for e in taunts))

    def test_changed_taunt_text_is_rejected(self):
        catalogue = bank()
        next(e for e in catalogue['entries'] if 'taunt_tag' in e)['text'] += ' Extra'
        with self.assertRaises(SystemExit):
            VOICE.validate(catalogue, CATALOGUE)


class WordChecks(unittest.TestCase):
    aliases = bank()['word_aliases']

    def matches(self, heard, text, accept=()):
        return VOICE.matches_words(heard, {'text': text, 'accept': list(accept)}, self.aliases)

    def test_literal_equivalents_pass(self):
        self.assertTrue(self.matches('Rank S, outstanding.', 'Rank S! Outstanding!'))
        self.assertTrue(self.matches('Fingers on ASDF and JKL.', 'Fingers on A, S, D, F and J, K, L!'))
        self.assertTrue(self.matches('10 chain!', 'Ten chain!'))
        self.assertTrue(self.matches('Hohohoho!', 'Ho ho ho ho!'))
        self.assertTrue(self.matches('No! My beautiful machine!', 'Nooo! My beautiful machine!'))
        self.assertTrue(self.matches('Supersonic!', 'Super Sonic!'))

    def test_different_or_repeated_words_fail(self):
        self.assertFalse(self.matches('Rank as outstanding', 'Rank S! Outstanding!'))
        self.assertFalse(self.matches('You call that piping?', 'You call that typing?'))
        self.assertFalse(self.matches('Get ready! Get ready!', 'Get ready!'))
        self.assertTrue(self.matches('Rank ass', 'Rank S!', accept=['Rank ass']))

    def test_last_complete_utterance_is_selected(self):
        stamps = [{'word': w, 'start': i*.4, 'end': i*.4+.35}
                  for i, w in enumerate('You call that? You call that typing? My toaster types faster!'.split())]
        entry = {'text': 'You call that typing? My toaster types faster!'}
        self.assertEqual(VOICE.find_takes(stamps, entry, self.aliases), [(3, 10)])
        repeated = stamps[3:]+[dict(s, start=s['start']+4, end=s['end']+4) for s in stamps[3:]]
        self.assertEqual(VOICE.find_takes(repeated, entry, self.aliases), [(0, 7), (8, 15)])

    def test_degenerate_word_times_refuse_an_edit(self):
        timed = {'text': 'Rank S outstanding!', 'words': [{'word': w, 'start': 10.8, 'end': 10.8}
                                                              for w in ('Rank', 'S', 'outstanding!')]}
        with self.assertRaisesRegex(ValueError, 'degenerate'):
            VOICE.take_edit(Path('unused.mp3'), timed, {'text': 'Rank S! Outstanding!'}, self.aliases)


class Publication(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.bank = bank()
        self.bank['entries'] = [e for e in self.bank['entries'] if e['id'] in ('title', 'taunt_toaster')]
        for name, speaker in self.bank['speakers'].items():
            speaker.update(voice='test-voice', preset='clean')
        self.saved = dict(VOICE.APPROVED_PRESET_SHA256)
        VOICE.APPROVED_PRESET_SHA256['clean'] = VOICE.preset_hash(self.bank['presets']['clean'])
        self.output = self.root/'resources/audio/voice'
        self.output.mkdir(parents=True)
        for entry in self.bank['entries']:
            self.fake_line(entry, frames=7*VOICE.TICK_FRAMES+1)  # rounds up to 8 ticks

    def tearDown(self):
        VOICE.APPROVED_PRESET_SHA256.clear()
        VOICE.APPROVED_PRESET_SHA256.update(self.saved)
        self.temp.cleanup()

    def fake_line(self, entry, frames):
        key = VOICE.line_key(entry)
        folder = self.root/'cache/lines'/key
        folder.mkdir(parents=True)
        (folder/'raw.mp3').write_bytes(b'synthetic source '+key.encode())
        path = self.output/(key+'.wav')
        with wave.open(str(path), 'wb') as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(VOICE.RATE)
            handle.writeframes(b'\x00\x10'*frames)
        request = {'model': self.bank['model'], 'voice': 'test-voice', 'response_format': 'mp3',
                   'input': VOICE.request_input(self.bank, entry)}
        VOICE.write_json(folder/'request.json', {'state': 'complete', 'request': request,
                                                'source_sha256': hashlib.sha256((folder/'raw.mp3').read_bytes()).hexdigest()})
        VOICE.write_json(folder/'verification.json', {
            'frames': frames, 'seconds': frames/VOICE.RATE, 'peak_dbfs': -6.0,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'source_sha256': hashlib.sha256((folder/'raw.mp3').read_bytes()).hexdigest(),
            'preset': 'clean', 'preset_sha256': VOICE.preset_hash(self.bank['presets']['clean']),
            'transcript': entry['text'], 'matches_words': True, 'duration_ok': True})

    def test_publish_writes_enum_manifest_block_and_provenance(self):
        manifest = self.output.parent/'audio-manifest.yaml'
        enum = self.root/'java/typinghog/audio/VoiceLine.java'
        VOICE.publish(self.bank, self.root/'cache', self.output, enum)
        java = enum.read_text()
        self.assertIn('ANNOUNCER_TITLE(Speaker.ANNOUNCER, "voice-announcer-title", 8, 3, 0, null, '
                      '"The Typing of the Hedgehog!")', java)
        self.assertIn('ROBOTNIK_TAUNT_TOASTER(Speaker.ROBOTNIK, "voice-robotnik-taunt-toaster", 8, 3, 0, "any", '
                      '"You call that typing? My toaster types faster!")', java)
        self.assertIn('public enum Speaker { ANNOUNCER, ROBOTNIK, TAILS }', java)
        self.assertIn('assetPath: audio/voice/robotnik_taunt_toaster.wav', manifest.read_text())
        provenance = json.loads((self.output/'provenance.json').read_text())
        self.assertEqual([e['id'] for e in provenance['entries']], ['announcer_title', 'robotnik_taunt_toaster'])
        # A later publication replaces only the generated block.
        manifest.write_text(manifest.read_text().replace('sfx:\n', 'sfx:\n  - id: other-sfx\n    assetPath: audio/other.wav\n    gain: 1.0\n'))
        VOICE.publish(self.bank, self.root/'cache', self.output, enum)
        text = manifest.read_text()
        self.assertIn('id: other-sfx', text)
        self.assertEqual(text.count('id: voice-announcer-title'), 1)

    def test_unmarked_existing_manifest_is_not_overwritten(self):
        manifest = self.output.parent/'audio-manifest.yaml'
        manifest.write_text('formatVersion: 1\ntracks: []\nsfx: []\n')
        with self.assertRaises(SystemExit):
            VOICE.publish(self.bank, self.root/'cache', self.output, self.root/'VoiceLine.java')
        self.assertEqual(manifest.read_text(), 'formatVersion: 1\ntracks: []\nsfx: []\n')

    def test_unapproved_preset_or_changed_asset_refuses(self):
        changed = copy.deepcopy(self.bank)
        changed['presets']['clean'] = [{'effect': 'edge_fades', 'milliseconds': 6}]
        with self.assertRaises(SystemExit):
            VOICE.publish(changed, self.root/'cache', self.output, self.root/'VoiceLine.java')
        (self.output/'announcer_title.wav').write_bytes(b'tampered')
        with self.assertRaises(AssertionError):
            VOICE.publish(self.bank, self.root/'cache', self.output, self.root/'VoiceLine.java')


if __name__ == '__main__':
    unittest.main()
