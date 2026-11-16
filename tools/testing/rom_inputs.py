"""Discover verified original ROMs for queued test launches; never create or copy ROMs.

The category runner and the focused Maven queue share this owner, so both
identify the same user-supplied dumps by SHA-1 and report only property names.
Discovery reads the caller's worktree, an explicit directory, then the repository's
main checkout. Caller-supplied properties always win. Origin: 2026-11-03
validation simplification (ordinary focused tests without hand-assembled flags).

Sonic CD's disc is a CHD of a few hundred megabytes, too large to hash on every
queued launch. It is recognised by its CHD v5 header instead: the tag and the
header's raw-data SHA-1, which chdman computes over the decoded disc. That key
only selects the file; the runtime's whole-disc check against the USA release's
Redump record (CdHostedMedia.openVerified) stays the authority, so a CHD with a
forged header is still refused when a test opens it. Origin: 2026-11-15 default
Sonic CD disc for queued tests.
"""
import hashlib
import os
from pathlib import Path
import struct

ROM_PROPERTIES = {
    '6ddb7de1e17e7f6cdb88927bd906352030daa194': 'sonic1.rev00.rom.path',
    '14dd06fc3aa19a59a818ea1f6de150c9061b14d4': 'sonic2.rev00.rom.path',
    '69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b': 'sonic1.rom.path',
    '8bca5dcef1af3e00098666fd892dc1c2a76333f9': 'sonic2.rom.path',
    'cfbf98c36c776677290a872547ac47c53d2761d6': 's3k.rom.path',
    # User-supplied lock-on dump documented in the KiS2 fidelity-tier design.
    '6cd0537a3aee0e012bb86d5837ddff9342595004': 'kis2.rom.path',
}
# Sonic CD's disc by the CHD v5 header's raw-data SHA-1 (offset 0x40), never by
# name. The property names the .chd itself, as the cartridge properties name a .gen.
DISC_PROPERTIES = {
    # Sonic CD (USA), Redump 29188, as chdman v5 (cdlz/cdzl/cdfl).
    '8bcf26f53545dcd560ac9fd8db91766b977f1efd': 'soniccd.disc.path',
}
# A caller naming the regional media root chose the disc too: the tests prefer
# it, so injecting the CHD beside it would only make the report misleading.
CALLER_ALIASES = {'soniccd.media.root': 'soniccd.disc.path'}
CHD_TAG = b'MComprHD'
CHD_V5_HEADER_BYTES = 0x7C
CHD_V5_RAW_SHA1 = slice(0x40, 0x54)
# RomTestUtils prefers a system property over these variables, so an injected
# property would silently replace the caller's choice.
ROM_ENVIRONMENT = {'sonic1.rom.path': 'SONIC_1_ROM_PATH', 'sonic2.rom.path': 'SONIC_2_ROM_PATH',
                   's3k.rom.path': 'SONIC_3K_ROM_PATH'}
# Maven and JVM launch inputs that can define the same system properties.
LAUNCH_VARIABLES = ('MAVEN_ARGS', 'MAVEN_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')


def injected_properties():
    """Every property discovery can inject: cartridge ROMs and the Sonic CD disc."""
    return {*ROM_PROPERTIES.values(), *DISC_PROPERTIES.values()}


def chd_raw_sha1(path):
    """The raw-data SHA-1 a CHD v5 header records, or None for any other file.

    Reads only the 124-byte header; nothing is decompressed or hashed here.
    """
    with path.open('rb') as stream:
        header = stream.read(CHD_V5_HEADER_BYTES)
    if len(header) != CHD_V5_HEADER_BYTES or not header.startswith(CHD_TAG):
        return None
    length, version = struct.unpack('>II', header[8:16])
    if length != CHD_V5_HEADER_BYTES or version != 5:
        return None
    return header[CHD_V5_RAW_SHA1].hex()


# Disc image suffixes and how each is identified. A CUE sheet would need its
# track files hashed, so sheets are not discovered; name them with
# -Dsoniccd.disc.path or -Dsoniccd.media.root.
DISC_IDENTITIES = {'*.chd': chd_raw_sha1}


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
        for pattern, identity in DISC_IDENTITIES.items():
            for path in sorted(directory.glob(pattern)):
                resolved = path.resolve()
                if resolved in seen or not path.is_file():
                    continue
                seen.add(resolved)
                key = identity(path)
                if key in DISC_PROPERTIES:
                    found.setdefault(DISC_PROPERTIES[key], str(resolved))
    return [f'-D{key}={value}' for key, value in sorted(found.items())]


def caller_rom_properties(args, root, environment=None):
    """ROM properties the caller already chose, including an intentionally empty value.

    Any mention in a command token, launch variable or .mvn config counts, so
    argLine-embedded and unparseable definitions are never overridden. The
    caller's media root (CALLER_ALIASES) is reported under its own name.
    """
    environment = os.environ if environment is None else environment
    texts = [*args, *(environment.get(key, '') for key in LAUNCH_VARIABLES)]
    for name in ('maven.config', 'jvm.config'):
        path = Path(root) / '.mvn' / name
        if path.is_file():
            texts.append(path.read_text())
    return {key for key in (*injected_properties(), *CALLER_ALIASES)
            if any(key in text for text in texts)
            or (key in ROM_ENVIRONMENT and ROM_ENVIRONMENT[key] in environment)}


def covered(caller):
    """The injectable properties the caller's own choices already decide."""
    return {*caller, *(CALLER_ALIASES[key] for key in caller if key in CALLER_ALIASES)}


def without_caller(arguments, caller):
    chosen = covered(caller)
    return [argument for argument in arguments if argument[2:].split('=', 1)[0] not in chosen]


def rom_properties(arguments, caller=()):
    """Name discovered, caller-supplied and missing ROM properties; paths stay in commands."""
    found = sorted(argument[2:].split('=', 1)[0] for argument in arguments)
    return {'found': found, 'caller': sorted(caller),
            'missing': sorted(injected_properties() - set(found) - covered(caller))}


def describe(properties):
    return (f"ROM properties found={','.join(properties['found']) or 'none'} "
            f"caller={','.join(properties['caller']) or 'none'} "
            f"missing={','.join(properties['missing']) or 'none'}")
