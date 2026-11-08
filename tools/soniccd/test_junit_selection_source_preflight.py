"""Source-shaped regressions and adversarial controls; no Java execution."""
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import junit_selection_source_preflight as preflight


class SelectionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / 'git-fixture'
        self.repo.mkdir()
        self.git('init', '-q')
        self.git('config', 'user.name', 'Fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.put('p.Frame', 'package p; import org.junit.jupiter.api.Test; class Frame { @Test void chosen() {} }')
        self.put('p.Boot', self.boot())
        self.put('level.Body', 'package level; import org.junit.jupiter.api.Test; class Body {' +
                 ''.join('@Test void case%d() {}' % n for n in range(315)) + '}')
        self.git('add', '.')
        self.git('commit', '-qm', 'Synthetic immutable source')
        self.commit = self.git('rev-parse', 'HEAD').strip()
        self.spec = self.root / 'handoff.json'
        self.value = self.handoff(['p.Frame'])

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args], text=True, stderr=subprocess.DEVNULL)

    def put(self, name, source):
        p = self.repo / ('src/test/java/' + name.replace('.', '/') + '.java')
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(source)

    def boot(self):
        return ('package p; import org.junit.jupiter.api.Test; import org.junit.jupiter.api.Nested; class Boot {' +
                '@Test void outer1() {} @Test void outer2() {} @Nested class ActualUsa {' +
                ''.join('@Test void inner%d() {}' % n for n in range(10)) + '}}')

    def handoff(self, classes):
        return dict(version=1, id='synthetic', packet=dict(version=1, worktree_head='1'*40, files=[
            dict(source=str(self.root / 'unused.java'), destination='src/main/java/p/Unused.java',
                 sha256='0'*64, bytes=0, mode='0444', utf8=True, lf=True, expected={'absent': True})]),
            dependencies=[], api_changes=[], known_defects=[], tests=dict(classes=classes, properties={},
            expected_tests={n: 1 for n in classes}, java_major=25))

    def mixed(self):
        self.value = self.handoff(['p.Frame', 'p.Boot'])
        self.value['tests'].update(methods={'p.Frame': ['chosen']}, expected_cases={'p.Frame': ['p.Frame#chosen']})
        self.value['tests']['expected_tests']['p.Boot'] = 12

    def groups(self):
        self.value['tests']['report_groups'] = {
            'p.Frame': {'p.Frame': {'expected_tests': 1, 'owners': ['p.Frame']}},
            'p.Boot': {'p.Boot': {'expected_tests': 2, 'owners': ['p.Boot']},
                       'p.Boot$ActualUsa': {'expected_tests': 10, 'owners': ['p.Boot$ActualUsa']}}}

    def save(self):
        if self.spec.exists():
            self.spec.chmod(0o644)
        self.spec.write_text(json.dumps(self.value) + '\n')
        self.spec.chmod(0o444)

    def run_check(self, **limits):
        self.save()
        return preflight.check(str(self.repo / '.git'), self.commit, str(self.spec), **limits)

    def overlay(self, name, source):
        p = self.root / 'incoming.java'
        if p.exists():
            p.chmod(0o644)
        p.write_text(source)
        p.chmod(0o444)
        b = p.read_bytes()
        self.value['packet']['files'] = [dict(source=str(p), destination='src/test/java/' + name.replace('.', '/') + '.java',
            sha256=hashlib.sha256(b).hexdigest(), bytes=len(b), mode='0444', utf8=True, lf=True, expected={'absent': True})]
        return p

    def test_r115_wrong_body_root_not_remapped(self):
        self.value = self.handoff(['player.Body'])
        r = self.run_check()
        self.assertEqual(r['status'], 'INCOMPLETE')
        self.assertIn('missing_conventional_source', [f['kind'] for f in r['findings']])
        self.assertEqual(r['roots'][0]['class'], 'player.Body')
        self.assertEqual(len(r['roots']), 1)
        self.value = self.handoff(['level.Body'])
        self.assertEqual(self.run_check()['status'], 'BOUNDED_SOURCE_ONLY')

    def test_r115_mixed_whole_boot_requires_actualusa(self):
        self.mixed()
        r = self.run_check()
        self.assertEqual(r['topology'], 'mixed_literal_methods_and_whole_roots')
        self.assertEqual(r['status'], 'INCOMPLETE')
        self.assertIn('p.Boot$ActualUsa', [s['class'] for s in r['roots'][1]['scopes']])
        self.assertIn('report_group_scope_mismatch', [f['kind'] for f in r['findings']])
        self.groups()
        self.assertEqual(self.run_check()['status'], 'BOUNDED_SOURCE_ONLY')

    def test_methodless_nested_still_needs_groups(self):
        self.value = self.handoff(['p.Boot'])
        self.assertEqual(self.run_check()['topology'], 'methodless_whole_roots')
        self.assertEqual(self.run_check()['status'], 'INCOMPLETE')

    def test_split_package_identifier_tokens_are_incomplete(self):
        for name, declaration in [('pack.Frame', 'package pa ck;'),
                                  ('pq.Frame', 'package p/**/q;')]:
            with self.subTest(declaration=declaration):
                self.value = self.handoff([name])
                self.overlay(name, declaration + ' import org.junit.jupiter.api.Test; '
                             'class Frame { @Test void x(){} }\n')
                result = self.run_check()
                self.assertEqual(result['status'], 'INCOMPLETE')
                self.assertFalse(result['roots'][0]['root_matches'])
                self.assertIsNone(result['roots'][0]['package'])

    def test_split_import_identifier_tokens_are_incomplete(self):
        for imported in ['Te st', 'Te/**/st']:
            with self.subTest(imported=imported):
                self.value = self.handoff(['p.Frame'])
                self.overlay('p.Frame', 'package p; import org.junit.jupiter.api.' + imported +
                             '; class Frame { @Test void x(){} }\n')
                result = self.run_check()
                self.assertEqual(result['status'], 'INCOMPLETE')
                self.assertFalse(result['roots'][0]['root_matches'])
                self.assertIsNone(result['roots'][0]['package'])

    def test_spaced_qualified_header_boundaries_remain_supported(self):
        self.value = self.handoff(['p.q.Frame'])
        self.overlay('p.q.Frame', 'package p /*gap*/ . q; '
                     'import org . junit . jupiter . api . Test; '
                     'import static org.junit.jupiter.api.Assertions.*; '
                     'class Frame { @Test void x(){} }\n')
        result = self.run_check()
        self.assertEqual(result['status'], 'BOUNDED_SOURCE_ONLY')
        self.assertEqual(result['roots'][0]['package'], 'p.q')

    def test_nonleading_import_cannot_supply_annotation_binding(self):
        self.overlay('p.Frame', 'package p; class Frame { '
                     '@org.junit.jupiter.api.Test void x(){ '
                     'import org.junit.jupiter.api.Test; } }\n')
        self.assertEqual(self.run_check()['status'], 'INCOMPLETE')

    def test_flat_and_no_expected_count_inference(self):
        self.value['tests']['expected_tests']['p.Frame'] = 999
        r = self.run_check()
        self.assertEqual(r['status'], 'BOUNDED_SOURCE_ONLY')
        self.assertEqual(r['tests'], {k: v for k, v in self.value['tests'].items() if k != 'properties'})
        self.assertNotIn('proposed_count', json.dumps(r))

    def test_new_incoming_absent_at_git_and_moving_source_ignored(self):
        self.value = self.handoff(['p.New'])
        self.overlay('p.New', 'package p; import org.junit.jupiter.api.Test; class New { @Test void yes() {} }\n')
        self.put('p.New', 'not Java')
        r = self.run_check()
        self.assertEqual(r['status'], 'BOUNDED_SOURCE_ONLY')
        self.assertEqual(r['roots'][0]['provenance']['authority'], 'incoming_packet')

    def test_package_mismatch(self):
        self.overlay('p.Frame', 'package wrong; class Frame {}\n')
        r = self.run_check()
        self.assertIn('declared_root_mismatch', [f['kind'] for f in r['findings']])

    def test_false_scopes_comments_literals_annotation_arguments(self):
        self.overlay('p.Frame', '''package p;
import org.junit.jupiter.api.Test;
class Frame {
// @Nested class Fake {}
String a="@Nested class Fake {}";
String b="""
@Nested class Fake {} package wrong;
""";
@Test void chosen() { String x="class Nope"; }
}\n''')
        r = self.run_check()
        self.assertEqual(r['status'], 'BOUNDED_SOURCE_ONLY')
        self.assertEqual([s['class'] for s in r['roots'][0]['scopes']], ['p.Frame'])
        self.overlay('p.Frame', 'package p; @Unknown(text="@Nested class Fake {}") class Frame {}\n')
        r = self.run_check()
        self.assertEqual(r['status'], 'INCOMPLETE')
        self.assertNotIn('Fake', json.dumps(r))

    def test_dynamic_ambiguous_custom_and_local_scopes_fail_closed(self):
        sources = [
            'package p; import org.junit.jupiter.api.TestFactory; class Frame { @TestFactory Object x() { return null; }}',
            'package p; import org.junit.jupiter.api.*; import unknown.*; class Frame { @Test void x() {} }',
            'package p; import other.Test; class Frame { @Test void x() {} }',
            'package p; class Frame { void x() { class Local {} }}',
            'package p; class Frame extends Parent {}',
            'package p; class Frame { class Helper {} }',
            'package p; class Frame { @org.junit.jupiter.api.Test void x() {} class org {} }',
        ]
        for source in sources:
            with self.subTest(source=source):
                self.overlay('p.Frame', source + '\n')
                self.assertEqual(self.run_check()['status'], 'INCOMPLETE')

    def test_unresolved_generated_root_not_absence_certificate(self):
        self.value = self.handoff(['p.Generated'])
        r = self.run_check()
        self.assertEqual(r['status'], 'INCOMPLETE')
        self.assertFalse(r['absence_certificate'])

    def test_literal_method_missing_and_all_method_topology(self):
        self.value['tests'].update(methods={'p.Frame': ['missing']}, expected_cases={'p.Frame': ['p.Frame#missing']})
        r = self.run_check()
        self.assertEqual(r['status'], 'INCOMPLETE')
        self.assertEqual(r['topology'], 'literal_methods_only')
        self.assertIn('missing_literal_method', [f['kind'] for f in r['findings']])

    def test_group_is_exact_finite_and_counts_remain_caller_assertions(self):
        self.mixed(); self.groups()
        self.value['tests']['report_groups']['p.Boot']['p.Boot$ActualUsa']['expected_tests'] = 100
        self.value['tests']['expected_tests']['p.Boot'] = 102
        self.assertEqual(self.run_check()['status'], 'BOUNDED_SOURCE_ONLY')
        group = self.value['tests']['report_groups']['p.Boot']
        group['p.Boot$Invented'] = dict(expected_tests=1, owners=['p.Boot$Invented'])
        self.value['tests']['expected_tests']['p.Boot'] += 1
        self.assertEqual(self.run_check()['status'], 'INCOMPLETE')

    def test_unsafe_aliases_pins_modes_and_paths(self):
        for change in ('symlink', 'hardlink', 'hash', 'mode', 'destination', 'source_alias', 'role_alias'):
            with self.subTest(change=change):
                self.value = self.handoff(['p.New'])
                p = self.overlay('p.New', 'package p; class New {}\n')
                e = self.value['packet']['files'][0]
                if change == 'symlink':
                    link = self.root / 'link.java'; link.symlink_to(p); e['source'] = str(link)
                elif change == 'hardlink':
                    link = self.root / 'hard.java'; os.link(p, link)
                elif change == 'hash': e['sha256'] = 'f'*64
                elif change == 'mode': p.chmod(0o644)
                elif change == 'destination': e['destination'] = 'src/test/java/../p/New.java'
                elif change == 'source_alias': e['source'] = str(p.parent) + '/./incoming.java'
                elif change == 'role_alias': e['source'] = str(self.repo / e['destination'])
                with self.assertRaises(preflight.PreflightError): self.run_check()
                if change in ('symlink', 'hardlink'): link.unlink()

    def test_schema_unknown_duplicate_nonfinite_and_limits(self):
        bad = copy.deepcopy(self.value); bad['unexpected'] = True
        self.value = bad
        with self.assertRaises(preflight.PreflightError): self.run_check()
        self.value = self.handoff(['p.Frame']); self.save()
        for data in ('{"version":1,"version":1}\n', '{"x":NaN}\n', '['*100 + '0' + ']'*100 + '\n'):
            self.spec.chmod(0o644); self.spec.write_text(data); self.spec.chmod(0o444)
            with self.assertRaises(preflight.PreflightError):
                preflight.check(str(self.repo / '.git'), self.commit, str(self.spec))
        for limits in (dict(max_total_bytes=10), dict(max_nodes=2), dict(max_output_bytes=100), dict(max_file_bytes=10)):
            with self.subTest(limits=limits), self.assertRaises(preflight.PreflightError): self.run_check(**limits)

    def test_source_token_context_budgets_are_incomplete(self):
        for limits in (dict(max_tokens=3), dict(max_context_tokens=3)):
            with self.subTest(limits=limits):
                self.assertEqual(self.run_check(**limits)['status'], 'INCOMPLETE')

    def test_commit_and_gitdir_must_be_exact_canonical(self):
        self.save()
        for commit in ('HEAD', self.commit+'~0', 'f'*40):
            with self.assertRaises(preflight.PreflightError): preflight.check(str(self.repo / '.git'), commit, str(self.spec))
        with self.assertRaises(preflight.PreflightError): preflight.check(str(self.repo / '.git')+'/', self.commit, str(self.spec))

    def test_dependency_pin_and_nonatomic_recheck(self):
        size, digest = preflight.PINS['java_declaration_census']
        for pin in ((1, digest), (size, '0'*64)):
            with patch.dict(preflight.PINS, {'java_declaration_census': pin}):
                with self.assertRaises(preflight.PreflightError): self.run_check()
        original = preflight.recheck
        def mutate(saved, *args):
            self.spec.chmod(0o644)
            self.spec.write_text('{}\n')
            return original(saved, *args)
        with patch.object(preflight, 'recheck', side_effect=mutate):
            with self.assertRaises(preflight.PreflightError): self.run_check()

    def test_adversarial_additional_scopes_and_binding_controls(self):
        sources = [
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test @Test void x() {} }',
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test static void x() {} }',
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test void x(int p) {} }',
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test void x() {} } class Extra {}',
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test void x() { new Object() {}; }}',
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test void x() {} @interface Custom {} }',
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test void x() {} @org.junit.jupiter.api.Nested static class Child {} }',
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test void x() {} } package wrong;',
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test void x() {} void bad( }',
            'package p; import org.junit.jupiter.api.Test; class Frame { @Test void x() {} }' + chr(92) + 'u000a',
        ]
        for source in sources:
            with self.subTest(source=source):
                self.overlay('p.Frame', source + '\n')
                self.assertEqual(self.run_check()['status'], 'INCOMPLETE')
        self.overlay('p.Frame', 'package p; class Frame { @org.junit.jupiter.api.Test void x() {} }\n')
        self.assertEqual(self.run_check()['status'], 'BOUNDED_SOURCE_ONLY')

    def test_deep_nested_scopes_and_explicit_ancestors(self):
        self.value = self.handoff(['p.Frame'])
        self.overlay('p.Frame', 'package p; import org.junit.jupiter.api.Nested; import org.junit.jupiter.api.Test; '
                     'class Frame { @Nested class A { @Nested class B { @Test void leaf() {} }}}\n')
        t = self.value['tests']
        t['report_groups'] = {'p.Frame': {
            'p.Frame': {'expected_tests': 0, 'owners': ['p.Frame']},
            'p.Frame$A': {'expected_tests': 0, 'owners': ['p.Frame$A']},
            'p.Frame$A$B': {'expected_tests': 1, 'owners': ['p.Frame$A$B']}}}
        r = self.run_check()
        self.assertEqual(r['status'], 'BOUNDED_SOURCE_ONLY')
        self.assertEqual([s['class'] for s in r['roots'][0]['scopes']], ['p.Frame', 'p.Frame$A', 'p.Frame$A$B'])
        del t['report_groups']['p.Frame']['p.Frame$A']
        with self.assertRaises(preflight.PreflightError): self.run_check()

    def test_git_symlink_root_and_external_parent_symlink(self):
        path = self.repo / 'src/test/java/p/Alias.java'
        path.symlink_to('Frame.java')
        self.git('add', '.'); self.git('commit', '-qm', 'Symlink fixture')
        self.commit = self.git('rev-parse', 'HEAD').strip()
        self.value = self.handoff(['p.Alias'])
        with self.assertRaises(preflight.PreflightError): self.run_check()
        self.value = self.handoff(['p.Frame'])
        self.overlay('p.Frame', 'package p; class Frame {}\n')
        alias = self.root / 'alias-directory'
        alias.symlink_to(self.root, target_is_directory=True)
        self.value['packet']['files'][0]['source'] = str(alias / 'incoming.java')
        with self.assertRaises(preflight.PreflightError): self.run_check()

    def test_duplicate_conflicting_and_reused_pinned_dependencies(self):
        source = self.overlay('p.Frame', 'package p; import org.junit.jupiter.api.Test; class Frame { @Test void x() {} }\n')
        e = self.value['packet']['files'][0]
        dep = dict(name='same-input', path=str(source), **{k: e[k] for k in ('sha256','mode','bytes')})
        self.value['dependencies'] = [dep]
        self.assertEqual(self.run_check()['status'], 'BOUNDED_SOURCE_ONLY')
        self.value['dependencies'] = [dep, dep]
        with self.assertRaises(preflight.PreflightError): self.run_check()
        self.value['dependencies'] = [dict(dep, sha256='f'*64)]
        with self.assertRaises(preflight.PreflightError): self.run_check()
        self.value['dependencies'] = []
        self.value['packet']['files'].append(dict(e))
        with self.assertRaises(preflight.PreflightError): self.run_check()

    def test_cli_stdout_and_nonzero_contract(self):
        self.save()
        tool = str(Path(preflight.__file__))
        args = [sys.executable, '-B', tool, '--git-dir', str(self.repo / '.git'),
                '--commit', self.commit, '--handoff', str(self.spec)]
        for expected, selection in ((0, ['p.Frame']), (3, ['player.Body'])):
            self.value = self.handoff(selection); self.save()
            result = subprocess.run(args, capture_output=True, text=True)
            self.assertEqual(result.returncode, expected)
            self.assertEqual(result.stderr, '')
            self.assertFalse(json.loads(result.stdout)['absence_certificate'])
        result = subprocess.run(args[:-1] + [str(self.spec) + '/..'], capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(json.loads(result.stdout)['status'], 'INVALID')

    def test_handoff_mode_and_tiny_cli_output_bound(self):
        self.save(); self.spec.chmod(0o777)
        with self.assertRaises(preflight.PreflightError):
            preflight.check(str(self.repo / '.git'), self.commit, str(self.spec))
        self.save()
        result = subprocess.run([sys.executable, '-B', str(Path(preflight.__file__)),
            '--git-dir', str(self.repo / '.git'), '--commit', self.commit, '--handoff', str(self.spec),
            '--max-output-bytes', '3'], capture_output=True)
        self.assertEqual(result.returncode, 3)
        self.assertLessEqual(len(result.stdout), 3)

    def test_no_runtime_execution_or_secret_content_output(self):
        self.overlay('p.Frame', 'package p; import org.junit.jupiter.api.Test; class Frame {\n'
                     '@Test void chosen() { String secret="DO_NOT_EMIT_012345"; Runtime.getRuntime().exec("no"); }}\n')
        self.assertNotIn('DO_NOT_EMIT', json.dumps(self.run_check()))


if __name__ == '__main__':
    unittest.main()
