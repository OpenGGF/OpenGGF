#!/usr/bin/env python3
"""Offline ARB contribution tools. Java CatalogTool alone validates ICU grammar."""
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass, field
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ROOT = REPO_ROOT / 'src/main/resources/i18n'
MESSAGE_ID = re.compile(r'[A-Za-z][A-Za-z0-9_.-]*\Z')
HASH = re.compile(r'[0-9a-f]{64}\Z')
ORIGINS = {'human', 'claude-haiku-5-5'}
PLACEHOLDER_TYPES = {'String', 'string', 'int', 'long', 'double', 'num', 'number', 'Number'}


class CatalogError(ValueError):
    pass


def canonical_tag(value: str) -> str:
    """Structural language/script/region/variant tags; ICU owns locale negotiation."""
    parts = value.strip().split('-')
    if not parts or not re.fullmatch(r'[A-Za-z]{2,8}', parts[0]) or parts[0].lower() == 'und':
        raise CatalogError(f'Invalid language tag: {value!r}')
    language = parts.pop(0).lower()
    # Locale.Builder canonicalizes these legacy ISO spellings, independent of catalogs.
    language = {'iw': 'he', 'ji': 'yi', 'in': 'id'}.get(language, language)
    if len(language) <= 3:
        extlangs = []
        while parts and len(extlangs) < 3 and re.fullmatch(r'[A-Za-z]{3}', parts[0]):
            extlangs.append(parts.pop(0).lower())
        if extlangs:
            language = extlangs[0]
    result = [language]
    if parts and re.fullmatch(r'[A-Za-z]{4}', parts[0]):
        result.append(parts.pop(0).title())
    if parts and re.fullmatch(r'[A-Za-z]{2}|[0-9]{3}', parts[0]):
        result.append(parts.pop(0).upper())
    variants = set()
    for part in parts:
        if not re.fullmatch(r'[A-Za-z0-9]{5,8}|[0-9][A-Za-z0-9]{3}', part) or part.lower() in variants:
            raise CatalogError(f'Invalid or unsupported language tag: {value!r}; use language/script/region/variant subtags')
        variants.add(part.lower())
        result.append(part)
    return '-'.join(result)


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise CatalogError(f'Duplicate JSON key: {key}')
        result[key] = value
    return result


def _reject_constant(value):
    raise CatalogError(f'Invalid JSON constant: {value}')


def _unicode(value):
    if isinstance(value, str):
        value.encode('utf-8', errors='strict')
    elif isinstance(value, dict):
        for key, item in value.items():
            _unicode(key)
            _unicode(item)
    elif isinstance(value, list):
        for item in value:
            _unicode(item)


def read_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_bytes().decode('utf-8', errors='strict'),
                           object_pairs_hook=_pairs, parse_constant=_reject_constant)
        if not isinstance(value, dict):
            raise CatalogError('Catalog must be a JSON object')
        _unicode(value)
        return value
    except (OSError, UnicodeError, json.JSONDecodeError, CatalogError) as error:
        raise CatalogError(f'{path}: {error}') from error


def text_hash(pattern: str) -> str:
    return hashlib.sha256(pattern.encode('utf-8')).hexdigest()


def source_fingerprint(pattern: str, metadata: dict) -> str:
    contracts = sorted(name + ':' + item.get('type', 'String')
                       for name, item in metadata.get('placeholders', {}).items())
    return text_hash(pattern + '\n' + metadata.get('description', '') + '\n' + '\n'.join(contracts))


def _string(value, where, *, nonempty=False):
    if not isinstance(value, str) or (nonempty and not value.strip()):
        raise CatalogError(f'{where}: expected {"nonempty " if nonempty else ""}string')


