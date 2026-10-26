"""Every one-frame segment must survive Matroska timestamp rounding, in order."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import test_reel


@unittest.skipUnless(shutil.which('ffmpeg'), 'FFmpeg is required for the reel timing integration check')
class FrameTimingTests(unittest.TestCase):
    def test_eighty_one_frame_cards_keep_exact_identity_and_duration(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for color in ('red', 'blue'):
                subprocess.run(['ffmpeg', '-v', 'error', '-xerror', '-nostdin', '-f', 'lavfi',
                                '-i', f'color=c={color}:s=16x16:r=60', '-frames:v', '1',
                                '-c:v', 'ffv1', '-pix_fmt', 'bgr0', '-threads', '1',
                                str(root/f'{color}.mkv')], check=True)
            # These intentionally have the same 1/1000 segment time base as the
            # retained full-reel fragments. No fictitious JUnit evidence is made.
            concat = root/'cards.ffconcat'
            concat.write_text('ffconcat version 1.0\n' + ''.join(
                f"file '{'red' if i % 2 == 0 else 'blue'}.mkv'\n" for i in range(80)))
            for slow in (1, 4):
                with self.subTest(slow=slow):
                    pixels = subprocess.check_output([
                        'ffmpeg', '-v', 'error', '-xerror', '-nostdin', '-f', 'concat', '-safe', '0',
                        '-i', str(concat), '-vf', test_reel.assembly_filter(slow),
                        '-frames:v', str(80*slow), '-r', '60', '-pix_fmt', 'rgb24',
                        '-f', 'rawvideo', '-threads', '1', '-'])
                    self.assertEqual(80*slow*16*16*3, len(pixels))
                    for frame in range(80*slow):
                        offset = frame*16*16*3
                        red, _, blue = pixels[offset:offset+3]
                        self.assertEqual((frame//slow) % 2 == 0, red > blue,
                                         f'wrong segment identity at output frame {frame}, speed factor {slow}')


if __name__ == '__main__':
    unittest.main()
