"""Exact declared nested selectors under Surefire's global method filter.

Origin: 2026-11-07 R67/R67b source diagnosis. Synthetic declarations, XML and
mocked queue/source observations only. The literal filter model below is limited
to this tool's validated exact ASCII class/method subset; it is not Java/JUnit
execution, a general Surefire parser, or report/source-owner discovery.
"""
import copy
import json
from unittest import mock
import unittest

import integration_handoff as handoff
import integration_packet as packet
from test_integration_nested_reports import NestedFixture, ROOT, CHILD, DEEP, run


CATALOGUE = 'example.Catalogue'


def literal_method_source_admitted(argument, source_class, method):
    """Finite model of 3.2.5's resolver/filter for validated literal selectors.

    Without any method pattern the provider installs no TestMethodFilter. With
    one, its ORed includes compare the MethodSource class (not XML classname).
    A class-only include accepts every method of that exact binary class.
    """
    selectors = argument.removeprefix('-Dtest=').split(',')
    if not any('#' in selector for selector in selectors):
        return True
    for selector in selectors:
        owner, separator, methods = selector.partition('#')
        if owner == source_class and (not separator or method in methods.split('+')):
            return True
    return False


class MixedNestedSelectorTests(NestedFixture):
    def mixed(self):
        self.classes.append(CATALOGUE)
        self.groups[CATALOGUE] = {CATALOGUE: dict(expected_tests=2, owners=[CATALOGUE])}
        return dict(methods={CATALOGUE: ['zCheck', 'aCheck']}, expected_cases={
            CATALOGUE: [CATALOGUE+'#zCheck()', CATALOGUE+'#aCheck()']})

    def plan(self, changes=None, *, flat=False):
        tests = self.declared_tests()
        if changes:
            tests.update(changes)
        if flat:
            del tests['report_groups']
        handoff._tests(tests)
        before = copy.deepcopy(tests)
        spec = self.base/'plan.json'
        spec.write_text('synthetic pinned declaration\n')
        with mock.patch.object(run, 'handoff_summary', return_value=dict(
                ready=True, id='synthetic-mixed', tests=tests)):
            result = run.run_handoff(self.root, spec)
        self.assertEqual(tests, before)
        self.assertEqual(result['classes'], self.classes)
        self.assertEqual(result['expected_tests'], tests['expected_tests'])
        return result

    def write_mixed(self):
        self.write()
        self.xml(CATALOGUE, [(CATALOGUE, 'zCheck()'), (CATALOGUE, 'aCheck()')])

    def test_whole_root_gets_only_declared_child_and_method_root_stays_literal(self):
        changes = self.mixed()
        result = self.plan(changes)
        self.assertEqual(result['requested_maven_args'][1],
            '-Dtest='+ROOT+','+CHILD+','+CATALOGUE+'#aCheck+zCheck')
        self.assertEqual(result['report_groups'], self.groups)
        self.assertEqual(result['methods'], changes['methods'])
        self.assertEqual(result['expected_cases'], changes['expected_cases'])

    def test_exact_provider_subset_admits_nested_source_without_broadening_catalogue(self):
        result = self.plan(self.mixed())
        argument = result['requested_maven_args'][1]
        # An ancestor-owned XML case in a child report still has the child's
        # discovery MethodSource class. Both source-owning classes must pass.
        for owner in (ROOT, CHILD):
            self.assertTrue(literal_method_source_admitted(argument, owner, 'parent'))
            self.assertTrue(literal_method_source_admitted(argument, owner, 'child'))
        self.assertTrue(literal_method_source_admitted(argument, CATALOGUE, 'aCheck'))
        self.assertTrue(literal_method_source_admitted(argument, CATALOGUE, 'zCheck'))
        for owner, method in ((CATALOGUE, 'other'), (CHILD+'$Unlisted', 'child'),
                              (ROOT+'$Sibling', 'child'), (CATALOGUE+'$Child', 'aCheck'),
                              ('foreign.Outer', 'parent')):
            self.assertFalse(literal_method_source_admitted(argument, owner, method))

    def test_no_methods_preserves_root_only_discovery_bytes(self):
        result = self.plan()
        self.assertEqual(result['requested_maven_args'][1], '-Dtest='+ROOT)
        self.assertTrue(literal_method_source_admitted('-Dtest='+ROOT, CHILD, 'child'))

    def test_flat_mixed_and_flat_whole_requests_are_byte_identical(self):
        changes = self.mixed()
        self.assertEqual(self.plan(changes, flat=True)['requested_maven_args'][1],
                         '-Dtest='+ROOT+','+CATALOGUE+'#aCheck+zCheck')
        self.assertEqual(self.plan(flat=True)['requested_maven_args'][1],
                         '-Dtest='+ROOT+','+CATALOGUE)

    def test_singleton_groups_do_not_change_flat_mixed_bytes(self):
        changes = self.mixed()
        self.groups[ROOT] = {ROOT: dict(expected_tests=2, owners=[ROOT])}
        self.assertEqual(self.plan(changes)['requested_maven_args'][1],
                         '-Dtest='+ROOT+','+CATALOGUE+'#aCheck+zCheck')

    def test_declared_multilevel_ancestors_are_sorted_after_each_original_root(self):
        changes = self.mixed()
        sibling = ROOT+'$Zed'
        # Declaration insertion order does not control selector order. Empty
        # intermediate ancestors remain explicit; no prefix glob is generated.
        self.groups[ROOT] = {
            DEEP: dict(expected_tests=2, owners=[ROOT, CHILD, DEEP]),
            sibling: dict(expected_tests=1, owners=[sibling]),
            CHILD: dict(expected_tests=0, owners=[ROOT, CHILD]),
            ROOT: dict(expected_tests=0, owners=[ROOT])}
        self.classes.reverse()
        self.assertEqual(self.plan(changes)['requested_maven_args'][1],
            '-Dtest='+CATALOGUE+'#aCheck+zCheck,'+ROOT+','+CHILD+','+DEEP+','+sibling)

    def test_method_selected_nested_group_is_not_widened(self):
        self.classes.append(CATALOGUE)
        self.groups[CATALOGUE] = {CATALOGUE: dict(expected_tests=1, owners=[CATALOGUE])}
        changes = dict(methods={ROOT: ['selected']}, expected_cases={
            ROOT: [ROOT+'#parent()', CHILD+'#child()']})
        self.assertEqual(self.plan(changes)['requested_maven_args'][1],
                         '-Dtest='+ROOT+'#selected,'+CATALOGUE)

    def test_fifty_explicit_reports_fit_existing_budget_and_fifty_one_rejects(self):
        changes = self.mixed()
        self.groups[ROOT] = {ROOT: dict(expected_tests=0, owners=[ROOT])}
        for i in range(48):
            child = ROOT+f'$Child{i:02d}'
            self.groups[ROOT][child] = dict(expected_tests=1, owners=[child])
        argument = self.plan(changes)['requested_maven_args'][1]
        self.assertEqual(len(argument.removeprefix('-Dtest=').split(',')), 50)
        child = ROOT+'$TooMany'
        self.groups[ROOT][child] = dict(expected_tests=1, owners=[child])
        with self.assertRaisesRegex(packet.PacketError, 'total report suite limit'):
            self.plan(changes)

    def test_unsafe_foreign_and_missing_ancestor_declarations_reject_before_render(self):
        changes = self.mixed()
        original = copy.deepcopy(self.groups)
        for name in ('foreign.Child', ROOT+'$*', DEEP):
            self.groups = copy.deepcopy(original)
            self.groups[ROOT][name] = self.groups[ROOT].pop(CHILD)
            with self.subTest(name=name), self.assertRaises(packet.PacketError):
                self.plan(changes)

    def test_terminal_count_identity_rewrite_and_source_guards_stay_strict(self):
        changes = self.mixed()
        result, calls = self.execute(action=self.write_mixed, test_changes=changes, export=True)
        self.assertEqual(calls[0][2], '-Dtest='+ROOT+','+CHILD+','+CATALOGUE+'#aCheck+zCheck')
        self.assertTrue(result['clean'])
        receipt = json.loads((self.base/'receipt.json').read_text())
        self.assertEqual(receipt['run']['classes'], self.classes)
        self.assertEqual(len(receipt['run']['junit']['classes']), 3)
        # Missing child is not repaired or discovered from another run's XML.
        result, _ = self.execute(action=lambda: (self.xml(ROOT), self.xml(CATALOGUE, [
            (CATALOGUE, 'aCheck()'), (CATALOGUE, 'zCheck()')])), test_changes=changes)
        self.assertEqual(result['status'], 'incomplete')
        self.assertIn('not rewritten', result['evidence_error'])
        wrong = copy.deepcopy(changes)
        wrong['expected_cases'][CATALOGUE][0] = CATALOGUE+'#other()'
        result, _ = self.execute(action=self.write_mixed, test_changes=wrong)
        self.assertEqual(result['status'], 'incomplete')
        self.assertIn('case identity', result['evidence_error'])
        identities = [dict(head=self.head, fingerprint=value*64, files=1, bytes=2, gitlinks=0)
                      for value in ('2', '2', '3')]
        result, _ = self.execute(action=self.write_mixed, test_changes=changes,
                                source=mock.Mock(side_effect=identities))
        self.assertEqual(result['status'], 'incomplete')
        self.assertIn('Source changed', result['evidence_error'])

    def test_literal_r67_selection_shape_keeps_seven_roots_and_catalogue_two_methods(self):
        # Root-supplied request shape only; all counts here are synthetic, not a
        # claim about the actual Java inventory or either observed Maven run.
        prefix = 'com.openggf.game.soniccd.'
        self.classes = [prefix+'level.TestCdR31aObjectAssets', prefix+'level.TestCdR13ObjectAssets',
            prefix+'level.TestCdR31aAssets', prefix+'media.TestCdSystemProgramSource',
            prefix+'media.TestCdProgramAssets', prefix+'TestCdProgramCatalogue',
            prefix+'media.TestCdTimeWarpPrograms']
        self.groups = {owner: {owner: dict(expected_tests=1, owners=[owner])}
                       for owner in self.classes}
        for owner in (self.classes[0], self.classes[3]):
            child = owner+'$ActualUsa'
            self.groups[owner][child] = dict(expected_tests=1, owners=[owner, child])
        catalogue = self.classes[5]
        names = ['everyDispatchHasOneDispositionAndEveryFileHasAUseOrAnExplicitLimit',
                 'fileAndCommandIdsAreUniqueAndBoundedIncludingTheUnnamedInvalidSlot']
        self.groups[catalogue][catalogue]['expected_tests'] = 2
        changes = dict(methods={catalogue: names}, expected_cases={
            catalogue: [catalogue+'#'+name+'()' for name in names]})
        result = self.plan(changes)
        expected = [self.classes[0], self.classes[0]+'$ActualUsa', self.classes[1], self.classes[2],
            self.classes[3], self.classes[3]+'$ActualUsa', self.classes[4],
            catalogue+'#'+'+'.join(names), self.classes[6]]
        self.assertEqual(result['requested_maven_args'][1], '-Dtest='+','.join(expected))
        self.assertEqual(len(result['classes']), 7)
        self.assertEqual(sum(len(group) for group in result['report_groups'].values()), 9)


if __name__ == '__main__':
    unittest.main()