def validate_document(path: Path, document: dict, *, source: bool):
    for key, value in document.items():
        where = f'{path}: {key}'
        if key.startswith('@@'):
            if key not in {'@@locale', '@@name', '@@quality', '@@parents', '@@description'}:
                raise CatalogError(f'{where}: unknown catalog metadata')
            if key == '@@parents':
                if not isinstance(value, list) or any(not isinstance(tag, str) for tag in value):
                    raise CatalogError(f'{where}: expected language-tag array')
                parents = [canonical_tag(tag) for tag in value]
                if parents != value or len(set(parents)) != len(parents):
                    raise CatalogError(f'{where}: expected unique canonical tags')
            else:
                _string(value, where, nonempty=True)
            continue
        if key.startswith('@'):
            message = key[1:]
            if message not in document or not MESSAGE_ID.fullmatch(message):
                raise CatalogError(f'{where}: metadata has no matching message')
            if not isinstance(value, dict):
                raise CatalogError(f'{where}: expected metadata object')
            if 'description' in value:
                _string(value['description'], where + '.description')
            if 'placeholders' in value:
                if not isinstance(value['placeholders'], dict):
                    raise CatalogError(f'{where}.placeholders: expected object')
                for name, item in value['placeholders'].items():
                    if not MESSAGE_ID.fullmatch(name) or not isinstance(item, dict):
                        raise CatalogError(f'{where}.placeholders: invalid placeholder {name}')
                if not isinstance(item.get('type', 'String'), str) or item.get('type', 'String') not in PLACEHOLDER_TYPES:
                        raise CatalogError(f'{where}.placeholders.{name}: unsupported type')
            stamp = value.get('x-openggf', {})
            if not isinstance(stamp, dict):
                raise CatalogError(f'{where}.x-openggf: expected object')
            if source:
                if 'critical' in stamp and not isinstance(stamp['critical'], bool):
                    raise CatalogError(f'{where}: critical must be boolean')
            else:
                for name in ('sourceHash', 'translationHash'):
                    if name in stamp and (not isinstance(stamp[name], str) or not HASH.fullmatch(stamp[name])):
                        raise CatalogError(f'{where}: {name} must be lowercase SHA-256')
                if 'origin' in stamp and (not isinstance(stamp['origin'], str) or stamp['origin'] not in ORIGINS):
                    raise CatalogError(f'{where}: origin must be human or claude-haiku-5-5')
                if 'reviewed' in stamp and not isinstance(stamp['reviewed'], bool):
                    raise CatalogError(f'{where}: reviewed must be boolean')
                if 'reviewer' in stamp:
                    _string(stamp['reviewer'], where + '.reviewer', nonempty=True)
            continue
        if not MESSAGE_ID.fullmatch(key):
            raise CatalogError(f'{where}: invalid message ID')
        _string(value, where)
    if document.get('@@locale') != 'en' and source:
        raise CatalogError(f'{path}: source @@locale must be en')
    if not source:
        for name in ('@@locale', '@@name', '@@quality'):
            _string(document.get(name), f'{path}: {name}', nonempty=True)
        if '\n' in document['@@name'] or '\r' in document['@@name']:
            raise CatalogError(f'{path}: @@name must fit one line')
        tag = canonical_tag(document['@@locale'])
        if tag == 'en' or tag != document['@@locale'] or path.name != tag + '.arb':
            raise CatalogError(f'{path}: canonical @@locale must match filename; English belongs in source/')


@dataclass(frozen=True)
class Source:
    pattern: str
    metadata: dict
    fingerprint: str


def load_source(root: Path) -> dict[str, Source]:
    messages = {}
    for path in sorted((root / 'source').rglob('*.arb')):
        document = read_json(path)
        validate_document(path, document, source=True)
        for message, pattern in document.items():
            if message.startswith('@'):
                continue
            if message in messages:
                raise CatalogError(f'{path}: duplicate source message {message}')
            metadata = document.get('@' + message, {})
            messages[message] = Source(pattern, metadata, source_fingerprint(pattern, metadata))
    if not messages:
        raise CatalogError(f'{root}: no English source messages')
    return messages


@dataclass
class Report:
    tag: str
    quality: str
    total: int
    usable: int = 0
    missing: int = 0
    stale: int = 0
    identical: int = 0
    unreviewed_critical: int = 0
    reviewed: int = 0
    review_invalidated: int = 0
    origins: Counter = field(default_factory=Counter)
    details: list[str] = field(default_factory=list)


def _target(path: Path, source: dict[str, Source]) -> dict:
    document = read_json(path)
    validate_document(path, document, source=False)
    for message in document:
        if not message.startswith('@') and message not in source:
            raise CatalogError(f'{path}: unknown message {message}')
    return document


