"""Offline contributor behavior; ICU grammar remains owned by the Java runtime."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import catalogs


class CatalogToolTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'i18n'
        (self.root / 'source').mkdir(parents=True)
        self.source = {
            '@@locale': 'en',
            'action.save': 'Save',
            '@action.save': {'description': 'Commit the pending settings.'},
            'item.count': '{count, plural, one {# item} other {# items}}',
            '@item.count': {'description': 'Number of items.', 'placeholders': {'count': {'type': 'int'}}},
            'legal.notice': 'An independent project.',
            '@legal.notice': {'description': 'Legal meaning must be reviewed.', 'x-openggf': {'critical': True}},
        }
        self.write(self.root / 'source' / 'core.arb', self.source)

    @staticmethod
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    def command(self, *args):
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = catalogs.main(['--root', str(self.root), *args])
        return code, stdout.getvalue(), stderr.getvalue()

    def init(self, tag='fr', name='Français'):
        code, out, error = self.command('init', tag, '--name', name)
        self.assertEqual(0, code, error)
        return self.root / 'locales' / f'{catalogs.canonical_tag(tag)}.arb'

    def translate(self, path, message='action.save', pattern='Enregistrer', origin='human'):
        document = catalogs.read_json(path)
        document[message] = pattern
        document['@' + message]['x-openggf']['origin'] = origin
        self.write(path, document)

    def report(self, tag='fr'):
        return catalogs.inspect(self.root)[1][tag]

    def test_init_adds_one_catalog_with_open_language_script_and_region_tag_and_no_java(self):
        marker = self.root / 'Engine.java'
        marker.write_text('unchanged', encoding='utf-8')
        before = set(self.root.rglob('*'))
        path = self.init('sr-latn-rs', 'Srpski (latinica)')
        document = catalogs.read_json(path)
        self.assertEqual('sr-Latn-RS', document['@@locale'])
        self.assertEqual('Srpski (latinica)', document['@@name'])
        self.assertEqual('community draft', document['@@quality'])
        self.assertEqual('', document['action.save'])
        stamp = document['@action.save']['x-openggf']
        self.assertRegex(stamp['sourceHash'], r'^[0-9a-f]{64}$')
        self.assertEqual('human', stamp['origin'])
        self.assertFalse(stamp['reviewed'])
        self.assertEqual('unchanged', marker.read_text())
        self.assertEqual({self.root / 'locales', path}, set(self.root.rglob('*')) - before)
        self.assertNotEqual(0, self.command('init', 'sr-Latn-RS', '--name', 'Overwrite')[0])

    def test_fingerprint_uses_exact_utf8_description_and_sorted_placeholder_contract(self):
        metadata = {'description': 'Greeting', 'placeholders': {'z': {'type': 'int'}, 'name': {}}}
        expected = hashlib.sha256('Hi {name}\nGreeting\nname:String\nz:int'.encode('utf-8')).hexdigest()
        self.assertEqual(expected, catalogs.source_fingerprint('Hi {name}', metadata))
        self.assertNotEqual(expected, catalogs.source_fingerprint('Hi {name}', {**metadata, 'description': 'New note'}))

    def test_partial_catalog_status_counts_missing_and_does_not_count_copied_english(self):
        path = self.init()
        self.translate(path)
        self.translate(path, 'item.count', self.source['item.count'])
        report = self.report()
        self.assertEqual(1, report.usable)
        self.assertEqual(1, report.identical)
        self.assertEqual(1, report.missing)
        code, output, error = self.command('check')
        self.assertEqual(0, code, error)
        self.assertIn('ICU', output)
        self.assertIn('English-identical', output)

    def test_source_revision_becomes_stale_until_deliberate_stamp_and_review_is_cleared(self):
        path = self.init()
        self.translate(path)
        self.assertEqual(0, self.command('review', 'fr', '--reviewer', 'Camille', '--ids', 'action.save')[0])
        self.source['@action.save']['description'] = 'Revised source meaning.'
        self.write(self.root / 'source' / 'core.arb', self.source)
        self.assertEqual(1, self.report().stale)
        self.assertEqual(0, self.command('stamp', 'fr', '--ids', 'action.save')[0])
        stamp = catalogs.read_json(path)['@action.save']['x-openggf']
        self.assertFalse(stamp['reviewed'])
        self.assertNotIn('translationHash', stamp)
        self.assertNotIn('reviewer', stamp)
        self.assertEqual(1, self.report().usable)

    def test_review_records_exact_pattern_and_keeps_machine_origin_and_critical_fallback(self):
        path = self.init()
        self.translate(path, 'legal.notice', 'Un projet indépendant.', 'claude-haiku-5-5')
        self.assertEqual(1, self.report().unreviewed_critical)
        self.assertEqual(0, self.command('review', 'fr', '--reviewer', 'Camille', '--ids', 'legal.notice')[0])
        document = catalogs.read_json(path)
        stamp = document['@legal.notice']['x-openggf']
        self.assertEqual('claude-haiku-5-5', stamp['origin'])
        self.assertEqual('Camille', stamp['reviewer'])
        self.assertEqual(hashlib.sha256(document['legal.notice'].encode('utf-8')).hexdigest(), stamp['translationHash'])
        self.assertEqual(1, self.report().reviewed)
        document['legal.notice'] = 'Une nouvelle traduction.'
        self.write(path, document)
        report = self.report()
        self.assertEqual(0, report.reviewed)
        self.assertEqual(1, report.review_invalidated)
        self.assertEqual(1, report.unreviewed_critical)

    def test_duplicate_json_keys_invalid_utf8_unknown_ids_and_nonstring_shapes_fail(self):
        path = self.init()
        original = path.read_bytes()
        bad = [
            b'{"@@locale":"fr","@@locale":"de"}',
            b'{"@@locale":"fr","@@name":"\xff"}',
            json.dumps({'@@locale': 'fr', '@@name': 'Français', '@@quality': 'community draft', 'unknown.id': 'Bonjour'}).encode(),
            json.dumps({'@@locale': 'fr', '@@name': 4}).encode(),
            original.replace(b'"action.save": ""', b'"action.save": []'),
            original.replace(b'"reviewed": false', b'"reviewed": "false"'),
        ]
        for payload in bad:
            with self.subTest(payload=payload[:80]):
                path.write_bytes(payload)
                self.assertNotEqual(0, self.command('check')[0])
        path.write_bytes(original)
        self.assertEqual(0, self.command('check')[0])

    def test_source_fragments_cannot_duplicate_ids_or_orphan_metadata(self):
        self.write(self.root / 'source' / 'duplicate.arb', {'@@locale': 'en', 'action.save': 'Duplicate'})
        self.assertNotEqual(0, self.command('check')[0])

    def test_placeholder_contract_metadata_has_string_types_and_json_rejects_surrogates(self):
        self.source['@item.count']['placeholders']['count']['type'] = []
        self.write(self.root / 'source' / 'core.arb', self.source)
        self.assertNotEqual(0, self.command('check')[0])
        self.source['@item.count']['placeholders']['count']['type'] = 'int'
        self.write(self.root / 'source' / 'core.arb', self.source)
        path = self.init()
        path.write_bytes(b'{"@@locale":"fr","@@name":"\\ud800"}')
        self.assertNotEqual(0, self.command('check')[0])

    def test_review_refuses_empty_entries_and_stamp_cannot_relabel_a_machine_origin(self):
        path = self.init()
        before = path.read_bytes()
        self.assertNotEqual(0, self.command('review', 'fr', '--reviewer', 'Camille', '--ids', 'legal.notice')[0])
        self.assertEqual(before, path.read_bytes(), 'failed review must not partially publish edits')
        self.translate(path, origin='claude-haiku-5-5')
        before = path.read_bytes()
        self.assertNotEqual(0, self.command('stamp', 'fr', '--origin', 'human', '--ids', 'action.save')[0])
        self.assertEqual(before, path.read_bytes())
        self.write(self.root / 'source' / 'duplicate.arb', {'@@locale': 'en', '@missing.id': {}})
        self.assertNotEqual(0, self.command('check')[0])

    def test_invalid_provenance_hash_shapes_and_fallback_cycles_fail(self):
        path = self.init()
        self.translate(path)
        original = catalogs.read_json(path)
        for key, value in [('sourceHash', 'wrong'), ('origin', 'machine'), ('reviewed', 1)]:
            document = json.loads(json.dumps(original))
            document['@action.save']['x-openggf'][key] = value
            self.write(path, document)
            self.assertNotEqual(0, self.command('check')[0])
        original['@@parents'] = ['fr']
        self.write(path, original)
        self.assertNotEqual(0, self.command('check')[0])

    def test_quick_checks_do_not_parse_icu_and_runtime_uses_the_queued_java_authority(self):
        path = self.init()
        self.translate(path, pattern='{not valid ICU')
        self.assertEqual(0, self.command('check')[0])
        with mock.patch.object(catalogs.subprocess, 'run', return_value=mock.Mock(returncode=7)) as run:
            code, _, _ = self.command('check', '--runtime')
        self.assertEqual(7, code)
        args = run.call_args.args[0]
        self.assertTrue(any(str(argument).endswith('tools/testing/maven_queue.py') for argument in args))
        self.assertIn('compile', args)
        self.assertIn('exec:java', args)
        self.assertIn('-Dexec.mainClass=com.openggf.i18n.CatalogTool', args)
        self.assertNotIn('test', args)

    def test_status_is_read_only_and_reports_new_source_ids(self):
        path = self.init()
        before = path.read_bytes()
        self.source['action.new'] = 'New action'
        self.source['@action.new'] = {'description': 'Added later.'}
        self.write(self.root / 'source' / 'core.arb', self.source)
        code, output, error = self.command('status', 'fr', '--details')
        self.assertEqual(0, code, error)
        self.assertIn('action.new', output)
        self.assertEqual(before, path.read_bytes())


if __name__ == '__main__':
    unittest.main()
