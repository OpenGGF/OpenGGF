#!/usr/bin/env python3
"""Check and carry disassembly line-number citations in OpenGGF sources.

Purpose: engine comments, tests and Markdown cite the disassembly submodules by
line number (``s2.asm:76942-76956``, ``sonic3k.asm lines 60377-60427``,
``_incObj/18 Platforms.asm:54-67``, or a bare ``:199033-199037`` that inherits
the last explicit file named in the same source file). Those numbers drift when
a disassembly gains comments or labels. This tool:

* ``check``  verifies every citation against one disassembly revision (default:
  the superproject's pinned gitlink): the file must exist, the lines must exist,
  and when a disassembly label is named just before the citation, that label
  must sit in or just before the cited range.
* ``remap``  carries citations from revision A to revision B of one submodule
  through the line mapping of ``git diff A B``. Citations touching changed
  lines are reported, never guessed. Run it in the same commit that moves a
  submodule pin, so citations follow the content.

Inputs: the superproject (cwd or --repo); disassembly history is read from git
objects, so a linked worktree whose submodules are not initialised still works
(the main checkout's submodule repositories are used).
Outputs: a report on stdout (``--json`` for machine use); ``remap --write``
edits citing files in place, preserving line endings.
Origin: citation-audit task, 2026-10-09 (feature/ai-disasm-citation-check).
"""
import argparse
import collections
import json
import os
import re
import subprocess
import sys
from pathlib import Path

DEFAULT_SCAN = ('src/main/java', 'src/test/java', 'docs', '.agents', '.claude', 'tools')
SKIP_PREFIXES = ('docs/s1disasm/', 'docs/s2disasm/', 'docs/skdisasm/', 'docs/kis2disasm/', 'docs/scddisasm/')
SCAN_SUFFIXES = ('.java', '.md', '.py', '.lua', '.ps1', '.sh', '.json')
LINE_COMMENT = {'.py': '#', '.sh': '#', '.ps1': '#', '.lua': '--'}

# file.asm:N[-M][, N[-M]...]   or   file.asm line(s) N[-M]. The path is read backwards
# from ".asm" (S1 names contain spaces, e.g. "_incObj/18 Platforms.asm"); the longest
# candidate that resolves to one disassembly file wins.
RANGE = r'(?P<a>\d{1,6})(?:\s*[-\u2013]\s*(?P<b>\d{1,6}))?'
ASM_AT = re.compile(r'\.asm(?::|,?\s+lines?\s+)' + RANGE)
NAME_PREFIX = re.compile(r'^(?:[0-9A-F]{1,2},?|sub|&)$')
PATH_TAIL = re.compile(r'[\w.&+/,-]+(?: [\w&+,-][\w.&+/,-]*)*$')
MORE = re.compile(r'\s*,\s*' + RANGE)
LISTING = re.compile(r'[\w.-]+\.lst(?::|,?\s+lines?\s+)\d+')
BARE = re.compile(r'(?:(?<=[\s(])|^):' + RANGE + r'\b')
LABEL_DEF = re.compile(r'^([A-Za-z_][\w.]*):')
ADDR_ALIAS = re.compile(r':\s*;\s*((?:loc|sub|locret|word|byte)_[0-9A-F]{4,6})\b')
IDENT = re.compile(r'[A-Za-z_][\w.]*')
GAME_HINTS = {'s1disasm': ('sonic1', 's1-', '/s1', 'sonic 1', 's1disasm'),
              's2disasm': ('sonic2', 's2-', '/s2', 'sonic 2', 's2disasm'),
              'skdisasm': ('sonic3k', 's3k', 'skdisasm', 'sonic 3')}
ANCHOR_WINDOW = 120   # characters before the citation searched for a label name
ROUTINE_SPAN = 250    # a range may sit this many lines inside the named label's code


def run(args, cwd=None, check=True):
    p = subprocess.run(args, cwd=cwd, capture_output=True, text=True, errors='replace')
    if check and p.returncode:
        raise RuntimeError(f'{" ".join(args)}: {p.stderr.strip()[:300]}')
    return p


