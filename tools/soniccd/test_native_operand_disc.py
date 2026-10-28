"""Constructed CUE/raw ISO/MMD only; no original media or extraction."""
import copy
import importlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import native_operand_check as check


def dual(value, width):
    return value.to_bytes(width, 'little')+value.to_bytes(width, 'big')


def record(name, lba, size, flags=0):
    name = name.encode('ascii')
    raw = bytearray(33+len(name)+(len(name) % 2 == 0))
    raw[0] = len(raw); raw[2:10] = dual(lba, 4); raw[10:18] = dual(size, 4)
    raw[25] = flags; raw[28:32] = dual(1, 2); raw[32] = len(name)
    raw[33:33+len(name)] = name
    return raw


def raw_iso(data, *, rows=None, root_size=2048, sectors=32):
    logical = bytearray(sectors*2048)
    pvd = bytearray(2048); pvd[:7] = b'\x01CD001\x01'
    pvd[80:88] = dual(sectors, 4); pvd[128:132] = dual(2048, 2)
    pvd[156:190] = record('\0', 20, root_size, 2)
    logical[16*2048:17*2048] = pvd
    rows = rows if rows is not None else record('WARP__.MMD;1', 21, len(data))
    logical[20*2048:20*2048+len(rows)] = rows
    logical[21*2048:21*2048+len(data)] = data
    result = bytearray()
    for offset in range(0, len(logical), 2048):
        sector = bytearray(2352); sector[:12] = b'\0'+b'\xff'*10+b'\0'; sector[15] = 1
        sector[16:2064] = logical[offset:offset+2048]; result.extend(sector)
    return result


