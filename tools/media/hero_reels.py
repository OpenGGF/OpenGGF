#!/usr/bin/env python3
"""Regenerate the website hero reels from the current engine, with no hand editing.

Inputs: one committed manifest per game (tools/media/hero-reels/<game>.json),
the compiled checkout, user-supplied ROMs found by SHA-1 and committed input
movies. Each shot runs in its own headless JVM, one after another: trace shots
render a committed trace fixture's BK2 through TraceCaptureTool (the sanctioned
replay bootstrap; trace rows are comparison data only), capture shots drive a
direct-level GameplayCaptureTool boot from a committed BK2 or input log. Shots
are joined with hard cuts (optional short dips), scaled 2x nearest-neighbour and
encoded as faststart H.264. Outputs: promo-<game>.mp4, a poster PNG/WebP, a
contact sheet and <name>.provenance.json. A missing ROM, a failed JVM, a short or
zero-frame shot, a death or black run inside a shot window, or an over-budget
encode stops the build before anything is published to --out.
Origin: 2026-11-05 replacement of the hand-cut 0.6 Resolve reels.
"""
import argparse
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST_DIR = Path(__file__).resolve().with_name('hero-reels')
GAMES = ('s1', 's2', 's3k')
TRACE_ROOT = Path('src/test/resources/traces')
CLASSPATH_FILE = Path('target/hero-reels-classpath.txt')
ROM_PROPERTY_GAME = {'sonic1.rom.path': 's1', 'sonic2.rom.path': 's2', 's3k.rom.path': 's3k'}
ROM_CONFIG_KEY = {'s1': 'sonic1', 's2': 'sonic2', 's3k': 'sonic3k'}
SOURCES = ('trace', 'capture')
CAPTURE_FLAGS = {  # manifest key -> GameplayCaptureTool flag (values pass through verbatim)
    'x': '--x', 'y': '--y', 'rings': '--rings', 'emeralds': '--emeralds', 'settle': '--settle',
    'width': '--width', 'vint_run_count': '--vint-run-count',
    'donor': '--donor',      # cross-game donation: the host borrows the donor's art and features
    'rewind': '--rewind',    # 'FRAME:LENGTH' in capture frames: hold the live rewind key
}
CAPTURE_SWITCHES = {'star_post': '--star-post', 'title_card': '--title-card',
                    'complete_special_stage': '--complete-special-stage',
                    'title_screen': '--title-screen', 'interactive_pacing': '--interactive-pacing'}
JVM_TIMEOUT_SECONDS = 1800


class ReelError(RuntimeError):
    """A condition that must stop the build rather than emit a partial reel."""


# --------------------------------------------------------------------------- manifest

def load_manifest(game, manifest_dir=MANIFEST_DIR):
    path = Path(manifest_dir) / f'{game}.json'
    if not path.is_file():
        raise ReelError(f'missing manifest {path}')
    raw = path.read_bytes()
    manifest = json.loads(raw)
    validate_manifest(manifest, game)
    manifest['_path'] = str(path)
    manifest['_sha256'] = hashlib.sha256(raw).hexdigest()
    return manifest


