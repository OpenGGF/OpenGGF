#!/usr/bin/env python3
"""Read-only stage-data research, originating in the 2026-10-23 CD01/04/10 task.

Inputs: supplied split MODE1/2352 CUE/BINs and scddisasm at SOURCE_PIN.
Output: deterministic external JSON metadata, never commercial byte payloads.
This is an analysis tool, not a runtime loader or a general 68000 assembler.
Only R11A receives semantic descriptors; other retail stages receive MMD facts.
Source blobs are matched against media in memory, then native tables and their
consumer instruction encodings are checked. No disassembly assets are exported.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import json
import os
import re
import struct
import subprocess
from pathlib import Path

import catalogue
from retail_profiles import parse_cue

SOURCE_PIN = "1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff"
ROOT = Path(__file__).resolve().parents[2]
BASE = 0x200000
PP = "Level/Palmtree Panic/"
DATA = PP + "Data (Act 1 Present).asm"
SCROLL = PP + "Scroll (Act 1 Present).asm"
PAL = PP + "Palette Data (Act 1 Present).asm"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def bounded(data, offset, length):
    require(0 <= offset <= len(data) and 0 <= length <= len(data) - offset,
            f"Outside file-backed extent: {offset:#x}+{length:#x}/{len(data):#x}")
    return data[offset:offset + length]


def u16(data, offset=0):
    return int.from_bytes(bounded(data, offset, 2), "big")


def u32(data, offset=0):
    return int.from_bytes(bounded(data, offset, 4), "big")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def occurrences(data, needle):
    require(bool(needle), "Empty search pattern")
    result, cursor = [], 0
    while (cursor := data.find(needle, cursor)) >= 0:
        result.append(cursor)
        cursor += 1
    return result


def unique(data, needle, name):
    hits = occurrences(data, needle)
    require(len(hits) == 1, f"{name}: expected one native match, got {len(hits)}")
    return hits[0]


class Mmd:
    """The file-backed domains used by CdMmdImage; no uninitialised RAM reads."""
    def __init__(self, data):
        require(0x100 <= len(data) <= 0x40000, "Invalid MMD file extent")
        self.data = data
        flags, reserved, origin, size, entry, hint, vint = struct.unpack(">BBIHIII", data[:20])
        count = (size + 1) * 4 if origin and entry else 0
        require(count <= 0x3ff00, "Copy exceeds source Word RAM")
        require((origin & 0xffffff) + count <= 0x1000000, "Copy wraps native address space")
        self.header = dict(flags=flags, reserved=reserved, origin=origin, size_counter=size,
                           entry=entry, horizontal_interrupt=hint, vertical_interrupt=vint,
                           declared_copy_bytes=count, unresolved_copy_bytes=max(0, count-len(data)+0x100))

    def offset(self, address, length=1, domain="ORIGINAL_WORD_RAM"):
        require(0 <= address < 0x1000000, "Pointer is not a 24-bit native address")
        if domain == "ORIGINAL_WORD_RAM":
            offset = address - BASE
        elif domain == "RELOCATED_COPY":
            size = min(self.header["declared_copy_bytes"], len(self.data)-0x100)
            relative = address - (self.header["origin"] & 0xffffff)
            require(0 <= relative <= size and 0 <= length <= size-relative, "Outside relocated copy")
            offset = relative + 0x100
        else:
            raise ValueError("Unknown MMD domain")
        bounded(self.data, offset, length)
        return offset

    def view(self, offset, length):
        data = bounded(self.data, offset, length)
        return dict(file_offset=offset, length=length, end_exclusive=offset+length,
                    domain="ORIGINAL_WORD_RAM", native_address=BASE+offset, sha256=sha(data))


class RawIso:
    """Deliberately narrow reader for the supplied split raw retail data tracks.

    This does not certify a disc or replace the production CUE/ISO readers.
    Redundant ISO fields, sector mode, extents and file limits are checked.
    """
    def __init__(self, track):
        self.path = Path(track)
        self.stream = self.path.open("rb")
        try:
            self.before = self.path.stat()
            require(self.before.st_size % 2352 == 0, "Partial raw sector")
            pvd = self.read(16*2048, 2048)
            require(pvd[:7] == b"\x01CD001\x01", "Expected ISO primary volume descriptor")
            require(self.both(pvd, 128, 2) == 2048, "Unexpected logical block size")
            self.volume_bytes = self.both(pvd, 80, 4)*2048
            require(self.volume_bytes <= self.before.st_size//2352*2048, "ISO exceeds track")
            root = self.record(pvd, 156)
            require(root["flags"] == 2, "Invalid root directory")
            directory = self.read(root["lba"]*2048, root["size"])
            self.files = {}
            cursor = 0
            while cursor < len(directory):
                if directory[cursor] == 0:
                    cursor = (cursor//2048+1)*2048
                    continue
                entry = self.record(directory, cursor)
                require(cursor//2048 == (cursor+entry["record_bytes"]-1)//2048,
                        "Directory record crosses sector")
                cursor += entry["record_bytes"]
                if entry["name"] in ("\x00", "\x01"):
                    continue
                require(entry["flags"] == 0, "Only single-extent root files supported")
                require(entry["name"] not in self.files, "Duplicate ISO filename")
                self.files[entry["name"]] = entry
        except BaseException:
            self.stream.close()
            raise

    @staticmethod
    def both(data, offset, width):
        raw = bounded(data, offset, width*2)
        value = int.from_bytes(raw[:width], "little")
        require(value == int.from_bytes(raw[width:], "big"), "ISO endian disagreement")
        return value

    def record(self, data, offset):
        size = bounded(data, offset, 1)[0]
        record = bounded(data, offset, size)
        require(size >= 34 and record[1] == 0 and record[26:28] == b"\0\0", "Unsupported ISO record")
        require(self.both(record, 28, 2) == 1, "Unexpected volume sequence")
        name = bounded(record, 33, record[32]).decode("ascii")
        lba, length = self.both(record, 2, 4), self.both(record, 10, 4)
        require(lba*2048+length <= self.volume_bytes, "File exceeds ISO volume")
        return dict(name=name, lba=lba, size=length, flags=record[25], record_bytes=size)

    def read(self, offset, length):
        capacity = self.before.st_size//2352*2048
        require(0 <= offset <= capacity and 0 <= length <= capacity-offset, "Read exceeds data track")
        result = bytearray()
        while length:
            sector, inside = divmod(offset, 2048)
            self.stream.seek(sector*2352)
            raw = self.stream.read(2352)
            require(len(raw) == 2352 and raw[:12] == b"\0"+b"\xff"*10+b"\0" and raw[15] == 1,
                    "Invalid MODE1/2352 sector")
            count = min(length, 2048-inside)
            result.extend(raw[16+inside:16+inside+count])
            offset += count
            length -= count
        return bytes(result)

    def file(self, name):
        require(name in self.files, f"Missing retail file {name}")
        item = self.files[name]
        require(item["size"] <= 0x40000, "Stage exceeds Word RAM")
        return self.read(item["lba"]*2048, item["size"])

    def close(self):
        try:
            after = self.path.stat()
            require((after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns) ==
                    (self.before.st_ino, self.before.st_size, self.before.st_mtime_ns, self.before.st_ctime_ns),
                    "Source data track changed during inspection")
        finally:
            self.stream.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


class Source:
    def __init__(self, root):
        self.root = Path(root).resolve()
        def git(*args):
            return subprocess.check_output(["git", "-C", str(self.root), *args])
        require(git("rev-parse", "HEAD").decode().strip() == SOURCE_PIN, "Wrong scddisasm source pin")
        self.tree = {}
        for row in git("ls-tree", "-rz", SOURCE_PIN).split(b"\0"):
            if row:
                attributes, name = row.split(b"\t")
                self.tree[name.decode()] = attributes.split()[2].decode()
        self.used = {}

    def read(self, path):
        key = path if path == "make.sh" else "src/"+path
        require(key in self.tree, f"Unpinned source input: {key}")
        resolved = (self.root/key).resolve()
        require(resolved.is_relative_to(self.root), "Source escapes checkout")
        data = resolved.read_bytes()
        oid = hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
        require(oid == self.tree[key], f"Modified pinned source input: {key}")
        self.used[key] = dict(git_blob=oid, sha256=sha(data))
        return data

    def text(self, path):
        return self.read(path).decode()

    def cite(self, path, label):
        lines = self.text(path).splitlines()
        hits = [i+1 for i, line in enumerate(lines)
                if re.match(r"^"+re.escape(label)+r"(?::(?:\s|$)|\s+(?:macro|rs\.[bwl])(?:\s|$))", line)]
        require(len(hits) == 1, f"Missing/ambiguous source label {path}:{label}")
        return dict(path="src/"+path, label=label, line=hits[0])


def expression(text, names=None):
    """Restricted dc expression evaluator; never eval() source text."""
    tree = ast.parse(re.sub(r"\$([0-9a-fA-F]+)", r"0x\1", text.strip()), mode="eval")
    def visit(node):
        if isinstance(node, ast.Constant) and type(node.value) is int:
            return node.value
        if isinstance(node, ast.Name) and names is not None and node.id in names:
            return names[node.id]
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
            return -visit(node.operand)
        if isinstance(node, ast.BinOp):
            left, right = visit(node.left), visit(node.right)
            if isinstance(node.op, ast.Add): return left+right
            if isinstance(node.op, ast.Sub): return left-right
            if isinstance(node.op, ast.BitOr): return left | right
            if isinstance(node.op, ast.Mult): return left*right
            if isinstance(node.op, ast.Div):
                require(right != 0 and left % right == 0, "Nonintegral assembly division")
                return left//right
        raise ValueError(f"Unsupported dc expression: {text}")
    return visit(tree.body)


def dc_region(text):
    """Parse only straight dc.b/w/l blocks, with explicit global labels."""
    labels, entries, cursor = {}, [], 0
    for raw in text.splitlines():
        line = raw.split(";", 1)[0].strip()
        if not line:
            continue
        if label := re.match(r"^(\w+):\s*(.*)$", line):
            require(label[1] not in labels, "Duplicate dc label")
            labels[label[1]] = cursor
            line = label[2]
            if not line:
                continue
        if line == "even":
            if cursor % 2:
                entries.append((cursor, 1, "0"))
                cursor += 1
            continue
        match = re.fullmatch(r"dc\.([bwl])\s+(.+)", line)
        require(match is not None, f"Not a straight dc block: {line}")
        width = {"b": 1, "w": 2, "l": 4}[match[1]]
        for item in match[2].split(","):
            entries.append((cursor, width, item.strip()))
            cursor += width
    return labels, entries, cursor


def assemble_dc(entries, names):
    output = bytearray()
    for offset, width, item in entries:
        require(offset == len(output), "Nonconsecutive dc entries")
        value = expression(item, names)
        require(-(1 << (width*8-1)) <= value < 1 << (width*8), "dc field overflow")
        output.extend((value & ((1 << (width*8))-1)).to_bytes(width, "big"))
    return bytes(output)


def scalar_table(source, path, label):
    text = source.text(path).split(label+":", 1)[1]
    lines = []
    for raw in text.splitlines():
        line = raw.split(";", 1)[0].strip()
        if not line:
            continue
        if not line.startswith("dc."):
            break
        lines.append(line)
    _, entries, _ = dc_region("\n".join(lines))
    require(bool(entries), f"No scalar table {label}")
    return assemble_dc(entries, {})


def literal_data(text):
    """Resolve local labels in a pure data include; no code or binary includes."""
    text = re.sub(r"(?<!\w)\.(\w+)", r"Local_\1", text)
    labels, entries, extent = dc_region(text)
    return assemble_dc(entries, labels), labels, entries, extent


def absolute_refs(image, offset, opcode):
    """Verify an explicit 68000 absolute-long operand, not arbitrary pointer hits."""
    return [at for at in occurrences(image.data, bytes.fromhex(opcode)+struct.pack(">I", BASE+offset))
            if 0x100 <= at < getattr(image, "code_end", len(image.data)) and at % 2 == 0]


def proof_ref(image, offset, opcode, name):
    refs = absolute_refs(image, offset, opcode)
    require(bool(refs), f"Missing native consumer {name}")
    return [dict(instruction_file_offset=i, target_file_offset=offset,
                 addressing="absolute_long", opcode=opcode) for i in refs]


def relative_table(image, offset, count):
    rows = []
    for slot in range(count):
        displacement = struct.unpack(">h", bounded(image.data, offset+2*slot, 2))[0]
        target = offset+displacement
        bounded(image.data, target, 1)
        rows.append(dict(slot=slot, displacement=displacement, target_file_offset=target))
    return rows


def layout_info(data):
    require(len(data) >= 2, "Truncated layout header")
    width, height = data[0]+1, data[1]+1
    require(width <= 0x80 and height <= 0x10, "Layout exceeds native RAM strides")
    require(len(data) == 2+width*height, "Layout dimensions disagree with extent")
    return dict(columns=width, rows=height, ram_row_stride=0x80,
                chunk_pixels=256, chunk_id_mask=0x7f, loop_flag=0x80, chunk_id_base=1, blank_chunk_id=0)


def rs_symbols(text, names=None):
    """Interpret straight rs declarations, not conditional assembly or macros."""
    values, cursor = dict(names or {}), 0
    for raw in text.splitlines():
        line = raw.split(";", 1)[0].strip()
        if not line:
            continue
        if line == "rsreset":
            cursor = 0
        elif line.startswith("rsset\t") or line.startswith("rsset "):
            cursor = expression(line.split(None, 1)[1], values)
        elif match := re.fullmatch(r"(\w+)\s+EQU\s+(.+)", line):
            values["__rs"] = cursor
            values[match[1]] = expression(match[2], values)
        elif match := re.fullmatch(r"(?:(\w+)\s+)?rs\.([bwl])\s+(.+)", line):
            if match[1]:
                values[match[1]] = cursor
            cursor += {"b": 1, "w": 2, "l": 4}[match[2]]*expression(match[3], values)
        else:
            raise ValueError(f"Unsupported rs declaration: {line}")
    return values


def native_variables(source):
    cpu = source.text("_Include/Main CPU.inc")
    match = re.search(r"^WORKRAM\s+EQU\s+(\$[0-9A-F]+)", cpu, re.M)
    require(match is not None, "Missing Work RAM constant")
    base = dict(WORKRAM=expression(match[1]))
    globals_text = source.text("_Include/Main CPU Variables.inc")
    globals_text = globals_text[globals_text.index("\trsreset"):]
    values = rs_symbols(globals_text, base)
    defs = source.text("Level/_Definitions.inc")
    size = expression(re.search(r"^oSize\s+EQU\s+([^;\n]+)", defs, re.M)[1])
    require("rept\toSize\noVar\\$c\t\tEQU\tc" in defs, "Unknown object-variable macro")
    values.update({f"oVar{i:02X}": i for i in range(size)})
    values["oSize"] = size
    defs = defs[defs.index("\trsreset"):defs.index("; VDP DMA from 68000 memory to VDP memory")]
    return rs_symbols(defs, values)


def carry_prefix(source, image, variables):
    """Independently encode only the straight native MOVE prefix of warp restore.

    Stops before the first conditional; it never interprets or ports gameplay.
    Source names and field widths must encode to the actual retail instructions.
    """
    path = "Level/Load Saved Data.asm"
    text = source.text(path).split("LoadTimeWarpData:\n", 1)[1].split("\tcmpi.b", 1)[0]
    code, fields = bytearray(), []
    for raw in text.splitlines():
        line = raw.split(";", 1)[0].strip()
        if not line:
            continue
        match = re.fullmatch(r"move\.([bwl])\s+(\w+),(\w+)(\.w|\(a6\))?", line)
        require(match is not None, f"Unsupported carry MOVE: {line}")
        width, src, dst, form = match.groups()
        require(src in variables and dst in variables, "Unknown carry field")
        # MOVE base word plus destination mode/register and absolute-long source.
        opcode = {"b": 0x1000, "w": 0x3000, "l": 0x2000}[width] | 0x39
        if form == "(a6)":
            opcode |= (6 << 9) | (5 << 6)
            destination = struct.pack(">H", variables[dst])
            target = dict(object_register="a6", field_offset=variables[dst])
        elif form == ".w":
            opcode |= (0 << 9) | (7 << 6)
            destination = struct.pack(">H", variables[dst] & 0xffff)
            target = dict(native_ram_address=variables[dst] & 0xffffff)
        else:
            opcode |= (1 << 9) | (7 << 6)
            destination = struct.pack(">I", variables[dst] & 0xffffffff)
            target = dict(native_ram_address=variables[dst] & 0xffffff)
        relative = len(code)
        code.extend(struct.pack(">HI", opcode, variables[src] & 0xffffffff)+destination)
        fields.append(dict(source_field=src, destination_field=dst, bytes={"b": 1, "w": 2, "l": 4}[width],
                           instruction_relative_offset=relative, source_native_ram_address=variables[src] & 0xffffff,
                           destination=target))
    require(len(fields) == 20, "Warp prefix field count changed")
    start = unique(image.data, code, "LoadTimeWarpData MOVE prefix")
    return dict(**image.view(start, len(code)), source=source.cite(path, "LoadTimeWarpData"), fields=fields,
                proof="source_rs_fields_independently_encoded_as_native_MOVE_instructions", scope="straight_prefix_only")


def event_proof(source, image, variables):
    path = "Level/_Events.asm"
    # First PPZ1 comparisons are distinctive; unlike labels, operand values
    # and addressing modes are checked against the native program.
    signature = struct.pack(">HHI", 0x0c39, 1, variables["timeZone"] & 0xffffffff)
    candidates = []
    for at in occurrences(image.data, signature):
        if image.data[at+8] == 0x66 and image.data[at+10:at+14] == bytes.fromhex("0c781c16"):
            candidates.append(at)
    require(len(candidates) == 1, "Ambiguous native LevEvents_PPZ1")
    ppz1 = candidates[0]
    index = ppz1-6
    rows = relative_table(image, index, 3)
    require(rows[0]["target_file_offset"] == ppz1, "PPZ1 dispatch target mismatch")
    ppz2 = ppz1+10+struct.unpack("b", image.data[ppz1+9:ppz1+10])[0]
    require(rows[1]["target_file_offset"] == ppz2, "PPZ1 early branch disagrees with PPZ2 slot")
    # Independently encode the complete bounded PPZ1 routine. This subset is
    # intentionally local: unsupported source changes fail instead of guessing.
    body = source.text(path).split("LevEvents_PPZ1:\n", 1)[1].split("LevEvents_PPZ2:\n", 1)[0]
    encoded, local_labels, fixups = bytearray(), {"LevEvents_PPZ2": ppz2-ppz1}, []
    for raw in body.splitlines():
        line = raw.split(";", 1)[0].strip()
        if not line:
            continue
        if line.endswith(":"):
            local_labels[line[:-1]] = len(encoded)
        elif match := re.fullmatch(r"(bne|beq|bcs|bcc)\.s\s+([.\w]+)", line):
            opcode = {"bne": 0x66, "beq": 0x67, "bcs": 0x65, "bcc": 0x64}[match[1]]
            fixups.append((len(encoded), match[2]))
            encoded.extend(bytes([opcode, 0]))
        elif match := re.fullmatch(r"(cmpi\.w|move\.w)\s+#([^,]+),(.+)\.w", line):
            opcode = 0x0c78 if match[1] == "cmpi.w" else 0x31fc
            encoded.extend(struct.pack(">HHH", opcode, expression(match[2], variables), expression(match[3], variables) & 0xffff))
        elif match := re.fullmatch(r"cmpi\.b\s+#([^,]+),(\w+)", line):
            encoded.extend(struct.pack(">HHI", 0x0c39, expression(match[1], variables), variables[match[2]]))
        elif match := re.fullmatch(r"tst\.b\s+(\w+)", line):
            encoded.extend(struct.pack(">HI", 0x4a39, variables[match[1]]))
        elif line == "rts":
            encoded.extend(bytes.fromhex("4e75"))
        else:
            raise ValueError(f"Unsupported PPZ1 instruction: {line}")
    for at, name in fixups:
        require(name in local_labels, "Unknown PPZ1 branch target")
        displacement = local_labels[name]-at-2
        require(-128 <= displacement <= 127 and displacement != 0, "Invalid PPZ1 short branch")
        encoded[at+1] = displacement & 0xff
    require(len(encoded) == ppz2-ppz1 and bounded(image.data, ppz1, len(encoded)) == encoded,
            "Complete native PPZ1 routine differs from source field/branch encoding")
    # PPZ index sequence contains PC-relative indexed operand extensions.
    owner = index-18
    expected = bytes.fromhex("70001039")+struct.pack(">I", variables["act"] & 0xffffffff)+bytes.fromhex("d040303b00064efb0002")
    require(bounded(image.data, owner, 18) == expected, "PPZ act dispatch instructions differ")
    zone_index = owner-14
    zone_rows = relative_table(image, zone_index, 7)
    require(zone_rows[0]["target_file_offset"] == owner, "PPZ zone dispatch mismatch")
    names = ["LevEvents_PPZ", "LevEvents_CCZ", "LevEvents_TTZ", "LevEvents_QQZ", "LevEvents_WWZ", "LevEvents_SSZ", "LevEvents_MMZ"]
    for row, name in zip(zone_rows, names, strict=True):
        row.update(source=source.cite(path, name), name_proof="source_dispatch_slot_order")
    for row, name in zip(rows, ["LevEvents_PPZ1", "LevEvents_PPZ2", "LevEvents_PPZ3"], strict=True):
        row["source"] = source.cite(path, name)
    return dict(zone_dispatch=dict(**image.view(zone_index, 14), rows=zone_rows),
                act_dispatch=dict(**image.view(index, 6), rows=rows, source=source.cite(path, "LevEvents_PPZ_Index")),
                ppz1_code_view=image.view(ppz1, ppz2-ppz1), source=source.cite(path, "LevEvents_PPZ1"),
                proof="complete_PPZ1_instruction_encoding_act_index_and_relative_dispatch_targets",
                behavior_status="source_catalogue_only_no_execution_parity")


def scroll_proof(source, image, variables, tables, region):
    signature = struct.pack(">HH", 0x4a38, variables["scrollLock"] & 0xffff)+bytes.fromhex("67024e75")
    signature += b"".join(struct.pack(">HH", 0x4278, variables[name] & 0xffff)
                           for name in ("scrollFlags", "scrollFlagsBg", "scrollFlagsBg2", "scrollFlagsBg3"))
    start = unique(image.data, signature, "LevelScroll lock/clear prefix")
    order = ["RunLevelEvents", "ScrollCamX", "ScrollCamY"] if region == "USA" else ["ScrollCamX", "ScrollCamY", "RunLevelEvents"]
    calls = []
    for index, name in enumerate(order):
        at = start+len(signature)+4*index
        require(u16(image.data, at) == 0x6100, "Expected native word-displacement BSR")
        target = at+2+struct.unpack(">h", bounded(image.data, at+2, 2))[0]
        bounded(image.data, target, 2)
        calls.append(dict(source_label=name, instruction_file_offset=at, target_file_offset=target))
    # Verify code-side longword cloud speed increments directly against the
    # source's nineteen ADDI.L immediates, not a guessed parallax descriptor.
    scroll = source.text(SCROLL)
    constants = re.findall(r"^\s*addi\.l\s+#(\$[0-9A-F]+),\(a2\)\+", scroll, re.M)
    require(len(constants) == 19, "Cloud speed increment count changed")
    code = b"".join(struct.pack(">HI", 0x069a, expression(item)) for item in constants)
    cloud = unique(image.data, code, "cloud speed immediate instructions")
    upside = scalar_table(source, SCROLL, "CloudUpsideDownSectSizes")
    at = tables["CloudSectSizes"]["end_exclusive"]
    require(bounded(image.data, at, len(upside)) == upside, "Upside-down cloud sections differ")
    tables["CloudUpsideDownSectSizes"] = dict(**image.view(at, len(upside)), source=source.cite(SCROLL, "CloudUpsideDownSectSizes"),
        consumers=proof_ref(image, at, "47f9", "lea CloudUpsideDownSectSizes,a3"))
    # Scalar tables are short but checked with explicit consumers and adjacency.
    return dict(source=source.cite(SCROLL, "LevelScroll"), prefix_view=image.view(start, len(signature)+12), calls=calls,
                cloud_increment_instructions=image.view(cloud, len(code)),
                cloud_increment_fixed_point_values=[expression(item) for item in constants],
                scope="verified_data_and_native_instruction_operands; deformation_loop_not_executed")


def object_info(data, dispatch_count=64):
    require(len(data) % 8 == 0, "Object placement record extent")
    rows, previous, terminator = [], -1, False
    for offset in range(0, len(data), 8):
        x, packed_y, encoded, subtype, periods, extra = struct.unpack(">HHBBBB", data[offset:offset+8])
        if x == 0xffff:
            require(offset+8 == len(data), "Data after placement sentinel")
            terminator = True
            break
        require(x >= previous, "Unsorted object placements")
        require(1 <= encoded & 0x7f <= dispatch_count, "Placement object outside dispatch")
        rows.append(dict(record_file_relative_offset=offset, object_id=encoded & 0x7f,
                         saved_flags=bool(encoded & 0x80), period_flags=periods))
        previous = x
    require(terminator, "Missing object sentinel")
    return dict(record_bytes=8, count=len(rows), sentinel_relative_offset=len(data)-8,
                ids=sorted({row["object_id"] for row in rows}),
                period_mask_by_name=dict(past=0x20, present=0x40, future=0x80),
                period_counts={name: sum(bool(row["period_flags"] & bit) for row in rows)
                               for name, bit in (("past", 0x20), ("present", 0x40), ("future", 0x80))},
                present_ids=sorted({row["object_id"] for row in rows if row["period_flags"] & 0x40}),
                fields=[dict(offset=0, bytes=2, name="x", encoding="unsigned_big_endian"),
                        dict(offset=2, bytes=2, name="y_and_flip_flags", y_mask=0xfff, flip_bits=[14, 15]),
                        dict(offset=4, bytes=1, name="id_and_saved_flag", id_mask=0x7f, saved_flag=0x80),
                        dict(offset=5, bytes=1, name="subtype"), dict(offset=6, bytes=1, name="time_zone_flags"),
                        dict(offset=7, bytes=1, name="subtype2")],
                period_flag_values=sorted({row["period_flags"] for row in rows}))


def disc_header(data, expected):
    fields = {"disc_type": (0, 16), "volume_id": (16, 11), "build_date": (0x50, 8),
              "product_id": (0x180, 16), "market": (0x1f0, 16)}
    observed = {key: bounded(data, at, size).decode("ascii").replace("\0", " ").rstrip()
                for key, (at, size) in fields.items()}
    require(observed == dict(disc_type="SEGADISCSYSTEM", volume_id=expected["disc_header_volume_id"],
                             build_date=expected["source_build_date"], product_id=expected["product_id"],
                             market=expected["header_market"]), "Disc header disagrees with requested source region")
    return observed


def source_asset(source, image, label, path, offset=None):
    blob = source.read(path)
    start = unique(image.data, blob, label) if offset is None else offset
    require(bounded(image.data, start, len(blob)) == blob, f"Source/media mismatch {label}")
    result = dict(**image.view(start, len(blob)), source_blob="src/"+path,
                  kind="SOURCE_BLOB", proof="exact_source_blob_equals_native_file_slice",
                  encoding="unknown", consumer_views=[],
                  consumer_semantics_status="unverified_no_encoding_consumer_proof")
    return result


# These bounded macro bodies are checked against the pinned source before
# encoding the one LoadShieldArt expansion. This is not a macro assembler.
LVLDMA_SOURCE = r"""LVLDMA macro src, dest, len, type
 lea VDPCTRL,a5
 move.l #$94009300|((((\len)/2)&$FF00)<<8)|(((\len)/2)&$FF),(a5)
 move.l #$96009500|((((\src)/2)&$FF00)<<8)|(((\src)/2)&$FF),(a5)
 move.w #$9700|(((\src)>>17)&$7F),(a5)
 VDPCMD move.w,\dest,\type,DMA,>>16,(a5)
 VDPCMD move.w,\dest,\type,DMA,&$FFFF,dmaCmdLow.w
 move.w dmaCmdLow.w,(a5)
 endm"""
VDPCMD_SOURCE = r"""VDPCMD macro ins, addr, type, rwd, end, end2
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