class Superproject:
    def __init__(self, root):
        self.root = Path(run(['git', 'rev-parse', '--show-toplevel'], cwd=root).stdout.strip())
        self.subs = self._submodules()

    def _submodules(self):
        out = {}
        p = run(['git', 'config', '-f', '.gitmodules', '--get-regexp', r'^submodule\..*\.path$'],
                cwd=self.root, check=False)
        for line in p.stdout.splitlines():
            path = line.split(None, 1)[1].strip()
            name = path.rsplit('/', 1)[-1]
            if name.endswith('disasm'):
                out[name] = Disasm(self, name, path)
        return out

    def main_worktree(self):
        p = run(['git', 'worktree', 'list', '--porcelain'], cwd=self.root)
        first = p.stdout.split('\n', 1)[0]
        return Path(first.split(' ', 1)[1]) if first.startswith('worktree ') else self.root

    def pinned(self, path, rev='HEAD'):
        p = run(['git', 'ls-tree', rev, path], cwd=self.root, check=False)
        parts = p.stdout.split()
        return parts[2] if len(parts) >= 3 and parts[1] == 'commit' else None

    def tracked(self, scan):
        p = run(['git', 'ls-files', '-z', '--', *scan], cwd=self.root)
        return [f for f in p.stdout.split('\0') if f and f.endswith(SCAN_SUFFIXES)
                and not f.startswith(SKIP_PREFIXES)]


class Disasm:
    def __init__(self, sp, name, path):
        self.sp, self.name, self.path = sp, name, path
        self._gitdir = None
        self._files, self._trees, self._renames = {}, {}, None

    def gitdir(self):
        if self._gitdir is None:
            env = os.environ.get('OPENGGF_' + self.name.upper() + '_GIT')
            candidates = [Path(env)] if env else []
            candidates += [self.sp.root / self.path, self.sp.main_worktree() / self.path]
            common = Path(run(['git', 'rev-parse', '--git-common-dir'], cwd=self.sp.root).stdout.strip())
            if not common.is_absolute():
                common = self.sp.root / common
            candidates.append(common / 'modules' / self.path)
            for c in candidates:
                if (c / '.git').exists() or (c / 'HEAD').exists() and (c / 'objects').exists():
                    p = run(['git', '-C', str(c), 'rev-parse', '--git-dir'], check=False)
                    if p.returncode == 0:
                        self._gitdir = str(c)
                        break
            else:
                self._gitdir = ''
        return self._gitdir or None

    def git(self, *args, check=True):
        return run(['git', '-C', self.gitdir(), *args], check=check)

    def resolve_rev(self, rev):
        if rev in (None, 'pinned'):
            return self.sp.pinned(self.path)
        if rev == 'checkout':
            return self.git('rev-parse', 'HEAD').stdout.strip()
        return self.git('rev-parse', '--verify', rev + '^{commit}').stdout.strip()

    def has(self, rev):
        return self.git('cat-file', '-e', rev + '^{commit}', check=False).returncode == 0

    def renamed_to(self, rel, rev):
        """Current name of a file that renames moved away from, if git history records it."""
        if self._renames is None:
            self._renames = {}
            p = self.git('log', '--all', '--format=', '--name-status', '-M', '--diff-filter=R', check=False)
            for line in p.stdout.splitlines():
                parts = line.split('\t')
                if len(parts) == 3:
                    self._renames.setdefault(parts[1], parts[2])
        seen, cur = set(), rel
        while cur in self._renames and cur not in seen:
            seen.add(cur)
            cur = self._renames[cur]
            if cur in self.tree(rev):
                return cur
        return None

    def tree(self, rev):
        if rev not in self._trees:
            self._trees[rev] = self.git('ls-tree', '-r', '--name-only', rev).stdout.splitlines()
        return self._trees[rev]

    def lines(self, rev, rel):
        """(lines, labels) of a file at rev, or (None, None) if absent."""
        key = (rev, rel)
        if key not in self._files:
            p = self.git('show', f'{rev}:{rel}', check=False)
            if p.returncode:
                self._files[key] = (None, None)
            else:
                text = p.stdout.splitlines()
                labels = {}
                for i, t in enumerate(text, 1):
                    m = LABEL_DEF.match(t)
                    if m:
                        labels.setdefault(m.group(1), i)
                        alias = ADDR_ALIAS.search(t)
                        if alias:
                            labels.setdefault(alias.group(1), i)
                self._files[key] = (text, labels)
        return self._files[key]


