#!/usr/bin/env python3
"""Verify one root MMD in a pinned original CUE without extracting any bytes.

Separate strict version-1 disc recipe; the existing binary CLI/schema is unchanged.
Linux /proc/self/fd is required for secure bound-descriptor RawIso reuse. Only the
declared CUE, data-track sectors and MMD are read; audio is not opened or hashed.
Supplied pins/bases do not authenticate region, source, physical media or gameplay.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import stat
import sys

import native_operand_check as check
from retail_profiles import parse_cue
from stage_inventory import BASE, Mmd, RawIso

MAX_CUE = 64 * 1024
MAX_MMD = 0x40000
MAX_ROOT_BYTES = 1024 * 1024
MAX_ROOT_ENTRIES = 4096
MAX_TRACK = 1024**3
MAX_LOGICAL_READ = 2048 + MAX_ROOT_BYTES + MAX_MMD
PROC_FD_ROOT = Path('/proc/self/fd')
MMD_NAME = re.compile(r'[A-Z0-9_]{1,8}\.MMD\Z', re.ASCII)


def _basename(value):
    check.require(type(value) is str and 0 < len(value) <= 255 and
                  value not in ('.', '..') and not any(ord(c) < 32 or ord(c) == 127 for c in value)
                  and not any(c in value for c in '/\\\0') and value == value.strip(),
                  'Require an exact root basename without aliases')
    try:
        value.encode('utf-8', errors='strict')
    except UnicodeError as error:
        raise check.CheckError('Invalid basename Unicode scalar value') from error
    return value


class _BoundIso(RawIso):
    """Reuse every ISO parser check, adding budgets before inherited allocation."""
    def __init__(self, fd, expected):
        self.expected = expected
        self.logical_bytes_read = 0
        descriptor = PROC_FD_ROOT / str(fd)
        check.require(descriptor.is_file(), 'Linux /proc/self/fd unavailable; no path reopen fallback', 3)
        super().__init__(descriptor)
        try:
            check.require(len(self.files) <= MAX_ROOT_ENTRIES, 'ISO root entry budget exceeded')
        except BaseException:
            self.stream.close()
            raise

    def read(self, offset, length):
        check.require(check.signature(os.fstat(self.stream.fileno())) == self.expected,
                      'Bound data descriptor changed', 3)
        check.require(0 <= length <= MAX_ROOT_BYTES, 'ISO read/root directory exceeds byte budget')
        check.require(self.logical_bytes_read + length <= MAX_LOGICAL_READ,
                      'ISO aggregate logical read budget exceeded')
        self.logical_bytes_read += length
        return super().read(offset, length)


def _recheck_source(snapshot):
    try:
        check._recheck(snapshot)
    except check.CheckError as error:
        raise check.CheckError('Original input or parent changed during inspection', 3) from error


def _cue_tracks(text):
    """Validate every declaration before the shared parser retains its track rows."""
    files, pending = [], False
    for line in text.splitlines():
        words = shlex.split(line)
        if not words or words[0] in ('REM', 'CATALOG'):
            continue
        if words[0] == 'FILE':
            check.require(not pending and len(words) == 3 and words[2] == 'BINARY',
                          'Each CUE FILE requires exactly one following TRACK')
            files.append(_basename(words[1]))
            pending = True
        elif words[0] == 'TRACK':
            check.require(pending, 'Each CUE TRACK requires its own FILE')
            pending = False
        elif words[0] == 'INDEX':
            check.require(not pending, 'CUE INDEX precedes its declared TRACK')
    check.require(not pending, 'Dangling CUE FILE without TRACK')
    tracks = parse_cue(text)
    check.require(files == [track['file'] for track in tracks], 'CUE FILE/TRACK ownership mismatch')
    return tracks


def verify(recipe_path, root):
    """Read-only observations from explicit source pins; no original-media discovery."""
    check.require(sys.platform.startswith('linux') and hasattr(os, 'O_NOFOLLOW'),
                  'Linux no-follow and /proc/self/fd required', 3)
    root = check.canonical(root, directory=True)
    root_id = check.signature(root.lstat())
    recipe = check._read(recipe_path, check.MAX_RECIPE)
    check.require(not recipe.path.is_relative_to(root), 'Disc recipe must be external to selected media root')
    spec = check.decode(recipe.data)
    check.keys(spec, ('version', 'cue', 'mmd', 'spans', 'literals', 'operands'), 'disc recipe')
    check.require(type(spec['version']) is int and spec['version'] == 1, 'Unsupported disc recipe version', 3)
    cue_pin, mmd_pin = spec['cue'], spec['mmd']
    check.keys(cue_pin, ('path', 'bytes', 'sha256'), 'cue')
    check.keys(mmd_pin, ('name', 'bytes', 'sha256'), 'mmd')
    cue_name = _basename(cue_pin['path'])
    check.require(cue_name.lower().endswith('.cue'), 'Expected explicit CUE basename')
    check.number(cue_pin['bytes'], 'cue bytes', 1, MAX_CUE)
    check.hash_value(cue_pin['sha256'], 'cue sha256')
    check.require(type(mmd_pin['name']) is str and MMD_NAME.fullmatch(mmd_pin['name']),
                  'Require exact uppercase root MMD basename, without ISO version suffix')
    check.number(mmd_pin['bytes'], 'mmd bytes', 0x100, MAX_MMD)
    check.hash_value(mmd_pin['sha256'], 'mmd sha256')
    cue = check._read(root / cue_name, MAX_CUE)
    check.require(len(cue.data) == cue_pin['bytes'] and check.digest(cue.data) == cue_pin['sha256'],
                  'CUE identity mismatch', 1)
    check.require((recipe.info.st_dev, recipe.info.st_ino) != (cue.info.st_dev, cue.info.st_ino),
                  'Recipe and CUE must be distinct files')
    try:
        tracks = _cue_tracks(cue.data.decode('utf-8'))
        names = [_basename(track['file']) for track in tracks]
        check.require(len({name.casefold() for name in names}) == len(names), 'CUE file aliases rejected')
        data_path = check.canonical(root / names[0])
        before = data_path.lstat()
        check.require(stat.S_ISREG(before.st_mode), 'Data track must be a regular file')
        check.require(0 < before.st_size <= MAX_TRACK, 'Data track exceeds supported byte extent')
        check.require(all((before.st_dev, before.st_ino) != (item.info.st_dev, item.info.st_ino)
                          for item in (recipe, cue)), 'Data track aliases another input')
        parent_ids = check.parents(data_path)
        saved = check.Snapshot(data_path, b'', before, parent_ids)
        fd = os.open(data_path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(fd, 'rb') as held:
            check.require(check.signature(os.fstat(held.fileno())) == check.signature(before),
                          'Data track changed before descriptor binding', 3)
            _recheck_source(saved)
            _recheck_source(cue)
            with _BoundIso(held.fileno(), check.signature(before)) as iso:
                iso_name = mmd_pin['name'] + ';1'
                data = iso.file(iso_name)
                check.require(len(data) == mmd_pin['bytes'] and check.digest(data) == mmd_pin['sha256'],
                              'MMD identity mismatch', 1)
                image = Mmd(data)
                prepared = check._prepare_checks(spec, len(data))
                observations = check._verify_checks(data, prepared)
                header = dict(image.header)
                extent = dict(iso.files[iso_name])
                logical_read = iso.logical_bytes_read
            check.require(check.signature(os.fstat(held.fileno())) == check.signature(before),
                          'Data descriptor changed after inspection', 3)
            for item in (saved, cue, recipe):
                _recheck_source(item)
            check.require(check.signature(root.lstat()) == root_id, 'Selected media root changed', 3)
    except check.CheckError:
        raise
    except ValueError as error:
        # Reader diagnostics can include untrusted names: emit no source text/bytes.
        if str(error) == 'Source data track changed during inspection':
            raise check.CheckError('Original data track changed during inspection', 3) from error
        raise check.CheckError('Unsupported or invalid CUE/ISO/MMD structure') from error
    except OSError as error:
        raise check.CheckError('Original disc input unavailable during inspection', 3) from error
    return dict(version=1, status='verified',
        recipe=dict(path=str(recipe.path), bytes=len(recipe.data), sha256=check.digest(recipe.data)),
        cue=dict(path=str(cue.path), bytes=len(cue.data), sha256=check.digest(cue.data), tracks=len(tracks)),
        data_track=dict(path=str(data_path), bytes=before.st_size, full_sha256_observed=False),
        mmd=dict(name=mmd_pin['name'], iso_name=iso_name, iso_lba=extent['lba'], bytes=len(data),
                 sha256=check.digest(data), header=header, file_offset_domain='ORIGINAL_WORD_RAM',
                 native_file_base=BASE, file_backed_copy_bytes=min(header['declared_copy_bytes'], len(data)-0x100)),
        **observations, runtime_or_source_authentication=False,
        provenance=dict(cue_pinned=True, full_named_mmd_pinned=True, audio_tracks_observed=False,
                        whole_data_track_hashed=False, whole_disc_authenticated=False,
                        logical_bytes_read=logical_read),
        limits=dict(max_cue_bytes=MAX_CUE, max_mmd_bytes=MAX_MMD, max_root_directory_bytes=MAX_ROOT_BYTES,
                    max_root_entries=MAX_ROOT_ENTRIES, max_data_track_bytes=MAX_TRACK,
                    max_logical_read_bytes=MAX_LOGICAL_READ, max_recipe_bytes=check.MAX_RECIPE,
                    max_checks=check.MAX_CHECKS, max_literal_bytes=check.MAX_LITERAL_BYTES),
        observation_limit='Non-atomic input/parent rechecks can miss change-and-restore. CUE shape and named MMD pins '
            'do not authenticate a whole disc, region, source, physical provenance, native control flow or runtime. '
            'Only original file-backed offsets are checked; unresolved copy tails supply no bytes. '
            'Indexed instructions are literal observations only; no extension/index decoding or CPU model.')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True, help='Canonical selected original retail CUE root')
    parser.add_argument('--recipe', required=True, help='Canonical external version-1 disc JSON recipe')
    args = parser.parse_args(argv)
    try:
        print(json.dumps(verify(args.recipe, args.root), sort_keys=True))
        return 0
    except check.CheckError as error:
        print(json.dumps(dict(version=1, status={1:'mismatch',2:'invalid',3:'incomplete'}[error.code],
                              error=str(error)[:300]), sort_keys=True), file=sys.stderr)
        return error.code
    except (OSError, ValueError) as error:
        print(json.dumps(dict(version=1, status='incomplete', error=type(error).__name__)), file=sys.stderr)
        return 3


if __name__ == '__main__':
    raise SystemExit(main())