def validate_manifest(manifest, game, root=ROOT):
    """Reject anything that would make a shot ambiguous or unreproducible."""
    def need(cond, message):
        if not cond:
            raise ReelError(f'{game} manifest: {message}')
    need(manifest.get('schema') == 1, 'schema must be 1')
    need(manifest.get('game') == game, f"game must be '{game}'")
    need(isinstance(manifest.get('output'), str) and manifest['output'], 'output name required')
    video = manifest.get('video', {})
    for key in ('fps', 'scale', 'crf', 'max_bytes'):
        need(isinstance(video.get(key), int) and video[key] > 0, f'video.{key} must be a positive integer')
    shots = manifest.get('shots')
    need(isinstance(shots, list) and shots, 'shots must be a non-empty list')
    seen = set()
    for shot in shots:
        sid = shot.get('id')
        need(isinstance(sid, str) and sid and sid not in seen, f'shot id {sid!r} missing or duplicated')
        seen.add(sid)
        need(isinstance(shot.get('label'), str) and shot['label'], f'{sid}: label required')
        need(shot.get('source') in SOURCES, f'{sid}: source must be one of {SOURCES}')
        need(isinstance(shot.get('start'), int) and shot['start'] >= 0, f'{sid}: start must be >= 0')
        need(isinstance(shot.get('frames'), int) and shot['frames'] > 0, f'{sid}: frames must be > 0')
        for key in ('dip_in', 'dip_out'):
            value = shot.get(key, 0)
            need(isinstance(value, int) and 0 <= value * 2 <= shot['frames'], f'{sid}: {key} out of range')
        if shot['source'] == 'trace':
            trace_dir = root / TRACE_ROOT / game / str(shot.get('trace'))
            need((trace_dir / 'metadata.json').is_file(), f"{sid}: trace '{shot.get('trace')}' not found")
            meta = json.loads((trace_dir / 'metadata.json').read_text())
            last = shot['start'] + shot['frames'] - 1
            need(last < int(meta['trace_frame_count']),
                 f'{sid}: window ends at row {last} past trace length {meta["trace_frame_count"]}')
            team = trace_team(meta)
            if 'team' in shot:
                need(shot['team'] == team, f"{sid}: team {shot['team']} differs from trace {team}")
        else:
            need(isinstance(shot.get('zone'), (str, int)), f'{sid}: zone required')
            need(isinstance(shot.get('act'), int) and shot['act'] >= 1, f'{sid}: act is one-based')
            need(isinstance(shot.get('input'), str), f'{sid}: input path required')
            need((root / shot['input']).is_file(), f"{sid}: input {shot['input']} not found")
            unknown = set(shot.get('setup', {})) - set(CAPTURE_FLAGS) - set(CAPTURE_SWITCHES)
            need(not unknown, f'{sid}: unknown setup keys {sorted(unknown)}')
    poster = manifest.get('poster', {})
    need(poster.get('shot') in seen, 'poster.shot must name a shot')
    target = next(s for s in shots if s['id'] == poster['shot'])
    need(isinstance(poster.get('frame'), int) and 0 <= poster['frame'] < target['frames'],
         'poster.frame must lie inside its shot')
    return manifest


def trace_team(meta):
    sidekicks = meta.get('sidekicks') or []
    return '+'.join([meta.get('main_character', 'sonic'), *sidekicks])


def reel_layout(manifest):
    """Reel frame range [first, end) for every shot, in manifest order."""
    layout, cursor = [], 0
    for shot in manifest['shots']:
        layout.append((shot['id'], cursor, cursor + shot['frames']))
        cursor += shot['frames']
    return layout


# --------------------------------------------------------------------------- environment

def discover_roms(root=ROOT):
    """Verified ROM path per game, by SHA-1 (worktree, then main checkout)."""
    sys.path.insert(0, str(root / 'tools' / 'testing'))
    try:
        import rom_inputs
    finally:
        sys.path.pop(0)
    roms = {}
    for argument in rom_inputs.rom_args(root):
        key, value = argument[2:].split('=', 1)
        if key in ROM_PROPERTY_GAME:
            roms[ROM_PROPERTY_GAME[key]] = value
    return roms


def require_rom(roms, game):
    if game not in roms:
        raise ReelError(f'no verified {game} ROM found by SHA-1 in the worktree or main checkout; '
                        'supply the original dump (see AGENTS.md ROM table)')
    return roms[game]


