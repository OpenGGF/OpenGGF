"""Bounded API delta controls; fixtures only, no Java/Maven/network."""
from pathlib import Path
import importlib.util
import sys
import contextlib
import io
import json
import os
import stat
import subprocess
import tempfile
from unittest.mock import patch
import unittest

HERE = Path(__file__).absolute().parent


class MotivatingRed(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('consumer_audit', HERE / 'java_api_consumer_audit.py')
        cls.a = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = cls.a
        spec.loader.exec_module(cls.a)

    def test_removed_package_private_original_image_keeps_unresolved_var_candidates(self):
        r = self.a.observe(b'package p; class Assets { Image originalImage(){return null;} }',
            b'package p; class Assets {}',
            b'package p; class T { void t(){ var a=Fixture.actual().assets(); a.originalImage(); } }',
            'p.Assets')
        self.assertEqual(['originalImage'], [x['name'] for x in r['references']])
        self.assertEqual('unresolved_receiver', r['references'][0]['classification'])
        self.assertTrue(r['incomplete_reasons'])

    def test_r13_declared_receiver_is_foreign_not_an_r31_bug(self):
        r = self.a.observe(b'package p; class Assets { Image originalImage(){return null;} }',
            b'package p; class Assets {}',
            b'package p; import q.R13; class T { void t(R13 a){ a.originalImage(); } }', 'p.Assets')
        self.assertEqual('known_foreign_owner', r['references'][0]['classification'])

class ConsumerControls(MotivatingRed):
    before = b'package p; class Assets { Image originalImage(){return null;} void stay(){} }'
    after = b'package p; class Assets { void stay(){} }'

    def observation(self, body, before=None, after=None, missing=(), limits=None):
        data = body.encode() if isinstance(body,str) else body
        return self.a.observe(before or self.before, after or self.after, data, 'p.Assets', limits, missing)

    def test_direct_chained_unknown_var_and_constructor_all_enumerated(self):
        r = self.observation('''package p; class T {
          void test(Assets direct) {
            direct.originalImage(); Fixture.actual().assets().originalImage();
            var unresolved = Fixture.actual().assets(); unresolved.originalImage();
            unknown.originalImage(); new Assets().originalImage();
            var known = new Assets(); known.originalImage();
          }
        }''')
        self.assertEqual(6, len(r['references']))
        self.assertEqual(['advisory_hit','unresolved_receiver','unresolved_receiver',
                          'unresolved_receiver','advisory_hit','advisory_hit'],
                         [r['classification'] for r in r['references']])
        self.assertEqual('Fixture . actual ( ) . assets ( )', r['references'][1]['receiver']['syntax'])
        self.assertIsNone(r['references'][2]['receiver']['owner'])
        self.assertEqual('explicit_new_constructor', r['references'][-1]['receiver']['evidence'])

    def test_method_references_never_guess_arity(self):
        r = self.observation('package p; class T { void t(Assets a){ use(a::originalImage); use(Assets::originalImage); } }')
        self.assertEqual(2,len(r['references']))
        self.assertTrue(all(x['arity'] is None and x['kind'] == 'method_reference' for x in r['references']))
        self.assertTrue(r['incomplete_reasons'])

    def test_geometry_known_owner_missing_hint_does_not_claim_inherited_certainty(self):
        owner = b'package p; class Assets { void ensurePatternCapacity() {} }'
        r = self.observation('package p; class T { void t(){ var geometry = new Assets(); use(geometry::getPatterns); } }',
                             before=owner,after=owner,missing=('getPatterns',))
        self.assertEqual('p.Assets',r['references'][0]['receiver']['owner'])
        self.assertTrue(r['references'][0]['requested_missing_member'])
        self.assertEqual([],r['references'][0]['after_written_candidates'])
        self.assertTrue(any('inherited' in v for v in r['incomplete_reasons']))

    def test_overload_signature_and_arity_changes_are_not_binding_claims(self):
        before = b'package p; class Assets { void m(){} void m(int x){} void m(String x){} }'
        after = b'package p; class Assets { void m(long x){} void m(int x,int y){} }'
        r = self.observation('package p; class T { void t(Assets a){ a.m(); a.m(1); a.m(1,2); a.m(1,2,3); use(a::m); } }',before,after)
        self.assertEqual([0,1,2,3,None],[x['arity'] for x in r['references']])
        self.assertEqual([1,2,0,0,3],[len(x['delta_candidates']) for x in r['references']])
        self.assertEqual('changed_written_signature',r['delta'][1]['kind'])
        self.assertEqual('other_written_arity',r['references'][2]['classification'])

    def test_comments_strings_characters_and_text_blocks_are_not_calls(self):
        r = self.observation('''package p; class T { void t(Assets a){
          // a.originalImage();
          /* a.originalImage(); */
          String s="a.originalImage()"; char ch='x';
          String block="""
          a.originalImage();
          """;
          a.originalImage();
        } }''')
        self.assertEqual(1,len(r['references']))
        self.assertEqual(8,r['references'][0]['line'])

    def test_nested_blocks_and_member_types_do_not_leak_shadows(self):
        r = self.observation('''package p; import q.R13; class T {
          Assets a;
          void t(){ a.originalImage(); { R13 a = null; a.originalImage(); } a.originalImage(); }
          void u(R13 a){ a.originalImage(); }
          void v(){ a.originalImage(); }
          class N { R13 a; void n(){ a.originalImage(); } }
        }''')
        self.assertEqual(['p.Assets','q.R13','p.Assets','q.R13','p.Assets','q.R13'],
                         [x['receiver']['owner'] for x in r['references']])

    def test_scope_before_local_declaration_is_field_not_future_local(self):
        r = self.observation('package p; import q.R13; class T { Assets a; void t(){ a.originalImage(); R13 a=null; a.originalImage(); } }')
        self.assertEqual(['p.Assets','q.R13'],[x['receiver']['owner'] for x in r['references']])

    def test_lambda_for_catch_array_generic_shadows_block_outer_provenance(self):
        for text in (
            'use(a -> a.originalImage());',
            'for(R13 a : values){ a.originalImage(); }',
            'try {} catch(R13 a){ a.originalImage(); }',
            'Box<R13> a=null; a.originalImage();',
            'R13[] a=null; a.originalImage();',
        ):
            with self.subTest(text=text):
                r = self.observation('package p; import q.R13; class T { Assets a; void t(){ '+text+' } }')
                self.assertTrue(r['references'])
                self.assertIsNone(r['references'][0]['receiver']['owner'])
                self.assertTrue(r['incomplete_reasons'])

    def test_header_shadows_stop_at_body_or_lambda_end(self):
        for text in ('use(a -> a.originalImage());',
                     'for(R13 a : values){ a.originalImage(); }',
                     'try {} catch(R13 a){ a.originalImage(); }',
                     'use((a,b) -> a.originalImage());'):
            with self.subTest(text=text):
                r = self.observation('package p; class T { Assets a; void t(){ '+text+' a.originalImage(); } }')
                self.assertIsNone(r['references'][0]['receiver']['owner'])
                self.assertEqual('p.Assets',r['references'][-1]['receiver']['owner'])

    def test_generic_method_and_pattern_and_multi_declaration_do_not_claim_receiver(self):
        for text in ('package p; class T { <Assets> void t(){ Assets.originalImage(); } }',
                     'package p; class T { Assets a; void t(){ R13 x=null,a=null; a.originalImage(); } }',
                     'package p; class T { Assets a; void t(){ if(x instanceof R13 a){ a.originalImage(); } } }'):
            with self.subTest(text=text):
                r = self.observation(text)
                self.assertIsNone(r['references'][0]['receiver']['owner'])
                self.assertTrue(r['incomplete_reasons'])

    def test_inherited_anonymous_local_and_external_scopes_remain_uncertain(self):
        for declaration in ('class T extends External { void t(){ a.originalImage(); } }',
                            'class T { void t(){ class Local { void n(){ a.originalImage(); } } } }',
                            'class T { void t(){ use(new External(){ void n(){ a.originalImage(); } }); } }'):
            with self.subTest(declaration=declaration):
                r = self.observation('package p; '+declaration)
                self.assertTrue(r['incomplete_reasons'])
                self.assertEqual('unresolved_receiver',r['references'][0]['classification'])

    def test_foreign_fully_qualified_and_declared_import_receivers(self):
        r = self.observation('package p; import q.R13; class T { void t(R13 x){ x.originalImage(); q.R13.originalImage(); } }')
        self.assertTrue(all(x['classification']=='known_foreign_owner' for x in r['references']))

    def test_unimported_external_java_lang_and_unknown_declared_types_are_not_guessed(self):
        r = self.observation('package p; class T { void t(External x, Object y){ x.originalImage(); y.originalImage(); } }')
        self.assertTrue(all(x['receiver']['owner'] is None for x in r['references']))
        self.assertTrue(r['incomplete_reasons'])

    def test_redacted_header_literals_do_not_certify_signature_equality(self):
        r = self.observation('package p; class T { void t(Assets a){ a.originalImage(); } }',
            b'package p; class Assets { @A("old") Image originalImage(){return null;} }',
            b'package p; class Assets { @A("new") Image originalImage(){return null;} }')
        self.assertTrue(any('Redacted' in v for v in r['incomplete_reasons']))

    def test_wildcard_type_binding_unknown_and_qualified_field_unresolved(self):
        r = self.observation('package q; import p.*; class T { void t(Assets a){ a.originalImage(); this.a.originalImage(); } }')
        self.assertTrue(all(x['classification']=='unresolved_receiver' for x in r['references']))

    def test_static_import_implicit_and_generic_reference_are_not_omitted(self):
        r = self.observation('package p; import static p.Assets.originalImage; class T { void t(){ originalImage(); use(Assets::<String>originalImage); } }')
        self.assertEqual(3,len(r['references']))
        self.assertTrue(r['incomplete_reasons'])

    def test_argument_slots_generic_lambda_comparison_uncertain(self):
        r = self.observation('package p; class T { void t(Assets a){ a.originalImage(f(1,2),new int[]{1,2}); a.originalImage(new X<A,B>()); } }')
        self.assertEqual([2,None],[x['arity'] for x in r['references']])

    def test_malformed_headers_delimiters_parameters_and_context_are_incomplete(self):
        p,c,l = self.a.dependencies()
        for text in ('package p; class T { void t(){ a.originalImage( } }',
                     'package p; class T { void t(Assets a,){} }',
                     'package p; import p Assets; class T {}'):
            with self.subTest(text=text):
                try:
                    r = self.observation(text)
                except (self.a.AuditError,p.PreflightError,c.CensusError) as e:
                    self.assertEqual(3,e.code)
                else:
                    self.assertTrue(r['incomplete_reasons'])
        with self.assertRaises((self.a.AuditError,p.PreflightError,c.CensusError)):
            self.observation('package p; class T { void t(Assets a){ a.originalImage(1,2,3,4,5); } }',
                             limits=self.a.Limits(max_context_tokens=6))

    def test_lowerable_token_depth_and_hit_bounds(self):
        p,c,l = self.a.dependencies()
        for limits in (self.a.Limits(max_tokens=5),self.a.Limits(max_depth=1),self.a.Limits(max_hits=1)):
            with self.subTest(limits=limits), self.assertRaises((self.a.AuditError,p.PreflightError,c.CensusError)):
                self.observation('package p; class T { void t(Assets a){ a.originalImage(); a.originalImage(); } }',limits=limits)


class CustodyControls(MotivatingRed):
    def setUp(self):
        self.owned = tempfile.TemporaryDirectory(prefix='api-consumer-owned-')
        self.addCleanup(self.owned.cleanup)
        self.root = Path(self.owned.name)
        self.repo = self.root/'repo'
        self.repo.mkdir()
        self.git('init','-q')
        self.git('config','user.name','Owned Fixture')
        self.git('config','user.email','fixture@example.invalid')
        (self.repo/'Assets.java').write_text('package p; class Assets { Image originalImage(){return null;} }\n')
        (self.repo/'T.java').write_text('package p; class T { void t(Assets a){ a.originalImage(); } }\n')
        (self.repo/'Alias.java').symlink_to('Assets.java')
        self.git('add','.')
        self.git('commit','-qm','Owned fixture')
        self.rev = self.git('rev-parse','HEAD').strip()
        self.after = self.root/'After.java'
        self.after.write_text('package p; class Assets {}\n')
        self.after.chmod(0o444)
        self.caller = self.root/'Caller.java'
        self.caller.write_text('package p; class T { void t(Assets a){ a.originalImage(); } }\n')
        self.caller.chmod(0o444)

    def git(self,*args):
        env = {k:v for k,v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL=os.devnull)
        return subprocess.check_output(['git','-C',str(self.repo),*args],env=env,text=True)

    def flags(self,extra=()):
        data = self.after.read_bytes()
        return ['--git-dir',str(self.repo/'.git'),'--revision',self.rev,'--owner','p.Assets',
                '--source','Assets.java','--after',str(self.after),'--after-sha256',self.a.sha(data),
                '--after-bytes',str(len(data)),'--after-mode','0444','--caller','T.java',*extra]

    def result(self,extra=()):
        return self.a.run(self.a.parser().parse_args(self.flags(extra)))

    def main(self,extra=(),flags=None):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = self.a.main(flags or self.flags(extra))
        return code,out.getvalue()

    def test_review_exit_json_provenance_modes_lines_and_human(self):
        r = self.result()
        self.assertEqual('observed',r['status'])
        self.assertEqual(1,self.a.exit_code(r))
        self.assertEqual('100644',r['before']['provenance']['mode'])
        self.assertEqual('0444',r['after']['provenance']['mode'])
        self.assertEqual('T.java',r['advisory_hits'][0]['path'])
        self.assertEqual(1,r['advisory_hits'][0]['line'])
        code,out = self.main(('--json',))
        self.assertEqual(1,code)
        self.assertEqual(self.rev,json.loads(out)['before']['provenance']['revision'])
        self.assertIn('Zero candidates',self.main()[1])

    def test_ready_and_incomplete_exits_remain_source_observations(self):
        self.caller.chmod(0o644)
        self.caller.write_text('package p; import q.R13; class T { void t(R13 a){ a.originalImage(); } }')
        pin = self.caller.read_bytes()
        flags = self.flags()
        flags = flags[:-2]+['--caller-final',str(self.caller),self.a.sha(pin),str(len(pin)),'0644','--json']
        code,out = self.main(flags=flags)
        self.assertEqual(0,code)
        self.assertEqual(1,len(json.loads(out)['known_foreign_references']))
        self.caller.write_text('package p; class T { void t(){ var a=factory(); a.originalImage(); } }')
        pin = self.caller.read_bytes()
        flags[-5:-1] = [str(self.caller),self.a.sha(pin),str(len(pin)),'0644']
        code,out = self.main(flags=flags)
        self.assertEqual(3,code)
        self.assertEqual(1,len(json.loads(out)['advisory_hits']))

    def test_bad_pins_mode_revision_owner_and_duplicate_paths_reject(self):
        for extra in (('--after-sha256','0'*64),('--after-mode','0644'),('--revision','HEAD'),
                      ('--source','Alias.java'),('--owner','p.Wrong'),('--caller','T.java'),
                      ('--source','../Assets.java'),('--git-dir',str(self.repo)+ '/./.git')):
            with self.subTest(extra=extra):
                self.assertEqual(2,self.main(extra)[0])

    def test_annotated_local_shadows_remain_incomplete_and_end_with_their_block(self):
        for annotation in ('@Deprecated', '@SuppressWarnings("unused")', 'final @Deprecated'):
            with self.subTest(annotation=annotation):
                self.caller.chmod(0o644)
                self.caller.write_text('package p; import q.R13; class Caller { R13 a; '
                    'void check(){ { '+annotation+' Assets a=new Assets(); a.originalImage(); } '
                    'a.originalImage(); } }')
                data=self.caller.read_bytes()
                flags=self.flags()[:-2]+['--caller-final',str(self.caller),self.a.sha(data),str(len(data)),'0644','--json']
                code,out=self.main(flags=flags); result=json.loads(out)
                self.assertEqual(3,code)
                self.assertEqual(1,len(result['advisory_hits']))
                self.assertIsNone(result['callers'][0]['references'][0]['receiver']['owner'])
                self.assertEqual('unresolved_receiver',result['advisory_hits'][0]['classification'])
                self.assertEqual(1,len(result['known_foreign_references']))
                self.assertEqual('q.R13',result['callers'][0]['references'][1]['receiver']['owner'])

    def test_member_types_shadow_imports_in_their_owner_but_not_sibling_classes(self):
        for name in ('R13','Assets'):
            for declaration in ('void check('+name+' a){', 'void check(){ var a=new '+name+'();'):
                with self.subTest(name=name,declaration=declaration):
                    self.caller.chmod(0o644)
                    self.caller.write_text('package p; import q.R13; class Caller { '
                        'static class '+name+' { Object originalImage(){return null;} } '
                        +declaration+' a.originalImage(); } } '
                        'class Other { void check(R13 a){a.originalImage();} }')
                    data=self.caller.read_bytes()
                    flags=self.flags()[:-2]+['--caller-final',str(self.caller),self.a.sha(data),str(len(data)),'0644','--json']
                    code,out=self.main(flags=flags); result=json.loads(out)
                    self.assertEqual(3,code)
                    self.assertEqual(1,len(result['advisory_hits']))
                    self.assertIsNone(result['callers'][0]['references'][0]['receiver']['owner'])
                    self.assertEqual(1,len(result['known_foreign_references']))
                    self.assertEqual('q.R13',result['callers'][0]['references'][1]['receiver']['owner'])

    def test_symlink_parent_final_and_hardlink_alias_reject(self):
        link = self.root/'Link.java'
        link.symlink_to(self.after)
        self.assertEqual(2,self.main(('--after',str(link)))[0])
        parent = self.root/'alias'
        parent.symlink_to(self.root,target_is_directory=True)
        self.assertEqual(2,self.main(('--after',str(parent/'After.java')))[0])
        link.unlink()
        os.link(self.after,link)
        self.assertEqual(2,self.main()[0])

    def test_fixed_git_argv_and_hostile_literal_path_never_run_supplied_text(self):
        p,c,l = self.a.dependencies()
        engine = p.Engine(self.a.Limits())
        with patch.object(p.subprocess,'Popen',side_effect=AssertionError('must reject before spawn')):
            for argv in (['show','HEAD:Assets.java'],['cat-file','--filters',self.rev],['cat-file','blob','HEAD'],['ls-tree','-z',self.rev,'--','../T.java']):
                with self.assertRaises(p.PreflightError):
                    engine.git(self.repo/'.git',argv,1024)
        hostile = '$(touch NEVER_EXECUTED)`echo nope`.java'
        (self.repo/hostile).write_bytes((self.repo/'T.java').read_bytes())
        self.git('add','--',hostile)
        self.git('commit','-qm','Literal path')
        self.rev = self.git('rev-parse','HEAD').strip()
        self.assertEqual(1,self.main(('--caller',hostile))[0])
        self.assertFalse((self.repo/'NEVER_EXECUTED').exists())
        # A filter attribute exists only in owned fixtures. Blob reads must not invoke it.
        (self.repo/'.gitattributes').write_text('*.java filter=never\n')
        self.git('config','filter.never.smudge','touch FILTER_EXECUTED')
        self.assertEqual(1,self.main()[0])
        self.assertFalse((self.repo/'FILTER_EXECUTED').exists())

    def test_final_drift_even_after_incomplete_grammar(self):
        p,c,l = self.a.dependencies()
        original = c.read_final
        calls = 0
        def drifting(path,cap):
            nonlocal calls
            calls += 1
            if calls == 2:
                self.after.chmod(0o644)
            return original(path,cap)
        with patch.object(c,'read_final',side_effect=drifting):
            code,out = self.main(('--json',))
        self.assertEqual(3,code)
        self.assertIn('drift',out)

    def test_git_head_and_blob_and_directory_drift_are_incomplete(self):
        p,c,l = self.a.dependencies()
        original = c.read_regular
        def head_drift(path,cap,*args,**kwargs):
            value = original(path,cap,*args,**kwargs)
            if Path(path).name == 'HEAD':
                head_drift.n += 1
                if head_drift.n > 1:
                    return b'different HEAD',value[1]
            return value
        head_drift.n = 0
        with patch.object(c,'read_regular',side_effect=head_drift):
            self.assertEqual(3,self.main()[0])
        original_blob = p.Engine.blob
        def blob_drift(engine,*args,**kwargs):
            data,pin = original_blob(engine,*args,**kwargs)
            if kwargs.get('account') is False:
                data += b'\n'
            return data,pin
        with patch.object(p.Engine,'blob',new=blob_drift):
            self.assertEqual(3,self.main()[0])
        identity = c.directory_id(self.repo/'.git')
        with patch.object(c,'directory_id',side_effect=[identity,(0,0,0)]):
            self.assertEqual(3,self.main()[0])

    def test_context_and_output_exhaustion_never_return_clean_empty(self):
        code,out = self.main(('--max-context-tokens','4','--json'))
        self.assertEqual(3,code)
        self.assertTrue(json.loads(out)['omissions'])
        code,out = self.main(('--max-output-bytes','512','--json'))
        self.assertEqual(3,code)
        self.assertLessEqual(len(out.encode()),512)
        self.assertTrue(json.loads(out)['omissions'])
        code,out = self.main(('--max-hits','1','--caller-final',str(self.caller),self.a.sha(self.caller.read_bytes()),str(self.caller.stat().st_size),'0444','--json'))
        self.assertEqual(3,code)
        self.assertTrue(json.loads(out)['omissions'])

    def test_invalid_output_bound_and_invalid_pins_do_not_become_incomplete(self):
        self.assertEqual(2,self.main(('--max-output-bytes','0','--json'))[0])
        code,out = self.main(('--max-output-bytes','512','--after-sha256','0'*64,'--json'))
        self.assertEqual(2,code)
        self.assertEqual('error',json.loads(out)['status'])
        self.assertLessEqual(len(out.encode()),512)

    def test_helper_pin_rejected_before_execution(self):
        saved = self.a._DEPS
        try:
            self.a._DEPS = None
            with patch.object(self.a,'PIN_SHA256','0'*64):
                self.assertEqual(2,self.main()[0])
        finally:
            self.a._DEPS = saved


if __name__ == '__main__':
    unittest.main()
