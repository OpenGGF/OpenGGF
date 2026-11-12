"""Style audit sanity, and TALON's line-art pass: its rules, its record and the frames it redraws."""
import importlib.util
import json
import subprocess
import sys
import unittest
from pathlib import Path
try:
    from PIL import Image
except ModuleNotFoundError:
    raise unittest.SkipTest('Pillow is not installed; the playable-art pipeline tests need it')

HERE=Path(__file__).parent
ROOT=HERE.parents[1]
PROD=ROOT/'examples/mica-and-talon/production/talon'
SHEET=ROOT/'examples/mica-and-talon/art/talon/sheet'
PROFILE=ROOT/'examples/mica-and-talon/production/style-audit.json'
sys.path.insert(0,str(HERE/'playable_art'))
sys.path.insert(0,str(PROD))
import style_audit
import lineart_pass as lineart
import pose_cleanup

LUM=[0,10]+[20*i for i in range(2,16)]
RAMPS={'plumage':(2,3,4),'light':(13,12)}


def block(fill=3,rim=None,gap=False,size=12):
    """An inked square of one shade, optionally with a darker rim inside the ink or a break in the outline."""
    grid=[[0]*(size+2) for _ in range(size+2)]
    for y in range(1,size+1):
        for x in range(1,size+1):
            edge=x in (1,size) or y in (1,size);inner=x in (2,size-1) or y in (2,size-1)
            grid[y][x]=1 if edge else rim if rim and inner else fill
    if gap:grid[1][5]=grid[1][6]=fill
    return grid


class StyleAuditTests(unittest.TestCase):
    def test_measures_read_outline_breaks_dark_rims_and_surviving_bands(self):
        clean=style_audit.measure(block(),LUM,RAMPS)
        self.assertEqual(0,clean['gap']);self.assertEqual(0,clean['weight']);self.assertEqual(1,clean['bands'])
        self.assertGreater(style_audit.measure(block(gap=True),LUM,RAMPS)['gap'],0)
        rimmed=style_audit.measure(block(rim=2),LUM,RAMPS)
        self.assertEqual(1,rimmed['weight']);self.assertEqual(2,rimmed['bands'])   # every pixel beside ink is the darkest shade
        self.assertLess(rimmed['cluster'],clean['cluster'])

    def test_ramp_distance_is_zero_on_the_reference_mix_and_grows_with_another_shading(self):
        reference={'plumage':{2:0.0,3:1.0,4:0.0},'light':{}}
        self.assertEqual(0,style_audit.measure(block(),LUM,RAMPS,reference=reference)['ramp'])
        self.assertAlmostEqual(1,style_audit.measure(block(fill=4),LUM,RAMPS,reference=reference)['ramp'])

    def test_no_frame_is_off_the_reference_style_unless_the_profile_accepts_it(self):
        result=subprocess.run([sys.executable,'-I',str(HERE/'playable_art/style_audit.py'),str(PROFILE),'--sheet','talon','--check'],
                              capture_output=True,text=True)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    def test_the_audit_flags_a_reference_frame_given_a_heavy_rim(self):
        rows,centre,spread,_=style_audit.audit(PROFILE,'talon')
        grid,lum=style_audit.load(SHEET/'frames/wait-00.png')
        ramps={k:tuple(v) for k,v in json.loads(PROFILE.read_text())['sheets']['talon']['ramps'].items()}
        heavy=[row[:] for row in grid]
        for y,row in enumerate(grid):
            for x,value in enumerate(row):
                if value in (3,4) and any(0<=y+b<len(grid) and 0<=x+a<len(row) and grid[y+b][x+a]==1 for a,b in style_audit.N4):
                    heavy[y][x]=2
        measured=style_audit.measure(heavy,lum,ramps)
        self.assertLess((rows['wait-00']['weight']-centre['weight'])/spread['weight'],style_audit.LIMIT)
        self.assertGreater((measured['weight']-centre['weight'])/spread['weight'],style_audit.LIMIT)


class LineartRuleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.record=json.loads(lineart.RECORD.read_text())
        with Image.open(SHEET/'frames/wait-00.png') as image:
            cls.donor=lineart.of_image(image)

    def test_rim_is_thinned_on_lit_edges_and_kept_in_shadow_and_in_solid_shade(self):
        out=lineart.thin_rim(block(fill=3,rim=2))
        self.assertEqual(3,out[2][5]);self.assertEqual(3,out[5][2])                # ink above, ink to the left
        self.assertEqual(2,out[11][5]);self.assertEqual(2,out[5][11])              # ink below, ink to the right
        solid=block(fill=2)
        self.assertEqual(solid,lineart.thin_rim(solid))                            # a 2x2 block of shade is shadow

    def body(self):
        grid=[[0]*40 for _ in range(44)]
        for y in range(8,42):
            for x in range(6,30):grid[y][x]=3
        for x,y in ((20,10),(21,10),(27,23),(27,24),(29,22)):grid[y][x]=14      # the old lens and old beak
        return grid

    def test_the_donor_head_lands_on_the_lens_whole_and_old_goggles_and_beak_go(self):
        marks=dict(lens=[20,10],far=None,tip=[27,20])
        spec=dict(lens=[20,10],angle=0,mirror=False,beak='closed')
        out,shift=lineart.transplant(self.body(),spec,marks,self.donor,self.record['kit'])
        ox,oy=20-lineart.LENS[0]-shift[0],10-lineart.LENS[1]-shift[1]
        for y in range(lineart.NECK+1):
            for x,value in enumerate(self.donor[y]):
                if value:self.assertEqual(value,out[y+oy][x+ox],(x,y))                # transplant itself does not ink
        self.assertEqual(3,out[23-shift[1]][27-shift[0]])                           # old beak inside the body: plumage now
        self.assertEqual(0,out[22-shift[1]][29-shift[0]])                           # old beak on the edge: gone
        self.assertEqual(3,out[30-shift[1]][10-shift[0]])                           # the body is untouched

    def test_a_head_that_overhangs_the_frame_moves_the_origin_and_a_mirrored_head_faces_left(self):
        marks=dict(lens=[8,3],far=None,tip=[1,14])
        spec=dict(lens=[8,3],angle=0,mirror=True,beak='closed')
        out,shift=lineart.transplant(self.body(),spec,marks,self.donor,self.record['kit'])
        self.assertLess(shift[1],0)                                                 # two rows of crest above the old frame
        beak=[x for row in out for x,value in enumerate(row) if value in (14,15)]
        self.assertLess(min(beak)+shift[0],8)                                       # the beak is left of the lens
        opened=lineart.head_for(dict(angle=0,mirror=False,beak='open'),self.donor,self.record['kit'])
        self.assertTrue(any(value in (10,11) for value in opened.values()))         # the open beak shows mouth red
        self.assertFalse(any(value in (10,11) for value in
                             lineart.head_for(dict(angle=0,mirror=False,beak='closed'),self.donor,self.record['kit']).values()))


