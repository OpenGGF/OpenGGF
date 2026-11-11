"""Regression contract: no baked glove survives beneath the S3K wag sprites.
Inputs: maintained example indexed title assets. Origin: doubled-thumb report,
2026-11-10. Positive and intentionally broken contexts need no ROM or renderer.
"""
import importlib.util,json,unittest
try:
    import PIL  # noqa: F401  # Pillow is the art pipeline's only third-party dependency
except ModuleNotFoundError:
    raise unittest.SkipTest('Pillow is not installed; the playable-art pipeline tests need it')
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
ART=ROOT/'examples/mica-and-talon/art/mica/titles'
spec=importlib.util.spec_from_file_location('s3k_hand_ownership',ART/'../../../production/mica/titles/s3k_hand_ownership.py');ownership=importlib.util.module_from_spec(spec);spec.loader.exec_module(ownership)

class S3kTitleHandOwnershipTest(unittest.TestCase):
    def setUp(self):
        self.contract=json.loads((ART/'../../../production/mica/titles/s3k-hand-ownership.json').read_text());self.plane=Image.new('P',(320,224));self.plane.paste(Image.open(ART/'s3k-plane-13-left.png'),(0,0));self.plane.paste(Image.open(ART/'s3k-plane-13-right.png'),(160,0));self.frames={i:Image.open(ART/f's3k-sprite-{i}.png') for i in (0,1,4)}
    def test_all_wag_selectors_replace_the_complete_old_hand(self):
        self.assertGreater(self.contract['ownership_pixels'],1000)
        self.assertEqual([],ownership.source_errors(self.plane,self.frames,self.contract))
    def test_transparent_wag_context_is_rejected(self):
        frames={k:v.copy() for k,v in self.frames.items()};point=next(iter(ownership.points_from_spans(self.contract['ownership_rows'])));frames[0].putpixel(point,0)
        self.assertTrue(any(e[0]=='unowned context' for e in ownership.source_errors(self.plane,frames,self.contract)))
    def test_baked_thumb_residue_is_rejected(self):
        point=next(iter(ownership.points_from_spans(self.contract['ownership_rows'])));self.plane.putpixel((point[0]+160,point[1]+60),10)
        self.assertTrue(any(e[0]=='plane glove residue' for e in ownership.source_errors(self.plane,self.frames,self.contract)))
if __name__=='__main__':unittest.main()
