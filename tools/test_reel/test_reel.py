#!/usr/bin/env python3
"""Inventory, record and stitch actual JUnit fixtures. Origin: TAS test reel, 2026-10-26.
Inputs: explicit task commits, source ref, original ROM paths, opt-in frame adapters.
No gameplay replay, native-row import, or substitute footage. Requires Python 3.10+, Java 25,
Maven queue, FFmpeg/ffprobe; capture also needs Mesa surfaceless EGL.
"""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SCOPE = Path(__file__).with_name('tas-2026-10-26.json')


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def blob(ref, path):
    result = subprocess.run(['git', 'show', f'{ref}:{path}'], cwd=ROOT, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return result.stdout if result.returncode == 0 else ''


def closing(text, start, opening='(', end=')'):
    """Find a balanced Java delimiter, respecting comments and quoted literals."""
    depth, i = 1, start + 1
    while i < len(text):
        if text.startswith('//', i):
            i = text.find('\n', i)
            if i < 0:
                break
        elif text.startswith('/*', i):
            i = text.index('*/', i + 2) + 2
        elif text[i] in "\"'":
            quote = text[i]
            i += 1
            while i < len(text):
                if text[i] == '\\':
                    i += 2
                elif text[i] == quote:
                    i += 1
                    break
                else:
                    i += 1
        else:
            if text[i] == opening:
                depth += 1
            elif text[i] == end:
                depth -= 1
                if depth == 0:
                    return i
            i += 1
    raise ValueError('Unbalanced Java source')


def java_strings(text):
    return [json.loads(value) for value in re.findall(r'"(?:[^"\\]|\\.)*"', text)]


def methods(source):
    result = {}
    # Fail closed for providers requiring real JUnit discovery; never silently omit them.
    unsupported = re.findall(r'@(MethodSource|ArgumentsSource|CsvFileSource|EnumSource|TestFactory|RepeatedTest)\b', source)
    if unsupported:
        raise ValueError(f'Inventory provider needs an adapter: {sorted(set(unsupported))}')
    previous_finish = 0
    for marker in re.finditer(r'@(Test|ParameterizedTest)\b', source):
        header = re.search(r'\bvoid\s+(\w+)\s*\(', source[marker.end():])
        if header is None:
            raise ValueError('Cannot locate test method')
        name = header[1]
        paren = marker.end() + header.end() - 1
        args_end = closing(source, paren)
        body = source.index('{', args_end)
        finish = closing(source, body, '{', '}')
        annotations = source[previous_finish:paren]
        source_start = marker.start()
        values = [[]]
        if marker[1] == 'ParameterizedTest':
            providers = list(re.finditer(r'@(?:[\w]+\.)*(CsvSource|ValueSource)\s*\(', annotations))
            provider = providers[-1] if providers else None
            if provider is None:
                raise ValueError(f'{name}: unsupported parameter provider')
            source_start = min(source_start, previous_finish + provider.start())
            start = provider.end() - 1
            payload = annotations[start + 1:closing(annotations, start)]
            if provider[1] == 'CsvSource':
                if re.search(r'\b(delimiter|textBlock|quoteCharacter|nullValues|emptyValue)\s*=', payload):
                    raise ValueError(f'{name}: custom CSV settings need an inventory adapter')
                values = [[x.strip() for x in next(csv.reader([row], quotechar="'", skipinitialspace=True))]
                          for row in java_strings(payload)]
            else:
                match = re.search(r'(booleans|ints|shorts|longs|bytes|strings)\s*=\s*\{([^}]+)\}', payload, re.S)
                if match is None:
                    raise ValueError(f'{name}: unsupported ValueSource shape')
                raw = java_strings(match[2]) if match[1] == 'strings' else [v.strip() for v in match[2].split(',')]
                values = [[v] for v in raw]
            if not values:
                raise ValueError(f'{name}: empty parameter provider')
        source_arguments = [list(row) for row in values]
        types = [part.strip().removeprefix('final ').split()[0] for part in source[paren+1:args_end].split(',') if part.strip()]
        for row in values:
            if len(row) != len(types):
                raise ValueError(f'{name}: parameter arity mismatch')
            for i, kind in enumerate(types):
                if kind in ('int','short','byte','long','Integer','Short','Byte','Long'):
                    value = row[i].replace('_','').rstrip('Ll')
                    row[i] = str(int(value, 0) if value.lstrip('-+').startswith(('0x','0X','0b','0B')) else int(value,10))
                elif kind in ('boolean','Boolean'):
                    row[i] = row[i].lower()
        if name in result:
            raise ValueError(f'Overloaded test method needs explicit identity: {name}')
        result[name] = {'arguments': values, 'source_arguments':source_arguments, 'source': source[source_start:finish+1],
                        'parameterized': marker[1] == 'ParameterizedTest'}
        previous_finish = finish + 1
    return result


def presentation_disposition(scope, class_name, method, ordinal):
    """A reviewed invocation/method overrides a class-wide rendering disposition."""
    method_id = f'{class_name}#{method}'
    keys = ([f'{method_id}[{ordinal}]'] if ordinal is not None else []) + [method_id, class_name]
    for key in keys:
        if key in scope.get('non_drawable', {}):
            return None, scope['non_drawable'][key]
        if key in scope.get('adapters', {}):
            return scope['adapters'][key], 'Adapter available'
    return None, 'Not yet reviewed for drawable state'


def inventory(scope, source_ref, extra=(), include_unmerged=False):
    source_ref = git('rev-parse', source_ref)
    touched = {}
    overlays = {}
    for commit in [*scope['commits'], *extra]:
        commit = git('rev-parse', commit)
        ancestor = subprocess.run(['git', 'merge-base', '--is-ancestor', commit, source_ref], cwd=ROOT).returncode == 0
        if not ancestor and not (include_unmerged and commit in [git('rev-parse', c) for c in extra]):
            raise ValueError(f'{commit} is outside source ancestry; --include-unmerged is inventory-only planning')
        paths = git('diff-tree', '--no-commit-id', '--name-only', '-r', commit, '--', 'src/test/java').splitlines()
        for path in paths:
            if not path.endswith('.java'):
                continue
            if not ancestor:
                overlays[path] = commit
            before, after = methods(blob(commit+'^', path)), methods(blob(commit, path))
            for name, method in after.items():
                if name not in before or before[name]['source'] != method['source']:
                    touched.setdefault(path, {}).setdefault(name, []).append(commit)
    cases = []
    for path, names in sorted(touched.items()):
        file_ref = overlays.get(path, source_ref)
        file_source = blob(file_ref,path)
        final, original = methods(file_source), methods(blob(scope['audit_base'], path))
        for name, commits in sorted(names.items()):
            if name not in final:
                raise ValueError(f'Removed test needs explicit disposition: {path}#{name}')
            method = final[name]
            class_name = path.removeprefix('src/test/java/').removesuffix('.java').replace('/', '.')
            for ordinal, arguments in enumerate(method['arguments'], 1):
                adapter, disposition = presentation_disposition(
                    scope, class_name, name, ordinal if method['parameterized'] else None)
                previous = original.get(name)
                kind = ('new-test' if previous is None else 'new-parameter' if arguments not in previous['arguments']
                        else 'extended-test')
                game = ('s1' if 'S1' in class_name or 'sonic1' in class_name else
                        'kis2' if 'Kis2' in class_name or '.kis2.' in class_name else
                        's2' if 'S2' in class_name or 'sonic2' in class_name else
                        's3k' if 'S3k' in class_name or 'sonic3k' in class_name else 'shared')
                cases.append({'id': f'{class_name}#{name}' + (f'[{ordinal}]' if method['parameterized'] else ''),
                              'class': class_name, 'method': name, 'arguments': arguments,
                              'source_arguments': method['source_arguments'][ordinal-1],
                              'invocation_index': ordinal if method['parameterized'] else None,
                              'kind': kind, 'game': game, 'source_file': path, 'source_ref': file_ref,
                              'fixture_sha256':hashlib.sha256(file_source.encode()).hexdigest(),
                              'unmerged_source':path in overlays, 'task_commits': commits,
                              'adapter': adapter,
                              'drawable_disposition': disposition,
                              'inventory_kind': 'static-source-expansion', 'junit_unique_id': None,
                              'capture_status': 'not-run',
                              'reason': 'Adapter available; not executed' if adapter else
                              'No adapter yet: inspect actual fixture drawable state; no substitute footage'})
    order = {'s1':0,'s2':1,'kis2':2,'s3k':3,'shared':4}
    cases.sort(key=lambda c:(order[c['game']],c['class'],c['method'],c['invocation_index'] or 0))
    return {'schema': 1, 'name': scope['name'], 'source_commit': source_ref, 'audit_base': scope['audit_base'],
            'supplemental_sources': overlays,
            'task_commits': [*scope['commits'], *extra], 'pending': scope.get('pending', []), 'cases': cases}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def rom_identities(args):
    result = {}
    for game in ('s1','s2','s3k','kis2'):
        path = getattr(args, game+'_rom')
        if path:
            path = Path(path).resolve(strict=True)
            import zlib
            data = path.read_bytes()
            result[game] = {'path': str(path), 'sha1': hashlib.sha1(data).hexdigest(),
                            'crc32': f'{zlib.crc32(data):08x}'}
    return result


def validate_selected_cases(cases, sources):
    """Reject a dirty provider/signature change before assigning old inventory IDs."""
    groups = {}
    for case in cases:
        groups.setdefault((case['source_file'], case['method']), []).append(case)
    for (source_file, name), expected in groups.items():
        method = methods(sources[source_file]).get(name)
        if method is None:
            raise ValueError(f'Selected method missing from working source: {source_file}#{name}')
        actual = [(i if method['parameterized'] else None, arguments)
                  for i, arguments in enumerate(method['arguments'], 1)]
        declared = [(c['invocation_index'], c['arguments']) for c in expected]
        if actual != declared:
            raise ValueError(f'Arguments/ordinals changed in working source: {source_file}#{name}; regenerate inventory')


def recorded_fixture_hash(metadata, case, allow_legacy_source=False):
    """The recorded source snapshot owns execution identity; inventory never supplies it."""
    hashes = metadata.get('source_file_hashes')
    if ('source_file_hashes' not in metadata and 'source_fingerprint' not in metadata
            and allow_legacy_source):
        return None
    source_file = case.get('source_file')
    actual = hashes.get(source_file) if isinstance(hashes, dict) else None
    if not isinstance(actual, str) or not re.fullmatch(r'[0-9a-f]{64}', actual):
        raise ValueError(f'Actual captured fixture hash unavailable: {case["id"]}')
    if source_file in metadata.get('source_files_changed', []):
        raise ValueError(f'Fixture source changed while queued/running: {case["id"]}')
    return actual


def validate_invocation(case):
    actual = re.findall(r'\[test-template-invocation:#(\d+)\]', case['junit_unique_id'])
    expected = [] if case['invocation_index'] is None else [str(case['invocation_index'])]
    if actual != expected:
        raise ValueError(f'JUnit invocation ordinal mismatch: {case["id"]}')


def capture(args):
    inventory_path = Path(args.inventory).resolve()
    plan = json.loads(inventory_path.read_text())
    if git('rev-parse','HEAD') != plan['source_commit']:
        raise ValueError('Inventory source differs from HEAD; regenerate inventory')
    chosen = [c for c in plan['cases'] if any(re.search(s,c['id']) for s in args.select)]
    if not chosen:
        raise ValueError('Selection matches no inventoried cases')
    # Surefire cannot select one template invocation: include every case of each selected method.
    selectors = sorted({f"{c['class']}#{c['method']}" for c in chosen})
    chosen = [c for c in plan['cases'] if f"{c['class']}#{c['method']}" in selectors]
    for case in chosen:
        if case.get('unmerged_source') and blob('HEAD',case['source_file']) != blob(case['source_ref'],case['source_file']):
            raise ValueError(f"Unmerged inventory-only case cannot run in this tree: {case['id']}")
    fixture_sources = {c['source_file']:(ROOT/c['source_file']).read_text() for c in chosen}
    validate_selected_cases(chosen, fixture_sources)
    out = Path(args.out).resolve()
    out.mkdir(parents=True,exist_ok=False)
    roms = rom_identities(args)
    properties = {'s1':'sonic1.rom.path','s2':'sonic2.rom.path','s3k':'s3k.rom.path','kis2':'kis2.rom.path'}
    source_files = sorted(set(git('ls-files','--cached','--others','--exclude-standard','src','pom.xml').splitlines())
                          | {str(p.relative_to(ROOT)) for p in Path(__file__).parent.glob('*') if p.is_file()})
    source_hashes = {p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in source_files if (ROOT/p).is_file()}
    fingerprint = hashlib.sha256(json.dumps(source_hashes,sort_keys=True).encode()).hexdigest()
    command = [sys.executable, 'tools/testing/maven_queue.py', '-Dmse=off', '-Dopenggf.headless.gl=surfaceless-egl',
               '-Dtest='+','.join(selectors), '-Dopenggf.testReel.dir='+str(out),
               '-Dopenggf.testReel.source='+plan['source_commit'], '-Dopenggf.testReel.fingerprint='+fingerprint,
               *['-D'+properties[g]+'='+r['path'] for g,r in roms.items()], 'test']
    env = dict(os.environ)
    env.pop('DISPLAY',None); env.pop('WAYLAND_DISPLAY',None)
    metadata = {'command':command,'roms':roms, 'source_diff':git('diff','HEAD','--','src','tools/test_reel'),
                'source_fingerprint':fingerprint, 'source_file_hashes':source_hashes,
                'selected_ids':[c['id'] for c in chosen], 'source_commit':plan['source_commit']}
    write_json(out/'run.json', metadata)
    result = subprocess.run(command,cwd=ROOT,env=env)
    # The invocation extension writes even on assertion failure. Setup skips have no invocation;
    # retain them explicitly, rather than claiming they ran or fabricating a clip.
    metadata['maven_exit_code'] = result.returncode
    metadata['source_file_hashes_after'] = {
        path:hashlib.sha256((ROOT/path).read_bytes()).hexdigest() if (ROOT/path).is_file() else None
        for path in fixture_sources}
    metadata['source_files_changed'] = [path for path, value in metadata['source_file_hashes_after'].items()
                                        if value != source_hashes.get(path)]
    write_json(out/'run.json', metadata)
    reconcile(plan,out)
    write_json(out/'manifest.json',plan)
    return result.returncode


def reconcile(plan, out, allow_legacy_source=False):
    run_metadata = json.loads((out/'run.json').read_text())
    selected = set(run_metadata['selected_ids'])
    plan['run'] = run_metadata
    rows = [json.loads(p.read_text()) | {'case_dir':str(p.parent.relative_to(out))}
            for p in sorted((out/'cases').glob('*/case.json'))] if (out/'cases').exists() else []
    for case in plan['cases']:
        if case['id'] not in selected:
            continue
        matches = [r for r in rows if r['class']==case['class'] and r['method']==case['method']
                   and r['arguments']==case['arguments']
                   and (case['invocation_index'] is None or
                        f"[test-template-invocation:#{case['invocation_index']}]" in r['junit_unique_id'])]
        if len(matches)>1:
            raise ValueError(f"Duplicate captured JUnit identity: {case['id']}")
        if matches:
            captured_hash = recorded_fixture_hash(run_metadata, case, allow_legacy_source)
            if captured_hash is None and run_metadata.get('source_commit') != plan.get('source_commit'):
                raise ValueError('Legacy recording must have the same explicit source base')
            case.update(matches[0]); case['reason']=matches[0]['capture_reason']
            validate_invocation(case)
            case['roms'] = run_metadata.get('roms', {})
            case['inventory_fixture_sha256'] = case.get('fixture_sha256')
            case['capture_fixture_sha256'] = captured_hash
            case['capture_hash_origin'] = 'run.json:source_file_hashes' if captured_hash else 'unavailable'
            case['matches_inventory_source'] = captured_hash == case.get('fixture_sha256') if captured_hash else None
            if captured_hash is None:
                case['provenance_limit'] = 'Legacy preview: source base plus dirty diff; actual source-file hash unavailable'
            if not case['frames'] and case.get('drawable_disposition') != 'Not yet reviewed for drawable state':
                case['reason'] = case.get('drawable_disposition',case['reason'])
        else:
            case.update(capture_status='not-executed', outcome='unknown',
                        reason='No invocation record: setup failure/disabled/aborted run; inspect Maven result')
    unexpected = [r for r in rows if not any(c.get('junit_unique_id')==r['junit_unique_id'] for c in plan['cases'])]
    if unexpected:
        raise ValueError(f'Unmapped JUnit invocations: {[r["junit_unique_id"] for r in unexpected]}')


def run(command):
    subprocess.run(command,check=True)


def reconcile_command(args):
    """Derive corrected metadata from retained records without touching media/evidence."""
    plan = json.loads(Path(args.inventory).read_text())
    source = Path(args.run_dir).resolve()
    destination = Path(args.out).resolve()
    if destination.exists():
        raise ValueError('Refusing to overwrite reconciled evidence')
    reconcile(plan, source, args.allow_legacy_source)
    for case in plan['cases']:
        if case.get('case_dir'):
            case['case_dir'] = os.path.relpath(source/case['case_dir'], destination)
            case['recording_run'] = str(source/'run.json')
    plan['reconciled_from'] = str(source)
    destination.mkdir(parents=True)
    write_json(destination/'manifest.json', plan)
    print(destination/'manifest.json')


def combine(args):
    plan = json.loads(Path(args.inventory).read_text())
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=False)
    inputs = []
    recordings = [(Path(item).resolve(), json.loads(Path(item).read_text())) for item in args.manifest]
    latest = {}
    if args.prefer_last:
        for ordinal, (_, recording) in enumerate(recordings):
            for case in recording['cases']:
                if case.get('case_dir'):
                    latest[case['id']] = ordinal
    superseded = []
    for ordinal, (path, recording) in enumerate(recordings):
        inputs.append(str(path))
        for case in recording['cases']:
            if not case.get('case_dir'):
                continue
            if args.prefer_last and latest[case['id']] != ordinal:
                superseded.append({'id':case['id'], 'manifest':str(path)})
                continue
            target = next((c for c in plan['cases'] if c['id']==case['id']),None)
            if target is None:
                raise ValueError(f"Recording absent from inventory: {case['id']}")
            for field in ('class', 'method', 'arguments', 'invocation_index', 'source_file'):
                if field not in case or field not in target or case[field] != target[field]:
                    raise ValueError(f"Recorded invocation identity mismatch ({field}): {case['id']}")
            validate_invocation(case)
            run_path = Path(case.get('recording_run', path.parent/'run.json'))
            if not run_path.is_absolute():
                run_path = path.parent/run_path
            metadata = json.loads(run_path.read_text())
            actual_hash = recorded_fixture_hash(metadata, case, args.allow_legacy_source)
            if actual_hash is None:
                if (recording['source_commit'] != plan['source_commit']
                        or metadata.get('source_commit') != plan['source_commit']):
                    raise ValueError('Legacy recording must have the same explicit source base')
                case['provenance_limit'] = 'Legacy preview: source base plus dirty diff; actual source-file hash unavailable'
            elif target.get('fixture_sha256') != actual_hash:
                raise ValueError(f"Fixture changed or old capture lacks fingerprint; recapture explicitly: {case['id']}")
            if target.get('case_dir') and not args.prefer_last:
                raise ValueError(f"Duplicate recording: {case['id']}; supply only the selected revision")
            inventory_fields = {key:target[key] for key in ('fixture_sha256', 'source_ref', 'unmerged_source',
                                'task_commits', 'kind', 'source_arguments', 'adapter', 'drawable_disposition') if key in target}
            target.update(case)
            target.update(inventory_fields)
            target['inventory_fixture_sha256'] = target.get('fixture_sha256')
            target['capture_fixture_sha256'] = actual_hash
            target['capture_hash_origin'] = 'run.json:source_file_hashes' if actual_hash else 'unavailable'
            target['matches_inventory_source'] = True if actual_hash else None
            target['case_dir'] = os.path.relpath(path.parent/case['case_dir'],out)
            target['recording_run'] = str(run_path.resolve())
            if not target.get('roms'):
                target['roms'] = metadata.get('roms',{})
    plan['combined_from'] = inputs
    plan['superseded_recordings'] = superseded
    write_json(out/'manifest.json',plan)
    print(out/'manifest.json')


