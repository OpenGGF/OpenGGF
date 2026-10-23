#!/usr/bin/env python3
"""ROM-free constructed-record tests for stage_inventory's research boundaries.

Run: python3 -B -m unittest discover -s tools/soniccd -p test_stage_inventory.py
Actual-media validation is opt-in through stage_inventory.py's required CLI
arguments; these tests never discover or read commercial media or source assets.
"""
import json
import struct
import tempfile
import unittest
from pathlib import Path

import stage_inventory as s


def mmd(size=0x120, **fields):
    header = dict(flags=0, reserved=0, origin=0, counter=0, entry=s.BASE+0x100,
                  hint=s.BASE+0x106, vint=s.BASE+0x10c)
    header.update(fields)
    data = bytearray(size)
    data[:20] = struct.pack(">BBIHIII", *header.values())
    return bytes(data)


class MmdTests(unittest.TestCase):
    def test_original_and_copy_are_distinct_file_backed_domains(self):
        image = s.Mmd(mmd(origin=0xff1000, counter=3))
        self.assertEqual(image.offset(s.BASE+0x108, 4), 0x108)
        self.assertEqual(image.offset(0xff1008, 4, "RELOCATED_COPY"), 0x108)
        with self.assertRaises(ValueError):
            image.offset(0xff1008, 4)
        with self.assertRaises(ValueError):
            image.offset(0xff1010, 1, "RELOCATED_COPY")

    def test_copy_never_supplies_bytes_beyond_file_end(self):
        image = s.Mmd(mmd(origin=0xff1000, counter=15))
        self.assertEqual(image.header["unresolved_copy_bytes"], 32)
        with self.assertRaises(ValueError):
            image.offset(0xff1020, 1, "RELOCATED_COPY")

    def test_zero_entry_suppresses_copy_as_native_loader_does(self):
        image = s.Mmd(mmd(entry=0, origin=0x1000, counter=0xffff))
        self.assertEqual(image.header["declared_copy_bytes"], 0)

    def test_truncation_copy_overflow_and_native_wrap_rejected(self):
        for record in (mmd()[:255], mmd(origin=1, counter=0xffff),
                       mmd(origin=0xfffffe, counter=1), mmd(size=0x40001)):
            with self.subTest(size=len(record)), self.assertRaises(ValueError):
                s.Mmd(record)

    def test_pointer_domain_and_bounds_fail_closed(self):
        image = s.Mmd(mmd())
        for address, length in ((s.BASE-1, 1), (s.BASE+0x120, 1), (-1, 0), (0x1000000, 0), (s.BASE, -1)):
            with self.subTest(address=address), self.assertRaises(ValueError):
                image.offset(address, length)
        with self.assertRaises(ValueError):
            image.offset(s.BASE, 1, "FABRICATED_ROM")
        self.assertEqual(image.view(0x11f, 1)["end_exclusive"], 0x120)


