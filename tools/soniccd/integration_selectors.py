"""Read-only explicit source/class observations before an independently queued run.

Origin: 2026-10-29 Sonic CD integration selector preflight. This optional mode
does not gate or alter integration_run, infer filenames, discover tests, execute
Java, or qualify Maven/JUnit outcomes. Source observations are non-atomic.
"""
from pathlib import Path
import os
import stat

import integration_packet as packet
from integration_handoff import MAX_SPEC, handoff_summary
import java_declaration_census as census

MAX_SELECTED_BYTES = 64 * 1024 * 1024
MAX_HINT_METHODS = 100
MAX_HINT_REASONS = 20
MAX_MESSAGE = 512

LIMITATIONS = [
    'Explicit named Java sources and exact lexical named-type owners only; no filename/package inference, Java compiler, classpath, ABI or Surefire selector validation.',
    'Generated/external sources must be explicitly supplied or remain missing; nested owners retain lexical dotted names, with no JVM binary-name conversion.',
    'Incomplete ordinary JUnit inventory does not invalidate an observed owner. Counts are advisory source proposals only; inherited/selected method resolution, overloads, annotations, extensions, discovery and runtime skips remain unproved.',
    'A complete static whole-class proposal can flag a differing declared count; no count is compared for incomplete inventory or an explicit method selection. No handoff or selector is rewritten.',
    'Existing source-census grammar/token/file limits remain in force; unsupported or oversized source is incomplete, not an absent owner or a clean result.',
    'Named sources, spec, handoff and HEAD are observed and rechecked non-atomically. Unselected inputs and edits restored between observations remain outside this proof.',
    'Only the separate unchanged run --execute path can request Maven admission; fresh executed XML, source identity, terminal status, exact cases/counts/properties and zero skips still own validation.',
]


def _source_mapping(root, sources, classes):
    packet.require(isinstance(sources, (list, tuple)) and 1 <= len(sources) <= 50,
                   'Require 1..50 explicit FQN=relative.java source mappings')
    result = {}
    for value in sources:
        packet.require(isinstance(value, str) and len(value) <= 4609
                       and not any(ord(c) < 32 or ord(c) == 127 for c in value),
                       'Source mapping requires bounded plain FQN=relative.java text')
        name, separator, relative = value.partition('=')
        packet.require(separator and name in classes and name not in result,
                       'Source mappings require each requested class exactly once')
        try:
            census.relative(relative)
        except census.CensusError as error:
            raise packet.PacketError(str(error)[:MAX_MESSAGE]) from error
        path = packet.canonical(root / relative)
        packet.require(path.is_relative_to(root) and path != root,
                       'Selected source must stay inside worktree')
        result[name] = path
    packet.require(set(result) == set(classes), 'Source mappings must cover exactly tests.classes')
    return result


def _bounded_snapshot(path, limit):
    """Descriptor-bound named observation; oversized content stays unread/unhashed."""
    packet.canonical(path)
    try:
        before = path.lstat()
    except FileNotFoundError:
        return packet.Snapshot(path, None, None)
    packet.require(stat.S_ISREG(before.st_mode), 'Selected input must be a regular file')
    if before.st_size > limit:
        return packet.Snapshot(path, None, before)
    fd = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0) | getattr(os, 'O_NONBLOCK', 0))
    with os.fdopen(fd, 'rb') as stream:
        packet.require(packet.signature(os.fstat(stream.fileno())) == packet.signature(before),
                       'Selected input changed before bounded read')
        # The initial descriptor size is <=limit. A growing file cannot enlarge this read.
        data = stream.read(before.st_size)
        after = os.fstat(stream.fileno())
    packet.require(packet.signature(before) == packet.signature(after) == packet.signature(path.lstat())
                   and len(data) == before.st_size, 'Selected input changed during bounded read')
    packet.canonical(path)
    return packet.Snapshot(path, data, before)


def _source_unchanged(saved, limit):
    current = _bounded_snapshot(saved.path, limit)
    packet.require((saved.info is None and current.info is None) or
        (saved.info is not None and current.info is not None and
         packet.signature(saved.info) == packet.signature(current.info) and saved.data == current.data),
        'Selected source changed during observation')


