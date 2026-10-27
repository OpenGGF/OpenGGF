"""Native toolchain/platform safety controls; no compiler process or downloads."""
import tempfile
from pathlib import Path
import unittest
from build import check_toolchain, native_dependencies, VERSION
import os
from toolchain import ASSETS

class NativeBuildTests(unittest.TestCase):
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