def _report(path: Path, document: dict, source: dict[str, Source]) -> Report:
    report = Report(document['@@locale'], document['@@quality'], len(source))
    for message, original in source.items():
        pattern = document.get(message, '')
        stamp = document.get('@' + message, {}).get('x-openggf', {})
        if not pattern:
            state = 'missing'
        else:
            if 'origin' not in stamp:
                raise CatalogError(f'{path}: {message}: missing origin; stamp with an explicit --origin')
            report.origins[stamp['origin']] += 1
            current = stamp.get('sourceHash') == original.fingerprint
            reviewed = (stamp.get('reviewed') is True and current
                        and stamp.get('translationHash') == text_hash(pattern))
            if stamp.get('reviewed') is True and not reviewed:
                report.review_invalidated += 1
            if not current:
                state = 'stale'
            elif pattern == original.pattern:
                state = 'identical'
            elif original.metadata.get('x-openggf', {}).get('critical', False) and not reviewed:
                state = 'unreviewed_critical'
            else:
                state = 'usable'
                if reviewed:
                    report.reviewed += 1
        setattr(report, state, getattr(report, state) + 1)
        if state != 'usable':
            report.details.append(f'{message}: {state.replace("_", " ")}')
    return report


def inspect(root: Path) -> tuple[dict[str, Source], dict[str, Report]]:
    source = load_source(root)
    documents = {}
    for path in sorted((root / 'locales').rglob('*.arb')):
        if path.parent != root / 'locales':
            raise CatalogError(f'{path}: locale catalogs must be directly inside locales/')
        document = _target(path, source)
        documents[document['@@locale']] = (path, document)
    def parents(tag, seen):
        if tag == 'en':
            return
        if tag in seen:
            raise CatalogError(f'Locale fallback cycle: {" -> ".join([*seen, tag])}')
        if tag not in documents:
            raise CatalogError(f'Missing fallback locale: {tag}')
        for parent in documents[tag][1].get('@@parents', []):
            if parent != 'en' and parent.split('-')[0] != tag.split('-')[0]:
                raise CatalogError(f'Fallback crosses languages: {tag} -> {parent}')
            parents(parent, [*seen, tag])
    for tag in documents:
        parents(tag, [])
    return source, {tag: _report(path, document, source) for tag, (path, document) in documents.items()}


def write_json(path: Path, document: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent,
                                         prefix=path.name + '.', suffix='.tmp', delete=False) as handle:
            temporary = Path(handle.name)
            json.dump(document, handle, ensure_ascii=False, indent=2)
            handle.write('\n')
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def initialize(root: Path, tag: str, name: str):
    tag = canonical_tag(tag)
    if tag == 'en':
        raise CatalogError('English source messages belong in source/')
    _string(name, '--name', nonempty=True)
    if '\n' in name or '\r' in name:
        raise CatalogError('--name must fit one line')
    path = root / 'locales' / (tag + '.arb')
    if path.exists():
        raise CatalogError(f'{path}: already exists; no file changed')
    document = {'@@locale': tag, '@@name': name.strip(), '@@quality': 'community draft'}
    for message, original in sorted(load_source(root).items()):
        document[message] = ''
        document['@' + message] = {'x-openggf': {
            'sourceHash': original.fingerprint, 'origin': 'human', 'reviewed': False}}
    write_json(path, document)
    print(f'Created {path}; translate empty messages. No Java, ROM or registry change is needed.')