def source_lines(text):
    return [" ".join(raw.split(";", 1)[0].split()) for raw in text.splitlines()
            if raw.split(";", 1)[0].strip()]


def source_macro(source, path, name, expected):
    text = source.text(path)
    match = re.search(r"^"+name+r"\s+macro\b.*?^\s*endm\b", text, re.M | re.S)
    require(match is not None and source_lines(match[0]) == source_lines(expected),
            f"Unsupported {name} macro")


def consumer_view(image, offset, length):
    return dict(kind="CONSUMED_VIEW", **image.view(offset, length))


def add_consumer(asset, consumer):
    # This summary describes only these checked uses, never every possible use
    # of the source blob. The authoritative encoding is on each consumer.
    if any(item["consumer_id"] == consumer["consumer_id"] for item in asset["consumer_views"]):
        return
    asset["consumer_views"].append(consumer)
    encodings = {item["encoding"] for item in asset["consumer_views"]}
    asset["encoding"] = next(iter(encodings)) if len(encodings) == 1 else "consumer_specific"
    asset["consumer_semantics_status"] = "listed_consumers_verified_other_uses_unverified"


def source_segments(image, assets, offset, length):
    """Attribute every copied byte to an already matched source blob.

    Adjacency is observed on media. Missing/overlapping coverage fails instead
    of creating padding or treating the source blob as the consumer bound.
    """
    end, cursor, result = offset+length, offset, []
    for name, asset in sorted(assets.items(), key=lambda item: item[1]["file_offset"]):
        start, stop = max(offset, asset["file_offset"]), min(end, asset["end_exclusive"])
        if start < stop:
            require(sha(bounded(image.data, asset["file_offset"], asset["length"])) == asset["sha256"],
                    f"Changed matched source blob {name}")
            require(start == cursor, "Raw copy has missing/overlapping source blob coverage")
            result.append(dict(source_blob=name, source_blob_relative_offset=start-asset["file_offset"],
                               **image.view(start, stop-start)))
            cursor = stop
    require(cursor == end, "Raw copy extends beyond attributed source blobs")
    return result


