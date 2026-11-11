"""Single-master wag contract, including mutation negative control.
Inputs: maintained generated glove and native-angle recipe. Origin: owner report,
2026-11-10. This verifies derivation/landmark topology, not image recognition.
"""
import importlib.util,json,sys,unittest
try:
    import PIL  # noqa: F401  # Pillow is the art pipeline's only third-party dependency
except ModuleNotFoundError:
    raise unittest.SkipTest('Pillow is not installed; the playable-art pipeline tests need it')
from pathlib import Path
from PIL import Image
ART=Path(__file__).resolve().parents[2]/'examples/mica-and-talon/art/mica/titles'
sys.path.insert(0,str(ART/'../../../production/mica/titles'))
import s3k_hand_anatomy as anatomy
from s3k_hand_ownership import points_from_spans

def expected_frames():
    spec=json.loads((ART/'../../../production/mica/titles/s3k-hand-anatomy.json').read_text());master=Image.open(ART/'s3k-hand-master.png');plane=Image.open(ART/'s3k-plane-13-right.png');owned=points_from_spans(json.loads((ART/'../../../production/mica/titles/s3k-hand-ownership.json').read_text())['ownership_rows']);result={}
    for selector,angle in spec['relative_angles'].items():
        pose=anatomy.derive(master,spec['wrist'],angle);frame=Image.new('P',master.size)
        for x,y in owned:frame.putpixel((x,y),plane.getpixel((x,y+60)) or 1)
        for y in range(pose.height):
            for x in range(pose.width):
                if pose.getpixel((x,y)):frame.putpixel((x,y),pose.getpixel((x,y)))
        result[int(selector)]=frame
    return result

class S3kTitleHandAnatomyTest(unittest.TestCase):
    def test_all_phases_are_rotations_of_the_one_glove(self):
        for selector,frame in expected_frames().items():self.assertEqual(frame.tobytes(),Image.open(ART/f's3k-sprite-{selector}.png').tobytes())
    def test_independently_changed_finger_is_detected(self):
        frame=expected_frames()[0];point=(19,15);frame.putpixel(point,(frame.getpixel(point)+1)%14)
        self.assertNotEqual(frame.tobytes(),Image.open(ART/'s3k-sprite-0.png').tobytes())
    def test_five_digit_landmarks_keep_their_side_and_material(self):
        spec=json.loads((ART/'../../../production/mica/titles/s3k-hand-anatomy.json').read_text());master=Image.open(ART/'s3k-hand-master.png');pivot=spec['wrist'];self.assertEqual({'thumb','index','middle','ring','little'},set(spec['landmarks']))
        for angle in spec['relative_angles'].values():
            pose=anatomy.derive(master,pivot,angle);points={name:anatomy.landmark(point,pivot,angle) for name,point in spec['landmarks'].items()};ix,iy=points['index'][0]-pivot[0],points['index'][1]-pivot[1]
            side=lambda pt:ix*(pt[1]-pivot[1])-iy*(pt[0]-pivot[0])
            self.assertLess(side(points['thumb']),0)
            for name in ('middle','ring','little'):self.assertGreater(side(points[name]),0)
            for point in points.values():self.assertIn(pose.getpixel(tuple(round(v) for v in point)),range(8,14))
if __name__=='__main__':unittest.main()