Citation = collections.namedtuple('Citation', 'file line spans sub rel a b kind anchor text')
# spans: [(start, end)] character spans of the number(s) a and b in the source line


def game_hint(path):
    low = path.lower()
    return [s for s, hints in GAME_HINTS.items() if any(h in low for h in hints)]


class Resolver:
    """Maps a cited path to (submodule, path inside it) at the reference revisions.

    Ambiguous names (one basename in several directories or disassemblies, after
    using the citing file's game as a hint) stay unresolved rather than guessed.
    """

    def __init__(self, sp, revs):
        self.sp, self.revs, self.cache = sp, revs, {}

    def _hits(self, p, pred):
        return [(name, f) for name, d in self.sp.subs.items() if self.revs.get(name)
                for f in d.tree(self.revs[name]) if pred(f)]

    def __call__(self, cited, source, loose=True):
        """loose: also accept S1 "NN Name.asm" partial names and case-insensitive names."""
        key = (cited, tuple(game_hint(source)), loose)
        if key in self.cache:
            return self.cache[key]
        p = cited.replace('\\', '/').lstrip('./')
        p = re.sub(r'^docs/', '', p)
        result = (None, p)
        m = re.match(r'(\w*disasm)/(.*)', p)
        if m and m.group(1) in self.sp.subs:
            result = (m.group(1), m.group(2))
        else:
            p = p.lstrip(', ')
            partial = (lambda f: '/' not in p and f.rsplit('/', 1)[-1].endswith(' ' + p))
            hits = self._hits(p, lambda f: f == p or f.endswith('/' + p) or (loose and partial(f)))
            if not loose and self._hits(p, partial):
                hits = []   # a shortened name that also ends another file's name is ambiguous
            if not hits and loose:
                low = p.lower()
                hits = self._hits(p, lambda f: f.lower() == low or f.lower().endswith('/' + low))
            if len({h[0] for h in hits}) > 1:
                hinted = [h for h in hits if h[0] in game_hint(source)]
                hits = hinted or hits
            exact = [h for h in hits if h[1] == p or h[1].endswith('/' + p)]
            if len(hits) > 1 and len(exact) == 1:
                hits = exact
            if len(hits) == 1:
                result = hits[0]
        self.cache[key] = result
        return result


def is_comment_context(source, text, pos):
    """True when text[pos] is inside a comment (Java, scripts) or anywhere in Markdown/JSON."""
    if source.endswith(('.md', '.json')):
        return True
    if source.endswith('.java'):
        s = text.lstrip()
        return s.startswith(('//', '*', '/*')) or '//' in text[:pos] or '/*' in text[:pos]
    marker = LINE_COMMENT.get(Path(source).suffix)
    return bool(marker) and marker in text[:pos]


def parse_file(source, content, resolve):
    out, context = [], None
    markdown = source.endswith('.md')
    for n, text in enumerate(content.splitlines(), 1):
        taken = []
        if markdown and text.startswith('#'):
            context = None   # a bare :N-M inherits its file only within one Markdown section
        for m in LISTING.finditer(text):
            if is_comment_context(source, text, m.start()):
                out.append(Citation(source, n, [], None, m.group(0), 0, 0, 'generated', [], text))
        for m in (ASM_AT.finditer(text) if '.asm' in text else ()):
            tail = PATH_TAIL.search(text[max(0, m.start() - 160):m.start()])
            if not tail or not is_comment_context(source, text, m.start()):
                continue
            words = tail.group(0).split(' ')
            sub, rel = None, ' '.join(words) + '.asm'
            for i in range(len(words)):
                if i and NAME_PREFIX.match(words[i - 1]):
                    continue   # never strip an S1 object-number prefix such as "11" or "sub"
                sub, rel = resolve(' '.join(words[i:]) + '.asm', source, loose=(i == 0))
                if sub:
                    break
            start = m.start() - len(tail.group(0))
            context = (sub, rel) if sub else context
            ranges = [(m.start('a'), m.end('a'), m.start('b'), m.end('b'), m.group('a'), m.group('b'))]
            end = m.end()
            while True:
                more = MORE.match(text, end)
                if not more or (end < len(text) and text[more.end():more.end() + 1] in ':.' and
                                text[more.end():more.end() + 2].strip(':.').isdigit()):
                    break
                ranges.append((more.start('a'), more.end('a'), more.start('b'), more.end('b'),
                               more.group('a'), more.group('b')))
                end = more.end()
            for sa, ea, sb, eb, a, b in ranges:
                taken.append((sa, eb if b else ea))
                out.append(_cite(source, n, text, sub, rel, sa, ea, sb, eb, a, b, 'explicit', start))
        if context and context[0] and ':' in text and not source.endswith('.json'):
            for m in BARE.finditer(text):
                if any(s <= m.start() < e or m.start() == e for s, e in taken):
                    continue
                if not is_comment_context(source, text, m.start()):
                    continue
                out.append(_cite(source, n, text, context[0], context[1], m.start('a'), m.end('a'),
                                 m.start('b'), m.end('b'), m.group('a'), m.group('b'), 'bare', m.start()))
    return out