@unittest.skipUnless(sys.platform.startswith('linux'), 'Linux bound-fd mode')
class DiscTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve(); self.root = self.base/'media'; self.root.mkdir()
        self.data = bytearray(0x900)
        self.data[:20] = struct.pack('>BBIHIII', 0, 0, 0xff1000, 7, 0xff1000, 0, 0)
        self.data[0x100:0x110] = bytes.fromhex('43f9002095be43fa001043fb0022fff0')
        self.data = bytes(self.data)
        self.track = self.root/'Track 01.bin'; self.track.write_bytes(raw_iso(self.data))
        self.cue = self.root/'Original.cue'
        self.cue_text = 'FILE "Track 01.bin" BINARY\n TRACK 01 MODE1/2352\n INDEX 01 00:00:00\n'
        for number in range(2, 36):
            self.cue_text += f'FILE "Audio {number:02}.bin" BINARY\n TRACK {number:02} AUDIO\n INDEX 00 00:00:00\n INDEX 01 00:02:00\n'
        self.cue.write_text(self.cue_text)
        self.recipe = self.base/'recipe.json'
        self.spec = dict(version=1, cue=dict(path=self.cue.name, bytes=self.cue.stat().st_size,
            sha256=check.digest(self.cue.read_bytes())), mmd=dict(name='WARP__.MMD', bytes=len(self.data),
            sha256=check.digest(self.data)), spans=[dict(id='table', offset=0x120, bytes=32,
            sha256=check.digest(self.data[0x120:0x140]))], literals=[dict(id='indexed-lea',
            offset=0x10a, hex='43fb0022')], operands=[dict(id='absolute', kind='absolute-long',
            offset=0x102, width=4, encoded='002095be', expected_target=0x2095be),
            dict(id='pc', kind='pc-relative-word', offset=0x108, width=2,
            encoded='0010', pc_base=0x200108, expected_target=0x200118)])

    def module(self):
        return importlib.import_module('native_operand_disc')

    def write(self):
        self.recipe.write_text(json.dumps(self.spec)+'\n'); return self.recipe

    def verify(self):
        return self.module().verify(self.write(), self.root)

    def reject(self, code=None):
        with self.assertRaises(check.CheckError) as raised: self.verify()
        if code is not None: self.assertEqual(raised.exception.code, code)
        return str(raised.exception)

    def repin_cue(self, text):
        self.cue.write_text(text)
        self.spec['cue'].update(bytes=self.cue.stat().st_size, sha256=check.digest(self.cue.read_bytes()))

    def test_cli_reads_named_original_mmd_without_any_output_file_or_payload(self):
        before = sorted(str(p.relative_to(self.base)) for p in self.base.rglob('*'))
        self.write()
        result = subprocess.run([sys.executable, '-B', str(Path(check.__file__).with_name('native_operand_disc.py')),
            '--root', str(self.root), '--recipe', str(self.recipe)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual(report['status'], 'verified')
        self.assertEqual(report['mmd']['sha256'], check.digest(self.data))
        self.assertEqual([item['actual_target'] for item in report['operands']], [0x2095be, 0x200118])
        self.assertNotIn('43fb0022', result.stdout); self.assertNotIn('encoded', result.stdout)
        self.assertEqual(report['mmd']['header']['unresolved_copy_bytes'], 0)
        self.assertFalse(report['runtime_or_source_authentication'])
        self.assertEqual(sorted(str(p.relative_to(self.base)) for p in self.base.rglob('*')), sorted(before+['recipe.json']))

    def test_original_cue_size_and_hash_are_both_required_pins(self):
        for field, value in (('bytes', self.cue.stat().st_size+1), ('sha256', '0'*64)):
            old = self.spec['cue'][field]; self.spec['cue'][field] = value
            with self.subTest(field=field): self.reject(1)
            self.spec['cue'][field] = old

    def test_full_mmd_identity_includes_header_and_trailing_bytes(self):
        for field, value in (('bytes', len(self.data)-1), ('sha256', check.digest(self.data[0x100:]))):
            old = self.spec['mmd'][field]; self.spec['mmd'][field] = value
            with self.subTest(field=field): self.reject(1)
            self.spec['mmd'][field] = old

    def test_only_canonical_root_cue_basenames_are_accepted(self):
        for name in ('../Original.cue', './Original.cue', 'a//Original.cue', '/Original.cue', 'a\\Original.cue'):
            self.spec['cue']['path'] = name
            with self.subTest(name=name): self.reject(2)

    def test_cli_invalid_unicode_cue_basename_is_invalid_without_source_dump(self):
        self.spec['cue']['path'] = 'Bad\ud800.cue'; self.write()
        result = subprocess.run([sys.executable,'-B',str(Path(check.__file__).with_name('native_operand_disc.py')),
            '--root',str(self.root),'--recipe',str(self.recipe)],capture_output=True,text=True)
        self.assertEqual(result.returncode,2,result.stderr)
        self.assertEqual(result.stdout,'')
        self.assertEqual(json.loads(result.stderr)['status'],'invalid')

    def test_mmd_name_is_exact_uppercase_root_iso_name_without_version_alias(self):
        for name in ('warp__.MMD', 'WARP__.MMD;1', '../WARP__.MMD', 'DIR/WARP__.MMD', 'WARP.MMD ', ''):
            self.spec['mmd']['name'] = name
            with self.subTest(name=name): self.reject(2)

    def test_cue_profile_and_every_file_name_are_validated_without_audio_access(self):
        for text in (self.cue_text.replace('MODE1/2352', 'MODE2/2352'),
                     self.cue_text.replace('INDEX 01 00:00:00', 'INDEX 01 00:00:01'),
                     self.cue_text.replace('Audio 35.bin', '../Audio 35.bin'),
                     self.cue_text.replace('Audio 35.bin', 'Audio 34.bin'),
                     self.cue_text.replace('Audio 35.bin', 'audio 34.bin'),
                     self.cue_text.split('FILE "Audio 35.bin"')[0]):
            self.repin_cue(text)
            with self.subTest(text=text[-100:]): self.reject()

    def test_every_file_directive_has_exactly_one_track_and_is_not_shadowed_or_dangling(self):
        variants = (
            'FILE "../UNOWNED.bin" BINARY\n' + self.cue_text,
            self.cue_text + 'FILE "../UNOWNED.bin" BINARY\n',
            'FILE "Unassigned.bin" BINARY\n' + self.cue_text,
            self.cue_text + 'FILE "Unassigned.bin" BINARY\n',
            self.cue_text.replace('FILE "Audio 02.bin" BINARY\n',
                                  'FILE "Audio 02.bin" BINARY\n INDEX 02 00:02:00\n'),
        )
        for text in variants:
            with self.subTest(directive=text[-80:]):
                self.repin_cue(text)
                with patch.object(self.module(), '_BoundIso', side_effect=AssertionError('Invalid CUE must fail before ISO access')):
                    self.reject(2)

    def test_missing_audio_files_are_never_opened_statted_or_hashed(self):
        original_open, original_stat = os.open, Path.stat
        def guarded_open(path, *args, **kwargs):
            self.assertNotIn('Audio ', str(path)); return original_open(path, *args, **kwargs)
        def guarded_stat(path, *args, **kwargs):
            self.assertNotIn('Audio ', str(path)); return original_stat(path, *args, **kwargs)
        with patch('os.open', side_effect=guarded_open), patch.object(Path, 'stat', guarded_stat):
            report = self.verify()
        self.assertEqual(report['cue']['tracks'], 35)
        self.assertFalse(report['provenance']['audio_tracks_observed'])

    def test_caller_selected_root_recipe_cue_and_data_symlinks_are_rejected(self):
        self.write()
        alias = self.base/'alias'; alias.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(check.CheckError): self.module().verify(self.recipe, alias)
        for path in (self.cue, self.track, self.recipe):
            backup = path.with_suffix(path.suffix+'.original'); path.rename(backup); path.symlink_to(backup)
            with self.subTest(path=path):
                with self.assertRaises(check.CheckError): self.module().verify(self.recipe, self.root)
            path.unlink(); backup.rename(path)

    def test_data_track_cannot_alias_recipe_even_as_a_hardlink(self):
        self.write(); self.track.unlink(); os.link(self.recipe, self.track)
        with self.assertRaises(check.CheckError): self.module().verify(self.recipe, self.root)

    def test_partial_raw_sector_and_outside_volume_extents_reject(self):
        original = raw_iso(self.data)
        for raw in (original[:-1], raw_iso(self.data, rows=record('WARP__.MMD;1', 32, len(self.data)))):
            self.track.write_bytes(raw)
            with self.subTest(size=len(raw)): self.reject()

    def test_pvd_mode_both_endian_and_duplicate_directory_records_reject(self):
        for kind in ('pvd', 'mode', 'endian', 'record-endian', 'duplicate', 'multiextent'):
            raw = raw_iso(self.data); pvd = 16*2352+16; root = 20*2352+16
            if kind=='pvd': raw[pvd+1] = 0
            if kind=='mode': raw[16*2352+15] = 2
            if kind=='endian': raw[pvd+80] ^= 1
            if kind=='record-endian': raw[root+2] ^= 1
            if kind=='duplicate':
                row = record('WARP__.MMD;1',21,len(self.data)); raw[root+len(row):root+2*len(row)] = row
            if kind=='multiextent': raw[root+25] = 128
            self.track.write_bytes(raw)
            with self.subTest(kind=kind): self.reject()

    def test_directory_budget_is_checked_before_inherited_read_allocation(self):
        module = self.module()
        self.track.write_bytes(raw_iso(self.data, root_size=1048577, sectors=600))
        original = module.RawIso.read; observed = []
        def read(reader, offset, length):
            observed.append(length); return original(reader, offset, length)
        with patch.object(module.RawIso, 'read', read): self.reject()
        self.assertEqual(observed, [2048])

    def test_oversized_mmd_is_rejected_before_mmd_read(self):
        self.track.write_bytes(raw_iso(self.data, rows=record('WARP__.MMD;1',21,0x40001), sectors=160))
        self.reject()

    def test_invalid_program_header_cannot_be_reported_as_a_match(self):
        data = bytearray(self.data); data[:20] = struct.pack('>BBIHIII',0,0,1,0xffff,1,0,0)
        self.track.write_bytes(raw_iso(data)); self.spec['mmd']['sha256'] = check.digest(data)
        self.reject()

    def test_unavailable_copy_tail_is_explicit_and_never_supplies_check_bytes(self):
        data = bytearray(self.data); data[:20] = struct.pack('>BBIHIII',0,0,0xff1000,0x3ff,0xff1000,0,0)
        self.track.write_bytes(raw_iso(data)); self.spec['mmd']['sha256'] = check.digest(data)
        report = self.verify()
        self.assertEqual(report['mmd']['header']['declared_copy_bytes'], 4096)
        self.assertEqual(report['mmd']['header']['unresolved_copy_bytes'], 2048)
        self.spec['spans'][0].update(offset=len(data), bytes=1)
        self.reject(2)

    def test_schema_rejects_unknown_fields_bool_counts_duplicate_keys_and_domain_substitution(self):
        original = copy.deepcopy(self.spec)
        for mutate in (lambda s:s.update(domain='RELOCATED_COPY'), lambda s:s['cue'].update(bytes=True),
                       lambda s:s['mmd'].update(path='WARP__.MMD'), lambda s:s.update(version=2)):
            self.spec = copy.deepcopy(original); mutate(self.spec)
            with self.subTest(spec=self.spec): self.reject()
        self.recipe.write_text('{"version":1,"version":1}\n')
        with self.assertRaises(check.CheckError): self.module().verify(self.recipe,self.root)

    def test_shared_operand_validation_and_mismatch_are_not_bypassed(self):
        for field, value, code in (('expected_target',0x2095bf,1),('width',2,2),('kind','cpu-evaluate',3)):
            old = self.spec['operands'][0][field]; self.spec['operands'][0][field] = value
            with self.subTest(field=field): self.reject(code)
            self.spec['operands'][0][field] = old

    def test_changed_cue_data_or_parent_prevents_success_after_checks(self):
        for kind in ('cue', 'data', 'parent'):
            module = self.module(); original = check._verify_checks
            def changed(*args, **kwargs):
                result = original(*args, **kwargs)
                if kind=='cue': self.cue.write_text(self.cue_text+'\n')
                if kind=='data':
                    with self.track.open('r+b') as stream: stream.seek(0); stream.write(b'X')
                if kind=='parent': (self.root/'new-file').write_text('changed directory')
                return result
            with self.subTest(kind=kind), patch.object(check, '_verify_checks', changed): self.reject()
            self.repin_cue(self.cue_text); self.track.write_bytes(raw_iso(self.data))
            if (self.root/'new-file').exists(): (self.root/'new-file').unlink()

    def test_unavailable_proc_fd_is_incomplete_with_no_unsafe_reopen(self):
        module = self.module()
        with patch.object(module, 'PROC_FD_ROOT', self.base/'no-proc-fd'): self.reject(3)

    def test_nofollow_binding_rejects_symlink_swapped_in_before_open(self):
        original = os.open
        other = self.base/'unrelated.bin'; other.write_bytes(raw_iso(self.data))
        def swapped(path, *args, **kwargs):
            if Path(path) == self.track:
                self.track.unlink(); self.track.symlink_to(other)
            return original(path, *args, **kwargs)
        with patch('os.open', side_effect=swapped): self.reject(3)

    def test_bound_reader_does_not_follow_original_path_replacement(self):
        module = self.module(); original = module.RawIso.read
        reads = []
        def replaced(reader, offset, length):
            if not reads:
                self.track.rename(self.root/'replaced.bin')
                self.track.write_bytes(b'X'*len(raw_iso(self.data)))
            reads.append((str(reader.path), length))
            return original(reader, offset, length)
        with patch.object(module.RawIso, 'read', replaced): self.reject(3)
        self.assertTrue(reads)
        self.assertTrue(all(path.startswith('/proc/self/fd/') for path, _ in reads))

    def test_nonregular_data_is_rejected_without_opening_fifo(self):
        self.track.unlink(); os.mkfifo(self.track)
        original = os.open
        def guarded(path, *args, **kwargs):
            self.assertNotEqual(Path(path), self.track)
            return original(path, *args, **kwargs)
        with patch('os.open', side_effect=guarded): self.reject(2)

    def test_recipe_must_be_external_to_selected_original_media_root(self):
        self.recipe = self.root/'recipe.json'
        self.reject(2)

    def test_cue_and_root_entry_limits_reject_before_unbounded_work(self):
        module = self.module()
        with patch.object(module, 'MAX_ROOT_ENTRIES', 0): self.reject(2)
        self.cue.write_bytes(b'X'*65537)
        self.reject(2)

    def test_cli_mismatch_has_no_success_stdout_or_raw_bytes(self):
        self.spec['mmd']['sha256'] = '0'*64; self.write()
        result = subprocess.run([sys.executable,'-B',str(Path(check.__file__).with_name('native_operand_disc.py')),
            '--root',str(self.root),'--recipe',str(self.recipe)],capture_output=True,text=True)
        self.assertEqual(result.returncode,1,result.stderr); self.assertEqual(result.stdout,'')
        self.assertEqual(json.loads(result.stderr)['status'],'mismatch')
        self.assertNotIn('43fb0022',result.stderr)

    def test_cli_invalid_and_incomplete_recipes_have_distinct_nonzero_exits(self):
        original = copy.deepcopy(self.spec)
        for changes, code, status in ((dict(unknown=True),2,'invalid'), (dict(version=2),3,'incomplete')):
            self.spec = {**original, **changes}; self.write()
            result = subprocess.run([sys.executable,'-B',str(Path(check.__file__).with_name('native_operand_disc.py')),
                '--root',str(self.root),'--recipe',str(self.recipe)],capture_output=True,text=True)
            with self.subTest(status=status):
                self.assertEqual(result.returncode,code,result.stderr)
                self.assertEqual(result.stdout,'')
                self.assertEqual(json.loads(result.stderr)['status'],status)


if __name__=='__main__': unittest.main()
