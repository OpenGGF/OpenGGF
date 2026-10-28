"""Read-only custody checks with bounded synthetic Git repositories only."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

import integration_packet as packet
import integration_custody as custody


class CustodyFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / 'tree'
        self.root.mkdir()
        self.git(self.root, 'init', '-q')
        (self.root / 'Main.txt').write_text('root\n')
        self.git(self.root, 'add', '.')
        self.commit(self.root)

    def git(self, root, *args, data=None):
        env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
                   GIT_AUTHOR_NAME='Custody test', GIT_AUTHOR_EMAIL='test@example.invalid',
                   GIT_COMMITTER_NAME='Custody test', GIT_COMMITTER_EMAIL='test@example.invalid')
        return subprocess.check_output(['git', *args], cwd=root, input=data, env=env,
                                       stderr=subprocess.DEVNULL).decode().strip()

    def commit(self, root):
        self.git(root, 'commit', '-qm', 'synthetic custody fixture')

    def module(self, name='docs/reference'):
        path = self.root / name
        path.mkdir(parents=True)
        self.git(path, 'init', '-q')
        (path / 'Data.txt').write_text('reference\n')
        self.git(path, 'add', '.')
        self.commit(path)
        head = self.git(path, 'rev-parse', 'HEAD')
        self.git(self.root, 'update-index', '--add', '--cacheinfo', f'160000,{head},{name}')
        (self.root / '.gitmodules').write_text(f'[submodule "reference"]\n\tpath = {name}\n\turl = https://example.invalid/reference.git\n')
        return path, head

    def report(self, **kwargs):
        return custody.custody_summary(self.root, **kwargs)


class RootTests(CustodyFixture):
    def test_clean_root_has_no_custody_issues(self):
        result = self.report()
        self.assertTrue(result['ready'])
        self.assertEqual(result['version'], 1)
        self.assertEqual(result['worktree'], str(self.root))
        self.assertEqual(result['head'], self.git(self.root, 'rev-parse', 'HEAD'))
        self.assertEqual(result['modules'], [])
        self.assertIn('not inspected', result['root_sources'])

    def test_dirty_ordinary_root_is_not_mistaken_for_module_failure(self):
        (self.root / 'Main.txt').write_text('edited by Root\n')
        self.assertTrue(self.report()['ready'])

    def test_root_alias_and_subdirectory_reject(self):
        alias = self.base / 'alias'; alias.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(packet.PacketError): custody.custody_summary(alias)
        (self.root / 'nested').mkdir()
        with self.assertRaises(packet.PacketError): custody.custody_summary(self.root / 'nested')

    def test_unmerged_ordinary_index_blocks_readiness(self):
        blob = self.git(self.root, 'rev-parse', 'HEAD:Main.txt')
        self.git(self.root, 'update-index', '--index-info', data=(f'0 {"0"*40}\tMain.txt\n100644 {blob} 1\tMain.txt\n100644 {blob} 2\tMain.txt\n').encode())
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertTrue(result['index_unmerged'])

    def test_git_command_failure_is_bounded_and_does_not_dump_stderr(self):
        with mock.patch.object(custody._Git, 'run', side_effect=packet.PacketError('git command failed')):
            with self.assertRaisesRegex(packet.PacketError, 'git command failed'): self.report()

    def test_actual_git_error_is_bounded_without_stderr_dump(self):
        with self.assertRaises(packet.PacketError) as stopped:
            custody._Git().run(self.root, 'rev-parse', '--definitely-not-a-real-mode', '--verify')
        self.assertLess(len(str(stopped.exception)), 100)
        self.assertNotIn('usage:', str(stopped.exception))

    def test_output_limit_and_command_timeout_fail_closed(self):
        with mock.patch.object(custody, 'MAX_COMMAND_OUTPUT', 8):
            with self.assertRaises(packet.PacketError): self.report()
        with mock.patch.object(custody, 'MAX_SECONDS', 0):
            with self.assertRaises(packet.PacketError): self.report()

    def test_detected_index_mutation_rejects_observation(self):
        original = custody._Git.run
        seen = 0
        def changed(git, root, *args, **kwargs):
            nonlocal seen
            value = original(git, root, *args, **kwargs)
            if args == ('ls-files', '--stage', '-z'):
                seen += 1
                if seen == 2: return value + b'100644 ' + b'1'*40 + b' 0\tOther.txt\0'
            return value
        with mock.patch.object(custody._Git, 'run', changed):
            with self.assertRaisesRegex(packet.PacketError, 'changed'): self.report()


class ModuleTests(CustodyFixture):
    def test_matching_initialized_module_is_ready(self):
        path, head = self.module()
        result = self.report()
        self.assertTrue(result['ready'], result['issues'])
        module = result['modules'][0]
        self.assertEqual(module['path'], 'docs/reference')
        self.assertEqual(module['expected_commit'], head)
        self.assertEqual(module['actual_commit'], head)
        self.assertEqual(module['state'], 'clean')
        self.assertEqual(module['status'], {'staged': 0, 'tracked': 0, 'untracked': 0, 'unmerged': 0})

    def test_mismatched_head_visible_under_ignore_all(self):
        path, head = self.module()
        self.git(self.root, 'config', 'submodule.reference.ignore', 'all')
        (path / 'Data.txt').write_text('new commit\n')
        self.git(path, 'add', '.')
        self.commit(path)
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertNotEqual(result['modules'][0]['actual_commit'], head)
        self.assertIn('commit mismatch', ' '.join(result['issues']))

    def test_tracked_untracked_and_staged_module_changes_are_separate(self):
        path, _ = self.module()
        (path / 'Data.txt').write_text('staged\n')
        self.git(path, 'add', 'Data.txt')
        (path / 'Data.txt').write_text('also unstaged\n')
        (path / 'New.txt').write_text('untracked\n')
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'][0]['status'], {'staged': 1, 'tracked': 1, 'untracked': 1, 'unmerged': 0})

    def test_missing_registration_blocks(self):
        self.module(); (self.root / '.gitmodules').unlink()
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertIn('missing registration', ' '.join(result['issues']))

    def test_registration_without_gitlink_blocks(self):
        (self.root / '.gitmodules').write_text('[submodule "orphan"]\npath = docs/orphan\n')
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertIn('no gitlink', ' '.join(result['issues']))

    def test_duplicate_and_unsafe_registration_block_without_traversal(self):
        self.module()
        with (self.root / '.gitmodules').open('a') as stream:
            stream.write('[submodule "duplicate"]\npath = docs/reference\n[submodule "unsafe"]\npath = ../outside\n')
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertIn('duplicate registration', ' '.join(result['issues']))
        self.assertIn('unsafe registration', ' '.join(result['issues']))

    def test_duplicate_path_key_and_missing_path_block(self):
        (self.root / '.gitmodules').write_text('[submodule "bad"]\npath = docs/a\npath = docs/b\n[submodule "missing"]\nurl = https://example.invalid/a\n')
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertIn('exactly one path', ' '.join(result['issues']))

    def test_empty_uninitialized_module_is_not_clean(self):
        self.module()
        path = self.root / 'docs/reference'
        import shutil; shutil.rmtree(path); path.mkdir()
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'][0]['state'], 'uninitialized')

    def test_nonempty_uninitialized_module_is_not_clean(self):
        self.module()
        import shutil; shutil.rmtree(self.root / 'docs/reference/.git')
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'][0]['state'], 'uninitialized-nonempty')

    def test_linked_reference_is_never_probed_as_a_module(self):
        path, _ = self.module()
        import shutil; shutil.rmtree(path)
        external = self.base / 'reference'; external.mkdir()
        path.symlink_to(external, target_is_directory=True)
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'][0]['state'], 'linked-reference')
        self.assertIsNone(result['modules'][0]['actual_commit'])

    def test_gitmodules_symlink_and_module_parent_symlink_refuse(self):
        self.module()
        actual = self.base / 'modules'; actual.write_bytes((self.root / '.gitmodules').read_bytes())
        (self.root / '.gitmodules').unlink(); (self.root / '.gitmodules').symlink_to(actual)
        with self.assertRaises(packet.PacketError): self.report()

    def test_module_metadata_symlink_is_not_traversed(self):
        path, _ = self.module()
        import shutil; shutil.move(path / '.git', self.base / 'metadata')
        (path / '.git').symlink_to(self.base / 'metadata', target_is_directory=True)
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertIn('metadata symlink', ' '.join(result['issues']))

    def test_configured_clean_process_and_textconv_are_never_executed(self):
        path, _ = self.module()
        sentinel = self.base / 'MUST-NOT-EXIST'
        self.git(path, 'config', 'filter.unsafe.clean', f'touch {sentinel}')
        self.git(path, 'config', 'filter.unsafe.process', f'touch {sentinel}')
        self.git(path, 'config', 'diff.unsafe.textconv', f'touch {sentinel}')
        self.git(path, 'config', 'diff.unsafe.command', f'touch {sentinel}')
        (path / '.gitattributes').write_text('Data.txt filter=unsafe diff=unsafe\n')
        (path / 'Data.txt').write_text('changed\n')
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'][0]['state'], 'unsafe-config')
        self.assertFalse(sentinel.exists())
        self.assertNotIn('touch', json.dumps(result))
        self.assertNotIn(str(sentinel), json.dumps(result))

    def test_included_filter_config_is_rejected_before_status(self):
        path, _ = self.module()
        config = self.base / 'included'; config.write_text('[filter "hidden"]\nclean = echo MUST-NOT-RUN\n')
        self.git(path, 'config', 'include.path', str(config))
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'][0]['state'], 'unsafe-config')
        self.assertNotIn('MUST-NOT-RUN', json.dumps(result))

    def test_gitmodules_include_is_not_followed(self):
        config = self.base / 'outside'; config.write_text('[submodule "external"]\npath = ../outside\n')
        (self.root / '.gitmodules').write_text(f'[include]\npath = {config}\n')
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'], [])
        self.assertIn('includes are unsupported', ' '.join(result['issues']))

    def test_missing_module_and_parent_are_uninitialized(self):
        path, _ = self.module()
        import shutil; shutil.rmtree(self.root / 'docs')
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'][0]['state'], 'uninitialized')

    def test_module_parent_symlink_refuses_without_traversing(self):
        self.module()
        import shutil; shutil.move(self.root / 'docs', self.base / 'outside')
        (self.root / 'docs').symlink_to(self.base / 'outside', target_is_directory=True)
        with self.assertRaisesRegex(packet.PacketError, 'parent'): self.report()

    def test_fsmonitor_command_is_overridden_not_executed(self):
        path, _ = self.module()
        sentinel = self.base / 'MUST-NOT-EXIST'
        self.git(path, 'config', 'core.fsmonitor', f'touch {sentinel}')
        result = self.report()
        self.assertTrue(result['ready'], result['issues'])
        self.assertFalse(sentinel.exists())

    def test_ambient_git_config_injection_is_not_observed_or_executed(self):
        self.module()
        with mock.patch.dict(os.environ, {'GIT_CONFIG_COUNT':'1', 'GIT_CONFIG_KEY_0':'filter.unsafe.clean', 'GIT_CONFIG_VALUE_0':'MUST-NOT-EXECUTE', 'GIT_DIR':'/does/not/exist'}):
            self.assertTrue(self.report()['ready'])

    def test_unmerged_module_index_is_dirty(self):
        path, _ = self.module()
        blob = self.git(path, 'rev-parse', 'HEAD:Data.txt')
        self.git(path, 'update-index', '--index-info', data=(f'0 {"0"*40}\tData.txt\n100644 {blob} 1\tData.txt\n100644 {blob} 2\tData.txt\n').encode())
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'][0]['status']['unmerged'], 1)

    def test_nested_modules_are_not_traversed_or_silently_certified(self):
        path, head = self.module()
        self.git(path, 'update-index', '--add', '--cacheinfo', f'160000,{head},nested')
        result = self.report()
        self.assertFalse(result['ready'])
        self.assertEqual(result['modules'][0]['nested_gitlinks'], 1)
        self.assertIn('nested gitlinks were not traversed', ' '.join(result['issues']))

    def test_status_size_and_entry_bounds_fail_closed(self):
        path, _ = self.module()
        (path / 'Extra.txt').write_text('unknown\n')
        with mock.patch.object(custody, 'MAX_STATUS', 8):
            with self.assertRaises(packet.PacketError): self.report()
        with mock.patch.object(custody, 'MAX_STATUS_ENTRIES', 0):
            with self.assertRaises(packet.PacketError): self.report()

    def test_module_head_change_during_recheck_rejects(self):
        path, _ = self.module()
        original = custody._Git.run
        seen = 0
        def changed(git, root, *args, **kwargs):
            nonlocal seen
            value = original(git, root, *args, **kwargs)
            if root == path and args == ('rev-parse', 'HEAD'):
                seen += 1
                if seen == 2: return b'1'*40+b'\n'
            return value
        with mock.patch.object(custody._Git, 'run', changed):
            with self.assertRaisesRegex(packet.PacketError, 'changed'): self.report()


class LinkTests(CustodyFixture):
    def test_relative_and_absolute_links_match_canonical_target(self):
        target = self.base / 'resource'; target.write_text('bytes\n')
        (self.root / 'Relative').symlink_to('../resource')
        (self.root / 'Absolute').symlink_to(target)
        result = self.report(links=[('Relative', str(target)), ('Absolute', str(target))])
        self.assertTrue(result['ready'], result['issues'])
        self.assertEqual([entry['matches_expected'] for entry in result['links']], [True, True])
        self.assertTrue(all(entry['target_kind'] == 'file' for entry in result['links']))

    def test_broken_and_wrong_link_block(self):
        target = self.base / 'resource'; target.write_text('bytes\n')
        (self.root / 'Broken').symlink_to('../missing')
        (self.root / 'Wrong').symlink_to('../other')
        result = self.report(links=[('Broken', str(self.base / 'missing')), ('Wrong', str(target))])
        self.assertFalse(result['ready'])
        self.assertFalse(result['links'][0]['target_exists'])
        self.assertFalse(result['links'][1]['matches_expected'])

    def test_dotdot_cannot_recover_through_a_missing_or_regular_component(self):
        target = self.base / 'resource'; target.mkdir()
        (self.base / 'ordinary-file').write_text('not a directory\n')
        for component in ('missing', 'ordinary-file'):
            with self.subTest(component=component):
                link = self.root / 'Selected'
                link.symlink_to('../'+component+'/../resource')
                try:
                    self.assertFalse(link.exists())
                    result = self.report(links=[('Selected', str(target))])
                    self.assertFalse(result['ready'])
                    self.assertFalse(result['links'][0]['target_exists'])
                finally:
                    link.unlink()

    def test_regular_file_or_absent_path_is_not_a_valid_resource_link(self):
        target = self.base / 'resource'; target.write_text('bytes\n')
        result = self.report(links=[('Main.txt', str(target)), ('Absent', str(target))])
        self.assertFalse(result['ready'])
        self.assertEqual([entry['kind'] for entry in result['links']], ['file', 'absent'])

    def test_duplicate_traversal_and_lexical_alias_declarations_reject(self):
        target = self.base / 'resource'; target.write_text('bytes\n')
        for links in ([('A', str(target)), ('A', str(target))], [('../A', str(target))], [('A//B', str(target))], [('A', str(self.base) + '/./resource')]):
            with self.subTest(links=links), self.assertRaises(packet.PacketError): self.report(links=links)

    def test_selected_link_parent_symlink_and_expected_target_symlink_reject(self):
        target = self.base / 'resource'; target.write_text('bytes\n')
        alias = self.base / 'alias'; alias.symlink_to(target)
        (self.root / 'Parent').symlink_to(self.base, target_is_directory=True)
        with self.assertRaises(packet.PacketError): self.report(links=[('Parent/resource', str(target))])
        with self.assertRaises(packet.PacketError): self.report(links=[('A', str(alias))])

    def test_symlink_loop_is_bounded(self):
        (self.root / 'A').symlink_to('B'); (self.root / 'B').symlink_to('A')
        with self.assertRaises(packet.PacketError): self.report(links=[('A', str(self.base / 'target'))])

    def test_link_mutation_detected_during_recheck(self):
        target = self.base / 'resource'; target.write_text('bytes\n')
        link = self.root / 'A'; link.symlink_to(target)
        original = custody._Git.run
        seen = 0
        def changed(git, root, *args, **kwargs):
            nonlocal seen
            value = original(git, root, *args, **kwargs)
            if args == ('ls-files', '--stage', '-z'):
                seen += 1
                if seen == 2: link.unlink(); link.symlink_to('../different')
            return value
        with mock.patch.object(custody._Git, 'run', changed):
            with self.assertRaisesRegex(packet.PacketError, 'changed'): self.report(links=[('A', str(target))])


if __name__ == '__main__': unittest.main()
