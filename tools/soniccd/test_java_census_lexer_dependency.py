#!/usr/bin/env python3
"""Exact approved lexer-pin regression and bounded rejection controls.

Purpose: prevent a sibling lexer update disabling census before source scanning.
Inputs: sibling census + approved enum helper; owned temporary dependency copies.
Origin: 2026-11-05 private Sonic CD2b7f610f source-preflight repair.
"""
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import test_java_declaration_census as support

census = support.census
APPROVED_BYTES = 47279
APPROVED_SHA256 = 'bc4d7daf1689216391efd423b4628c728a1f0b63395e89398b09930f686a770d'


class ApprovedLexerDependency(unittest.TestCase):
    def setUp(self):
        self.approved = (Path(census.__file__).parent / 'enum_contract_audit.py').read_bytes()
        self.assertEqual(APPROVED_BYTES, len(self.approved))
        self.assertEqual(APPROVED_SHA256, hashlib.sha256(self.approved).hexdigest())
        self.owned = tempfile.TemporaryDirectory(prefix='census-dependency-owned-')
        self.addCleanup(self.owned.cleanup)
        self.root = Path(self.owned.name)
        self.source = self.root / 'java_declaration_census.py'
        self.dependency = self.root / 'enum_contract_audit.py'
        self.dependency.write_bytes(self.approved)
        self.source.write_bytes(b'# selected dependency location only\n')
        self.saved_lexer = mock.patch.object(census, '_LEXER', None)
        self.saved_lexer.start()
        self.addCleanup(self.saved_lexer.stop)
        self.saved_path = mock.patch.object(census, '__file__', str(self.source))
        self.saved_path.start()
        self.addCleanup(self.saved_path.stop)

    def test_approved_sibling_loads_before_source_inventory(self):
        lexer = census.lexer()
        java = lexer.lex(b'package p; import q.Other; class C { void f() {} }', census.Limits())
        self.assertEqual('p', java.package)
        self.assertEqual([(False, 'q.Other')], java.imports)
        opening = next(i for i,t in enumerate(java.tokens) if t.value == '{')
        self.assertEqual('}', java.tokens[java.pairs[opening]].value)
        self.assertEqual(['C', 'f'], [d['name'] for d in census.inventory(b'class C { void f() {} }', census.Limits())])
        count = census.test_inventory(b'package p; import org.junit.jupiter.api.Test; class C { @Test void one() {} }', census.Limits(), 'p.C')
        self.assertEqual(1, count['proposed_count'])
        self.assertTrue(count['complete'])

    def test_same_size_unapproved_bytes_reject_before_compile(self):
        data = bytearray(self.approved)
        data[0] ^= 1
        self.dependency.write_bytes(data)
        with mock.patch('builtins.compile') as compile_spy:
            with self.assertRaises(census.CensusError) as caught:
                census.lexer()
            self.assertEqual(2, caught.exception.code)
            self.assertIn('dependency pin', str(caught.exception))
            compile_spy.assert_not_called()
        self.assertIsNone(census._LEXER)

    def test_oversized_dependency_rejects_before_any_byte_read(self):
        self.dependency.write_bytes(self.approved + b'\n')
        with mock.patch.object(census.os, 'read', wraps=census.os.read) as read_spy, mock.patch('builtins.compile') as compile_spy:
            with self.assertRaises(census.CensusError) as caught:
                census.lexer()
            self.assertEqual(3, caught.exception.code)
            self.assertEqual('File byte budget exceeded before read', str(caught.exception))
            read_spy.assert_not_called()
            compile_spy.assert_not_called()
        self.assertIsNone(census._LEXER)

    def test_short_dependency_rejects_before_compile(self):
        self.dependency.write_bytes(self.approved[:-1])
        with mock.patch('builtins.compile') as compile_spy:
            with self.assertRaises(census.CensusError) as caught:
                census.lexer()
            self.assertEqual(2, caught.exception.code)
            compile_spy.assert_not_called()
        self.assertIsNone(census._LEXER)

    def test_dependency_symlink_rejected_without_reading_bytes(self):
        self.dependency.unlink()
        target = self.root / 'approved.py'
        target.write_bytes(self.approved)
        self.dependency.symlink_to(target)
        with mock.patch.object(census.os, 'read', wraps=census.os.read) as read_spy:
            with self.assertRaises(census.CensusError) as caught:
                census.lexer()
            self.assertEqual(2, caught.exception.code)
            read_spy.assert_not_called()
        self.assertIsNone(census._LEXER)

    def test_source_limits_and_unsupported_unicode_remain_enforced(self):
        census.lexer()
        cases = [(b'class C {}', census.Limits(max_file_bytes=1)),
                 (b'class C {}', census.Limits(max_tokens=1)),
                 (b'class C { \\u0041 x; }', census.Limits())]
        for data, limits in cases:
            with self.subTest(data=data, limits=limits), self.assertRaises(census.CensusError) as caught:
                census.inventory(data, limits)
            self.assertEqual(3, caught.exception.code)


if __name__ == '__main__':
    unittest.main()
