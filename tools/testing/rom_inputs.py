"""Discover verified original ROMs for queued test launches; never create or copy ROMs.

The category runner and the focused Maven queue share this owner, so both
identify the same user-supplied dumps by SHA-1 and report only property names.
Discovery reads the caller's worktree, an explicit directory, then the repository's
main checkout. Caller-supplied properties always win. Origin: 2026-11-03
validation simplification (ordinary focused tests without hand-assembled flags).
"""
import hashlib
import os
from pathlib import Path

ROM_PROPERTIES = {
    '6ddb7de1e17e7f6cdb88927bd906352030daa194': 'sonic1.rev00.rom.path',
    '14dd06fc3aa19a59a818ea1f6de150c9061b14d4': 'sonic2.rev00.rom.path',
    '69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b': 'sonic1.rom.path',
    '8bca5dcef1af3e00098666fd892dc1c2a76333f9': 'sonic2.rom.path',
    'cfbf98c36c776677290a872547ac47c53d2761d6': 's3k.rom.path',
    # User-supplied lock-on dump documented in the KiS2 fidelity-tier design.
    '6cd0537a3aee0e012bb86d5837ddff9342595004': 'kis2.rom.path',
}
# RomTestUtils prefers a system property over these variables, so an injected
# property would silently replace the caller's choice.
ROM_ENVIRONMENT = {'sonic1.rom.path': 'SONIC_1_ROM_PATH', 'sonic2.rom.path': 'SONIC_2_ROM_PATH',
                   's3k.rom.path': 'SONIC_3K_ROM_PATH'}
# Maven and JVM launch inputs that can define the same system properties.
LAUNCH_VARIABLES = ('MAVEN_ARGS', 'MAVEN_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')


def main_checkout(root):
    """Return the main checkout of a linked worktree root, or None.

    Reads Git's own worktree links. Only the repository's non-bare main
    checkout qualifies; submodules and other linked worktrees never do.
    """
    root = Path(root).resolve()
    try:
        link = (root / '.git').read_text().strip() if (root / '.git').is_file() else ''
        if not link.startswith('gitdir:'):
            return None
        metadata = (root / link.removeprefix('gitdir:').strip()).resolve()
        common = (metadata / (metadata / 'commondir').read_text().strip()).resolve()
    except OSError:
        return None
    main = common.parent
    return main if common.name == '.git' and common.is_dir() and main != root else None


def rom_args(root, rom_directory=None):
    """Name each verified ROM once: the caller's root wins, then the explicit directory, then main."""
    root = Path(root).resolve()
    directories = [root]
    if rom_directory is not None:
        rom_directory = Path(rom_directory).resolve()
        if not rom_directory.is_dir():
            raise ValueError(f'ROM directory does not exist or is not a directory: {rom_directory}')
        directories.append(rom_directory)
    main = main_checkout(root)
    if main is not None:
        directories.append(main)
    found, seen = {}, set()
    for directory in dict.fromkeys(directories):
        for path in sorted(directory.glob('*.gen')):
            resolved = path.resolve()
            if resolved in seen or not path.is_file():
                continue
            seen.add(resolved)
            with path.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha1').hexdigest()
            if digest in ROM_PROPERTIES:
                found.setdefault(ROM_PROPERTIES[digest], str(resolved))
    return [f'-D{key}={value}' for key, value in sorted(found.items())]


def caller_rom_properties(args, root, environment=None):
    """ROM properties the caller already chose, including an intentionally empty value.

    Any mention in a command token, launch variable or .mvn config counts, so
    argLine-embedded and unparseable definitions are never overridden.
    """
    environment = os.environ if environment is None else environment
    texts = [*args, *(environment.get(key, '') for key in LAUNCH_VARIABLES)]
    for name in ('maven.config', 'jvm.config'):
        path = Path(root) / '.mvn' / name
        if path.is_file():
            texts.append(path.read_text())
    return {key for key in ROM_PROPERTIES.values()
            if any(key in text for text in texts)
            or (key in ROM_ENVIRONMENT and ROM_ENVIRONMENT[key] in environment)}


def without_caller(arguments, caller):
    return [argument for argument in arguments if argument[2:].split('=', 1)[0] not in caller]


def rom_properties(arguments, caller=()):
    """Name discovered, caller-supplied and missing ROM properties; paths stay in commands."""
    found = sorted(argument[2:].split('=', 1)[0] for argument in arguments)
    return {'found': found, 'caller': sorted(caller),
            'missing': sorted(set(ROM_PROPERTIES.values()) - set(found) - set(caller))}


def describe(properties):
    return (f"ROM properties found={','.join(properties['found']) or 'none'} "
            f"caller={','.join(properties['caller']) or 'none'} "
            f"missing={','.join(properties['missing']) or 'none'}")
