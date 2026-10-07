#!/usr/bin/env python3
"""Safety checks for the S1 song-form research interpreter (Sitar Hero, 2026-10-07).

Generated control streams test native duration/control rules, not any song/audio asset.
Run with python3 -m unittest discover -s tools/audio/tests -p test_s1_song_forms.py.
"""
import importlib.util
from pathlib import Path
import sys
import unittest

SPEC = importlib.util.spec_from_file_location('s1_song_forms', Path(__file__).resolve().parents[1] / 's1_song_forms.py')
FORMS = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = FORMS
SPEC.loader.exec_module(FORMS)


def control_rom(stream):
    """One DAC track, no instruments: generated test input, never a runtime asset."""
    data = bytearray(FORMS.MUSIC_TABLE + 4)
    base = 0x100
    data[FORMS.MUSIC_TABLE:FORMS.MUSIC_TABLE+4] = base.to_bytes(4, 'big')
    data[base:base+10] = bytes([0, 0, 1, 0, 1, 3, 0, 10, 0, 0])
    data[base+10:base+10+len(stream)] = bytes(stream)
    return data


class SongFormsSafety(unittest.TestCase):
    def test_saved_duration_is_scaled_only_when_an_explicit_byte_is_read(self):
        # SetDuration raw8/divider1 -> saved8. E5 divider2 does not alter
        # SavedDuration; the following note reuses8, not8*2 (native SD:384-387).
        source = control_rom([0x81, 8, 0xE5, 2, 0x81, 0xF2])
        result = FORMS.survey(source, 0x81, 100, events=True)
        self.assertEqual(24, result['end_frame'])
        self.assertEqual([0, 12], [a['frame'] for a in result['tracks'][0]['events']])
        self.assertEqual(16, result['tracks'][0]['end_scaled'])

    def test_new_duration_after_a_divider_change_is_scaled(self):
        result = FORMS.survey(control_rom([0x81, 8, 0xE5, 2, 0x81, 8, 0xF2]), 0x81, 100)
        self.assertEqual(36, result['end_frame'])
        self.assertEqual(24, result['tracks'][0]['end_scaled'])

    def test_finite_pattern_repeat_is_not_a_song_loop(self):
        # Four repetitions of one8-unit hit, then the native stop command.
        result = FORMS.survey(control_rom([0x81, 8, 0xF7, 0, 4, 0xFF, 0xFB, 0xF2]), 0x81, 100)
        self.assertEqual(48, result['end_frame'])
        self.assertEqual(4, result['tracks'][0]['attacks'])
        self.assertEqual([], result['tracks'][0]['jumps'])

    def test_bounded_prefix_does_not_claim_a_natural_end(self):
        result = FORMS.survey(control_rom([0x81, 8, 0xF2]), 0x81, 1)
        self.assertIsNone(result['end_frame'])
        self.assertIsNone(result['tracks'][0]['stop_frame'])


if __name__ == '__main__':
    unittest.main()
