"""Schema/selection controls only; no Java, Maven, media or native execution."""
from contextlib import contextmanager
import copy
import hashlib
import json
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch

import run_categories as runner


# Exact pre-schema2 policy function from e34ece3c9981e9b3672cdf0ed1e3684c11e5845a.
# Keeping the actual reader tests the older absolute-runner compatibility hazard.
OLD_POLICY_SOURCE = r'''def policy(root=ROOT):
    data = json.loads((root / 'tools/testing/test-categories.json').read_text())
    valid = set(data['categories'])
    for category, _ in data['owners']:
        if category not in valid:
            raise ValueError(f'Unknown owner category: {category}')
    for category, pattern in (data['name_owners'] | data['fallback_name_owners']).items():
        if category not in valid:
            raise ValueError(f'Unknown name category: {category}')
        re.compile(pattern)
    for rule in data['change_rules']:
        if not rule['categories'] or not set(rule['categories']) <= valid:
            raise ValueError(f'Invalid change rule: {rule}')
    return data'''
OLD_POLICY_SHA256 = '50687bb2316db6763ec7b7601b838c341a8bcdc34d72e5d295a6d848c1208993'


def legacy_policy():
    return dict(version=1, categories={
        'common': 'Shared tests.', 'gameplay': 'Game tests.', 'rendering': 'Render tests.'},
        owners=[['rendering', ['graphics/']], ['gameplay', ['game/']]],
        name_owners={}, fallback_name_owners={}, change_rules=[
            dict(prefixes=['src/main/java/com/openggf/game/sonic2/objects/'], categories=['gameplay']),
            dict(prefixes=['src/main/java/com/openggf/graphics/'], categories=['rendering'])])


def version_two():
    data = legacy_policy()
    data['version'] = 2
    data['categories']['soniccd'] = 'Native CD tests.'
    data['ownership_rules'] = [['soniccd', ['game/soniccd/']], *data.pop('owners')]
    data['category_dependencies'] = {'gameplay': ['soniccd']}
    return data


def normalized_two():
    data = version_two()
    data['owners'] = data.pop('ownership_rules')
    return data


@contextmanager
def policy_root(data):
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        path = root / 'tools/testing/test-categories.json'
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps(data))
        yield root


def old_reader(root):
    if hashlib.sha256(OLD_POLICY_SOURCE.encode()).hexdigest() != OLD_POLICY_SHA256:
        raise AssertionError('The exact pinned compatibility reader changed')
    namespace = dict(json=json, re=re, ROOT=root)
    exec(compile(OLD_POLICY_SOURCE, '<actual policy reader at e34ece3c9>', 'exec'), namespace)
    return namespace['policy'](root)