def shield_art_proof(source, image, variables, assets):
    path = "Level/_Objects/Powerup/Main.asm"
    text = source.text(path)
    body = text.split("LoadShieldArt:\n", 1)[1].split("ShieldArtIndex:", 1)[0]
    expected = """bclr #7,powerup
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
rts"""
    require(source_lines(body) == source_lines(expected), "Unsupported LoadShieldArt instruction sequence")
    names = re.findall(r"^\s*dc\.l\s+(\w+)\s*$", text.split("ShieldArtIndex:", 1)[1], re.M)
    require(names == ["Art_Shield", "Art_InvStars", "Art_TimeStars", "Art_GameOver", "Art_TimeOver"],
            "Unexpected ShieldArtIndex entries")
    source_macro(source, "Level/_Definitions.inc", "LVLDMA", LVLDMA_SOURCE)
    source_macro(source, "_Include/Main CPU.inc", "VDPCMD", VDPCMD_SOURCE)
    cpu = source.text("_Include/Main CPU.inc")
    for name, value in (("VDPCTRL", 0xc00004), ("VRAMDMA", 0x40000080)):
        match = re.search(r"^"+name+r"\s+EQU\s+(\$[0-9A-F]+)", cpu, re.M)
        require(match is not None and expression(match[1]) == value, "Unsupported VDP definition")
    buffer, copied, uploaded = variables["aniArtBuffer"], (0xff+1)*4, 0x480
    require(variables["scrlSectSpeeds"]-buffer == uploaded, "Animated art buffer extent differs")
    table_bytes = struct.pack(">5I", *(BASE+assets[name]["file_offset"] for name in names))
    table = unique(image.data, table_bytes, "ShieldArtIndex")
    # Full routine, including the PC-index extension, DBF back edge, all DMA
    # setup instructions, command low-word staging and the final RTS.
    prefix = (struct.pack(">HHI", 0x08b9, 7, variables["powerup"])
              +bytes.fromhex("674470001039")+struct.pack(">I", variables["powerup"])
              +bytes.fromhex("5500d040227b003845f9")+struct.pack(">I", buffer)
              +bytes.fromhex("303c00ff24d951c8fffc"))
    words, source_word, command = uploaded//2, buffer//2, 0x40000080 | ((0xa880 & 0x3fff) << 16) | (0xa880//0x4000)
    dma_code = (struct.pack(">HI", 0x4bf9, 0xc00004)
                +struct.pack(">HI", 0x2abc, 0x94009300 | ((words & 0xff00) << 8) | (words & 0xff))
                +struct.pack(">HI", 0x2abc, 0x96009500 | ((source_word & 0xff00) << 8) | (source_word & 0xff))
                +struct.pack(">HHHHHHHHH", 0x3abc, 0x9700 | ((buffer >> 17) & 0x7f),
                              0x3abc, command >> 16, 0x31fc, command & 0xffff,
                              variables["dmaCmdLow"] & 0xffff, 0x3ab8, variables["dmaCmdLow"] & 0xffff))
    code = prefix+dma_code+bytes.fromhex("4e75")
    start = table-len(code)
    require(bounded(image.data, start, len(code)) == code, "Complete native LoadShieldArt copy/DMA sequence differs")
    rows = []
    for slot, name in enumerate(names):
        offset = image.offset(u32(image.data, table+4*slot), copied)
        view = consumer_view(image, offset, copied)
        segments = source_segments(image, assets, offset, copied)
        add_consumer(assets[name], dict(consumer_id="LoadShieldArt:"+str(slot),
            owner=source.cite(path, "LoadShieldArt"), encoding="raw", operation="copy_longwords",
            input_view=view, source_segments=segments, destination_native_ram_address=buffer,
            longword_counter=0xff, copied_bytes=copied,
            proof="ShieldArtIndex_and_complete_native_LoadShieldArt_copy_and_DMA_sequence",
            all_other_consumers_status="unverified"))
        rows.append(dict(slot=slot, art=name, pointer_entry_file_offset=table+4*slot,
                         target_file_offset=offset, consumed_view=view, source_segments=segments))
    return dict(**image.view(table, len(table_bytes)), source=source.cite(path, "ShieldArtIndex"), rows=rows,
        code_view=image.view(start, len(code)), owner=source.cite(path, "LoadShieldArt"),
        indexed_load_instruction_file_offset=start+22, copy_loop_instruction_file_offset=start+32,
        proof="ordered_pointer_table_and_complete_native_instruction_encoding",
        dma=dict(instruction_view=image.view(start+len(prefix), len(dma_code)), length=uploaded,
                 source_native_ram_address=buffer, source_copy_bytes=copied, source_copy_longwords=256,
                 ram_buffer_bytes=uploaded, vram_address=0xa880, length_units="bytes",
                 macro=source.cite("Level/_Definitions.inc", "LVLDMA"),
                 command_macro=source.cite("_Include/Main CPU.inc", "VDPCMD"),
                 ram_declaration=source.cite("_Include/Main CPU Variables.inc", "aniArtBuffer"),
                 resident_ram_tail=dict(native_address=(buffer+copied) & 0xffffff, length=uploaded-copied,
                     contents_status="unverified_resident_RAM_not_file_backed")),
        scope="static_consumer_proof_only_no_execution_or_resident_RAM_contents_proof")


