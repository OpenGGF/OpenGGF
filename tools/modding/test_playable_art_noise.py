"""Noise metric sanity, and TALON's cleaned sheet stays reproducible and clean."""
import importlib.util
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


class TalonPoseCleanupTests(unittest.TestCase):
    SHEET=ROOT/'examples/mica-and-talon/art/talon/sheet'
    PROD=ROOT/'examples/mica-and-talon/production/talon'

    def records(self):
        walk=json.loads((self.PROD/'walk-cleanup.json').read_text())['flats']
        poses=json.loads((self.PROD/'pose-cleanup.json').read_text())['drawings']
        return walk,poses

    def test_recorded_pose_cleanup_reproduces_the_shipped_frames(self):
        result=subprocess.run([sys.executable,'-I',str(self.PROD/'pose_cleanup.py'),'--check'],capture_output=True,text=True)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    def test_every_flat_pose_is_cleaned_or_listed_as_left_alone(self):
        walk,poses=self.records()
        left=json.loads((self.PROD/'pose-cleanup.json').read_text())['left_alone']
        manifest=json.loads((self.SHEET/'frames.yaml').read_text())['frames']
        kept={'roll-00','roll-01','roll-02','roll-03','spindash-03','family-super-transform-02','family-blank-00'}
        kept|={f'wait-{n}' for n in range(13,22)}              # the feather composites and look_up-00 on the idle's ground line
        self.assertTrue({'roll-00..03','spindash-03','family-super-transform-02','family-blank-00','wait-13..21'}<=set(left))
        flight={'TAILS_FLY','TAILS_FLY_ASCEND','TAILS_FLY_CARRY','TAILS_FLY_CARRY_ASCEND'}
        cleaned={d['id'] for d in walk}|{d['id'] for d in poses}
        for frame in manifest:
            if frame['angle_set']!='flat' or frame['animation'] in flight:continue
            self.assertNotEqual(frame['id'] in cleaned,frame['id'] in kept,frame['id'])
        self.assertEqual(209,len(poses))                        # 203 less the six retired idle frames, plus his twelve

    def test_cleanup_stays_inside_a_material_and_leaves_goggles_beak_and_shoes_as_drawn(self):
        spec=importlib.util.spec_from_file_location('walk_cleanup',self.PROD/'walk_cleanup.py')
        walk_cleanup=importlib.util.module_from_spec(spec);spec.loader.exec_module(walk_cleanup)
        sys.path.insert(0,str(self.PROD))
        import pose_cleanup
        from PIL import Image
        plumage,light={2,3,4,5},{6,7,8,12,13}
        walk,poses=self.records()
        colors=json.loads((self.SHEET/'palette.yaml').read_text())['colors']
        # The cleanup stage alone: lineart_pass.py redraws heads on top of it, and has its own tests.
        staged=pose_cleanup.cleaned(json.loads((self.PROD/'pose-cleanup.json').read_text()),colors)
        redrawn=json.loads((self.PROD/'lineart-pass.json').read_text())['frames']
        protected=0
        for drawing in walk+poses:
            with self.subTest(frame=drawing['id']), Image.open(self.SHEET/'frames'/(drawing['id']+'.png')) as image:
                rows=drawing['source_rows'];width=len(rows[0])
                was=[int(v,16) for row in rows for v in row]
                if drawing['id'] in staged:
                    now=[int(v,16) for row in staged[drawing['id']] for v in row]
                    if drawing['id'] not in redrawn:                     # WAIT, BLINK, VICTORY: the stage is the frame
                        self.assertEqual(now,list(image.get_flattened_data()))
                else:
                    now=list(image.get_flattened_data())
                self.assertEqual(len(was),len(now))
                for a,b in zip(was,now):
                    if a!=b:self.assertTrue({a,b}<=plumage or {a,b}<=light,(a,b))
                # A white/cream patch with no plumage beside it that touches amber, brown or red is a goggle
                # glint, beak shine or shoe white: part of something the cleanup must not redraw.
                for x,y in walk_cleanup.part_detail([[int(v,16) for v in row] for row in rows]):
                    self.assertEqual(was[y*width+x],now[y*width+x],(x,y));protected+=1
        self.assertGreater(protected,500)                       # 840 such pixels across the 215 drawings

    def test_cleaned_poses_score_with_the_walk_and_the_sheet_median_follows(self):
        # Before the 2026-11-12 pose cleanup these drawings scored 0.20-0.46 (median 0.34); the sheet median was 0.32.
        rows=noise.sheet(self.SHEET)
        by_id={name:m for _,_,name,m in rows}
        _,poses=self.records()
        scores=sorted(by_id[d['id']]['noise'] for d in poses)
        self.assertLessEqual(scores[-1],0.29)
        self.assertLessEqual(scores[len(scores)//2],0.21)
        every=sorted(m['noise'] for *_,m in rows)
        self.assertLessEqual(every[len(every)//2],0.20)
        self.assertTrue(all(m['edge']==1 for *_,m in rows))


if __name__=='__main__':
    unittest.main()
