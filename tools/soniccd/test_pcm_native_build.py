"""ROM-free source/patch/ABI checks for bounded PCM native fixtures, CD-06."""
import hashlib
import os
from pathlib import Path
import tempfile
import unittest

import pcm_native_build as build
from pcm_native_common import fresh, ROOT
from pcm_native_patch import generate, PCM, PCM_SHA256


class TestPcmNativeBuildSafety(unittest.TestCase):
    def test_wrong_pcm_source_fails_before_patch_or_build(self):
        ROOT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT,prefix='test-pcm-native-source-negative-') as d:
            root=Path(d); pcm=root/PCM; pcm.parent.mkdir(parents=True); pcm.write_text('not pinned pcm.c\n')
            with self.assertRaisesRegex(ValueError,'Unaltered pinned'):
                generate(root)

    def test_output_guard_rejects_main_old_sources_and_symlink_paths(self):
        for path in [Path.cwd(),Path('/tmp/pcm-unowned'),ROOT,ROOT.parent/'owner-ledger/instrumented-r3']:
            with self.subTest(path=path),self.assertRaises(ValueError): fresh(path)

    def test_export_set_is_explicit_typed_pcm_only(self):
        self.assertEqual(11,len(build.EXPORTS))
        self.assertEqual({'pcm_native_'+n for n in ['abi','summary_bytes','block_bytes','capacity','fault',
            'dropped','count','ready','configure','poll','copy_block']},build.EXPORTS)

    def test_hook_storage_and_calls_never_use_handlers_or_gameplay_writes(self):
        text=Path(__file__).with_name('pcm_native_observer.c').read_text()
        self.assertEqual(1,text.count('} pcm_observer ECL_INVISIBLE;'))
        for name in ['pcm_write(', 'pcm_read(', 'pcm_run(', 's68k_run(', 'm68k_run(', 'blip_add_delta', 'config.pcm_volume =']:
            self.assertNotIn(name,text)
        self.assertIn('length > SAMPLES',text)
        self.assertIn('pcm_observer.fault |= 1',text)


@unittest.skipUnless(os.environ.get('OPENGGF_PCM_NATIVE_SOURCE'),'explicit pinned complete source required')
class TestActualPcmNativePatch(unittest.TestCase):
    def test_patch_is_derived_from_exact_source_and_clip_phase(self):
        source=Path(os.environ['OPENGGF_PCM_NATIVE_SOURCE'])
        self.assertEqual(PCM_SHA256,hashlib.sha256((source/PCM).read_bytes()).hexdigest())
        patch_text=generate(source)
        self.assertEqual(Path(__file__).with_name('pcm_native_core.patch').read_bytes(),patch_text.encode())
        self.assertIn('pcm_observe_sample(i, l, r, prev_l, prev_r);',patch_text)
        self.assertIn('/* PCM output mixing level (0-100%) */',patch_text)
        self.assertNotIn('native_owner_',patch_text)


if __name__=='__main__': unittest.main()
