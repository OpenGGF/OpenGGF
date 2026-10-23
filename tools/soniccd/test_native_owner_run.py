"""Behavioral rejection controls for owner-core AV/restore comparisons."""
import json
from pathlib import Path
import tempfile
import unittest

from native_owner_run import compare, verify_run


def fixture(root):
    root.mkdir()
    summary = dict(frames=2, is_mega_cd=True, restore_120_frames_equal=True,
                   observation_savestate_unchanged=True, checkpoint_view={'RAM': 'a'},
                   final_view={'RAM': 'b'}, fps_num=53693175, fps_den=896040)
    (root / 'summary.json').write_text(json.dumps(summary))
    (root / 'av.jsonl').write_text('\n'.join(json.dumps(dict(frame=n, start=n == 2, audio='a')) for n in [1, 2]))
    (root / 'continuation.jsonl').write_text('\n'.join(json.dumps(dict(frame=n, start=False)) for n in range(3, 123)))
    (root / 'views.jsonl').write_text('RAM and CPU view')
    return summary


class RunControls(unittest.TestCase):
    def test_rejects_failed_restore(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name) / 'run'
            summary = fixture(root)
            summary['restore_120_frames_equal'] = False
            (root / 'summary.json').write_text(json.dumps(summary))
            with self.assertRaisesRegex(ValueError, 'restore failed'):
                verify_run(root, 2, [2])

    def test_rejects_observer_saved_state_side_effect(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name) / 'run'
            summary = fixture(root)
            summary['observation_savestate_unchanged'] = False
            (root / 'summary.json').write_text(json.dumps(summary))
            with self.assertRaisesRegex(ValueError, 'control/restore'):
                verify_run(root, 2, [2])

    def test_rejects_missing_frame(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name) / 'run'
            fixture(root)
            (root / 'av.jsonl').write_text(json.dumps(dict(frame=2, start=True)))
            with self.assertRaisesRegex(ValueError, 'unordered'):
                verify_run(root, 2, [2])

    def test_rejects_wrong_controller_schedule(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name) / 'run'
            fixture(root)
            with self.assertRaisesRegex(ValueError, 'input differs'):
                verify_run(root, 2, [1])

    def test_rejects_changed_av(self):
        with tempfile.TemporaryDirectory() as name:
            a, b = Path(name) / 'a', Path(name) / 'b'
            fixture(a); fixture(b)
            (b / 'av.jsonl').write_text('changed synthesized audio')
            with self.assertRaisesRegex(ValueError, 'av.jsonl'):
                compare(a, b)

    def test_rejects_changed_cpu_or_ram(self):
        with tempfile.TemporaryDirectory() as name:
            a, b = Path(name) / 'a', Path(name) / 'b'
            fixture(a); fixture(b)
            (b / 'views.jsonl').write_text('different CPU')
            with self.assertRaisesRegex(ValueError, 'views.jsonl'):
                compare(a, b)


if __name__ == '__main__':
    unittest.main()