def reel_cases(plan, include_nondrawable_cards=False, require_complete=False):
    """Keep real frame counts distinct from explicitly requested editorial cards."""
    selected = []
    for case in plan['cases']:
        status = case.get('capture_status')
        if status not in ('captured', 'no-adapter'):
            if require_complete:
                raise ValueError(f'Incomplete reel: no executed capture for {case["id"]}')
            continue
        if require_complete:
            if not case.get('junit_unique_id') or case.get('outcome') != 'passed':
                raise ValueError(f'Reel requires a passed actual invocation: {case["id"]}')
            if not case.get('capture_fixture_sha256') or case.get('matches_inventory_source') is not True:
                raise ValueError(f'Reel source identity is not qualified: {case["id"]}')
        if status == 'captured':
            if case.get('frames', 0) <= 0:
                raise ValueError(f'Captured case has no frames: {case["id"]}')
            selected.append((case, 'engine-frames', case['frames']))
        elif include_nondrawable_cards:
            disposition = case.get('drawable_disposition')
            if (not disposition or disposition in ('Adapter available', 'Not yet reviewed for drawable state')
                    or not case.get('junit_unique_id') or case.get('outcome') not in ('passed', 'failed', 'aborted')
                    or case.get('frames') != 0):
                raise ValueError(f'Card requires an executed, reviewed non-drawable case: {case["id"]}')
            selected.append((case, 'non-drawable-card', 1))
        elif require_complete:
            raise ValueError(f'Incomplete reel: non-drawable card disabled for {case["id"]}')
    return selected


