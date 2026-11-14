"""TALON's own idle, the signpost plates and the victory pass: the tool's record, its composites and what it leaves to others."""
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
try:
    from PIL import Image
except ModuleNotFoundError:
    raise unittest.SkipTest('Pillow is not installed; the playable-art pipeline tests need it')

HERE=Path(__file__).parent
ROOT=HERE.parents[1]
MOD=ROOT/'examples/mica-and-talon'
PROD=MOD/'production'
SHEET=MOD/'art/talon/sheet'
sys.path.insert(0,str(HERE/'playable_art'))
sys.path.insert(0,str(PROD/'talon'))
import lineart_pass as lineart

spec=importlib.util.spec_from_file_location('idle_sign_victory',PROD/'idle_sign_victory.py')
tool=importlib.util.module_from_spec(spec);spec.loader.exec_module(tool)


def pixels(path):
    with Image.open(path) as image:
        flat=list(image.get_flattened_data())
        return [flat[i:i+image.width] for i in range(0,len(flat),image.width)]


class IdleSignVictoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.record=json.loads(tool.RECORD.read_text())
        cls.handoff=json.loads((SHEET/'frames.yaml').read_text())
        cls.listed={f['id']:f for f in cls.handoff['frames']}

    def test_the_record_reproduces_the_shipped_art(self):
        result=subprocess.run([sys.executable,'-I',str(PROD/'idle_sign_victory.py'),'--check'],capture_output=True,text=True)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    def test_the_check_fails_when_a_sign_portrait_no_longer_matches_the_plates(self):
        changed=json.loads(tool.RECORD.read_text())
        row=changed['signs']['talon']['rows'][10]
        changed['signs']['talon']['rows'][10]=row[:20]+('c' if row[20]!='c' else '1')+row[21:]
        with tempfile.TemporaryDirectory() as directory:
            copy=Path(directory)/'idle-sign-victory.json';copy.write_text(json.dumps(changed))
            kept=tool.RECORD;tool.RECORD=copy
            try:
                with self.assertRaises(SystemExit) as failure:tool.main(['--check'])
            finally:tool.RECORD=kept
        self.assertIn('Stale',str(failure.exception));self.assertIn('talon/hud-and-screens/s1-sign.png',str(failure.exception))

    def test_his_idle_is_one_stand_then_the_approved_loop(self):
        script=self.handoff['scripts']['WAIT'];loop=self.record['talon_idle']['script']['loop']
        self.assertEqual('loop-back',script['end']);self.assertEqual(len(loop),script['loopBackSteps'])
        self.assertEqual(['wait-00'],script['frames'][:len(script['frames'])-len(loop)])
        self.assertEqual(426,sum(step['ticks'] for step in loop))                     # 7.1 s at 60 Hz
        self.assertEqual([4,4,6,6,8,8],[s['ticks'] for s in loop if s['frame'] in ('wait-07','wait-08')])   # the shakes slow down
        self.assertEqual(18,next(s['ticks'] for s in loop if s['frame']=='wait-06'))  # the puffed hold comes first
        self.assertEqual(3,next(s['ticks'] for s in loop if s['frame']=='wait-10'))   # the catch is one 50 ms snap
        shown=set(script['frames'])|set(self.handoff['nativeFrames'].values())
        self.assertFalse({f for f,entry in self.listed.items() if entry['animation']=='WAIT'}-shown)
        self.assertEqual(22,sum(entry['animation']=='WAIT' for entry in self.listed.values()))
        self.assertEqual(['family-blink-00','family-blink-01','family-blink-02'],self.handoff['scripts']['BLINK']['frames'])

    def test_native_wait_frames_point_at_his_own_drawings(self):
        native=self.handoff['nativeFrames']
        self.assertEqual(self.record['talon_idle']['native_frames'],{k:v for k,v in native.items() if k.startswith('family-wait/')})
        self.assertTrue(all(native[key] in self.listed for key in native))
        recipe=json.loads((PROD/'talon/recipe.json').read_text())
        self.assertEqual(self.handoff['scripts'],recipe['scripts']);self.assertEqual(native,recipe['nativeFrames'])

    def test_a_feather_frame_is_its_base_drawing_plus_the_feather_and_nothing_else(self):
        feather=tool.grid_of(self.record['talon_idle']['feather']['rows'])
        for name,spec in self.record['talon_idle']['composites'].items():
            base=self.listed[spec['base']];frame=self.listed[name]
            grid=pixels(SHEET/frame['file']);under=pixels(SHEET/base['file'])
            dx,dy=frame['hotspot'][0]-base['hotspot'][0],frame['hotspot'][1]-base['hotspot'][1]
            fx,fy=frame['hotspot'][0]+spec['at'][0],frame['hotspot'][1]+spec['at'][1]
            shape=[row[::-1] for row in feather] if spec['mirrored'] else feather
            covered={(fx+x,fy+y):v for y,row in enumerate(shape) for x,v in enumerate(row) if v}
            with self.subTest(frame=name):
                for y,row in enumerate(grid):
                    for x,value in enumerate(row):
                        by,bx=y-dy,x-dx
                        below=under[by][bx] if 0<=by<len(under) and 0<=bx<len(under[0]) else 0
                        self.assertEqual(covered.get((x,y),below),value,(x,y))
                self.assertTrue(covered)

    def test_the_reused_look_up_drawing_stands_on_the_idles_ground_line(self):
        up,idle,stand=self.listed['look_up-00'],self.listed['wait-21'],self.listed['wait-00']
        self.assertEqual(pixels(SHEET/up['file']),pixels(SHEET/idle['file']))
        sole=lambda frame:len(pixels(SHEET/frame['file']))-1-frame['hotspot'][1]
        self.assertEqual(sole(stand),sole(idle));self.assertEqual(3,sole(idle)-sole(up))
        self.assertNotIn('look_up-00',self.handoff['scripts']['WAIT']['frames'])

    def test_one_portrait_is_on_every_plate_of_a_character(self):
        for name in ('mica','talon'):
            face=tool.grid_of(self.record['signs'][name]['rows']);folder=MOD/f'art/{name}/hud-and-screens'
            for plate in tool.SIGNS:self.assertEqual(face,pixels(folder/f'{plate}.png'),(name,plate))
            for plate,flip in tool.SPINS:
                self.assertEqual(tool.spin(face,flip),pixels(folder/f'{plate}.png'),(name,plate))
                with Image.open(folder/f'{plate}-backing.png') as backing:self.assertEqual((32,32),backing.size)
            used={value for row in face for value in row}
            self.assertGreaterEqual(len(used),8);self.assertIn(0,used)               # a portrait, not a filled plate
            columns=[x for x in range(48) if any(row[x] for row in face)];rows=[y for y,row in enumerate(face) if any(row)]
            self.assertGreaterEqual(columns[-1]-columns[0]+1,24);self.assertEqual((2,29),(rows[0],rows[-1]))   # it fills the plate's field
            self.assertTrue(3<=columns[0] and columns[-1]<=44)

    def test_spin_squeezes_rows_and_mirrors_the_far_side(self):
        face=[[0]*48 for _ in range(32)]
        for y in range(32):face[y][2]=face[y][3]=5;face[y][44]=face[y][45]=9
        near,far=tool.spin(face,False),tool.spin(face,True)
        self.assertEqual(5,near[2][5]);self.assertEqual(9,near[2][26]);self.assertEqual(0,near[1][5])
        self.assertEqual([row[::-1] for row in near],far)

    def test_the_selected_drawings_are_the_ones_the_cleanup_builds(self):
        held={d['id']:d['source_rows'] for d in json.loads((PROD/'talon/pose-cleanup.json').read_text())['drawings']}
        chosen={**self.record['talon_idle']['drawings'],**self.record['talon_victory']['drawings']}
        self.assertEqual(16,len(chosen))
        for name,entry in chosen.items():self.assertEqual(entry['rows_sha256'],tool.digest(held[name]),name)
        self.assertFalse({f'wait-{n:02}' for n in range(13,22)}&set(held))            # composites are not drawings
        passed=[entry['gate_passed'] for entry in chosen.values()]
        self.assertEqual(15,sum(passed));self.assertFalse(chosen['wait-03']['gate_passed'])   # the one recorded exception

    def test_a_shut_eye_changes_only_the_eye_and_a_hand_stays_in_front_of_the_head(self):
        record=json.loads(lineart.RECORD.read_text());colors=json.loads((SHEET/'palette.yaml').read_text())['colors']
        donor=pixels(SHEET/'frames/wait-00.png')
        opened=lineart.head_for(dict(angle=0,mirror=False,beak='closed'),donor,record['kit'])
        shut=lineart.head_for(dict(angle=0,mirror=False,beak='closed',eye='shut'),donor,record['kit'])
        changed={key for key in opened if opened[key]!=shut[key]}
        self.assertEqual({(x-lineart.LENS[0],y-lineart.LENS[1]) for x,y,_ in lineart.SHUT_EYE},changed)
        salute=record['frames']['family-victory-01'];box=salute['head']['over'][0]
        staged=lineart.to_grid(__import__('pose_cleanup').cleaned(json.loads((PROD/'talon/pose-cleanup.json').read_text()),colors)['family-victory-01'])
        final=pixels(SHEET/self.listed['family-victory-01']['file'])
        dx=self.listed['family-victory-01']['hotspot'][0]-salute['hotspot_before'][0]
        dy=self.listed['family-victory-01']['hotspot'][1]-salute['hotspot_before'][1]
        gloves=[(x,y) for y in range(box[1],box[3]+1) for x in range(box[0],box[2]+1) if staged[y][x] in lineart.GLOVE]
        self.assertGreater(len(gloves),15)
        for x,y in gloves:self.assertEqual(staged[y][x],final[y+dy][x+dx],(x,y))

    def test_blink_and_victory_are_in_the_line_art_record_now(self):
        frames=json.loads(lineart.RECORD.read_text())['frames']
        for name in [f'family-blink-0{i}' for i in range(5)]:self.assertIsNone(frames[name]['head'],name)
        self.assertTrue(all(frames[f'family-victory-0{i}']['head'] for i in range(4)))
        fluffed={name for name in ('wait-06','wait-07','wait-08') if frames[name]['head'] is None}
        self.assertEqual({'wait-06','wait-07','wait-08'},fluffed)
        self.assertEqual(9,sum(1 for n in range(1,13) if frames[f'wait-{n:02}']['head']))


if __name__=='__main__':
    unittest.main()
