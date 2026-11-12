"""Noise metric sanity, and TALON's cleaned WALK/RUN set stays reproducible and clean."""
import json
import subprocess
import sys
from pathlib import Path
import unittest
try:
    import PIL  # noqa: F401  # Pillow is the art pipeline's only third-party dependency
except ModuleNotFoundError:
    raise unittest.SkipTest('Pillow is not installed; the playable-art pipeline tests need it')

HERE=Path(__file__).parent
ROOT=HERE.parents[1]
sys.path.insert(0,str(HERE/'playable_art'))
import noise_audit as noise

LUM=[0,10]+[20*i for i in range(2,16)]


class NoiseMetricTests(unittest.TestCase):
    def test_flat_block_is_clean_and_checkerboard_is_all_noise(self):
        ring=[[1]*8]+[[1]+[4]*6+[1] for _ in range(6)]+[[1]*8]
        flat=noise.metrics(ring,LUM)
        self.assertEqual((0,0,0,0,0),tuple(flat[k] for k in ('iso','fleck','short','ramp','trans')))
        self.assertEqual(1,flat['edge']);self.assertEqual(64,flat['pixels'])
        board=[[1]*8]+[[1]+[4 if (x+y)%2 else 7 for x in range(6)]+[1] for y in range(6)]+[[1]*8]
        busy=noise.metrics(board,LUM)
        self.assertEqual((1,1,1,1),tuple(busy[k] for k in ('iso','fleck','short','trans')))
        self.assertEqual(0,busy['ramp'])                                  # two shades alternate; nothing lies between
        self.assertIsNone(noise.metrics([[0,0],[0,0]],LUM))

    def test_ramp_counts_one_pixel_steps_between_two_shades_only(self):
        # 3 | 5 | 9 across: the single 5 column is an anti-alias step; a two-wide 5 band is a real band.
        step=noise.metrics([[3,5,9]]*4,LUM)
        self.assertAlmostEqual(1/3,step['ramp'])
        band=noise.metrics([[3,5,5,9]]*4,LUM)
        self.assertEqual(0,band['ramp'])
        self.assertEqual(0,noise.metrics([[1,5,9]]*4,LUM)['ramp'])        # shade beside ink is not a ramp

    def test_unoutlined_frame_reports_its_missing_ink(self):
        self.assertEqual(0,noise.metrics([[4,4],[4,4]],LUM)['edge'])


class TalonWalkCleanupTests(unittest.TestCase):
    SHEET=ROOT/'examples/mica-and-talon/art/talon/sheet'

    def test_recorded_cleanup_reproduces_the_shipped_frames(self):
        script=ROOT/'examples/mica-and-talon/production/talon/walk_cleanup.py'
        result=subprocess.run([sys.executable,'-I',str(script),'--check'],capture_output=True,text=True)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    def test_walk_and_run_stay_cleaner_than_the_sheet_and_fully_inked(self):
        # Before the 2026-11-12 cleanup the flat WALK/RUN frames scored 0.30-0.38 like most of the sheet,
        # the 45 degree sets 0.21-0.26. The flight set, the crisp reference, scores about 0.15.
        rows=noise.sheet(self.SHEET)
        cleaned=[(name,angle,m) for animation,angle,name,m in rows if animation in ('WALK','RUN')]
        self.assertEqual(48,len(cleaned))
        for name,angle,m in cleaned:
            with self.subTest(frame=name):
                self.assertLessEqual(m['noise'],0.24 if angle in ('flat','steep') else 0.16)
                self.assertEqual(1,m['edge'])
        self.assertTrue(all(m['edge']==1 for *_,m in rows))

    def test_cleanup_touched_only_plumage_and_white_and_no_silhouette(self):
        record=json.loads((ROOT/'examples/mica-and-talon/production/talon/walk-cleanup.json').read_text())
        from PIL import Image
        for flat in record['flats']:
            with self.subTest(frame=flat['id']), Image.open(self.SHEET/'frames'/(flat['id']+'.png')) as image:
                now=list(image.get_flattened_data());was=[int(v,16) for row in flat['source_rows'] for v in row]
                self.assertEqual(image.size,(len(flat['source_rows'][0]),len(flat['source_rows'])))
                plumage,light={2,3,4,5},{6,7,8,12,13}
                for a,b in zip(was,now):
                    if a!=b:self.assertTrue({a,b}<=plumage or {a,b}<=light,(a,b))
                self.assertGreater(sum(a!=b for a,b in zip(was,now)),20)


if __name__=='__main__':
    unittest.main()
