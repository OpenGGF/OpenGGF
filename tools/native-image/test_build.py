"""Native toolchain/platform safety controls; no compiler process or downloads."""
import tempfile
from pathlib import Path
import unittest
from build import check_toolchain, compile_prepared, native_dependencies, compiler_options, VERSION
from unittest.mock import patch
import os
from toolchain import ASSETS

class NativeBuildTests(unittest.TestCase):
    def test_platform_recipes_and_heap_override(self):
        for system in ('Darwin', 'Linux', 'Windows', 'FreeBSD'):
            with self.subTest(system=system), patch('build.platform.system', return_value=system):
                with patch.dict(os.environ, {}, clear=True):
                    options = compiler_options()
                self.assertEqual(system == 'Darwin', '-Os' in options)
                self.assertFalse(any(option.startswith('-O') and option != '-Os' for option in options))
                self.assertIn('-J-Xmx5g' if system == 'Darwin' else '-J-Xmx8g', options)
                self.assertIn('-J-XX:+UseG1GC', options)
                self.assertIn('-J-XX:ActiveProcessorCount=3', options)
                self.assertIn('--parallelism=2', options)
                with patch.dict(os.environ, {'OPENGGF_NATIVE_IMAGE_HEAP': '10g'}, clear=True):
                    overridden = compiler_options()
                self.assertEqual([option for option in options if not option.startswith('-J-Xmx')],
                                 [option for option in overridden if not option.startswith('-J-Xmx')])
                self.assertIn('-J-Xmx10g', overridden)

    def test_compile_stage_reuses_the_prepared_recipe(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            graal, output = root / 'jdk', root / 'target'
            graal.mkdir()
            (graal / 'release').write_text(f'IMPLEMENTOR="GraalVM Community"\nGRAALVM_VERSION="{VERSION.split("+")[0]}"\n')
            with self.assertRaisesRegex(ValueError, 'Prepare the native build'):
                compile_prepared(graal, output)
            argfile = output / 'native-mods/native-image.args'
            argfile.parent.mkdir(parents=True)
            argfile.write_text('"prepared arguments"\n')
            with patch('build.run') as launch:
                compile_prepared(graal, output)
                executable = 'native-image.cmd' if os.name == 'nt' else 'native-image'
                launch.assert_called_once_with([graal / 'bin' / executable, '@' + str(argfile)], output)

    def test_all_release_targets_have_distinct_pinned_archives(self):
        self.assertEqual({('Linux', 'x86_64'), ('Windows', 'AMD64'), ('Darwin', 'arm64')}, set(ASSETS))
        self.assertEqual(3, len({name for name, _ in ASSETS.values()}))
        for _, checksum in ASSETS.values(): self.assertRegex(checksum, r'^[0-9a-f]{64}$')

    def test_native_analysis_excludes_the_transitive_ffm_core(self):
        paths = ['deps/lwjgl-3.4.1.jar', 'deps/lwjgl-opengl-3.4.1.jar', 'deps/lwjgl-3.4.1-unsafe.jar']
        self.assertEqual(['deps/lwjgl-3.4.1-unsafe.jar', 'deps/lwjgl-opengl-3.4.1.jar'],
                         native_dependencies(os.pathsep.join(paths)).split(os.pathsep))
        with self.assertRaisesRegex(ValueError, 'unsafe core'):
            native_dependencies(os.pathsep.join(paths[:2]))

    def test_closed_world_and_oracle_toolchains_are_refused(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for implementor, version in [('Oracle Corporation', VERSION.split('+')[0]), ('GraalVM Community', '25.3.4.1')]:
                (root / 'release').write_text(f'IMPLEMENTOR="{implementor}"\nGRAALVM_VERSION="{version}"\n')
                with self.assertRaisesRegex(ValueError, 'no closed-world fallback'): check_toolchain(root)
            (root / 'release').write_text(f'IMPLEMENTOR="GraalVM Community"\nGRAALVM_VERSION="{VERSION.split("+")[0]}"\n')
            check_toolchain(root)

if __name__ == '__main__': unittest.main()