def nemesis_consumer_proof(source, image, variables, index, plc):
    """Bind PLC and block consumers to native Nemesis entry/header code.

    No decompression, final compressed read cursor or decoded result is proved.
    """
    path = "Level/Functions (General).asm"
    source.text(path)  # pin the owning code, not the incbin filename
    immediate = (struct.pack(">HI", 0x43f9, BASE+plc)
        +bytes.fromhex("d0403031000043f100003219205970003019e588e44800404000484023c000c00004"))
    start = unique(image.data, immediate, "LoadPLCImm indexed table and art fetch")
    call = start+len(immediate)
    require(u16(image.data, call) == 0x6100, "PLC Nemesis call opcode differs")
    nem = call+2+struct.unpack(">h", bounded(image.data, call+2, 2))[0]
    require(bounded(image.data, nem, 6) == bytes.fromhex("48e7ffdc47f9"), "NemDec entry differs")
    writer = image.offset(u32(image.data, nem+6), 2)
    require(bounded(image.data, nem+10, 8) == bytes.fromhex("49f900c00000600a"), "NemDec VDP/branch sequence differs")
    ram = nem+18
    require(bounded(image.data, ram, 6) == bytes.fromhex("48e7ffdc47f9"), "NemDecToRAM entry differs")
    ram_writer = image.offset(u32(image.data, ram+6), 2)
    require(ram_writer == writer+22, "Nemesis RAM/VDP writer relation differs")
    row_targets = []
    for at, prefix in ((writer, "2884534d380d"), (ram_writer, "28c4534d380d")):
        require(bounded(image.data, at, 6) == bytes.fromhex(prefix)
                and image.data[at+6] == 0x66 and u16(image.data, at+8) == 0x4e75,
                "Nemesis write helpers differ")
        row_targets.append(at+8+struct.unpack("b", bounded(image.data, at+7, 1))[0])
    require(row_targets[0] == row_targets[1], "Nemesis row writer branch targets differ")
    main = ram+10
    require(bounded(image.data, main, 10) == struct.pack(">HHHHH", 0x43f8, variables["nemBuffer"] & 0xffff,
                                                       0x3418, 0xe34a, 0x6404), "Nemesis header consumer differs")
    require(bounded(image.data, call+4, 6) == bytes.fromhex("51c9ffe44e75"), "PLC entry loop differs")
    block = (bytes.fromhex("700045f9")+struct.pack(">I", BASE+index)
             +bytes.fromhex("2f0a588a221a0281003fffff204149f9")+struct.pack(">I", variables["blockBuffer"]))
    block_at = unique(image.data, block, "LoadLevelData tagged block fetch")
    block_call = block_at+len(block)
    require(u16(image.data, block_call) == 0x6100 and block_call+2+struct.unpack(">h", bounded(image.data, block_call+2, 2))[0] == ram,
            "Block consumer does not call NemDecToRAM")
    return dict(scope="checked_PLC_immediate_and_block_entry_header_consumers_only_not_full_decoder_execution",
        plc=dict(source=source.cite(path, "LoadPLCImm"), code_view=image.view(start, len(immediate)+10),
                 decoder_entry_file_offset=nem),
        blocks=dict(source=source.cite(PP+"Load Level Data.asm", "LoadLevelData"),
                    code_view=image.view(block_at, len(block)+4), decoder_entry_file_offset=ram),
        decoder=dict(source=source.cite(path, "NemDec"), ram_source=source.cite(path, "NemDecToRAM"),
                     header_source=source.cite(path, "NemDecMain"), entry_and_header_view=image.view(nem, main+10-nem)))


