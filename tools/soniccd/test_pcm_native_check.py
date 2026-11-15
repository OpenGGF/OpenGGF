"""ROM-free rejection checks and opt-in complete native capture checks, CD-06.

2026-10-23. Constructed protocol bytes below test admission only; they are
never saved as native evidence. Native data comes solely from the real core.
"""
import copy
import gzip
import json
import os
from pathlib import Path
import struct
import tempfile
import unittest

import pcm_native_check as check
from pcm_native_common import BLOCK_BYTES, ROOT
from pcm_native_run import parse_inputs


def protocol_block(enabled=128):
    data=bytearray(BLOCK_BYTES)
    struct.pack_into('<16I',data,0,0x424d4350,1,7,3,2,224,65536,32,2,1,1,0,0,0,0,0)
    for start,cycles in [(64,0),(288,3072)]:
        struct.pack_into('<8I',data,start+48*4,enabled,0,0,0,0,0,cycles,100)
    for n in range(2):
        struct.pack_into('<IiiiiIII',data,131584+n*32,n,0,0,0,0,int(bool(enabled)),0,0)
    return data


class TestPcmNativeExtentRejection(unittest.TestCase):
    def test_complete_protocol_block_and_exact_result(self):
        b=check.decode_block(protocol_block())
        expected=dict(state=check.device_state(b['post']),waveRam=b['ram_post'],
                      steps=[[0,0,0,0,True],[0,0,0,0,True]])
        check.compare_device_result(b,expected)

    def test_missing_trailing_or_oversized_blocks_fail(self):
        for payload in [protocol_block()[:-1],protocol_block()+b'\0',b'']:
            with self.subTest(length=len(payload)),self.assertRaises(ValueError):
                check.decode_block(payload)

    def test_every_header_extent_phase_and_reserved_word_is_checked(self):
        for offset,value in [(0,0),(4,2),(8,0),(12,0),(16,0),(16,4097),(20,220),(24,65535),
                             (28,28),(32,1),(36,2),(40,0)]+[(i*4,1) for i in range(11,16)]:
            bad=protocol_block(); struct.pack_into('<I',bad,offset,value)
            with self.subTest(offset=offset,value=value),self.assertRaises(ValueError):
                check.decode_block(bad)

    def test_native_channel_and_chip_widths_reject_instead_of_masking(self):
        for word,value in [(1,1),(2,65536),(3,65536),(4,256),(5,256),(48,1),(49,256),
                           (50,8),(51,16),(52,32768),(53,0xffff7fff),(55,99)]:
            bad=protocol_block(); struct.pack_into('<I',bad,64+word*4,value)
            with self.subTest(word=word),self.assertRaises(ValueError):
                check.decode_block(bad)

    def test_ram_mutation_in_any_bank_fails_even_outside_active_channel(self):
        for n in [0,4095,4096,32768,65535]:
            bad=protocol_block(); bad[66048+n]=1
            with self.subTest(address=n),self.assertRaisesRegex(ValueError,'RAM changed'):
                check.decode_block(bad)

    def test_sample_delta_submission_loop_and_padding_corruption_fail(self):
        for relative,value in [(0,1),(4,32768),(12,1),(20,0),(24,256),(28,1),(64,1),(4095*32,1)]:
            bad=protocol_block(); struct.pack_into('<I',bad,131584+relative,value)
            with self.subTest(offset=relative),self.assertRaises(ValueError):
                check.decode_block(bad)

    def test_output_context_cycles_and_register_changes_are_not_hidden(self):
        for offset,value in [(288,1),(288+4,1<<19),(288+48*4,0),(288+49*4,1),(288+50*4,1),
                             (288+51*4,1),(288+52*4,1),(288+54*4,3073)]:
            bad=protocol_block(); struct.pack_into('<I',bad,offset,value)
            with self.subTest(offset=offset),self.assertRaises(ValueError):
                check.decode_block(bad)

    def test_exact_comparison_covers_all_channels_selectors_ram_and_samples(self):
        b=check.decode_block(protocol_block())
        good=dict(state=check.device_state(b['post']),waveRam=b['ram_post'],steps=[[0,0,0,0,True]]*2)
        bad_values=[]
        for channel in range(8):
            for field in range(6):
                bad=copy.deepcopy(good); bad['state']['channels'][channel][field]=1; bad_values.append(bad)
        for field in ['enabledChannelMask','selectedChannel','selectedBank','leftOutput','rightOutput']:
            bad=copy.deepcopy(good); bad['state'][field]=1; bad_values.append(bad)
        bad=copy.deepcopy(good); bad['state']['chipEnabled']=False; bad_values.append(bad)
        for address in [0,4096,65535]:
            bad=copy.deepcopy(good); ram=bytearray(bad['waveRam']); ram[address]=1; bad['waveRam']=bytes(ram); bad_values.append(bad)
        for field in range(5):
            bad=copy.deepcopy(good); bad['steps']=copy.deepcopy(bad['steps']); bad['steps'][1][field]=False if field==4 else 1; bad_values.append(bad)
        for key in good:
            bad=copy.deepcopy(good); del bad[key]; bad_values.append(bad)
        bad=copy.deepcopy(good); bad['ignored-mask']=0; bad_values.append(bad)
        bad=copy.deepcopy(good); bad['steps'][0][4]=1; bad_values.append(bad)
        for bad in bad_values:
            with self.assertRaisesRegex(ValueError,'exact device/RAM/output'):
                check.compare_device_result(b,bad)

    def test_summary_missing_reordered_and_wrong_widths_reject(self):
        words=[1,1,2,128,0,0,0,0,3072,0,0,0,0,0,0,100]
        data=struct.pack('<16I',*words)
        self.assertEqual(1,len(check.summaries(data,1)))
        for bad in [data[:-1],data+data]:
            with self.assertRaises(ValueError): check.summaries(bad,1)
        for index,value in [(0,2),(1,0),(1,2),(3,1),(4,256),(5,8),(6,16),(8,3073),(13,3),(14,17),(15,99)]:
            row=words.copy(); row[index]=value
            with self.subTest(index=index),self.assertRaises(ValueError):
                check.summaries(struct.pack('<16I',*row),1)

    def test_compression_bound_rejects_before_unbounded_allocation(self):
        ROOT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT,prefix='test-pcm-native-negative-') as d:
            path=Path(d)/'block.gz'; path.write_bytes(gzip.compress(b'\0'*(BLOCK_BYTES+1),mtime=0))
            with self.assertRaisesRegex(ValueError,'declared bound'):
                check.uncompress(path,BLOCK_BYTES)

    def test_authored_inputs_are_bounded_normal_controls(self):
        self.assertEqual([dict(first=3,last=5,button='A')],parse_inputs('3-5:A',10))
        for text in ['0:A','3-2:A','1-11:A','2:PCM','1:Start;touch x','-1:A']:
            with self.subTest(text=text),self.assertRaises(ValueError): parse_inputs(text,10)


