#!/usr/bin/env python3
"""Bounded read-only Java API-delta consumer audit; advisory source evidence only.

Inputs: immutable Git owner/callers and externally pinned FINAL owner/callers.
Origin: 2026-11-07 Sonic CD R90b removed package-private originalImage/0;
var/factory consumers survived source consolidation and failed cold compilation.
Reuses pinned import-preflight safety readers, census declarations and enum lexer.
No Java, classpath, compiler, rewriting, job inspection or result cache.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import types

PIN_BYTES = 61348
PIN_SHA256 = 'da06d326e68d11665b5a228889ce01c4ccbd373bbe11c0a8501266be502cb6b8'
IDENT = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*\Z')
QUALIFIED = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*\Z')
LIMITATIONS = [
    'Advisory written-source name/arity candidates, never Java compilation, API approval, ABI, behavior, accessibility or actual overload binding.',
    'Explicit selected files only: zero candidates is not consumer completeness. No checkout scan, classpath, inherited/generated/external resolution, generic inference or chained-factory return inference.',
    'Receiver provenance is narrow lexical evidence: simple declared parameters/variables, exact new constructors and class spellings. It does not prove runtime type, subtype relationships or staticness. Foreign declared owners are controls, not certified valid calls.',
    'Method-reference overload arity is unknown. Calls count only bounded top-level argument slots; generics/comparisons/lambdas in arguments remain unknown. Same-arity type/return/modifier changes require review without guessing overload binding.',
    'Executable Java grammar is opaque; malformed bodies can yield candidates. Unsupported scopes and binding syntax remain incomplete. Comments/literals/text blocks are redacted; Unicode escapes are unsupported.',
    'Only selected immutable blobs, FINAL bytes/modes/identity, Git directory and HEAD metadata are re-observed, non-atomically. Change-and-restore can escape detection; unselected files/index/environment are unobserved. Trusted local Git metadata required.',
    'No supplied text executes, Git filters/network/mutation, Maven, Java, pass cache or output files. JSON/human output goes only to stdout.',
]


class AuditError(ValueError):
    def __init__(self, message, code=3):
        super().__init__(message)
        self.code = code


def need(condition, message, code=3):
    if not condition:
        raise AuditError(message, code)


def sha(data):
    return hashlib.sha256(data).hexdigest()


_DEPS = None


def dependencies():
    """Bootstrap only reviewed pinned sibling bytes, walking every component no-follow."""
    global _DEPS
    if _DEPS is None:
        path = Path(__file__).absolute().parent / 'java_project_import_preflight.py'
        fds = []
        try:
            fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY)
            fds.append(fd)
            for i, part in enumerate(path.parts[1:]):
                flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK
                if i < len(path.parts) - 2:
                    flags |= os.O_DIRECTORY
                fd = os.open(part, flags, dir_fd=fd)
                fds.append(fd)
            info = os.fstat(fd)
            need(stat.S_ISREG(info.st_mode) and info.st_size == PIN_BYTES, 'Pinned preflight helper size/type mismatch', 2)
            data = bytearray()
            while len(data) <= PIN_BYTES:
                block = os.read(fd, min(65536, PIN_BYTES + 1 - len(data)))
                if not block:
                    break
                data.extend(block)
            need(info == os.fstat(fd) and len(data) == PIN_BYTES and sha(data) == PIN_SHA256,
                 'Pinned preflight helper hash/identity mismatch', 2)
        finally:
            for fd in reversed(fds):
                os.close(fd)
        module = types.ModuleType('_consumer_preflight')
        module.__file__ = str(path)
        sys.modules[module.__name__] = module
        exec(compile(bytes(data), str(path), 'exec'), module.__dict__)
        census, lexer = module.dependencies()
        _DEPS = module, census, lexer
    return _DEPS


@dataclass(frozen=True)
class Limits:
    max_file_bytes: int = 1048576
    max_total_bytes: int = 4194304
    max_tokens: int = 100000
    max_context_tokens: int = 4096
    max_depth: int = 32
    max_hits: int = 256
    max_callers: int = 16
    max_seconds: int = 30
    max_output_bytes: int = 1048576
    max_declarations: int = 5000

    def validate(self):
        for key, ceiling in vars(Limits()).items():
            need(type(getattr(self, key)) is int and 1 <= getattr(self, key) <= ceiling,
                 key + ' outside lowerable bound', 2)


def census_limits(limits):
    _, c, _ = dependencies()
    return c.Limits(**{key: min(getattr(limits, key), ceiling) for key, ceiling in vars(c.Limits()).items()})


def owner_inventory(data, owner, engine):
    p, c, _ = dependencies()
    scope = engine.parse(data)  # validates strict headers in addition to census
    declarations = c.inventory(data, census_limits(engine.limits))
    types_ = [d for d in scope.declarations if d['qualified'] == owner and not d['local']]
    need(len(types_) == 1, 'Owner lacks exact selected written type/package provenance: ' + owner, 2)
    methods = [d for d in declarations if d['owner'] == owner and d['kind'] == 'method']
    return dict(owner=owner, methods=methods, unsupported=scope.reasons)


def literal_uncertainty(before, after, old, new):
    return ['Redacted method header literals cannot establish written signature equality across different bytes'] if before != after and any(
        '<literal>' in row['signature'] for row in old['methods']+new['methods']) else []


def delta(before, after, limits):
    def key(row):
        return row['name'], row['arity'], row['signature']
    after_keys = {key(r) for r in after['methods']}
    changes, output_bytes = [], 0
    by_name = {}
    for row in after['methods']:
        by_name.setdefault(row['name'], []).append(row)
    for row in before['methods']:
        if key(row) in after_keys:
            continue
        same_name = by_name.get(row['name'], [])
        need(len(same_name) <= limits.max_hits, 'Changed overload group budget exhausted')
        same_arity = [r for r in same_name if r['arity'] == row['arity']]
        need(len(changes) < limits.max_hits, 'Delta row budget exhausted')
        change = dict(name=row['name'], arity=row['arity'], before=row,
                            after_same_name=same_name,
                            kind='changed_written_signature' if same_arity else 'removed_written_name_arity')
        output_bytes += len(json.dumps(change,ensure_ascii=True).encode())
        need(output_bytes <= limits.max_output_bytes, 'Delta payload byte budget exhausted')
        changes.append(change)
    return changes


class Consumer:
    def __init__(self, data, engine):
        self.engine, self.limits = engine, engine.limits
        self.scope = engine.parse(data)
        self.java, self.ts = self.scope.java, self.scope.ts
        self.reasons = list(self.scope.reasons)
        self.unsupported_ranges = list(self.scope.unsupported_ranges)
        self.bindings, self.declaration_names, self.parameter_tokens = [], set(), set()
        self.curly, stack = {}, []
        for i in range(len(self.ts)):
            self.curly[i] = tuple(stack)
            if self.v(i) == '{':
                stack.append(i)
            elif self.v(i) == '}':
                stack.pop()
        _, c, _ = dependencies()
        try:
            parsed, _ = c.source_declarations(data, census_limits(self.limits))
            for row, (start, end) in zip(parsed.result, parsed.spans):
                if row['kind'] not in ('method', 'constructor'):
                    continue
                # A method header ends immediately before its body/semicolon.
                name_at = next(i for i in range(start, end) if self.ts[i].line == row['line'] and
                               self.v(i) == row['name'] and self.v(i+1) == '(')
                self.declaration_names.add(name_at)
                opening = name_at+1
                self.parameter_tokens.update(span[2] for span in parsed.parameter_spans.get(opening, []))
                if self.v(end) != '{':
                    continue
                if any(self.v(k) == '<' for k in range(start,name_at)):
                    self.unsupported_ranges.append((end,self.java.pairs[end]))
                    self.reasons.append('Generic callable receiver binding unsupported at line '+str(row['line']))
                for parameter in row['parameters']:
                    self.bindings.append(dict(name=parameter['name'], type=parameter['type'],
                        start=end, stop=self.java.pairs[end], depth=len(self.curly[end])+1,
                        at=name_at, evidence='declared_parameter', line=parameter['line']))
        except c.CensusError as error:
            self.reasons.append('Caller declaration grammar incomplete: ' + str(error))
        self.variables()

    def v(self, i):
        return self.ts[i].value if 0 <= i < len(self.ts) else '<EOF>'

    def context(self, start, stop):
        need(stop-start <= self.limits.max_context_tokens, 'Consumer context token budget exhausted')
        return ' '.join(t.value for t in self.ts[max(0, start):stop])

    def annotated_declaration_start(self, at):
        """Recognize an annotation-bearing prefix only to install an unknown binding."""
        k, annotated = at-1, False
        while k >= self.scope.header_end:
            need(at-k <= self.limits.max_context_tokens, 'Annotated declaration context budget exhausted')
            if IDENT.fullmatch(self.v(k)) or self.v(k) == '.':
                k -= 1
            elif self.v(k) == '@':
                annotated, k = True, k-1
            elif self.v(k) == ')':
                # Skip balanced annotation arguments, never an ordinary call.
                name = self.java.pairs[k]-1
                while IDENT.fullmatch(self.v(name)) or self.v(name) == '.':
                    need(at-name <= self.limits.max_context_tokens, 'Annotated declaration context budget exhausted')
                    name -= 1
                if self.v(name) != '@':
                    break
                annotated, k = True, name-1
            else:
                break
        need(at-k <= self.limits.max_context_tokens, 'Annotated declaration context budget exhausted')
        return k if annotated and self.v(k) in ('{',';','}','(',',') else None

    def variables(self):
        """Only simple statement/member declarations; block unsupported shadows conservatively."""
        for i in range(self.scope.header_end, len(self.ts)-1):
            self.engine.check()
            if not IDENT.fullmatch(self.v(i)) or self.v(i) in dependencies()[0].RESERVED:
                continue
            if self.v(i+1) not in ('=', ';', ',', ')', ':', '-'):
                continue
            # Extract only identifier/dot simple types; generic/array syntax blocks provenance.
            j = i-1
            while j >= 0 and (IDENT.fullmatch(self.v(j)) or self.v(j) == '.'):
                need(i-j <= self.limits.max_context_tokens, 'Variable context budget exhausted')
                j -= 1
            words = [self.v(k) for k in range(j+1, i)]
            while words and words[0] in ('public','private','protected','static','final','transient','volatile'):
                words.pop(0)
            type_text = ''.join(words)
            simple = bool(words) and not any(w in dependencies()[0].RESERVED and w not in dependencies()[1].PRIMITIVES for w in words) and len(words) % 2 == 1 and all(
                IDENT.fullmatch(w) if k % 2 == 0 else w == '.' for k, w in enumerate(words))
            boundary = self.v(j) in ('{',';','}')
            parameterish = self.v(j) in ('(', ',')
            annotated_start = self.annotated_declaration_start(i)
            annotated = annotated_start is not None
            if annotated:
                boundary = self.v(annotated_start) in ('{',';','}')
                parameterish = self.v(annotated_start) in ('(', ',')
            # x -> ... can shadow a field/local even without a written type.
            lambda_name = self.v(i+1) == '-' and self.v(i+2) == '>'
            paren = self.java.parents[i]
            lambda_header = paren is not None and self.v(self.java.pairs[paren]+1) == '-' and self.v(self.java.pairs[paren]+2) == '>'
            pattern_shadow = self.v(i-2) == 'instanceof'
            unsupported_type = self.v(i-1) in (']', '>') and self.v(i+1) in ('=', ';', ',', ')', ':')
            comma_declarator = self.v(i-1) == ',' and self.v(i+1) in ('=', ';')
            if not (simple and (boundary or parameterish)) and not lambda_name and not unsupported_type and not comma_declarator and not lambda_header and not pattern_shadow and not annotated:
                continue
            stack = self.curly[i]
            if not stack:
                continue
            start, stop = i, self.java.pairs[stack[-1]]
            class_body = any(d['opening'] == stack[-1] for d in self.scope.declarations)
            if class_body:
                start = stack[-1]
            evidence = 'declared_type'
            if not boundary or lambda_name or unsupported_type or comma_declarator or lambda_header or pattern_shadow or annotated:
                # Method parameters were handled above. Keep a blocker for other
                # headers (for/catch/lambda); never leak its type into a wider scope.
                if i in self.parameter_tokens:
                    continue
                if (parameterish and not comma_declarator) or lambda_name or lambda_header:
                    # Header shadows belong to their body, never the rest of the
                    # enclosing method. Expression lambdas end at the containing
                    # argument boundary or semicolon; their binding stays unknown.
                    paren = self.java.parents[i]
                    close = self.java.pairs[paren] if paren is not None else i
                    body = close+1
                    if self.v(body) == '{':
                        start, stop = body, self.java.pairs[body]
                    elif self.v(body) == '-' and self.v(body+1) == '>':
                        start, stop = i, self.lambda_stop(body+2)
                    elif lambda_name:
                        start, stop = i, self.lambda_stop(i+3)
                    else:
                        # Unbraced for/catch-like statements are deliberately
                        # unsupported; block only through their first semicolon.
                        start = i
                        stop = self.statement_stop(body)
                type_text = None
                evidence = 'unsupported_annotated_declaration' if annotated else 'unsupported_header_or_lambda_shadow'
                self.reasons.append(('Annotated declaration' if annotated else 'Header/lambda binding')+
                                    ' unsupported at line ' + str(self.ts[i].line))
            elif type_text == 'var':
                type_text, evidence = None, 'unresolved_var_initializer'
                if self.v(i+1) == '=' and self.v(i+2) == 'new':
                    k = i+3
                    while IDENT.fullmatch(self.v(k)) or self.v(k) == '.':
                        need(k-i <= self.limits.max_context_tokens, 'Constructor context budget exhausted')
                        k += 1
                    spelling = ''.join(self.v(n) for n in range(i+3,k))
                    if QUALIFIED.fullmatch(spelling) and self.v(k) == '(' and self.v(self.java.pairs[k]+1) == ';':
                        type_text, evidence = spelling, 'explicit_new_constructor'
            self.bindings.append(dict(name=self.v(i), type=type_text, start=start, stop=stop,
                depth=len(stack)+int(parameterish or lambda_name or lambda_header), at=i, line=self.ts[i].line, evidence=evidence))

    def statement_stop(self, start):
        at = start
        while at < len(self.ts) and self.v(at) not in (';', '}'):
            need(at-start <= self.limits.max_context_tokens, 'Statement shadow context exhausted')
            at = self.java.pairs[at]+1 if self.v(at) in ('(', '[', '{') else at+1
        return at

    def lambda_stop(self, start):
        if self.v(start) == '{':
            return self.java.pairs[start]
        at = start
        while at < len(self.ts) and self.v(at) not in (';', ',', ')', '}'):
            need(at-start <= self.limits.max_context_tokens, 'Lambda shadow context exhausted')
            at = self.java.pairs[at]+1 if self.v(at) in ('(', '[', '{') else at+1
        return at

    def type_owner(self, spelling, at):
        if not spelling or not QUALIFIED.fullmatch(spelling):
            return None
        p, _, _ = dependencies()
        if any(word in p.RESERVED or word == 'var' for word in spelling.split('.')):
            return None
        if '.' in spelling:
            return spelling  # qualified spelling evidence, not classpath certification
        containing = {d['qualified'] for d in self.scope.declarations if d['opening'] in self.curly[at]}
        matches = [d for d in self.scope.declarations if d['name'] == spelling and
                   (d['top_level'] or d['owner'] in containing or d['opening'] in self.curly[at])]
        if matches:
            # Only trust a single top-level name; member/type shadows remain unresolved.
            if len(matches) == 1 and matches[0]['top_level']:
                return matches[0]['qualified']
            return None
        direct = [n for static, n in self.java.imports if not static and n.split('.')[-1] == spelling]
        if len(direct) == 1:
            return direct[0]
        if not direct and spelling == self.known_owner.split('.')[-1] and self.java.package == self.known_owner.rpartition('.')[0]:
            return self.known_owner
        if direct or any(n.endswith('.*') or static for static, n in self.java.imports):
            return None
        return None  # Unselected same-package/java.lang/external names are not inferred.

    def receiver(self, at, separator):
        end = at-2 if separator == '.' else at-3
        if separator == 'implicit':
            return dict(syntax='<implicit>', owner=None, evidence='implicit_or_static_import_unresolved')
        # Recover a bounded expression suffix, including chained/factory syntax.
        start = end
        while start >= self.scope.header_end:
            value = self.v(start)
            if value in (')', ']'):
                start = self.java.pairs[start]-1
                continue
            if (IDENT.fullmatch(value) and value not in dependencies()[0].RESERVED) or value in ('.','this','super','new'):
                start -= 1
                continue
            break
        start += 1
        syntax = self.context(start, end+1)
        owner, evidence, binding = None, 'expression_or_chain_unresolved', None
        # Simple identifier or dotted class reference only. Qualified fields remain unknown.
        if start == end and IDENT.fullmatch(self.v(end)):
            name = self.v(end)
            bindings = [b for b in self.bindings if b['name'] == name and b['start'] <= at < b['stop']]
            if bindings:
                binding = max(bindings, key=lambda b: (b['depth'], b['start'], b['at']))
                owner = self.type_owner(binding['type'], at)
                evidence = binding['evidence']
            else:
                # A class reference requires a type-like spelling and no unsupported
                # inheritance/opaque scope which could contribute a value shadow.
                if name[:1].isupper() and not any(static for static,_ in self.java.imports):
                    owner = self.type_owner(name, at)
                    evidence = 'class_spelling'
        elif QUALIFIED.fullmatch(syntax.replace(' ', '')):
            parts = syntax.replace(' ', '').split('.')
            if len(parts) > 1 and all(w not in dependencies()[0].RESERVED for w in parts) and parts[-1][:1].isupper() and not any(b['name'] == parts[0] and
                    b['start'] <= at < b['stop'] for b in self.bindings):
                owner, evidence = '.'.join(parts), 'qualified_class_spelling'
        elif self.v(start) == 'new' and self.v(end) == ')':
            opening = self.java.pairs[end]
            spelling = ''.join(self.v(k) for k in range(start+1, opening))
            if QUALIFIED.fullmatch(spelling):
                owner, evidence = self.type_owner(spelling, at), 'explicit_new_constructor'
        if any(a < at < b for a,b in self.unsupported_ranges):
            owner, evidence = None, 'unsupported_containing_scope'
        result = dict(syntax=syntax, owner=owner, evidence=evidence)
        if binding:
            result['binding'] = binding
        return result

    def arity(self, opening):
        closing = self.java.pairs[opening]
        self.context(opening, closing+1)
        if closing == opening+1:
            return 0
        count, i = 1, opening+1
        while i < closing:
            if self.v(i) in ('<','>','?', '-', ':'):
                return None
            if self.v(i) in ('(', '[', '{'):
                i = self.java.pairs[i]+1
            elif self.v(i) == ',':
                need(i > opening+1 and self.v(i-1) != ',' and i+1 < closing, 'Malformed argument slots')
                count += 1
                i += 1
            else:
                i += 1
        return count

    def references(self, changes, owner, after, missing):
        self.known_owner = owner
        names = {d['name'] for d in changes} | set(missing)
        rows, output_bytes = [], 0
        for at, token in enumerate(self.ts):
            self.engine.check()
            if token.value not in names or at in self.declaration_names:
                continue
            is_ref = self.v(at-1) == ':' and self.v(at-2) == ':'
            is_call = self.v(at+1) == '('
            if not (is_ref or is_call):
                self.reasons.append('Selected name outside supported call/reference at line ' + str(token.line))
            separator = '::' if is_ref else '.' if self.v(at-1) == '.' else 'implicit'
            receiver = self.receiver(at, separator)
            arity = self.arity(at+1) if is_call and not is_ref else None
            candidates = [d for d in changes if d['name'] == token.value and
                          (arity is None or d['arity'] == arity or any(p['varargs'] for p in d['before']['parameters']))]
            known_after = [r for r in after['methods'] if r['name'] == token.value and (arity is None or r['arity'] == arity)]
            classification = 'advisory_hit'
            if receiver['owner'] is None:
                classification = 'unresolved_receiver'
                self.reasons.append('Receiver unresolved at line ' + str(token.line))
            elif receiver['owner'] != owner:
                classification = 'known_foreign_owner'
            elif not candidates and token.value not in missing:
                classification = 'other_written_arity'
            if arity is None:
                self.reasons.append('Overload arity unresolved at line ' + str(token.line))
            if token.value in missing:
                self.reasons.append('Missing-member hints exclude inherited/generated/external members at line ' + str(token.line))
            need(len(rows) < self.limits.max_hits, 'Reference output row budget exhausted')
            row = dict(name=token.value, line=token.line, kind='method_reference' if is_ref else 'call' if is_call else 'unsupported_name_context',
                arity=arity, receiver=receiver, classification=classification, delta_candidates=candidates,
                after_written_candidates=known_after, requested_missing_member=token.value in missing,
                context=self.context(max(self.scope.header_end, at-8), min(len(self.ts), at+9)))
            output_bytes += len(json.dumps(row,ensure_ascii=True).encode())
            need(output_bytes <= self.limits.max_output_bytes, 'Reference payload byte budget exhausted')
            rows.append(row)
        return rows


def observe(before, after, caller, owner, limits=None, missing=()):
    """In-memory test seam. CLI adds immutable/pinned custody and double observations."""
    limits = limits or Limits()
    limits.validate()
    p, _, _ = dependencies()
    engine = p.Engine(limits)
    old, new = owner_inventory(before, owner, engine), owner_inventory(after, owner, engine)
    changes = delta(old, new, limits)
    consumer = Consumer(caller, engine)
    rows = consumer.references(changes, owner, new, missing)
    return dict(delta=changes, references=rows, incomplete_reasons=sorted(set(consumer.reasons + old['unsupported'] + new['unsupported'] + literal_uncertainty(before,after,old,new))))


def parser():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument('--git-dir', required=True)
    p.add_argument('--revision', required=True, help='Full immutable lowercase commit SHA-1; never HEAD')
    p.add_argument('--owner', required=True, help='Explicit qualified written method owner')
    p.add_argument('--source', required=True, help='Exact immutable before-owner relative Java path')
    p.add_argument('--after', required=True, help='Canonical absolute FINAL owner Java file')
    p.add_argument('--after-sha256', required=True)
    p.add_argument('--after-bytes', required=True, type=int)
    p.add_argument('--after-mode', required=True, help='Externally pinned four-digit octal mode, e.g. 0444')
    p.add_argument('--caller', action='append', default=[], help='Explicit immutable caller file (repeatable)')
    p.add_argument('--caller-final', nargs=4, action='append', default=[], metavar=('PATH','SHA256','BYTES','MODE'),
                   help='Explicit canonical FINAL caller with independent pins; repeatable')
    p.add_argument('--check-member', action='append', default=[], help='Optional known-owner missing written method name hint')
    p.add_argument('--json', action='store_true', help='Emit JSON instead of human-readable observations')
    for key, value in vars(Limits()).items():
        p.add_argument('--'+key.replace('_','-'), type=int, default=value, help='Lowerable ceiling: '+str(value))
    return p


def run(args):
    limits = Limits(**{key: getattr(args,key) for key in vars(Limits())})
    limits.validate()
    need(limits.max_output_bytes >= 512, 'Output bound must allow a 512-byte incomplete diagnostic', 2)
    p, c, _ = dependencies()
    need(p.SHA1.fullmatch(args.revision), 'Require full immutable lowercase commit SHA-1', 2)
    need(len(args.owner) <= 4096 and QUALIFIED.fullmatch(args.owner) and
         all(w not in p.RESERVED for w in args.owner.split('.')), 'Invalid explicit owner', 2)
    need(1 <= len(args.caller)+len(args.caller_final) <= limits.max_callers, 'Require bounded explicit caller files', 2)
    need(len(args.check_member) <= limits.max_hits and len(set(args.check_member)) == len(args.check_member) and
         all(len(n) <= 4096 and IDENT.fullmatch(n) and n not in p.RESERVED for n in args.check_member), 'Invalid check-member list', 2)
    git_dir, source = p.absolute(args.git_dir), p.relative(args.source)
    directory = c.directory_id(git_dir)
    # Read HEAD as metadata only; never resolve it as source. Linked worktree metadata
    # is explicit. Common-dir contents/reflogs/index are deliberately not scanned.
    head_before = c.read_regular(git_dir / 'HEAD', 4096, no_alias=True)
    engine = p.Engine(limits)
    need(engine.git(git_dir, ['cat-file','-t',args.revision], min(128,limits.max_file_bytes)).strip() == b'commit', 'Revision is not a local commit', 2)
    saved, selected = [], set()

    def blob(path):
        path = p.relative(path)
        need(('git', path) not in selected, 'Duplicate selected source alias', 2)
        selected.add(('git',path))
        data, provenance = engine.blob(git_dir,args.revision,path)
        entry = engine.git(git_dir, ['ls-tree','-z',args.revision,'--',path], min(8192,limits.max_file_bytes))
        provenance['mode'] = entry.split(b' ',1)[0].decode('ascii')
        saved.append(('blob',path,data,provenance,None))
        return data, provenance

    def final(path, digest, size, mode):
        path = p.absolute(path)
        need(('final',str(path)) not in selected, 'Duplicate FINAL source alias', 2)
        selected.add(('final',str(path)))
        need(isinstance(digest,str) and p.SHA256.fullmatch(digest) and str(size).isascii() and str(size).isdigit(), 'Invalid FINAL SHA256/byte pins', 2)
        size = int(size)
        need(size <= limits.max_file_bytes and re.fullmatch(r'0[0-7]{3}', mode), 'Invalid FINAL size/mode pin', 2)
        engine.account(size)
        with c.opened(path) as fd:
            info = os.fstat(fd)
            need(info.st_size == size and stat.S_IMODE(info.st_mode) == int(mode,8), 'FINAL byte/mode pin mismatch before read', 2)
        data, identity = c.read_final(path, min(size,limits.max_file_bytes))
        need(len(data) == size and sha(data) == digest and stat.S_IMODE(identity[2]) == int(mode,8), 'FINAL hash/bytes/mode pin mismatch', 2)
        need(not any(kind == 'final' and sig[:2] == identity[:2] for kind,_,_,_,sig in saved), 'FINAL inode alias rejected', 2)
        provenance = dict(kind='pinned_final',path=str(path),bytes=size,sha256=digest,mode=mode)
        saved.append(('final',path,data,provenance,identity))
        return data, provenance

    before, old_provenance = blob(source)
    after, new_provenance = final(args.after,args.after_sha256,args.after_bytes,args.after_mode)
    callers = [blob(path) for path in args.caller] + [final(*values) for values in args.caller_final]
    result = dict(version=1,status='observed',owner=args.owner,limits=vars(limits),limitations=LIMITATIONS,
                  before=dict(provenance=old_provenance),after=dict(provenance=new_provenance),
                  delta=[],callers=[],advisory_hits=[],known_foreign_references=[],unsupported=[],omissions=[],observation_limits=[])
    try:
        old = owner_inventory(before,args.owner,engine)
        new = owner_inventory(after,args.owner,engine)
        result['before']['methods'], result['after']['methods'] = old['methods'], new['methods']
        result['delta'] = changes = delta(old,new,limits)
        result['unsupported'].extend(old['unsupported']+new['unsupported']+literal_uncertainty(before,after,old,new))
        absent = [n for n in args.check_member if not any(r['name'] == n for r in new['methods'])]
        result['check_members'] = dict(requested=args.check_member, absent_written_names=absent)
        for data, provenance in callers:
            engine.check()
            row = dict(provenance=provenance,references=[],incomplete_reasons=[])
            result['callers'].append(row)
            try:
                consumer = Consumer(data,engine)
                refs = consumer.references(changes,args.owner,new,absent)
                need(sum(len(r['references']) for r in result['callers'])+len(refs) <= limits.max_hits, 'Aggregate reference row budget exhausted')
                row['references'], row['incomplete_reasons'] = refs, sorted(set(consumer.reasons))
                for ref in refs:
                    location = dict(path=provenance['path'],sha256=provenance['sha256'],line=ref['line'],name=ref['name'],classification=ref['classification'])
                    if ref['classification'] in ('advisory_hit','unresolved_receiver'):
                        result['advisory_hits'].append(location)
                    elif ref['classification'] == 'known_foreign_owner':
                        result['known_foreign_references'].append(location)
                result['unsupported'].extend(row['incomplete_reasons'])
            except (AuditError,p.PreflightError,c.CensusError) as error:
                if error.code == 2:
                    raise
                row['incomplete_reasons'].append(str(error))
                result['omissions'].append(dict(path=provenance['path'],reason=str(error),scope='Caller references may be partial or absent'))
    except (AuditError,p.PreflightError,c.CensusError) as error:
        if error.code == 2:
            raise
        result['omissions'].append(dict(scope='Selected owner/consumer processing stopped',reason=str(error)))
    # Rechecks also run after unsupported grammar/exhaustion; never turn drift into a clean result.
    for kind,path,data,provenance,identity in saved:
        try:
            if kind == 'blob':
                again, pin = engine.blob(git_dir,args.revision,path,account=False)
                entry = engine.git(git_dir,['ls-tree','-z',args.revision,'--',path],min(8192,limits.max_file_bytes))
                pin['mode'] = entry.split(b' ',1)[0].decode('ascii')
                need(again == data and pin == provenance, 'Immutable Git source drift')
            else:
                again, sig = c.read_final(path,min(len(data),limits.max_file_bytes))
                need(again == data and sig == identity, 'FINAL source/mode/identity drift')
        except (AuditError,p.PreflightError,c.CensusError) as error:
            raise AuditError('Re-observation incomplete: '+str(error)) from error
    try:
        need(c.directory_id(git_dir) == directory and c.read_regular(git_dir/'HEAD',4096,no_alias=True) == head_before,
             'Git directory or HEAD metadata drift')
    except c.CensusError as error:
        raise AuditError('Git directory/HEAD re-observation incomplete: '+str(error)) from error
    result['observation_limits'] = [
        f'Selected {len(saved)} source inputs only; no unselected consumers searched.',
        f'Reader accounted {engine.bytes} selected bytes and {engine.tokens} lexer tokens; declaration re-lexing does not add new inputs.',
        'Source/identity/mode/Git HEAD rechecks are non-atomic; restored transient changes can escape detection.',
        'Time bounds apply at reader/parse checkpoints, not a hard process CPU deadline.',
    ]
    result['unsupported'] = sorted(set(result['unsupported']))
    if result['unsupported'] or result['omissions']:
        result['status'] = 'incomplete'
    return result


def exit_code(result):
    return 3 if result['status'] == 'incomplete' else 2 if result['status'] == 'error' else int(bool(result['advisory_hits']))


def human(result):
    lines = ['Java API consumer audit: '+result['status']+' (advisory lexical observations only)']
    lines.append('Zero candidates never establishes consumer completeness.')
    for key in ('before','after'):
        if key in result:
            pin = result[key]['provenance']
            lines.append(f"{key}: {pin['path']} sha256={pin['sha256']} bytes={pin['bytes']} mode={pin['mode']}")
    for caller in result.get('callers',[]):
        pin = caller['provenance']
        lines.append(f"caller: {pin['path']} sha256={pin['sha256']} mode={pin['mode']}")
        for row in caller['references']:
            lines.append(f"  line {row['line']} {row['name']}/{row['arity']} {row['classification']}: {row['receiver']}")
    for key in ('error','delta','advisory_hits','known_foreign_references','unsupported','omissions','observation_limits','limitations'):
        if result.get(key):
            lines.append(key+': '+json.dumps(result[key],ensure_ascii=True,sort_keys=True))
    return '\n'.join(lines)


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        p,c,_ = dependencies()
        try:
            result = run(args)
        except (p.PreflightError,c.CensusError) as error:
            raise AuditError(str(error),error.code) from error
    except (AuditError,OSError,UnicodeError,ValueError) as error:
        code = getattr(error,'code',2)
        result = dict(version=1,status='incomplete' if code == 3 else 'error',error=str(error),
                      advisory_hits=[],unsupported=[],omissions=[],observation_limits=[],limitations=LIMITATIONS)
    encoded = json.dumps(result,ensure_ascii=True,sort_keys=True,indent=2) if args.json else human(result)
    output_bound = args.max_output_bytes if 512 <= args.max_output_bytes <= Limits().max_output_bytes else Limits().max_output_bytes
    if len(encoded.encode())+1 > output_bound:
        if result['status'] == 'error':
            result = dict(version=1,status='error',error='Invalid input/read/pin; expanded diagnostic omitted by output bound',
                          advisory_hits=[],omissions=['Expanded error diagnostic omitted'],observation_limits=['Output bound exhausted'])
        else:
            result = dict(version=1,status='incomplete',advisory_hits=[],unsupported=[],
                          omissions=['All expanded observations omitted: output byte budget exhausted'],
                          observation_limits=['Output bound exhausted; no empty/clean inference'])
        encoded = json.dumps(result,sort_keys=True) if args.json else human(result)
        if len(encoded.encode())+1 > output_bound:
            encoded = json.dumps(dict(status=result['status'],omissions=['Output bound exhausted'])) if args.json else result['status']+': output bound exhausted; expanded observations omitted'
    print(encoded)
    return exit_code(result)


if __name__ == '__main__':
    sys.exit(main())