def _cite(source, n, text, sub, rel, sa, ea, sb, eb, a, b, kind, at):
    spans = [(sa, ea)] + ([(sb, eb)] if b else [])
    before = text[max(0, at - ANCHOR_WINDOW):at]
    return Citation(source, n, spans, sub, rel, int(a), int(b or a), kind, IDENT.findall(before), text)


ADDRESS_LABEL = re.compile(r'^(?:loc|sub|locret)_[0-9A-F]{4,6}$')


def anchor_status(c, lines, labels):
    """('none'|'ok'|'near'|'mismatch', label, label_line) for the nearest named label.

    If the nearest address-style name (loc_XXXX) is not a label at this revision
    (a fork renamed it without keeping the address), the citation is not judged
    by some farther label: that would report false matches.
    """
    for ident in reversed(c.anchor):
        if ident not in labels and ADDRESS_LABEL.match(ident):
            return 'none', None, None
        if ident in labels:
            at = labels[ident]
            if c.a - 3 <= at <= c.b:
                return 'ok', ident, at
            if at <= c.a and c.b - at <= ROUTINE_SPAN:
                return 'near', ident, at
            return 'mismatch', ident, at
    return 'none', None, None


def scan(sp, revs, scan_paths):
    resolve = Resolver(sp, revs)
    protected = protected_prefixes(sp.root)
    cites = []
    for f in sp.tracked(scan_paths):
        try:
            raw = (sp.root / f).read_bytes()
        except OSError:
            continue
        content = raw.decode('utf-8', errors='replace')
        if ':' not in content and 'line' not in content:
            continue
        found = parse_file(f, content, resolve)
        if f in protected:
            # lines starting inside the hook-pinned prefix are history, written against older revisions
            last = raw[:protected[f]].count(b'\n') + (0 if raw[:protected[f]].endswith(b'\n') else 1)
            found = [c._replace(kind='historic') if c.line < last or (c.line == last and raw[:protected[f]].endswith(b'\n')) else c
                     for c in found]
        cites.extend(found)
    return cites


def reference_revs(sp, rev_arg, need_objects=True):
    revs, problems = {}, []
    for name, d in sp.subs.items():
        if not d.gitdir():
            problems.append(f'{name}: no local repository (git submodule update --init {d.path})')
            continue
        want = rev_arg.get(name, rev_arg.get('*', 'pinned'))
        try:
            rev = d.resolve_rev(want)
        except RuntimeError:
            rev = d.sp.pinned(d.path) if want == 'pinned' else want
        if rev and need_objects and not d.has(rev):
            problems.append(f'{name}: revision {rev[:12]} not present; run '
                            f'git -C {d.gitdir()} fetch origin {rev}')
            continue
        revs[name] = rev
    return revs, problems


HARD = ('missing-file', 'out-of-range')