def sha256_file(path):
    with open(path, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sha1_file(path):
    with open(path, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha1').hexdigest()


def git(*args, root=ROOT):
    return subprocess.run(['git', '-C', str(root), *args], check=True, capture_output=True,
                          text=True).stdout.strip()


def runtime_config(roms, root=ROOT):
    """An isolated config: never read or rewrite the checkout's (often linked) config.yaml."""
    lines = ['configFormat: 2', 'roms:']
    for game in GAMES:
        if game in roms:
            lines.append(f'  {ROM_CONFIG_KEY[game]}: {json.dumps(roms[game])}')
    lines += ['  directory: "empty-rom-dir"', 'debug:', '  testMode:',
              f'    catalogDir: {json.dumps(str(root / TRACE_ROOT))}',
              '  traceRender:', '    showDesyncGhosts: false', '    showGameHud: true',
              '    showDebugHud: false', '']
    return '\n'.join(lines)


def prepare_runtime(work, roms, root=ROOT):
    runtime = Path(work) / 'runtime'
    (runtime / 'empty-rom-dir').mkdir(parents=True, exist_ok=True)
    (runtime / 'saves').mkdir(exist_ok=True)
    (runtime / 'config.yaml').write_text(runtime_config(roms, root))
    return runtime


def compile_classpath(root=ROOT):
    """Compile through the repository queue and write the runtime classpath."""
    command = [sys.executable, str(root / 'tools/testing/maven_queue.py'), '-Dmse=off', '-q',
               'compile', 'dependency:build-classpath', f'-Dmdep.outputFile={CLASSPATH_FILE}']
    print('+', ' '.join(command), flush=True)
    if subprocess.run(command, cwd=root).returncode != 0:
        raise ReelError('compile failed')


def java_classpath(root=ROOT):
    cp_file = root / CLASSPATH_FILE
    classes = root / 'target' / 'classes'
    if not cp_file.is_file() or not classes.is_dir():
        raise ReelError(f'no compiled checkout ({classes}, {cp_file}); run without --skip-compile')
    return f'{classes}{os.pathsep}{cp_file.read_text().strip()}'


def java_command(classpath, runtime, main_class, args):
    return ['java', '--enable-native-access=ALL-UNNAMED', '-Xmx3g', '-Djava.awt.headless=true',
            '-Dopenggf.headless.gl=surfaceless-egl', f'-Duser.dir={runtime}',
            f'-Dopenggf.saveRoot={runtime / "saves"}', '-cp', classpath, main_class, *args]


def headless_env():
    """Surfaceless EGL only: no display connection and no audio device."""
    env = {k: v for k, v in os.environ.items()
           if k not in ('DISPLAY', 'WAYLAND_DISPLAY', 'XAUTHORITY', 'XDG_SESSION_TYPE')}
    env['ALSOFT_DRIVERS'] = 'null'
    return env


# --------------------------------------------------------------------------- shot commands

def trace_args(game, shot, out_dir, root=ROOT):
    trace_dir = root / TRACE_ROOT / game / shot['trace']
    end = shot['start'] + shot['frames'] - 1
    return ['--trace', str(trace_dir), '--clip', f"frames:{shot['start']}:{end}", '--no-ghosts',
            '--scale', '1', '--codec', 'ffv1', '--out-dir', str(out_dir)]


def capture_args(game, shot, rom, out_dir, root=ROOT):
    args = ['--game', game, '--rom', str(rom), '--zone', str(shot['zone']), '--act', str(shot['act']),
            '--main', shot.get('main', 'sonic'), '--sidekick', shot.get('sidekick', 'none'),
            '--input', str(root / shot['input']), '--input-start', str(shot.get('input_start', 0)),
            '--frames', str(shot['start'] + shot['frames']), '--capture-from', str(shot['start']),
            '--every', '1', '--no-video', '--out-dir', str(out_dir)]
    for key, value in shot.get('setup', {}).items():
        if key in CAPTURE_SWITCHES:
            if value:
                args.append(CAPTURE_SWITCHES[key])
        else:
            args += [CAPTURE_FLAGS[key], str(value)]
    return args


def run_jvm(command, runtime, log_path):
    print('+', ' '.join(command[-40:]), flush=True)
    with open(log_path, 'w') as log:
        try:
            result = subprocess.run(command, cwd=runtime, env=headless_env(), stdin=subprocess.DEVNULL, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=JVM_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            raise ReelError(f'capture JVM exceeded {JVM_TIMEOUT_SECONDS}s; log {log_path}')
    if result.returncode != 0:
        tail = Path(log_path).read_text(errors='replace').splitlines()[-15:]
        raise ReelError(f'capture JVM exited {result.returncode}; log {log_path}\n' + '\n'.join(tail))


def check_capture_window(state_csv, shot):
    """Fail on a short capture, a death inside the window or an unexpected game mode."""
    import csv
    with open(state_csv, newline='') as stream:
        rows = list(csv.DictReader(stream))
    first, end = shot['start'], shot['start'] + shot['frames']
    if len(rows) < end:
        raise ReelError(f"{shot['id']}: capture stepped {len(rows)} frames, window needs {end}")
    window = rows[first:end]
    if not shot.get('allow_death') and any(row['dead'] == '1' for row in window):
        frame = next(row['frame'] for row in window if row['dead'] == '1')
        raise ReelError(f"{shot['id']}: leader dead at frame {frame} inside the shot window")
    expected = shot.get('expect_mode')
    if expected and any(row['mode'] != expected for row in window):
        modes = sorted({row['mode'] for row in window})
        raise ReelError(f"{shot['id']}: window modes {modes}, expected only {expected}")
    return {'first_x': int(window[0]['x']), 'last_x': int(window[-1]['x']),
            'modes': sorted({row['mode'] for row in window}),
            'rings': [int(window[0]['rings']), int(window[-1]['rings'])]}


def count_frames(video):
    out = subprocess.run(['ffprobe', '-v', 'error', '-count_frames', '-select_streams', 'v:0',
                          '-show_entries', 'stream=nb_read_frames', '-of', 'csv=p=0', str(video)],
                         check=True, capture_output=True, text=True).stdout.strip()
    return int(out or 0)


def black_and_freeze(video, fps, max_black, max_freeze):
    """Return (black runs, frozen runs) longer than the shot allows, in seconds."""
    result = subprocess.run(['ffmpeg', '-nostdin', '-hide_banner', '-nostats', '-i', str(video), '-vf',
                             f'blackdetect=d={max_black}:pix_th=0.06,freezedetect=n=0.0005:d={max_freeze}',
                             '-an', '-f', 'null', '-'], capture_output=True, text=True)
    log = result.stderr
    blacks = [line for line in log.splitlines() if 'black_start' in line]
    freezes = [line for line in log.splitlines() if 'freeze_duration' in line]
    return blacks, freezes


def render_shot(game, shot, rom, classpath, runtime, shot_dir, fps, root=ROOT):
    """Render one shot to a lossless 320-wide intermediate of exactly shot['frames'] frames."""
    if shot_dir.exists():
        shutil.rmtree(shot_dir)
    shot_dir.mkdir(parents=True)
    raw_dir = shot_dir / 'raw'
    log = shot_dir / 'capture.log'
    clip = shot_dir / 'clip.mkv'
    detail = {}
    if shot['source'] == 'trace':
        args = trace_args(game, shot, raw_dir, root)
        run_jvm(java_command(classpath, runtime, 'com.openggf.tools.TraceCaptureTool', args), runtime, log)
        movies = sorted(raw_dir.glob('*.mov')) + sorted(raw_dir.glob('*.mkv'))
        if len(movies) != 1:
            raise ReelError(f"{shot['id']}: expected one capture movie, found {len(movies)}")
        source = movies[0]
        produced = count_frames(source)
        input_args = ['-i', str(source)]
    else:
        args = capture_args(game, shot, rom, raw_dir, root)
        run_jvm(java_command(classpath, runtime, 'com.openggf.tools.GameplayCaptureTool', args), runtime, log)
        detail = check_capture_window(raw_dir / 'state.csv', shot)
        pngs = sorted((raw_dir / 'frames').glob('*.png'))
        produced = len(pngs)
        input_args = ['-framerate', str(fps), '-start_number', str(shot['start']),
                      '-i', str(raw_dir / 'frames' / '%05d.png')]
    if produced < shot['frames']:
        raise ReelError(f"{shot['id']}: produced {produced} frames, shot needs {shot['frames']}")
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', *input_args, '-an', '-frames:v', str(shot['frames']),
                    '-vf', 'format=rgb24', '-c:v', 'ffv1', str(clip)], check=True)
    written = count_frames(clip)
    if written != shot['frames']:
        raise ReelError(f"{shot['id']}: intermediate has {written} frames, expected {shot['frames']}")
    blacks, freezes = black_and_freeze(clip, fps, shot.get('max_black_seconds', 0.25),
                                       shot.get('max_freeze_seconds', 2.0))
    if blacks:
        raise ReelError(f"{shot['id']}: black run inside shot: {blacks[0]}")
    if freezes:
        raise ReelError(f"{shot['id']}: frozen picture inside shot: {freezes[0]}")
    shutil.rmtree(raw_dir / 'frames', ignore_errors=True)
    for movie in list(raw_dir.glob('*.mov')) + list(raw_dir.glob('*.mkv')):
        movie.unlink()
    def portable(argument):
        text = str(argument).replace(str(rom), '<rom>').replace(str(shot_dir), '<shot>')
        return text.replace(f'{root}{os.sep}', '')
    return {'tool': 'TraceCaptureTool' if shot['source'] == 'trace' else 'GameplayCaptureTool',
            'arguments': [portable(a) for a in args],
            'produced_frames': produced, 'clip': clip, **detail}


# --------------------------------------------------------------------------- assembly

def filter_graph(manifest):
    """Concat every shot (per-shot dips), scale nearest-neighbour, fade the loop ends."""
    video = manifest['video']
    total = sum(shot['frames'] for shot in manifest['shots'])
    parts = []
    for index, shot in enumerate(manifest['shots']):
        chain = [f"trim=end_frame={shot['frames']}", 'setpts=PTS-STARTPTS']
        if shot.get('dip_in'):
            chain.append(f"fade=t=in:s=0:n={shot['dip_in']}")
        if shot.get('dip_out'):
            chain.append(f"fade=t=out:s={shot['frames'] - shot['dip_out']}:n={shot['dip_out']}")
        parts.append(f"[{index}:v]{','.join(chain)}[s{index}]")
    labels = ''.join(f'[s{i}]' for i in range(len(manifest['shots'])))
    tail = [f"concat=n={len(manifest['shots'])}:v=1:a=0",
            f"scale=iw*{video['scale']}:ih*{video['scale']}:flags=neighbor"]
    if video.get('fade_in_frames'):
        tail.append(f"fade=t=in:s=0:n={video['fade_in_frames']}")
    if video.get('fade_out_frames'):
        tail.append(f"fade=t=out:s={total - video['fade_out_frames']}:n={video['fade_out_frames']}")
    tail.append('format=yuv420p')
    parts.append(f"{labels}{','.join(tail)}[out]")
    return ';'.join(parts)


def encode_command(manifest, clips, output):
    video = manifest['video']
    inputs = []
    for clip in clips:
        inputs += ['-i', str(clip)]
    return ['ffmpeg', '-nostdin', '-v', 'error', '-y', *inputs, '-filter_complex', filter_graph(manifest),
            '-map', '[out]', '-an', '-r', str(video['fps']), '-c:v', 'libx264',
            '-preset', video.get('preset', 'slow'), '-crf', str(video['crf']),
            '-profile:v', 'high', '-level:v', '4.0', '-g', str(video['fps'] * 2),
            '-color_range', 'tv', '-colorspace', 'bt709', '-color_primaries', 'bt709',
            '-color_trc', 'bt709', '-movflags', '+faststart', str(output)]


def is_faststart(path):
    """True when the moov atom precedes mdat."""
    with open(path, 'rb') as stream:
        offset = 0
        while True:
            header = stream.read(8)
            if len(header) < 8:
                return False
            size = int.from_bytes(header[:4], 'big')
            kind = header[4:8]
            if kind == b'moov':
                return True
            if kind == b'mdat':
                return False
            if size == 1:
                size = int.from_bytes(stream.read(8), 'big')
                stream.seek(offset + size)
            elif size < 8:
                return False
            else:
                stream.seek(offset + size)
            offset += size


def verify_reel(path, manifest):
    video = manifest['video']
    expected = sum(shot['frames'] for shot in manifest['shots'])
    probe = json.loads(subprocess.run(
        ['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-count_frames', '-show_entries',
         'stream=codec_name,width,height,r_frame_rate,nb_read_frames,pix_fmt:format=duration',
         '-of', 'json', str(path)], check=True, capture_output=True, text=True).stdout)
    stream = probe['streams'][0]
    width, height = video.get('width', 320) * video['scale'], video.get('height', 224) * video['scale']
    problems = []
    if stream['codec_name'] != 'h264' or stream['pix_fmt'] != 'yuv420p':
        problems.append(f"codec {stream['codec_name']}/{stream['pix_fmt']}")
    if (stream['width'], stream['height']) != (width, height):
        problems.append(f"size {stream['width']}x{stream['height']} != {width}x{height}")
    if stream['r_frame_rate'] != f"{video['fps']}/1":
        problems.append(f"rate {stream['r_frame_rate']}")
    if int(stream['nb_read_frames']) != expected:
        problems.append(f"frames {stream['nb_read_frames']} != {expected}")
    size = Path(path).stat().st_size
    if size > video['max_bytes']:
        problems.append(f"{size} bytes exceeds budget {video['max_bytes']}")
    if not is_faststart(path):
        problems.append('moov atom is not before mdat (not faststart)')
    if problems:
        raise ReelError(f'{path.name}: ' + '; '.join(problems))
    return {'frames': expected, 'duration_seconds': round(expected / video['fps'], 3),
            'bytes': size, 'width': width, 'height': height}


def write_poster(manifest, clips_by_id, out_stem):
    poster = manifest['poster']
    scale = manifest['video']['scale']
    clip = clips_by_id[poster['shot']]
    png = out_stem.with_suffix('.poster.png')
    webp = out_stem.with_suffix('.poster.webp')
    vf = f"select=eq(n\\,{poster['frame']}),scale=iw*{scale}:ih*{scale}:flags=neighbor"
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-i', str(clip), '-vf', vf, '-frames:v', '1',
                    str(png)], check=True)
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-i', str(png), '-c:v', 'libwebp',
                    '-quality', '90', str(webp)], check=True)
    for path in (png, webp):
        if not path.is_file() or path.stat().st_size == 0:
            raise ReelError(f'poster {path} was not written')
    return [png, webp]


