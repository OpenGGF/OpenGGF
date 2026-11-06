#!/usr/bin/env python3
"""Generate the Git-backed source atlas. Origin: 2026-10-27 project atlas.

Inputs: one immutable Git commit and local Git history. Output: one HTML file
with the source census, historical file lifetimes, imports and activity charts.
No ROMs, Maven, checkout changes, servers or generation-time network access.
"""
from __future__ import annotations

import argparse
import base64
import collections
import copy
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

from model import COMPONENTS, DOMAINS, canonical_namespace_path, canonical_package, compact_types, parse_java, source_path, source_record


class Git:
    def __init__(self, root):
        self.root = Path(root).resolve()

    def run(self, *args):
        return subprocess.check_output(['git', '-c', 'core.quotePath=false', *args], cwd=self.root)

    def text(self, *args):
        return self.run(*args).decode('utf-8', errors='replace')

    def resolve(self, ref):
        return self.text('rev-parse', '--verify', '--end-of-options', ref+'^{commit}').strip()

    def tree(self, ref):
        rows = []
        for entry in self.run('ls-tree', '-r', '-l', '-z', ref).split(b'\0'):
            if not entry:
                continue
            fields, path = entry.split(b'\t', 1)
            mode, kind, sha, size = fields.split()
            if kind == b'blob' and mode in (b'100644', b'100755'):
                rows.append((path.decode('utf-8'), sha.decode(), int(size)))
        return rows

    def blobs(self, specs):
        """Stream batch blob bodies; a temporary stdin file avoids pipe deadlock."""
        specs = list(specs)
        with tempfile.TemporaryFile() as commands:
            commands.write(('\n'.join(specs)+'\n').encode())
            commands.seek(0)
            process = subprocess.Popen(['git', 'cat-file', '--batch'], cwd=self.root,
                                       stdin=commands, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            try:
                for spec in specs:
                    header = process.stdout.readline().split()
                    if len(header) != 3 or header[1] != b'blob':
                        raise ValueError(f'Cannot read Git blob: {spec}')
                    size = int(header[2])
                    data = process.stdout.read(size)
                    if len(data) != size or process.stdout.read(1) != b'\n':
                        raise ValueError(f'Incomplete Git blob: {spec}')
                    yield data
                if process.wait() != 0:
                    raise ValueError('git cat-file failed')
            finally:
                if process.poll() is None:
                    process.kill()
                process.wait()
                process.stdout.close()


def scan(git, pin, label):
    tree = git.tree(pin)
    java = sorted((p, sha) for p, sha, _ in tree if p.endswith('.java'))
    packages, roots, records, imports = [], [], [], []
    lookup = collections.defaultdict(list)
    fingerprint = hashlib.sha256()
    for (path, _), raw in zip(java, git.blobs(sha for _, sha in java)):
        text = raw.decode('utf-8', errors='replace').replace('\r\n', '\n')
        fingerprint.update(path.encode()+b'\0'+text.encode())
        record, imported = source_record(path, text, packages, roots)
        records.append(record)
        imports.append(imported)
        for name, _, top in record[6]:
            if top:
                package = packages[record[1]]
                lookup[(package+'.' if package else '')+name].append(len(records)-1)
    edges = collections.Counter()
    wildcard = ambiguous = 0
    for i, imported in enumerate(imports):
        targets = set()
        for static, name, star in imported:
            if star:
                wildcard += 1
            target = name
            while target and target not in lookup:
                target = target.rpartition('.')[0]
            if not target or (star and not static):
                continue
            candidates = lookup[target]
            local = [j for j in candidates if records[j][2] == records[i][2]]
            main = [j for j in candidates if records[j][3] == 0]
            pool = local or main or candidates
            if len(pool) != 1:
                ambiguous += 1
                continue
            if pool[0] != i:
                targets.add(pool[0])
        records[i][7] = sorted(targets)
        for j in targets:
            if records[i][4] != records[j][4]:
                edges[(records[i][4], records[j][4], records[i][3])] += 1
    extensions = collections.Counter()
    folders = collections.defaultdict(collections.Counter)
    for path, _, _ in tree:
        ext = Path(path).suffix.lower() or '(none)'
        extensions[ext] += 1
        folders[path.split('/')[0]][ext] += 1
    libraries = []
    version, java_release = 'unknown', 'unknown'
    if any(path == 'pom.xml' for path, _, _ in tree):
        pom = ET.fromstring(next(git.blobs([pin+':pom.xml'])))
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        version = pom.findtext('m:version', default='unknown', namespaces=ns)
        java_release = pom.findtext('m:properties/m:maven.compiler.release', default='unknown', namespaces=ns)
        for d in pom.findall('m:dependencies/m:dependency', ns):
            if d.find('m:classifier', ns) is not None:
                continue
            libraries.append([d.findtext('m:groupId', namespaces=ns), d.findtext('m:artifactId', namespaces=ns),
                              d.findtext('m:scope', default='compile', namespaces=ns)])
    data = dict(sha=pin, branch=label, fingerprint=fingerprint.hexdigest(), version=version, javaRelease=java_release,
                domains=DOMAINS, components=COMPONENTS, packages=packages, roots=roots, files=records,
                edges=[[a, b, s, n] for (a, b, s), n in sorted(edges.items())], libraries=libraries,
                repository={'files': len(tree), 'bytes': sum(size for _, _, size in tree), 'extensions': extensions.most_common(),
                            'folders': {k: dict(v) for k, v in sorted(folders.items())}},
                limits={'wildcards': wildcard, 'ambiguous': ambiguous, 'resolvedImports': sum(len(r[7]) for r in records)},
                stats={'files': len(records), 'lines': sum(r[5] for r in records), 'types': sum(len(r[6]) for r in records),
                       'topLevel': sum(t[2] for r in records for t in r[6]), 'packages': len(packages)})
    assert all(source_path(r, packages, roots) == java[i][0] for i, r in enumerate(records))
    return data


def diff_scopes(path):
    return [0]+([1] if path.endswith('.java') else [])+([2] if path.endswith('.java') and '/src/test/java/' in '/'+path else [])


def extract_history(git, pin, inventory):
    output = git.text('log', '--first-parent', '--root', '--diff-merges=first-parent', '--no-renames', '--numstat',
                      '--format=@@%H%x09%ct%x09%s', pin)
    paths, path_ids, rows = [], {}, []
    current, changes = None, []

    def finish():
        if current is None:
            return
        stats = [[0]*4 for _ in range(3)]
        candidates = [[] for _ in range(3)]
        for path, adds, removes, binary in changes:
            for s in diff_scopes(path):
                for k, n in enumerate((adds, removes, 1, int(binary))):
                    stats[s][k] += n
                candidates[s].append([path, adds, removes, int(binary)])
        samples = []
        for entries in candidates:
            sample = []
            for path, a, d, b in sorted(entries, key=lambda r: r[1]+r[2], reverse=True)[:5]:
                if path not in path_ids:
                    path_ids[path] = len(paths)
                    paths.append(path)
                sample.append([path_ids[path], a, d, b])
            samples.append(sample)
        rows.append([*current, stats, samples])

    for line in output.splitlines():
        if line.startswith('@@'):
            finish()
            sha, stamp, subject = line[2:].split('\t', 2)
            current, changes = [sha[:12], int(stamp), subject], []
        elif line and '\t' in line:
            a, d, path = line.split('\t', 2)
            if a.isdigit() and d.isdigit():
                changes.append((path, int(a), int(d), False))
            elif a == d == '-':
                changes.append((path, 0, 0, True))
    finish()
    rows.sort(key=lambda r: (r[1], r[0]))
    count = int(git.text('rev-list', '--first-parent', '--count', pin))
    assert len(rows) == count
    assert sum(r[3][1][0]-r[3][1][1] for r in rows) == inventory['stats']['lines'], 'Java Git deltas do not reconcile to source census'
    milestones = []

    def milestone(ref, label, lane, why, kind='Commit'):
        try:
            sha = git.resolve(ref)
        except subprocess.CalledProcessError:
            return
        if subprocess.run(['git', 'merge-base', '--is-ancestor', sha, pin], cwd=git.root, stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL).returncode:
            return
        stamp, subject = git.text('show', '-s', '--format=%ct%x09%s', sha).strip().split('\t', 1)
        milestones.append(dict(sha=sha[:12], time=int(stamp), label=label, lane=lane, why=why, subject=subject, kind=kind))

    origin = git.text('rev-list', '--first-parent', '--max-parents=0', pin).strip().splitlines()
    if origin:
        milestone(origin[0], 'First commit', 'Origins', 'Initial repository commit in the first-parent lineage.')
    for ref, label, lane, why in [
        ('d6a89fd76','Multi-game configuration','Engine','Adds per-game ROM filename configuration.'),
        ('0b5a23469','S3K data foundation','Engine','Explicit initial Sonic 3 & Knuckles data support.'),
        ('8bffff3f6','Rewind controller','Engine','Orchestrated rewind/playback controller; not complete rewind certification.'),
        ('78296018b','Native-time test reels','Verification','Opt-in recording of actual JUnit fixtures at native time.'),
        ('1259f7fdd','Protocol 8 hardening','Multiplayer','Authority and lifecycle hardening source checkpoint, not a launch date.'),
    ]:
        # These optional project-specific checkpoints need not exist in a fixture or old ref.
        if subprocess.run(['git','rev-parse','--verify','--quiet',ref], cwd=git.root, stdout=subprocess.DEVNULL).returncode == 0:
            milestone(ref, label, lane, why)
    names = [p for p, _, _ in git.tree(pin)]
    if 'CHANGELOG.md' in names:
        index = next(git.blobs([pin+':CHANGELOG.md'])).decode(errors='replace')
        for version in dict.fromkeys(re.findall(r'\[(\d+\.\d+\.\d{8})\]', index)):
            if subprocess.run(['git','show-ref','--verify','--quiet','refs/tags/v'+version], cwd=git.root).returncode == 0:
                milestone('refs/tags/v'+version, version, 'Release tags', 'Release-index tag at target-commit time, not an asserted publication time.', 'Release tag')
    subjects = [line.split('\t', 2) for line in git.text('log','--format=%H%x09%ct%x09%s',pin).splitlines()]
    for pattern, label, lane in [(r'stageplay','Stageplay feature checkpoint','Creators'),
                                 (r'modcontext|mod api|mod-api','Mod API feature checkpoint','Creators'),
                                 (r'multiplayer','Multiplayer feature checkpoint','Multiplayer')]:
        matches = [(int(stamp), sha) for sha, stamp, subject in subjects
                   if re.search(r'^feat(?=[(:])(?=.*(?:'+pattern+'))', subject, re.I)]
        if matches:
            milestone(min(matches)[1], label, lane, 'Earliest reachable feat-subject checkpoint; not a claim of complete feature qualification.')
    milestone(pin, 'Atlas source snapshot', 'Verification', 'Exact source revision used by this atlas.')
    milestones.sort(key=lambda m:(m['time'],m['label']))
    return dict(pin=pin, commits=rows, paths=paths, milestones=milestones,
                scopes=['All tracked text','Java files','Java test files'],
                lanes=['Origins','Engine','Creators','Multiplayer','Verification','Release tags'],
                firstParentCommits=count, reachableCommits=int(git.text('rev-list','--count',pin)),
                totals=[[sum(r[3][s][i] for r in rows) for i in range(4)] for s in range(3)],
                start=min(r[1] for r in rows), end=max(r[1] for r in rows))


def compact_history(full):
    days = {}
    for row in full['commits']:
        day = row[1]//86400*86400
        bucket = days.setdefault(day, [day, 0, [[0]*5 for _ in range(3)]])
        bucket[1] += 1
        for s in range(3):
            for i in range(4):
                bucket[2][s][i] += row[3][s][i]
            bucket[2][s][4] += int(row[3][s][2] > 0)
    data = copy.deepcopy(full)
    data['daily'] = sorted(days.values())
    data['commits'] = data['commits'][-100:]
    used = sorted({v[0] for r in data['commits'] for sample in r[4] for v in sample})
    remap = {old:i for i,old in enumerate(used)}
    data['paths'] = [data['paths'][i] for i in used]
    for row in data['commits']:
        for sample in row[4]:
            for value in sample:
                value[0] = remap[value[0]]
    data['detailLimit'] = len(data['commits'])
    assert sum(r[1] for r in data['daily']) == full['firstParentCommits']
    assert all(sum(r[2][s][i] for r in data['daily']) == full['totals'][s][i] for s in range(3) for i in range(4))
    return data


def namespace_groups(git, commits, intervals, edits):
    """Join same-integration namespace moves with matching declarations and no overlap.

    Similar basenames, moves in other packages, and concurrently existing copies
    do not establish continuity. Keep exact original path intervals as aliases.
    """
    candidates = []
    for index, (added, deleted) in sorted(edits.items()):
        a, d = collections.defaultdict(list), collections.defaultdict(list)
        for path in added:
            a[canonical_namespace_path(path)].append(path)
        for path in deleted:
            d[canonical_namespace_path(path)].append(path)
        for key in sorted(a.keys() & d.keys()):
            if index and len(a[key]) == len(d[key]) == 1 and a[key][0] != d[key][0]:
                candidates.append((index, d[key][0], a[key][0]))
    specs = [spec for index, old, new in candidates
             for spec in (commits[index-1][0]+':'+old, commits[index][0]+':'+new)]
    bodies = iter(git.blobs(specs))
    parent = {path:path for path in intervals}
    members = {path:{path} for path in intervals}
    boundaries = collections.Counter()

    def find(path):
        while parent[path] != path:
            parent[path] = parent[parent[path]]
            path = parent[path]
        return path

    for index, old, new in candidates:
        before, after = [parse_java(next(bodies).decode(errors='replace')) for _ in range(2)]
        # Records and ordinary classes retain their named class identity when
        # converted during the package move; interface/enum changes stay distinct.
        old_types = sorted((name, 0 if kind == 3 else kind) for name, kind, top in before[1] if top)
        new_types = sorted((name, 0 if kind == 3 else kind) for name, kind, top in after[1] if top)
        if not old_types or old_types != new_types or before[0] == after[0] or canonical_package(before[0]) != canonical_package(after[0]):
            continue
        left, right = find(old), find(new)
        if left != right:
            overlap = any(a < d and c < b for p in members[left] for q in members[right]
                          for a,b in zip(intervals[p][::2], intervals[p][1::2])
                          for c,d in zip(intervals[q][::2], intervals[q][1::2]))
            if overlap:
                continue
            parent[right] = left
            members[left].update(members.pop(right))
        boundaries[index] += 1
    # Consume/close the Git batch even if no candidates were accepted.
    for _ in bodies:
        pass
    return list(members.values()), boundaries


def extract_lifetimes(git, pin, inventory):
    commits = [line.split('\t', 1) for line in git.text('log','--reverse','--first-parent','--format=%H%x09%ct',pin).splitlines()]
    positions = {sha:i for i,(sha,_) in enumerate(commits)}
    n = len(commits)
    intervals, live, last_end = collections.defaultdict(list), {}, {}
    edits = collections.defaultdict(lambda: (set(), set()))
    changes = git.text('log','--reverse','--first-parent','--root','--diff-merges=first-parent','--no-renames',
                       '--diff-filter=AD','--name-status','--format=@@%H',pin,'--','*.java')
    index = None
    for line in changes.splitlines():
        if line.startswith('@@'):
            index = positions[line[2:]]
        elif line.startswith('A\t'):
            path = line[2:]
            assert path not in live, ('duplicate add',path,index)
            live[path] = index
            edits[index][0].add(path)
        elif line.startswith('D\t'):
            path = line[2:]
            assert path in live, ('delete without add',path,index)
            intervals[path].extend([live.pop(path), index])
            last_end[path] = index
            edits[index][1].add(path)
    for path, start in live.items():
        intervals[path].extend([start, n])
    current_paths = [source_path(r,inventory['packages'],inventory['roots']) for r in inventory['files']]
    assert set(live) == set(current_paths), 'History head must match the exact source inventory paths'
    groups, boundaries = namespace_groups(git, commits, intervals, edits)
    by_representative, merged = {}, {}
    current_set = set(live)
    for group in groups:
        current = group & current_set
        assert len(current) <= 1, 'Joined source identities must not coexist'
        representative = next(iter(current)) if current else max(group, key=lambda p:(last_end[p],p))
        by_representative[representative] = group
        spans = sorted((a,b) for path in group for a,b in zip(intervals[path][::2],intervals[path][1::2]))
        union = []
        for start,end in spans:
            if union and start == union[-1]:
                union[-1] = end
            else:
                union.extend([start,end])
        merged[representative] = union
    removed_paths = sorted(set(by_representative)-set(live))
    packages, roots = list(inventory['packages']), list(inventory['roots'])
    specs = [commits[last_end[path]-1][0]+':'+path for path in removed_paths]
    removed = []
    for path, raw in zip(removed_paths, git.blobs(specs)):
        record, _ = source_record(path, raw.decode(errors='replace').replace('\r\n','\n'), packages, roots)
        removed.append(record)
    # The slider uses exact commit snapshots, sampled at daily integration boundaries.
    # Keep first and final commits, and both sides of each day boundary.
    stops = {0,n-1}
    for i in range(n-1):
        if int(commits[i][1])//86400 != int(commits[i+1][1])//86400:
            stops.add(i)
    for index in boundaries:
        stops.update([index-1,index])
    steps = [[i,int(commits[i][1]),commits[i][0][:12]] for i in sorted(stops)]
    paths = current_paths+removed_paths
    lifetimes = [merged[path] for path in paths]
    aliases = [[i,[[p,intervals[p]] for p in sorted(by_representative[path]) if p != path]]
               for i,path in enumerate(paths) if len(by_representative[path]) > 1]
    # Compare path identities, not merely totals, with independent Git tree snapshots.
    checked = []
    check_indices = {0,n//4,n//2,3*n//4,n-1} | {i for boundary in boundaries for i in (boundary-1,boundary)}
    for i in sorted(check_indices):
        expected = {path for path,_,_ in git.tree(commits[i][0]) if path.endswith('.java')}
        actual = {path for path, spans in intervals.items() if any(a <= i < b for a,b in zip(spans[::2],spans[1::2]))}
        assert actual == expected, ('historical tree mismatch',i,actual^expected)
        assert sum(any(a <= i < b for a,b in zip(s[::2],s[1::2])) for s in lifetimes) == len(expected), ('identity count mismatch',i)
        checked.append([i,len(expected),commits[i][0][:12]])
    for record in removed:
        compact_types(record)
    return dict(count=n, steps=steps, lifetimes=lifetimes, removed=removed,
                packages=packages[len(inventory['packages']):], roots=roots[len(inventory['roots']):], checked=checked,
                aliases=aliases, boundaries=[[i,commits[i][0][:12],count] for i,count in sorted(boundaries.items())],
                namespaceMoves=sum(boundaries.values()), rawPathCount=len(intervals))


def encode(value):
    raw = json.dumps(value,separators=(',',':')).encode()
    return base64.b64encode(gzip.compress(raw,mtime=0)).decode()


def build(git, ref, output, t3_limit=False):
    pin = git.resolve(ref)
    print('Scanning source blobs at',pin,flush=True)
    inventory = scan(git,pin,ref)
    print('Extracting first-parent diff history',flush=True)
    history = extract_history(git,pin,inventory)
    print('Reconstructing Java file lifetimes and checking Git trees',flush=True)
    temporal = extract_lifetimes(git,pin,inventory)
    compact = copy.deepcopy(inventory)
    compact['adjacency'] = [r[7] for r in compact['files']]
    for record in compact['files']:
        record[7] = []
        compact_types(record)
    folder = Path(__file__).parent
    html = (folder/'atlas.template.html').read_text()
    substitutions = {'__INVENTORY__':encode(compact), '__HISTORY__':encode(compact_history(history)),
                     '__TEMPORAL__':encode(temporal), '__HISTORY_UI__':(folder/'history-ui.js').read_text(),
                     '__TIME_UI__':(folder/'time-ui.js').read_text()}
    for token, value in substitutions.items():
        if html.count(token) != 1:
            raise ValueError(f'Template must contain exactly one {token}')
        html = html.replace(token,value)
    if t3_limit and len(html)>512000:
        raise ValueError(f'T3 HTML input limit exceeded: {len(html):,} characters. Standalone generation remains available without --t3-limit.')
    output = Path(output)
    output.parent.mkdir(parents=True,exist_ok=True)
    # Atomic replacement avoids leaving a half-written interactive on interruption.
    with tempfile.NamedTemporaryFile(mode='w',encoding='utf-8',dir=output.parent,prefix=output.name+'.',suffix='.tmp',delete=False) as f:
        temporary = Path(f.name)
        f.write(html)
    try:
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    print(json.dumps({'output':str(output),'sha':pin,'characters':len(html),'source':inventory['stats'],
                      'historicalOnlyFiles':len(temporal['removed']),'sliderStops':len(temporal['steps']),
                      'linkedNamespaceMoves':temporal['namespaceMoves'],
                      'verifiedGitTrees':len(temporal['checked']),'firstParentCommits':history['firstParentCommits']},indent=2))
    return inventory, history, temporal


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2],help='Git checkout to read (never modified)')
    parser.add_argument('--ref',default='HEAD',help='Immutable snapshot source, resolved once; default HEAD')
    parser.add_argument('--output',type=Path,help='Default: docs/architecture/audits/project-source-atlas.html under --repo')
    parser.add_argument('--t3-limit',action='store_true',help='Also enforce the 512,000-character T3 HTML input limit')
    args = parser.parse_args()
    build(Git(args.repo),args.ref,args.output or args.repo/'docs/architecture/audits/project-source-atlas.html',args.t3_limit)


if __name__ == '__main__':
    main()
