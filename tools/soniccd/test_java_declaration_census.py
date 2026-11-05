#!/usr/bin/env python3
"""Source census contract/safety tests; only Python and owned synthetic Git fixtures.

Origin: 2026-10-26 private java-declaration-census task. Set TMPDIR to owned
scratch when running. No Java/compiler/runtime code is executed.
"""
import contextlib
import hashlib
import io
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('java_declaration_census', HERE / 'java_declaration_census.py')
census = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = census
spec.loader.exec_module(census)


def parsed(text, **limits):
    return census.inventory(text.encode(), census.Limits(**limits))


def rows(text, kind):
    return [d for d in parsed(text) if d['kind'] == kind]


class DeclarationContract(unittest.TestCase):
    def test_nested_record_explicit_and_compact_constructors(self):
        text = '''package p;
public class Outer<T> {
  public record Point<T>(@Tag(v={"a,b", "c"}) T x, int[] ys, String... labels) {
    public Point { check(x); }
    public Point(T x) { this(x, new int[0]); }
    public T x() { return x; }
  }
  Outer() {}
}'''
        ds = parsed(text)
        self.assertEqual([(d['owner'], d['name'], d['kind'], d['arity']) for d in ds], [
            ('p', 'Outer', 'class', None), ('p.Outer', 'Point', 'record', None),
            ('p.Outer.Point', 'x', 'record_component', None),
            ('p.Outer.Point', 'ys', 'record_component', None),
            ('p.Outer.Point', 'labels', 'record_component', None),
            ('p.Outer.Point', 'Point', 'compact_constructor', None),
            ('p.Outer.Point', 'Point', 'constructor', 1),
            ('p.Outer.Point', 'x', 'method', 0), ('p.Outer', 'Outer', 'constructor', 0)])
        compact = ds[5]
        self.assertEqual(compact['parameters'], [])
        self.assertEqual(compact['record_component_count'], 3)
        self.assertEqual(compact['modifiers'], ['public'])
        self.assertEqual(compact['line'], 4)
        self.assertEqual(ds[4]['type'], 'String . . .')
        self.assertEqual(sum(d['kind'] == 'method' for d in ds), 1)

    def test_overloads_abstract_native_and_interface_modifiers(self):
        text = '''package p;
interface I {
  void f();
  default int f(int a) { return a; }
  static <T> T f(T a, T b) { return a; }
  private void hidden() {}
}
abstract class C implements I {
  public abstract void f();
  protected native int nativeCall(String x[]);
}'''
        ms = rows(text, 'method')
        self.assertEqual([(d['owner'],d['name'],d['arity'],d['modifiers']) for d in ms], [
            ('p.I','f',0,[]),('p.I','f',1,['default']),('p.I','f',2,['static']),
            ('p.I','hidden',0,['private']),('p.C','f',0,['public','abstract']),
            ('p.C','nativeCall',1,['protected','native'])])
        self.assertEqual(ms[-1]['parameters'][0]['type'], 'String [ ]')

    def test_generic_annotation_commas_arrays_varargs_and_throws(self):
        text = '''class C {
 @A(x={1,2}) public <T extends Map<String, List<Integer>> & Comparable<T>>
 @B T[] pick(@A(v={"x,y", "z"}) final Map<String, List<T[]>> input,
              String @B [] @B ... rest) throws E, p.F { return null; }
}'''
        m = rows(text, 'method')[0]
        self.assertEqual(m['arity'], 2)
        self.assertEqual(m['parameters'][0]['name'], 'input')
        self.assertIn('Map < String , List < T [ ] > >', m['parameters'][0]['type'])
        self.assertTrue(m['parameters'][1]['varargs'])
        self.assertEqual(m['modifiers'], ['public'])
        self.assertIn('throws E , p . F', m['signature'])

    def test_calls_literals_comments_initializers_are_not_methods(self):
        text = '''class C {
 String value = helper("void fake(int a, int b) {}");
 String block = """
 class Fake { void wrong() {} }
 """;
 // public void ghost(int a) {}
 static { call(1, 2); }
 { call(); }
 void real() { another(1, 2); String t = "record Fake(int x) {}"; }
 int[] a = {1,2}, b = {3};
 enum E { A(1,2), B; E(int... args) {} }
}'''
        ds = parsed(text)
        self.assertEqual([d['name'] for d in ds if d['kind']=='method'], ['real'])
        self.assertEqual([d['name'] for d in ds if d['kind']=='constructor'], ['E'])

    def test_no_generated_declarations_or_implicit_modifiers(self):
        ds = parsed('record R(int x) {} interface I { int value(); } enum E { A, B }')
        self.assertEqual([d['kind'] for d in ds], ['record','record_component','interface','method','enum'])
        self.assertEqual(ds[-2]['modifiers'], [])

    def test_multiline_name_line_and_crlf(self):
        ds=parsed('class C {\r\n @A public\r\n void\r\n f(int x) {}\r\n}')
        self.assertEqual(ds[1]['line'], 4)

    def test_unknown_grammar_empty_and_unsupported_scopes_fail_closed(self):
        cases = ['', '// nothing', 'module x {}', '@interface A { int x(); }',
                 'class C { magic ???; }', 'class C { f(); }',
                 'class C { void f(C this) {} }',
                 'class C { void f(){ class Local {} } }',
                 'class C { Object a = new Object() { void hidden(){} }; }',
                 'enum E { A { void hidden() {} } }',
                 'class C { void f(){ record R(int x){} } }',
                 'class C { void f(int... x, int y) {} }',
                 'class C { void f(){ String s="unterminated; } }',
                 'class C { /* \\u000a */ }', 'class C { int café; }',
                 'class C { void f() }', 'record R { }', 'class C { int _; }']
        for text in cases:
            with self.subTest(text=text), self.assertRaises(census.CensusError) as caught:
                parsed(text)
            self.assertEqual(caught.exception.code,3)

    def test_annotation_argument_names_do_not_move_component_source_lines(self):
        ds = parsed('record R(@Tag(name="x")\n String\n name) {}')
        component = [d for d in ds if d['kind']=='record_component'][0]
        self.assertEqual(component['line'],3)
        self.assertEqual(component['name'],'name')
        self.assertIn('@ Tag ( name = <literal> )', component['signature'])

    def test_annotated_anonymous_constructor_scope_is_incomplete(self):
        for text in ['class C { Object x = new @A(v={1,2}) Object() { void hidden(){} }; }',
                     'class C { void f() { Object x = new Box<@A(v=1) String>() { void hidden(){} }; } }']:
            with self.subTest(text=text), self.assertRaises(census.CensusError): parsed(text)

    def test_generic_nested_member_types_and_sealed_headers(self):
        ds=parsed('sealed class C<T> permits D { non-sealed class D extends C<String> {} '
                  'interface I<T> extends A<T>, B { record R(int x) {} } }')
        self.assertEqual([(d['owner'],d['name']) for d in ds],
                         [('', 'C'), ('C','D'), ('C','I'), ('C.I','R'), ('C.I.R','x')])
        self.assertEqual(ds[1]['modifiers'], ['non-sealed'])

    def test_bounds_tokens_declarations_context_depth_bytes(self):
        for limits,text in [({'max_tokens':3},'class C {}'),
                            ({'max_declarations':1},'class C {void f(){}}'),
                            ({'max_context_tokens':5},'class C { void f(int a,int b){} }'),
                            ({'max_depth':1},'class C { class D {} }'),
                            ({'max_file_bytes':3},'class C {}')]:
            with self.subTest(limits=limits),self.assertRaises(census.CensusError) as caught:
                parsed(text,**limits)
            self.assertEqual(caught.exception.code,3)

    def test_duplicate_signature_and_type_are_ambiguous(self):
        for text in ['class C {void f(int a){} void f(int b){}}',
                     'class C {} class C {}','class C {} record C(int x){}','record R(int x, int x){}']:
            with self.subTest(text=text), self.assertRaises(census.CensusError): parsed(text)

    def test_diff_record_migration_and_overload_ambiguity(self):
        base=parsed('record R(int x) { R(int x){} void f(int a){} void f(String b){} }')
        final=parsed('record R(int x,int y) { R(int x,int y){} void f(long a){} void f(Object b){} }')
        diff=census.compare(base,final)
        self.assertEqual(len(diff['ambiguous_groups']),1)
        self.assertEqual({d['name'] for d in diff['added']}, {'y','f'})
        self.assertEqual([d['name'] for d in diff['removed']], ['f','f'])
        self.assertEqual({d['before']['kind'] for d in diff['changed']}, {'record','constructor'})

    def test_annotation_literal_diff_is_explicitly_incomplete(self):
        before=parsed('@Tag("before") class C {}')
        after=parsed('@Tag("after") class C {}')
        diff=census.compare(before,after)
        self.assertFalse(diff['complete'])
        self.assertEqual(diff['literal_ambiguities'],[dict(owner='',kind='class',name='C')])

    def test_diff_line_shift_ignored_and_modifier_change_reported(self):
        base=parsed('class C { public int f(int x){return 1;} }')
        same=parsed('\n\nclass C { public int f(int x){return 2;} }')
        self.assertFalse(census.compare(base,same)['has_changes'])
        changed=census.compare(base,parsed('class C { protected int f(int x){return 1;} }'))
        self.assertEqual(len(changed['changed']),1)
        self.assertEqual(changed['changed'][0]['match'],'same_parameter_types')