def contact_sheet(video, out_png):
    """The review format: one frame every 4 s at native size, 6x4 tiles."""
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-i', str(video), '-vf',
                    'fps=1/4,scale=320:224,tile=6x4', '-frames:v', '1', str(out_png)], check=True)
    return out_png


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')


def build_game(game, work, roms, classpath, root=ROOT, manifest_dir=MANIFEST_DIR):
    manifest = load_manifest(game, manifest_dir)
    rom = require_rom(roms, game)
    runtime = prepare_runtime(work, roms, root)
    fps = manifest['video']['fps']
    started = utc_now()
    shots, clips = [], {}
    layout = {sid: (a, b) for sid, a, b in reel_layout(manifest)}
    for shot in manifest['shots']:
        print(f"[{game}] {shot['id']}: {shot['label']}", flush=True)
        result = render_shot(game, shot, rom, classpath, runtime, Path(work) / game / shot['id'], fps, root)
        clips[shot['id']] = result.pop('clip')
        record = {'id': shot['id'], 'label': shot['label'], 'source': shot['source'],
                  'reel_frames': list(layout[shot['id']]),
                  'source_frames': [shot['start'], shot['start'] + shot['frames']], **result}
        if shot['source'] == 'trace':
            trace_dir = root / TRACE_ROOT / game / shot['trace']
            meta = json.loads((trace_dir / 'metadata.json').read_text())
            bk2 = trace_bk2(trace_dir, meta, root)
            record.update({'trace': str(trace_dir.relative_to(root)), 'zone': meta.get('zone'),
                           'act': meta.get('act'), 'team': trace_team(meta),
                           'input': str(bk2.relative_to(root)) if bk2 else None,
                           'input_sha256': sha256_file(bk2) if bk2 else None,
                           'bk2_frame_offset': meta.get('bk2_frame_offset'),
                           'entry': 'trace replay bootstrap (declared start + carried segment state)'})
        else:
            record.update({'zone': shot['zone'], 'act': shot['act'],
                           'team': '+'.join(x for x in (shot.get('main', 'sonic'), shot.get('sidekick'))
                                            if x and x != 'none'),
                           'input': shot['input'], 'input_sha256': sha256_file(root / shot['input']),
                           'input_start': shot.get('input_start', 0), 'setup': shot.get('setup', {}),
                           'entry': 'direct level boot (GameplayCaptureSession)'})
        if shot.get('note'):
            record['note'] = shot['note']
        shots.append(record)
    staged = Path(work) / game / 'reel'
    staged.mkdir(parents=True, exist_ok=True)
    reel = staged / f"{manifest['output']}.mp4"
    subprocess.run(encode_command(manifest, [clips[s['id']] for s in manifest['shots']], reel), check=True)
    summary = verify_reel(reel, manifest)
    posters = write_poster(manifest, clips, staged / manifest['output'])
    sheet = contact_sheet(reel, staged / f"{manifest['output']}.sheet.png")
    dirty = git('status', '--porcelain', '--untracked-files=no', root=root)
    provenance = {
        'schema': 1, 'game': game, 'output': reel.name, 'started_utc': started, 'finished_utc': utc_now(),
        'engine': {'commit': git('rev-parse', 'HEAD', root=root), 'dirty': bool(dirty),
                   'branch': git('rev-parse', '--abbrev-ref', 'HEAD', root=root)},
        'manifest': {'path': str(Path(manifest['_path']).relative_to(root)), 'sha256': manifest['_sha256']},
        'rom_sha1': sha1_file(rom), 'video': {**manifest['video'], **summary,
                                              'sha256': sha256_file(reel)},
        'poster': {'shot': manifest['poster']['shot'], 'frame': manifest['poster']['frame'],
                   'files': [p.name for p in posters]},
        'capture': {'backend': 'surfaceless-egl', 'audio': 'discarded', 'jvms': 'sequential, one per shot'},
        'shots': shots,
    }
    (staged / f"{manifest['output']}.provenance.json").write_text(json.dumps(provenance, indent=2) + '\n')
    print(f"[{game}] {reel.name}: {summary['duration_seconds']} s, {summary['bytes']} bytes (staged)")
    return [reel, *posters, sheet, staged / f"{manifest['output']}.provenance.json"]


