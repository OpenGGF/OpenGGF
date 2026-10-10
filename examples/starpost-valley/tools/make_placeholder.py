"""Generate original Real Valley / Real Ruins placeholders, without any ROM inputs.

Origin: Starpost Valley P1 (2026-10-09, checkpoint 4b1a7af90).
Input: optional output directory, palette line and colour; defaults: levels/valley, 3, 15.
Real Ruins uses levels/ruins, 1, 2 to leave room for Scrap Brain's full line 3.
Output: one constant-colour pattern, empty chunk/block/map/collision records.
All bytes are authored here; no game art or disassembly assets are read.
"""
import struct, sys, pathlib
out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parents[1] / 'src/main/resources/levels/valley'
out.mkdir(parents=True, exist_ok=True)
line = int(sys.argv[2]) if len(sys.argv) > 2 else 3
color = int(sys.argv[3]) if len(sys.argv) > 3 else 15
def fixed(magic, size, records): return magic + struct.pack('>HHI', 1, size, len(records)) + b''.join(records)
(out/'patterns.gptn').write_bytes(fixed(b'GPTN', 32, [bytes([(color << 4) | color]*32)]))
(out/'chunks.gchk').write_bytes(fixed(b'GCHK', 8, [struct.pack('>HHHH', line << 13, line << 13, line << 13, line << 13)]))
(out/'blocks.gblk').write_bytes(b'GBLK' + struct.pack('>HBBI', 1, 8, 0, 1) + bytes(128))
(out/'fg-map.gmap').write_bytes(b'GMAP' + struct.pack('>HHHHI', 1, 1, 1, 1, 1) + bytes(1))
(out/'solid-heights.gshg').write_bytes(fixed(b'GSHG', 16, [bytes(16)]))
(out/'solid-widths.gswd').write_bytes(fixed(b'GSWD', 16, [bytes(16)]))
(out/'solid-angles.gsan').write_bytes(fixed(b'GSAN', 1, [bytes(1)]))
(out/'collision-primary.gcol').write_bytes(b'GCOL' + struct.pack('>HBBI', 1, 0, 2, 1) + bytes(2))
(out/'collision-secondary.gcol').write_bytes(b'GCOL' + struct.pack('>HBBI', 1, 1, 2, 1) + bytes(2))
