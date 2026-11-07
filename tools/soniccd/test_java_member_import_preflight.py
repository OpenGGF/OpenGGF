#!/usr/bin/env python3
"""Standard-library synthetic controls. No Git writes, Java, discovery or saved results.
Optional exact legacy compatibility loads only the pinned immutable Python baseline.
"""
from pathlib import Path
import contextlib
import hashlib
import importlib.util
import io
import json
import os
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

REV='4addcd1e87ce11604d228db287dd200078d7951d'
BASE_SHA='7026d31d76577e757f1ae0eef3d8cbb508aa1a7b6ebc51138608951976e9a47d'
OWNER='''package p; public class Outer {
 public class OrdinaryAccess {} public class BlockRead {} public class Store {}
 public class PlaceBlock {} public class Fm {} public class SubCpu {}
 public static class StaticType {} public record Cut(int value) {}
 public enum Kind { A } public interface Port {}
}'''

class MemberImportTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.here=Path(__file__).absolute().parent
  spec=importlib.util.spec_from_file_location('_member_import_under_test',cls.here/'java_project_import_preflight.py')
  cls.p=importlib.util.module_from_spec(spec);sys.modules[spec.name]=cls.p;spec.loader.exec_module(cls.p)
 @classmethod
 def tearDownClass(cls):
  sys.modules.pop('_member_import_under_test',None)
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory(prefix='member-import-owned-',dir=os.environ.get('PREFLIGHT_TEST_TMPDIR'))
  self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.meta=self.root/'metadata';self.meta.mkdir()
  self.final=self.root/'Consumer.java';self.owner=self.root/'Owner.java';self.owner.write_text(OWNER)
  self.sources={'Outer.java':OWNER.encode(),'Top.java':b'package p; public class Top {}'}
 def args(self,text,extra=(),module=None):
  p=module or self.p;data=text.encode();self.final.write_bytes(data)
  return p.parser().parse_args(['--git-dir',str(self.meta),'--revision',REV,'--final',str(self.final),
   '--final-bytes',str(len(data)),'--final-sha256',hashlib.sha256(data).hexdigest(),*extra])
 def execute(self,args,module=None,change=None):
  p=module or self.p
  def blob(engine,git_dir,revision,path,account=True):
   data=self.sources[path]
   if account:engine.account(len(data))
   return data,dict(kind='synthetic_immutable_blob',path=path,revision=revision,bytes=len(data),sha256=p.sha(data))
  def git(engine,git_dir,argv,cap):
   self.assertEqual(['cat-file','-t',REV],argv);return b'commit\n'
  with patch.object(p.Engine,'git',git),patch.object(p.Engine,'blob',blob):
   if change:return change(p,args)
   return p.run(args)
 def result(self,text,extra=(),owner=OWNER):
  self.sources['Outer.java']=owner.encode()
  return self.execute(self.args(text,['--known-member-owner','p.Outer=Outer.java',*extra]))
 def test_real_blocker_names_red_then_ordinary_named_imports_green(self):
  names=['OrdinaryAccess','BlockRead','Store','PlaceBlock','Fm','SubCpu']
  body='class Test { '+' '.join(n+' field'+str(i)+';' for i,n in enumerate(names))+' }'
  bad=self.result('package q; import static p.Outer.*; '+body)
  self.assertEqual(names,[h['name'] for h in bad['hints']]);self.assertEqual('observed',bad['status'])
  self.assertTrue(all(not h['ambiguous'] for h in bad['hints']))
  good=self.result('package q; import static p.Outer.*; '+''.join('import p.Outer.'+n+';' for n in names)+body)
  self.assertEqual([],good['hints']);self.assertEqual('observed',good['status'])
  self.assertTrue(all(u['availability']=='ordinary_named_import' for u in good['member_uses']))
 def test_qualified_inner_construction_is_receiver_selection_not_import_failure(self):
  for expression in ['outer.new BlockRead()', 'outer.new <String> BlockRead()']:
   with self.subTest(expression=expression):
    r=self.result('package q; import static p.Outer.*; class T { Object make(p.Outer outer){ return '+expression+'; } }')
    self.assertFalse(r['hints']);self.assertEqual('incomplete',r['status'])
    self.assertEqual('unresolved_qualified_construction_receiver',r['member_uses'][0]['availability'])
 def test_enum_constant_specific_bodies_are_incomplete_member_scopes(self):
  for constant in ['A','A()']:
   with self.subTest(constant=constant):
    r=self.result('package q; import static p.Outer.*; enum T { '+constant+' { BlockRead field; }; }')
    self.assertEqual('incomplete',r['status']);self.assertTrue(r['hints'][0]['ambiguous'])
    self.assertTrue(any('Enum constant anonymous' in reason for reason in r['incomplete_reasons']))
 def test_selected_owner_enum_constant_body_also_keeps_incomplete(self):
  r=self.result('package q; import static p.Outer.*; class T { BlockRead field; }',
   owner='package p; enum Outer { A { void unused() {} }; class BlockRead {} }')
  self.assertEqual('incomplete',r['status']);self.assertTrue(r['hints'][0]['ambiguous'])
  self.assertTrue(any('Enum constant anonymous' in reason for reason in r['known_member_owners'][0]['incomplete_reasons']))
 def test_enum_constants_without_bodies_do_not_add_scope_uncertainty(self):
  r=self.result('package q; import static p.Outer.*; enum T { A, B(1); T() {} T(int n) {} BlockRead field; }')
  self.assertEqual('observed',r['status']);self.assertFalse(r['hints'][0]['ambiguous'])
 def test_enum_member_scope_detection_obeys_context_budget(self):
  text='package q; import static p.Outer.*; enum T { A('+','.join('1' for _ in range(80))+') { BlockRead field; }; }'
  with self.assertRaises(self.p.PreflightError):self.result(text,['--max-context-tokens','40'])
 def test_records_enums_interfaces_and_direct_static_classes_are_static(self):
  r=self.result('package q; import static p.Outer.*; class T { StaticType a; Cut b; Kind c; Port d; }')
  self.assertFalse(r['hints']);self.assertEqual('observed',r['status'])
  self.assertEqual(['explicit_static_class','implicit_static_record','implicit_static_enum','implicit_static_interface'],[u['static_kind'] for u in r['member_uses']])
 def test_staticness_modifiers_never_come_from_comments_or_literals(self):
  source='package p; class Outer { /* static */ class BlockRead {} String s="static class Store {}"; }'
  r=self.result('package q; import static p.Outer.*; class T { BlockRead x; }',owner=source)
  self.assertEqual('nonstatic_member_class',r['member_uses'][0]['static_kind']);self.assertEqual(1,len(r['hints']))
 def test_class_declared_in_interface_is_implicitly_static(self):
  r=self.result('package q; import static p.Outer.*; class T { BlockRead x; }',owner='package p; public interface Outer { class BlockRead {} }')
  self.assertFalse(r['hints']);self.assertEqual('implicit_static_interface_member',r['member_uses'][0]['static_kind'])
 def test_ordinary_owner_wildcard_imports_nonstatic_members_but_package_wildcard_does_not(self):
  good=self.result('package q; import static p.Outer.*; import p.Outer.*; class T { BlockRead x; }')
  self.assertFalse(good['hints']);self.assertEqual('ordinary_owner_on_demand_import',good['member_uses'][0]['availability'])
  bad=self.result('package p; import static p.Outer.*; class T { BlockRead x; }')
  self.assertEqual(1,len(bad['hints']))
  uncertain=self.result('package q; import static p.Outer.*; import p.*; class T { BlockRead x; }')
  self.assertEqual('incomplete',uncertain['status']);self.assertTrue(uncertain['hints'][0]['ambiguous'])
 def test_actual_same_enclosing_type_is_available_without_importing_its_member(self):
  text='package p; import static p.Outer.*; public class Outer { public class BlockRead {} BlockRead x; }'
  r=self.result(text,owner=text);self.assertFalse(r['hints']);self.assertEqual('enclosing_type',r['member_uses'][0]['availability'])
 def test_explicit_owner_qualification_removes_the_member_import_hint(self):
  r=self.result('package q; import static p.Outer.*; import p.Outer; class T { Outer.BlockRead a; p.Outer.Store b; }')
  self.assertFalse(r['hints']);self.assertEqual(2,len(r['member_uses']))
  self.assertTrue(all(u['availability']=='qualified_member_spelling' for u in r['member_uses']))
 def test_inheritance_anonymous_and_generic_scopes_remain_incomplete_advisories(self):
  for body in ['class T extends Base { BlockRead x; }','class T<X> { BlockRead x; }',
               'class T { Object x=new Base(){ BlockRead y; }; }']:
   with self.subTest(body=body):
    r=self.result('package q; import static p.Outer.*; '+body)
    self.assertEqual('incomplete',r['status']);self.assertTrue(r['hints'][0]['ambiguous'])
 def test_owner_inheritance_and_unsupported_member_modifier_never_report_false_clean(self):
  for owner in ['package p; class Outer extends Base { class BlockRead {} }',
                'package p; class Outer { @Tag class BlockRead {} }']:
   r=self.result('package q; import static p.Outer.*; class T { BlockRead x; }',owner=owner)
   self.assertEqual('incomplete',r['status']);self.assertTrue(r['incomplete_reasons'])
 def test_value_method_and_generic_parameter_clues_are_incomplete(self):
  for body in ['class T { Object BlockRead; Object x=BlockRead; }',
               'class T { void f(){ BlockRead(); } }','class T { <BlockRead> void f(BlockRead x){} }']:
   r=self.result('package q; import static p.Outer.*; '+body)
   self.assertEqual('incomplete',r['status']);self.assertTrue(all(h['ambiguous'] for h in r['hints']))
 def test_competing_named_static_and_other_wildcard_imports_are_not_certified(self):
  for extra in ['import r.BlockRead;','import static r.Other.*;','import r.Other.*;', 'import static p.Outer.BlockRead;']:
   r=self.result('package q; import static p.Outer.*; '+extra+' class T { BlockRead x; }')
   self.assertEqual('incomplete',r['status']);self.assertTrue(r['incomplete_reasons'])
 def test_comments_strings_chars_textblocks_and_unselected_names_are_redacted(self):
  r=self.result('''package q; import static p.Outer.*; class T {
   // BlockRead x;
   String s="Store x;"; char c='x'; String t="""\nPlaceBlock y;\n""";
   Other z; /* Fm x; */
  }''')
  self.assertEqual([],r['member_uses']);self.assertEqual([],r['hints'])
 def test_unselected_owner_and_indirect_qualified_receivers_do_not_mint_binding(self):
  r=self.result('package q; import static r.Other.*; class T { BlockRead x; }')
  self.assertEqual([],r['member_uses'])
  r=self.result('package q; import static p.Outer.*; class T { Object x=receiver.BlockRead; }')
  self.assertEqual('incomplete',r['status']);self.assertFalse(r['hints'])
 def test_nested_known_owner_and_duplicate_member_names_remain_explicit(self):
  source='package p; class Outer { class Nest { class BlockRead {} } }';self.sources['Outer.java']=source.encode()
  r=self.execute(self.args('package q; import static p.Outer.Nest.*; class T { BlockRead x; }',['--known-member-owner','p.Outer.Nest=Outer.java']))
  self.assertEqual('p.Outer.Nest.BlockRead',r['hints'][0]['suggested_import'])
  self.sources['Outer.java']=OWNER.encode()
  self.sources['Other.java']=b'package r; class Other { class BlockRead {} }'
  r=self.execute(self.args('package q; import static p.Outer.*; import static r.Other.*; class T { BlockRead x; }',
   ['--known-member-owner','p.Outer=Outer.java','--known-member-owner','r.Other=Other.java']))
  self.assertEqual('incomplete',r['status']);self.assertEqual(2,len(r['member_uses'][0]['candidates']))
 def test_final_owner_pins_reuse_safe_reads_and_cache_same_source(self):
  data=self.owner.read_bytes();sel='p.Outer='+str(len(data))+'='+hashlib.sha256(data).hexdigest()+'='+str(self.owner)
  r=self.execute(self.args('package q; import static p.Outer.*; class T { BlockRead x; }',['--known-member-owner-final',sel]))
  self.assertEqual(1,len(r['hints']));self.assertEqual('external_final',r['known_member_owners'][0]['provenance']['kind'])
  self.assertEqual('p.Outer',r['known_member_owners'][0]['qualified_name'])
 def test_same_final_owner_consumer_is_accounted_and_lexed_once(self):
  text='package p; import static p.Outer.*; class Outer { class BlockRead {} BlockRead x; }'
  self.owner.write_text(text);data=self.owner.read_bytes()
  selection='p.Outer='+str(len(data))+'='+hashlib.sha256(data).hexdigest()+'='+str(self.owner)
  args=self.args(text,['--known-member-owner-final',selection]);args.final=str(self.owner)
  r=self.execute(args);self.assertEqual(len(data),r['selected_bytes'])
  self.assertEqual(len(self.p.Engine(self.p.Limits()).parse(data).ts),r['selected_tokens'])
  self.assertFalse(r['hints'])
 def test_imported_nested_owner_qualification_and_package_nonavailability(self):
  self.sources['Outer.java']=b'package p; class Outer { class Nest { class BlockRead {} } }'
  for header,expected in [('package q; import p.Outer.Nest;', 'observed'),('package p;', 'incomplete')]:
   r=self.execute(self.args(header+' import static p.Outer.Nest.*; class T { Nest.BlockRead x; }',
    ['--known-member-owner','p.Outer.Nest=Outer.java']))
   self.assertFalse(r['hints']);self.assertEqual(expected,r['status'])
 def test_new_owner_selections_are_distinct_and_share_byte_output_and_hit_bounds(self):
  text='package q; import static p.Outer.*; class T { BlockRead a; BlockRead b; }'
  small='package p; class Outer { class BlockRead {} }'
  for extra in [['--max-hits','1'],['--max-total-bytes','10'],['--max-output-bytes','10']]:
   with self.assertRaises(self.p.PreflightError):self.result(text,extra,owner=small)
  args=self.args(text,['--known-member-owner','p.Outer=Outer.java','--known-member-owner','p.Outer=Outer.java'])
  with self.assertRaises(self.p.PreflightError) as c:self.execute(args)
  self.assertEqual(2,c.exception.code)
 def test_duplicate_owner_or_consumer_declarations_never_produce_false_clean_binding(self):
  for source in ['package p; class Outer { static class BlockRead {} static class BlockRead {} }',
                 'package p; class Outer { class BlockRead {} class BlockRead {} }']:
   r=self.result('package q; import static p.Outer.*; class T { BlockRead x; }',owner=source)
   self.assertEqual('incomplete',r['status']);self.assertTrue(r['incomplete_reasons'])
  r=self.result('package q; import static p.Outer.*; class BlockRead {} class BlockRead {} class T { BlockRead x; }')
  self.assertEqual('incomplete',r['status']);self.assertTrue(r['member_uses'][0]['ambiguous'])
 def test_new_git_owner_is_reobserved_without_source_mutation(self):
  args=self.args('package q; import static p.Outer.*; class T { BlockRead x; }',['--known-member-owner','p.Outer=Outer.java'])
  def changed(module,args):
   original=module.Engine.blob;seen={}
   def read(engine,git,rev,path,account=True):
    data,provenance=original(engine,git,rev,path,account);seen[path]=seen.get(path,0)+1
    return (data+b' ',provenance) if seen[path]>1 else (data,provenance)
   with patch.object(module.Engine,'blob',read):return module.run(args)
  with self.assertRaises(self.p.PreflightError) as c:self.execute(args,change=changed)
  self.assertEqual(3,c.exception.code)
 def test_wrong_final_pins_aliases_paths_and_wrong_owners_reject(self):
  data=self.owner.read_bytes();base='p.Outer='+str(len(data))+'='+hashlib.sha256(data).hexdigest()+'='
  alias=self.root/'Alias.java';alias.symlink_to(self.owner)
  hard=self.root/'Hard.java';os.link(self.owner,hard)
  for selection in [base+str(alias),base+str(hard),base+str(self.owner),
                    'p.Outer=1='+'0'*64+'='+str(self.owner)]:
   with self.subTest(selection=selection):
    with self.assertRaises(self.p.PreflightError):self.execute(self.args('class T {}',['--known-member-owner-final',selection]))
  hard.unlink()
  for selection in ['p.Wrong=Outer.java','p.Outer=../Outer.java','p..Outer=Outer.java']:
   with self.assertRaises(self.p.PreflightError):self.execute(self.args('class T {}',['--known-member-owner',selection]))
 def test_shared_selection_inventory_hit_context_token_and_time_budgets(self):
  for extra in [['--max-known-types','1','--known-type','p.Top=Top.java'],['--max-hits','1'],['--max-context-tokens','1'],['--max-tokens','10']]:
   with self.subTest(extra=extra):
    with self.assertRaises(self.p.PreflightError):self.result('package q; import static p.Outer.*; class T { BlockRead a; Store b; }',extra)
  with patch.object(self.p.time,'monotonic',side_effect=[0,100]):
   with self.assertRaises(self.p.PreflightError):self.result('class T {}')
 def test_final_owner_and_consumer_are_reobserved_for_changes(self):
  data=self.owner.read_bytes();sel='p.Outer='+str(len(data))+'='+hashlib.sha256(data).hexdigest()+'='+str(self.owner)
  args=self.args('package q; import static p.Outer.*; class T { BlockRead x; }',['--known-member-owner-final',sel])
  original=self.p.read_final;counts={}
  def changed(path,cap):
   counts[path]=counts.get(path,0)+1
   out=original(path,cap)
   if path==self.owner and counts[path]>1:return out[0]+b' ',out[1]
   return out
  with patch.object(self.p,'read_final',changed):
   with self.assertRaises(self.p.PreflightError) as caught:self.execute(args)
  self.assertEqual(3,caught.exception.code)
 def test_cli_help_and_exit_shapes(self):
  helptext=self.p.parser().format_help();self.assertIn('--known-member-owner',helptext);self.assertIn('--known-member-owner-final',helptext)
  args=self.args('package q; import static p.Outer.*; class T { BlockRead x; }',['--known-member-owner','p.Outer=Outer.java'])
  class Out(io.StringIO):
   def __init__(self):super().__init__();self.buffer=io.BytesIO()
  out=Out()
  def call(module,args):
   argv=['--git-dir',args.git_dir,'--revision',args.revision,'--known-member-owner',args.known_member_owner[0],
    '--final',args.final,'--final-bytes',str(args.final_bytes),'--final-sha256',args.final_sha256]
   with patch.object(sys,'stdout',out):return module.main(argv)
  code=self.execute(args,change=call);self.assertEqual(1,code)
  self.assertEqual('possible_missing_nonstatic_member_import',json.loads(out.buffer.getvalue())['hints'][0]['kind'])
 @unittest.skipUnless(os.environ.get('PREFLIGHT_CLI_GIT_DIR'),'explicit read-only CLI Git metadata not supplied')
 def test_real_cli_final_owner_bad_fixed_incomplete_and_pin_error(self):
  data=self.owner.read_bytes();selection='p.Outer='+str(len(data))+'='+hashlib.sha256(data).hexdigest()+'='+str(self.owner)
  cases=[('package q; import static p.Outer.*; class T { BlockRead x; }',1,'observed'),
   ('package q; import static p.Outer.*; import p.Outer.BlockRead; class T { BlockRead x; }',0,'observed'),
   ('package q; import static p.Outer.*; class T extends Base { BlockRead x; }',3,'incomplete')]
  for text,code,status in cases:
   b=text.encode();self.final.write_bytes(b)
   argv=[sys.executable,'-B',str(self.here/'java_project_import_preflight.py'),
    '--git-dir',os.environ['PREFLIGHT_CLI_GIT_DIR'],'--revision',REV,'--known-member-owner-final',selection,
    '--final',str(self.final),'--final-bytes',str(len(b)),'--final-sha256',hashlib.sha256(b).hexdigest()]
   out=subprocess.run(argv,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=10)
   self.assertEqual(code,out.returncode,out.stderr);self.assertEqual(status,json.loads(out.stdout)['status'])
  argv[argv.index('--final-sha256')+1]='0'*64
  out=subprocess.run(argv,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=10)
  self.assertEqual(2,out.returncode);self.assertEqual('error',json.loads(out.stdout)['status'])
 @unittest.skipUnless(os.environ.get('PREFLIGHT_COMPAT_BASELINE_GIT_DIR'),'explicit immutable baseline metadata not supplied')
 def test_exact_legacy_json_error_and_limit_surface_without_opt_in(self):
  raw=subprocess.check_output(['git','--git-dir='+os.environ['PREFLIGHT_COMPAT_BASELINE_GIT_DIR'],'show',REV+':tools/soniccd/java_project_import_preflight.py'])
  self.assertEqual(BASE_SHA,hashlib.sha256(raw).hexdigest())
  old=types.ModuleType('_member_preflight_exact_legacy');old.__file__=str(self.here/'java_project_import_preflight.py');sys.modules[old.__name__]=old
  self.addCleanup(sys.modules.pop,old.__name__,None);exec(compile(raw,old.__file__,'exec'),old.__dict__)
  self.assertEqual(vars(old.Limits()),vars(self.p.Limits()));self.assertEqual(old.LIMITATIONS,self.p.LIMITATIONS)
  cases=[('package q; class T { Top x; }',[]),('package q; import p.Top; class T { Top x; }',[]),
   ('package q; import static unknown.Other.*; class T { Top x; }',[]),
   ('package q; import p.Outer; class T { Object x=Outer.Kind.MISSING; }',['--known-enum','p.Outer.Kind=Outer.java']),
   ('package q; class T { Top x; Top y; }',['--max-hits','1']),('package q; import p. To p; class T {}',[])]
  for text,extra in cases:
   outcomes=[]
   for module in [old,self.p]:
    try:outcome=self.execute(self.args(text,['--known-type','p.Top=Top.java',*extra],module),module);outcomes.append(module.render(outcome))
    except module.PreflightError as error:outcomes.append((error.code,str(error)))
   self.assertEqual(outcomes[0],outcomes[1])

if __name__=='__main__':unittest.main()
