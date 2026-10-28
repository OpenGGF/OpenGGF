"""ROM/compiler-free controls for research variant input isolation."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import native_variant


class NativeVariantTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / 'variant'
        self.engine = self.root / 'engine-input.jar'
        self.preserved = self.root / 'preserved-engine.jar'
        for jar in (self.engine, self.preserved):
            with zipfile.ZipFile(jar, 'w') as z:
                z.writestr('example/Hot.class', b'original')
                z.writestr('unchanged.txt', b'resource')
        self.base = self.root / 'base.args'
        args = ['-H:+RuntimeClassLoading', '-H:-PreserveIncludesJNI',
                f'-H:Preserve=path={self.preserved},package=java.util',
                '-H:IncludeResources=example/.*', '--features=Feature',
                '-J--add-exports=module/package=ALL-UNNAMED',
                '-cp', f'host:{self.preserved}:{self.engine}:dependency.jar',
                'com.openggf.tools.nativeimage.NativeEngine', 'old-output']
        self.base.write_text('\n'.join(map(json.dumps, args)) + '\n')

    def run_variant(self, *extra):
        command = ['native_variant', '--base-args', str(self.base), '--native-image',
                   str(self.root / 'bin/native-image'), '--output', str(self.output), *extra]

        def fake_run(args, **kwargs):
            if args[0] == '/usr/bin/time':
                (self.output / 'OpenGGF').write_bytes(b'fake-image')
            return subprocess.CompletedProcess(args, 0)

        with patch.object(sys, 'argv', command), patch.object(
                native_variant.subprocess, 'run', side_effect=fake_run) as run:
            code = native_variant.main()
        self.assertEqual(0, code)
        return json.loads((self.output / 'build.json').read_text()), run

    def test_closed_control_removes_only_mod_loading_recipe(self):
        result, run = self.run_variant('--closed-world')
        args = result['arguments']
        self.assertEqual(['-O2', '-H:-DeleteLocalSymbols'], args[:2])
        self.assertFalse(any(s.startswith(('-H:Preserve=', '--features=',
                                          '-J--add-exports=')) for s in args))
        self.assertNotIn('-H:+RuntimeClassLoading', args)
        self.assertIn('-H:IncludeResources=META-INF/openggf/bundled-mods.json', args)
        self.assertEqual('com.openggf.tools.nativeimage.LegacyBenchmarkEngine', args[-2])
        self.assertEqual(3, run.call_count)  # build, extract symbols, strip

    def test_overlay_updates_both_jars_and_preserve_path_without_mutating_base(self):
        classes = self.root / 'classes'
        (classes / 'example').mkdir(parents=True)
        (classes / 'example/Hot.class').write_bytes(b'final-variant')
        result, _ = self.run_variant('--classes', str(classes),
                                     '--option=-H:TuneInlinerExploration=1')
        args = result['arguments']
        self.assertIn('-H:+RuntimeClassLoading', args)
        self.assertIn(f'-H:Preserve=path={self.output}/preserved-engine.jar,package=java.util', args)
        self.assertEqual('-H:TuneInlinerExploration=1', args[0])
        for jar in (self.engine, self.preserved):
            with zipfile.ZipFile(jar) as z:
                self.assertEqual(b'original', z.read('example/Hot.class'))
            with zipfile.ZipFile(self.output / jar.name) as z:
                self.assertEqual(b'final-variant', z.read('example/Hot.class'))
                self.assertEqual(b'resource', z.read('unchanged.txt'))

    def test_unknown_overlay_class_is_rejected(self):
        classes = self.root / 'classes'
        classes.mkdir()
        (classes / 'Unknown.class').write_bytes(b'unexpected')
        with self.assertRaisesRegex(ValueError, 'outside prepared engine'):
            self.run_variant('--classes', str(classes))

    def test_existing_output_is_rejected(self):
        self.output.mkdir()
        with self.assertRaises(FileExistsError):
            self.run_variant()


if __name__ == '__main__':
    unittest.main()