def _early_changed(root, head, error):
    return dict(version=1, kind='source-selector-preflight', worktree=str(root),
        head_before=head, head_after=None, id=None, selectors=[], ready=False,
        status='incomplete', exit_code=3, observation_error=str(error)[:MAX_MESSAGE],
        handoff=dict(ready=False, stage='unobserved', reasons=['Named handoff changed before readiness could be retained']),
        coverage=dict(complete_test_proof=False, method_resolution_proved=False,
                      stable_named_sources=False, non_atomic=True), limitations=LIMITATIONS)


def _hint(data, limits, selected, expected, methods):
    hint = census.test_inventory(data, limits, selected)
    candidates = hint['candidate_methods']
    reasons = hint['incomplete_reasons']
    bounded = dict(complete=hint['complete'], selected_class=hint['selected_class'],
        proposed_count=hint['proposed_count'], candidate_methods=candidates[:MAX_HINT_METHODS],
        candidate_methods_omitted=max(0, len(candidates) - MAX_HINT_METHODS),
        incomplete_reasons=reasons[:MAX_HINT_REASONS],
        incomplete_reasons_omitted=max(0, len(reasons) - MAX_HINT_REASONS))
    applicable = hint['complete'] and not methods
    comparison = dict(applicable=applicable, declared=expected,
                      proposed=hint['proposed_count'],
                      matches=(expected == hint['proposed_count']) if applicable else None,
                      observation='Whole-class source hint only; selected/inherited methods and executed invocations are not resolved')
    return bounded, comparison