def stamp(root: Path, tag: str, ids, *, reviewer=None, origin=None):
    source = load_source(root)
    path = root / 'locales' / (canonical_tag(tag) + '.arb')
    document = _target(path, source)
    selected = ids if ids is not None else [message for message in source
                                         if message in document and (reviewer is None or document[message])]
    if reviewer is not None:
        _string(reviewer, '--reviewer', nonempty=True)
    for message in selected:
        if message not in source:
            raise CatalogError(f'Unknown source message: {message}')
        pattern = document.setdefault(message, '')
        metadata = document.setdefault('@' + message, {})
        provenance = metadata.setdefault('x-openggf', {})
        existing = provenance.get('origin')
        if origin is not None and existing is not None and origin != existing:
            raise CatalogError(f'{message}: preserve its existing {existing} origin; review does not replace provenance')
        if existing is None and origin is None:
            raise CatalogError(f'{message}: choose --origin human or claude-haiku-5-5')
        provenance['origin'] = existing or origin
        provenance['sourceHash'] = source[message].fingerprint
        provenance['reviewed'] = reviewer is not None
        provenance.pop('translationHash', None)
        provenance.pop('reviewer', None)
        if reviewer is not None:
            if not pattern:
                raise CatalogError(f'{message}: cannot review an empty translation')
            provenance['translationHash'] = text_hash(pattern)
            provenance['reviewer'] = reviewer.strip()
    write_json(path, document)
    print(f'{path}: {len(selected)} message(s) {"reviewed" if reviewer is not None else "stamped; previous review invalidated"}.')


def show(source, reports, tag=None, details=False):
    print(f'English source: {len(source)} messages. Coverage excludes missing, stale and English-identical entries.')
    if tag is not None:
        tag = canonical_tag(tag)
        if tag not in reports:
            raise CatalogError(f'No locale catalog: {tag}')
        reports = {tag: reports[tag]}
    for report in reports.values():
        print(f'{report.tag}: {report.usable}/{report.total} current translated; {report.missing} missing, '
              f'{report.stale} stale, {report.identical} English-identical, '
              f'{report.unreviewed_critical} critical awaiting review; {report.reviewed} reviewed, '
              f'{report.review_invalidated} invalidated reviews; quality={report.quality}; '
              f'origins={dict(report.origins)}')
        if details:
            for item in report.details:
                print('  ' + item)


def runtime_check(root: Path) -> int:
    # Keep Maven admission/build isolation centralized. No shell, credentials or online translator.
    argument = str(root.resolve()).replace('"', '\\"')
    command = [sys.executable, str(REPO_ROOT / 'tools/testing/maven_queue.py'), '-Dmse=off',
               'compile', 'exec:java', '-Dexec.mainClass=com.openggf.i18n.CatalogTool',
               '-Dexec.args="' + argument + '"']
    return subprocess.run(command, cwd=REPO_ROOT, check=False).returncode


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=DEFAULT_ROOT, help='i18n resource directory')
    commands = parser.add_subparsers(dest='command', required=True)
    for name in ('init', 'check', 'status', 'stamp', 'review'):
        command = commands.add_parser(name)
        command.add_argument('--root', type=Path, default=argparse.SUPPRESS)
        if name in ('init', 'stamp', 'review'):
            command.add_argument('tag')
        elif name == 'status':
            command.add_argument('tag', nargs='?')
        if name == 'init':
            command.add_argument('--name', required=True, help='language endonym')
        if name in ('check', 'status'):
            command.add_argument('--details', action='store_true', help='show missing/stale IDs')
        if name == 'check':
            command.add_argument('--runtime', action='store_true', help='compile and run the queued authoritative Java/ICU checker')
        if name in ('stamp', 'review'):
            command.add_argument('--ids', nargs='+', help='selected message IDs; default is all existing entries')
        if name == 'stamp':
            command.add_argument('--origin', choices=sorted(ORIGINS), help='required only when importing entries without provenance')
        if name == 'review':
            command.add_argument('--reviewer', required=True, help='person who checked the exact translation against current source')
    args = parser.parse_args(argv)
    try:
        root = args.root.resolve()
        if args.command == 'init':
            initialize(root, args.tag, args.name)
        elif args.command in ('stamp', 'review'):
            stamp(root, args.tag, args.ids, reviewer=getattr(args, 'reviewer', None), origin=getattr(args, 'origin', None))
        else:
            source, reports = inspect(root)
            show(source, reports, getattr(args, 'tag', None), args.details)
            if args.command == 'check':
                print('Quick checks passed. ICU syntax, argument equivalence and locale plural rules require --runtime.', flush=True)
                if args.runtime:
                    return runtime_check(root)
        return 0
    except (CatalogError, OSError) as error:
        print(f'Catalog error: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
