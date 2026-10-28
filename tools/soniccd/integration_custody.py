"""Bounded read-only worktree, direct gitlink and selected resource-link custody.

Origin: 2026-10-28 Sonic CD integration tooling. Observational diagnostics only:
no repair, initialization, checkout, queued execution or cryptographic attribution
of initialized module bytes. Git config commands and local reference paths are
never execution authority. The owning caller chooses and reviews all link targets.
"""
from collections import deque
import os
from pathlib import Path, PurePosixPath
import re
import selectors
import signal
import stat
import subprocess
import time

import integration_packet as packet

MAX_FILES = 50000
MAX_MODULES = 128
MAX_LINKS = 128
MAX_COMMANDS = 600
MAX_COMMAND_OUTPUT = 8 * 1024**2
MAX_TOTAL_OUTPUT = 32 * 1024**2
MAX_STATUS = 1024**2
MAX_STATUS_ENTRIES = 10000
MAX_MODULES_FILE = 1024**2
MAX_SECONDS = 60
MAX_COMMAND_SECONDS = 10
MAX_PATH = 4096
COMMIT = re.compile(rb'[0-9a-f]{40}(?:[0-9a-f]{24})?\Z')
UNSAFE_CONFIG = r'^(filter\..*\.(clean|process)|diff\..*\.(textconv|command)|diff\.external)$'


class _Git:
    def __init__(self):
        self.started = time.monotonic()
        self.calls = self.total = 0
        self.env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        self.env.update(GIT_OPTIONAL_LOCKS='0', GIT_CONFIG_NOSYSTEM='1',
                        GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_SYSTEM=os.devnull,
                        GIT_ATTR_NOSYSTEM='1', GIT_TERMINAL_PROMPT='0',
                        GIT_NO_LAZY_FETCH='1', GIT_PAGER='cat')

    def run(self, root, *args, ok=(0,), limit=None):
        self.calls += 1
        remaining = MAX_SECONDS - (time.monotonic() - self.started)
        packet.require(self.calls <= MAX_COMMANDS and remaining > 0,
                       'Custody Git command/time budget exhausted')
        cap = min(MAX_COMMAND_OUTPUT, limit if limit is not None else MAX_COMMAND_OUTPUT)
        command = ['git', '--no-pager', '-c', 'core.fsmonitor=false',
                   '-c', 'core.untrackedCache=false', '-c', 'core.hooksPath='+os.devnull,
                   '-c', 'status.renames=false', '-c', 'submodule.recurse=false', *args]
        process = subprocess.Popen(command, cwd=root, env=self.env, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
        output = {process.stdout: bytearray(), process.stderr: bytearray()}
        deadline = time.monotonic() + min(remaining, MAX_COMMAND_SECONDS)
        try:
            with selectors.DefaultSelector() as selected:
                for stream in output: selected.register(stream, selectors.EVENT_READ)
                while selected.get_map():
                    left = deadline - time.monotonic()
                    packet.require(left > 0, 'Custody Git command timed out')
                    for key, _ in selected.select(min(left, .2)):
                        chunk = os.read(key.fileobj.fileno(), min(65536, cap + 1))
                        if not chunk:
                            selected.unregister(key.fileobj)
                            continue
                        output[key.fileobj].extend(chunk)
                        self.total += len(chunk)
                        packet.require(sum(map(len, output.values())) <= cap
                                       and self.total <= MAX_TOTAL_OUTPUT,
                                       'Custody Git output budget exhausted')
            code = process.wait(timeout=max(.001, deadline - time.monotonic()))
            packet.require(code in ok, 'Custody Git command failed: '+str(args[0]))
            return bytes(output[process.stdout])
        except subprocess.TimeoutExpired as error:
            raise packet.PacketError('Custody Git command timed out') from error
        finally:
            if process.poll() is None:
                try: os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError: pass
                process.wait()
            for stream in output: stream.close()


class _Observation:
    def __init__(self): self.saved = {}

    def read(self, path):
        packet.require(len(self.saved) < MAX_FILES or path in self.saved,
                       'Custody filesystem observation budget exhausted')
        try:
            info = path.lstat()
            signature = ((info.st_dev, info.st_ino, info.st_mode) if stat.S_ISDIR(info.st_mode)
                         else packet.signature(info))
            value = (signature, os.readlink(path) if stat.S_ISLNK(info.st_mode) else None)
        except FileNotFoundError:
            info, value = None, None
        if path in self.saved:
            packet.require(self.saved[path] == value, 'Custody filesystem changed during observation')
        else: self.saved[path] = value
        return info

    def recheck(self):
        for path in tuple(self.saved): self.read(path)


def _text(data, label):
    try: return data.decode('utf-8', 'strict')
    except UnicodeError as error: raise packet.PacketError(label+': invalid UTF-8') from error


def _relative(value):
    packet.require(isinstance(value, str) and 0 < len(value) <= MAX_PATH
                   and not any(ord(c) < 32 or ord(c) == 127 for c in value)
                   and '\\' not in value, 'Require bounded relative POSIX path')
    path = PurePosixPath(value)
    packet.require(not path.is_absolute() and str(path) == value
                   and not any(part in ('.', '..', '.git') for part in path.parts),
                   'Unsafe or aliased relative POSIX path')
    return value


def _kind(info):
    if info is None: return 'absent'
    if stat.S_ISLNK(info.st_mode): return 'symlink'
    if stat.S_ISDIR(info.st_mode): return 'directory'
    if stat.S_ISREG(info.st_mode): return 'file'
    return 'other'


def _parents(root, relative, observed):
    current = root
    for part in PurePosixPath(relative).parts[:-1]:
        current /= part
        info = observed.read(current)
        if info is None: return False
        packet.require(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode),
                       'Custody path parent is not an ordinary directory')
    return True


