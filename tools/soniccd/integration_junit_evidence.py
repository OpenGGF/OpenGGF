"""Export one explicit fresh JUnit XML selection as bounded observational JSON.

Inputs: exact classes, cutoff, allowlisted properties and a NEW external path.
Reuses the comparison reader and its strict fresh-JUnit parser; never launches
tests, alters reports or infers command completion/source match from XML.
Origin: 2026-11-04 continued Sonic CD packet-integration tooling task.
"""
import json
import os
from pathlib import Path
import stat

import integration_packet as packet
from integration_junit_compare import (_Evidence, _properties, MAX_CLASSES,
        MAX_DIRECTORY_ENTRIES, MAX_SELECTED_BYTES, MAX_CASES, MAX_XML_DEPTH)

MAX_OUTPUT_BYTES = 32 * 1024**2


def _protected(root):
    # Bind the selected repository, not an unrelated ancestor dotfiles repository.
    env = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
    env['GIT_OPTIONAL_LOCKS'] = '0'
    try:
        value = packet.subprocess.check_output(['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'],
                cwd=root, text=True, stderr=packet.subprocess.PIPE, env=env, timeout=10).strip()
    except (OSError, packet.subprocess.SubprocessError) as error:
        raise packet.PacketError('Cannot bind selected repository common metadata') from error
    packet.require(len(value) <= 4096, 'Common metadata path exceeds limit')
    common = packet.canonical(Path(value))
    roots = [root, packet.ROOT, common]
    # Ordinary main checkout metadata names its owning checkout explicitly.
    if common.name == '.git':
        roots.append(common.parent)
    return roots


def _parents(path, protected):
    packet.require(not any(path.is_relative_to(root) for root in protected),
                   'Evidence must be external to the selected repository/common metadata and tool checkout')
    packet.require(not any(part.casefold() in ('.git', 'target') for part in path.parts),
                   'Evidence cannot be written under Git metadata or target directories')
    result = []
    for parent in reversed((path.parent, *path.parent.parents)):
        packet.canonical(parent)
        info = parent.stat()
        packet.require(stat.S_ISDIR(info.st_mode), f'Evidence parent is not a directory: {parent}')
        result.append((str(parent), info.st_dev, info.st_ino, info.st_mode))
    return result


def _destination(value, protected):
    packet.require(isinstance(value, str) and bool(value) and len(value) <= 4096,
                   'Evidence requires a bounded explicit canonical absolute path')
    path = Path(value)
    packet.require(value == str(path), 'Evidence path contains a lexical alias')
    packet.canonical(path)
    parents = _parents(path, protected)
    packet.require(not path.exists() and not path.is_symlink(), f'Evidence destination already exists: {path}')
    return path, parents


def _encode(value):
    # Reject oversize complete evidence rather than truncate any identity or outcome.
    output = bytearray()
    for text in json.JSONEncoder(ensure_ascii=True, allow_nan=False, indent=2).iterencode(value):
        encoded = text.encode('utf-8')
        packet.require(len(output) + len(encoded) + 1 <= MAX_OUTPUT_BYTES,
                       f'Complete evidence exceeds {MAX_OUTPUT_BYTES}-byte output limit')
        output.extend(encoded)
    output.extend(b'\n')
    return bytes(output)


def _write_evidence(path, parents, protected, payload, *, recheck=None):
    """Write preflighted bounded JSON exclusively; optional input checks bracket creation.

    A failed check after creation retains the artifact as explicitly partial.
    Callers own the observation schema, input bounds and preflight timing.
    """
    if recheck is not None:
        recheck()
    created = False
    try:
        # Existing descriptor traversal pins the actual output parent without following any links.
        with packet.parent_fd(Path('/'), path) as parent:
            packet.require(_parents(path, protected) == parents, 'Evidence parent identities changed before creation')
            fd = os.open(path.name, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=parent)
            created = True
            with os.fdopen(fd, 'w+b') as stream:
                os.fchmod(stream.fileno(), 0o600)
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
                stream.seek(0)
                actual = stream.read(MAX_OUTPUT_BYTES + 1)
                info = os.fstat(stream.fileno())
                named = os.stat(path.name, dir_fd=parent, follow_symlinks=False)
                packet.require(actual == payload and stat.S_ISREG(info.st_mode) and info.st_nlink == 1
                        and packet.signature(info) == packet.signature(named), 'Evidence file changed during creation')
                packet.require(_parents(path, protected) == parents, 'Evidence parent identities changed during creation')
                if recheck is not None:
                    recheck()
    except (OSError, ValueError) as error:
        if created:
            # Never delete reports or unlink an output name that another writer may now own.
            raise packet.PacketError('Evidence export incomplete after exclusive creation; retain and inspect the artifact',
                    dict(evidence=str(path), partial=True)) from error
        raise
    return dict(evidence=str(path), sha256=packet.digest(payload), bytes=len(payload))


def save_junit_evidence(root, classes, since, properties, output, *, report_groups=None):
    """Observe XML and exclusively save an inventory, never a pass cache or run receipt."""
    packet.require(os.name == 'posix' and hasattr(os, 'O_NOFOLLOW') and hasattr(os, 'O_DIRECTORY'),
                   'Evidence export requires POSIX no-follow directory descriptors')
    root, _ = packet.worktree(root)
    packet.require(isinstance(classes, (list, tuple)) and 1 <= len(classes) <= MAX_CLASSES
            and all(isinstance(name, str) and packet.CLASS.fullmatch(name) for name in classes)
            and len(set(classes)) == len(classes), 'Supply 1–50 distinct exact --class names; no patterns')
    properties = _properties(properties)
    protected = _protected(root)
    path, parents = _destination(output, protected)
    nested = {} if report_groups is None else dict(report_groups=report_groups)
    observed = _Evidence(root, classes, since, properties, **nested)
    report = observed.summary
    report['requested_classes'] = list(classes)
    document = dict(version=1, kind='junit-xml-observations', report=report,
            limits=dict(max_classes=MAX_CLASSES, max_directory_entries=MAX_DIRECTORY_ENTRIES,
                max_xml_bytes_per_file=packet.MAX_FILE, max_selected_xml_bytes=MAX_SELECTED_BYTES,
                max_testcases=MAX_CASES, max_xml_depth=MAX_XML_DEPTH, max_output_bytes=MAX_OUTPUT_BYTES))
    payload = _encode(document)
    observed.recheck()
    saved = _write_evidence(path, parents, protected, payload)
    return dict(**saved, classes=len(report['classes']), **{key: report[key] for key in
                ('tests', 'failures', 'errors', 'skipped', 'clean', 'attribution')})
