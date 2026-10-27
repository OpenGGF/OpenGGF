"""Source inventory model for the 2026-10-27 project-atlas task.

Inputs are immutable Git blobs, never ROMs, submodule interiors or live class state.
This is a lexical inventory and explicit-import graph, not a Java compiler.
"""
from __future__ import annotations

import re

DOMAINS = ['Native games', 'Engine & world', 'Rendering & sound', 'Creator systems', 'Verification & tools', 'Racing & mods']
COMPONENTS = [
 ('Sonic 1', 0, 'ROM-backed Sonic 1 objects, events, menus and game systems.'),
 ('Sonic 2', 0, 'Sonic 2 gameplay, competition, special stages and Knuckles in Sonic 2.'),
 ('Sonic 3 & Knuckles', 0, 'S3K zones, objects, events, runtime, special stages and endings.'),
 ('Game framework', 1, 'Shared game sessions, semantic rules, profiles and resource ownership.'),
 ('World & objects', 1, 'Levels, object lifecycle, rings, terrain, scrolling and world mutation.'),
 ('Players & physics', 1, 'Playable sprites, movement, terrain sensing and collision.'),
 ('Rewind', 1, 'Captured state, identity graphs, schemas and restoration.'),
 ('Engine orchestration', 1, 'Game loop, startup, frame admission and subsystem composition.'),
 ('ROM & data', 1, 'ROM identity, byte pipelines, compression and resource IO.'),
 ('Graphics & camera', 2, 'Graphics pipeline, native viewport, shaders and sprite presentation.'),
 ('Audio', 2, 'SMPS, synthesis, audio sessions, output and rewind state.'),
 ('Input & settings', 2, 'Physical/logical controls, settings and configuration persistence.'),
 ('Mods & SDK', 3, 'Creator contracts, assets, manifests, scene hosting and fault boundaries.'),
 ('Stageplay', 3, 'Directed scenes, choreography, routes and cinematic runtime.'),
 ('Editor', 3, 'Experimental editing, commands, projects and editor rendering.'),
 ('Trace & replay', 4, 'Trace comparison, replay sessions and timing evidence.'),
 ('Capture & diagnostics', 4, 'Video/audio capture, debugging and benchmark instrumentation.'),
 ('Developer tools', 4, 'Headless boot, capture CLIs, asset probes and SDK tooling.'),
 ('Test support', 4, 'Cross-cutting tests, architecture guards and test infrastructure.'),
 ('Networking', 5, 'Reusable client/host networking, identity, wire messages and room services.'),
 ('Time Attack', 5, 'Bundled racing mod, lobby, HUD, ghosts and attempts.'),
 ('Racing server', 5, 'Master service, relay, verification and server tools.'),
 ('Example mods', 5, 'First-party example games, mods and tutorials.'),
 ('Integration & utilities', 1, 'Platform integrations, shared utility helpers and version support.'),
 ('Fixture & doc sources', 4, 'Embedded Java projects, documentation snippets and harness sources.'),
]
LEX = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
DECL = re.compile(r'\b(class|interface|enum)\s+([A-Za-z_$][\w$]*)|\brecord\s+([A-Za-z_$][\w$]*)\s*(?=[<(])|[{}]')
IMPORT = re.compile(r'\bimport\s+(static\s+)?([\w.$]+)(\.\*)?\s*;')


def canonical_package(package):
    return re.sub(r'^uk\.co\.jamesj999\.sonic(?=\.|$)', 'com.openggf', package)


def canonical_namespace_path(path):
    return re.sub(r'(^|/)uk/co/jamesj999/sonic/', r'\1com/openggf/', path, count=1)


def parse_java(text):
    """Mask strings/comments; count named declarations including nested/local types."""
    clean = LEX.sub(lambda m: re.sub(r'[^\n]', ' ', m.group()), text)
    match = re.search(r'\bpackage\s+([\w.]+)\s*;', clean)
    package = match.group(1) if match else ''
    depth = 0
    types = []
    for match in DECL.finditer(clean):
        token = match.group()
        if token == '{':
            depth += 1
        elif token == '}':
            depth -= 1
        else:
            kind, name = match.group(1) or 'record', match.group(2) or match.group(3)
            if kind == 'interface' and clean[max(0, match.start()-1):match.start()] == '@':
                kind = 'annotation'
            types.append([name, {'class': 0, 'interface': 1, 'enum': 2, 'record': 3, 'annotation': 4}[kind], int(depth == 0)])
    if depth:
        raise ValueError(f'Unbalanced lexical Java braces: {depth}')
    imports = [(bool(m.group(1)), m.group(2), bool(m.group(3))) for m in IMPORT.finditer(clean)]
    return package, types, imports


def source_scope(path):
    if '/src/test/java/' in '/'+path or path.startswith('src/test/java/'):
        return 1
    if path.startswith('src/main/java/') or (path.startswith('src/') and '/resources/' not in path and '/test/' not in path):
        return 0
    return 2 if path.startswith(('examples/', 'racing/')) else 3


def component(path, package):
    if path.startswith('examples/'):
        return 22
    if path.startswith('racing/time-attack/'):
        return 20
    if path.startswith('racing/server/'):
        return 21
    if not path.startswith('src/') or path.startswith(('src/main/resources/', 'src/test/resources/')):
        return 24
    # Historical paths predate the com.openggf rename. Normalize only for grouping.
    normalized = canonical_package(package)
    p = normalized.removeprefix('com.openggf.')
    for prefix, c in [('game.sonic1',0),('game.sonic2',1),('game.sonic3k',2),('game.rewind',6),('level.rewind',6),('trace',15),('tests.trace',15),('mods',12),('stageplay',13),('editor',14),('audio',10),('graphics',9),('camera',9),('render',9),('sprites.render',9),('sprites.art',9),('sprites',5),('physics',5),('level',4),('data',8),('io',8),('control',11),('configuration',11),('net',19),('capture',16),('debug',16),('bench',16),('tools',17),('testmode',15),('tests',18),('game',3),('timer',7),('architecture',7)]:
        if p == prefix or p.startswith(prefix+'.'):
            return c
    return 7 if normalized == 'com.openggf' else 23


def source_record(path, text, packages, roots):
    package, types, imports = parse_java(text)
    if package not in packages:
        packages.append(package)
    suffix = package.replace('.', '/')+'/' if package else ''
    directory = path.rsplit('/', 1)[0]+'/' if '/' in path else ''
    matches = bool(suffix and directory.endswith(suffix))
    root = directory[:-len(suffix)] if matches else directory
    if root not in roots:
        roots.append(root)
    name = path.rsplit('/', 1)[-1][:-5]
    record = [name, packages.index(package), roots.index(root), source_scope(path), component(path, package),
              text.count('\n')+int(bool(text) and not text.endswith('\n')), types, [], '' if matches else path[len(root):]]
    return record, imports


def source_path(record, packages, roots):
    return roots[record[2]]+(record[8] or ((packages[record[1]].replace('.', '/')+'/') if packages[record[1]] else '')+record[0]+'.java')


def compact_types(record):
    """Losslessly encode primary declaration names from their enclosing filename."""
    alphabet = '0123456789abcdefghijklmnopqrstuvwxyz'
    record[6] = [kind if name == record[0] and top else alphabet[kind+8*top]+name for name, kind, top in record[6]]