class TalonLineartTests(unittest.TestCase):
    LOOPS=('PUSH','BALANCE','BALANCE2','BALANCE3','BALANCE4','BUBBLE','TAILS_SWIM','TAILS_SWIM_ASCEND','TAILS_SWIM_CARRY',
           'TAILS_SWIM_TIRED','TAILS_CARRIED','TAILS_FLY_TIRED','HANG','HANG2','HURT_FALL','GET_UP','SKID')

    @classmethod
    def setUpClass(cls):
        cls.record=json.loads(lineart.RECORD.read_text())
        cls.frames=cls.record['frames']
        cls.listed={f['id']:f for f in json.loads((SHEET/'frames.yaml').read_text())['frames']}
        cls.colors=json.loads((SHEET/'palette.yaml').read_text())['colors']
        cls.staged=pose_cleanup.cleaned(json.loads((PROD/'pose-cleanup.json').read_text()),cls.colors)

    def grid(self,name):
        with Image.open(SHEET/self.listed[name]['file']) as image:
            return lineart.of_image(image)

    def test_the_record_covers_every_drawing_it_should_and_no_reference(self):
        result=subprocess.run([sys.executable,'-I',str(PROD/'lineart_pass.py'),'--check'],capture_output=True,text=True)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        for name,frame in self.listed.items():
            if frame['animation'] in lineart.REFERENCE|lineart.OTHER_LANE:self.assertNotIn(name,self.frames)
        self.assertGreaterEqual(sum(1 for entry in self.frames.values() if entry['head']),80)

    def test_references_and_the_other_lanes_frames_are_the_cleanup_stage_unchanged(self):
        for name,rows in self.staged.items():
            if name not in self.frames:
                self.assertEqual(lineart.to_grid(rows),self.grid(name),name)

    def test_a_straight_head_is_the_reference_head_pixel_for_pixel(self):
        donor=lineart.to_grid(self.staged[lineart.DONOR]);checked=0
        for name,entry in self.frames.items():
            head=entry['head']
            if not head or head['angle'] or head['mirror'] or head['beak']!='closed':continue
            grid=self.grid(name)
            dx=self.listed[name]['hotspot'][0]-entry['hotspot_before'][0]
            dy=self.listed[name]['hotspot'][1]-entry['hotspot_before'][1]
            ox,oy=head['lens'][0]+dx-lineart.LENS[0],head['lens'][1]+dy-lineart.LENS[1]
            with self.subTest(frame=name):
                for y in range(lineart.NECK):                    # the neck row itself is inked where nothing lies below it
                    for x,value in enumerate(donor[y]):
                        if value:self.assertEqual(value,grid[y+oy][x+ox],(x,y))
            checked+=1
        self.assertGreaterEqual(checked,40)

    def test_a_loop_gets_the_reference_head_on_every_frame_or_on_none(self):
        for loop in self.LOOPS:
            heads={bool(entry['head']) for entry in self.frames.values() if entry['animation']==loop}
            self.assertEqual({True},heads,loop)

    def test_turned_copies_are_their_redrawn_source_turned(self):
        from scale_audit import turned
        copies=[(name,entry['copy']) for name,entry in self.frames.items() if 'copy' in entry]
        self.assertEqual(16,len(copies))
        for name,copy in copies:
            source=lineart.to_image(self.grid(copy['source']),self.colors)
            image,hot=turned(source,self.listed[copy['source']]['hotspot'],copy['angle'],copy['flip'])
            self.assertEqual(lineart.of_image(image),self.grid(name),name)
            self.assertEqual(hot,self.listed[name]['hotspot'],name)

    def test_the_pass_keeps_each_drawing_at_its_size_and_fully_inked(self):
        for name,entry in self.frames.items():
            grid=self.grid(name);was=self.staged[name]
            if 'copy' not in entry:
                self.assertLessEqual(abs(len(grid[0])-len(was[0])),6,name)          # a head may overhang the old frame
                self.assertLessEqual(abs(len(grid)-len(was)),6,name)
            self.assertEqual(grid,lineart.ink(grid),name)


if __name__=='__main__':
    unittest.main()
