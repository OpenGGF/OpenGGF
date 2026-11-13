"""TALON's scale audit: the head measure itself, the rebuild record and the tree it describes."""
import importlib.util
import json
import math
import subprocess
import sys
import unittest
from pathlib import Path
try:
    from PIL import Image, ImageDraw
except ModuleNotFoundError:
    raise unittest.SkipTest('Pillow is not installed; the playable-art pipeline tests need it')

ROOT=Path(__file__).resolve().parents[2]
PROD=ROOT/'examples/mica-and-talon/production/talon'
SHEET=ROOT/'examples/mica-and-talon/art/talon/sheet'
spec=importlib.util.spec_from_file_location('scale_audit',PROD/'scale_audit.py')
audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)
LENS,BEAK,BROWN=(255,153,0,255),(255,214,0,255),(110,70,30,255)


def bird(scale=1.0,turn=0,beak=True,lens=LENS):
    """A stand-in head: an orange lens 60x24 and a yellow beak whose tip is 120 px from the lens centre."""
    canvas=Image.new('RGBA',(400,400));draw=ImageDraw.Draw(canvas)
    draw.ellipse((170,188,230,212),fill=lens)
    draw.ellipse((150,150,170,160),fill=BROWN)                    # a strap end: lens-shaped, wrong colour
    if beak:draw.polygon([(290,190),(320,200),(290,215)],fill=BEAK)
    if turn:canvas=canvas.rotate(turn,resample=Image.Resampling.BICUBIC)
    return canvas.resize((round(400*scale),round(400*scale)),Image.Resampling.LANCZOS)


class HeadMeasureTests(unittest.TestCase):
    def test_reads_the_lens_band_and_the_span_to_the_beak_tip(self):
        band,span=audit.head(bird())
        self.assertAlmostEqual(24,band,delta=2)                   # the short axis, not the 60 px long one
        self.assertAlmostEqual(120,span,delta=3)

    def test_both_readings_scale_with_the_drawing_and_ignore_its_tilt(self):
        band,span=audit.head(bird())
        big_band,big_span=audit.head(bird(scale=1.5))
        self.assertAlmostEqual(1.5,big_band/band,delta=0.06)
        self.assertAlmostEqual(1.5,big_span/span,delta=0.04)
        for turn in (30,90,200):                                  # hanging, floating and object poses are turned
            tilted_band,tilted_span=audit.head(bird(turn=turn))
            self.assertAlmostEqual(1,tilted_band/band,delta=0.08,msg=turn)
            self.assertAlmostEqual(1,tilted_span/span,delta=0.03,msg=turn)

    def test_a_hidden_part_reads_as_missing_not_as_a_number(self):
        band,span=audit.head(bird(beak=False))
        self.assertAlmostEqual(24,band,delta=2);self.assertIsNone(span)
        self.assertEqual((None,None),audit.head(bird(lens=BEAK)))  # a beak-yellow blob is not taken for a lens

    def test_anchors_keep_the_sole_the_grip_or_the_centre(self):
        self.assertEqual([10,13],audit.anchor('VICTORY',[24,40],[12,20],[20,33]))       # sole stays 20 px below
        self.assertEqual([10,20],audit.anchor('TAILS_CARRIED',[24,40],[12,20],[20,33])) # the gripped top stays
        self.assertEqual([10,16],audit.anchor('TAILS_FLY',[24,40],[12,20],[20,33]))     # airborne: about the hotspot


class TalonScaleRebuildTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.record=json.loads((PROD/'scale-audit.json').read_text())
        cls.rows=cls.record['frames']

    def test_the_tree_holds_the_rebuilt_drawings_the_record_describes(self):
        result=subprocess.run([sys.executable,'-I',str(PROD/'scale_audit.py'),'--check'],capture_output=True,text=True)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    def test_every_decision_follows_from_its_recorded_reading(self):
        tolerance=self.record['tolerance']
        for name,row in self.rows.items():
            with self.subTest(frame=name):
                if row['band'] and row['span'] and row['kind'] not in ('object','hand'):
                    self.assertAlmostEqual(math.sqrt(row['band']*row['span']),row['head'],delta=0.002)
                if row['decision']=='rebuild':
                    self.assertGreater(abs(row['multiplier']-1),tolerance)
                    made=row['rebuilt']
                    self.assertAlmostEqual(1/row['multiplier'],made['resize'],delta=0.001)
                    # the drawing really changed size by that factor (rounding and the crop allow two pixels)
                    for was,now in zip(row['before_size'],made['size']):
                        self.assertAlmostEqual(was*made['resize'],now,delta=2.5)
                    if row['multiplier']<1:self.assertTrue(row['why'].startswith('by '),row['why'])  # only by hand
                else:
                    self.assertNotIn('rebuilt',row)

    def test_rebuilt_animations_land_on_the_rung_and_the_references_were_not_touched(self):
        for name,summary in self.record['animations'].items():
            if summary['rebuilt']==summary['drawings']:
                self.assertAlmostEqual(1,summary['after'][1],delta=self.record['tolerance'],msg=name)
        for name in ('WAIT','WALK','RUN'):
            self.assertEqual(0,self.record['animations'][name]['rebuilt'])
            self.assertAlmostEqual(1,self.record['animations'][name]['before'][1],delta=0.05,msg=name)
        self.assertGreaterEqual(sum(row['decision']=='rebuild' for row in self.rows.values()),60)

    def test_standing_poses_stand_within_a_twelfth_of_the_rung(self):
        manifest=json.loads((SHEET/'frames.yaml').read_text())['frames']
        feather={f'wait-{n}' for n in range(13,21)}             # a loose feather above his head is not his height
        for frame in manifest:
            if frame['id'] in feather:continue
            if frame['animation'] in ('WAIT','LOOK_UP','VICTORY') and frame['angle_set']=='flat':
                with Image.open(SHEET/frame['file']) as image:
                    self.assertAlmostEqual(37,image.height,delta=3,msg=frame['id'])    # VICTORY stood 45-47 px


if __name__=='__main__':
    unittest.main()
