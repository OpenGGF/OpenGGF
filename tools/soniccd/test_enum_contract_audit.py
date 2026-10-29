#!/usr/bin/env python3
"""Temporary-repo safety/contract tests; no runtime assets, Java or Maven execution.

Inputs: generated Java text and Git repositories owned by each test under this
configured temporary directory. Origin: 2026-10-26 private enum-contract tooling task.
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('enum_contract_audit', HERE / 'enum_contract_audit.py')
audit = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = audit
spec.loader.exec_module(audit)
SOURCE = 'src/main/java/p/Owner.java'
CONSUMER = 'src/test/java/p/TestMappings.java'


def declaration(names, owner='Owner', enum='Table'):
    return f'package p; public class {owner} {{ public enum {enum} {{ ' + ', '.join(names) + '; } }\n'


def options(fixture_root, fixture_base, **changes):
    args = audit.parser().parse_args(['--root', str(fixture_root), '--base', fixture_base, '--source', SOURCE,
                                      '--enum', 'Table', '--type', 'Owner.Table',
                                      '--scan-root', 'src/test/java', '--json'])
    for key, value in changes.items():
        setattr(args, key, value)
    return args


class EnumLexing(unittest.TestCase):
    def setUp(self):
        self.limits = options(Path('/unused'), 'a' * 40)

    def extract(self, text, selected='Owner.Table', name='Table'):
        return audit.enum_constants(audit.lex(text.encode(), self.limits), name, selected, self.limits)

    def test_comments_literals_nested_commas_and_anonymous_body(self):
        text = '''package p;
        class Owner {
          enum Table {
            // PHANTOM, ANOTHER
            @Tag(values={"X,Y", "enum Table { WRONG }"})
            ALPHA("a,b", helper(new int[]{1, 2}, nested(3, 4))) {
              void body() { int[] x = {1,2}; String s = "GHOST,VOID"; }
              enum Other { IN_BODY, NOT_A_CONSTANT }
            }, /* FAKE, FAKE2 */
            BETA("""
              enum Table { TEXT, BLOCK } // comment-looking
              escaped \\" quote, commas,,,\x20
              """),
            GAMMA('x'),;
            String after = "NOT_CONSTANT";
            Table(Object... args) {}
          }
        }
        '''
        self.assertEqual(self.extract(text), (['ALPHA', 'BETA', 'GAMMA'], 'p.Owner.Table', 'Owner.Table'))

    def test_select_owner_and_top_level(self):
        text = 'package p; class Other { enum Table { WRONG } } class Owner { enum Table { RIGHT } }'
        self.assertEqual(self.extract(text)[0], ['RIGHT'])
        self.assertEqual(self.extract('package p; enum Choice { A, B }', 'p.Choice', 'Choice')[0], ['A', 'B'])

    def test_empty_enum_and_crlf(self):
        self.assertEqual(self.extract('package p;\r\nclass Owner { enum Table { ; } }')[0], [])
        self.assertEqual(self.extract('package p; class Owner { enum Table { } }')[0], [])
        self.assertEqual(self.extract('package p;\rclass Owner { enum Table { // GHOST,\r A, B } }')[0], ['A', 'B'])

    def test_unicode_preprocessing_and_broken_grammar_are_incomplete(self):
        for text in ['enum Table { A } // \\u000a B', 'class Owner { enum Table { A("bad) } }',
                     'class Owner { enum Table { A, B ', 'class Owner { enum Table { A } /* open']:
            with self.subTest(text=text), self.assertRaises(audit.AuditError) as caught:
                self.extract(text)
            self.assertEqual(caught.exception.code, 3)

    def test_duplicate_and_wrong_owner_rejected(self):
        for text, selected in [('class Owner { enum Table { A,A } }', 'Owner.Table'),
                               ('class Owner { enum Table { A } }', 'Wrong.Table')]:
            with self.subTest(text=text), self.assertRaises(audit.AuditError):
                self.extract(text, selected)


class TemporaryRepo(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='enum-audit-test-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / 'repo'
        self.root.mkdir()
        self.git('init', '-q', '--template=')
        self.write(SOURCE, declaration(['A', 'B']))
        self.write(CONSUMER, 'package p; class TestMappings {}\n')
        self.base = self.commit()
        self.write(SOURCE, declaration(['A', 'B', 'C', 'D']))

    def git(self, *args):
        env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM='1', GIT_TERMINAL_PROMPT='0')
        return subprocess.check_output(['git', '-c', 'core.hooksPath=/dev/null',
                                        '-c', 'user.name=Enum fixture', '-c', 'user.email=fixture@example.invalid',
                                        '-c', 'commit.gpgsign=false', *args], cwd=self.root,
                                       env=env, stderr=subprocess.PIPE).decode().strip()

    def commit(self):
        self.git('add', '--all')
        self.git('commit', '-q', '-m', 'test fixture\n\nCo-authored-by: OpenAI Codex GPT-6.1 <noreply@openai.com>')
        return self.git('rev-parse', 'HEAD')

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode('utf-8'))

    def run_audit(self, **changes):
        return audit.audit(options(self.root, self.base, **changes))

    def findings(self, report, kind):
        return [f for f in report['findings'] if f['kind'] == kind]

    def consumer(self, body, imports='', package='p'):
        self.write(CONSUMER, f'package {package};\n{imports}\nclass TestMappings {{ void verify() {{\n{body}\n}} }}\n')

    def test_actual_shaped_62_to_64_mapper_at_2439_and_unrelated_106(self):
        names = [f'OBJECT_{i}' for i in range(62)]
        source = SOURCE.replace('Owner.java', 'CdR11aObjectAssets.java')
        self.write(source, declaration(names, 'CdR11aObjectAssets'))
        self.write('src/main/java/p/CdR11aAsset.java', declaration([f'ASSET_{i}' for i in range(106)], 'CdR11aAsset'))
        self.base = self.commit()
        self.write(source, declaration([*names, 'NEW_A', 'NEW_B'], 'CdR11aObjectAssets'))
        self.write(CONSUMER, 'package p;\nclass TestCdObjectMappings { void test() {\n' + '\n' * 2436 +
                   'assertEquals(62, CdR11aObjectAssets.Table.values().length);\n' +
                   'assertEquals(106, CdR11aAsset.Table.values().length);\n} }\n')
        report = self.run_audit(source=source, type='CdR11aObjectAssets.Table')
        self.assertEqual((report['enum']['old_count'], report['enum']['new_count']), (62, 64))
        self.assertTrue(report['enum']['append_only_prefix'])
        stale = self.findings(report, 'stale_total_assertion')
        self.assertEqual(len(stale), 1, report)
        self.assertEqual((stale[0]['line'], stale[0]['literal'], stale[0]['type']), (2439, 62, 'p.CdR11aObjectAssets.Table'))
        self.assertEqual(len(report['findings']), 1)
        self.assertEqual(report['exit_code'], 1)

    def test_append_prefix_reorder_and_removal(self):
        for names, prefix, changed, removed in [(['A', 'B', 'C'], True, [], []),
                                               (['B', 'A', 'C'], False, ['A', 'B'], []),
                                               (['A'], False, [], ['B'])]:
            self.write(SOURCE, declaration(names))
            report = self.run_audit()
            self.assertEqual(report['enum']['append_only_prefix'], prefix)
            self.assertEqual([x['name'] for x in report['enum']['changed_ordinals']], changed)
            self.assertEqual(report['enum']['removed'], removed)
            self.assertEqual(report['exit_code'], 0 if prefix else 1)

    def test_whitespace_multiline_reversed_literal_args_and_crlf(self):
        self.consumer('assertEquals (\r\n ((Owner . Table . values ( ) . length)),\r\n (2), "message");\r\n'
                      'assertEquals(0x2L, p.Owner.Table.values().length);\r\n'
                      'assertEquals(Owner.Table.values().length, 0b10);\r\n'
                      'assertEquals(02, Owner.Table.values().length);')
        report = self.run_audit()
        self.assertEqual(len(self.findings(report, 'stale_total_assertion')), 4, report)
        self.assertTrue(all(f['literal'] == 2 for f in report['findings']))
        self.assertEqual(report['exit_code'], 1)

    def test_correct_new_symbolic_and_nonassertion_counts(self):
        self.consumer('assertEquals(4, Owner.Table.values().length);\n'
                      'assertEquals(EXPECTED, Owner.Table.values().length);\n'
                      'int count = Owner.Table.values().length;\n'
                      'assertEquals(2 + 0, Owner.Table.values().length);')
        report = self.run_audit()
        self.assertFalse(self.findings(report, 'stale_total_assertion'))
        self.assertEqual([f['assertion'] for f in report['findings']],
                         ['current_total', 'symbolic_or_expression', 'none', 'symbolic_or_expression'])
        self.assertEqual(report['exit_code'], 0)
        self.assertFalse(report['coverage']['complete_consumer_proof'])

    def test_unchanged_old_count_is_not_stale(self):
        self.write(SOURCE, declaration(['A', 'B']))
        self.consumer('assertEquals(2, Owner.Table.values().length);')
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 0)
        self.assertEqual(report['findings'][0]['assertion'], 'current_total')

    def test_stream_slices_bounded_and_unbounded_review(self):
        self.consumer('var a = Arrays.stream(Owner.Table.values()).skip(54).limit(5).toList();\n'
                      'var b = java.util.Arrays.stream(Owner.Table.values()).skip(54)\n'
                      ' .filter(x -> x.name().contains(".limit(5)")) .toList();\n'
                      'var c = Arrays.stream(Owner.Table.values()).limit(LIMIT).skip(1).toList();',
                      'import java.util.Arrays;')
        report = self.run_audit()
        self.assertEqual(len(self.findings(report, 'bounded_stream_skip')), 2, report)
        review = self.findings(report, 'unbounded_stream_skip')
        self.assertEqual(len(review), 1)
        self.assertEqual((review[0]['literal'], review[0]['severity']), (54, 'review'))
        self.assertEqual(report['exit_code'], 1)

    def test_explicit_import_forms_and_fully_qualified(self):
        cases = [('import p.Owner;', 'Owner.Table'), ('import p.Owner.Table;', 'Table'),
                 ('import static p.Owner.Table;', 'Table'), ('', 'p.Owner.Table')]
        for imported, spelling in cases:
            self.consumer(f'assertEquals(2, {spelling}.values().length);', imported, package='other')
            report = self.run_audit()
            self.assertEqual(len(self.findings(report, 'stale_total_assertion')), 1, report)
        self.consumer('var xs = stream(p.Owner.Table.values()).skip(1).toList();',
                      'import static java.util.Arrays.stream;', package='other')
        self.assertEqual(len(self.findings(self.run_audit(), 'unbounded_stream_skip')), 1)

    def test_unsupported_indirect_wildcard_static_and_method_reference_truthful(self):
        cases = [('', 'Owner.Table[] xs = Owner.Table.values(); assertEquals(2, xs.length);', 'unsupported_values_use', 'p'),
                 ('import p.Owner.*;', 'assertEquals(2, Table.values().length);', 'unresolved_type_reference', 'other'),
                 ('import static p.Owner.Table.values;', 'var a = values();', 'unsupported_static_values', 'other'),
                 ('', 'var factory = Owner.Table::values;', 'unsupported_method_reference', 'p'),
                 ('import java.util.*;', 'var a = Arrays.stream(Owner.Table.values()).skip(2).toList();', 'unsupported_values_use', 'p')]
        for imported, body, expected, package in cases:
            with self.subTest(expected=expected):
                self.consumer(body, imported, package)
                report = self.run_audit()
                self.assertEqual(report['exit_code'], 3, report)
                self.assertTrue(self.findings(report, expected), report)
                self.assertFalse(report['coverage']['complete_consumer_proof'])
                self.assertTrue(report['coverage']['stable_bounded_read'])

    def test_unrelated_comments_literals_textblocks_and_other_type_ignored(self):
        self.consumer('''// assertEquals(2, Owner.Table.values().length);
        String a = "Owner.Table.values().length";
        String b = """
          Arrays.stream(Owner.Table.values()).skip(54).toList()
          """;
        assertEquals(106, Other.Table.values().length);
        /* assertEquals(2, Owner.Table.values().length); */''')
        report = self.run_audit()
        self.assertEqual(report['findings'], [])
        self.assertEqual(report['exit_code'], 0)

    def test_output_provenance_determinism_and_no_writes(self):
        self.consumer('int n = Owner.Table.values().length;')
        before_index = (self.root / '.git/index').read_bytes()
        before_files = {str(p.relative_to(self.root)): p.read_bytes() for p in (self.root / 'src').rglob('*') if p.is_file()}
        report = self.run_audit()
        self.assertEqual(report, self.run_audit())
        self.assertEqual(report['version'], 1)
        prov = report['provenance']
        self.assertEqual(prov['base_commit'], self.base)
        self.assertEqual(prov['head_before'], prov['head_after'])
        self.assertEqual(prov['base_blob'], self.git('rev-parse', self.base + ':' + SOURCE))
        self.assertEqual(prov['source_sha256_before'], hashlib.sha256((self.root / SOURCE).read_bytes()).hexdigest())
        self.assertEqual(prov['scope_sha256_before'], prov['scope_sha256_after'])
        self.assertEqual((self.root / '.git/index').read_bytes(), before_index)
        self.assertEqual(before_files, {str(p.relative_to(self.root)): p.read_bytes() for p in (self.root / 'src').rglob('*') if p.is_file()})
        finding = report['findings'][0]
        self.assertEqual(finding['sha256'], hashlib.sha256((self.root / CONSUMER).read_bytes()).hexdigest())
        human = audit.human(report)
        for item in [CONSUMER + ':4:', 'values_length', 'literal=None', 'p.Owner.Table', finding['sha256']]:
            self.assertIn(item, human)

    def test_nonatomic_source_change_detected_with_hashes(self):
        actual = audit.read_file
        calls = 0
        def racing(root, name, cap):
            nonlocal calls
            saved = actual(root, name, cap)
            if name == SOURCE:
                calls += 1
                if calls == 1:
                    self.write(SOURCE, declaration(['A', 'B', 'C', 'D']) + '// concurrent edit\n')
            return saved
        with mock.patch.object(audit, 'read_file', side_effect=racing):
            report = self.run_audit()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertIn('changed', report['error'])
        self.assertNotEqual(report['provenance']['source_sha256_before'], report['provenance']['source_sha256_after'])
        self.assertFalse(report['coverage']['stable_bounded_read'])

    def test_added_java_path_and_head_change_detected(self):
        actual = audit.enumerate_java
        calls = 0
        def changing(root, roots, limits):
            nonlocal calls
            calls += 1
            if calls == 2:
                self.write('src/test/java/p/Added.java', 'package p; class Added {}')
            return actual(root, roots, limits)
        with mock.patch.object(audit, 'enumerate_java', side_effect=changing):
            report = self.run_audit()
        self.assertEqual(report['exit_code'], 3)
        self.assertIn('path set changed', report['error'])
        with mock.patch.object(audit, 'head', side_effect=[self.base, 'f' * 40]):
            report = self.run_audit()
        self.assertEqual(report['exit_code'], 3)
        self.assertIn('HEAD', report['error'])

    def test_symlink_source_parent_scan_entry_and_root_rejected(self):
        external = Path(self.temporary.name) / 'external.java'
        external.write_text('class Secret {}')
        original = (self.root / SOURCE).read_bytes()
        (self.root / SOURCE).unlink()
        (self.root / SOURCE).symlink_to(external)
        self.assertEqual(self.run_audit()['exit_code'], 2)
        (self.root / SOURCE).unlink()
        (self.root / SOURCE).write_bytes(original)
        link = self.root / 'src/test/java/p/Link.java'
        link.symlink_to(external)
        self.assertEqual(self.run_audit()['exit_code'], 2)
        link.unlink()
        link = self.root / 'linked'
        link.symlink_to(self.root / 'src/main/java', target_is_directory=True)
        self.assertEqual(self.run_audit(source='linked/p/Owner.java')['exit_code'], 2)
        alias = Path(self.temporary.name) / 'alias'
        alias.symlink_to(self.root, target_is_directory=True)
        self.assertEqual(self.run_audit(root=str(alias))['exit_code'], 2)
        self.assertEqual(external.read_text(), 'class Secret {}')

    def test_unsafe_paths_refs_and_scope_rejected_before_git(self):
        cases = [dict(source='../Owner.java'), dict(source='/tmp/Owner.java'), dict(source='src//Owner.java'),
                 dict(source='src/./Owner.java'), dict(source='src/Owner.java/..'), dict(source='src\\Owner.java'),
                 dict(source='.git/config.java'), dict(source=':(glob)**.java'), dict(base='HEAD'),
                 dict(base=self.base + '^'), dict(base='a' * 39), dict(base=self.base.upper()),
                 dict(scan_root=['src', 'src/test/java']), dict(scan_root=['src', 'src']),
                 dict(root=str(self.root) + '/'), dict(max_hits=0)]
        for changes in cases:
            with self.subTest(changes=changes), mock.patch.object(audit, 'git', side_effect=AssertionError('Git must not run')):
                self.assertEqual(self.run_audit(**changes)['exit_code'], 2)

    def test_budget_exhaustion_is_incomplete_never_silent_success(self):
        self.consumer('assertEquals(2, Owner.Table.values().length);\n' * 3)
        for changes in [dict(max_files=1), dict(max_entries=1), dict(max_file_bytes=8),
                        dict(max_total_bytes=80, max_file_bytes=80), dict(max_tokens=6), dict(max_hits=2),
                        dict(max_context_tokens=6)]:
            with self.subTest(changes=changes):
                report = self.run_audit(**changes)
                self.assertEqual(report['exit_code'], 3, report)
                self.assertIn('budget', report['error'].lower())
                self.assertFalse(report['coverage']['stable_bounded_read'])

    def test_git_large_output_bounded_and_argv_only(self):
        with self.assertRaises(audit.AuditError) as caught:
            audit.git(self.root, ['cat-file', 'blob', self.git('rev-parse', self.base + ':' + SOURCE)], cap=2)
        self.assertEqual(caught.exception.code, 3)
        actual = subprocess.Popen
        seen = []
        def observe(argv, **kwargs):
            seen.append((argv, kwargs.get('shell'), kwargs.get('env')))
            return actual(argv, **kwargs)
        with mock.patch.object(audit.subprocess, 'Popen', side_effect=observe):
            self.assertEqual(self.run_audit()['exit_code'], 0)
        self.assertTrue(seen)
        self.assertTrue(all(isinstance(argv, list) and shell is False for argv, shell, _ in seen))
        self.assertTrue(all(env['GIT_OPTIONAL_LOCKS'] == '0' and env['GIT_NO_REPLACE_OBJECTS'] == '1' and
                            env['GIT_NO_LAZY_FETCH'] == '1' and env['GIT_ALLOW_PROTOCOL'] == ''
                            for _, _, env in seen))
        self.assertFalse(any(arg in ('status', 'diff', 'checkout', 'fetch', 'update-index', 'hash-object')
                             for argv, _, _ in seen for arg in argv))

    def test_generic_different_enum_owner_not_game_specific(self):
        source = 'src/main/java/p/Registry.java'
        self.write(source, declaration(['LEFT', 'RIGHT'], owner='Registry', enum='Mode'))
        self.base = self.commit()
        self.write(source, declaration(['LEFT', 'RIGHT', 'MIDDLE'], owner='Registry', enum='Mode'))
        self.consumer('assertEquals(2, Registry.Mode.values().length);')
        report = self.run_audit(source=source, enum='Mode', type='Registry.Mode')
        self.assertEqual(report['exit_code'], 1, report)
        self.assertEqual(report['enum']['type'], 'p.Registry.Mode')
        self.assertEqual(self.findings(report, 'stale_total_assertion')[0]['literal'], 2)

    def test_looping_root_symlink_is_json_rejection(self):
        loop = Path(self.temporary.name) / 'loop'
        loop.symlink_to(loop)
        report = self.run_audit(root=str(loop))
        self.assertEqual(report['exit_code'], 2)
        self.assertEqual(report['status'], 'rejected')

    def test_shell_metacharacters_are_literal_paths_not_commands(self):
        source = 'src/main/java/p/Owner$(touch PWN)`touch PWN2`[x].java'
        self.write(source, declaration(['A', 'B']))
        self.base = self.commit()
        self.write(source, declaration(['A', 'B', 'C']))
        report = self.run_audit(source=source)
        self.assertEqual(report['exit_code'], 0, report)
        self.assertFalse((self.root / 'PWN').exists())
        self.assertFalse((self.root / 'PWN2').exists())

    def test_baseline_symlink_missing_source_noncommit_and_subdir_root(self):
        self.assertEqual(self.run_audit(source='missing.java')['exit_code'], 2)
        blob = self.git('rev-parse', self.base + ':' + SOURCE)
        self.assertEqual(self.run_audit(base=blob)['exit_code'], 2)
        self.assertEqual(self.run_audit(root=str(self.root / 'src'))['exit_code'], 2)
        (self.root / SOURCE).unlink()
        (self.root / SOURCE).symlink_to('Other.java')
        self.base = self.commit()
        (self.root / SOURCE).unlink()
        self.write(SOURCE, declaration(['A', 'B', 'C']))
        self.assertEqual(self.run_audit()['exit_code'], 2)

    def test_unparseable_consumer_is_reported_incomplete(self):
        self.consumer('String a = "unterminated;')
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 3)
        self.assertEqual(report['issues'][0]['kind'], 'unsupported_java')
        self.assertTrue(report['coverage']['stable_bounded_read'])

    def test_unsupported_assertion_and_stream_grammar_not_certified(self):
        for body, kind in [
            ('assertEquals(2, Owner.Table.values().length + other);', 'unsupported_assertion_expression'),
            ('assertEquals("message", 2, Owner.Table.values().length);', 'unsupported_assertion_expression'),
            ('var xs = java.util.Arrays.stream(Owner.Table.values()).<String>map(Object::toString).skip(54).toList();',
             'unsupported_stream_chain')]:
            with self.subTest(kind=kind):
                self.consumer(body)
                report = self.run_audit()
                self.assertEqual(report['exit_code'], 3, report)
                self.assertTrue(self.findings(report, kind), report)

    def test_removed_file_during_second_observation_is_incomplete(self):
        actual = audit.read_file
        calls = 0
        def removing(root, name, cap):
            nonlocal calls
            if name == CONSUMER:
                calls += 1
                if calls == 2:
                    (self.root / CONSUMER).unlink()
            return actual(root, name, cap)
        with mock.patch.object(audit, 'read_file', side_effect=removing):
            report = self.run_audit()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertFalse(report['coverage']['stable_bounded_read'])

    def test_change_within_single_read_and_cap_checked_before_content(self):
        with mock.patch.object(audit.os, 'read', side_effect=AssertionError('Must not read oversized content')):
            with self.assertRaises(audit.AuditError) as caught:
                audit.read_file(self.root, SOURCE, 1)
            self.assertEqual(caught.exception.code, 3)
        actual = audit.os.read
        changed = False
        def modifying(fd, amount):
            nonlocal changed
            data = actual(fd, amount)
            if not changed:
                changed = True
                self.write(SOURCE, declaration(['A', 'B', 'C', 'D']) + ' ')
            return data
        with mock.patch.object(audit.os, 'read', side_effect=modifying):
            with self.assertRaises(audit.AuditError) as caught:
                audit.read_file(self.root, SOURCE, 1024)
        self.assertEqual(caught.exception.code, 3)
        self.assertIn('changed during read', str(caught.exception))

    def test_git_replacement_objects_cannot_change_immutable_base(self):
        old_blob = self.git('rev-parse', self.base + ':' + SOURCE)
        self.write('replacement.txt', declaration(['WRONG']))
        replacement = self.git('hash-object', '-w', '--', 'replacement.txt')
        self.git('replace', old_blob, replacement)
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['enum']['old_names'], ['A', 'B'])
        self.assertEqual(report['provenance']['base_blob'], old_blob)

    def test_nonregular_java_file_rejected_without_blocking(self):
        os.mkfifo(self.root / 'src/test/java/p/Pipe.java')
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 2)
        self.assertIn('Non-regular Java source', report['error'])

    def test_finding_cap_retains_bounded_partial_inventory(self):
        self.consumer('assertEquals(2, Owner.Table.values().length);' * 4)
        report = self.run_audit(max_hits=2)
        self.assertEqual(report['exit_code'], 3)
        self.assertEqual(len(report['findings']), 2)
        self.assertIn('Finding budget', report['error'])

    def enum_local_fixture(self, call='values()', extra_members='', owner_members=''):
        source = 'catalog/QuestCatalog.java'
        self.write(source, 'package adventure; public class QuestCatalog { public enum Stage { TOWN, FOREST } }')
        self.write('checks/InventoryCheck.java', 'package checks; class InventoryCheck {}')
        self.base = self.commit()
        self.write(source, 'package adventure;\npublic class QuestCatalog {\n public enum Stage {\n'
                   '  TOWN, FOREST, CAVE;\n'
                   f'  static void verify() {{ assertEquals(2, {call}.length); }}\n'
                   f'  {extra_members}\n }}\n {owner_members}\n}}\n')
        return dict(source=source, enum='Stage', type='adventure.QuestCatalog.Stage', scan_root=['catalog', 'checks'])

    def test_enum_local_unqualified_values_reports_incomplete_with_provenance(self):
        changes = self.enum_local_fixture()
        source = changes['source']
        before_source = (self.root / source).read_bytes()
        before_index = (self.root / '.git/index').read_bytes()
        report = self.run_audit(**changes)
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(report['status'], 'incomplete')
        self.assertEqual((report['enum']['old_count'], report['enum']['new_count']), (2, 3))
        self.assertTrue(report['enum']['append_only_prefix'])
        self.assertEqual(report['enum']['changed_ordinals'], [])
        self.assertEqual(report['version'], 1)
        self.assertEqual(report['coverage']['unsupported_observations'], 1)
        self.assertTrue(report['coverage']['stable_bounded_read'])
        self.assertFalse(report['coverage']['complete_consumer_proof'])
        self.assertEqual(len(report['findings']), 1)
        finding = report['findings'][0]
        self.assertEqual((finding['path'], finding['line'], finding['kind'], finding['severity']),
                         (source, 5, 'unsupported_implicit_values', 'incomplete'))
        self.assertEqual(finding['type'], 'adventure.QuestCatalog.Stage')
        self.assertEqual(finding['sha256'], hashlib.sha256(before_source).hexdigest())
        self.assertEqual(report['issues'][0]['kind'], finding['kind'])
        prov = report['provenance']
        self.assertEqual(prov['base_commit'], self.base)
        self.assertEqual(prov['base_blob'], self.git('rev-parse', self.base + ':' + source))
        self.assertEqual(prov['head_before'], prov['head_after'])
        self.assertEqual(prov['source_sha256_before'], finding['sha256'])
        self.assertEqual(prov['source_sha256_after'], finding['sha256'])
        self.assertEqual(prov['scope_sha256_before'], prov['scope_sha256_after'])
        self.assertEqual((self.root / source).read_bytes(), before_source)
        self.assertEqual((self.root / '.git/index').read_bytes(), before_index)

    def test_enum_local_qualified_values_keeps_stale_assertion_detection(self):
        report = self.run_audit(**self.enum_local_fixture(call='Stage.values()'))
        self.assertEqual(report['exit_code'], 1, report)
        self.assertEqual(report['issues'], [])
        self.assertEqual(len(report['findings']), 1)
        finding = report['findings'][0]
        self.assertEqual((finding['kind'], finding['literal'], finding['line']), ('stale_total_assertion', 2, 5))
        self.assertEqual(finding['resolution'], 'enum_source_simple_name')

    def test_unrelated_unqualified_values_in_other_scopes_are_ignored(self):
        changes = self.enum_local_fixture(call='new int[3]', extra_members='''
          enum Other { A; static void check() { assertEquals(106, values().length); } }
          static class Helper { static int[] values() { return new int[106]; }
            static void check() { assertEquals(106, values().length); } }
          static Object helper = new Object() {
            int[] values() { return new int[106]; }
            void check() { assertEquals(106, values().length); }
          };
          static void overload() { values(106); }
          static int[] values(int size) { return new int[size]; }
          String example = "values().length";
          // assertEquals(2, values().length);
        ''', owner_members='''
          enum Sibling { A; static void check() { assertEquals(106, values().length); } }
          static int[] values() { return new int[106]; }
          static void check() { assertEquals(106, values().length); }
        ''')
        self.write('checks/InventoryCheck.java', 'package checks; class InventoryCheck { '
                   'int[] values() { return new int[106]; } void check() { assertEquals(106, values().length); } }')
        report = self.run_audit(**changes)
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['findings'], [])
        self.assertEqual(report['issues'], [])

    def test_unqualified_annotation_name_is_not_enum_call(self):
        changes = self.enum_local_fixture(call='new int[3]', extra_members='@values() static void marker() {}',
                                          owner_members='@interface values {}')
        report = self.run_audit(**changes)
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['findings'], [])
        self.assertEqual(report['issues'], [])

    def test_unqualified_constructor_name_is_not_enum_call(self):
        changes = self.enum_local_fixture(call='new int[3]', extra_members='static Object helper = new values();',
                                          owner_members='static class values {}')
        report = self.run_audit(**changes)
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['findings'], [])
        self.assertEqual(report['issues'], [])

    def test_enum_local_anonymous_type_context_is_bounded(self):
        args = ','.join('1' for _ in range(30))
        changes = self.enum_local_fixture(call='new int[3]',
                                          extra_members=f'static Object helper = new Helper({args}) {{ void check() {{ values(); }} }};')
        report = self.run_audit(**changes, max_context_tokens=12)
        self.assertEqual(report['exit_code'], 3, report)
        self.assertIn('Anonymous type context budget', report['error'])
        self.assertFalse(report['coverage']['stable_bounded_read'])

    def test_enum_local_implicit_values_respects_scan_scope(self):
        changes = self.enum_local_fixture()
        changes['scan_root'] = ['checks']
        report = self.run_audit(**changes)
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['findings'], [])
        self.assertFalse(report['coverage']['complete_consumer_proof'])

    def test_enum_local_implicit_values_respects_finding_budget(self):
        changes = self.enum_local_fixture(extra_members='static void again() { values(); values(); }')
        report = self.run_audit(**changes, max_hits=2)
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(len(report['findings']), 2)
        self.assertTrue(all(f['kind'] == 'unsupported_implicit_values' for f in report['findings']))
        self.assertIn('Finding budget', report['error'])

    def test_enum_local_unqualified_values_cli_reports_incomplete(self):
        changes = self.enum_local_fixture()
        argv = [sys.executable, '-B', str(HERE / 'enum_contract_audit.py'), '--root', str(self.root),
                '--base', self.base, '--source', changes['source'], '--enum', changes['enum'],
                '--type', changes['type'], '--scan-root', 'catalog', '--scan-root', 'checks', '--json']
        result = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
        self.assertEqual(result.returncode, 3, result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual(report['version'], 1)
        self.assertEqual(report['exit_code'], result.returncode)
        self.assertEqual(report['findings'][0]['kind'], 'unsupported_implicit_values')
        self.assertTrue(report['coverage']['stable_bounded_read'])

    def test_cli_json_help_invalid_inputs_and_no_apply(self):
        argv = [sys.executable, '-B', str(HERE / 'enum_contract_audit.py'), '--root', str(self.root),
                '--base', self.base, '--source', SOURCE, '--enum', 'Table', '--type', 'Owner.Table',
                '--scan-root', 'src/test/java', '--json']
        result = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)['version'], 1)
        for extra in [['--apply'], ['--base', 'HEAD'], ['--max-hits', 'not-an-int']]:
            result = subprocess.run(argv + extra, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(json.loads(result.stdout)['status'], 'rejected')
        result = subprocess.run(argv[:3] + ['--help'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
        self.assertEqual(result.returncode, 0)
        self.assertIn(b'Exit:', result.stdout)


class DirectEnumConsumers(unittest.TestCase):
    """Inventory immediate source shapes without inferring Java execution."""
    setUp = TemporaryRepo.setUp
    git = TemporaryRepo.git
    commit = TemporaryRepo.commit
    write = TemporaryRepo.write
    run_audit = TemporaryRepo.run_audit
    findings = TemporaryRepo.findings
    consumer = TemporaryRepo.consumer

    def test_direct_foreach_source_inventory(self):
        self.consumer('for (var source : Owner.Table.values()) { use(source); }\n'
                      'for (final p.Owner.Table asset : p.Owner.Table.values()) if (keep(asset)) use(asset);')
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 0, report)
        rows = self.findings(report, 'values_foreach')
        self.assertEqual(len(rows), 2, report)
        self.assertTrue(all(row['source_shape'] == 'enhanced_for_source' for row in rows))
        self.assertTrue(all(row['literal'] is None for row in rows))
        self.assertFalse(report['coverage']['complete_consumer_proof'])
        self.assertTrue(report['coverage']['stable_bounded_read'])
        self.assertTrue(all(row['type'] == 'p.Owner.Table' and row['sha256'] ==
                            hashlib.sha256((self.root / CONSUMER).read_bytes()).hexdigest() for row in rows))

    def test_direct_array_index_inventory_without_range_inference(self):
        self.consumer('var first = Owner.Table.values()[0];\n'
                      'var key = Owner.Table.values()[i];\n'
                      'var later = Owner.Table.values()[offset + i];\n'
                      'var unknown = Owner.Table.values()[choose(indices[0])];\n'
                      'var negative = Owner.Table.values()[-1];')
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 0, report)
        rows = self.findings(report, 'values_index')
        self.assertEqual([row['literal'] for row in rows], [0, None, None, None, -1])
        self.assertEqual([row['index_expression'] for row in rows], ['0', 'i', 'offset+i', 'choose(indices[0])', '-1'])
        self.assertTrue(all(row['source_shape'] == 'direct_array_index' for row in rows))
        self.assertTrue(all('index_in_range' not in row and 'loop_bound' not in row for row in rows))
        self.assertFalse(report['coverage']['complete_consumer_proof'])

    def test_direct_as_list_sublist_inventory_keeps_old_prefix_bound(self):
        self.consumer('assertEquals(expected, Arrays.asList(Owner.Table.values()).subList(0, 2));\n'
                      'var appended = Arrays.asList(Owner.Table.values()).subList(2, 4);\n'
                      'var empty = Arrays.asList(Owner.Table.values()).subList(2, 2);',
                      'import java.util.Arrays;')
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 0, report)
        rows = self.findings(report, 'bounded_list_sublist')
        self.assertEqual([(row['start_inclusive'], row['end_exclusive']) for row in rows], [(0, 2), (2, 4), (2, 2)])
        self.assertTrue(all(row['source_shape'] == 'arrays_as_list_sublist' and row['severity'] == 'inventory' for row in rows))
        self.assertFalse(self.findings(report, 'stale_total_assertion'))
        self.assertFalse(report['coverage']['complete_consumer_proof'])

    def test_direct_other_enum_owner_and_exact_imports(self):
        source = 'catalog/Choice.java'
        self.write(source, 'package example; class Catalog { enum Choice { X, Y; } }')
        self.base = self.commit()
        self.write(source, 'package example; class Catalog { enum Choice { X, Y, Z; } }')
        cases = [('import example.Catalog;', 'Catalog.Choice'),
                 ('import example.Catalog.Choice;', 'Choice'),
                 ('import static example.Catalog.Choice;', 'Choice'),
                 ('', 'example.Catalog.Choice')]
        for imported, spelling in cases:
            with self.subTest(spelling=spelling, imported=imported):
                self.consumer(f'for (var item : {spelling}.values()) use(item);\n'
                              f'var item = {spelling}.values()[i];\n'
                              f'var subset = asList({spelling}.values()).subList(0, 2);',
                              imported + '\nimport static java.util.Arrays.asList;', package='other')
                report = self.run_audit(source=source, enum='Choice', type='example.Catalog.Choice')
                self.assertEqual(report['exit_code'], 0, report)
                self.assertCountEqual([row['kind'] for row in report['findings']],
                                      ['values_foreach', 'values_index', 'bounded_list_sublist'])
                self.assertTrue(all(row['type'] == 'example.Catalog.Choice' for row in report['findings']))

    def test_direct_as_list_wrapper_spellings_and_literal_radices(self):
        cases = [('', 'java.util.Arrays.asList'), ('import java.util.Arrays;', 'Arrays.asList'),
                 ('import static java.util.Arrays.asList;', 'asList')]
        for imported, wrapper in cases:
            with self.subTest(wrapper=wrapper):
                self.consumer(f'var subset = {wrapper}(p.Owner.Table.values()).subList((0x0), +0b10);',
                              imported, package='other')
                report = self.run_audit()
                self.assertEqual(report['exit_code'], 0, report)
                row = report['findings'][0]
                self.assertEqual((row['kind'], row['start_inclusive'], row['end_exclusive']),
                                 ('bounded_list_sublist', 0, 2))
                self.assertEqual(row['wrapper'], wrapper)

    def test_direct_unrelated_enum_is_not_attributed(self):
        self.consumer('for (var x : Other.Table.values()) use(x);\n'
                      'var x = Other.Table.values()[0];\n'
                      'var ys = java.util.Arrays.asList(Other.Table.values()).subList(0, 2);')
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(report['findings'], [])

    def test_direct_unsupported_receiver_wrappers_and_foreach_headers(self):
        bodies = ['for (var x : (Owner.Table.values())) use(x);',
                  'for (var x : identity(Owner.Table.values())) use(x);',
                  'for (@Tag var x : Owner.Table.values()) use(x);',
                  'var x = (Owner.Table.values())[0];',
                  'var x = identity(Owner.Table.values())[0];',
                  'var xs = java.util.Arrays.asList((Owner.Table.values())).subList(0, 2);',
                  'var xs = java.util.Arrays.asList(identity(Owner.Table.values())).subList(0, 2);',
                  'var xs = java.util.Arrays.asList(Owner.Table.values(), other).subList(0, 2);',
                  'var xs = java.util.Arrays.asList(Owner.Table.values());',
                  'var xs = java.util.Arrays.asList(Owner.Table.values()).stream().toList();',
                  'var xs = java.util.Arrays.asList(Owner.Table.values()).<Object>subList(0, 2);']
        for body in bodies:
            with self.subTest(body=body):
                self.consumer(body)
                report = self.run_audit()
                self.assertEqual(report['exit_code'], 3, report)
                self.assertTrue(report['issues'])
                self.assertFalse(report['coverage']['complete_consumer_proof'])

    def test_direct_sublist_complex_or_invalid_bounds_stay_incomplete(self):
        for bounds in ['FIRST, LAST', '0, 1 + 1', '0, upper()', '0', '0, 2, 3',
                       '-1, 2', '3, 2', '0, 5', '0, 0x7fffffffffffffff', '0,']:
            with self.subTest(bounds=bounds):
                self.consumer(f'var xs = java.util.Arrays.asList(Owner.Table.values()).subList({bounds});')
                report = self.run_audit()
                self.assertEqual(report['exit_code'], 3, report)
                self.assertFalse(self.findings(report, 'bounded_list_sublist'))
                self.assertTrue(report['issues'])

    def test_direct_empty_opaque_and_nested_receiver_indexes_stay_incomplete(self):
        for index, tail in [('', ''), ('"hidden"', ''), ('i; j', ''), ('i, j', ''), ('i', '[j]')]:
            with self.subTest(index=index, tail=tail):
                self.consumer(f'var key = Owner.Table.values()[{index}]{tail};')
                report = self.run_audit()
                self.assertEqual(report['exit_code'], 3, report)
                self.assertFalse(self.findings(report, 'values_index'))
                self.assertTrue(report['issues'])

    def test_direct_wildcard_ambiguous_and_obviously_shadowed_names_stay_incomplete(self):
        cases = [('import p.Owner.*;', 'for (var x : Table.values()) use(x);', 'other'),
                 ('import java.util.*;', 'var xs = Arrays.asList(Owner.Table.values()).subList(0, 2);', 'p'),
                 ('import p.Owner.Table; import q.Other.Table;', 'var x = Table.values()[i];', 'other'),
                 ('import p.Owner.Table;', 'class Table {} for (var x : Table.values()) use(x);', 'other'),
                 ('', 'var Owner = alias; var x = Owner.Table.values()[i];', 'p'),
                 ('import java.util.Arrays;', 'var Arrays = alias; var xs = Arrays.asList(Owner.Table.values()).subList(0, 2);', 'p'),
                 ('import static java.util.Arrays.asList;', 'var asList = alias; var xs = asList(Owner.Table.values()).subList(0, 2);', 'p')]
        for imported, body, package in cases:
            with self.subTest(body=body, imported=imported):
                self.consumer(body, imported, package)
                report = self.run_audit()
                self.assertEqual(report['exit_code'], 3, report)
                self.assertTrue(report['issues'])
                self.assertFalse(report['coverage']['complete_consumer_proof'])

    def test_direct_new_forms_respect_context_and_finding_budgets(self):
        for body in ['for (var x : p.Owner.Table.values()) use(x);',
                     'var x = Owner.Table.values()[choose(1, 2, 3, 4, 5, 6)];',
                     'var xs = java.util.Arrays.asList(Owner.Table.values()).subList(0, 2);']:
            with self.subTest(body=body):
                self.consumer(body)
                report = self.run_audit(max_context_tokens=8)
                self.assertEqual(report['exit_code'], 3, report)
                self.assertIn('context budget', report.get('error', ''))
                self.assertFalse(report['coverage']['stable_bounded_read'])
        self.consumer('for (var x : Owner.Table.values()) use(x);\n'
                      'var key = Owner.Table.values()[i];\n'
                      'var xs = java.util.Arrays.asList(Owner.Table.values()).subList(0, 2);')
        report = self.run_audit(max_hits=2)
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual([row['kind'] for row in report['findings']], ['values_foreach', 'values_index'])
        self.assertIn('Finding budget', report['error'])

    def test_direct_partial_inventory_stops_before_third_consumer(self):
        self.consumer('var key = Owner.Table.values()[i];')
        later = 'src/test/java/p/ZLater.java'
        self.write(later, 'package p; class ZLater { void check() { Owner.Table.values()[0]; } }')
        self.write('src/test/java/p/ZZFinal.java', 'package p; class ZZFinal { Object key = Owner.Table.values()[0]; }')
        original = audit.inspect_consumers
        seen = []
        def inspect(java, path, *args):
            seen.append(path)
            return original(java, path, *args)
        with mock.patch.object(audit, 'inspect_consumers', inspect):
            report = self.run_audit(max_hits=1)
        self.assertEqual(report['exit_code'], 3, report)
        self.assertEqual(len(report['findings']), 1)
        self.assertEqual(report['findings'][0]['kind'], 'values_index')
        self.assertEqual(seen, [CONSUMER, later])
        self.assertIsNone(report['provenance']['head_after'])

    def test_direct_inventory_keeps_read_recheck_and_existing_review_exits(self):
        self.consumer('for (var x : Owner.Table.values()) use(x);\n'
                      'var key = Owner.Table.values()[i];\n'
                      'var xs = java.util.Arrays.asList(Owner.Table.values()).subList(0, 2);\n'
                      'assertEquals(2, Owner.Table.values().length);')
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 1, report)
        self.assertEqual(len(self.findings(report, 'stale_total_assertion')), 1)
        self.assertTrue(report['coverage']['stable_bounded_read'])
        original = audit.read_file
        reads = 0
        def changed(root, name, cap):
            nonlocal reads
            if name == CONSUMER:
                reads += 1
                if reads == 2:
                    self.write(CONSUMER, 'package p; class Changed {}')
            return original(root, name, cap)
        with mock.patch.object(audit, 'read_file', changed):
            report = self.run_audit()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertIn('changed between observations', report['error'])
        self.assertFalse(report['coverage']['stable_bounded_read'])

    def test_direct_indirect_qualifier_and_malformed_foreach_declaration_stay_incomplete(self):
        for body in ['var x = factory().Owner.Table.values()[i];',
                     'for (var x : factory().Owner.Table.values()) use(x);',
                     'for (int extra x : Owner.Table.values()) use(x);',
                     'var xs = java.util.Arrays.asList(Owner.Table.values()).subList(0, 2).subList(0, 1);',
                     'var xs = java.util.Arrays.asList(Owner.Table.values()).subList(0, 2).get(0);']:
            with self.subTest(body=body):
                self.consumer(body)
                report = self.run_audit()
                self.assertEqual(report['exit_code'], 3, report)
                self.assertTrue(report['issues'])

    def test_direct_local_method_shadow_of_static_wrapper_is_incomplete(self):
        self.write(CONSUMER, 'package p; import static java.util.Arrays.asList; '
                   'class TestMappings { Object asList(Object[] x) { return x; } '
                   'void verify() { var xs = asList(Owner.Table.values()).subList(0, 2); } }')
        report = self.run_audit()
        self.assertEqual(report['exit_code'], 3, report)
        self.assertFalse(self.findings(report, 'bounded_list_sublist'))
        self.assertTrue(report['issues'])

    def test_direct_pinned_r11b_six_source_shapes_after_80_to_84(self):
        source = 'catalog/CdR11bAssets.java'
        names = [f'ASSET_{i}' for i in range(80)]
        self.write(source, declaration(names, 'CdR11bAssets', 'Asset'))
        self.base = self.commit()
        self.write(source, declaration([*names, 'SPRING_SPEEDS', 'ANI_SPRING', 'MAP_SPR_SPRING', 'OBJECT_TILE_IDS'],
                                       'CdR11bAssets', 'Asset'))
        bodies = [
            ('for (var source : CdR11bAssets.Asset.values()) {\n'
             '    if (source.isPlcSource() && source.range().nativeAddress() == pointer) { return source; }\n}', 'values_foreach'),
            ('for (var asset : CdR11bAssets.Asset.values()) if (asset.isPlcSource()) {\n'
             '    var source = source(assets, binding, asset);\n'
             '    sources.add(source); inputMap.put(asset, source);\n}', 'values_foreach'),
            ('var c = rows.get(i).split(" "); var key = CdR11bAssets.Asset.values()[i];', 'values_index'),
            ('assertEquals(List.of(RING_DELTAS, ANI_RING, MAP_SPR_RING),\n'
             '        Arrays.asList(CdR11bAssets.Asset.values()).subList(77, 80));', 'bounded_list_sublist'),
            ('assertEquals(List.of(TOUCH_DIMENSIONS, ENEMY_POINTS), Arrays.asList(CdR11bAssets.Asset.values()).subList(70, 72));',
             'bounded_list_sublist'),
            ('assertEquals(SPRING_TABLES, Arrays.asList(CdR11bAssets.Asset.values()).subList(80, 84));', 'bounded_list_sublist'),
        ]
        for body, kind in bodies:
            with self.subTest(kind=kind, body=body):
                self.consumer(body, 'import java.util.Arrays;')
                report = self.run_audit(source=source, enum='Asset', type='p.CdR11bAssets.Asset')
                self.assertEqual(report['exit_code'], 0, report)
                self.assertTrue(report['enum']['append_only_prefix'])
                self.assertEqual([row['kind'] for row in report['findings']], [kind])
                self.assertEqual(report['issues'], [])
                self.assertFalse(report['coverage']['complete_consumer_proof'])


    def test_direct_same_named_source_enum_is_not_attributed_to_selected_owner(self):
        for nested in (True, False):
            with self.subTest(nested=nested):
                other = 'class Other { enum Table { X, Y; } void verify() { ' + \
                    'for (var item : Table.values()) use(item); ' + \
                    'var item = Table.values()[0]; ' + \
                    'var subset = java.util.Arrays.asList(Table.values()).subList(0, 2); } }'
                selected = 'enum Table { A, B, C, D; }'
                text = ('package p; class Owner { ' + selected + ' ' + other + ' }\n') if nested else \
                    ('package p; class Owner { ' + selected + ' } ' + other + '\n')
                self.write(SOURCE, text)
                report = self.run_audit(scan_root=['src/main/java'])
                self.assertEqual(report['exit_code'], 3, report)
                self.assertEqual(len(report['issues']), 3, report)
                self.assertTrue(all(row['kind'] == 'unresolved_type_reference' for row in report['findings']))
                self.assertFalse(report['coverage']['complete_consumer_proof'])

    def test_direct_source_target_remains_inventory_when_binding_is_explicit(self):
        forms = lambda spelling: (f'for (var item : {spelling}.values()) use(item); ' +
                                  f'var item = {spelling}.values()[0]; ' +
                                  f'var subset = java.util.Arrays.asList({spelling}.values()).subList(0, 2);')
        self.write(SOURCE, 'package p; class Owner { enum Table { A, B, C, D; } void verify() { ' + forms('Table') + ' } }\n')
        report = self.run_audit(scan_root=['src/main/java'])
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(len(report['findings']), 3, report)
        self.write(SOURCE, 'package p; class Owner { enum Table { A, B, C, D; } ' +
                   'class Other { enum Table { X, Y; } } void verify() { ' +
                   forms('Owner.Table') + forms('p.Owner.Table') + ' } }\n')
        report = self.run_audit(scan_root=['src/main/java'])
        self.assertEqual(report['exit_code'], 0, report)
        self.assertEqual(len(report['findings']), 6, report)
        self.assertTrue(all(row['type'] == 'p.Owner.Table' for row in report['findings']))
        self.assertFalse(report['coverage']['complete_consumer_proof'])


if __name__ == '__main__':
    unittest.main()