class ImmutableInputs(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='census-test-')
        self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        self.repo=self.root/'repo';self.repo.mkdir()
        self.git('init','-q','--template=')
        (self.repo/'src').mkdir()
        self.source='src/C.java'
        (self.repo/self.source).write_text('package p; record C(int x) { public int f(){return 1;} }\n')
        self.git('add','--',self.source)
        self.git('commit','-qm','synthetic fixture')
        self.revision=self.git('rev-parse','HEAD').decode().strip()
        self.final=self.root/'FINAL.java'
        self.final.write_text('package p; record C(int x,int y) { public int f(){return 1;} }\n')
        self.pins=dict(final_sha256=hashlib.sha256(self.final.read_bytes()).hexdigest(),
                       final_bytes=self.final.stat().st_size)

    def git(self,*args):
        env={'PATH':os.defpath,'HOME':str(self.root),'GIT_CONFIG_NOSYSTEM':'1',
             'GIT_CONFIG_GLOBAL':os.devnull,'GIT_TERMINAL_PROMPT':'0','LC_ALL':'C'}
        return subprocess.check_output(['git','-c','core.hooksPath=/dev/null',
             '-c','user.name=Census fixture','-c','user.email=fixture@example.invalid',
             '-c','commit.gpgsign=false',*args],cwd=self.repo,env=env,timeout=10,
             stderr=subprocess.DEVNULL)

    def options(self, include_final=True, **changes):
        argv=['--git-dir',str(self.repo/'.git'),'--revision',self.revision,'--source',self.source]
        if include_final:
            argv+=['--final',str(self.final),'--final-sha256',self.pins['final_sha256'],
                   '--final-bytes',str(self.pins['final_bytes'])]
        args=census.parser().parse_args(argv)
        for k,v in changes.items():setattr(args,k,v)
        return args

    def test_immutable_baseline_ignores_worktree_and_head(self):
        (self.repo/self.source).write_text('class Moving { void wrong(){} }')
        result=census.run(self.options(False))
        self.assertEqual(result['baseline']['provenance']['revision'],self.revision)
        self.assertEqual(result['baseline']['declarations'][0]['name'],'C')
        p=result['baseline']['provenance']
        data=b'package p; record C(int x) { public int f(){return 1;} }\n'
        self.assertEqual(p['bytes'],len(data))
        self.assertEqual(p['sha256'],hashlib.sha256(data).hexdigest())
        self.assertEqual(p['blob_sha1'],hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest())

    def test_pinned_append_diff_determinism_and_preservation(self):
        before={p:p.read_bytes() for p in [self.repo/self.source,self.final,self.repo/'.git/index',self.repo/'.git/HEAD']}
        result=census.run(self.options())
        self.assertEqual(result,census.run(self.options()))
        self.assertTrue(result['diff']['has_changes'])
        self.assertEqual([d['name'] for d in result['diff']['added']],['y'])
        self.assertEqual([d['before']['kind'] for d in result['diff']['changed']],['record'])
        self.assertEqual(before,{p:p.read_bytes() for p in before})

    def test_missing_invented_bad_pins_and_limits_rejected(self):
        for changes in [dict(final_sha256=None),dict(final_bytes=None),dict(final_sha256='0'*64),
                        dict(final_bytes=0),dict(final_sha256='g'*64),dict(final_bytes=-1),
                        dict(max_total_bytes=10),dict(max_file_bytes=10),dict(revision='HEAD'),
                        dict(revision=self.revision[:12]),dict(source='../C.java')]:
            with self.subTest(changes=changes),self.assertRaises(census.CensusError):
                census.run(self.options(**changes))

    def test_path_alias_traversal_symlink_hardlink_nonregular(self):
        link=self.root/'alias';link.symlink_to(self.repo,target_is_directory=True)
        filelink=self.root/'alias.java';filelink.symlink_to(self.final)
        fifo=self.root/'fifo.java';os.mkfifo(fifo)
        for changes in [dict(git_dir=str(link/'.git')),dict(git_dir=str(self.repo/'src/../.git')),
                        dict(source='/src/C.java'),dict(source='src//C.java'),
                        dict(source='src/./C.java'),dict(source=':C.java')]:
            with self.subTest(changes=changes),self.assertRaises(census.CensusError):
                census.run(self.options(False,**changes))
        for path in [filelink,fifo]:
            with self.subTest(path=path),self.assertRaises(census.CensusError):
                census.run(self.options(final=str(path)))
        hardlink=self.root/'hard.java';os.link(self.final,hardlink)
        with self.assertRaises(census.CensusError):
            census.run(self.options(final=str(hardlink)))
        hardlink.unlink()

    def test_git_symlink_and_tree_sources_rejected(self):
        (self.repo/'src/Link.java').symlink_to('C.java')
        self.git('add','--','src/Link.java');self.git('commit','-qm','link fixture')
        rev=self.git('rev-parse','HEAD').decode().strip()
        for path in ['src/Link.java','src','src/Missing.java']:
            with self.subTest(path=path),self.assertRaises(census.CensusError):
                census.run(self.options(False,source=path,revision=rev))

    def test_literal_equality_requires_identical_full_bytes(self):
        original='@Tag("before") class C {}'
        (self.repo/self.source).write_text(original)
        self.git('add','--',self.source);self.git('commit','-qm','annotated fixture')
        revision=self.git('rev-parse','HEAD').decode().strip()
        def execute(text):
            self.final.write_text(text)
            return census.run(self.options(revision=revision,final_bytes=self.final.stat().st_size,
                            final_sha256=hashlib.sha256(self.final.read_bytes()).hexdigest()))
        same=execute(original)
        self.assertEqual(same['status'],'complete')
        self.assertTrue(same['diff']['complete'])
        different=execute('@Tag("after") class C {}')
        self.assertEqual(different['status'],'incomplete')
        self.assertFalse(different['diff']['complete'])

    def test_final_change_between_observations_is_incomplete(self):
        original=census.read_final
        reads=[]
        def changing(*args):
            result=original(*args);reads.append(1)
            if len(reads)==1:self.final.write_bytes(self.final.read_bytes()+b' ')
            return result
        with mock.patch.object(census,'read_final',side_effect=changing):
            with self.assertRaises(census.CensusError) as caught:census.run(self.options())
        self.assertEqual(caught.exception.code,3)

    def test_git_read_change_and_process_budget(self):
        original=census.git_read
        def changed(git_dir,args,cap):
            data=original(git_dir,args,cap)
            if args[:2]==['cat-file','blob']:return data+b'x'
            return data
        with mock.patch.object(census,'git_read',side_effect=changed):
            with self.assertRaises(census.CensusError):census.run(self.options(False))
        with mock.patch.object(census.subprocess,'Popen') as popen:
            with self.assertRaises(census.CensusError):census.git_read(self.repo/'.git',['status'],10)
            popen.assert_not_called()
        with self.assertRaises(census.CensusError) as caught:
            census.git_read(self.repo/'.git',['cat-file','blob',
                census.run(self.options(False))['baseline']['provenance']['blob_sha1']],1)
        self.assertEqual(caught.exception.code,3)

    def test_git_timeout_is_bounded_and_no_shell_or_foreign_env(self):
        original=census.subprocess.Popen
        with mock.patch.object(census.subprocess,'Popen',wraps=original) as popen:
            with mock.patch.object(census.time,'monotonic',side_effect=[0,11]):
                with self.assertRaises(census.CensusError) as caught:
                    census.git_read(self.repo/'.git',['cat-file','-t',self.revision],128)
            self.assertEqual(caught.exception.code,3)
            args,kwargs=popen.call_args
            self.assertFalse(kwargs['shell'])
            self.assertIn('--no-replace-objects',args[0])
            self.assertEqual(kwargs['env']['GIT_NO_LAZY_FETCH'],'1')
            self.assertEqual(kwargs['env']['GIT_ALLOW_PROTOCOL'],'')
            self.assertNotIn('GIT_WORK_TREE',kwargs['env'])

    def test_replacements_and_environment_cannot_select_different_baseline(self):
        (self.repo/self.source).write_text('class Wrong {}')
        self.git('add','--',self.source);self.git('commit','-qm','different fixture')
        other=self.git('rev-parse','HEAD').decode().strip()
        self.git('replace',self.revision,other)
        with mock.patch.dict(os.environ,{'GIT_DIR':'/missing','GIT_WORK_TREE':'/missing',
                'GIT_CONFIG_COUNT':'1','GIT_CONFIG_KEY_0':'alias.cat-file','GIT_CONFIG_VALUE_0':'!false'}):
            self.assertEqual(census.run(self.options(False))['baseline']['declarations'][0]['name'],'C')

    def test_cli_json_exit_codes_and_help(self):
        env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
        base=[sys.executable,'-B',str(HERE/'java_declaration_census.py')]
        def cli(args):return subprocess.run(base+args,env=env,capture_output=True,timeout=15)
        args=['--git-dir',str(self.repo/'.git'),'--revision',self.revision,'--source',self.source]
        ok=cli(args);self.assertEqual(ok.returncode,0);self.assertEqual(json.loads(ok.stdout)['status'],'complete')
        different=cli(args+['--final',str(self.final),'--final-bytes',str(self.pins['final_bytes']),
                           '--final-sha256',self.pins['final_sha256']])
        self.assertEqual(different.returncode,1)
        bad=cli(args+['--final',str(self.final)])
        self.assertEqual(bad.returncode,2);self.assertEqual(json.loads(bad.stdout)['status'],'error')
        self.final.write_text('module unsupported {}');self.pins=dict(final_bytes=self.final.stat().st_size,
                                         final_sha256=hashlib.sha256(self.final.read_bytes()).hexdigest())
        incomplete=cli(args+['--final',str(self.final),'--final-bytes',str(self.pins['final_bytes']),
                            '--final-sha256',self.pins['final_sha256']])
        self.assertEqual(incomplete.returncode,3);self.assertEqual(json.loads(incomplete.stdout)['status'],'incomplete')
        self.assertEqual(cli(['--help']).returncode,0)