def classify(sp, revs, c):
    """Status of one citation at the reference revisions, with a detail string.

    ok            a named label sits in or just before the cited range
    near          the range lies within ROUTINE_SPAN lines after the named label (plausible,
                  but a shift inside the routine would go unnoticed)
    stale         a named label exists but is elsewhere (the numbers drifted or are wrong)
    unverifiable  the lines exist but no label is named next to the citation
    generated     cites an assembler listing (.lst), which no revision contains
    historic      inside a hook-protected append-only history; written against an older revision
    missing-file / out-of-range   hard errors; unresolved-file / no-reference   not checked
    """
    if c.kind == 'historic':
        return 'historic', 'inside a hook-protected historic prefix; not judged'
    if c.kind == 'generated':
        return 'generated', f'{c.rel}: listing files are build output; cite the .asm line instead'
    if not c.sub:
        return 'unresolved-file', f'cannot resolve {c.rel!r} to one disassembly file'
    if c.sub not in revs:
        return 'no-reference', f'{c.sub} has no reference revision'
    d = sp.subs[c.sub]
    lines, labels = d.lines(revs[c.sub], c.rel)
    bad_range = lines is not None and (c.a < 1 or c.a > c.b or c.b > len(lines))
    if c.kind == 'bare' and (lines is None or bad_range):
        return 'unverifiable', f'bare citation; {c.rel} (inherited) does not fit'
    if lines is None:
        new = d.renamed_to(c.rel, revs[c.sub])
        return 'missing-file', f'{c.sub}/{c.rel} absent at {revs[c.sub][:12]}' + (f'; renamed to {new!r}' if new else '')
    if bad_range:
        return 'out-of-range', f'{c.a}-{c.b} outside 1-{len(lines)}'
    status, label, at = anchor_status(c, lines, labels)
    if status in ('ok', 'near'):
        return status, label
    if status == 'mismatch':
        return 'stale', f'{label} is at line {at}'
    return 'unverifiable', ''


def cmd_check(args):
    sp = Superproject(args.repo)
    revs, problems = reference_revs(sp, parse_rev_args(args.rev))
    for p in problems:
        print('warning:', p, file=sys.stderr)
    for name, d in sp.subs.items():
        if d.gitdir() and name in revs:
            head = d.resolve_rev('checkout')
            if head != revs[name]:
                print(f'warning: {name} checkout {head[:12]} differs from the reference {revs[name][:12]}; '
                      f'line numbers you read locally will not match this check', file=sys.stderr)
    cites = scan(sp, revs, args.paths or DEFAULT_SCAN)
    findings, counts = [], collections.Counter()
    for c in cites:
        status, detail = classify(sp, revs, c)
        counts[status] += 1
        if status in HARD or status == 'stale' or (args.verbose and status in ('unresolved-file', 'generated')):
            findings.append((status, c, detail))
    if args.json:
        print(json.dumps({'reference': revs, 'counts': counts, 'findings': [
            {'kind': k, 'file': c.file, 'line': c.line, 'sub': c.sub, 'path': c.rel, 'a': c.a, 'b': c.b,
             'detail': d} for k, c, d in findings]}, indent=1))
    else:
        for k, c, d in findings[:args.limit or None]:
            print(f'{c.file}:{c.line}: {k}: {c.sub}/{c.rel}:{c.a}-{c.b}: {d}')
        print('reference:', ', '.join(f'{k}={v[:12]}' for k, v in revs.items()))
        print('summary:', ', '.join(f'{k}={v}' for k, v in sorted(counts.items())))
    hard = sum(counts[k] for k in HARD) + (counts['stale'] if args.strict else 0)
    return 1 if hard else 0


class LineMap:
    """Line mapping old->new for one file, from ``git diff -U0`` hunks.

    A hunk ``-os,oc +ns,nc`` replaces old lines os..os+oc-1; when oc == 0 it
    inserts after old line os. An old line outside every hunk moves by the net
    size change of the hunks before it; a line inside a hunk has no mapping.
    """

    def __init__(self, hunks, old_lines=None, new_lines=None):
        self.hunks = hunks          # [(old_start, old_count, new_start, new_count)]
        self.old, self.new = old_lines or [], new_lines or []

    def map_range(self, a, b, carry_edited=False):
        """(new_a, new_b), or None if any line of a..b changed or lines were inserted inside it.

        carry_edited: also carry a range across in-place edits, i.e. hunks that replace
        each line with one line of the same kind (same mnemonic, or both labels/comments),
        such as an upstream rename of a constant. Insertions and deletions still refuse.
        """
        shift = {}
        for os_, oc, ns, nc in self.hunks:
            if (oc and os_ <= b and a <= os_ + oc - 1) or (not oc and a <= os_ < b):
                if not (carry_edited and oc == nc and self._in_place(os_, ns, oc)):
                    return None
                for k in range(oc):
                    shift[os_ + k] = ns + k
        return shift.get(a) or self.map(a), shift.get(b) or self.map(b)

    def _in_place(self, os_, ns, count):
        return all(line_kind(self.old[os_ + k - 1]) == line_kind(self.new[ns + k - 1])
                   for k in range(count) if os_ + k - 1 < len(self.old) and ns + k - 1 < len(self.new))

    def map(self, n):
        delta = 0
        for os_, oc, ns, nc in self.hunks:
            if oc and os_ <= n < os_ + oc:
                return None
            if (oc and os_ + oc - 1 < n) or (not oc and os_ < n):
                delta += nc - oc
        return n + delta