def _resolve(path, observed):
    """Resolve only bounded lstat/readlink metadata; never open target contents."""
    pending, current, steps, hops = deque(path.parts[1:]), Path('/'), 0, 0
    while pending:
        steps += 1
        packet.require(steps <= 512, 'Resource target resolution exceeds component limit')
        part = pending.popleft()
        if part in ('', '.'): continue
        if part == '..': current = current.parent; continue
        candidate = current / part
        info = observed.read(candidate)
        if info is not None and stat.S_ISLNK(info.st_mode):
            hops += 1
            packet.require(hops <= 40, 'Resource target resolution exceeds symlink limit')
            target = os.readlink(candidate)
            packet.require(len(target) <= MAX_PATH, 'Resource link target exceeds path limit')
            target_path = Path(target)
            if target_path.is_absolute(): current = Path('/')
            pending.extendleft(reversed(target_path.parts[1:] if target_path.is_absolute() else target_path.parts))
        else:
            current = candidate
            # Kernel path lookup cannot walk through a missing/non-directory
            # component, even when a later '..' lexically removes it. Retain a
            # diagnostic destination without falsely claiming the link exists.
            if pending and (info is None or not stat.S_ISDIR(info.st_mode)):
                return Path(os.path.normpath(str(current.joinpath(*pending)))), None
    return current, observed.read(current)


def _index(git, root):
    data = git.run(root, 'ls-files', '--stage', '-z')
    entries, modules, unmerged = data.split(b'\0'), {}, False
    packet.require(len(entries) <= MAX_FILES + 1, 'Custody index exceeds path limit')
    for entry in filter(None, entries):
        try:
            header, raw = entry.split(b'\t', 1)
            mode, commit, stage = header.split()
            packet.require(stage in (b'0', b'1', b'2', b'3'), 'Malformed index stage')
            name = _relative(_text(raw, 'Index path'))
            packet.require(COMMIT.fullmatch(commit) is not None, 'Malformed index object pin')
        except ValueError as error:
            raise packet.PacketError('Malformed custody index entry') from error
        unmerged |= stage != b'0'
        if mode == b'160000':
            modules.setdefault(name, []).append({'stage': int(stage), 'commit': commit.decode('ascii')})
    packet.require(len(modules) <= MAX_MODULES, 'Custody gitlink count exceeds limit')
    return data, modules, unmerged


