"""Hero reel manifests and driver checks; no ROM, JVM or GPU."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('hero_reels', Path(__file__).with_name('hero_reels.py'))
hero = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hero)


def fake_repo(temp):
    """A repository root with one trace fixture and one input movie."""
    root = Path(temp)
    trace = root / hero.TRACE_ROOT / 's1' / 'ghz1_completerun'
    trace.mkdir(parents=True)
    (trace / 'metadata.json').write_text(json.dumps(
        {'zone': 'ghz', 'act': 1, 'trace_frame_count': 1000, 'main_character': 'sonic',
         'source_bk2': 'run.bk2', 'bk2_frame_offset': 788}))
    (root / 'inputs').mkdir()
    (root / 'inputs' / 'route.bk2').write_bytes(b'bk2')
    return root


def manifest():
    return {
        'schema': 1, 'game': 's1', 'output': 'promo-s1',
        'video': {'fps': 60, 'scale': 2, 'crf': 20, 'max_bytes': 20_000_000,
                  'fade_in_frames': 12, 'fade_out_frames': 30},
        'poster': {'shot': 'hills', 'frame': 30},
        'shots': [
            {'id': 'hills', 'label': 'Green Hill', 'source': 'trace', 'trace': 'ghz1_completerun',
             'start': 600, 'frames': 240, 'team': 'sonic'},
            {'id': 'stage', 'label': 'Special Stage', 'source': 'capture', 'zone': 'ghz', 'act': 1,
             'input': 'inputs/route.bk2', 'input_start': 774, 'start': 4400, 'frames': 180,
             'expect_mode': 'SPECIAL_STAGE', 'dip_in': 6, 'setup': {'rings': 50}},
        ],
    }


class ManifestValidation(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = fake_repo(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def assertRejected(self, edit, pattern):
        data = manifest()
        edit(data)
        with self.assertRaisesRegex(hero.ReelError, pattern):
            hero.validate_manifest(data, 's1', root=self.root)

    def test_valid_manifest_passes(self):
        hero.validate_manifest(manifest(), 's1', root=self.root)

    def test_duplicate_shot_ids_are_rejected(self):
        self.assertRejected(lambda d: d['shots'].append(copy.deepcopy(d['shots'][0])), 'duplicated')

    def test_window_past_trace_end_is_rejected(self):
        self.assertRejected(lambda d: d['shots'][0].update(start=900), 'past trace length')

    def test_missing_trace_and_input_are_rejected(self):
        self.assertRejected(lambda d: d['shots'][0].update(trace='nope'), "trace 'nope' not found")
        self.assertRejected(lambda d: d['shots'][1].update(input='inputs/missing.bk2'), 'not found')

    def test_declared_team_must_match_trace(self):
        self.assertRejected(lambda d: d['shots'][0].update(team='sonic+tails'), 'differs from trace')

    def test_zero_length_and_unknown_setup_are_rejected(self):
        self.assertRejected(lambda d: d['shots'][0].update(frames=0), 'frames must be > 0')
        self.assertRejected(lambda d: d['shots'][1]['setup'].update(teleport=1), 'unknown setup')

    def test_poster_must_lie_inside_its_shot(self):
        self.assertRejected(lambda d: d['poster'].update(frame=240), 'poster.frame')
        self.assertRejected(lambda d: d['poster'].update(shot='gone'), 'poster.shot')

    def test_wrong_game_and_schema_are_rejected(self):
        self.assertRejected(lambda d: d.update(game='s2'), "game must be 's1'")
        self.assertRejected(lambda d: d.update(schema=2), 'schema')

    def test_committed_manifests_validate(self):
        for game in hero.GAMES:
            with self.subTest(game=game):
                loaded = hero.load_manifest(game)
                self.assertEqual(64, len(loaded['_sha256']))
                total = sum(shot['frames'] for shot in loaded['shots'])
                self.assertGreater(total, 30 * loaded['video']['fps'])


class Commands(unittest.TestCase):
    def test_trace_shot_uses_inclusive_clip_window_without_ghosts(self):
        args = hero.trace_args('s1', manifest()['shots'][0], Path('/out'), root=Path('/repo'))
        self.assertEqual('/repo/src/test/resources/traces/s1/ghz1_completerun', args[1])
        self.assertIn('frames:600:839', args)
        self.assertIn('--no-ghosts', args)
        self.assertEqual('1', args[args.index('--scale') + 1])

    def test_capture_shot_steps_through_window_end_and_captures_every_frame(self):
        args = hero.capture_args('s1', manifest()['shots'][1], '/rom.gen', Path('/out'), root=Path('/repo'))
        self.assertEqual('4580', args[args.index('--frames') + 1])
        self.assertEqual('4400', args[args.index('--capture-from') + 1])
        self.assertEqual('774', args[args.index('--input-start') + 1])
        self.assertEqual('50', args[args.index('--rings') + 1])
        self.assertEqual('none', args[args.index('--sidekick') + 1])

    def test_jvm_runs_display_free_in_isolated_user_dir(self):
        command = hero.java_command('cp', Path('/work/runtime'), 'Main', ['--x'])
        self.assertIn('-Dopenggf.headless.gl=surfaceless-egl', command)
        self.assertIn('-Duser.dir=/work/runtime', command)
        with mock.patch.dict(hero.os.environ, {'DISPLAY': ':0', 'WAYLAND_DISPLAY': 'w'}):
            env = hero.headless_env()
        self.assertNotIn('DISPLAY', env)
        self.assertNotIn('WAYLAND_DISPLAY', env)

    def test_runtime_config_names_roms_and_catalog_absolutely(self):
        text = hero.runtime_config({'s1': '/roms/s1.gen'}, root=Path('/repo'))
        self.assertIn('sonic1: "/roms/s1.gen"', text)
        self.assertIn('catalogDir: "/repo/src/test/resources/traces"', text)
        self.assertNotIn('sonic2', text)

    def test_missing_rom_fails_loudly(self):
        with self.assertRaisesRegex(hero.ReelError, 'no verified s2 ROM'):
            hero.require_rom({'s1': '/roms/s1.gen'}, 's2')


class Assembly(unittest.TestCase):
    def test_layout_is_contiguous(self):
        self.assertEqual([('hills', 0, 240), ('stage', 240, 420)], hero.reel_layout(manifest()))

    def test_filter_graph_trims_dips_scales_and_fades_loop_ends(self):
        graph = hero.filter_graph(manifest())
        self.assertIn('[0:v]trim=end_frame=240,setpts=PTS-STARTPTS[s0]', graph)
        self.assertIn('[1:v]trim=end_frame=180,setpts=PTS-STARTPTS,fade=t=in:s=0:n=6[s1]', graph)
        self.assertIn('concat=n=2:v=1:a=0,scale=iw*2:ih*2:flags=neighbor', graph)
        self.assertIn('fade=t=out:s=390:n=30', graph)
        self.assertTrue(graph.endswith('format=yuv420p[out]'))

    def test_encode_is_silent_faststart_h264(self):
        command = hero.encode_command(manifest(), ['a.mkv', 'b.mkv'], 'reel.mp4')
        self.assertIn('-an', command)
        self.assertEqual('+faststart', command[command.index('-movflags') + 1])
        self.assertEqual('libx264', command[command.index('-c:v') + 1])

    def test_faststart_detection_reads_atom_order(self):
        def atom(kind, body=b''):
            return (8 + len(body)).to_bytes(4, 'big') + kind + body
        with tempfile.TemporaryDirectory() as temp:
            good, bad = Path(temp) / 'good.mp4', Path(temp) / 'bad.mp4'
            good.write_bytes(atom(b'ftyp', b'isom') + atom(b'moov') + atom(b'mdat', b'x' * 16))
            bad.write_bytes(atom(b'ftyp', b'isom') + atom(b'mdat', b'x' * 16) + atom(b'moov'))
            self.assertTrue(hero.is_faststart(good))
            self.assertFalse(hero.is_faststart(bad))


class CaptureWindow(unittest.TestCase):
    HEADER = 'frame,x,y,xvel,yvel,gspeed,air,rolling,spindash,hurt,dead,rings,mapping_frame,cam_x,cam_y,' \
             'sk_present,sk_x,sk_y,high_priority,mode,input\n'

    def write_state(self, temp, rows, dead_at=None, mode='LEVEL'):
        path = Path(temp) / 'state.csv'
        lines = [self.HEADER]
        for frame in range(rows):
            dead = '1' if dead_at is not None and frame >= dead_at else '0'
            lines.append(f'{frame},{frame},0,0,0,0,0,0,0,0,{dead},0,0,0,0,0,0,0,0,{mode},\n')
        path.write_text(''.join(lines))
        return path

    def shot(self, **extra):
        return {'id': 'probe', 'start': 10, 'frames': 20, **extra}

    def test_short_capture_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(hero.ReelError, 'stepped 25 frames, window needs 30'):
                hero.check_capture_window(self.write_state(temp, 25), self.shot())

    def test_death_inside_window_is_rejected_but_after_it_is_not(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(hero.ReelError, 'dead at frame 15'):
                hero.check_capture_window(self.write_state(temp, 40, dead_at=15), self.shot())
            detail = hero.check_capture_window(self.write_state(temp, 40, dead_at=30), self.shot())
            self.assertEqual((10, 29), (detail['first_x'], detail['last_x']))

    def test_unexpected_mode_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(hero.ReelError, 'expected only SPECIAL_STAGE'):
                hero.check_capture_window(self.write_state(temp, 40),
                                          self.shot(expect_mode='SPECIAL_STAGE'))


if __name__ == '__main__':
    unittest.main()