def assembly_filter(slow):
    # Matroska fragments use millisecond timestamps. Ignoring their rounded
    # durations prevents fps from substituting frames across short clip cuts.
    return f'settb=expr=1/60,setpts=N*{slow},fps=60,tpad=stop_mode=clone:stop={slow}'


def stitch(args):
    manifest_path = Path(args.manifest).resolve()
    root = manifest_path.parent
    plan = json.loads(manifest_path.read_text())
    video = Path(args.out).resolve()
    if video.exists():
        raise ValueError('Refusing to overwrite reel')
    video.parent.mkdir(parents=True,exist_ok=True)
    selected = reel_cases(plan, args.include_nondrawable_cards, args.require_complete)
    segments, chapters, edit = [], [], []
    segment_root = video.with_suffix('.segments')
    segment_root.mkdir(exist_ok=False)
    frame = 0
    for ordinal, (case, kind, source_frames) in enumerate(selected):
        directory = segment_root/f'{ordinal:04d}'
        directory.mkdir()
        source = root/case['case_dir']
        if kind == 'engine-frames':
            pngs = sorted((source/'frames').glob('*.png'))
            if len(pngs) != case['frames'] or not pngs:
                raise ValueError(f"Missing frames: {case['id']}")
            for i,png in enumerate(pngs):
                if png.name != f'{i:06d}.png':
                    raise ValueError('Frame sequence is not contiguous')
        label = directory/'label.txt'
        text = (f"{case['game'].upper()} {case['class'].split('.')[-1]}\n{case['method']}"
                + (f" [{case['invocation_index']}] {', '.join(case['arguments'])}" if case['arguments'] else ''))
        label.write_text(text)
        segment = directory/'segment.mkv'
        # textfile prevents filter/shell interpolation of test names and parameters.
        # Relative fixed label name also avoids FFmpeg path escaping traps.
        if kind == 'engine-frames':
            input_args = ['-framerate','60','-i',str(source/'frames/%06d.png')]
            filters = 'scale=960:672:flags=neighbor,pad=960:720:0:48:black,'
            filters += 'drawtext=expansion=none:textfile=label.txt:fontcolor=white:fontsize=14:x=6:y=5'
        else:
            import textwrap
            label.write_text('NON-DRAWABLE TEST — ONE-FRAME CARD\nNot engine footage\n\n'
                             + '\n'.join(textwrap.fill(line, 72) for line in text.splitlines())
                             + '\n\n' + textwrap.fill(case['drawable_disposition'], 72)
                             + '\n\nJUnit outcome: '+case['outcome'])
            input_args = ['-f','lavfi','-i','color=c=0x18202c:s=960x720:r=60']
            filters = 'drawtext=expansion=none:textfile=label.txt:fontcolor=white:fontsize=20:line_spacing=8:x=35:y=50'
        command = ['ffmpeg','-v','error','-xerror','-nostdin',*input_args,'-vf',filters,
                   '-frames:v',str(source_frames),'-an','-c:v','ffv1','-pix_fmt','bgr0',
                   '-threads','1',str(segment)]
        subprocess.run(command,cwd=directory,check=True)
        segments.append(segment)
        length=source_frames*args.slow
        chapters.append(f'[CHAPTER]\nTIMEBASE=1/60\nSTART={frame}\nEND={frame+length}\ntitle={case["id"]}\n')
        edit.append({'id':case['id'],'junit_unique_id':case['junit_unique_id'],'source':case['case_dir'],'kind':kind,
                     'source_start_frame':0,'source_end_frame_exclusive':case['frames'],
                     'output_start_frame':frame,'output_end_frame_exclusive':frame+length})
        case['clip'] = os.path.relpath(segment,root)
        frame+=length
    if not segments:
        raise ValueError('No executed invocations selected for stitching')
    concat=video.with_suffix('.ffconcat')
    def quote(path):
        return "'"+str(path).replace("'", "'\\''")+"'"
    concat.write_text('ffconcat version 1.0\n'+''.join('file '+quote(p)+'\n' for p in segments))
    chapter_path=video.with_suffix('.ffmetadata')
    chapter_path.write_text(';FFMETADATA1\n'+''.join(chapters))
    command=['ffmpeg','-v','error','-xerror','-nostdin','-f','concat','-safe','0','-i',str(concat),
             '-f','ffmetadata','-i',str(chapter_path),'-map_metadata','1','-map_chapters','1',
             '-vf',assembly_filter(args.slow),
             '-frames:v',str(frame),'-r','60','-an','-c:v','libx264','-threads','2',
             '-pix_fmt','yuv420p','-movflags','+faststart',str(video)]
    run(command)
    run(['ffmpeg','-v','error','-xerror','-nostdin','-i',str(video),'-f','null','-'])
    probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-count_frames','-show_streams',
                                             '-show_chapters','-show_format','-of','json',str(video)],text=True))
    actual=int(probe['streams'][0]['nb_read_frames'])
    if actual != frame:
        raise ValueError(f'Encoded frames {actual} differ from edit list {frame}')
    write_json(video.with_suffix('.edit.json'),{'fps':60,'frames':frame,'slow':args.slow,'clips':edit,
                                               'engine_frames':sum(c['frames'] for c,k,n in selected if k=='engine-frames'),
                                               'editorial_card_frames':sum(n for c,k,n in selected if k=='non-drawable-card'),
                                               'assembly_command':command,'ffprobe':probe})
    write_json(manifest_path,plan)
    print(f'{video}: {frame} frames / {frame/60:.3f}s, {len(edit)} actual test invocations')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='action',required=True)
    p=sub.add_parser('inventory');p.add_argument('--scope',default=str(DEFAULT_SCOPE));p.add_argument('--source',default='HEAD')
    p.add_argument('--add-commit',action='append',default=[]);p.add_argument('--include-unmerged',action='store_true');p.add_argument('--out',required=True)
    p=sub.add_parser('capture');p.add_argument('--inventory',required=True);p.add_argument('--select',action='append',required=True)
    p.add_argument('--out',required=True)
    for game in ('s1','s2','s3k','kis2'):
        p.add_argument('--'+game+'-rom')
    p=sub.add_parser('reconcile');p.add_argument('--inventory',required=True);p.add_argument('--run-dir',required=True);p.add_argument('--out',required=True);p.add_argument('--allow-legacy-source',action='store_true')
    p=sub.add_parser('combine');p.add_argument('--inventory',required=True);p.add_argument('--manifest',action='append',required=True);p.add_argument('--out',required=True);p.add_argument('--prefer-last',action='store_true');p.add_argument('--allow-legacy-source',action='store_true')
    p=sub.add_parser('stitch');p.add_argument('--manifest',required=True);p.add_argument('--out',required=True)
    p.add_argument('--slow',type=int,choices=range(1,17),default=1)
    p.add_argument('--include-nondrawable-cards',action='store_true',help='One explicitly labelled editorial frame per executed, reviewed non-drawable case')
    p.add_argument('--require-complete',action='store_true',help='Reject missing/failed executions, unknown source identity or omitted cases')
    args=parser.parse_args()
    if args.action=='inventory':
        plan=inventory(json.loads(Path(args.scope).read_text()),args.source,args.add_commit,args.include_unmerged)
        write_json(Path(args.out),plan)
        print(f"{len(plan['cases'])} new/extended invocations, {len({c['class'] for c in plan['cases']})} classes")
    elif args.action=='capture':
        return capture(args)
    elif args.action=='combine':
        combine(args)
    elif args.action=='reconcile':
        reconcile_command(args)
    else:
        stitch(args)
    return 0

if __name__=='__main__':
    sys.exit(main())
