#!/usr/bin/env python3
"""Bounded static JUnit annotation inventory controls; no Java is executed."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import unittest

import test_java_declaration_census as support

census = support.census


def source(body, imports='import org.junit.jupiter.api.Test;', header='class C'):
    return ('package p; ' + imports + ' ' + header + ' {' + body + '}').encode()


def inventory(data, selected='p.C', **limits):
    return census.test_inventory(data, census.Limits(**limits), selected)


class OrdinaryInventory(unittest.TestCase):
    def test_five_ordinary_tests_exclude_helpers_calls_comments_and_literals(self):
        data = source('''
 @Test void first() { String s = "@Test void fake() {}"; }
 @Test() public void second() throws Exception {}
 @Test final void third() {}
 @Test void fourth() {}
 @Test void fifth() {}
 // @Test void ghost() {}
 void helper() { first(); }
 ''')
        self.assertTrue(callable(getattr(census, 'test_inventory', None)),
                        'Static whole-class JUnit inventory API is missing')
        result = inventory(data)
        self.assertTrue(result['complete'])
        self.assertEqual(result['proposed_count'], 5)
        self.assertEqual([m['name'] for m in result['candidate_methods']],
                         ['first', 'second', 'third', 'fourth', 'fifth'])
        self.assertEqual(result['selected_class'], 'p.C')
        self.assertEqual(result['incomplete_reasons'], [])
        self.assertEqual(result['candidate_methods'][0]['owner'], 'p.C')

    def test_direct_fully_qualified_annotations_and_simple_lifecycle(self):
        data = source('''
 @org.junit.jupiter.api.BeforeAll static void load() throws Exception { assumeTrue(false); }
 @org.junit.jupiter.api.BeforeEach void prepare() {}
 @org.junit.jupiter.api.Test void check() {}
 @org.junit.jupiter.api.AfterEach void cleanup() {}
 @org.junit.jupiter.api.AfterAll static void finish() {}
 ''', imports='')
        self.assertEqual(inventory(data)['proposed_count'], 1)

    def test_qualified_junit_root_shadowing_is_incomplete(self):
        for imports in ['import custom.org;', 'import static custom.Owner.org;',
                        'import static custom.Owner.*;']:
            with self.subTest(imports=imports):
                result = inventory(source('@org.junit.jupiter.api.Test void check() {}', imports=imports))
                self.assertFalse(result['complete'])
                self.assertIsNone(result['proposed_count'])

    def test_qualified_junit_root_named_type_is_incomplete(self):
        result = inventory(source('@org.junit.jupiter.api.Test void check() {}', imports='', header='class org'),
                           selected='p.org')
        self.assertFalse(result['complete'])
        self.assertIsNone(result['proposed_count'])

    def test_qualified_annotation_and_direct_import_controls_stay_complete(self):
        for imports in ['', 'import custom.Org;', 'import static org.junit.jupiter.api.Assertions.*;']:
            with self.subTest(imports=imports):
                self.assertEqual(inventory(source('@org.junit.jupiter.api.Test void check() {}', imports=imports))['proposed_count'], 1)
        # A single type import binds Test without guessing members of another type's on-demand import.
        self.assertEqual(inventory(source('@Test void check() {}', imports=
            'import org.junit.jupiter.api.Test; import static custom.Owner.*;'))['proposed_count'], 1)

    def test_unresolved_shadow_wildcard_and_wrong_test_annotations_are_incomplete(self):
        for imports in ['', 'import other.Test;', 'import org.junit.jupiter.api.*;',
                        'import org.junit.jupiter.api.Test; import other.*;',
                        'import org.junit.jupiter.api.Test; import other.Test;']:
            with self.subTest(imports=imports):
                result = inventory(source('@Test void check() {}', imports=imports))
                self.assertFalse(result['complete'])
                self.assertIsNone(result['proposed_count'])
                self.assertTrue(result['incomplete_reasons'])

    def test_parameterized_dynamic_repeated_disabled_conditional_unknown_are_incomplete(self):
        for annotation in ['@org.junit.jupiter.params.ParameterizedTest',
                           '@org.junit.jupiter.api.TestFactory',
                           '@org.junit.jupiter.api.TestTemplate',
                           '@org.junit.jupiter.api.RepeatedTest(3)',
                           '@org.junit.jupiter.api.Disabled',
                           '@org.junit.jupiter.api.condition.EnabledOnOs(OS.LINUX)',
                           '@org.junit.jupiter.api.extension.ExtendWith(Plugin.class)',
                           '@Custom', '@org.junit.jupiter.api.Tag("fast")']:
            with self.subTest(annotation=annotation):
                result = inventory(source(annotation + ' void other() {} @Test void check() {}'))
                self.assertFalse(result['complete'])
                self.assertIsNone(result['proposed_count'])
                self.assertEqual([m['name'] for m in result['candidate_methods']], ['check'])

    def test_class_field_parameter_and_body_annotations_cannot_escape_review(self):
        for data in [source('@Test void check() {}', header='@Disabled class C'),
                     source('@RegisterExtension Object extension; @Test void check() {}'),
                     source('void helper(@Custom String s) {} @Test void check() {}'),
                     source('@Test void check() { @Custom String x; }')]:
            with self.subTest(data=data):
                self.assertIsNone(inventory(data)['proposed_count'])

    def test_inheritance_nesting_additional_types_and_nonordinary_class_are_incomplete(self):
        for header in ['class C extends Base', 'class C implements I', 'abstract class C',
                       'class C<T>', 'record C()', 'enum C']:
            body = '; @Test void check() {}' if header == 'enum C' else '@Test void check() {}'
            with self.subTest(header=header):
                self.assertIsNone(inventory(source(body, header=header))['proposed_count'])
        for body in ['class Helper {} @Test void check() {}',
                     '@org.junit.jupiter.api.Nested class Inner { @Test void nested() {} } @Test void check() {}']:
            self.assertIsNone(inventory(source(body))['proposed_count'])
        self.assertIsNone(inventory(source('@Test void check() {}') + b' class Other {}')['proposed_count'])

    def test_nonordinary_method_shapes_constructors_and_duplicate_annotations_are_incomplete(self):
        for method in ['@Test private void check() {}', '@Test static void check() {}',
                       '@Test int check() { return 1; }', '@Test void check(String x) {}',
                       '@Test <T> void check() {}', '@Test void check();',
                       '@Test @Test void check() {}', '@Test(1) void check() {}',
                       '@Test @org.junit.jupiter.api.BeforeEach void check() {}',
                       'C(String x) {} @Test void check() {}',
                       'private C() {} @Test void check() {}']:
            with self.subTest(method=method):
                self.assertIsNone(inventory(source(method))['proposed_count'])

    def test_unsupported_lifecycle_shape_is_incomplete(self):
        for method in ['@org.junit.jupiter.api.BeforeAll void init() {}',
                       '@org.junit.jupiter.api.BeforeEach static void init() {}',
                       '@org.junit.jupiter.api.BeforeEach void init(String x) {}']:
            self.assertIsNone(inventory(source(method + ' @Test void check() {}'))['proposed_count'])

    def test_absent_selected_class_and_no_tests_are_incomplete(self):
        self.assertIsNone(inventory(source('void helper() {}'))['proposed_count'])
        self.assertIsNone(inventory(source('@Test void check() {}'), 'p.Other')['proposed_count'])
        self.assertIsNone(inventory(source('class Inner { @Test void check() {} }'), 'p.C.Inner')['proposed_count'])

    def test_existing_lexer_limits_and_unsupported_grammar_are_preserved(self):
        for data, limits in [(source('@Test void check() {}'), {'max_tokens': 4}),
                             (source('@Test void check() { class Local {} }'), {}),
                             (source('@Test void check() {}') + b' // \\u000a', {})]:
            with self.subTest(data=data), self.assertRaises(census.CensusError) as caught:
                inventory(data, **limits)
            self.assertEqual(caught.exception.code, 3)


class PinnedInventory(unittest.TestCase):
    setUp = support.ImmutableInputs.setUp
    git = support.ImmutableInputs.git
    options = support.ImmutableInputs.options

    def pin_test_source(self, text):
        (self.repo / self.source).write_bytes(text)
        self.git('add', '--', self.source)
        self.git('commit', '-qm', 'static inventory fixture')
        self.revision = self.git('rev-parse', 'HEAD').decode().strip()

    def cli(self, *extra):
        argv = ['--git-dir', str(self.repo / '.git'), '--revision', self.revision,
                '--source', self.source, *extra]
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = census.main(argv)
        return code, json.loads(output.getvalue())

    def test_cli_stale_expected_four_fails_five_succeeds_without_writes(self):
        self.pin_test_source(source(' '.join('@Test void t%d() {}' % n for n in range(5))))
        preserved = {p: p.read_bytes() for p in [self.repo / self.source,
                     self.repo / '.git/index', self.repo / '.git/HEAD']}
        (self.repo / self.source).write_bytes(b'class Moving {}')
        preserved[self.repo / self.source] = b'class Moving {}'
        code, result = self.cli('--test-inventory', 'p.C', '--expected-count', '4')
        self.assertEqual(code, 1)
        self.assertEqual(result['baseline']['test_inventory']['proposed_count'], 5)
        self.assertFalse(result['expected_count']['matches'])
        self.assertEqual(result['expected_count']['source'], 'baseline')
        self.assertEqual(result['baseline']['provenance']['revision'], self.revision)
        self.assertEqual(self.cli('--test-inventory', 'p.C', '--expected-count', '5')[0], 0)
        self.assertEqual(preserved, {p: p.read_bytes() for p in preserved})

    def test_cli_shadowed_qualified_annotation_never_qualifies_expected_count(self):
        for imports in ['import custom.org;', 'import static custom.Owner.org;']:
            with self.subTest(imports=imports):
                self.pin_test_source(source('@org.junit.jupiter.api.Test void check() {}', imports=imports))
                code, result = self.cli('--test-inventory', 'p.C', '--expected-count', '1')
                self.assertEqual(code, 3)
                self.assertIsNone(result['baseline']['test_inventory']['proposed_count'])
                self.assertIsNone(result['expected_count']['matches'])

    def test_final_uses_independent_pins_and_expected_count_targets_final(self):
        self.pin_test_source(source('@Test void first() {}'))
        self.final.write_bytes(source('@Test void first() {} @Test void second() {}'))
        pins = ['--final', str(self.final), '--final-sha256', hashlib.sha256(self.final.read_bytes()).hexdigest(),
                '--final-bytes', str(self.final.stat().st_size)]
        code, result = self.cli('--test-inventory', 'p.C', '--expected-count', '2', *pins)
        self.assertEqual(code, 0)
        self.assertEqual(result['baseline']['test_inventory']['proposed_count'], 1)
        self.assertEqual(result['final']['test_inventory']['proposed_count'], 2)
        self.assertEqual(result['expected_count'], dict(value=2, source='final', matches=True))
        self.assertNotIn('diff', result)
        bad = pins.copy(); bad[3] = '0' * 64
        self.assertEqual(self.cli('--test-inventory', 'p.C', *bad)[0], 2)

    def test_incomplete_never_compares_an_invented_count(self):
        self.pin_test_source(source('@Test void check() {}', header='class C extends Base'))
        code, result = self.cli('--test-inventory', 'p.C', '--expected-count', '1')
        self.assertEqual(code, 3)
        self.assertEqual(result['status'], 'incomplete')
        self.assertIsNone(result['expected_count']['matches'])
        self.assertIsNone(result['baseline']['test_inventory']['proposed_count'])

    def test_bad_mode_arguments_are_rejected(self):
        self.pin_test_source(source('@Test void check() {}'))
        for extra in [('--expected-count', '1'), ('--test-inventory', 'p.C', '--expected-count', '-1'),
                      ('--test-inventory', 'p.C', '--expected-count', '5001'),
                      ('--test-inventory', '../C')]:
            with self.subTest(extra=extra):
                self.assertEqual(self.cli(*extra)[0], 2)


if __name__ == '__main__':
    unittest.main()
