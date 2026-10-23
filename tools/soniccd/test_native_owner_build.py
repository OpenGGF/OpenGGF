"""ROM-free provenance rejection tests for the CD-02 build lever."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import native_owner_build as build
import native_owner_patch as owner_patch


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


class BuildControls(unittest.TestCase):
    def test_checked_patch_is_regenerated_from_current_ledger_and_pinned_source(self):
        # Optional source-only integration check; no Git or build is run.
        import os
        source = Path(os.environ.get('NATIVE_OWNER_PINNED_SOURCE',
            build.ROOT / 'pinned-r2/source'))
        if not source.is_dir():
            self.skipTest('Complete pinned native sources required')
        actual = Path(owner_patch.__file__).with_name('native_owner_core.patch').read_bytes()
        self.assertEqual(actual, owner_patch.generate(source).encode())

    def test_rejects_output_outside_owned_root(self):
        with self.assertRaisesRegex(ValueError, 'real child'):
            build.fresh(Path('/tmp/native_owner_must_not_exist'))

    def test_existing_output_is_preserved(self):
        with tempfile.TemporaryDirectory() as name, patch.object(build, 'ROOT', Path(name)):
            target = Path(name) / 'owned'
            target.mkdir()
            (target / 'unknown').write_text('preserve')
            with self.assertRaises(FileExistsError):
                build.fresh(target)
            self.assertEqual((target / 'unknown').read_text(), 'preserve')

    def test_rejects_source_corruption(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'source.c').write_bytes(b'altered')
            listing = f'100644 blob {blob(b"original")}\tsource.c\0'.encode()
            with patch.object(build, 'git', return_value=listing), self.assertRaisesRegex(ValueError, 'blob differs'):
                build.verify_files(root, 'pin', root)

    def test_restores_archive_export_substitution_from_exact_blob(self):
        original = b'$Format:literal$'
        listing = f'100644 blob {blob(original)}\tproject.txt\0'.encode()
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'project.txt').write_text('expanded archive attribute')
            with patch.object(build, 'git', side_effect=[listing, original]):
                self.assertEqual(build.restore_archive_attributes(root, 'pin', root), 1)
            with patch.object(build, 'git', return_value=listing):
                self.assertEqual(build.verify_files(root, 'pin', root), 1)

    def test_rejects_wrong_commit_content(self):
        with patch.object(build, 'git', return_value=b'tree fake\n'), self.assertRaisesRegex(ValueError, 'commit/tree'):
            build.verify_commit(Path('/read-only'), '0' * 40, '1' * 40)

    def test_rejects_executable_mode_change(self):
        data = b'script'
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            f = root / 'run.sh'
            f.write_bytes(data)
            f.chmod(0o644)
            listing = f'100755 blob {blob(data)}\trun.sh\0'.encode()
            with patch.object(build, 'git', return_value=listing), self.assertRaisesRegex(ValueError, 'mode differs'):
                build.verify_files(root, 'pin', root)


if __name__ == '__main__':
    unittest.main()