class DeclarationTests(unittest.TestCase):
    def test_dc_tags_relative_pointers_and_widths(self):
        labels, rows, size = s.dc_region("Index:\ndc.l $3000000|Art\ndc.w List-Index\nList:\ndc.b -1,2\n")
        names = dict(Art=0x220000, **labels)
        data = s.assemble_dc(rows, names)
        self.assertEqual(data, struct.pack(">IHBB", 0x03220000, 6, 255, 2))
        self.assertEqual(size, 8)

    def test_pure_data_include_local_labels_inline_records_and_even(self):
        data, labels, _, length = s.literal_data(".Index:\n dc.w .Frame-.Index\n.Frame: dc.b 2, 3, 4\n even\n")
        self.assertEqual(data, bytes([0, 2, 2, 3, 4, 0]))
        self.assertEqual(labels["Local_Frame"], 2)
        self.assertEqual(length, 6)

    def test_instructions_conditionals_unknown_symbols_and_python_rejected(self):
        for text in ("if DEMO=0", "moveq #0,d0", "X:\nX:"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                s.dc_region(text)
        for text in ("unknown", "__import__('os')", "1/2", "[1,2]", "x.y", "1<<40"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                s.expression(text, {})
        with self.assertRaises(ValueError):
            s.assemble_dc([(0, 1, "256")], {})

    def test_rs_aliases_word_and_long_sizes(self):
        values = s.rs_symbols("rsset BASE+$20\nzero rs.b 0\nx rs.w 1\ny rs.l 1\nSIZE EQU __rs-zero", {"BASE": 0xff0000})
        self.assertEqual((values["zero"], values["x"], values["y"], values["SIZE"]), (0xff0020, 0xff0020, 0xff0022, 6))
        with self.assertRaises(ValueError):
            s.rs_symbols("rsset UNKNOWN")

    def test_pattern_ambiguity_cannot_select_first_match(self):
        for data in (b"aaaa", b"none"):
            with self.assertRaises(ValueError):
                s.unique(data, b"aa", "fixture")
        self.assertEqual(s.unique(b"abcd", b"bc", "fixture"), 1)

    def test_relative_table_is_signed_and_bounded(self):
        data = bytearray(mmd())
        data[0x110:0x114] = struct.pack(">hh", -4, 8)
        rows = s.relative_table(s.Mmd(bytes(data)), 0x110, 2)
        self.assertEqual([row["target_file_offset"] for row in rows], [0x10c, 0x118])
        data[0x112:0x114] = struct.pack(">h", 0x100)
        with self.assertRaises(ValueError):
            s.relative_table(s.Mmd(bytes(data)), 0x110, 2)

    def test_absolute_reference_requires_opcode_and_correct_domain(self):
        data = bytearray(mmd())
        data[0x100:0x106] = bytes.fromhex("43f9")+struct.pack(">I", s.BASE+0x110)
        image = s.Mmd(bytes(data))
        self.assertEqual(s.absolute_refs(image, 0x110, "43f9"), [0x100])
        with self.assertRaises(ValueError):
            s.proof_ref(image, 0x110, "41f9", "wrong register")


LVLDMA_FIXTURE = r"""LVLDMA macro src, dest, len, type
 lea VDPCTRL,a5
 move.l #$94009300|((((\len)/2)&$FF00)<<8)|(((\len)/2)&$FF),(a5)
 move.l #$96009500|((((\src)/2)&$FF00)<<8)|(((\src)/2)&$FF),(a5)
 move.w #$9700|(((\src)>>17)&$7F),(a5)
 VDPCMD move.w,\dest,\type,DMA,>>16,(a5)
 VDPCMD move.w,\dest,\type,DMA,&$FFFF,dmaCmdLow.w
 move.w dmaCmdLow.w,(a5)
 endm"""
VDPCMD_FIXTURE = r"""VDPCTRL EQU $C00004
VRAMDMA EQU $40000080
VDPCMD macro ins, addr, type, rwd, end, end2
 local cmd
cmd = (\type\\rwd\)|(((\addr)&$3FFF)<<16)|((\addr)/$4000)
 if narg=5
 \ins #\#cmd,\end
 elseif narg>=6
 \ins #(\#cmd)\end,\end2
 else
 \ins cmd
 endif
 endm"""
SHIELD_SOURCE = """LoadShieldArt:
 bclr #7,powerup
 beq.s .End
 moveq #0,d0
 move.b powerup,d0
 subq.b #2,d0
 add.w d0,d0
 movea.l ShieldArtIndex(pc,d0.w),a1
 lea aniArtBuffer,a2
 move.w #$FF,d0
.Loop:
 move.l (a1)+,(a2)+
 dbf d0,.Loop
 LVLDMA aniArtBuffer,$A880,$480,VRAM
.End:
 rts
ShieldArtIndex:
 dc.l Art_Shield
 dc.l Art_InvStars
 dc.l Art_TimeStars
 dc.l Art_GameOver
 dc.l Art_TimeOver
"""


class ShieldConsumerTests(unittest.TestCase):
    # A synthetic program with five source blobs and two adjacent overreads.
    # The first word of each .nem-named raw asset deliberately looks like a
    # plausible Nemesis header. Expected lengths/hashes come from literal data.
    def fixture(self):
        data = bytearray(mmd(size=0x2800))
        code = bytes.fromhex(
            "08b9000700ff156b67447000103900ff156b5500d040227b0038"
            "45f900ff1980303c00ff24d951c8fffc"
            "4bf900c000042abc940293402abc968c95c03abc977f"
            "3abc688031fc0082f6403ab8f6404e75")
        self.assertEqual(len(code), 80)
        data[0x200:0x250] = code
        starts = dict(Art_Shield=0x1000, Art_InvStars=0x1480, Art_TimeStars=0x1900,
                      Art_DiagonalSpring=0x1ce0, Art_TimeOver=0x2000, Art_GameOver=0x23c0)
        blobs = dict(Art_Shield=b"S"*1152, Art_InvStars=b"I"*1152,
                     Art_TimeStars=b"T"*992, Art_DiagonalSpring=b"D"*80,
                     Art_TimeOver=b"\x06f"+b"O"*958, Art_GameOver=b"\0\0"+b"G"*1022)
        for name, blob in blobs.items():
            data[starts[name]:starts[name]+len(blob)] = blob
        names = ["Art_Shield", "Art_InvStars", "Art_TimeStars", "Art_GameOver", "Art_TimeOver"]
        data[0x250:0x264] = struct.pack(">5I", *(s.BASE+starts[name] for name in names))
        class ConstructedSource:
            def read(self, path):
                return blobs[Path(path).stem]
            def text(self, path):
                if path == "Level/_Objects/Powerup/Main.asm":
                    return SHIELD_SOURCE
                return LVLDMA_FIXTURE if path == "Level/_Definitions.inc" else VDPCMD_FIXTURE
            def cite(self, path, label):
                return dict(path="src/"+path, label=label, line=1)
        source = ConstructedSource()
        image = s.Mmd(bytes(data))
        variables = dict(powerup=0xff156b, aniArtBuffer=0xff1980,
                         scrlSectSpeeds=0xff1e00, dmaCmdLow=0xfff640)
        assets = {name: s.source_asset(source, image, name, name+".nem", at)
                  for name, at in starts.items()}
        return source, image, variables, assets

    def test_filename_does_not_prove_encoding_or_output_length(self):
        class BlobSource:
            def read(self, _):
                return b"\x06f"+bytes(14)
        data = bytearray(mmd())
        data[0x100:0x110] = b"\x06f"+bytes(14)
        image = s.Mmd(bytes(data))
        for suffix in (".nem", ".bin", ".raw"):
            asset = s.source_asset(BlobSource(), image, "Art", "misleading"+suffix, 0x100)
            self.assertEqual(asset["encoding"], "unknown")
            self.assertNotIn("output_bytes", asset)
            self.assertEqual(asset["consumer_views"], [])

    def test_all_five_raw_views_separate_blobs_dma_and_resident_ram_tail(self):
        source, image, variables, assets = self.fixture()
        proof = s.shield_art_proof(source, image, variables, assets)
        self.assertEqual(proof["file_offset"], 0x250)
        self.assertEqual(proof["code_view"]["file_offset"], 0x200)
        self.assertEqual(len(proof["rows"]), 5)
        for row in proof["rows"]:
            consumer = assets[row["art"]]["consumer_views"][0]
            self.assertEqual(consumer["encoding"], "raw")
            self.assertEqual(consumer["input_view"]["length"], 1024)
            self.assertNotIn("output_bytes", assets[row["art"]])
        time = assets["Art_TimeOver"]["consumer_views"][0]
        stars = assets["Art_TimeStars"]["consumer_views"][0]
        self.assertEqual(time["input_view"]["sha256"], s.sha(b"\x06f"+b"O"*958+b"\0\0"+b"G"*62))
        self.assertEqual(stars["input_view"]["sha256"], s.sha(b"T"*992+b"D"*32))
        self.assertEqual([(x["source_blob"],x["length"]) for x in time["source_segments"]],
                         [("Art_TimeOver",960),("Art_GameOver",64)])
        dma = proof["dma"]
        self.assertEqual((dma["length"], dma["source_copy_bytes"]), (1152,1024))
        self.assertEqual(dma["resident_ram_tail"], dict(native_address=0xff1d80, length=128,
                         contents_status="unverified_resident_RAM_not_file_backed"))
        self.assertEqual(dma["vram_address"], 0xa880)

    def test_changed_extension_bytes_cannot_reuse_a_matched_source_blob(self):
        source, image, variables, assets = self.fixture()
        proof = s.shield_art_proof(source, image, variables, assets)
        self.assertEqual(proof["rows"][2]["consumed_view"]["length"], 1024)
        data = bytearray(image.data)
        data[0x1cff] ^= 1  # final actual byte of the 32-byte DiagonalSpring extension
        with self.assertRaisesRegex(ValueError, "Changed matched source blob"):
            s.shield_art_proof(source, s.Mmd(bytes(data)), variables, assets)
        self.assertEqual(assets["Art_TimeStars"]["length"], 992)

    def test_source_copy_width_count_and_macro_unit_mutations_fail_closed(self):
        for old, new in (("move.l (a1)+,(a2)+", "move.w (a1)+,(a2)+"),
                         ("#$FF,d0", "#$11F,d0"),
                         ("LVLDMA aniArtBuffer,$A880,$480,VRAM", "LVLDMA aniArtBuffer,$A880,$400,VRAM")):
            source, image, variables, assets = self.fixture()
            original = source.text
            source.text = lambda path: original(path).replace(old, new)
            with self.subTest(mutation=new), self.assertRaises(ValueError):
                s.shield_art_proof(source, image, variables, assets)
        source, image, variables, assets = self.fixture()
        original = source.text
        source.text = lambda path: original(path).replace("(\\len)/2", "(\\len)")
        with self.assertRaisesRegex(ValueError, "LVLDMA macro"):
            s.shield_art_proof(source, image, variables, assets)

    def test_instruction_table_and_dma_mutations_fail_closed(self):
        for at in (0x216, 0x218, 0x220, 0x222, 0x226, 0x22c, 0x240, 0x253):
            source, image, variables, assets = self.fixture()
            data = bytearray(image.data)
            data[at] ^= 1
            with self.subTest(offset=hex(at)), self.assertRaises(ValueError):
                s.shield_art_proof(source, s.Mmd(bytes(data)), variables, assets)

    def test_raw_copy_requires_actual_adjacent_bytes_and_dma_fits_ram(self):
        source, image, variables, assets = self.fixture()
        # The TimeOver source blob still fits; its actual copy no longer does.
        # Keep GameOver's other pointer valid by moving this final table entry.
        data = bytearray(image.data)
        start = len(data)
        data.extend(b"O"*960)
        data[0x260:0x264] = struct.pack(">I", s.BASE+start)
        assets["Art_TimeOver"].update(file_offset=start, length=960, end_exclusive=len(data))
        with self.assertRaisesRegex(ValueError, "file-backed extent"):
            s.shield_art_proof(source, s.Mmd(bytes(data)), variables, assets)
        variables["scrlSectSpeeds"] = variables["aniArtBuffer"]+1024
        with self.assertRaisesRegex(ValueError, "buffer"):
            s.shield_art_proof(source, image, variables, assets)

    def test_unattributed_extension_is_rejected_instead_of_padding(self):
        source, image, variables, assets = self.fixture()
        assets.pop("Art_DiagonalSpring")
        with self.assertRaisesRegex(ValueError, "source blob"):
            s.shield_art_proof(source, image, variables, assets)


class NemesisConsumerTests(unittest.TestCase):
    def test_consumer_header_semantics_do_not_depend_on_filename(self):
        class BlobSource:
            def read(self, _):
                return b"\x80\x03"+bytes(14)
        data = bytearray(mmd())
        data[0x100:0x110] = b"\x80\x03"+bytes(14)
        image = s.Mmd(bytes(data))
        for suffix in (".bin", ".nem"):
            asset = s.source_asset(BlobSource(), image, "Art", "Art"+suffix, 0x100)
            s.nemesis_asset(image, asset, "constructed_verified_PLC_row", {"label": "LoadPLCImm"})
            self.assertEqual(asset["encoding"], "Nemesis")
            self.assertEqual(asset["output_bytes"], 96)
            consumer = asset["consumer_views"][0]
            self.assertEqual(consumer["declared_output_bytes"], 96)
            self.assertEqual(consumer["source_blob_view"]["kind"], "SOURCE_BLOB")
            self.assertIn("unverified", consumer["input_extent_status"])
            self.assertIn("unverified", consumer["decoded_output_status"])


class LayoutAndPlacementTests(unittest.TestCase):
    def test_minus_one_layout_dimensions(self):
        info = s.layout_info(bytes([1, 2])+bytes(6))
        self.assertEqual((info["columns"], info["rows"], info["chunk_id_base"]), (2, 3, 1))
        for data in (b"", bytes([1, 2])+bytes(5), bytes([128, 0])+bytes(129), bytes([0, 16])+bytes(17)):
            with self.assertRaises(ValueError):
                s.layout_info(data)

    @staticmethod
    def placement(x=1, object_id=1, periods=0xe0):
        return struct.pack(">HHBBBB", x, 0xc020, object_id, 3, periods, 4)

    def test_sorted_shared_period_records_and_saved_flag(self):
        data = self.placement(object_id=0x81)+self.placement(x=2, periods=0x40)+b"\xff\xff"+bytes(6)
        info = s.object_info(data)
        self.assertEqual(info["count"], 2)
        self.assertEqual(info["period_counts"], dict(past=1, present=2, future=1))
        self.assertEqual(info["ids"], [1])

    def test_invalid_placement_shape_sort_id_and_sentinel_rejected(self):
        end = b"\xff\xff"+bytes(6)
        for data in (self.placement(), self.placement()[:-1], self.placement(object_id=0)+end,
                     self.placement(object_id=65)+end, self.placement(x=2)+self.placement(x=1)+end,
                     end+self.placement()):
            with self.subTest(length=len(data)), self.assertRaises(ValueError):
                s.object_info(data)


class SourceAndCarryTests(unittest.TestCase):
    def test_native_disc_header_must_match_source_region(self):
        data = bytearray(b" "*0x200)
        for at, text in ((0, "SEGADISCSYSTEM"), (16, "EXAMPLE"), (0x50, "01021993"), (0x180, "GM EXAMPLE"), (0x1f0, "U")):
            data[at:at+len(text)] = text.encode()
        expected = dict(disc_header_volume_id="EXAMPLE", source_build_date="01021993", product_id="GM EXAMPLE", header_market="U")
        self.assertEqual(s.disc_header(bytes(data), expected)["market"], "U")
        expected["header_market"] = "J"
        with self.assertRaisesRegex(ValueError, "source region"):
            s.disc_header(bytes(data), expected)

    def test_source_content_is_checked_against_git_blob_pin(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/"src").mkdir()
            path = root/"src/example.asm"
            data = b"Example:\n dc.w 1\n"
            path.write_bytes(data)
            source = s.Source.__new__(s.Source)
            source.root, source.used = root, {}
            source.tree = {"src/example.asm": s.hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()}
            self.assertEqual(source.cite("example.asm", "Example")["line"], 1)
            path.write_text("Example:\n dc.w 2\n")
            with self.assertRaisesRegex(ValueError, "Modified pinned"):
                source.read("example.asm")

    def test_carry_mapping_reencodes_native_fields_and_rejects_changed_operand(self):
        class ConstructedSource:
            def text(self, _):
                return "LoadTimeWarpData:\n"+"\n".join(f"move.w from{i},field(a6)" for i in range(20))+"\n\tcmpi.b #6,zone"
            def cite(self, path, label):
                return dict(path=path, label=label, line=1)
        variables = {f"from{i}": 0xff1000+2*i for i in range(20)}
        variables["field"] = 8
        code = b"".join(struct.pack(">HIH", 0x3d79, 0xff1000+2*i, 8) for i in range(20))
        data = bytearray(mmd(size=0x200))
        data[0x120:0x120+len(code)] = code
        image = s.Mmd(bytes(data))
        result = s.carry_prefix(ConstructedSource(), image, variables)
        self.assertEqual(result["file_offset"], 0x120)
        self.assertEqual(len(result["fields"]), 20)
        variables["from5"] += 2
        with self.assertRaisesRegex(ValueError, "expected one native match"):
            s.carry_prefix(ConstructedSource(), image, variables)


def dual(value, width):
    return value.to_bytes(width, "little")+value.to_bytes(width, "big")


def iso_record(name, lba, length, flags=0):
    name = name.encode("ascii")
    record = bytearray(33+len(name)+(len(name) % 2 == 0))
    record[0] = len(record)
    record[2:10] = dual(lba, 4)
    record[10:18] = dual(length, 4)
    record[25] = flags
    record[28:32] = dual(1, 2)
    record[32] = len(name)
    record[33:33+len(name)] = name
    return bytes(record)


def raw_fixture():
    logical = bytearray(24*2048)
    pvd = bytearray(2048)
    pvd[:7] = b"\x01CD001\x01"
    pvd[80:88] = dual(24, 4)
    pvd[128:132] = dual(2048, 2)
    pvd[156:190] = iso_record("\x00", 20, 2048, 2)
    logical[16*2048:17*2048] = pvd
    rows = iso_record("\x00", 20, 2048, 2)+iso_record("\x01", 20, 2048, 2)+iso_record("R11A__.MMD;1", 21, 2300)
    logical[20*2048:20*2048+len(rows)] = rows
    logical[21*2048:21*2048+2300] = bytes(i % 251 for i in range(2300))
    output = bytearray()
    for at in range(0, len(logical), 2048):
        raw = bytearray(2352)
        raw[:12] = b"\0"+b"\xff"*10+b"\0"
        raw[15] = 1
        raw[16:2064] = logical[at:at+2048]
        output.extend(raw)
    return output


class RawIsoTests(unittest.TestCase):
    def write_raw(self, directory, raw):
        path = Path(directory)/"constructed.bin"
        path.write_bytes(raw)
        return path

    def test_cross_sector_file_read_without_export(self):
        with tempfile.TemporaryDirectory() as directory:
            with s.RawIso(self.write_raw(directory, raw_fixture())) as disc:
                self.assertEqual(disc.file("R11A__.MMD;1"), bytes(i % 251 for i in range(2300)))
                with self.assertRaises(ValueError):
                    disc.file("MISSING;1")
                with self.assertRaises(ValueError):
                    disc.read(24*2048, 1)

    def test_invalid_mode_endian_and_extent_rejected(self):
        for mutation in ("mode", "endian", "root_extent", "partial", "multiextent", "duplicate"):
            raw = raw_fixture()
            pvd = 16*2352+16
            root = 20*2352+16
            if mutation == "mode": raw[16*2352+15] = 2
            if mutation == "endian": raw[pvd+80] ^= 1
            if mutation == "root_extent": raw[pvd+156+2:pvd+156+10] = dual(24, 4)
            if mutation == "partial": raw.pop()
            if mutation == "multiextent": raw[root+68+25] = 128
            if mutation == "duplicate":
                record = iso_record("R11A__.MMD;1", 21, 2300)
                raw[root+68+len(record):root+68+2*len(record)] = record
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(ValueError):
                    with s.RawIso(self.write_raw(directory, raw)):
                        pass

    def test_input_mutation_is_reported_on_close(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.write_raw(directory, raw_fixture())
            disc = s.RawIso(path)
            with path.open("ab") as output:
                output.write(b"changed")
            with self.assertRaisesRegex(ValueError, "changed during"):
                disc.close()
            self.assertTrue(disc.stream.closed)


class OutputTests(unittest.TestCase):
    def test_external_json_only_and_explicit_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/"inventory.json"
            s.write_output(path, {"z": 1, "a": 2})
            expected = path.read_bytes()
            with self.assertRaises(FileExistsError):
                s.write_output(path, {"bad": 1})
            self.assertEqual(path.read_bytes(), expected)
            s.write_output(path, {"new": 1}, overwrite=True)
            self.assertEqual(json.loads(path.read_text()), {"new": 1})
            for bad in (s.ROOT/"inventory.json", s.ROOT.parents[1]/"inventory.json", Path(directory)/"asset.bin"):
                with self.subTest(path=bad), self.assertRaises(ValueError):
                    s.external_output(bad)

    def test_symlink_cannot_bypass_output_guard(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/"inventory.json"
            target = Path(directory)/"target.json"
            target.write_text("preserve")
            path.symlink_to(target)
            with self.assertRaises(ValueError):
                s.write_output(path, {}, overwrite=True)
            self.assertEqual(target.read_text(), "preserve")


if __name__ == "__main__":
    unittest.main()