def nemesis_asset(image, asset, consumer_id, owner, **fields):
    length = (u16(image.data, asset["file_offset"]) & 0x7fff)*32
    add_consumer(asset, dict(consumer_id=consumer_id, owner=owner, encoding="Nemesis",
        source_blob_view=dict(kind="SOURCE_BLOB", **image.view(asset["file_offset"], asset["length"])),
        input_extent_status="matched_source_blob_extent; actual_decoder_read_extent_unverified",
        declared_output_bytes=length, decoded_output_status="unverified_decompression_not_executed",
        proof="native_PLC_or_block_consumer_bound_to_Nemesis_entry_and_header_instructions", **fields))
    asset["output_bytes"] = length  # declared header only, scoped by consumer above


def r11a(source, image, region):
    require(image.header["origin"] == 0 and image.header["entry"] == BASE+0x100,
            "R11A is not the original Word RAM program")
    assets, positions = {}, {}
    variables = native_variables(source)
    entry_points = {}
    for field, stub, target_label in (("entry", "JmpTo_Start", "Start"),
                                      ("horizontal_interrupt", "JmpTo_HInt", "HInterrupt"),
                                      ("vertical_interrupt", "JmpTo_VInt", "VInterrupt")):
        at = image.offset(image.header[field], 6)
        require(u16(image.data, at) == 0x4ef9, "MMD entry is not an absolute-long JMP stub")
        target = image.offset(u32(image.data, at+2), 2)
        entry_points[field] = dict(**image.view(at, 6), source=source.cite("Level/Initialization.asm", stub),
                                   target_file_offset=target, source_target_label=target_label)

    def asset(label, path, offset=None):
        assets[label] = source_asset(source, image, label, path, offset)
        start = assets[label]["file_offset"]
        positions[label] = BASE+start
        return start

    # Palette aliases are deliberate: tie the whole contiguous retail sequence,
    # rather than choosing the first identical palette occurrence.
    palette_order = [
        ("Pal_S1Title", "Level/_Data/Palette (Sonic 1 Title).bin"),
        ("Pal_S1LevSel", "Level/_Data/Palette (Sonic 1 Level Select).bin"),
        ("Pal_Sonic", "Level/_Objects/Sonic/Data/Palette.bin"),
        ("Pal_Level", PP+"Data/Palette (Present).bin"),
        ("Pal_LevelProto", PP+"Data/Palette (Present, Prototype).bin"),
        ("Pal_LevelEnd", PP+"Data/Palette (Present).bin"),
    ]
    cursor = unique(image.data, b"".join(source.read(p) for _, p in palette_order), "palette sequence")
    for label, path in palette_order:
        cursor = asset(label, path, cursor)+len(source.read(path))
    positions["Pal_S1SegaBG"] = positions["Pal_S1Title"]

    # Only unconditional, entire incbins in the retail data suffix are candidates.
    # Excludes all earlier demo branches and legacy-data includes.
    suffix = source.text(DATA).split("MapSpr_Sonic:", 1)[1]
    for label, path in re.findall(r"^(\w+):\s*\n\s*incbin\s+\"([^\"]+)\"\s*$", suffix, re.M):
        asset(label, path)
    for label, path in [
        ("LevelChunks", PP+"Data/Chunks (Act 1 Present).bin"),
        ("ColAngleMap", "Level/_Data/Collision Angles.bin"),
        ("ColHeightMap", "Level/_Data/Collision Height Map.bin"),
        ("ColWidthMap", "Level/_Data/Collision Width Map.bin"),
        ("LayoutFG", PP+"Data/Foreground (Act 1 Present).bin"),
        ("LayoutBG", PP+"Data/Background (Act 1 Present).bin"),
        ("ObjectPlacementRecords", PP+"Data/Objects (Act 1).bin"),
        ("Art_BigRingFlash", "Level/_Objects/Level End/Data/Art (Big Ring Flash).nem"),
    ]:
        if label not in assets:
            asset(label, path)

    # LevelDataIndex and the entire live PLC block are reconstructed from dc
    # declarations, using offsets independently found from actual binary blobs.
    prefix = source.text(DATA).split("LevelDataIndex:", 1)[1].split(
        "; Leftover data from other level files used as padding", 1)[0]
    labels, entries, extent = dc_region("LevelDataIndex:"+prefix)
    pattern = assemble_dc(entries[:6], positions)  # three longs, two bytes, first palette byte
    table = unique(image.data, pattern, "LevelDataIndex")
    image.code_end = table  # live included code precedes this final dc block
    positions.update({name: BASE+table+relative for name, relative in labels.items()})
    expected = assemble_dc(entries, positions)
    require(bounded(image.data, table, extent) == expected, "Native LevelDataIndex/PLC declarations differ")
    tables = {"LevelDataIndex": dict(**image.view(table, 16), source=source.cite(DATA, "LevelDataIndex"),
        consumers=proof_ref(image, table, "45f9", "LoadLevelData lea LevelDataIndex,a2"),
        fields=dict(art_pointer_tag=3, block_pointer_tag=2, pointer_mask=0x3fffff,
                    chunks_pointer=positions["LevelChunks"], trailing_bytes_before_palette_ids=[0, 0x81],
                    palette_ids=[5, 5], initial_plc_id_from_art_pointer_high_byte=3,
                    section_plc_id_from_block_pointer_high_byte=2))}
    plc = positions["PLCLists"]-BASE
    decode_proof = nemesis_consumer_proof(source, image, variables, table, plc)
    nemesis_asset(image, assets["LevelBlocks"], "LoadLevelData:block_decode", decode_proof["blocks"]["source"],
                  consumer_proof="encoding_consumers.blocks")
    table_names = [item for _, _, item in entries if item.endswith("-PLCLists")]
    rows = relative_table(image, plc, len(table_names))
    for row, name in zip(rows, table_names, strict=True):
        label = name.split("-", 1)[0]
        start = row["target_file_offset"]
        count = struct.unpack(">h", bounded(image.data, start, 2))[0]+1
        require(0 <= count <= 32, "Unbounded PLC list")
        row.update(label=label, entries=[])
        for index in range(count):
            at = start+2+6*index
            pointer = u32(image.data, at)
            target = image.offset(pointer, 2)
            candidates = [label for label, fact in assets.items() if fact["file_offset"] == target]
            require(len(candidates) == 1, "PLC pointer not a matched source asset")
            nemesis_asset(image, assets[candidates[0]], "LoadPLCImm:row:"+str(at), decode_proof["plc"]["source"],
                          consumer_proof="encoding_consumers.plc", plc_row_file_offset=at,
                          vram_address=u16(image.data, at+4))
            row["entries"].append(dict(art=candidates[0], file_offset=target, vram_address=u16(image.data, at+4)))
        row["view"] = image.view(start, 2+6*count)
    tables["PLCLists"] = dict(**image.view(plc, 2*len(rows)), rows=rows,
        source=source.cite(DATA, "PLCLists"),
        consumers=proof_ref(image, plc, "43f9", "LoadPLC/InitPLC lea PLCLists,a1"))

    tables["ShieldArtIndex"] = shield_art_proof(source, image, variables, assets)

    # Layout table has 36 signed words: three real plane pointers reused per
    # zone row, and an empty Null label. Verify every displacement from source shape.
    fg, bg = assets["LayoutFG"], assets["LayoutBG"]
    layouts = fg["file_offset"]-72
    expected_offsets = [72, bg["file_offset"]-layouts, bg["end_exclusive"]-layouts]
    expected_words = (expected_offsets+[expected_offsets[2]]*9)*3
    require(bounded(image.data, layouts, 72) == struct.pack(">36H", *expected_words), "LevelLayouts table mismatch")
    for label in ("LayoutFG", "LayoutBG"):
        fact = assets[label]
        fact.update(layout_info(bounded(image.data, fact["file_offset"], fact["length"])))
        require(max(b & 0x7f for b in bounded(image.data, fact["file_offset"]+2, fact["length"]-2)) * 512
                <= assets["LevelChunks"]["length"], "Layout indexes outside chunks")
        used_ids = set(b & 0x7f for b in bounded(image.data, fact["file_offset"]+2, fact["length"]-2))-{0}
        chunks = assets["LevelChunks"]
        block_ids = [u16(image.data, chunks["file_offset"]+(chunk-1)*512+word*2) & 0x7ff
                     for chunk in used_ids for word in range(256)]
        require(max(block_ids) < assets["LevelBlocks"]["output_bytes"]//8, "Referenced chunk block outside decompressed blocks")
        require(max(block_ids) < assets["LevelCollision"]["length"], "Referenced block outside collision IDs")
        fact.update(referenced_chunk_count=len(used_ids), maximum_referenced_block_id=max(block_ids))
    tables["LevelLayouts"] = dict(**image.view(layouts, 72), rows=relative_table(image, layouts, 36),
        source=source.cite(DATA, "LevelLayouts"),
        consumers=proof_ref(image, layouts, "43f9", "LoadLevelLayout lea LevelLayouts,a1"))

    # Table directly precedes the joined palette blobs, including the removed
    # SEGA palette's intentional alias. Runtime destination is a sign-extended word.
    palette = assets["Pal_S1Title"]["file_offset"]-48
    _, pal_entries, _ = dc_region(source.text(PAL).split("PaletteTable:", 1)[1].split(
        "; Sonic 1 SEGA screen background (leftover, data completely removed)", 1)[0])
    expected = assemble_dc(pal_entries, dict(positions, palette=variables["palette"] & 0xffff))
    require(bounded(image.data, palette, 48) == expected, "Palette metadata mismatch")
    palettes = []
    for index in range(6):
        at = palette+8*index
        target, destination, counter = u32(image.data, at), u16(image.data, at+4), u16(image.data, at+6)
        start, length = image.offset(target, (counter+1)*4), (counter+1)*4
        palettes.append(dict(id=index, view=image.view(start, length), destination_word=destination,
                             longword_counter=counter, disposition="live" if index in (3, 5) else "source_leftover"))
    tables["PaletteTable"] = dict(**image.view(palette, 48), rows=palettes, source=source.cite(PAL, "PaletteTable"),
        consumers=proof_ref(image, palette, "43f9", "LoadPalette lea PaletteTable,a1"))
    cycle_path = PP+"Palette Cycle (Present).asm"
    cycle_data = "PalCycleScript1:"+source.text(cycle_path).split("PalCycleScript1:", 1)[1]
    cycle_blob, cycle_labels, _, _ = literal_data(cycle_data)
    cycle_at = unique(image.data, cycle_blob, "live palette cycle data")
    for index in range(1, 7):
        for kind, length, opcode in (("Script", 8, "43f9"), ("Colors", 6, "45f9")):
            label = f"PalCycle{kind}{index}"
            at = cycle_at+cycle_labels[label]
            tables[label] = dict(**image.view(at, length), source=source.cite(cycle_path, label),
                                consumers=proof_ref(image, at, opcode, "live palette cycle "+label))
        script = cycle_at+cycle_labels[f"PalCycleScript{index}"]
        require(image.data[script] < 64 and image.data[script+1] == 3, "Cycle palette/frame bounds")
        require(all(image.data[script+3+2*frame] < 3 for frame in range(3)), "Cycle color index outside table")

    # Object table has a separate 8-byte backwards sentinel and 6-byte Null.
    placement = assets["ObjectPlacementRecords"]
    objects = placement["file_offset"]-12
    require(bounded(image.data, objects, 12) == struct.pack(">6H", 12, 12+placement["length"], 0xffff, 0, 0, 0),
            "ObjectLayouts header mismatch")
    require(bounded(image.data, placement["end_exclusive"], 6) == b"\xff\xff"+b"\0"*4, "Missing null placement")
    placement.update(object_info(bounded(image.data, placement["file_offset"], placement["length"])))
    tables["ObjectLayouts"] = dict(**image.view(objects, 12), source=source.cite(PP+"Object Layout (Act 1).asm", "ObjectLayouts"),
        consumers=proof_ref(image, objects, "41f9", "SpawnObjects_Init lea ObjectLayouts,a0"))

    # Dispatch proof: fixed table width, native Null body and all source alias
    # relations, plus the actual RunObjects indexed-fetch/JSR instruction sequence.
    index_source = source.text(PP+"Object Index (Act 1 Present).asm")
    object_names = re.findall(r"^\s*dc\.l\s+(\w+)", index_source, re.M)
    require(len(object_names) == 64, "Unexpected dispatch slot count")
    null_body = bytes.fromhex("10bc00004e75")
    candidates = []
    for null in occurrences(image.data, null_body):
        start = null-4*len(object_names)
        if start < 0:
            continue
        pointers = [u32(image.data, start+4*i) for i in range(64)]
        aliases = {}
        valid = True
        for name, pointer in zip(object_names, pointers, strict=True):
            if not BASE+0x100 <= pointer < BASE+len(image.data) or pointer & 1:
                valid = False
                break
            if name in aliases and aliases[name] != pointer:
                valid = False
                break
            aliases[name] = pointer
        if valid and aliases["ObjNull"] == BASE+null and absolute_refs(image, start, "43f9"):
            candidates.append((start, pointers))
    require(len(candidates) == 1, "Ambiguous native object dispatch")
    dispatch, pointers = candidates[0]
    refs = proof_ref(image, dispatch, "43f9", "RunObjects lea ObjectIndex,a1")
    require(any(bounded(image.data, row["instruction_file_offset"]-4, 4) == bytes.fromhex("d040d040")
                and bounded(image.data, row["instruction_file_offset"]+6, 6) == bytes.fromhex("227100fc4e91") for row in refs),
            "RunObjects indexed dispatch instructions differ")
    source_rows = [(i+1, raw.split(";", 1)[1].strip() if ";" in raw else "")
                   for i, raw in enumerate(index_source.splitlines()) if re.match(r"\s*dc\.l\s+", raw)]
    tables["ObjectIndex"] = dict(**image.view(dispatch, 256), source=source.cite(PP+"Object Index (Act 1 Present).asm", "ObjectIndex"),
        consumers=refs, rows=[dict(id=i+1, source_label=name, target_file_offset=image.offset(pointer, 2),
                                  source_line=source_rows[i][0], source_description=source_rows[i][1])
                              for i, (name, pointer) in enumerate(zip(object_names, pointers, strict=True))],
        target_name_proof="native_slot_order_and_alias_relations; target bodies not independently assembled")

    for label, path, opcode in [("CamBounds", SCROLL, "41f9"), ("SectionRanges", PP+"Section Data (Act 1 Present).asm", None),
                                 ("CloudSectSizes", SCROLL, "47f9"), ("WaterDeformSects", SCROLL, "47f9")]:
        blob = scalar_table(source, path, label)
        start = unique(image.data, blob, label)
        tables[label] = dict(**image.view(start, len(blob)), source=source.cite(path, label))
        if opcode:
            tables[label]["consumers"] = proof_ref(image, start, opcode, label)
        positions[label] = BASE+start
    bounds = tables["CamBounds"]
    bounds["values"] = dict(zip(["unused", "left", "right", "top", "bottom", "camera_y_center"],
                                struct.unpack(">6H", bounded(image.data, bounds["file_offset"], 12)), strict=True))
    startpos = asset("LevelStartLoc", PP+"Data/Start Position (Act 1 Present).bin")
    assets["LevelStartLoc"]["consumers"] = proof_ref(image, startpos, "43f9", "DefaultStart lea LevelStartLoc,a1")
    assets["LevelStartLoc"]["coordinates"] = dict(zip(["x", "y"], struct.unpack(">HH", bounded(image.data, startpos, 4)), strict=True))

    # The whole section table block, not just its first signature.
    section_path = PP+"Section Data (Act 1 Present).asm"
    section_labels, section_entries, section_len = dc_region(source.text(section_path))
    section = positions["SectionRanges"]-BASE
    require(bounded(image.data, section, section_len) == assemble_dc(section_entries, {}), "Section tables mismatch")
    for name, relative in section_labels.items():
        at = section+relative
        pc_refs = []
        for i in range(0x100, section-3, 2):
            if image.data[i:i+2] == bytes.fromhex("43fa"):
                displacement = struct.unpack(">h", image.data[i+2:i+4])[0]
                if i+2+displacement == at:
                    pc_refs.append(dict(instruction_file_offset=i, target_file_offset=at, addressing="pc_relative_word"))
            if image.data[i:i+2] == bytes.fromhex("303b") and image.data[i+2] == 0x10:
                displacement = struct.unpack("b", image.data[i+3:i+4])[0]
                if i+2+displacement == at:
                    pc_refs.append(dict(instruction_file_offset=i, target_file_offset=at, addressing="pc_indexed_d1_word"))
        tables[name] = dict(**image.view(at, 10), source=source.cite(section_path, name), consumers=pc_refs)
    require(len(tables["SectionRanges"]["consumers"]) == 2, "Section ranges native PC consumers differ")
    require(len(tables["SectionInitPLCs"]["consumers"]) == 1 and len(tables["SectionUpdatePLCs"]["consumers"]) == 1,
            "Section PLC native indexed consumers differ")

    # Collision-ID pointer table and the chunk-base immediate independently
    # connect the already-matched blobs to the native collision consumers.
    colptr = struct.pack(">I", positions["LevelCollision"])
    colindex = unique(image.data, colptr*8, "LevelColIndex")
    tables["LevelColIndex"] = dict(**image.view(colindex, 32), source=source.cite("Level/Main.asm", "LevelColIndex"))
    assets["LevelChunks"]["consumers"] = proof_ref(image, assets["LevelChunks"]["file_offset"], "223c", "GetLevelBlock move.l #LevelChunks,d1")
    require(assets["LevelChunks"]["length"] % 512 == 0, "Chunk table not 16x16 block words")
    assets["LevelChunks"].update(record_bytes=512, blocks_per_axis=16, chunk_pixels=256)
    for label in ("ColAngleMap", "ColHeightMap", "ColWidthMap"):
        assets[label]["consumers"] = proof_ref(image, assets[label]["file_offset"], "45f9", "Collision lea "+label+",a2")
    assets["LevelBlocks"].update(record_bytes_after_decode=8, block_pixels=16, decode_destination=variables["blockBuffer"] & 0xffffff,
                               decode_capacity_bytes=0x2000)
    require(assets["LevelBlocks"]["output_bytes"] <= 0x2000, "Blocks exceed RAM decode capacity")
    # Player visual prerequisites are bounded data, not a player implementation.
    for label, path, table_label, opcode in [
        ("MapSpr_Sonic", "Level/_Objects/Sonic/Data/Mappings.asm", "Local_Map", None),
        ("DPLC_Sonic", "Level/_Objects/Sonic/Data/DPLCs.asm", "Local_DPLCs", "45f9"),
        ("Ani_Sonic", "Level/_Objects/Sonic/Data/Animations.asm", "Local_Anim", "43f9"),
    ]:
        text = source.text(path)
        if label == "MapSpr_Sonic":
            declaration = "\tif def(R43_LEGACY_SONIC_TAIL)\n\telse\nR43_LEGACY_SONIC_TAIL EQU 0\n\tendif\n"
            require(text.startswith(declaration), "Unknown Sonic mappings conditional prefix")
            text = catalogue.selected_lines(text[len(declaration):], "R43_LEGACY_SONIC_TAIL", "0")
        blob, local_labels, local_entries, extent = literal_data(text)
        at = unique(image.data, blob, label+" complete data include")
        count = 0
        for _, width, item in local_entries:
            if width == 2 and item.endswith("-"+table_label):
                count += 1
            else:
                break
        require(count > 0, "Missing player relative index")
        rows = relative_table(image, at, count)
        for row in rows:
            require(at+2*count <= row["target_file_offset"] < at+extent, "Player table pointer outside include")
        if label == "DPLC_Sonic":
            art = assets["Art_Sonic"]
            aligned_bytes = (art["length"]+1) & ~1
            padding = aligned_bytes-art["length"]
            require(bounded(image.data, art["end_exclusive"], padding) == bytes(padding), "Sonic art native EVEN padding differs")
            art["native_read_view_including_even_padding"] = image.view(art["file_offset"], aligned_bytes)
            art["alignment_padding_bytes"] = padding
            for row in rows:
                target = row["target_file_offset"]
                pieces = u16(image.data, target)
                require(target+2+pieces*2 <= at+extent, "DPLC pieces exceed include")
                tiles = 0
                for piece in range(pieces):
                    value = u16(image.data, target+2+2*piece)
                    length = ((value >> 12)+1)*32
                    require((value & 0xfff)*32+length <= aligned_bytes, "DPLC references outside raw Sonic art and native EVEN padding")
                    tiles += length
                require(tiles <= 0x300, "Sonic DPLC exceeds native art buffer")
                row.update(piece_count=pieces, output_bytes=tiles)
        tables[label] = dict(**image.view(at, extent), source_data_include="src/"+path,
                             proof="complete_source_data_include_equals_native_bytes", index_count=count, rows=rows)
        if opcode:
            tables[label]["consumers"] = proof_ref(image, at, opcode, label)
        else:
            refs = absolute_refs(image, at, "217c")  # MOVE.L #MapSpr_Sonic,oMap(a0)
            require(any(u16(image.data, ref+6) == variables["oMap"] for ref in refs), "Missing Sonic mapping assignment")
            tables[label]["consumer_instruction_file_offsets"] = refs
    assets["Art_Sonic"]["consumers"] = proof_ref(image, assets["Art_Sonic"]["file_offset"], "43f9", "LoadSonicDynPLC lea Art_Sonic,a1")
    # Checkpoint entry independently proves the mode-2 branch reaches the warp
    # restore prefix. Other suffix branches remain source-level prerequisites.
    carry = carry_prefix(source, image, variables)
    checkpoint_sig = struct.pack(">HHHHI", 0x4df8, variables["objPlayerSlot"] & 0xffff,
                                 0x0c39, 2, variables["spawnMode"] & 0xffffffff)
    checkpoint = unique(image.data, checkpoint_sig, "LoadCheckpointData mode-2 prefix")
    require(u16(image.data, checkpoint+12) == 0x6700, "Checkpoint mode-2 branch shape")
    target = checkpoint+14+struct.unpack(">h", bounded(image.data, checkpoint+14, 2))[0]
    require(target == carry["file_offset"], "Checkpoint mode 2 does not reach warp restore")
    carry["checkpoint_entry"] = dict(**image.view(checkpoint, 16), source=source.cite("Level/Load Saved Data.asm", "LoadCheckpointData"),
                                    spawn_mode=2, branch_target_file_offset=target)
    events = event_proof(source, image, variables)
    scroll = scroll_proof(source, image, variables, tables, region)
    run_events = next(row for row in scroll["calls"] if row["source_label"] == "RunLevelEvents")["target_file_offset"]
    require(bounded(image.data, run_events, 8) == bytes.fromhex("70001039")+struct.pack(">I", variables["zone"]),
            "RunLevelEvents caller target does not read zone")
    events["RunLevelEvents_entry"] = dict(**image.view(run_events, 18), source=source.cite("Level/_Events.asm", "RunLevelEvents"))
    load_source = PP+"Load Level Data.asm"
    return dict(status="bounded_data_layout_proved; gameplay_not_implemented", assets=assets, tables=tables,
                events=events, parallax=scroll, native_carry=carry, encoding_consumers=decode_proof,
                encoding_scope="per_listed_consumer_only; filenames_never_prove_encoding; unlisted_consumers_unverified",
                entry_points=entry_points,
                load_sequence=[
                    dict(owner=source.cite("Level/Main.asm", "LevelStart"), operation="LevelDataIndex art high byte selects immediate PLC 3 (alias PLC_Level), then immediate standard PLC 1"),
                    dict(owner=source.cite(SCROLL, "LevelSizeLoad"), operation="Copy CamBounds; default start uses LevelStartLoc, nonzero spawnMode loads checkpoint/warp carry; initialize camera and background scroll"),
                    dict(owner=source.cite(load_source, "LoadLevelData"), operation="Mask tagged block pointer with 0x3fffff; Nemesis decode to blockBuffer; chunks remain in original Word RAM"),
                    dict(owner=source.cite(load_source, "LoadLevelLayout"), operation="Clear 0x800 layout bytes; copy FG and BG at offsets 0 and 0x40, row stride 0x80, dimensions encoded minus one"),
                    dict(owner=source.cite(load_source, "LoadLevelData"), operation="Use palette ID 5; nonzero spawnMode uses section PLCs; title-card-loaded cold entry uses block-pointer high byte PLC 2"),
                    dict(owner=source.cite("Level/Main.asm", "LoadLevelCollision"), operation="Bind collision ID array through LevelColIndex; wait for PLC completion before LoadPlayer"),
                    dict(owner=source.cite("Level/Main.asm", "LoadPlayer"), operation="Set player object ID 1; nonzero spawnMode adds hurt timer 0x78; player runtime/animation and service timing not ported"),
                ],
                unknowns=["Non-R11A semantic descriptors are not derived from common offsets.",
                          "Nemesis encoding/header sizes are tied to checked PLC/block consumers; decompression/read extents remain unverified.",
                          "Source blob extents differ from consumed views; unlisted consumer semantics remain unknown.",
                          "LoadShieldArt copies 1024 file-backed bytes then DMA reads 1152 RAM bytes; the 128-byte resident tail is unverified.",
                          "Object target labels come from validated dispatch slots, not independent assembly of every object body.",
                          "Carry prefix is proven; checkpoint restore body and conditional mini-Sonic/left-bound suffix need their own execution checks.",
                          "Live camera/events/parallax/player/carry execution and service timing remain separate implementation prerequisites."])


def external_output(path):
    requested = Path(path).expanduser()
    require(not requested.is_symlink(), "Refusing symlink output")
    resolved = requested.resolve()
    require(resolved.suffix == ".json", "Output must be metadata .json")
    require(not resolved.is_relative_to(ROOT), "Output must be outside the implementation checkout")
    git_common = subprocess.check_output(["git", "-C", str(ROOT), "rev-parse", "--git-common-dir"], text=True).strip()
    common = (ROOT/git_common).resolve()
    repository = common.parent
    require(not resolved.is_relative_to(repository), "Output must be outside the main repository and worktrees")
    return resolved


def write_output(path, result, overwrite=False):
    destination = external_output(path)
    require(destination.parent.is_dir(), "Output parent must already exist")
    # Recheck at open: do not follow a symlink installed after validation.
    flags = os.O_WRONLY | os.O_CREAT | os.O_NOFOLLOW | (os.O_TRUNC if overwrite else os.O_EXCL)
    with os.fdopen(os.open(destination, flags, 0o600), "w", encoding="utf-8") as output:
        output.write(json.dumps(result, indent=2, sort_keys=True)+"\n")


def inventory(source, media_root):
    # Validate inputs used by catalogue.inventory before it performs its reads.
    for path in ("_Include/Common.inc", "_Include/System.inc", "_Include/MMD.inc", "CD Initial Program/IPX.asm",
                 "CD System Program/SPX.asm", "Main.asm", "make.sh"):
        source.read(path)
    build = source.text("make.sh")
    for path in re.findall(r"^assemble(?:_symbols)?\s+'([^']+)'", build, re.M):
        source.read(path.replace("\\", "/"))
    catalog = catalogue.inventory(source.root)
    stages = [row for row in catalog["commands"] if row["disposition"] == "STAGE_LOAD"]
    require(len(stages) == 70 and len({row["symbol"] for row in stages}) == 70, "Retail stage catalogue differs")
    file_by_symbol = {row["symbol"]: row for row in catalog["files"]}
    origins = dict((name, path.replace("\\", "/")) for path, name in re.findall(
        r"^assemble\s+'([^']+)'\s+'\.\.\\out\\files\\([^']+)'", build, re.M))
    releases = []
    for region in ("USA", "Japan", "Europe"):
        cues = sorted((Path(media_root)/region).glob("*.cue"))
        require(len(cues) == 1, f"Expected one supplied {region} CUE")
        cue_bytes = cues[0].read_bytes()
        tracks = parse_cue(cue_bytes.decode())
        track = (cues[0].parent/tracks[0]["file"]).resolve()
        require(track.is_relative_to(cues[0].parent.resolve()), "Track escapes media directory")
        with RawIso(track) as disc:
            header = disc_header(disc.read(0, 0x200), next(item for item in catalog["releases"] if item["name"] == region.upper()))
            disc.stream.seek(0)
            digest = hashlib.file_digest(disc.stream, "sha256").hexdigest()
            payloads = []
            for row in stages:
                stage = row["symbol"]
                file = file_by_symbol[stage]["filename"]
                program = Mmd(disc.file(file))
                require(program.header["reserved"] == 0, "Unexpected retail MMD reserved byte")
                source_entry = origins[file.removesuffix(";1")]
                source.text(source_entry)
                entry = disc.files[file]
                payload = dict(stage=stage, file=file, file_id=file_by_symbol[stage]["id"], command_id=row["id"],
                               pcm_file=file_by_symbol[row["load_files"][1]]["filename"],
                               iso_extent=dict(lba=entry["lba"], bytes=entry["size"]), sha256=sha(program.data),
                               mmd=program.header, source_entry="src/"+source_entry,
                               descriptor_status="header_only_no_semantic_offsets")
                for field in ("entry", "horizontal_interrupt", "vertical_interrupt"):
                    if program.header[field]:
                        program.offset(program.header[field] & 0xffffff, 2)
                if stage == "R11A":
                    payload["load_plan"] = r11a(source, program, region)
                    payload["descriptor_status"] = "R11A_data_layout_verified_against_supplied_media"
                payloads.append(payload)
            releases.append(dict(region=region, cue_sha256=sha(cue_bytes), data_track=track.name,
                                 data_track_bytes=disc.before.st_size, data_track_sha256=digest,
                                 observed_disc_header=header, stages=payloads))
    return dict(schema="soniccd-stage-inventory-v2", source_pin=SOURCE_PIN,
                analysis_tool_sha256=sha(Path(__file__).read_bytes()),
                catalogue_tool_sha256=sha(Path(catalogue.__file__).read_bytes()),
                cue_parser_tool_sha256=sha(Path(__file__).with_name("retail_profiles.py").read_bytes()),
                binding_scope="file/command/PCM names from pinned source catalogue joined to actual ISO; native SPX dispatch not rechecked here",
                proof_scope="read_only_program_local_research; no_runtime_assets_or_parity_claim", releases=releases,
                source_content_pins=dict(sorted(source.used.items())))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--media-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="external metadata JSON path")
    parser.add_argument("--overwrite", action="store_true", help="explicitly replace an existing external report")
    args = parser.parse_args()
    try:
        destination = external_output(args.output)
        require(not destination.is_relative_to(args.media_root.resolve()), "Output cannot be inside supplied media")
        require(not destination.is_relative_to(args.source.resolve()), "Output cannot be inside reference source")
        require(args.overwrite or not destination.exists(), "Output exists; use --overwrite explicitly")
        result = inventory(Source(args.source), args.media_root)
        write_output(destination, result, args.overwrite)
        print(f"Verified 70 retail MMDs per region, R11A bounded data plan in {len(result['releases'])} regions: {destination}")
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"stage_inventory: {error}\n")


if __name__ == "__main__":
    main()