def selector_summary(root, spec_path, sources, *, installed=False):
    """Observe explicit lexical owners without changing the run contract or inputs."""
    packet.require(type(installed) is bool, 'installed must be boolean')
    root, initial_head = packet.worktree(root)
    spec_path = packet.canonical(Path(spec_path))
    packet.require(not spec_path.is_relative_to(root), 'Selector handoff spec must be external')
    packet.require(spec_path.lstat().st_size <= MAX_SPEC, 'Selector handoff spec exceeds existing size budget')
    spec = _bounded_snapshot(spec_path, MAX_SPEC)
    packet.require(spec.info is not None and spec.data is not None, 'Missing or oversized selector handoff spec')
    try:
        ready = handoff_summary(root, spec_path, installed=installed)
    except (packet.PacketError, OSError):
        try:
            _source_unchanged(spec, MAX_SPEC)
        except (packet.PacketError, OSError) as changed:
            return _early_changed(root, initial_head, changed)
        raise
    try:
        _source_unchanged(spec, MAX_SPEC)
    except (packet.PacketError, OSError) as changed:
        return _early_changed(root, initial_head, changed)
    tests = ready['tests']
    mapped = _source_mapping(root, sources, tests['classes'])
    limits = census.Limits()
    limits.validate()
    report = dict(version=1, kind='source-selector-preflight', worktree=str(root),
        head_before=initial_head, head_after=None, id=ready['id'],
        handoff={key: ready[key] for key in ('ready', 'stage', 'reasons')},
        selectors=[], ready=False, status='needs_review', exit_code=1,
        limits=dict(max_classes=50, max_selected_bytes=MAX_SELECTED_BYTES,
                    census=vars(limits), max_hint_methods=MAX_HINT_METHODS,
                    max_hint_reasons=MAX_HINT_REASONS),
        limitations=LIMITATIONS,
        coverage=dict(complete_test_proof=False, method_resolution_proved=False,
                      stable_named_sources=False, non_atomic=True,
                      scope='Explicit source paths, exact lexical owners, handoff/spec and HEAD; no test execution or complete classpath discovery'))
    observed, metadata_only, identities, parents, read_limits = {}, {}, {}, {}, {}
    total = 0
    incomplete = False
    review = not ready['ready']
    for name in tests['classes']:
        path = mapped[name]
        row = dict(**{'class': name}, method_selection=tests.get('methods', {}).get(name),
                   expected_tests=tests['expected_tests'][name], status='source_missing',
                   source=dict(path=str(path), bytes=None, sha256=None))
        report['selectors'].append(row)
        if path not in observed and path not in metadata_only:
            parent = path.parent
            while parent.is_relative_to(root):
                packet.canonical(parent)
                try:
                    parent_info = parent.lstat()
                except FileNotFoundError:
                    parent = parent.parent
                    continue
                packet.require(stat.S_ISDIR(parent_info.st_mode), 'Selected source parent must be a directory')
                parents.setdefault(parent, (parent_info.st_dev, parent_info.st_ino, parent_info.st_mode))
                parent = parent.parent
            try:
                info = path.lstat()
            except FileNotFoundError:
                info = None
            if info is not None:
                packet.require(stat.S_ISREG(info.st_mode), 'Selected Java source must be a regular file')
                identity = (info.st_dev, info.st_ino)
                packet.require(identity not in identities or identities[identity] == path,
                               'Distinct selected source paths are hardlink aliases')
                identities[identity] = path
                if info.st_size > limits.max_file_bytes or total + info.st_size > MAX_SELECTED_BYTES:
                    metadata_only[path] = (packet.signature(info), info.st_size)
                else:
                    limit = min(limits.max_file_bytes, MAX_SELECTED_BYTES - total)
                    try:
                        saved = _bounded_snapshot(path, limit)
                    except (packet.PacketError, OSError) as error:
                        row.update(status='source_incomplete', reason=str(error)[:MAX_MESSAGE])
                        incomplete = True
                        continue
                    if saved.info is not None and saved.data is None:
                        metadata_only[path] = (packet.signature(saved.info), saved.info.st_size)
                    else:
                        observed[path] = saved
                        read_limits[path] = limit
                        total += len(saved.data or b'')
            else:
                observed[path] = packet.Snapshot(path, None, None)
                read_limits[path] = min(limits.max_file_bytes, MAX_SELECTED_BYTES - total)
        if path in metadata_only:
            row['source']['bytes'] = metadata_only[path][1]
            row.update(status='source_incomplete', reason='Existing census file or selected aggregate byte budget exceeded; source content not read')
            incomplete = True
            continue
        saved = observed[path]
        if saved.info is None:
            row['reason'] = 'Explicit selected source is missing; generated/external class availability is unproved'
            review = True
            continue
        row['source'].update(bytes=len(saved.data), sha256=packet.digest(saved.data))
        try:
            declarations = census.inventory(saved.data, limits)
            types = [item for item in declarations if item['kind'] in ('class', 'interface', 'enum', 'record')]
            matches = [item for item in types
                       if '.'.join(part for part in (item['owner'], item['name']) if part) == name]
            if len(matches) != 1:
                row.update(status='owner_ambiguous' if matches else 'owner_not_observed',
                           reason='Require exactly one matching lexical named-type owner in this explicit source')
                review = True
                continue
            row.update(status='owner_matched', declaration=matches[0])
            hint, comparison = _hint(saved.data, limits, name, row['expected_tests'], row['method_selection'])
            row.update(test_inventory=hint, expected_count_check=comparison)
            review |= comparison['matches'] is False
        except census.CensusError as error:
            row.update(status='source_incomplete', reason=str(error)[:MAX_MESSAGE])
            incomplete = True
    report.update(source_count=len(observed) + len(metadata_only), selected_bytes_read=total)
    try:
        for saved in observed.values():
            _source_unchanged(saved, read_limits[saved.path])
        for path, (signature, _) in metadata_only.items():
            packet.canonical(path)
            packet.require(packet.signature(path.lstat()) == signature,
                           'Budget-limited selected source changed during observation')
        for parent, signature in parents.items():
            packet.canonical(parent)
            info = parent.lstat()
            packet.require((info.st_dev, info.st_ino, info.st_mode) == signature,
                           'Selected source parent changed during observation')
        _source_unchanged(spec, MAX_SPEC)
        again = handoff_summary(root, spec_path, installed=installed)
        packet.require(again == ready, 'Handoff readiness changed during selector observation')
        _, final_head = packet.worktree(root)
        packet.require(final_head == initial_head, 'HEAD changed during selector observation')
        report['head_after'] = final_head
        report['coverage']['stable_named_sources'] = (not metadata_only and len(observed) == len(set(mapped.values())))
    except (packet.PacketError, OSError) as error:
        report['observation_error'] = str(error)[:MAX_MESSAGE]
        incomplete = True
    report.update(exit_code=3 if incomplete else 1 if review else 0,
                  status='incomplete' if incomplete else 'needs_review' if review else 'source_owners_observed',
                  ready=not incomplete and not review)
    return report