def _head(git, root):
    value = git.run(root, 'rev-parse', 'HEAD').strip()
    packet.require(COMMIT.fullmatch(value) is not None, 'Custody requires a committed HEAD')
    return value.decode('ascii')


def _registrations(git, root, observed, issues):
    path = root / '.gitmodules'
    info = observed.read(path)
    if info is None: return None, {}, []
    packet.require(stat.S_ISREG(info.st_mode) and info.st_size <= MAX_MODULES_FILE,
                   '.gitmodules must be a bounded ordinary file, never a symlink')
    saved = packet.snapshot(path)
    data = git.run(root, 'config', '--no-includes', '--file', str(path), '--null', '--list',
                   limit=MAX_MODULES_FILE)
    registrations = {}
    names = {}
    records = data.split(b'\0')
    packet.require(len(records) <= MAX_FILES + 1, 'Custody registration field limit exhausted')
    for entry in filter(None, records):
        key, _, value = entry.partition(b'\n')
        key = _text(key, '.gitmodules key')
        if key.startswith('include.') or key.startswith('includeif.'):
            issues.append('.gitmodules includes are unsupported and were not followed')
        if not key.startswith('submodule.'): continue
        name, separator, field = key[len('submodule.'):].rpartition('.')
        packet.require(separator and len(name) <= 256 and not any(ord(c) < 32 for c in name),
                       'Malformed submodule registration name')
        names.setdefault(name, [])
        if field == 'path': names[name].append(_text(value, '.gitmodules path'))
    packet.require(len(names) <= MAX_MODULES, 'Custody registration count exceeds limit')
    summary = []
    for name, paths in sorted(names.items()):
        if len(paths) != 1:
            issues.append('Registration '+name+' requires exactly one path')
            summary.append(dict(name=name, path=None, valid=False))
            continue
        try: relative = _relative(paths[0])
        except packet.PacketError:
            issues.append('unsafe registration path for '+name)
            summary.append(dict(name=name, path=paths[0][:MAX_PATH], valid=False))
            continue
        registrations.setdefault(relative, []).append(name)
        summary.append(dict(name=name, path=relative, valid=True))
    for relative, registered in registrations.items():
        if len(registered) != 1: issues.append('Duplicate registration for '+relative)
    packet.unchanged(saved, message='.gitmodules changed during registration observation')
    return saved, registrations, summary