class OrdinarySelectorPreflight(unittest.TestCase):
    """Static selector contracts over pinned synthetic sources, without Java."""
    setUp = ImmutableInputs.setUp
    git = ImmutableInputs.git
    options = ImmutableInputs.options

    def pin_source(self, body, header='class C', imports='import org.junit.jupiter.api.Test;'):
        data = ('package p; ' + imports + ' ' + header + ' {' + body + '}\n').encode()
        (self.repo / self.source).write_bytes(data)
        self.git('add', '--', self.source)
        self.git('commit', '-qm', 'ordinary selector fixture')
        self.revision = self.git('rev-parse', 'HEAD').decode().strip()
        return data

    def cli(self, *extra):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = census.main(['--git-dir', str(self.repo / '.git'), '--revision', self.revision,
                                '--source', self.source, *extra])
        return code, json.loads(output.getvalue())

    def final_args(self, data):
        self.final.write_bytes(data)
        return ['--final', str(self.final), '--final-sha256', hashlib.sha256(data).hexdigest(),
                '--final-bytes', str(len(data))]

    def test_repeatable_selectors_emit_literal_ids_in_requested_order(self):
        self.pin_source('@Test void first() {} @Test() void second() {} '
                        'void helper() { String s = "@Test void invented() {}"; }')
        code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'second',
                                '--require-ordinary-test', 'first', '--expected-count', '2')
        self.assertEqual(code, 0)
        self.assertEqual(result['ordinary_test_selection'], dict(source='baseline', selected_class='p.C',
            requested_names=['second', 'first'], selected_ids=['p.C#second', 'p.C#first'],
            missing_names=[], matches=True))
        self.assertEqual(result['expected_count']['value'], 2)
        self.assertEqual(result['baseline']['test_inventory']['proposed_count'], 2)

    def test_unknown_names_are_explicit_and_preserve_known_selection(self):
        actual = 'complete138RowManifestPreservesTheOriginal136AndPinsEveryAdditionalOriginalDomainSpan'
        typo = 'complete138RowManifestStillPinsAllOldOrdinalsAndAppendedLiteralTables'
        self.pin_source('@Test void ' + actual + '() {} void helper() {}')
        code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', actual,
                                '--require-ordinary-test', typo, '--require-ordinary-test', 'helper')
        self.assertEqual(code, 1)
        selection = result['ordinary_test_selection']
        self.assertEqual(selection['selected_ids'], ['p.C#' + actual])
        self.assertEqual(selection['missing_names'], [typo, 'helper'])
        self.assertFalse(selection['matches'])
        self.assertEqual(result['status'], 'complete')

    def test_expected_count_stays_whole_inventory_not_selection_size(self):
        self.pin_source('@Test void first() {} @Test void second() {}')
        args = ['--test-inventory', 'p.C', '--require-ordinary-test', 'first']
        code, result = self.cli(*args, '--expected-count', '1')
        self.assertEqual(code, 1)
        self.assertTrue(result['ordinary_test_selection']['matches'])
        self.assertFalse(result['expected_count']['matches'])
        self.assertEqual(self.cli(*args, '--expected-count', '2')[0], 0)

    def test_final_pins_select_final_names_without_union_or_baseline_fallback(self):
        self.pin_source('@Test void old() {}')
        pins = self.final_args(b'package p; import org.junit.jupiter.api.Test; class C { @Test void fresh() {} }')
        code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'fresh', *pins)
        self.assertEqual(code, 0)
        self.assertEqual(result['ordinary_test_selection']['source'], 'final')
        self.assertEqual(result['ordinary_test_selection']['selected_ids'], ['p.C#fresh'])
        code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'old', *pins)
        self.assertEqual(code, 1)
        self.assertEqual(result['ordinary_test_selection']['missing_names'], ['old'])
        pins[3] = '0' * 64
        self.assertEqual(self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'fresh', *pins)[0], 2)

    def test_exact_class_is_required_and_absent_class_is_incomplete(self):
        self.pin_source('@Test void check() {}')
        for selected in ['p.Other', 'p.C.Inner']:
            with self.subTest(selected=selected):
                code, result = self.cli('--test-inventory', selected, '--require-ordinary-test', 'check')
                self.assertEqual(code, 3)
                selection = result['ordinary_test_selection']
                self.assertIsNone(selection['selected_ids'])
                self.assertIsNone(selection['missing_names'])
                self.assertIsNone(selection['matches'])
        self.assertEqual(self.cli('--test-inventory', 'p.*', '--require-ordinary-test', 'check')[0], 2)

    def test_inherited_and_parameterized_factory_repeated_methods_never_qualify(self):
        cases = [('class C extends Base', '@Test void check() {}', 'inherited'),
                 ('class C', '@org.junit.jupiter.params.ParameterizedTest void parameter(int x) {} '
                  '@Test void check() {}', 'parameter'),
                 ('class C', '@org.junit.jupiter.api.TestFactory Object factory() { return null; } '
                  '@Test void check() {}', 'factory'),
                 ('class C', '@org.junit.jupiter.api.RepeatedTest(2) void repeated() {} '
                  '@Test void check() {}', 'repeated')]
        for header, body, name in cases:
            with self.subTest(name=name):
                self.pin_source(body, header=header)
                code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', name)
                self.assertEqual(code, 3)
                self.assertIsNone(result['ordinary_test_selection']['selected_ids'])
                self.assertIsNone(result['ordinary_test_selection']['missing_names'])
                self.assertTrue(result['baseline']['test_inventory']['incomplete_reasons'])

    def test_testinstance_and_member_types_keep_real_target_scope_incomplete(self):
        cases = [('@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS) class C',
                  '@Test void check() {}'),
                 ('class C', '@Test void check() {} record Helper(int n) {}')]
        for header, body in cases:
            with self.subTest(header=header, body=body):
                self.pin_source(body, header=header)
                code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'check')
                self.assertEqual(code, 3)
                self.assertIsNone(result['ordinary_test_selection']['selected_ids'])
                self.assertIsNone(result['ordinary_test_selection']['matches'])

    def test_overloaded_nonordinary_test_keeps_existing_incomplete_classification(self):
        self.pin_source('@Test void check() {} @Test void check(int x) {}')
        code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'check')
        self.assertEqual(code, 3)
        self.assertIsNone(result['ordinary_test_selection']['matches'])
        self.assertIsNone(result['baseline']['test_inventory']['proposed_count'])

    def test_incomplete_priority_over_missing_names_and_wrong_expected_count(self):
        self.pin_source('@Test void check() {}', header='class C extends Base')
        code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'missing',
                                '--expected-count', '999')
        self.assertEqual(code, 3)
        self.assertIsNone(result['ordinary_test_selection']['matches'])
        self.assertIsNone(result['expected_count']['matches'])

    def test_either_side_incomplete_retains_exit_three(self):
        self.pin_source('@Test void check() {}', header='class C extends Base')
        pins = self.final_args(b'package p; import org.junit.jupiter.api.Test; class C { @Test void check() {} }')
        code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'missing', *pins)
        self.assertEqual(code, 3)
        self.assertEqual(result['ordinary_test_selection']['missing_names'], ['missing'])
        self.pin_source('@Test void check() {}')
        pins = self.final_args(b'package p; import org.junit.jupiter.api.Test; class C extends Base { @Test void check() {} }')
        code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'check', *pins)
        self.assertEqual(code, 3)
        self.assertIsNone(result['ordinary_test_selection']['selected_ids'])

    def test_token_and_combined_byte_budgets_still_fail_incomplete(self):
        data = self.pin_source('@Test void check() {}')
        for args in [('--max-tokens', '4'),
                     ('--max-total-bytes', str(len(data)), *self.final_args(data))]:
            with self.subTest(args=args):
                code, result = self.cli('--test-inventory', 'p.C', '--require-ordinary-test', 'missing', *args)
                self.assertEqual(code, 3)
                self.assertEqual(result['status'], 'incomplete')
                self.assertNotIn('ordinary_test_selection', result)

    def test_malformed_duplicate_without_inventory_and_oversized_selectors_are_invalid(self):
        self.pin_source('@Test void check() {}')
        invalid = [('--require-ordinary-test', 'check'),
                   ('--test-inventory', 'p.C', '--require-ordinary-test', 'check',
                    '--require-ordinary-test', 'check')]
        for name in ['', 'check()', 'p.C#check', 'p.C.check', '*', '1check', 'void', 'null', 'x' * 4097]:
            invalid.append(('--test-inventory', 'p.C', '--require-ordinary-test', name))
        invalid.append(tuple(['--test-inventory', 'p.C'] +
                       [item for n in range(51) for item in ('--require-ordinary-test', 't%d' % n)]))
        for args in invalid:
            with self.subTest(args=args):
                code, result = self.cli(*args)
                self.assertEqual(code, 2)
                self.assertEqual(result['status'], 'error')

    def test_fifty_distinct_ordinary_selectors_are_allowed(self):
        self.pin_source(' '.join('@Test void t%d() {}' % n for n in range(50)))
        args = ['--test-inventory', 'p.C']
        for n in range(50):
            args += ['--require-ordinary-test', 't%d' % n]
        code, result = self.cli(*args)
        self.assertEqual(code, 0)
        self.assertEqual(result['ordinary_test_selection']['selected_ids'], ['p.C#t%d' % n for n in range(50)])

    def test_null_or_nonlist_selector_inputs_fail_before_source_reads(self):
        for value in [[None], ['check', None], 'check', [1], [], ()]:
            with self.subTest(value=value):
                args = self.options(False)
                args.test_inventory = 'p.C'
                args.require_ordinary_test = value
                with mock.patch.object(census, 'read_baseline') as read:
                    with self.assertRaises(census.CensusError) as caught:
                        census.run(args)
                    self.assertEqual(caught.exception.code, 2)
                    read.assert_not_called()

    def test_unused_flag_preserves_old_result_and_optional_namespace_shape(self):
        self.pin_source('@Test void check() {}')
        args = self.options(False)
        args.test_inventory = 'p.C'
        result = census.run(args)
        self.assertNotIn('ordinary_test_selection', result)
        del args.require_ordinary_test
        self.assertEqual(result, census.run(args))


if __name__=='__main__':unittest.main()
