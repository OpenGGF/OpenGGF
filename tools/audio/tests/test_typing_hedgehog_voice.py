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

    def test_letters_join_before_collapsing(self):
        self.assertTrue(self.matches('Prepare to be outtyped!', 'Prepare to be out-typed!'))
        self.assertTrue(self.matches('Nobody defies the genius of Dr. Robotnik!', 'Nobody defies the genius of Doctor Robotnik!'))
        self.assertTrue(self.matches('Rank D. Keep practicing.', 'Rank D. Keep practising.'))
        stamps = [{'word': w, 'start': i*.4, 'end': i*.4+.35} for i, w in enumerate('Prepare to be outtyped!'.split())]
        self.assertEqual(VOICE.find_takes(stamps, {'text': 'Prepare to be out-typed!'}, self.aliases), [(0, 3)])

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
        self.bank['shipping'] = {'format': 'wav'}  # no FFmpeg needed for the synthetic cache
        self.bank['entries'] = [e for e in self.bank['entries'] if e['id'] in ('title', 'taunt_toaster')]
        self.speakers = ['announcer', 'robotnik']
        self.output = self.root/'resources/audio/voice'
        for entry in self.bank['entries']:
            self.fake_line(entry, frames=7*VOICE.TICK_FRAMES+1)  # rounds up to 8 ticks

    def tearDown(self):
        self.temp.cleanup()

    def publish(self, bank=None, enum=None):
        VOICE.publish(bank or self.bank, self.root/'cache', self.output, enum or self.root/'VoiceLine.java', self.speakers)

    def fake_line(self, entry, frames, retro_transcript=None):
        key = VOICE.line_key(entry)
        folder = VOICE.line_folder(self.root/'cache', entry)
        folder.mkdir(parents=True, exist_ok=True)
        (folder/'raw.mp3').write_bytes(b'synthetic source '+key.encode())
        shipped = folder/'shipped.wav'
        with wave.open(str(shipped), 'wb') as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(VOICE.RATE)
            handle.writeframes(b'\x00\x10'*frames)
        speaker = self.bank['speakers'][entry['speaker']]
        request = {'model': self.bank['model'], 'voice': speaker['voice'], 'response_format': 'mp3',
                   'input': VOICE.request_input(self.bank, entry)}
        VOICE.write_json(folder/'request.json', {'state': 'complete', 'request': request,
                                                'source_sha256': hashlib.sha256((folder/'raw.mp3').read_bytes()).hexdigest()})
        VOICE.write_json(folder/'verification.json', {
            'frames': frames, 'seconds': frames/VOICE.RATE, 'peak_dbfs': -6.0, 'format': 'wav',
            'sha256': hashlib.sha256(shipped.read_bytes()).hexdigest(), 'clean_wav_sha256': 'c', 'gate_sha256': 'g',
            'source_sha256': hashlib.sha256((folder/'raw.mp3').read_bytes()).hexdigest(),
            'preset': speaker['preset'], 'preset_sha256': VOICE.canonical_hash(self.bank['presets'][speaker['preset']]),
            'runtime_variant_sha256': VOICE.runtime_variant_hash(self.bank),
            'clean_transcript': entry['text'], 'retro_transcript': retro_transcript or entry['text']})

    def test_publish_writes_enum_manifest_blocks_and_provenance(self):
        manifest = self.output.parent/'audio-manifest.yaml'
        enum = self.root/'java/typinghog/audio/VoiceLine.java'
        (self.output).mkdir(parents=True)
        (self.output/'stale_take.ogg').write_bytes(b'unpublished')
        self.publish(enum=enum)
        java = enum.read_text()
        self.assertIn('ANNOUNCER_TITLE("ANNOUNCER", "voice-announcer-title", 8, 3, 0, null, '
                      '"The Typing of the Hedgehog!")', java)
        self.assertIn('ROBOTNIK_TAUNT_TOASTER("ROBOTNIK", "voice-robotnik-taunt-toaster", 8, 3, 0, "any", '
                      '"You call that typing? My toaster types faster!")', java)
        self.assertIn('public enum Speaker { ANNOUNCER, ROBOTNIK, TAILS }', java)
        self.assertIn('public static final String RETRO_SUFFIX = "-md";', java)
        self.assertIn('return new Announcer.Clip(asset, durationTicks, priority, cooldownTicks);', java)
        self.assertNotIn('static {', java)
        self.assertNotIn('HashMap', java)
        self.assertIn('public Speaker speaker() {', java)
        text = manifest.read_text()
        self.assertIn('  - id: voice-robotnik-taunt-toaster\n    assetPath: audio/voice/robotnik_taunt_toaster.wav\n'
                      '    gain: 1.0\n  - id: voice-robotnik-taunt-toaster-md\n'
                      '    assetPath: audio/voice/robotnik_taunt_toaster.wav\n    gain: 1.0\n    dac:\n'
                      '      sampleRate: 8000\n      bits: 7\n      lowpassHz: 3400\n      recoveryMix: 0.1\n'
                      '      recoveryHighpassHz: 2400\n', text)
        self.assertEqual(sorted(p.name for p in self.output.iterdir()),
                         ['announcer_title.wav', 'provenance.json', 'robotnik_taunt_toaster.wav'])
        provenance = json.loads((self.output/'provenance.json').read_text())
        self.assertEqual([e['id'] for e in provenance['entries']], ['announcer_title', 'robotnik_taunt_toaster'])
        self.assertEqual(provenance['entries'][0]['retro_asset'], 'voice-announcer-title-md')
        # A later publication replaces only the generated blocks.
        manifest.write_text(text.replace('sfx:\n', 'sfx:\n  - id: other-sfx\n    assetPath: audio/other.wav\n    gain: 1.0\n'))
        self.publish(enum=enum)
        text = manifest.read_text()
        self.assertIn('id: other-sfx', text)
        self.assertEqual(text.count('id: voice-announcer-title\n'), 1)
        self.assertEqual(text.count('id: voice-announcer-title-md'), 1)

    def test_retro_mishearing_needs_a_recorded_acceptance(self):
        entry = self.bank['entries'][1]
        self.fake_line(entry, frames=4000, retro_transcript='You call that piping? My toaster types faster!')
        with self.assertRaisesRegex(AssertionError, 'retro gate misheard'):
            self.publish()
        entry['retro_mishearing_accepted'] = 'clean render passes; DAC blurs t/p'
        self.publish()
        record = json.loads((self.output/'provenance.json').read_text())['entries'][1]
        self.assertFalse(record['retro_gate_word_check'])
        self.assertEqual(record['retro_mishearing_accepted'], 'clean render passes; DAC blurs t/p')

    def test_unmarked_existing_manifest_is_not_overwritten(self):
        self.output.mkdir(parents=True)
        manifest = self.output.parent/'audio-manifest.yaml'
        manifest.write_text('formatVersion: 1\ntracks: []\nsfx: []\n')
        with self.assertRaises(SystemExit):
            self.publish()
        self.assertEqual(manifest.read_text(), 'formatVersion: 1\ntracks: []\nsfx: []\n')

    def test_unapproved_recipe_or_changed_source_refuses(self):
        changed = copy.deepcopy(self.bank)
        changed['presets']['clean'] = [{'effect': 'edge_fades', 'milliseconds': 6}]
        with self.assertRaises(SystemExit):
            self.publish(changed)
        changed = copy.deepcopy(self.bank)
        changed['runtime_variant']['dac']['bits'] = 6
        with self.assertRaises(SystemExit):
            self.publish(changed)
        unchosen = copy.deepcopy(self.bank)
        unchosen['speakers']['tails']['voice'] = None
        with self.assertRaises(SystemExit):
            VOICE.publish(unchosen, self.root/'cache', self.output, self.root/'VoiceLine.java', ['tails'])
        folder = VOICE.line_folder(self.root/'cache', self.bank['entries'][0])
        (folder/'shipped.wav').write_bytes(b'tampered')
        with self.assertRaisesRegex(AssertionError, 'shipped file changed'):
            self.publish()


if __name__ == '__main__':
    unittest.main()