def _module(git, root, relative, pins, registrations, observed, issues):
    result = dict(path=relative, registrations=registrations, index_entries=pins,
                  expected_commit=pins[0]['commit'] if len(pins) == 1 and pins[0]['stage'] == 0 else None,
                  actual_commit=None, kind='absent', state='uninitialized', status=None)
    if len(registrations) != 1: issues.append(relative+': missing registration or duplicate registration')
    if not pins: issues.append(relative+': registration has no gitlink')
    if pins and result['expected_commit'] is None:
        issues.append(relative+': unmerged gitlink')
    path = root / relative
    if not _parents(root, relative, observed):
        issues.append(relative+': uninitialized module (parent absent)')
        return result
    info = observed.read(path)
    result['kind'] = _kind(info)
    if info is None:
        issues.append(relative+': uninitialized module')
        return result
    if stat.S_ISLNK(info.st_mode):
        result['state'] = 'linked-reference'
        issues.append(relative+': linked reference was not traversed as a module')
        return result
    if not stat.S_ISDIR(info.st_mode):
        result['state'] = 'unsupported-kind'
        issues.append(relative+': module path is not an ordinary directory')
        return result
    metadata = observed.read(path / '.git')
    if metadata is None:
        with os.scandir(path) as entries: nonempty = next(entries, None) is not None
        result['state'] = 'uninitialized-nonempty' if nonempty else 'uninitialized'
        issues.append(relative+': '+result['state']+' module')
        return result
    if stat.S_ISLNK(metadata.st_mode):
        result['state'] = 'unsupported-kind'
        issues.append(relative+': metadata symlink was not traversed')
        return result
    packet.require(stat.S_ISREG(metadata.st_mode) or stat.S_ISDIR(metadata.st_mode),
                   'Unsupported module Git metadata kind')
    top = _text(git.run(path, 'rev-parse', '--show-toplevel').rstrip(b'\n'), 'Module root')
    packet.require(top == str(path), 'Module Git root does not match its declared directory')
    # Name-only inspection follows Git's actual local includes without emitting
    # values. Refuse drivers before status can sample any filtered file content.
    config = git.run(path, 'config', '--includes', '--name-only', '--get-regexp', UNSAFE_CONFIG, ok=(0, 1))
    if config:
        result['state'] = 'unsafe-config'
        issues.append(relative+': configured clean/process/textconv/external diff requires independent safe custody')
        return result
    result['actual_commit'] = _head(git, path)
    module_index, nested, _ = _index(git, path)
    result['nested_gitlinks'] = len(nested)
    if nested: issues.append(relative+': nested gitlinks were not traversed')
    status_bytes = git.run(path, 'status', '--porcelain=v1', '-z', '--untracked-files=all',
                           '--ignore-submodules=all', limit=MAX_STATUS)
    records = list(filter(None, status_bytes.split(b'\0')))
    packet.require(len(records) <= MAX_STATUS_ENTRIES, 'Module status entry limit exhausted')
    counts = dict(staged=0, tracked=0, untracked=0, unmerged=0)
    for record in records:
        packet.require(len(record) >= 4 and record[2:3] == b' ', 'Malformed module status record')
        pair = record[:2]
        if pair == b'??': counts['untracked'] += 1
        elif pair == b'!!': raise packet.PacketError('Unexpected ignored module status record')
        else:
            packet.require(pair[0:1] in (b' ', b'M', b'A', b'D', b'T', b'U')
                           and pair[1:2] in (b' ', b'M', b'A', b'D', b'T', b'U'), 'Unsupported module status code')
            counts['staged'] += pair[0:1] != b' '
            counts['tracked'] += pair[1:2] != b' '
            counts['unmerged'] += pair in (b'DD', b'AU', b'UD', b'UA', b'DU', b'AA', b'UU')
    result['status'] = counts
    if result['actual_commit'] != result['expected_commit']:
        issues.append(relative+': commit mismatch')
    if any(counts.values()): issues.append(relative+': module has staged/tracked/untracked changes')
    result['state'] = 'dirty' if any(counts.values()) else 'clean'
    # Return private recheck inputs separately: no status filenames, config values
    # or index contents are emitted as diagnostic text.
    result['_recheck'] = (result['actual_commit'], module_index, status_bytes, config)
    return result


def _link(root, relative, expected, observed, issues):
    packet.require(isinstance(expected, str) and 0 < len(expected) <= MAX_PATH
                   and not any(ord(c) < 32 or ord(c) == 127 for c in expected),
                   'Require bounded canonical absolute expected link target')
    target = Path(expected)
    packet.require(target.is_absolute() and str(target) == expected and '..' not in target.parts,
                   'Expected resource target must be canonical absolute path')
    canonical, _ = _resolve(target, observed)
    packet.require(canonical == target, 'Expected resource target contains a symlink alias')
    present_parent = _parents(root, relative, observed)
    path = root / relative
    info = observed.read(path) if present_parent else None
    result = dict(path=relative, kind=_kind(info), link_text=None, resolved_target=None,
                  target_exists=False, target_kind='absent', expected_target=expected, matches_expected=False)
    if info is not None and stat.S_ISLNK(info.st_mode):
        result['link_text'] = os.readlink(path)
        resolved, endpoint = _resolve(path, observed)
        result.update(resolved_target=str(resolved), target_exists=endpoint is not None,
                      target_kind=_kind(endpoint), matches_expected=resolved == target)
    if result['kind'] != 'symlink': issues.append(relative+': selected resource is not a symlink')
    elif not result['target_exists']: issues.append(relative+': resource link target is absent')
    elif not result['matches_expected']: issues.append(relative+': resource link target mismatch')
    return result


