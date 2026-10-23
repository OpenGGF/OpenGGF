"""Promo compositor contracts: beat clock, pixel scaling, transitions, typography, pacing and mix.
Synthetic inputs only; no ROM, display or engine. Run: python3 -m unittest tools/media/test_promo_kit.py"""
import importlib.util
from pathlib import Path
import tempfile
import unittest

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('promo_kit', Path(__file__).with_name('promo_kit.py'))
kit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(kit)


def has_font():
    try:
        kit.font_path('title')
        return True
    except FileNotFoundError:
        return False


class SmallEdit(kit.Edit):
    def __init__(self, **kw):
        super().__init__(size=(160, 90), **kw)


class BeatClock(unittest.TestCase):
    def test_beats_map_to_rounded_frames(self):
        b = kit.Beats(171.107, 0.316, 60)
        self.assertEqual(b.bf(0), 19)
        self.assertAlmostEqual(b.bt(4) - b.bt(0), 4 * 60 / 171.107)
        self.assertEqual(b.bf(16), round((0.316 + 16 * 60 / 171.107) * 60))

    def test_tempo_recovers_a_click_track(self):
        with tempfile.TemporaryDirectory() as d:
            wav = kit.synth_click(Path(d) / 'click.wav', 132, 24, first=0.4)
            x = kit.read_wav(wav).mean(axis=1)
        bpm, first = kit.tempo(x)
        self.assertAlmostEqual(bpm, 132, delta=0.8)
        period = 60 / 132
        self.assertLess(min(abs(first - 0.4) % period, period - abs(first - 0.4) % period), 0.03)


class PixelScaling(unittest.TestCase):
    def test_integer_upscale_keeps_hard_pixel_edges(self):
        img = Image.fromarray(np.kron(np.indices((4, 4)).sum(axis=0) % 2 * 255, np.ones((1, 1))).astype(np.uint8))
        out = np.asarray(kit.resample(img.convert('RGB'), 0, 0, 4, 4, 40, 40))
        self.assertEqual(set(np.unique(out)), {0, 255})

    def test_game_view_crops_rather_than_stretches(self):
        img = Image.new('RGB', (400, 224), (10, 20, 30))
        out = kit.game_view(img, None, None, 1.0, 1920, 1080)
        self.assertEqual(out.size, (1920, 1080))


class Layouts(unittest.TestCase):
    class Solid:
        def __init__(self, colour, size=(800, 224)):
            self.img = Image.new('RGB', size, colour)

        def frame(self, i):
            return self.img

    def test_stacked_split_shows_full_width_strips_after_sliding_in(self):
        e = SmallEdit()
        panes = [(0, self.Solid((200, 0, 0)), 0, None, None), (0, self.Solid((0, 0, 200)), 0, None, None)]
        out = kit.stacked_split(e, 16, panes, divider_px=4, bg=np.zeros((90, 160, 3), np.float32))
        sh = (90 - 4) // 2
        np.testing.assert_allclose(out[:sh, :, 0], 200, atol=1)
        np.testing.assert_allclose(out[sh + 4:sh + 4 + sh, :, 2], 200, atol=1)
        np.testing.assert_allclose(out[sh:sh + 4], np.broadcast_to(np.array(kit.YELLOW, np.float32), (4, 160, 3)), atol=1)

    def test_stacked_split_strip_is_off_screen_before_its_start(self):
        e = SmallEdit()
        panes = [(0, self.Solid((200, 0, 0)), 0, None, None), (30, self.Solid((0, 0, 200)), 0, None, None)]
        out = kit.stacked_split(e, 10, panes, divider_px=4, bg=np.zeros((90, 160, 3), np.float32))
        self.assertEqual(out[50:, :, 2].max(), 0)


class Transitions(unittest.TestCase):
    def setUp(self):
        self.e = SmallEdit()
        self.old = np.full((90, 160, 3), 10, np.float32)
        self.new = np.full((90, 160, 3), 200, np.float32)

    def test_every_transition_starts_on_old_and_ends_on_new(self):
        for name, fn in kit.TRANSITIONS.items():
            with self.subTest(name=name):
                direction = 'left' if name in ('push', 'whip') else None
                end = fn(self.old, self.new, 20, 0, 20, self.e, direction)
                self.assertGreater((np.abs(end - 200) < 1).mean(), 0.97, name)
                start = fn(self.old, self.new, 0, 0, 20, self.e, direction)
                self.assertGreater((np.abs(start - 10) < 1).mean(), 0.9, name)

    def test_push_moves_in_every_direction(self):
        for d in ('left', 'right', 'up', 'down'):
            with self.subTest(direction=d):
                mid = kit.push(self.old, self.new, 5, 0, 20, self.e, d, seam_px=0)
                frac = (mid == 200).all(axis=2).mean()
                self.assertTrue(0.1 < frac < 0.9)