def line_kind(line):
    """Shape of an assembly line for in-place comparison: mnemonic, 'label', 'comment' or ''."""
    code = line.split(';', 1)[0]
    if not code.strip():
        return 'comment' if line.strip() else ''
    if LABEL_DEF.match(code):
        return 'label'
    return code.split()[0].lower().split('.', 1)[0]


def file_linemap(d, a, b, rel):
    p = d.git('diff', '--no-ext-diff', '--no-color', '--ignore-cr-at-eol', '-U0', a, b, '--', rel, check=False)
    hunks = []
    for line in p.stdout.splitlines():
        m = re.match(r'^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@', line)
        if m:
            hunks.append((int(m.group(1)), int(m.group(2) or 1), int(m.group(3)), int(m.group(4) or 1)))
    return LineMap(hunks, d.lines(a, rel)[0], d.lines(b, rel)[0])


def renamed_path(d, a, b, rel):
    if d.lines(b, rel)[0] is not None:
        return rel
    p = d.git('diff', '--name-status', '-M', a, b, check=False)
    for line in p.stdout.splitlines():
        parts = line.split('\t')
        if parts[0].startswith('R') and parts[1] == rel:
            return parts[2]
    return None


def cmd_remap(args):
    sp = Superproject(args.repo)
    if args.sub not in sp.subs:
        sys.exit(f'unknown disassembly {args.sub}; known: {", ".join(sp.subs)}')
    d = sp.subs[args.sub]
    a, b = d.resolve_rev(args.src), d.resolve_rev(args.dst)
    for r in (a, b):
        if not d.has(r):
            sys.exit(f'{r[:12]} not present; run git -C {d.gitdir()} fetch origin {r}')
    revs = {args.sub: a}
    every = [c for c in scan(sp, revs, args.paths or DEFAULT_SCAN) if c.sub == args.sub]
    cites = [c for c in every if c.kind != 'historic' and not c.file.endswith('.json')]
    maps, edits, report = {}, collections.defaultdict(list), collections.Counter()
    historic = sum(1 for c in every if c.kind == 'historic')
    if historic:
        report['protected-history (left as written)'] = historic
    evidence = sum(1 for c in every if c.kind != 'historic' and c.file.endswith('.json'))
    if evidence:
        # JSON evidence may be pinned by hash (e.g. reviewed capture manifests): check it, never rewrite it
        report['json-evidence (left as written)'] = evidence
    unmapped = []
    for c in cites:
        if c.rel not in maps:
            new_rel = renamed_path(d, a, b, c.rel)
            maps[c.rel] = (new_rel, file_linemap(d, a, b, c.rel) if new_rel else None)
        new_rel, lm = maps[c.rel]
        if lm is None:
            report['file-gone'] += 1
            unmapped.append((c, f'{c.rel} absent at {b[:12]}'))
            continue
        if new_rel != c.rel:
            report['file-renamed'] += 1
            unmapped.append((c, f'{c.rel} renamed to {new_rel}; edit the path by hand'))
            continue
        mapped = lm.map_range(c.a, c.b)
        if mapped is None and args.carry_edited:
            mapped = lm.map_range(c.a, c.b, carry_edited=True)
            if mapped is not None:
                report['carried-across-in-place-edits'] += 1
        if mapped is None:
            report['touches-changed-lines'] += 1
            unmapped.append((c, f'{c.a}-{c.b} touches lines changed between revisions'))
            continue
        na, nb = mapped
        if (na, nb) == (c.a, c.b):
            report['unchanged'] += 1
            continue
        report['moved'] += 1
        new = [str(na)] + ([str(nb)] if len(c.spans) > 1 else [])
        edits[c.file].append((c.line, c.spans, new))
    for c, why in unmapped:
        print(f'{c.file}:{c.line}: needs review: {c.sub}/{c.rel}:{c.a}-{c.b}: {why}')
    if args.write:
        protected = protected_prefixes(sp.root)
        for f, items in edits.items():
            skipped = apply_edits(sp.root / f, items, protected.get(f, 0))
            if skipped:
                report['protected-history (left as written)'] += skipped
                print(f'{f}: {skipped} cited lines are in the hook-protected historic prefix; left unchanged')
    print(f'{args.sub} {a[:12]} -> {b[:12]}:', ', '.join(f'{k}={v}' for k, v in sorted(report.items())),
          '(written)' if args.write else '(dry run; --write to apply)')
    return 1 if unmapped and args.strict else 0