def custody_summary(root, *, links=None):
    """Observe direct module/index and caller-selected link custody, without writes.

    Invalid/unsafe inputs, failed commands, exhausted bounds or observed concurrent
    changes raise PacketError. Observed missing/mismatched/dirty/uninitialized
    custody yields ready=False. A clean result is not execution/source authority.
    """
    packet.require(os.name == 'posix', 'Custody bounded Git observation requires POSIX')
    root = packet.canonical(Path(root))
    packet.require(root.is_dir(), 'Custody worktree root must be a directory')
    git, observed, issues = _Git(), _Observation(), []
    observed.read(root)
    top = _text(git.run(root, 'rev-parse', '--show-toplevel').rstrip(b'\n'), 'Worktree root')
    packet.require(top == str(root), 'Select the exact custody worktree root')
    head = _head(git, root)
    index, modules, unmerged = _index(git, root)
    if unmerged: issues.append('Root index has unmerged entries')
    saved, registered, registrations = _registrations(git, root, observed, issues)
    paths = sorted(set(modules) | set(registered))
    packet.require(len(paths) <= MAX_MODULES, 'Custody module inventory exceeds limit')
    inventory = [_module(git, root, path, modules.get(path, []), registered.get(path, []), observed, issues)
                 for path in paths]
    links = [] if links is None else links
    packet.require(isinstance(links, list) and len(links) <= MAX_LINKS, 'Require bounded link declaration list')
    seen, resources = set(), []
    for declaration in links:
        packet.require(isinstance(declaration, (list, tuple)) and len(declaration) == 2,
                       'Link declaration requires relative path and absolute target')
        relative = _relative(declaration[0])
        packet.require(relative not in seen, 'Duplicate resource link declaration')
        seen.add(relative)
        resources.append(_link(root, relative, declaration[1], observed, issues))
    # Recheck after all observations, including module HEAD/index/status. Git status
    # ignores nested modules so no uninspected nested driver can execute recursively.
    packet.require(_head(git, root) == head and _index(git, root)[0] == index,
                   'Root HEAD/index changed during custody observation')
    if saved is not None: packet.unchanged(saved, message='.gitmodules changed during custody observation')
    for module in inventory:
        previous = module.pop('_recheck', None)
        if previous is None: continue
        path = root / module['path']
        config = git.run(path, 'config', '--includes', '--name-only', '--get-regexp', UNSAFE_CONFIG, ok=(0, 1))
        packet.require(not config, 'Module configuration changed during custody observation')
        current = (_head(git, path), _index(git, path)[0],
                   git.run(path, 'status', '--porcelain=v1', '-z', '--untracked-files=all',
                           '--ignore-submodules=all', limit=MAX_STATUS), config)
        packet.require(current == previous, 'Module HEAD/index/status changed during custody observation')
    observed.recheck()
    packet.require(time.monotonic() - git.started <= MAX_SECONDS, 'Custody observation time budget exhausted')
    return dict(version=1, ready=not issues, worktree=str(root), head=head,
                index_unmerged=unmerged, root_sources='Ordinary root source dirtiness not inspected',
                registrations=registrations, modules=inventory, links=resources, issues=issues,
                limits=['Non-atomic observations can miss changes restored between reads; no test/execution authority.',
                        'Module bytes are not cryptographically fingerprinted; ignored files and nested module contents are not inspected.',
                        'Only explicitly selected resource links are observed; target bytes, media and other external dependencies are not inspected.',
                        'System/global Git config is disabled; local clean/process/textconv/external-diff config blocks module status; fsmonitor and recursive module status are disabled.',
                        'POSIX only. Limits: 50000 filesystem/index observations, 128 modules, 128 links, 600 Git calls, 8MiB/command, 32MiB aggregate output, 1MiB status, 10000 status entries, 60s total/10s per Git command; filesystem calls are not preemptible.'])
