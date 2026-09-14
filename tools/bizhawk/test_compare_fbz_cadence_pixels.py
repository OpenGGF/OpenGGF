"""Standalone comparator safety tests; requires Pillow like the visual comparator."""
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from PIL import Image

spec = importlib.util.spec_from_file_location('pair', Path(__file__).with_name('compare_fbz_cadence_pixels.py'))
pair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pair)

class PairedPixelsTest(unittest.TestCase):
    def test_actual_source_pixels_pair_and_corruption_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            native, engine = root/'native', root/'engine'
            native.mkdir(); (engine/'provenance').mkdir(parents=True)
            vram = bytearray(65536); vram[0xc000:0xc002] = (0x208).to_bytes(2, 'big')
            vram[0x208*32:0x209*32] = bytes([0x11])*32
            cram = bytearray(128); cram[2:4] = b'\x00\x02'
            native_image = Image.new('RGB', (348,240))
            for y in range(8):
                for x in range(8): native_image.putpixel((14+x,8+y),(34,0,0))
            engine_image = Image.new('RGB',(320,224))
            for y in range(8):
                for x in range(8): engine_image.putpixel((x,y),(36,0,0))
            packed = b''.join(struct.pack('>HHHBB',x,y,0x208,128+y*8+x,1)
                              for y in range(8) for x in range(8))
            payload = pair.sha(vram[0x208*32:0x210*32])
            for i in range(6):
                stem = native/f'sample-{100+i}'
                for suffix,data in [('.vram',vram),('.cram',cram),('.vsram',bytes(80))]:
                    stem.with_suffix(suffix).write_bytes(data)
                native_image.save(stem.with_suffix('.png'))
                engine_image.save(engine/f'frame-{i}.png')
                receipt = {'capture_index':i,'pattern_payload_sha256':payload,
                           'destination_pixel_evidence':{'gpu_destination_sha256':payload,
                             'source_pixel_mask':{'records_hex':packed.hex(),
                              'uploaded_palette_rgb24':[0,36<<16]+[0]*62}}}
                (engine/'provenance'/f'frame-{i}.json').write_text(json.dumps(receipt))
            args = SimpleNamespace(engine_series=engine,native_series=native,native_frame_start=100,
                                   native_region=[0,0,8,8],channel='208',output=root/'output')
            rows = pair.compare(args)['frames']
            self.assertTrue(all(r['equal_source_pixels']==64 and r['native_unrepresented_source_pixels']==0 for r in rows))
            engine_image.putpixel((3,3),(0,0,0)); engine_image.save(engine/'frame-0.png')
            with self.assertRaisesRegex(ValueError,'mask/framebuffer'): pair.compare(args)
            engine_image.putpixel((3,3),(36,0,0)); engine_image.save(engine/'frame-0.png')
            path=engine/'provenance/frame-0.json'; receipt=json.loads(path.read_text())
            receipt['pattern_payload_sha256']='0'*64; path.write_text(json.dumps(receipt))
            with self.assertRaisesRegex(ValueError,'presentation phases'): pair.compare(args)

if __name__ == '__main__': unittest.main()