@unittest.skipUnless(os.environ.get('OPENGGF_PCM_NATIVE_CAPTURE'),'explicit actual native capture required')
class TestActualPcmNativeCapture(unittest.TestCase):
    def test_actual_overflow_is_incomplete_evidence_despite_unchanged_emulation(self):
        root=Path(os.environ['OPENGGF_PCM_NATIVE_CAPTURE'])
        summary=json.loads((root/'overflow/summary.json').read_text())
        self.assertEqual(1,summary['overflow_fault']); self.assertGreater(summary['overflow_dropped'],0)
        self.assertTrue(summary['restore_120_calls_equal'])
        with self.assertRaises(ValueError):
            check.summaries(check.uncompress(root/'overflow/calls.bin.gz',100000000),summary['frames'])

    def test_actual_full_extents_controls_and_native_loop_samples(self):
        root=Path(os.environ['OPENGGF_PCM_NATIVE_CAPTURE'])
        result=check.validate_capture(root)
        blocks=result['enabled-1']
        self.assertGreaterEqual(len(blocks),3); self.assertLessEqual(len(blocks),8)
        self.assertTrue(any(any(row[6] for row in b['raw']) for b in blocks))
        self.assertTrue(any(any(row[1] or row[2] for row in b['raw']) for b in blocks))
        self.assertEqual(len(blocks),len({b['ordinal'] for b in blocks}))
        for b in blocks:
            self.assertEqual(65536,len(b['ram_pre'])); self.assertEqual(b['ram_pre'],b['ram_post'])
            expected=dict(state=check.device_state(b['post']),waveRam=b['ram_post'],
                steps=[[l,r,dl,dr,bool(sub)] for _,l,r,dl,dr,sub,_,_ in b['raw']])
            check.compare_device_result(b,expected)

    def test_actual_provenance_and_whole_stream_tampering_is_rejected(self):
        original=Path(os.environ['OPENGGF_PCM_NATIVE_CAPTURE'])
        manifest=json.loads((original/'manifest.json').read_text())
        variants=[]
        for group,key in [('runtime_sha256','BizHawk.BizInvoke.dll'),('producer_sha256','pcm_native_probe.cs'),
                          ('source_pins','gpgx'),('instrumented_identity','raw_sha256')]:
            bad=copy.deepcopy(manifest); bad[group][key]='0'*64; variants.append(bad)
        for key in ['compiler_sha256','source_provenance_sha256','toolchain_provenance_sha256','bios_sha1']:
            bad=copy.deepcopy(manifest); bad[key]='0'*64; variants.append(bad)
        bad=copy.deepcopy(manifest); bad['media']['files'][0]['sha256']='0'*64; variants.append(bad)
        for name in ['calls.bin.gz','block-'+format(manifest['selected'][0],'08d')+'.bin.gz','checkpoint.state.gz']:
            bad=copy.deepcopy(manifest); bad['streams']['enabled-1'][name]['sha256']='0'*64; variants.append(bad)
        bad=copy.deepcopy(manifest); bad['controls_complete']=False; variants.append(bad)
        bad=copy.deepcopy(manifest); del bad['producer_sha256']['pcm_native_observer.h']; variants.append(bad)
        with tempfile.TemporaryDirectory(dir=ROOT,prefix='test-pcm-native-capture-negative-') as d:
            root=Path(d)
            for path in original.iterdir():
                if path.name!='manifest.json': (root/path.name).symlink_to(path,target_is_directory=path.is_dir())
            for n,bad in enumerate(variants):
                (root/'manifest.json').write_text(json.dumps(bad))
                with self.subTest(case=n),self.assertRaises(ValueError): check.validate_capture(root)


if __name__=='__main__': unittest.main()
