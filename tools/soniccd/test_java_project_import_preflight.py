#!/usr/bin/env python3
"""Focused stdlib controls; temporary Git fixtures are owned and removed by this suite.

Optional frozen forward test inputs are explicit PREFLIGHT_* environment pins.
No Maven, Java, source discovery or persistent result/index files.
"""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

REV = '696f4138855502d900a044f034e4b3c2443f176e'
DEPENDENCIES = {
    'java_declaration_census.py': ('304b6314407adaa114ae06f17e9e0c23da93f89110e454a1c681dfe51000a625', 65795),
    'enum_contract_audit.py': ('bc4d7daf1689216391efd423b4628c728a1f0b63395e89398b09930f686a770d', 47279),
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


class PreflightTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.owned = tempfile.TemporaryDirectory(prefix='import-preflight-owned-')
        cls.root = Path(cls.owned.name)
        stage = cls.root / 'tools'
        stage.mkdir()
        here = Path(__file__).absolute().parent
        for name, (sha, size) in DEPENDENCIES.items():
            # Installed-tree tests use verified siblings; external packets require
            # explicit immutable metadata, never a moving source checkout fallback.
            git_dir = os.environ.get('PREFLIGHT_DEPENDENCY_GIT_DIR')
            data = subprocess.check_output(['git', '--git-dir=' + git_dir, 'show',
                REV + ':tools/soniccd/' + name]) if git_dir else (here / name).read_bytes()
            if len(data) != size or digest(data) != sha:
                raise AssertionError('Trusted helper dependency pin mismatch: ' + name)
            (stage / name).write_bytes(data)
        shutil.copyfile(here / 'java_project_import_preflight.py', stage / 'java_project_import_preflight.py')
        spec = importlib.util.spec_from_file_location('_tested_import_preflight', stage / 'java_project_import_preflight.py')
        cls.p = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = cls.p
        spec.loader.exec_module(cls.p)
        cls.repo = cls.root / 'repo'
        cls.repo.mkdir()
        cls.git('init', '-q')
        cls.git('config', 'user.name', 'Owned Fixture')
        cls.git('config', 'user.email', 'fixture@example.invalid')
        (cls.repo / 'known').mkdir()
        (cls.repo / 'known' / 'Known.java').write_text('package p; public class Known { public enum Region { A } }\n')
        (cls.repo / 'known' / 'Odd.java').write_text('package p; public class Known {}\n')
        (cls.repo / 'known' / 'Wrong.java').write_text('package p; public class Other {}\n')
        (cls.repo / 'known' / 'Another.java').write_text('package r; public class Known {}\n')
        (cls.repo / 'Source.java').write_text('package q; class Source { Known.Region r; }\n')
        (cls.repo / 'known' / 'Alias.java').symlink_to('Known.java')
        cls.git('add', '.')
        cls.git('commit', '-qm', 'Owned immutable fixture')
        cls.rev = cls.git('rev-parse', 'HEAD').strip()
        cls.final = cls.root / 'Final.java'

    @classmethod
    def tearDownClass(cls):
        cls.owned.cleanup()

    @classmethod
    def git(cls, *args):
        env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        return subprocess.check_output(['git', '-C', str(cls.repo), *args], env=env, text=True)

    def args(self, text=None, extra=()):
        args = ['--git-dir', str(self.repo / '.git'), '--revision', self.rev,
                '--known-type', 'p.Known=known/Known.java']
        if text is None:
            args += ['--source', 'Source.java']
        else:
            data = text.encode() if isinstance(text, str) else text
            self.final.write_bytes(data)
            args += ['--final', str(self.final), '--final-sha256', digest(data), '--final-bytes', str(len(data))]
        return self.p.parser().parse_args([*args, *extra])

    def result(self, text=None, extra=()):
        return self.p.run(self.args(text, extra))

    def enum_result(self, text, extra=(), selections=None):
        args = self.args(text, extra)
        args.known_enum = selections or ['p.Known.Region=known/Known.java']
        return self.p.run(args)

    def test_known_enum_original_two_missing_members_are_reported_before_compile(self):
        r=self.enum_result('package q; import p.Known; class S { Object a=Known.Region.TOUCH_DIMENSIONS; Object b=Known.Region.ENEMY_POINTS; }')
        self.assertEqual(['TOUCH_DIMENSIONS','ENEMY_POINTS'],[h['member'] for h in r['hints']])
        self.assertTrue(all(h['kind']=='possible_missing_enum_member' for h in r['hints']))
        self.assertEqual('observed',r['status'])

    def test_known_enum_valid_constants_comments_whitespace_methods_and_class_literal(self):
        r=self.enum_result('package q; import p.Known; class S { Object a=Known /* split */ . Region . A; Object b=Known.Region.values(); Object c=Known.Region.class; String t="Known.Region.MISSING"; }')
        self.assertEqual([],r['hints']);self.assertEqual('observed',r['status'])
        self.assertEqual(['A'],[u['member'] for u in r['enum_uses']])
        self.assertEqual(['A'],r['known_enums'][0]['constants'])

    def test_known_enum_only_cli_and_fully_qualified_selector(self):
        args=self.args('package q; class S { Object a=p.Known.Region.MISSING; }')
        args.known_type=[];args.known_enum=['p.Known.Region=known/Known.java']
        r=self.p.run(args);self.assertEqual([],r['known_types']);self.assertEqual('MISSING',r['hints'][0]['member'])
        self.assertFalse(r['enum_uses'][0]['ambiguous'])
        argv=['--git-dir',args.git_dir,'--revision',args.revision,'--known-enum',args.known_enum[0],
              '--final',args.final,'--final-sha256',args.final_sha256,'--final-bytes',str(args.final_bytes)]
        proc=subprocess.run([sys.executable,'-B',self.p.__file__,*argv],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        self.assertEqual(1,proc.returncode,proc.stderr);self.assertEqual('MISSING',json.loads(proc.stdout)['hints'][0]['member'])

    def test_full_enum_spellings_keep_imported_first_identifier_ambiguity(self):
        for imported, blocked in [('import r.p;', True), ('import static r.Outer.p;', True),
                                  ('import static r.Outer.*;', False), ('import r.*;', False)]:
            for member in ['A', 'MISSING']:
                with self.subTest(imported=imported, member=member):
                    args=self.args('package q; '+imported+' class S { Object x=p.Known.Region.'+member+'; }')
                    args.known_type=[];args.known_enum=['p.Known.Region=known/Known.java']
                    result=self.p.run(args)
                    self.assertEqual('incomplete',result['status']);self.assertTrue(result['incomplete_reasons'])
                    self.assertTrue(result['enum_uses'][0]['ambiguous'])
                    if blocked:self.assertEqual([],result['hints'])
                    else:self.assertTrue(all(h['ambiguous'] for h in result['hints']))

    def test_annotated_generic_parameters_are_incomplete_without_enum_member_hints(self):
        for parameters in ['<Known>', '<@Ann Known>', '<@Ann(value=1) Known>',
                           '<T, @a.b.Ann(value={1,2}) Known>']:
            for member in ['A','MISSING']:
                with self.subTest(parameters=parameters,member=member):
                    args=self.args('package q; import p.Known; class S { '+parameters+' Object f(){ return Known.Region.'+member+'; } }')
                    args.known_type=[];args.known_enum=['p.Known.Region=known/Known.java']
                    result=self.p.run(args)
                    self.assertEqual('incomplete',result['status']);self.assertTrue(result['incomplete_reasons'])
                    self.assertFalse(any(h['kind']=='possible_missing_enum_member' for h in result['hints']))
        result=self.enum_result('package q; import p.Known; class S { @Ann Object f(){ return Known.Region.A; } }')
        self.assertEqual('observed',result['status']);self.assertEqual([],result['hints'])

    def test_known_enum_local_nested_value_and_type_parameter_shadowing_is_incomplete(self):
        for body in ['class S { class Known { } Object a=Known.Region.MISSING; }',
                     'class S { void f(){ class Known {} Object a=Known.Region.MISSING; } }',
                     'class S { Object Known; Object a=Known.Region.MISSING; }',
                     'class S<Known> { Object a=Known.Region.MISSING; }',
                     'class S { Object p; Object a=p.Known.Region.MISSING; }',
                     'class S { Object a=Known -> Known.Region.MISSING; }',
                     'class S { Object a=(Known) -> Known.Region.MISSING; }']:
            with self.subTest(body=body):
                r=self.enum_result('package q; '+body)
                self.assertEqual('incomplete',r['status']);self.assertTrue(r['incomplete_reasons'])
                self.assertFalse(any(h['kind']=='possible_missing_enum_member' for h in r['hints']))

    def test_known_enum_indirect_receiver_and_anonymous_scope_are_incomplete(self):
        for body in ['class S { Object a=receiver.Known.Region.MISSING; }',
                     'class S { Object a=new Object(){ Object x=Known.Region.MISSING; }; }']:
            r=self.enum_result('package q; import p.Known; '+body)
            self.assertEqual('incomplete',r['status'])
            self.assertFalse(any(h['kind']=='possible_missing_enum_member' for h in r['hints']))

    def test_known_enum_spelling_never_joins_split_tokens_and_malformed_selectors_are_incomplete(self):
        for selector in ['Known.Region.', 'Known.Region..MISSING', 'Known..Region.MISSING', 'Known.Reg ion.MISSING','Known.Region.MISS ING', 'Known.*.Region.MISSING', 'Known.Region.MISSING..field']:
            with self.subTest(selector=selector):
                r=self.enum_result('package q; import p.Known; class S { Object a='+selector+'; }')
                self.assertEqual('incomplete',r['status'])
                self.assertFalse(any(h['kind']=='possible_missing_enum_member' for h in r['hints']))
        r=self.enum_result('package q; import p.Known; class S { Object a=Known.Other.MISSING; }')
        self.assertEqual([],r['enum_uses'])
        self.assertEqual([],r['hints'])

    def test_known_enum_competing_explicit_import_blocks_resolution(self):
        r=self.enum_result('package q; import r.Known; class S { Object a=Known.Region.MISSING; }')
        self.assertEqual('incomplete',r['status'])
        self.assertFalse(any(h['kind']=='possible_missing_enum_member' for h in r['hints']))
        other=self.enum_result('package q; import p.*; class S { Object a=Known.Region.MISSING; } class T extends Object {}')
        self.assertEqual('incomplete',other['status']);self.assertTrue(other['hints'][0]['ambiguous'])

    def test_known_enum_known_type_count_bytes_tokens_and_hits_share_existing_limits(self):
        with self.assertRaises(self.p.PreflightError) as c:
            self.enum_result('package q; import p.Known; class S { Object a=Known.Region.A; }',['--max-known-types','1'])
        self.assertEqual(2,c.exception.code)
        with self.assertRaises(self.p.PreflightError) as c:
            self.enum_result('package q; import p.Known; class S { Object a=Known.Region.A; Object b=Known.Region.MISSING; }',['--max-hits','3'])
        self.assertEqual(3,c.exception.code)
        r=self.enum_result('package q; import p.Known; class S { Object a=Known.Region.A; }')
        self.assertEqual(2,len(r['uses'])+len(r['enum_uses']))
        self.assertEqual(self.result('package q; import p.Known; class S { Object a=Known.Region.A; }')['selected_bytes'],r['selected_bytes'])

    def test_known_enum_selection_requires_exact_enum_provenance_and_distinct_names(self):
        for selection in ['p.Known.Missing=known/Known.java','p.Known=known/Known.java','p.Known.Region=known/Wrong.java',
                          'p..Known.Region=known/Known.java','p.Known.Region=known/Alias.java']:
            with self.subTest(selection=selection):
                with self.assertRaises(self.p.PreflightError):self.enum_result('class S {}',selections=[selection])
        with self.assertRaises(self.p.PreflightError):
            self.enum_result('class S {}',selections=['p.Known.Region=known/Known.java']*2)

    def test_known_enum_duplicate_short_qualifiers_are_incomplete_without_a_false_member_hint(self):
        (self.repo/'known'/'OtherEnum.java').write_text('package r; class Known { enum Region { B } }')
        self.git('add','known/OtherEnum.java');self.git('commit','-qm','Owned enum ambiguity fixture')
        args=self.args('package q; class S { Object a=Known.Region.MISSING; }');args.revision=self.git('rev-parse','HEAD').strip()
        args.known_enum=['p.Known.Region=known/Known.java','r.Known.Region=known/OtherEnum.java']
        r=self.p.run(args);self.assertEqual('incomplete',r['status'])
        self.assertFalse(any(h['kind']=='possible_missing_enum_member' for h in r['hints']))

    def test_known_enum_malformed_declaration_and_local_declaration_are_incomplete(self):
        for index,source in enumerate(['package p; class Bad { enum Region { A B } }',
                                       'package p; class Bad { void f(){ enum Region { A } } }',
                                       'package p; class Bad { enum Region { A } enum Region { B } }']):
            path='known/Bad'+str(index)+'.java';(self.repo/path).write_text(source)
            self.git('add',path);self.git('commit','-qm','Owned malformed enum fixture')
            args=self.args('class S {}');args.revision=self.git('rev-parse','HEAD').strip();args.known_enum=['p.Bad.Region='+path]
            with self.assertRaises(self.p.PreflightError) as c:self.p.run(args)
            self.assertEqual(3,c.exception.code)

    @unittest.skipUnless(os.environ.get('PREFLIGHT_KNOWN_ENUM_GIT_DIR'),'explicit immutable actual enum source metadata not supplied')
    def test_actual_profile_mistake_and_player_enum_correction(self):
        git_dir=os.environ['PREFLIGHT_KNOWN_ENUM_GIT_DIR'];revision='696f4138855502d900a044f034e4b3c2443f176e'
        for owner,expected in [('CdR12bProfile',['TOUCH_DIMENSIONS','ENEMY_POINTS']),('CdR12bPlayerAssets',[])]:
            text='package q; import com.openggf.game.soniccd.level.'+owner+'; class S { Object a='+owner+'.Asset.TOUCH_DIMENSIONS; Object b='+owner+'.Asset.ENEMY_POINTS; }'
            args=self.args(text);args.git_dir=git_dir;args.revision=revision;args.known_type=[]
            args.known_enum=['com.openggf.game.soniccd.level.'+owner+'.Asset=src/main/java/com/openggf/game/soniccd/level/'+owner+'.java']
            r=self.p.run(args);self.assertEqual(expected,[h['member'] for h in r['hints']]);self.assertEqual('observed',r['status'])
            self.assertEqual(revision,r['known_enums'][0]['provenance']['revision'])

    @unittest.skipUnless(os.environ.get('PREFLIGHT_KNOWN_ENUM_GIT_DIR'),'explicit immutable actual enum source metadata not supplied')
    def test_actual_large_consumer_mistake_retains_ambiguous_hints_without_certifying_other_scopes(self):
        git_dir=os.environ['PREFLIGHT_KNOWN_ENUM_GIT_DIR'];revision='696f4138855502d900a044f034e4b3c2443f176e'
        path='src/main/java/com/openggf/game/soniccd/SonicCdAct2PastObjectPass.java'
        original=subprocess.check_output(['git','--git-dir='+git_dir,'show',revision+':'+path]).decode()
        anchor='primaryScanner=new CdPlayerObjectTouch(player.touchDimensions());'
        replacement='primaryScanner=new CdPlayerObjectTouch(CdR11aObjectAssets.decodeTouchDimensions(terrain.view(CdR12bProfile.Asset.TOUCH_DIMENSIONS).copy()));'
        self.assertEqual(1,original.count(anchor));self.assertEqual(1,original.count('CdR12bPlayerAssets.Asset.ENEMY_POINTS'))
        bad=original.replace(anchor,replacement).replace('CdR12bPlayerAssets.Asset.ENEMY_POINTS','CdR12bProfile.Asset.ENEMY_POINTS')
        fixed=bad.replace('terrain.view(CdR12bProfile.Asset.TOUCH_DIMENSIONS)','player.view(CdR12bPlayerAssets.Asset.TOUCH_DIMENSIONS)').replace('CdR12bProfile.Asset.ENEMY_POINTS','CdR12bPlayerAssets.Asset.ENEMY_POINTS')
        for text,owner,expected in [(bad,'CdR12bProfile',['TOUCH_DIMENSIONS','ENEMY_POINTS']),(fixed,'CdR12bPlayerAssets',[])]:
            args=self.args(text);args.git_dir=git_dir;args.revision=revision;args.known_type=[]
            args.known_enum=['com.openggf.game.soniccd.level.'+owner+'.Asset=src/main/java/com/openggf/game/soniccd/level/'+owner+'.java']
            r=self.p.run(args);self.assertEqual(expected,[h['member'] for h in r['hints']]);self.assertEqual('incomplete',r['status'])
            self.assertTrue(all(h['ambiguous'] for h in r['hints']));self.assertTrue(r['incomplete_reasons'])

    def test_split_package_and_import_identifier_tokens_are_incomplete(self):
        for text in ['package q; import p.Kn own; class S { Known k; }',
                     'package p q; class S { Known k; }',
                     'package q; import p. Know n; class S { Known k; }',
                     'package q; import static p.Ou ter.*; class S { Known k; }']:
            with self.subTest(text=text):
                with self.assertRaises(self.p.PreflightError) as caught:self.result(text)
                self.assertEqual(3,caught.exception.code)

    def test_split_selected_type_package_cannot_authenticate_provenance(self):
        (self.repo/'known'/'Split.java').write_text('package p q; public class Known {}\n')
        self.git('add','known/Split.java');self.git('commit','-qm','Owned split-package fixture')
        args=self.args('package q; import pq.Known; class S { Known k; }')
        args.revision=self.git('rev-parse','HEAD').strip();args.known_type=['pq.Known=known/Split.java']
        with self.assertRaises(self.p.PreflightError) as caught:self.p.run(args)
        self.assertEqual(3,caught.exception.code)

    def test_type_header_parenthesis_jump_respects_lowered_context(self):
        with self.assertRaises(self.p.PreflightError) as caught:
            self.result('package q; record S(int a, int b, int c, int d) {}',['--max-context-tokens','10'])
        self.assertEqual(3,caught.exception.code)

    def test_type_header_parenthesis_jump_respects_hard_context(self):
        text='package q; record S('+','.join('int a'+str(i) for i in range(1400))+') {}'
        with self.assertRaises(self.p.PreflightError) as caught:self.result(text)
        self.assertEqual(3,caught.exception.code)

    def test_type_header_context_exact_edge_and_empty_header(self):
        self.assertEqual('observed',self.result('package q; record S() {}',['--max-context-tokens','4'])['status'])
        with self.assertRaises(self.p.PreflightError) as caught:
            self.result('package q; record S() {}',['--max-context-tokens','3'])
        self.assertEqual(3,caught.exception.code)
        with self.assertRaises(self.p.PreflightError) as caught:
            self.result('class S {}',['--max-context-tokens','1'])
        self.assertEqual(3,caught.exception.code)

    def test_header_spacing_comments_and_final_wildcard_keep_valid_tokens(self):
        for header in ['package q; import p /* gap */ . Known;', 'package q; import p . * ;']:
            with self.subTest(header=header):
                r=self.result(header+' class S { Known k; }')
                self.assertEqual([],r['hints']);self.assertEqual('observed',r['status'])

    def test_missing_import_and_explicit_one_import_fix(self):
        before = 'package q; class Source { Known.Region a; Known.Region b; }'
        result = self.result(before)
        self.assertEqual(2, len(result['hints']))
        self.assertTrue(all(h['suggested_import'] == 'p.Known' for h in result['hints']))
        fixed = self.result(before.replace('class Source', 'import p.Known; class Source'))
        self.assertEqual([], fixed['hints'])
        self.assertEqual(['explicit_import', 'explicit_import'], [u['availability'] for u in fixed['uses']])

    def test_exact_git_source_and_provenance(self):
        r = self.result()
        self.assertEqual('git_blob', r['source']['kind'])
        self.assertEqual(self.rev, r['known_types'][0]['provenance']['revision'])
        self.assertEqual('p.Known', r['known_types'][0]['qualified_name'])
        self.assertEqual(1, len(r['hints']))

    def test_same_package(self):
        self.assertEqual('same_package', self.result('package p; class S { Known k; }')['uses'][0]['availability'])

    def test_wildcard_import(self):
        self.assertEqual('package_wildcard_import', self.result('package q; import p.*; class S { Known k; }')['uses'][0]['availability'])

    def test_unrelated_import_does_not_hide_missing(self):
        r = self.result('package q; import r.*; class S { Known k; }')
        self.assertEqual(1, len(r['hints']))
        self.assertEqual('incomplete', r['status'])
        r = self.result('package q; import p.*; import r.*; class S { Known k; }')
        self.assertEqual('incomplete', r['status'])
        self.assertEqual('package_wildcard_import', r['uses'][0]['availability'])

    def test_nested_spelling_is_one_root_and_fqcn_ignored(self):
        r = self.result('package q; class S { Known.Region a; p.Known.Region b; Other.Known c; }')
        self.assertEqual(1, len(r['uses']))
        self.assertEqual(1, len(r['hints']))

    def test_member_type_scope(self):
        r = self.result('package q; class S { class Known {} Known a; } class T { Known b; }')
        self.assertEqual(['file_member_type', 'missing_selected_import'], [u['availability'] for u in r['uses']])
        self.assertEqual(1, len(r['hints']))

    def test_top_level_type_in_same_file(self):
        r = self.result('package q; class Known {} class S { Known a; }')
        self.assertEqual([], r['hints'])
        self.assertEqual('file_top_level_type', r['uses'][0]['availability'])

    def test_local_type_and_value_shadow_are_incomplete(self):
        for text in ['package q; class S { void f() { class Known {} Known a; } }',
                     'package q; class S { Object Known; void f() { Known.toString(); } }',
                     'package q; class S<Known> { Known a; }']:
            with self.subTest(text=text):
                r = self.result(text)
                self.assertEqual('incomplete', r['status'])
                self.assertTrue(r['incomplete_reasons'])

    def test_conflicting_explicit_import_and_static_wildcard_incomplete(self):
        for imports in ['import r.Known;', 'import static r.Outer.*;']:
            r = self.result('package q; ' + imports + ' class S { Known.Region a; }')
            self.assertEqual('incomplete', r['status'])
            self.assertEqual(1, len(r['hints']))

    def test_static_exact_selected_type_import(self):
        r = self.result('package q; import static p.Known; class S { Known k; }')
        # A top-level type cannot be statically imported as a type; do not accept it.
        self.assertEqual('incomplete', r['status'])
        self.assertEqual(1, len(r['hints']))

    def test_comments_literals_and_text_blocks(self):
        r = self.result('package q; /* Known */ class S { String s="Known"; char c=\'K\'; String t="""\nKnown\n"""; // Known\n }')
        self.assertEqual([], r['uses'])

    def test_malformed_and_unsupported_source_is_incomplete(self):
        for text in ['package q; class S {', 'package q; class S { String s="x; }',
                     'package q; /* open', 'package q; class S { \\u004b n; }',
                     'package q; class S { café a; }', 'package q; package r; class S {}',
                     'package q; import .p.Known; class S {}', 'package int; class S {}', '@interface A {}']:
            with self.subTest(text=text):
                with self.assertRaises(self.p.PreflightError) as c:
                    self.result(text)
                self.assertEqual(3, c.exception.code)

    def test_known_type_requires_package_and_declaration_not_filename(self):
        args = self.args()
        args.known_type = ['p.Known=known/Wrong.java']
        with self.assertRaises(self.p.PreflightError):
            self.p.run(args)

    def test_known_type_uses_declaration_even_when_filename_differs(self):
        args = self.args(); args.known_type = ['p.Known=known/Odd.java']
        self.assertEqual(1, len(self.p.run(args)['hints']))

    def test_conflicting_file_type_and_import_is_incomplete(self):
        result = self.result('package q; import p.Known; class Known {} class S { Known a; }')
        self.assertEqual('incomplete', result['status'])

    def test_anonymous_type_is_incomplete(self):
        self.assertEqual('incomplete', self.result('package q; class S { Object a = new Object() { Known x; }; }')['status'])

    def test_selected_simple_name_ambiguity(self):
        args = self.args(); args.known_type.append('r.Known=known/Another.java')
        r = self.p.run(args)
        self.assertEqual('incomplete', r['status'])
        self.assertIsNone(r['hints'][0]['suggested_import'])
        self.assertEqual(['p.Known', 'r.Known'], r['uses'][0]['candidates'])

    def test_cli_exit_codes_and_json(self):
        for text, expected in [('package q; class S { Known k; }', 1),
                               ('package q; import p.Known; class S { Known k; }', 0),
                               ('package q; import static r.X.*; class S { Known k; }', 3)]:
            parsed = self.args(text)
            argv = ['--git-dir', parsed.git_dir, '--revision', parsed.revision,
                    '--known-type', parsed.known_type[0], '--final', parsed.final,
                    '--final-sha256', parsed.final_sha256, '--final-bytes', str(parsed.final_bytes)]
            proc = subprocess.run([sys.executable, '-B', self.p.__file__, *argv],
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
            self.assertEqual(expected, proc.returncode, proc.stderr)
            self.assertEqual(b'', proc.stderr)
            self.assertIsInstance(json.loads(proc.stdout), dict)
        argv[argv.index('--revision') + 1] = 'HEAD'
        proc = subprocess.run([sys.executable, '-B', self.p.__file__, *argv],
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
        self.assertEqual(2, proc.returncode)
        self.assertEqual('error', json.loads(proc.stdout)['status'])

    def test_path_depth_and_invalid_bounds(self):
        with self.assertRaises(self.p.PreflightError):
            self.p.relative('/'.join(['a'] * 64 + ['F.java']))
        with self.assertRaises(self.p.PreflightError):
            self.p.absolute('/' + '/'.join(['a'] * 65))
        with self.assertRaises(self.p.PreflightError) as c:
            self.result(extra=['--max-seconds', '0'])
        self.assertEqual(2, c.exception.code)

    def test_output_bound(self):
        with self.assertRaises(self.p.PreflightError) as c:
            self.result(extra=['--max-output-bytes', '100'])
        self.assertEqual(3, c.exception.code)

    def test_invalid_pin_and_paths(self):
        for key, value in [('revision', 'HEAD'), ('source', '../Source.java'),
                           ('source', '/Source.java'), ('source', 'known/Alias.java')]:
            with self.subTest(key=key, value=value):
                args = self.args(); setattr(args, key, value)
                with self.assertRaises(self.p.PreflightError):
                    self.p.run(args)
        args = self.args('class S {}'); args.final_sha256 = '0' * 64
        with self.assertRaises(self.p.PreflightError):
            self.p.run(args)

    def test_no_follow_final_and_parent_paths(self):
        args = self.args('class S {}')
        link = self.root / 'Link.java'
        link.symlink_to(self.final)
        args.final = str(link)
        with self.assertRaises(self.p.PreflightError):
            self.p.run(args)
        link.unlink()
        parent = self.root / 'alias'
        parent.symlink_to(self.root, target_is_directory=True)
        args.final = str(parent / self.final.name)
        with self.assertRaises(self.p.PreflightError):
            self.p.run(args)
        parent.unlink()

    def test_hardlink_final_rejected(self):
        args = self.args('class S {}')
        alias = self.root / 'hard.java'; os.link(self.final, alias)
        try:
            with self.assertRaises(self.p.PreflightError):
                self.p.run(args)
        finally:
            alias.unlink()

    def test_byte_token_depth_hit_and_total_bounds(self):
        cases = [('class S {}', ['--max-file-bytes', '8']),
                 ('class S {}', ['--max-total-bytes', '10']),
                 ('class S {}', ['--max-tokens', '2']),
                 ('class S extends Object {}', ['--max-context-tokens', '1']),
                 ('class S {{{}}}', ['--max-depth', '1']),
                 ('class S { Known a; Known b; }', ['--max-hits', '1'])]
        for text, extra in cases:
            with self.subTest(extra=extra):
                with self.assertRaises(self.p.PreflightError) as c:
                    self.result(text, extra)
                self.assertEqual(3, c.exception.code)

    def test_deadline_and_git_output_bounds(self):
        args = self.args()
        with patch.object(self.p.time, 'monotonic', side_effect=[0, 2]):
            args.max_seconds = 1
            with self.assertRaises(self.p.PreflightError):
                self.p.run(args)
        engine = self.p.Engine(self.p.Limits())
        with self.assertRaises(self.p.PreflightError):
            engine.git(self.repo / '.git', ['cat-file', 'blob', self.git('rev-parse', self.rev + ':Source.java').strip()], 1)

    def test_reobservation_change_is_incomplete(self):
        args = self.args('class S { Known k; }')
        original = self.p.read_final
        calls = 0
        def changing(*a):
            nonlocal calls
            calls += 1
            if calls == 2:
                self.final.write_bytes(b'class T {}')
            return original(*a)
        with patch.object(self.p, 'read_final', side_effect=changing):
            with self.assertRaises(self.p.PreflightError) as c:
                self.p.run(args)
            self.assertEqual(3, c.exception.code)

    def test_read_only_fixture(self):
        before = {p.relative_to(self.repo): digest(p.read_bytes()) for p in self.repo.rglob('*') if p.is_file() and not p.is_symlink()}
        self.result()
        after = {p.relative_to(self.repo): digest(p.read_bytes()) for p in self.repo.rglob('*') if p.is_file() and not p.is_symlink()}
        self.assertEqual(before, after)

    @unittest.skipUnless(os.environ.get('PREFLIGHT_BEFORE_FINAL'), 'explicit frozen FINAL pins not supplied')
    def test_actual_frozen_source_forward(self):
        git_dir = os.environ['PREFLIGHT_DEPENDENCY_GIT_DIR']
        known = 'com.openggf.game.soniccd.resources.CdR12aCameraArtResidency=src/main/java/com/openggf/game/soniccd/resources/CdR12aCameraArtResidency.java'
        results, inputs = [], []
        for prefix in ('BEFORE', 'FIXED'):
            path = os.environ['PREFLIGHT_' + prefix + '_FINAL']
            data = Path(path).read_bytes()
            inputs.append(data)
            self.assertEqual(os.environ['PREFLIGHT_' + prefix + '_SHA256'], digest(data))
            args = self.p.parser().parse_args(['--git-dir', git_dir, '--revision', REV,
                '--known-type', known, '--final', path, '--final-bytes', str(len(data)),
                '--final-sha256', digest(data)])
            results.append(self.p.run(args))
        inserted = b'import com.openggf.game.soniccd.resources.CdR12aCameraArtResidency;\n'
        anchor = b'import org.junit.jupiter.api.*;'
        self.assertEqual(inputs[0].replace(anchor, inserted + anchor, 1), inputs[1])
        self.assertEqual([46, 49], [h['line'] for h in results[0]['hints']])
        self.assertEqual([], results[1]['hints'])
        self.assertEqual(2, len(results[1]['uses']))
        for prefix, result in zip(('BEFORE', 'FIXED'), results):
            print(prefix + '_RESULT_SHA256=' + digest(self.p.render(result)))


if __name__ == '__main__':
    unittest.main()
