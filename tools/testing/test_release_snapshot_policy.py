"""Fast snapshot audit and integration regressions for release history admission."""
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('release_tree_audit', ROOT / '.githooks/audit-release-tree.py')
audit = importlib.util.module_from_spec(spec)
# Keep imported test helpers from creating binary files in the LF-only hook tree.
with patch.object(sys, 'dont_write_bytecode', True), patch.object(sys, 'path', [str(ROOT / '.githooks'), *sys.path]):
    spec.loader.exec_module(audit)
BASELINE = '45cecf566825aa50612f5e687b2682fc9681aed1'
NEXT_BASELINE = '218b8fff1a829c7a509331c453d2f3be7e51533b'
PUBLISHED_BASELINE = '37aeb6b84d6634457616c942187cdd40dd93c24f'
SHARED_DEVELOPMENT_BASELINE = 'f514a5f13d353550f8421462c773f17f90a04100'
OID = 'a' * 40
AUTHORED_ROOT = 'src/test/resources/mods/sample-two-act-campaign-src/project/src/main/resources/levels/tide'
AUTHORED_PATH = AUTHORED_ROOT + '/act1/patterns.bin'


class TreeAuditTests(unittest.TestCase):
    def check_entry(self, path='README.md', mode='100644', kind='blob', size='10', target=b'../relative'):
        entry = f'{mode} {kind} {OID} {size}\t{path}'.encode() + b'\0'
        def git(*args):
            if args[0] == 'rev-parse': return (OID + '\n').encode()
            if args[0] == 'ls-tree': return entry
            if args[0] == 'cat-file': return target
            raise AssertionError(args)
        with patch.object(audit, 'git', side_effect=git):
            audit.audit('HEAD')

    def test_regular_files_and_gitlinks_are_valid(self):
        self.check_entry()
        self.check_entry('tools/reference', '160000', 'commit', '-')

    def test_all_rom_extensions_are_rejected_case_insensitively(self):
        for extension in ['GEN', 'smd', 'bin', 'sms', 'gg', '32x']:
            with self.subTest(extension=extension), self.assertRaisesRegex(ValueError, 'ROM/binary'):
                self.check_entry('nested/game.' + extension)

    def test_exact_authored_fixture_blobs_are_admitted(self):
        fixtures = sorted((ROOT / AUTHORED_ROOT).glob('act*/*.bin'))
        self.assertEqual(22, len(fixtures))
        for fixture in fixtures:
            with self.subTest(path=fixture):
                payload = fixture.read_bytes()
                self.check_entry(fixture.relative_to(ROOT).as_posix(),
                                 size=str(len(payload)), target=payload)

    def test_authored_fixture_admission_rejects_changed_bytes_and_size(self):
        payload = (ROOT / AUTHORED_PATH).read_bytes()
        for content, size in ((bytes([payload[0] ^ 1]) + payload[1:], len(payload)),
                              (payload, len(payload) + 1)):
            with self.subTest(size=size), self.assertRaisesRegex(ValueError, 'ROM/binary'):
                self.check_entry(AUTHORED_PATH, size=str(size), target=content)

    def test_authored_fixture_admission_is_exact_path_and_regular_mode(self):
        payload = (ROOT / AUTHORED_PATH).read_bytes()
        paths = [AUTHORED_PATH.upper(), AUTHORED_PATH.replace('/act1/', '/act3/'),
                 AUTHORED_ROOT + '/act1/copied-patterns.bin', 'elsewhere/patterns.bin']
        for path in paths:
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, 'ROM/binary'):
                self.check_entry(path, size=str(len(payload)), target=payload)
        for mode in ('100755', '120000'):
            with self.subTest(mode=mode), self.assertRaisesRegex(ValueError, 'ROM/binary'):
                self.check_entry(AUTHORED_PATH, mode=mode,
                                 size=str(len(payload)), target=payload)

    def test_github_size_boundary(self):
        self.check_entry(size='99999999')
        with self.assertRaisesRegex(ValueError, 'file-size'):
            self.check_entry(size='100000000')

    def test_trace_size_boundary_and_compressed_payload(self):
        self.check_entry('traces/physics.csv', size='1048575')
        self.check_entry('traces/physics.csv.gz', size='1048576')
        for name in ['physics.csv', 'aux_state-tail.jsonl']:
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'uncompressed trace'):
                self.check_entry('traces/' + name, size='1048576')

    def test_scratch_and_protected_links(self):
        with self.assertRaisesRegex(ValueError, 'scratch'):
            self.check_entry('HANDOVER-release.md')
        self.check_entry('docs/architecture/HANDOVER-release.md')
        with self.assertRaisesRegex(ValueError, 'protected'):
            self.check_entry('config.yaml', '120000')

    def test_absolute_links_rejected_and_relative_links_preserved(self):
        self.check_entry('reference', '120000')
        for target in [b'/tmp/reference', b'C:\\reference', b'\\\\server\\share']:
            with self.subTest(target=target), self.assertRaisesRegex(ValueError, 'absolute symlink'):
                self.check_entry('reference', '120000', target=target)

    def test_unusual_names_do_not_escape_the_audit(self):
        with self.assertRaisesRegex(ValueError, 'ROM/binary'):
            self.check_entry('nested/tab\tnewline\nname.bin')

    def test_malformed_tree_entries_fail_closed(self):
        with self.assertRaisesRegex(ValueError, 'unsupported'):
            self.check_entry(mode='100600')
        with self.assertRaises(ValueError):
            self.check_entry(size='not-a-size')


class SnapshotPolicyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repo = self.root / 'repo'; self.repo.mkdir()
        self.git('init', '-b', 'main')
        self.git('config', 'user.name', 'Policy Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.write('README.md', 'initial\n'); self.base = self.commit('initial history')

    def tearDown(self):
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, stderr=subprocess.STDOUT, text=True).strip()

    def write(self, path, text):
        target = self.repo / path; target.parent.mkdir(parents=True, exist_ok=True); target.write_text(text)
        self.git('add', path)

    def commit(self, message):
        self.git('commit', '-m', message)
        return self.git('rev-parse', 'HEAD')

    def policies(self, baseline):
        directory = self.root / 'policy'; directory.mkdir(exist_ok=True)
        for name in ['validate-policy.sh', 'validate-policy.ps1', 'audit-release-tree.py',
                     'authored_fixture_policy.py', 'authored-fixtures.json',
                     'machine-local-path-grandfather.sha256']:
            text = (ROOT / '.githooks' / name).read_text().replace(BASELINE, baseline)
            text = text.replace(NEXT_BASELINE, getattr(self, 'next_baseline', NEXT_BASELINE))
            text = text.replace(PUBLISHED_BASELINE, getattr(self, 'published_baseline', PUBLISHED_BASELINE))
            text = text.replace(SHARED_DEVELOPMENT_BASELINE,
                                getattr(self, 'shared_baseline', SHARED_DEVELOPMENT_BASELINE))
            (directory / name).write_text(text)
        yield ['sh', str(directory / 'validate-policy.sh')]
        if shutil.which('pwsh'):
            yield ['pwsh', '-NoLogo', '-NoProfile', '-File', str(directory / 'validate-policy.ps1')]
        else:
            with self.subTest(implementation='pwsh'):
                self.skipTest('PowerShell policy coverage unavailable: pwsh is not installed')

    def assert_policy(self, baseline, before, after, branch, expected):
        for command in self.policies(baseline):
            with self.subTest(implementation=command[0]):
                result = subprocess.run(command + ['ci-push', before, after, branch], cwd=self.repo,
                                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                if expected is None:
                    self.assertEqual(0, result.returncode, result.stdout)
                    self.assertIn('Release tree audit:', result.stdout)
                else:
                    self.assertNotEqual(0, result.returncode, result.stdout)
                    self.assertIn(expected, result.stdout)

    def write_fixture(self, path=AUTHORED_PATH, content=None, mode='100644'):
        target = self.repo / path
        target.parent.mkdir(parents=True, exist_ok=True)
        if mode == '120000':
            target.symlink_to('relative-target')
        else:
            target.write_bytes((ROOT / AUTHORED_PATH).read_bytes() if content is None else content)
        self.git('add', '--', path)
        if mode == '100755':
            self.git('update-index', '--chmod=+x', '--', path)
        return target

    def assert_staged_policy(self, expected):
        for command in self.policies(self.base):
            with self.subTest(implementation=command[0]):
                result = subprocess.run(command + ['pre-commit'], cwd=self.repo, text=True,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                if expected is None:
                    self.assertEqual(0, result.returncode, result.stdout)
                else:
                    self.assertNotEqual(0, result.returncode, result.stdout)
                    self.assertIn(expected, result.stdout)

    def assert_api_commit_policy(self, expected):
        message = self.root / 'api-message.txt'
        message.write_text('chore: candidate API check\n\n' + '\n'.join(
            key + ': n/a' for key in ['Changelog', 'Guide', 'Known-Discrepancies',
                                     'S3K-Known-Discrepancies', 'Agent-Docs',
                                     'Configuration-Docs', 'Skills']) + '\n')
        for command in self.policies(self.base):
            with self.subTest(implementation=command[0]):
                result = subprocess.run(command + ['commit-msg', str(message)], cwd=self.repo,
                                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                if expected is None:
                    self.assertEqual(0, result.returncode, result.stdout)
                else:
                    self.assertNotEqual(0, result.returncode, result.stdout)
                    self.assertIn(expected, result.stdout)

    def check_api_source_change(self, old, new, expected):
        path = 'src/main/java/example/Rules.java'
        self.write(path, old)
        self.base = self.commit('existing candidate API declaration')
        self.write(path, new)
        self.assert_api_commit_policy(expected)

    def test_record_preset_initializer_and_implementation_bodies_do_not_require_pin(self):
        old = """@ModApi
public record Rules(int value) {
    public static final Rules STOCK = new Rules(
            1);
    static { if (true) { configure(1); } }
    public Rules { if (value < 0) { throw new IllegalArgumentException("old"); } }
    public int twice() { return value * 2; }
    private static void configure(int n) { }
}
"""
        new = old.replace('            1);', '            2);').replace('configure(1)', 'configure(2)')
        new = new.replace('"old"', '"new"').replace('value * 2', 'value + value')
        self.check_api_source_change(old, new, None)

    def test_actual_game_rules_preset_initializer_change_does_not_require_pin(self):
        old = (ROOT / 'src/main/java/com/openggf/game/rules/GameRules.java').read_text()
        begin = old.index('public static final GameRules SONIC_3K =')
        new = old[:begin] + old[begin:].replace('                    false,',
                                                '                    true,', 1)
        self.assertNotEqual(old, new)
        self.check_api_source_change(old, new, None)

    def test_record_initializer_lambdas_arrays_and_multiple_fields_keep_only_declarations(self):
        old = """@ModApi
public record Rules(int value) {
    public static final java.util.function.IntSupplier FN = () -> { return 1; };
    public static final int[] ARRAY = { 1, 2, 3 };
    public static final int FIRST = call(1, 2), SECOND = 3;
    public static final Object OBJECT = new Object() {
        public String toString() { return "old"; }
    };
}
"""
        new = old.replace('return 1;', 'return 2;').replace('{ 1, 2, 3 }', '{ 3, 4 }')
        new = new.replace('call(1, 2)', 'call(4, 5)').replace('SECOND = 3', 'SECOND = 9')
        new = new.replace('"old"', '"new"')
        self.check_api_source_change(old, new, None)
        self.write('src/main/java/example/Rules.java', new.replace(', SECOND = 9', ''))
        self.assert_api_commit_policy('candidate signature pin')

    def test_record_literals_cannot_hide_following_public_declarations(self):
        old = '''@ModApi
public record Rules(int value) {
    public static final String URL = "https://example.invalid/{/*";
    public String text() { return """
        // text, not a comment: { }
        /* still text */
        """; }
    public int size() { return 1; }
}
'''
        self.check_api_source_change(old, old.replace('still text', 'different text'), None)
        self.write('src/main/java/example/Rules.java', old.replace('int size()', 'long size()'))
        self.assert_api_commit_policy('candidate signature pin')

    def test_nested_interface_implicit_public_method_still_requires_pin(self):
        old = """@ModApi
public record Rules(int value) {
    public interface Nested {
        int size();
    }
}
"""
        self.check_api_source_change(old, old.replace('int size()', 'long size()'),
                                     'candidate signature pin')

    def test_class_multiline_initializer_does_not_capture_following_private_body(self):
        old = """@ModApi
public class Rules {
    public static final Rules STOCK = create(
            1);
    private void hidden() { publicLookingCall(1); }
    public int value() { return 1; }
}
"""
        self.check_api_source_change(old, old.replace('            1);', '            2);')
                                     .replace('publicLookingCall(1)', 'publicLookingCall(2)')
                                     .replace('return 1', 'return 2'), None)

    def test_record_public_declaration_changes_still_require_candidate_pin(self):
        old = """@ModApi
public record Rules(int value) {
    @Deprecated
    public static final Rules STOCK = new Rules(1);
    public Rules(String text) { this(Integer.parseInt(text)); }
    public int twice() { return value * 2; }
    public static class Nested { public int size() { return 1; } }
}
"""
        changes = [old.replace('int value)', 'long value)'),
                   old.replace('int twice()', 'long twice()'),
                   old.replace('final Rules STOCK', 'final Object STOCK'),
                   old.replace('Rules(String text)', 'Rules(CharSequence text)'),
                   old.replace('@Deprecated', '@Deprecated(since="1")'),
                   old.replace('static class Nested', 'static final class Nested'),
                   old.replace('public int size()', 'public long size()'),
                   old.replace('    public int twice() { return value * 2; }', ''),
                   old.replace('    public int twice()', '    public int extra() { return 0; }\n    public int twice()')]
        path = 'src/main/java/example/Rules.java'
        self.write(path, old); self.base = self.commit('existing candidate API declaration')
        for changed in changes:
            with self.subTest(changed=changed):
                self.write(path, changed)
                self.assert_api_commit_policy('candidate signature pin')

    def test_exact_authored_fixtures_pass_both_staged_and_commit_policy(self):
        for source in sorted((ROOT / AUTHORED_ROOT).glob('act*/*.bin')):
            self.write_fixture(source.relative_to(ROOT).as_posix(), source.read_bytes())
        self.assert_staged_policy(None)
        head = self.commit('reviewed original authored fixtures')
        self.assert_policy(self.base, self.base, head, 'feature/release', None)

    def test_authored_bytes_are_read_from_index_not_working_tree(self):
        payload = (ROOT / AUTHORED_PATH).read_bytes()
        target = self.write_fixture()
        target.write_bytes(b'bad local replacement')
        self.assert_staged_policy(None)
        self.git('add', '--', AUTHORED_PATH)
        target.write_bytes(payload)
        self.assert_staged_policy('ROM/binary')

    def test_staged_authored_paths_modes_and_bytes_fail_closed(self):
        payload = (ROOT / AUTHORED_PATH).read_bytes()
        cases = [(AUTHORED_PATH, bytes([payload[0] ^ 1]) + payload[1:], '100644'),
                 (AUTHORED_PATH, payload + b'\0', '100644'),
                 (AUTHORED_PATH.upper(), payload, '100644'),
                 ('moved/patterns.bin', payload, '100644'),
                 (AUTHORED_ROOT + '/act1/extra.bin', payload, '100644'),
                 (AUTHORED_PATH, payload, '100755'),
                 (AUTHORED_PATH, payload, '120000'),
                 (AUTHORED_ROOT + '/act1/game.gen', payload, '100644')]
        for path, content, mode in cases:
            with self.subTest(path=path, mode=mode, size=len(content)):
                self.git('reset', '--hard', self.base)
                self.write_fixture(path, content, mode)
                self.assert_staged_policy('ROM/binary')
                head = self.commit('invalid authored fixture declaration')
                self.assert_policy(self.base, self.base, head, 'feature/release', 'ROM/binary')

    def test_commit_fixture_check_ignores_unstaged_bad_local_bytes(self):
        target = self.write_fixture()
        head = self.commit('reviewed original authored fixture')
        target.write_bytes(b'bad local replacement')
        self.assert_policy(self.base, self.base, head, 'feature/release', None)

    def test_bad_authored_bytes_cannot_be_hidden_by_later_restore(self):
        self.write_fixture(content=b'bad authored fixture')
        self.commit('invalid authored fixture')
        self.write_fixture()
        head = self.commit('restore reviewed bytes')
        self.assert_policy(self.base, self.base, head, 'feature/release', 'ROM/binary')

    def test_bad_authored_bytes_cannot_be_hidden_by_later_removal(self):
        self.write_fixture(content=b'bad authored fixture')
        self.commit('invalid authored fixture')
        self.git('rm', '--', AUTHORED_PATH)
        head = self.commit('remove invalid authored fixture')
        self.assert_policy(self.base, self.base, head, 'feature/release', 'ROM/binary')

    def test_all_rom_extensions_reject_staged_and_removed_history(self):
        for extension in ('GEN', 'sMd', 'BIN', 'sMs', 'gG', '32X'):
            with self.subTest(extension=extension):
                self.git('reset', '--hard', self.base)
                path = 'nested/user-asset.' + extension
                self.write_fixture(path)
                self.assert_staged_policy('ROM/binary')
                self.commit('invalid generic ROM-like path')
                self.git('rm', '--', path)
                head = self.commit('remove generic ROM-like path')
                self.assert_policy(self.base, self.base, head, 'feature/release', 'ROM/binary')

    def test_snapshot_cli_does_not_emit_hook_bytecode(self):
        self.write_fixture()
        head = self.commit('reviewed original authored fixture')
        policy_directory = Path(next(self.policies(self.base))[1]).parent
        environment = dict(os.environ)
        environment.pop('PYTHONDONTWRITEBYTECODE', None)
        result = subprocess.run([sys.executable, str(policy_directory / 'audit-release-tree.py'), head],
                                cwd=self.repo, env=environment, text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertFalse(list(policy_directory.rglob('__pycache__')))

    def test_old_removed_violation_is_not_replayed_after_reviewed_snapshot(self):
        self.write('traces/physics.csv', 'x' * 1048576); self.commit('old oversized trace')
        self.git('rm', 'traces/physics.csv'); baseline = self.commit('reviewed clean snapshot')
        self.write('later.txt', 'later\n'); head = self.commit('new work')
        self.assert_policy(baseline, self.base, head, 'feature/release', None)

    def test_inherited_bad_snapshot_entry_is_rejected(self):
        self.write('inherited.bin', 'bad asset'); baseline = self.commit('bad snapshot')
        self.assert_policy(baseline, self.base, baseline, 'feature/release', 'ROM/binary')

    def test_new_violation_removed_from_tip_still_fails(self):
        baseline = self.base
        self.write('traces/physics.csv', 'x' * 1048576); self.commit('new oversized trace')
        self.git('rm', 'traces/physics.csv'); head = self.commit('remove trace')
        self.assert_policy(baseline, self.base, head, 'feature/release', 'uncompressed trace payload')

    def test_next_rollover_audits_snapshot_without_replaying_removed_history(self):
        self.write('traces/physics.csv', 'x' * 1048576); self.commit('historical oversized trace')
        self.git('rm', 'traces/physics.csv'); self.next_baseline = self.commit('reviewed next snapshot')
        self.assert_policy(BASELINE, self.base, self.next_baseline, 'feature/rollover', None)

    def test_next_rollover_does_not_hide_inherited_bad_tree_entries(self):
        self.write('inherited.bin', 'bad asset'); self.next_baseline = self.commit('bad next snapshot')
        self.assert_policy(BASELINE, self.base, self.next_baseline, 'feature/rollover', 'ROM/binary')

    def test_next_rollover_does_not_hide_later_removed_violations(self):
        self.next_baseline = self.base
        self.write('traces/physics.csv', 'x' * 1048576); self.commit('new oversized trace')
        self.git('rm', 'traces/physics.csv'); head = self.commit('remove new violation')
        self.assert_policy(BASELINE, self.base, head, 'feature/rollover', 'uncompressed trace payload')

    def test_next_rollover_admits_frozen_historical_trailer_debt(self):
        self.write('notes.txt', 'history still needs trailers'); self.next_baseline = self.commit('no trailers')
        self.assert_policy(BASELINE, self.base, self.next_baseline, 'develop', None)

    def test_next_rollover_rejects_postbaseline_missing_trailers(self):
        self.next_baseline = self.base
        self.write('later.txt', 'new work'); head = self.commit('missing trailers')
        self.assert_policy(BASELINE, self.base, head, 'develop', 'documentation policy')

    def test_next_rollover_rejects_postbaseline_mismapped_trailers(self):
        self.next_baseline = self.base
        self.write('later.txt', 'new work')
        head = self.commit('chore: new work\n\n' + '\n'.join(
            key + ': ' + ('updated' if key == 'Known-Discrepancies' else 'n/a')
            for key in ['Changelog', 'Guide', 'Known-Discrepancies', 'S3K-Known-Discrepancies',
                        'Agent-Docs', 'Configuration-Docs', 'Skills']))
        self.assert_policy(BASELINE, self.base, head, 'develop', 'Known-Discrepancies')

    def test_next_rollover_rejects_postbaseline_uncoupled_api_changes(self):
        self.next_baseline = self.base
        self.write('src/main/java/example/NewApi.java', '@ModApi\npublic interface NewApi {}\n')
        head = self.commit('chore: add API\n\n' + '\n'.join(
            key + ': n/a' for key in ['Changelog', 'Guide', 'Known-Discrepancies',
                                     'S3K-Known-Discrepancies', 'Agent-Docs',
                                     'Configuration-Docs', 'Skills']))
        self.assert_policy(BASELINE, self.base, head, 'develop', 'candidate signature pin')

    def assert_api_source_change(self, before, after, expected, update_pin=False):
        # These fixture commits intentionally construct policy violations. The
        # actual copied validators below must inspect both the index and history.
        self.git('reset', '--hard', self.base)
        self.write('mod-api-release-policy.properties',
                   'currentStatus=candidate\ncurrentApi=0.7.0\npublishedBaselines=\n')
        pin = 'src/test/resources/mods/mod-api-signatures-0.7.txt'
        self.write(pin, 'reviewed baseline\n')
        path = 'src/main/java/example/Api.java'
        self.write(path, before)
        baseline = self.commit('reviewed API baseline')
        self.next_baseline = baseline
        self.write(path, after)
        if update_pin:
            self.write(pin, 'changed candidate\n')
        message = 'chore: exercise API policy\n\n' + '\n'.join(
            key + ': n/a' for key in ['Changelog', 'Guide', 'Known-Discrepancies',
                                     'S3K-Known-Discrepancies', 'Agent-Docs',
                                     'Configuration-Docs', 'Skills'])
        message_path = self.root / 'commit-message'
        message_path.write_text(message)
        for command in self.policies(baseline):
            with self.subTest(implementation=command[0], mode='commit-msg'):
                result = subprocess.run(command + ['commit-msg', str(message_path)], cwd=self.repo,
                                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                if expected is None:
                    self.assertEqual(0, result.returncode, result.stdout)
                else:
                    self.assertNotEqual(0, result.returncode, result.stdout)
                    self.assertIn(expected, result.stdout)
        head = self.commit(message)
        self.assert_policy(baseline, baseline, head, 'develop', expected)


    def test_package_private_nested_record_does_not_require_a_pin(self):
        before = '@ModApi\npublic class Api {\n    public record State(int frame) {}\n}\n'
        after = before.replace('\n}', '''
    record EffectsRewindState(
            int frame, int tick, int mapping, boolean active,
            int x, int y, boolean facingLeft, int splashFrame,
            int splashTick, boolean surfaceActive, int surfaceX,
            int surfaceY, int surfaceTimer) {}
}''')
        self.assert_api_source_change(before, after, None)


    def test_retained_nested_type_owner_movement_requires_pin(self):
        before = '@ModApi\npublic interface Api { class A { public static class B {} } }\n'
        after = '@ModApi\npublic interface Api { class A {} public static class B {} }\n'
        self.assert_api_source_change(before, after, 'candidate signature pin')


    def test_retained_nested_type_owner_movement_accepts_changed_pin(self):
        before = '@ModApi\npublic interface Api { class A { public static class B {} } }\n'
        after = '@ModApi\npublic interface Api { class A {} public static class B {} }\n'
        self.assert_api_source_change(before, after, None, update_pin=True)


    def test_retained_deep_owner_movements_guard_pin_operations(self):
        cases = [
            ('public nested sibling',
             '@ModApi\npublic class Api { public static class A { public static class B { public static class C {} } } }\n',
             '@ModApi\npublic class Api { public static class A { public static class B {} public static class C {} } }\n'),
            ('protected record',
             '@ModApi\npublic class Api { public static class A { protected static record State(int frame) {} } }\n',
             '@ModApi\npublic class Api { public static class A {} protected static record State(int frame) {} }\n'),
            ('public member under implicit interface child',
             '@ModApi\npublic interface Api { class A { public static class B { public int value; } } }\n',
             '@ModApi\npublic interface Api { class A { public static class B {} public int value; } }\n'),
        ]
        for name, nested, sibling in cases:
            for direction, before, after in (('outward', nested, sibling), ('inward', sibling, nested)):
                for update_pin in (False, True):
                    with self.subTest(case=name, direction=direction, update_pin=update_pin):
                        self.assert_api_source_change(before, after,
                                                      None if update_pin else 'candidate signature pin',
                                                      update_pin=update_pin)


    def test_moving_unexposed_nested_implementation_does_not_require_pin(self):
        before = '@ModApi\npublic class Api { static class A { record Hidden(int frame) {} } static class B {} }\n'
        after = '@ModApi\npublic class Api { static class A {} static class B { record Hidden(int frame) {} } }\n'
        self.assert_api_source_change(before, after, None)


    def test_missing_powershell_reports_skips_after_shell_coverage(self):
        # Exercise real SH policy while simulating only the missing prerequisite.
        # Capture the nested result so the installed-platform suite itself remains
        # fully exercised; missing-pwsh runs report these skips normally.
        probe = SnapshotPolicyTests('test_package_private_nested_record_does_not_require_a_pin')
        result = unittest.TestResult()
        with patch.object(shutil, 'which', return_value=None):
            probe.run(result)
        self.assertEqual(1, result.testsRun)
        self.assertEqual([], result.errors)
        self.assertEqual([], result.failures)
        self.assertEqual(2, len(result.skipped))  # commit-msg and ci-push coverage
        for test, reason in result.skipped:
            self.assertIn("implementation='pwsh'", str(test))
            self.assertEqual('PowerShell policy coverage unavailable: pwsh is not installed', reason)


    def test_unexposed_nested_implementations_can_be_added_changed_and_removed(self):
        plain = '@ModApi\npublic class Api {\n    public int value() { return 0; }\n%s\n}\n'
        for visibility in ('', 'private '):
            for kind, header in (('class', 'static class Hidden'),
                                 ('record', 'record Hidden(\n int frame,\n int tick\n)'),
                                 ('interface', 'interface Hidden'),
                                 ('enum', 'enum Hidden'),
                                 ('annotation', '@interface Hidden')):
                members = ('    public int count() default 1;\n' if kind == 'annotation' else
                           '    public int count() { return 1; }\n')
                if kind == 'interface':
                    members = '    default int count() { return 1; }\n'
                if kind == 'enum':
                    members = '    ONE;\n' + members
                hidden = '    ' + visibility + header + ' {\n' + members + '    }'
                changed = hidden.replace('int count()', 'long count()')
                if kind == 'record':
                    changed = changed.replace('int tick', 'long tick')
                for action, before, after in (('add', '', hidden), ('change', hidden, changed),
                                               ('remove', hidden, '')):
                    with self.subTest(visibility=visibility, kind=kind, action=action):
                        self.assert_api_source_change(plain % before, plain % after, None)


    def test_public_members_under_a_hidden_nested_owner_do_not_expose_it(self):
        before = '''@ModApi
public class Api {
    static class Hidden {
        @Deprecated public static record State(int frame) {}
        public interface Factory { State make(); }
        protected static class Helper { public int count; }
    }
    public int stable() { return 0; }
}
'''
        after = before.replace('int frame', 'long frame').replace('public int count', 'public long count')
        self.assert_api_source_change(before, after, None)


    def test_public_and_protected_nested_types_still_require_the_pin(self):
        for visibility in ('public', 'protected'):
            for declaration in ('static class State { public int value; }',
                                'record State(\n int frame,\n int tick\n) {}',
                                'interface State { int value(); }',
                                'enum State { FIRST, SECOND }',
                                '@interface State { int value() default 1; }'):
                nested = visibility + '\n static ' + declaration.removeprefix('static ')
                plain = '@ModApi\npublic class Api {\n%s\n}\n'
                changed = nested.replace('int frame', 'long frame').replace('int value', 'long value')
                changed = changed.replace('FIRST, SECOND', 'FIRST, SECOND, THIRD')
                for action, before, after in (('add', '', nested), ('change', nested, changed),
                                               ('remove', nested, '')):
                    with self.subTest(visibility=visibility, declaration=declaration, action=action):
                        self.assert_api_source_change(plain % before, plain % after, 'candidate signature pin')


    def test_implicitly_public_interface_nested_types_remain_guarded(self):
        for kind in ('interface', '@interface'):
            before = ('@ModApi\npublic ' + kind + ' Api {\n'
                      '    record State(\n        int frame,\n        int tick) {}\n'
                      '    class Helper { public int value; }\n}\n')
            for after in (before.replace('int tick', 'long tick'),
                          before.replace('public int value', 'public long value')):
                with self.subTest(kind=kind, after=after):
                    self.assert_api_source_change(before, after, 'candidate signature pin')


    def test_hidden_types_referenced_by_retained_declarations_remain_guarded(self):
        references = [
            'public Internal leak() { return null; }',
            'protected void accept(Internal value) {}',
            'public java.util.List<? extends Internal> values;',
            'public <T extends Internal> T convert(T value) { return value; }',
            'public record State(Internal value) {}',
            '@Uses(Internal.class) public int annotated() { return 0; }',
            'public static class Child extends Internal {}',
            'public sealed interface Shape permits Internal {}',
            'public Link leak() { return null; }\n'
            'static class Link { public Internal target; }',
        ]
        for reference in references:
            # The sealed case has a valid permitted implementation; other cases
            # deliberately use the same hidden name to exercise closure edges.
            implementation = (' implements Shape' if 'permits' in reference else '')
            before = ('@ModApi\npublic class Api {\n' + reference + '\n'
                      'static final class Internal' + implementation + ' { public int value; }\n}\n')
            if 'extends Internal' in reference:
                before = before.replace('static final class Internal', 'static class Internal')
            with self.subTest(reference=reference):
                self.assert_api_source_change(before, before.replace('public int value;', 'public long value;'),
                                              'candidate signature pin')


    def test_explicitly_annotated_hidden_nested_type_is_retained_conservatively(self):
        before = '@ModApi\npublic class Api {\n @ModApi private record Internal(int frame) {}\n}\n'
        self.assert_api_source_change(before, before.replace('int frame', 'long frame'), 'candidate signature pin')
        before = '''@ModApi
public class Api {
    private static class Owner {
        @ModApi public record State(int frame) {}
    }
}
'''
        self.assert_api_source_change(before, before.replace('class Owner', 'class Renamed'), 'candidate signature pin')


    def test_private_signatures_and_implementation_references_do_not_expose_hidden_types(self):
        before = '''@ModApi
public class Api {
    private Hidden capture() { return new Hidden(1); }
    Hidden captureInternal() { return new Hidden(2); }
    public int stable() { Hidden local = capture(); return 0; }
    private java.util.function.Supplier<Hidden> factory = () -> { return capture(); };
    private static record Hidden(int frame) { public int count() { return 1; } }
}
'''
        self.assert_api_source_change(before, before.replace('int frame', 'long frame'), None)


    def test_hidden_types_respect_comments_literals_bodies_and_initializer_braces(self):
        before = '''@ModApi
public class Api {
    private Object factory = new Object() { public String label() { return "}"; } };
    static /* record Fake(public int value) { */ class Hidden {
        public String braces() { return "} // not a comment /* {"; }
        public char quote() { return '\\''; }
        public String block() { return """
            } /* public record Fake(int x) {} */ {
            """; }
        public static record Child(int frame) {}
    }
    public record State(int frame) {}
    public int stable() { return 1; }
}
'''
        self.assert_api_source_change(before, before.replace('Child(int frame)', 'Child(long frame)'), None)
        self.assert_api_source_change(before, before.replace('State(int frame)', 'State(long frame)'),
                                      'candidate signature pin')


    def test_same_line_hidden_body_does_not_swallow_visible_siblings(self):
        before = '@ModApi\npublic class Api { static class Hidden {} public record State(int frame) {} }\n'
        self.assert_api_source_change(before, before.replace('Hidden {}', 'Hidden { public long value; }'), None)
        self.assert_api_source_change(before, before.replace('int frame', 'long frame'), 'candidate signature pin')


    def test_multiline_generic_and_declaration_continuations_remain_guarded(self):
        before = '''@ModApi
public abstract class Api<T
        extends Number>
        implements java.io.Serializable {
    @Deprecated
    public
    abstract <R extends Number> java.util.List<R>
            values(
                T value)
            throws java.io.IOException;
    protected
    static class State<T>
            extends java.util.ArrayList<T>
            implements java.io.Serializable {}
}
'''
        for old, new in (('extends Number>', 'extends CharSequence>'),
                         ('T value)', 'String value)'),
                         ('throws java.io.IOException', 'throws java.lang.Exception'),
                         ('extends java.util.ArrayList<T>', 'extends java.util.LinkedList<T>'),
                         ('@Deprecated', '@Deprecated(since="1")')):
            with self.subTest(old=old):
                self.assert_api_source_change(before, before.replace(old, new), 'candidate signature pin')


    def test_executable_bodies_and_field_initializers_do_not_supply_surface_declarations(self):
        before = '''@ModApi
public class Api {
    private Object factory = new Object() { public int count() { return 1; } };
    public int value() {
        class Local { public int count() { return 1; } }
        String text = "public record Fake(int x) {} // /*";
        return new Local().count();
    }
    static { System.out.println("}"); }
}
'''
        after = before.replace('int count()', 'long count()').replace('return new Local().count();', 'return 0;')
        self.assert_api_source_change(before, after, None)


    def test_public_field_initializer_values_do_not_require_a_pin(self):
        # snapshotLines pins field owner/modifiers/type/name/annotations, not values.
        before = '@ModApi\npublic class Api { public static final int VALUE = 1; }\n'
        self.assert_api_source_change(before, before.replace('VALUE = 1', 'VALUE = 2'), None)


    def test_record_compact_constructor_bodies_do_not_require_a_pin(self):
        for outer in ('public record Api(int value)', 'public class Api'):
            before = '@ModApi\n' + outer + ' {\n'
            if outer == 'public class Api':
                before += ' public record State(int value) { public State { if (value < 0) throw new IllegalArgumentException("}"); } }\n'
            else:
                before += ' public Api { if (value < 0) throw new IllegalArgumentException("}"); }\n'
            before += '}\n'
            with self.subTest(outer=outer):
                self.assert_api_source_change(before, before.replace('value < 0', 'value < -1'), None)


    def test_annotation_literal_values_and_defaults_still_require_a_pin(self):
        before = '@ModApi\n@Label("two  spaces // /*")\npublic class Api {}\n'
        self.assert_api_source_change(before, before.replace('two  spaces', 'two spaces'), 'candidate signature pin')
        before = '@ModApi\npublic @interface Api { String value() default "first"; }\n'
        self.assert_api_source_change(before, before.replace('"first"', '"second"'), 'candidate signature pin')
        before = '@ModApi\npublic @interface Api { int[] value() default {1, 2}; }\n'
        self.assert_api_source_change(before, before.replace('{1, 2}', '{1, 3}'), 'candidate signature pin')
        before = '@ModApi\npublic @interface Api { boolean value() default 1 == 1; }\n'
        self.assert_api_source_change(before, before.replace('1 == 1', '1 == 2'), 'candidate signature pin')


    def test_compact_constructor_annotations_remain_guarded(self):
        before = '@ModApi\npublic record Api(int frame) { @Deprecated public Api {} }\n'
        self.assert_api_source_change(before, before.replace('@Deprecated ', ''), 'candidate signature pin')


    def test_inferred_sealed_permits_keep_hidden_implementations_guarded(self):
        before = '''@ModApi
public class Api {
    public sealed interface Shape {}
    static final class Internal implements Shape { public int value; }
}
'''
        self.assert_api_source_change(before, before.replace('public int value', 'public long value'),
                                      'candidate signature pin')


    def test_enum_constant_specific_bodies_remain_conservative(self):
        before = '''@ModApi
public enum Api {
    FIRST { public int count() { return 1; } };
    public int count() { return 0; }
}
'''
        self.assert_api_source_change(before, before.replace('public int count()', 'public long count()'),
                                      'candidate signature pin')


    def test_dollar_identifiers_in_referenced_hidden_types_remain_guarded(self):
        before = '''@ModApi
public class Api {
    public Internal$State state() { return null; }
    static record Internal$State(int value) {}
}
'''
        self.assert_api_source_change(before, before.replace('int value', 'long value'), 'candidate signature pin')


    def test_existing_spindash_controller_can_add_internal_effects_rewind_state(self):
        # Use the real annotated controller, including its existing public
        # four-component state and Unicode punctuation in implementation comments.
        before = (ROOT / 'src/main/java/com/openggf/sprites/managers/SpindashDustController.java').read_text()
        body, close = before.rsplit('}', 1)
        after = body + '''
    record EffectsRewindState(
            int frame, int tick, int mapping, boolean active,
            int x, int y, boolean facingLeft, int splashFrame,
            int splashTick, boolean surfaceActive, int surfaceX,
            int surfaceY, int surfaceTimer) {}
    EffectsRewindState captureEffects() { return null; }
}''' + close
        self.assert_api_source_change(before, after, None)


    def test_candidate_pin_changes_still_require_a_real_detectable_surface_change(self):
        before = '@ModApi\npublic class Api {\n public record State(int frame) {}\n}\n'
        after = before.replace('\n}', '\n private record Internal(int frame) {}\n}')
        self.assert_api_source_change(before, after, 'candidate signature-pin content changes require', update_pin=True)
        self.assert_api_source_change(before, before.replace('int frame', 'long frame'), None, update_pin=True)


    def test_unicode_escape_sources_are_retained_conservatively(self):
        before = '@ModApi\npublic class Api {\\u0070ublic record State(int value) {} }\n'
        self.assert_api_source_change(before, before.replace('int value', 'long value'), 'candidate signature pin')


    def test_annotation_default_arrays_at_punctuation_boundaries_remain_pin_content(self):
        for declaration in ('int[] value() default', 'int[] value()default',
                            'int[] value()default/* separator */'):
            before = '@ModApi\npublic @interface Api { ' + declaration + '{1, 2}; }\n'
            after = before.replace('{1, 2}', '{1, 3}')
            for update_pin in (False, True):
                with self.subTest(declaration=declaration, update_pin=update_pin):
                    self.assert_api_source_change(before, after,
                                                  None if update_pin else 'candidate signature pin',
                                                  update_pin=update_pin)


    def test_public_field_declarators_annotations_and_dimensions_remain_guarded(self):
        before = '''@ModApi
public class Api {
    @Deprecated
    public static final int FIRST = call(1, 2),
            SECOND[] = {3, 4}, THIRD[][] = {{5}};
}
'''
        changes = [
            before.replace('SECOND[]', 'RENAMED[]'),
            before.replace('SECOND[] = {3, 4}, ', ''),
            before.replace('final int FIRST', 'final long FIRST'),
            before.replace('public static final', 'public static'),
            before.replace('public static final', 'protected static final'),
            before.replace('THIRD[][] = {{5}}', 'THIRD[] = {5}'),
            before.replace('@Deprecated', '@Deprecated(since="1")'),
        ]
        for after in changes:
            for update_pin in (False, True):
                with self.subTest(after=after, update_pin=update_pin):
                    self.assert_api_source_change(before, after,
                                                  None if update_pin else 'candidate signature pin',
                                                  update_pin=update_pin)


    def test_generic_initializer_commas_and_comparisons_preserve_field_boundaries(self):
        before = '''@ModApi
public class Api {
    public static final java.util.Map<String, java.util.List<Integer>> FIRST =
            new java.util.HashMap<String, java.util.List<Integer>>(),
            SECOND = java.util.Collections.<String, java.util.List<Integer>>emptyMap();
    public static boolean LESS = 1 < 2, MORE = 3 > 2, EQUAL = 1 == 1;
    public static int UNSET, VALUE = call(1, 2), OTHER[];
    private void hidden() { System.out.println("public class Fake {}"); }
    public int following() { return 1; }
}
'''
        after = before.replace('HashMap', 'TreeMap').replace('Collections.', 'Map.').replace('emptyMap()', 'of()')
        after = after.replace('1 < 2', '3 < 4').replace('3 > 2', '5 > 4').replace('1 == 1', '2 == 2')
        after = after.replace('call(1, 2)', 'call(4, 5)').replace('return 1;', 'return 2;')
        self.assert_api_source_change(before, after, None)
        changed = before.replace(', OTHER[]', ', OTHER[][]')
        self.assert_api_source_change(before, changed, 'candidate signature pin')
        removed = before.replace(',\n            SECOND = java.util.Collections.<String, java.util.List<Integer>>emptyMap()', '')
        self.assert_api_source_change(before, removed, None, update_pin=True)


    def test_field_initializer_references_do_not_expose_hidden_implementation_types(self):
        before = '''@ModApi
public class Api {
    public static final Object VALUE = new Hidden();
    private static class Hidden { public int value; }
}
'''
        self.assert_api_source_change(before, before.replace('public int value', 'public long value'), None)
        exposed = before.replace('final Object VALUE', 'final Hidden VALUE')
        self.assert_api_source_change(before, exposed, 'candidate signature pin')
        self.assert_api_source_change(before, exposed, None, update_pin=True)


    def test_explicit_constructor_type_arguments_do_not_expose_hidden_implementation_types(self):
        before = '''@ModApi
public class Api {
    public static final Object VALUE = new <String, Hidden> Box();
    private static class Box { <A, B> Box() {} }
    private static class Hidden { public int value; }
}
'''
        for prefix in ('new ', 'new', 'new/* constructor type arguments */\n            '):
            source = before.replace('new ', prefix)
            with self.subTest(prefix=prefix):
                self.assert_api_source_change(source, source.replace('public int value', 'public long value'), None)
        changed = before.replace('<String, Hidden> Box()', '<Integer, Object> Box()')
        self.assert_api_source_change(before, changed, None)
        self.assert_api_source_change(before, changed, 'candidate signature-pin content changes require',
                                      update_pin=True)


    def test_explicit_constructor_nested_generics_preserve_following_field_declarators(self):
        before = '''@ModApi
public class Api {
    public static final Object FIRST =
            new <java.util.Map<String, java.util.List<Hidden>>, Hidden[]> Box<Hidden>(1),
            SECOND = new <String, Hidden> Box<Hidden>(2),
            THIRD[] = {new <String, Hidden> Box<Hidden>(3)};
    private static class Box<T> { <A, B> Box(int value) {} }
    private static class Hidden { public int value; }
    public Object following() { return null; }
}
'''
        changed = before.replace('Hidden', 'Internal').replace('Box<Internal>(1)', 'Box<Internal>(4)')
        changed = changed.replace('public int value', 'public long value')
        self.assert_api_source_change(before, changed, None)
        changes = [
            before.replace('SECOND =', 'RENAMED ='),
            before.replace('            SECOND = new <String, Hidden> Box<Hidden>(2),\n', ''),
            before.replace('final Object FIRST', 'final Box FIRST'),
            before.replace('public static final Object', 'protected static final Object'),
            before.replace('public static final Object', 'public static Object'),
            before.replace('public Object following()', 'public String following()'),
            before.replace('THIRD[] = {new <String, Hidden> Box<Hidden>(3)}',
                           'THIRD[][] = {{new <String, Hidden> Box<Hidden>(3)}}'),
        ]
        for after in changes:
            for update_pin in (False, True):
                with self.subTest(after=after, update_pin=update_pin):
                    self.assert_api_source_change(before, after,
                                                  None if update_pin else 'candidate signature pin',
                                                  update_pin=update_pin)


    def test_published_release_can_promote_to_next_but_later_debt_is_rejected(self):
        self.write('released.txt', 'published master work')
        self.published_baseline = self.commit('published history without trailers')
        self.assert_policy(self.base, self.base, self.published_baseline, 'next', None)
        self.write('later.txt', 'new promotion work'); head = self.commit('new missing trailers')
        self.assert_policy(self.base, self.base, head, 'next', 'documentation policy')

    def test_published_release_trailer_boundary_does_not_admit_new_resource_violations(self):
        self.published_baseline = self.base
        self.write('traces/physics.csv', 'x' * 1048576); self.commit('new oversized trace')
        self.git('rm', 'traces/physics.csv'); head = self.commit('remove new violation')
        self.assert_policy(self.base, self.base, head, 'next', 'uncompressed trace payload')

    def test_release_with_diverged_master_checks_only_new_trailers(self):
        self.git('switch', '-c', 'old-master')
        self.write('target.txt', 'existing target history\n'); target = self.commit('old target without trailers')
        self.git('switch', 'main')
        self.write('snapshot.txt', 'reviewed history\n'); baseline = self.commit('snapshot without trailers')
        self.git('merge', '--no-ff', 'old-master', '-m', 'merge target history')
        head = self.git('rev-parse', 'HEAD')
        self.assert_policy(baseline, target, head, 'master', None)
        self.write('new.txt', 'must carry trailers\n'); head = self.commit('missing new trailers')
        self.assert_policy(baseline, target, head, 'master', 'documentation policy')

    def test_shared_line_admits_frozen_import_but_rejects_later_trailer_debt(self):
        self.write('imported.txt', 'published next history')
        self.shared_baseline = self.commit('imported history without trailers')
        self.assert_policy(self.base, self.base, self.shared_baseline, 'develop', None)
        self.write('later.txt', 'new work'); head = self.commit('new missing trailers')
        self.assert_policy(self.base, self.base, head, 'develop', 'documentation policy')

    def test_shared_line_boundary_does_not_admit_new_uncoupled_api_changes(self):
        self.shared_baseline = self.base
        self.write('src/main/java/example/NewApi.java', '@ModApi\npublic interface NewApi {}\n')
        head = self.commit('chore: add API\n\n' + '\n'.join(
            key + ': n/a' for key in ['Changelog', 'Guide', 'Known-Discrepancies',
                                     'S3K-Known-Discrepancies', 'Agent-Docs',
                                     'Configuration-Docs', 'Skills']))
        self.assert_policy(self.base, self.base, head, 'develop', 'candidate signature pin')

    def test_shared_line_boundary_does_not_hide_imported_resource_violations(self):
        self.write('traces/physics.csv', 'x' * 1048576); self.commit('imported oversized trace')
        self.git('rm', 'traces/physics.csv'); self.shared_baseline = self.commit('remove violation')
        self.assert_policy(self.base, self.base, self.shared_baseline, 'develop', 'uncompressed trace payload')

    def test_shared_line_boundary_does_not_grandfather_unrelated_merged_work(self):
        self.git('switch', '-c', 'imported-next')
        self.write('imported.txt', 'published next history')
        self.shared_baseline = self.commit('imported history without trailers')
        self.git('switch', 'main')
        self.write('unrelated.txt', 'unreviewed history'); self.commit('unrelated missing trailers')
        self.git('merge', '--no-ff', 'imported-next', '-m', 'merge imported history')
        head = self.git('rev-parse', 'HEAD')
        self.assert_policy(self.base, self.base, head, 'develop', 'documentation policy')


if __name__ == '__main__':
    unittest.main()