class Typography(unittest.TestCase):
    def test_bitmap_font_parses_and_renders_outlined_word(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'font.txt'
            p.write_text('= A\n.#.\n#.#\n###\n#.#\n#.#\n= B\n##.\n#.#\n##.\n#.#\n##.\n')
            f = kit.BitmapFont(p)
        self.assertEqual(f.width('AB'), 7)
        img = f.render('AB', scale=2, upscale=3)
        self.assertEqual(img.shape[2], 4)
        colours = {tuple(c) for c in img[img[..., 3] > 0][:, :3].astype(int)}
        self.assertIn(kit.WHITE, colours)
        self.assertIn(kit.NAVY, colours)

    def test_bitmap_font_effects_stay_on_the_glyph_grid(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'font.txt'
            p.write_text('= A\n.#+\n#.+\n###\n')
            f = kit.BitmapFont(p)
        img = f.render('A', scale=9, outline=(0, 0, 0), outline_px=1, drop=(10, 10, 10), drop_px=1, drop_dx=1,
                       shades={'+': (182, 182, 182)})
        h, w = img.shape[:2]
        self.assertEqual((h % 9, w % 9), (0, 0))
        blocks = img.reshape(h // 9, 9, w // 9, 9, 4)
        self.assertTrue((blocks == blocks[:, :1, :, :1]).all(), 'every effect is whole glyph pixels')
        colours = {tuple(c) for c in img[img[..., 3] > 0][:, :3].astype(int)}
        self.assertIn((182, 182, 182), colours)
        self.assertIn(kit.WHITE, colours)

    @unittest.skipUnless(has_font(), 'no TTF installed')
    def test_tracking_widens_text_and_stroke_adds_ink(self):
        plain = kit.text('KICKER', 'sub', 40)
        wide = kit.text('KICKER', 'sub', 40, tracking=12)
        self.assertGreater(wide.shape[1], plain.shape[1] + 50)
        stroked = kit.text('KICKER', 'sub', 40, stroke=4)
        self.assertGreater((stroked[..., 3] > 0).sum(), (plain[..., 3] > 0).sum())

    def test_fonts_report_marks_fallbacks(self):
        rows = kit.fonts_report()
        self.assertEqual({r[0] for r in rows}, set(kit.ROLES))
        for role, name, state in rows:
            if name in kit.FALLBACK_NAMES:
                self.assertIn('FALLBACK', state)


class EditChecks(unittest.TestCase):
    def frames(self, d, n=30):
        return kit.synth_clip(Path(d) / 'c', n, 1, size=(40, 24))

    def test_gap_and_overrun_are_reported(self):
        with tempfile.TemporaryDirectory() as d:
            clip = self.frames(d)
            e = SmallEdit()
            e.S(kit.Game(0, 20, clip))
            e.S(kit.Game(25, 80, clip))
            warn = e.check()
        self.assertTrue(any('gap 20-25' in w for w in warn))
        self.assertTrue(any('past' in w for w in warn))

    def test_rushed_cutting_is_flagged(self):
        with tempfile.TemporaryDirectory() as d:
            clip = self.frames(d)
            e = SmallEdit()
            for k in range(30):
                e.S(kit.Game(k * 60, (k + 1) * 60, clip, hold=0))
            stats, warn = e.pacing()
        self.assertEqual(stats['shots'], 30)
        self.assertTrue(any('rushed' in w for w in warn))
        self.assertTrue(any('mean shot' in w for w in warn))

    def test_bare_gameplay_is_flagged_and_continuation_cuts_do_not_hide_it(self):
        with tempfile.TemporaryDirectory() as d:
            clip = self.frames(d)
            e = SmallEdit()
            e.S(kit.Game(0, 300, clip, hold=0))
            e.S(kit.Game(300, 900, clip, src=300, hold=0))
            stats, warn = e.pacing()
        self.assertTrue(any('boring' in w for w in warn))
        self.assertEqual(stats['longest_bare_s'], 15.0)

    def test_accompanied_gameplay_is_not_bare(self):
        with tempfile.TemporaryDirectory() as d:
            clip = self.frames(d)
            e = SmallEdit()
            e.S(kit.Game(0, 900, clip, hold=0))
            for k in range(0, 900, 200):
                e.O(kit.Flash(k, 6))
            stats, warn = e.pacing()
        self.assertFalse(any('boring' in w for w in warn))

    def test_captions_alone_are_not_accompaniment_and_dense_captions_are_flagged(self):
        with tempfile.TemporaryDirectory() as d:
            clip = self.frames(d)
            e = SmallEdit()
            e.S(kit.Game(0, 900, clip, hold=0))
            word = np.zeros((4, 4, 4), np.float32)
            for k in range(0, 900, 150):
                e.O(kit.Txt(k, k + 140, word, 10, 10))
            stats, warn = e.pacing()
        self.assertTrue(any('boring' in w for w in warn))
        self.assertTrue(any('over-captioned' in w for w in warn))

    def test_render_frame_composites_shot_overlay_and_transition(self):
        with tempfile.TemporaryDirectory() as d:
            clip = self.frames(d)
            e = SmallEdit()
            e.S(kit.Game(0, 10, clip))
            e.S(kit.Game(10, 20, clip, trans='iris', tdur=6))
            e.O(kit.Flash(10, 6))
            frame = e.render_frame(10)
        self.assertEqual(frame.shape, (90, 160, 3))
        self.assertEqual(frame.dtype, np.uint8)
        self.assertGreater(frame.mean(), 200)

    def test_mix_places_bed_and_sfx_and_masters_below_full_scale(self):
        with tempfile.TemporaryDirectory() as d:
            clip = self.frames(d)
            click = kit.synth_click(Path(d) / 'k.wav', 120, 3)
            e = SmallEdit()
            e.S(kit.Game(0, 120, clip, hold=0))
            e.bed(click, 0, 1.0)
            e.X(30, click, 2.0, length=0.2)
            out = e.mix()
        self.assertEqual(len(out), 120 * kit.SR // 60)
        self.assertLessEqual(np.abs(out).max(), 0.93 + 1e-6)
        self.assertGreater(np.abs(out).max(), 0.1)


if __name__ == '__main__':
    unittest.main()