def publish(files, out):
    """Copy staged outputs only once every requested game has passed its checks."""
    out.mkdir(parents=True, exist_ok=True)
    for path in files:
        shutil.copy2(path, out / path.name)
        print(f'wrote {out / path.name}')


def trace_bk2(trace_dir, meta, root=ROOT):
    """The committed movie a trace replays: beside it, in its run, or in the game's _movies."""
    name = meta.get('source_bk2')
    candidates = [trace_dir / name, trace_dir.parent / name, trace_dir.parent / '_movies' / name] if name else []
    candidates += sorted(trace_dir.glob('*.bk2'))
    return next((path for path in candidates if path.is_file()), None)


# --------------------------------------------------------------------------- CLI

def selected_games(value):
    return list(GAMES) if value == 'all' else [value]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    sub = parser.add_subparsers(dest='command', required=True)
    validate = sub.add_parser('validate', help='check manifests and referenced inputs (no ROM, no JVM)')
    validate.add_argument('--game', choices=(*GAMES, 'all'), default='all')
    build = sub.add_parser('build', help='render, encode and verify reels')
    build.add_argument('--game', choices=(*GAMES, 'all'), default='all')
    build.add_argument('--out', type=Path, required=True, help='directory for the published outputs')
    build.add_argument('--work', type=Path, help='intermediate directory (default: a fresh temp dir)')
    build.add_argument('--skip-compile', action='store_true', help='reuse target/classes and the classpath file')
    build.add_argument('--keep-work', action='store_true')
    sheet = sub.add_parser('sheet', help='write the 4-second contact sheet for a video')
    sheet.add_argument('video', type=Path)
    sheet.add_argument('png', type=Path)
    args = parser.parse_args(argv)
    try:
        if args.command == 'validate':
            for game in selected_games(args.game):
                manifest = load_manifest(game)
                total = sum(s['frames'] for s in manifest['shots'])
                print(f"{game}: {len(manifest['shots'])} shots, {total} frames "
                      f"({total / manifest['video']['fps']:.2f} s) ok")
            return 0
        if args.command == 'sheet':
            contact_sheet(args.video, args.png)
            return 0
        games = selected_games(args.game)
        for game in games:
            load_manifest(game)  # every manifest must validate before any JVM starts
        roms = discover_roms()
        for game in games:
            require_rom(roms, game)
        for tool in ('ffmpeg', 'ffprobe', 'java'):
            if shutil.which(tool) is None:
                raise ReelError(f'{tool} not on PATH')
        if not args.skip_compile:
            compile_classpath()
        classpath = java_classpath()
        work = args.work or Path(tempfile.mkdtemp(prefix='hero-reels-'))
        work.mkdir(parents=True, exist_ok=True)
        staged = []
        for game in games:
            staged += build_game(game, work, roms, classpath)
        publish(staged, args.out)
        if not args.keep_work and args.work is None:
            shutil.rmtree(work, ignore_errors=True)
        return 0
    except ReelError as error:
        print(f'hero_reels: FAILED: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