@contextmanager
def planning_root(include_common=True):
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        (root / 'pom.xml').write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
          <build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId>
          <configuration><excludes><exclude>**/*Guard.java</exclude></excludes></configuration>
          </plugin></plugins></build></project>''')
        paths = ['com/openggf/game/soniccd/TestScene.java',
                 'com/openggf/game/soniccd/device/TestUnusuallyNamed.java',
                 'com/openggf/game/sonic2/objects/TestObject.java',
                 'com/openggf/graphics/TestPaint.java',
                 'com/openggf/game/soniccd/TestExcludedGuard.java']
        if include_common:
            paths.append('com/openggf/tests/TestShared.java')
        for relative in paths:
            path = root / 'src/test/java' / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('// Selection fixture, never compiled.\n')
        yield root


def plan(root, data, requested=(), changes=None, workers=1):
    with patch.object(runner, 'git', return_value='synthetic-head'), \
            patch.object(runner, 'changed_files', return_value=('synthetic-base', changes or [])):
        return runner.make_plan(root, data, 'base' if changes is not None else None,
                                list(requested), workers=workers)


class CategorySchemaTwoTests(unittest.TestCase):
    def test_installed_policy_uses_fail_closed_version_two_shape(self):
        raw = json.loads((runner.ROOT / 'tools/testing/test-categories.json').read_text())
        self.assertEqual(2, raw['version'])
        self.assertIn('ownership_rules', raw)
        self.assertNotIn('owners', raw)
        self.assertEqual({'gameplay': ['soniccd']}, raw['category_dependencies'])

    def test_actual_old_reader_rejects_version_two_before_selection(self):
        with policy_root(version_two()) as root:
            with self.assertRaises(KeyError) as rejected:
                old_reader(root)
            self.assertEqual(('owners',), rejected.exception.args)

    def test_actual_old_reader_does_not_reject_a_version_number_alone(self):
        data = legacy_policy(); data['version'] = 2
        with policy_root(data) as root:
            self.assertEqual(data, old_reader(root))
            with self.assertRaises(ValueError):
                runner.policy(root)

    def test_valid_legacy_one_is_unchanged(self):
        data = legacy_policy()
        with policy_root(data) as root:
            self.assertEqual(data, runner.policy(root))
            self.assertEqual(old_reader(root), runner.policy(root))

    def test_version_two_normalizes_only_the_internal_owner_key(self):
        raw = version_two()
        with policy_root(raw) as root:
            loaded = runner.policy(root)
        self.assertEqual(normalized_two(), loaded)
        self.assertNotIn('owners', raw)
        self.assertIn('ownership_rules', raw)

    def test_legacy_one_cannot_silently_ignore_dependencies(self):
        for dependencies in ({}, {'gameplay': ['rendering']}, None):
            data = legacy_policy(); data['category_dependencies'] = dependencies
            with self.subTest(dependencies=dependencies), policy_root(data) as root:
                with self.assertRaises(ValueError):
                    runner.policy(root)

    def test_ambiguous_or_missing_ownership_shapes_fail_closed(self):
        both = version_two(); both['owners'] = legacy_policy()['owners']
        missing = version_two(); del missing['ownership_rules']
        legacy_both = legacy_policy(); legacy_both['ownership_rules'] = legacy_both['owners']
        for data in (both, missing, legacy_both):
            with self.subTest(data=data), policy_root(data) as root:
                with self.assertRaises(ValueError):
                    runner.policy(root)

    def test_versions_are_exact_supported_integers(self):
        for version in (True, False, 0, 3, -1, '2', 2.0, None, [], {}):
            data = version_two(); data['version'] = version
            with self.subTest(version=version), policy_root(data) as root:
                with self.assertRaises(ValueError):
                    runner.policy(root)

    def test_dependency_map_sources_targets_and_types_are_validated(self):
        invalid = (None, [], 'gameplay', True,
                   {'missing': ['soniccd']}, {'gameplay': ['missing']},
                   {'gameplay': 'soniccd'}, {'gameplay': None},
                   {'gameplay': True}, {'gameplay': [True]}, {'gameplay': [1]})
        for dependencies in invalid:
            data = version_two(); data['category_dependencies'] = dependencies
            with self.subTest(dependencies=dependencies), policy_root(data) as root:
                with self.assertRaises(ValueError):
                    runner.policy(root)

    def test_version_two_dependencies_are_optional_without_a_new_default(self):
        data = version_two(); del data['category_dependencies']
        with policy_root(data) as root:
            loaded = runner.policy(root)
        self.assertNotIn('category_dependencies', loaded)

    def test_unknown_owner_and_change_rule_names_still_reject(self):
        owner = version_two(); owner['ownership_rules'][0][0] = 'missing'
        change = version_two(); change['change_rules'][0]['categories'].append('missing')
        for data in (owner, change):
            with self.subTest(data=data), policy_root(data) as root:
                with self.assertRaises(ValueError):
                    runner.policy(root)


class CategoryDependencyPlanTests(unittest.TestCase):
    def test_existing_gameplay_plan_keeps_the_moved_cd_owner(self):
        # Before dependency-aware selection, moving package ownership loses both CD witnesses.
        with planning_root() as root:
            result = plan(root, normalized_two(), ['gameplay'])
        self.assertIn('com/openggf/game/soniccd/TestScene.java', result['tests'])
        self.assertIn('com/openggf/game/soniccd/device/TestUnusuallyNamed.java', result['tests'])
        self.assertIn('soniccd', result['categories'])

    def test_dependencies_apply_to_changed_categories_as_well_as_manual_ones(self):
        with planning_root() as root:
            result = plan(root, normalized_two(), changes=[
                'src/main/java/com/openggf/game/sonic2/objects/Object.java'])
        self.assertIn('com/openggf/game/soniccd/TestScene.java', result['tests'])
        self.assertFalse(result['full']); self.assertTrue(result['guards'])

    def test_transitive_dependencies_cycles_and_duplicates_terminate_as_a_set(self):
        data = normalized_two()
        data['category_dependencies'] = {
            'gameplay': ['soniccd', 'soniccd'], 'soniccd': ['rendering'], 'rendering': ['gameplay']}
        with planning_root() as root:
            result = plan(root, data, ['gameplay'])
        self.assertEqual(set(data['categories']), set(result['categories']))
        self.assertEqual(5, len(result['tests']))
        self.assertEqual(len(result['tests']), len(set(result['tests'])))

    def test_dependencies_are_generic_not_hardcoded_to_cd_names(self):
        data = normalized_two()
        data['categories']['dependent'] = 'Synthetic dependency.'
        data['owners'].insert(0, ['dependent', ['graphics/']])
        data['category_dependencies'] = {'rendering': ['dependent']}
        with planning_root() as root:
            result = plan(root, data, ['rendering'])
        self.assertIn('com/openggf/graphics/TestPaint.java', result['tests'])
        self.assertIn('dependent', result['categories'])
        self.assertNotIn('soniccd', result['categories'])

    def test_every_legacy_single_category_keeps_the_same_candidate_set(self):
        with planning_root() as root:
            for category in legacy_policy()['categories']:
                with self.subTest(category=category):
                    before = plan(root, legacy_policy(), [category])
                    after = plan(root, normalized_two(), [category])
                    self.assertEqual(before['tests'], after['tests'])

    def test_all_inventory_and_surefire_exclusions_are_identical(self):
        with planning_root() as root:
            before = plan(root, legacy_policy(), ['all'])
            after = plan(root, normalized_two(), ['all'])
        self.assertEqual(before['tests'], after['tests'])
        self.assertEqual(before['inventory_count'], after['inventory_count'])
        self.assertFalse(any('ExcludedGuard' in name for name in after['tests']))
        self.assertEqual('smoke', after['suite'])
        self.assertTrue(after['full']); self.assertTrue(after['guards'])

    def test_cd_focus_does_not_select_other_game_owners(self):
        with planning_root() as root:
            result = plan(root, normalized_two(), ['soniccd'])
        self.assertEqual({'common', 'soniccd'}, set(result['categories']))
        self.assertEqual(3, len(result['tests']))
        self.assertFalse(any('sonic2' in name for name in result['tests']))

    def test_unclassified_module_bootstrap_shared_and_new_scopes_remain_broad(self):
        current = runner.policy()
        paths = ['src/main/java/com/openggf/game/soniccd/SonicCdGameModule.java',
                 'src/main/java/com/openggf/game/soniccd/CdStockLaunch.java',
                 'src/main/java/com/openggf/game/soniccd/unreviewed/NewOwner.java',
                 'src/main/java/com/openggf/data/cd/CueSheet.java', 'pom.xml']
        for path in paths:
            with self.subTest(path=path):
                categories, full, _ = runner.select_changes([path], current)
                self.assertTrue(full)
                self.assertEqual(set(current['categories']), categories)

    def test_current_internal_prefixes_add_only_the_reviewed_semantic_dependencies(self):
        current = runner.policy()
        cases = [('level/CdR11bAssets.java', {'soniccd', 'content', 'rendering', 'rewind'}),
                 ('objects/CdTimeWarpLoadData.java', {'soniccd', 'physics', 'rendering', 'rewind'}),
                 ('audio/CdFmPlaySound.java', {'soniccd', 'audio', 'rewind'}),
                 ('device/CdStampGraphics.java', {'soniccd', 'rendering', 'rewind'}),
                 ('device/pcm/CdPcm.java', {'soniccd', 'audio', 'rendering', 'rewind'})]
        for relative, expected in cases:
            with self.subTest(relative=relative):
                categories, full, _ = runner.select_changes([
                    'src/main/java/com/openggf/game/soniccd/' + relative], current)
                self.assertFalse(full)
                self.assertEqual(expected | {'common'}, categories)

    def test_explicit_category_additions_cannot_narrow_a_broad_change(self):
        with planning_root() as root:
            result = plan(root, normalized_two(), ['soniccd'], changes=['pom.xml'])
        self.assertTrue(result['full']); self.assertTrue(result['guards'])
        self.assertEqual(5, len(result['tests']))

    def test_worker_choice_and_existing_plan_contract_do_not_change_selection(self):
        with planning_root() as root:
            one = plan(root, normalized_two(), ['gameplay'], workers=1)
            two = plan(root, normalized_two(), ['gameplay'], workers=2)
        self.assertEqual(1, one.pop('workers')); self.assertEqual(2, two.pop('workers'))
        self.assertEqual(one, two)
        self.assertEqual(1, one['schema'])
        self.assertEqual('focused', one['mode'])
        self.assertFalse(one['full']); self.assertFalse(one['guards'])

    def test_no_candidate_still_fails_instead_of_producing_a_zero_test_plan(self):
        with planning_root(include_common=False) as root:
            with self.assertRaisesRegex(ValueError, 'No tests selected'):
                plan(root, normalized_two(), ['common'])


if __name__ == '__main__':
    unittest.main()