PROTECTED_LIST = '.githooks/machine-local-path-grandfather.sha256'


def protected_prefixes(root):
    """{path: byte length} of append-only histories whose prefix a commit hook pins by hash."""
    out = {}
    try:
        for line in (root / PROTECTED_LIST).read_text().splitlines():
            parts = line.split('\t')
            if len(parts) >= 4 and parts[0] == '# baseline-prefix' and parts[1].isdigit():
                out[parts[3]] = int(parts[1])
    except OSError:
        pass
    return out


def apply_edits(path, items, protected=0):
    """Rewrite cited numbers in place; lines starting inside a protected byte prefix are kept.

    Returns the number of edited lines skipped because they are protected history.
    """
    raw = path.read_bytes()
    text = raw.decode('utf-8', errors='surrogateescape')
    lines = text.splitlines(keepends=True)
    starts, offset = [], 0
    for line in lines:
        starts.append(offset)
        offset += len(line.encode('utf-8', errors='surrogateescape'))
    by_line = collections.defaultdict(list)
    for line, spans, new in items:
        by_line[line].extend(zip(spans, new))
    skipped = sum(1 for line in by_line if starts[line - 1] < protected)
    by_line = {k: v for k, v in by_line.items() if starts[k - 1] >= protected}
    for line, reps in by_line.items():
        s = lines[line - 1]
        for (start, end), value in sorted(reps, key=lambda r: -r[0][0]):
            s = s[:start] + value + s[end:]
        lines[line - 1] = s
    path.write_bytes(''.join(lines).encode('utf-8', errors='surrogateescape'))
    return skipped


def parse_rev_args(values):
    out = {}
    for v in values or []:
        if '=' in v:
            k, r = v.split('=', 1)
            out[k] = r
        else:
            out['*'] = v
    return out


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--repo', default='.', help='superproject path (default: cwd)')
    sub = p.add_subparsers(dest='cmd', required=True)
    c = sub.add_parser('check', help='verify citations against one revision per disassembly')
    c.add_argument('paths', nargs='*', help=f'paths to scan (default: {" ".join(DEFAULT_SCAN)})')
    c.add_argument('--rev', action='append', metavar='[NAME=]REV',
                   help="reference revision: pinned (default), checkout, or a commit; NAME=REV for one disassembly")
    c.add_argument('--strict', action='store_true', help='also fail on anchor mismatches')
    c.add_argument('--json', action='store_true')
    c.add_argument('--limit', type=int, default=0, help='print at most N findings')
    c.add_argument('-v', '--verbose', action='store_true', help='also list unresolvable file names')
    r = sub.add_parser('remap', help='carry citations from one revision of a disassembly to another')
    r.add_argument('sub', help='disassembly name, e.g. skdisasm')
    r.add_argument('src', help='revision the citations are correct for (pinned, checkout, or a commit)')
    r.add_argument('dst', help='revision to carry them to')
    r.add_argument('paths', nargs='*')
    r.add_argument('--write', action='store_true', help='edit files in place')
    r.add_argument('--carry-edited', action='store_true',
                   help='also carry ranges across same-size, same-mnemonic edits (e.g. renamed constants)')
    r.add_argument('--strict', action='store_true', help='exit non-zero when any citation needs review')
    args = p.parse_args(argv)
    return {'check': cmd_check, 'remap': cmd_remap}[args.cmd](args)


if __name__ == '__main__':
    sys.exit(main())
