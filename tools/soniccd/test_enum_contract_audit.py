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
              escaped \\" quote, commas,,, 
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


if __name__ == '__main__':
    unittest.main()
