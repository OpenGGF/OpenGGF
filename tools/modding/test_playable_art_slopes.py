"""Owner slope correction: lossless quarter turns, EPX corners, the mode reduction and reproducibility."""
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
from PIL import Image

HERE=Path(__file__).parent
sys.path.insert(0,str(HERE/'playable_art'))
import derive_slopes as slopes

ROOT=HERE.parents[1]
_spec=importlib.util.spec_from_file_location('slope_sets',ROOT/'examples/mica-and-talon/production/slope_sets.py')
slope_sets=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(slope_sets)


class DeterministicSlopeTests(unittest.TestCase):
    def image(self):
        im=Image.new('P',(9,9));im.putpalette([i for i in range(256) for _ in range(3)])
        im.putpixel((3,2),4);im.putpixel((4,2),5);im.putpixel((4,3),10)
        return im

    def test_quarter_turn_is_exact_and_four_turns_restore_bytes(self):
        im=self.image();turned=slopes.rotate(im,90)
        self.assertEqual(turned.getpixel((2,5)),4)
        for _ in range(3):turned=slopes.rotate(turned,90)
        self.assertEqual(im.tobytes(),turned.tobytes())
        self.assertEqual(im.getpalette(),turned.getpalette())

    def test_inverted_is_lossless_quarter_turn_of_up45(self):
        im=self.image()
        for method in ('mode','nearest'):
            self.assertEqual(slopes.rotate(im,135,method).tobytes(),
                             slopes.rotate(im,45,method).transpose(Image.Transpose.ROTATE_90).tobytes())
            self.assertLessEqual(set(slopes.rotate(im,45,method).get_flattened_data()),{0,1,4,5,10})
        self.assertEqual(slopes.rotate(im,45).tobytes(),slopes.rotate(im,45,'mode').tobytes())   # mode is the default

    def test_scale2x_corner_rule_preserves_original_indices(self):
        im=Image.new('P',(3,3));im.putpixel((1,0),4);im.putpixel((0,1),4);im.putpixel((1,1),5)
        out=slopes.scale2x(im)
        self.assertEqual(out.getpixel((2,2)),4)
        self.assertEqual(out.getpixel((3,2)),5)

    def test_mode_reduction_redraws_the_outline_and_keeps_quarter_turns_exact(self):
        # An inked 12x12 block of 4 with a 2x2 core of 10. "mode" lifts the ring before the turn and inks
        # after it, so the diamond has one ring of ink and no stale outline pixels inside the body.
        im=Image.new('P',(32,32));im.putpalette([i for i in range(256) for _ in range(3)])
        for y in range(10,22):
            for x in range(10,22):im.putpixel((x,y),1 if x in (10,21) or y in (10,21) else 4)
        for y in (15,16):
            for x in (15,16):im.putpixel((x,y),10)
        self.assertEqual({0,4,10},set(slopes.lift_ring(im).get_flattened_data()))
        # A powered bank's contour slot is lifted the same way when it is named in the ring.
        slot=im.copy()
        for y in range(10,22):
            for x in range(10,22):
                if slot.getpixel((x,y))==1:slot.putpixel((x,y),2)
        self.assertEqual({0,2,4,10},set(slopes.lift_ring(slot).get_flattened_data()))
        self.assertEqual({0,4,10},set(slopes.lift_ring(slot,(1,2)).get_flattened_data()))
        self.assertEqual(slopes.rotate(im,45,'mode').tobytes(),slopes.rotate(slot,45,'mode',ring=(1,2)).tobytes())
        turned=slopes.rotate(im,45,'mode')
        self.assertEqual({0,1,4,10},set(turned.get_flattened_data()))
        px=turned.load()
        for y in range(32):
            for x in range(32):
                edge=px[x,y] and any(not (0<=a<32 and 0<=b<32) or px[a,b]==0 for a,b in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)))
                self.assertEqual(bool(edge),px[x,y]==1,(x,y))            # ink is exactly the silhouette
        self.assertEqual(turned.transpose(Image.Transpose.ROTATE_90).tobytes(),slopes.rotate(im,135,'mode').tobytes())
        self.assertEqual(slopes.rotate(im,90).tobytes(),slopes.rotate(im,90,'mode').tobytes())
        seen=[]
        finished=slopes.rotate(im,45,'mode',finish=lambda small:(seen.append(set(small.get_flattened_data())),small)[1])
        self.assertEqual([{0,4,10}],seen)                                 # cleanup runs before the ink
        self.assertEqual(turned.tobytes(),finished.tobytes())
        with self.assertRaises(ValueError):slopes.rotate(im,45,'bilinear')

    def test_mode_reduce_takes_the_commonest_index_and_drops_mostly_empty_blocks(self):
        big=Image.new('P',(16,8));big.putpalette([i for i in range(256) for _ in range(3)])
        for y in range(8):
            for x in range(8):big.putpixel((x,y),5 if x<5 else 9)        # 40 of 5, 24 of 9
            for x in range(8,11):big.putpixel((x,y),7)                    # 24 of 64 opaque
        self.assertEqual([5,0],list(slopes.mode_reduce(big,(2,1)).get_flattened_data()))

    def test_every_shipped_slope_rederives_from_flat_pixels(self):
        # Re-pinned 2026-11-12: MICA's slopes are the mode reduction with her material despeckle, as
        # production/slope_sets.py derives them (the 2026-11-08 nearest reduction no longer ships).
        root=ROOT/'examples/mica-and-talon/art/mica/sheet'
        m=json.loads((root/'frames.yaml').read_text());meta=json.loads((root/'../../../production/mica/native-pose-core.json').read_text())['frames']
        by_id={f['id']:f for f in m['frames']}
        checked=0
        for f in m['frames']:
            if f['angle_set']=='flat':continue
            with self.subTest(frame=f['id']):
                group,slope,phase=f['id'].split('-');base=by_id[f'{group}-{phase}']
                with Image.open(root/base['file']) as flat:
                    im,hot=slope_sets.slope(flat,base['hotspot'],slope,meta[f['id']]['native_support'],slope_sets.MICA)
                    old,_=slopes.derive(flat,base['hotspot'],slopes.ANGLES[slope],meta[f['id']]['native_support'],'nearest')
                self.assertEqual(f['hotspot'],hot)
                with Image.open(root/f['file']) as actual:
                    self.assertEqual(im.size,actual.size)
                    self.assertEqual(im.tobytes(),actual.tobytes())
                    if slope!='steep':                                    # the superseded reduction must not match
                        self.assertNotEqual((old.size,old.tobytes()),(actual.size,actual.tobytes()))
                checked+=1
        self.assertEqual(36,checked)

    def test_every_derived_set_of_both_characters_reproduces(self):
        # MICA's slopes and homing dives, her Open Cone slopes and Solar TALON's slopes; TALON's normal set too.
        for script in ('production/slope_sets.py','production/talon/walk_cleanup.py'):
            result=subprocess.run([sys.executable,'-I',str(ROOT/'examples/mica-and-talon'/script),'--check'],capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    def test_no_shipped_45_degree_set_carries_a_stale_outline(self):
        # A mode turn inks only the new silhouette, so a 45 degree frame has the ink share of its flat
        # (mean gap -0.004 on both sheets). The nearest reduction left the flat's rotated outline inside
        # the body and ran 0.04 higher (0.02-0.07 per frame).
        import noise_audit
        for character in ('mica','talon'):
            rows=noise_audit.sheet(ROOT/'examples/mica-and-talon/art'/character/'sheet')
            flat={name:m for _,angle,name,m in rows if angle=='flat'}
            gaps=[m['ink']-flat[name.replace('-up45','')]['ink'] for _,angle,name,m in rows if angle=='up45']
            self.assertEqual(12,len(gaps))
            self.assertLessEqual(sum(gaps)/len(gaps),0.015,character)
            self.assertTrue(all(m['edge']==1 for _,angle,_,m in rows if angle!='flat'),character)


if __name__=='__main__':
    unittest.main()
