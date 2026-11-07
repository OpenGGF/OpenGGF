#!/usr/bin/env python3
"""Literal public name/arity inventory controls; no Java and no new Git fixtures.

Purpose: stale member lists and correct-multiset/wrong-natural-order failures.
Inputs: explicit synthetic owner/consumer bytes, mocked immutable readers.
Origin: R54/R55 CdObjectMappings integration misses, 2026-11-07.
"""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import unittest
from unittest import mock
import test_java_declaration_census as support

census = support.census
IMPORTS = ('import java.util.List; import java.util.Arrays; import java.lang.reflect.Modifier; '
           'import p.Owner; import static org.junit.jupiter.api.Assertions.*;')


def owner(body='public void resolve(int a,int b,int c) {} public void withAct3FutureGameOver(int a,int b,int c) {}', header='public final class Owner'):
    return ('package p; '+header+' {'+body+'}').encode()


def consumer(expected='"resolve/3","withAct3FutureGameOver/3"', expression=None, imports=IMPORTS, header='class Inventory'):
    actual = expression or ('Arrays.stream(Owner.class.getDeclaredMethods())'
        '.filter(m->Modifier.isPublic(m.getModifiers()))'
        '.map(n->n.getName()+"/"+n.getParameterCount()).sorted().toList()')
    return ('package q; '+imports+' '+header+' { void check() { assertEquals(List.of('+expected+'),'+actual+'); } }').encode()


def check(o=None, t=None, **bounds):
    return census.public_method_inventory(owner() if o is None else o,
        consumer() if t is None else t, census.Limits(**bounds), 'p.Owner')


class LiteralInventory(unittest.TestCase):
    def test_stale_exact_list_reports_added_member(self):
        r=check(t=consumer('"resolve/3"'))
        self.assertTrue(r['complete']);self.assertFalse(r['matches'])
        c=r['checks'][0];self.assertEqual(['withAct3FutureGameOver/3'],c['missing_from_literal'])
        self.assertEqual([],c['extra_in_literal']);self.assertEqual('member_mismatch',c['finding'])

    def test_right_multiset_wrong_order_is_not_clean(self):
        r=check(t=consumer('"withAct3FutureGameOver/3","resolve/3"'))
        self.assertFalse(r['matches']);c=r['checks'][0]
        self.assertTrue(c['same_multiset']);self.assertEqual('order_mismatch',c['finding'])
        self.assertEqual(['withAct3FutureGameOver/3','resolve/3'],c['expected_literal'])

    def test_corrected_literal_matches_actual_natural_sort(self):
        r=check();self.assertTrue(r['complete']);self.assertTrue(r['matches'])
        self.assertEqual([],r['findings']);self.assertEqual(['resolve/3','withAct3FutureGameOver/3'],r['observed_public_methods'])

    def test_overloads_and_duplicate_name_arity_are_preserved(self):
        o=owner('public void f(int a){} public void f(String s){} public void f(){} private void secret(){} protected void hidden(){}')
        r=check(o,consumer('"f/0","f/1","f/1"'));self.assertTrue(r['matches'])
        self.assertEqual(['f/0','f/1','f/1'],r['observed_public_methods'])
        r=check(o,consumer('"f/0","f/1"'));self.assertEqual(['f/1'],r['checks'][0]['missing_from_literal'])

    def test_lexical_sort_not_numeric_arity_or_deduplicated(self):
        o=owner('public void f(int a,int b){} public void f(int a,int b,int c,int d,int e,int f,int g,int h,int i,int j){}')
        self.assertTrue(check(o,consumer('"f/10","f/2"'))['matches'])
        self.assertEqual('order_mismatch',check(o,consumer('"f/2","f/10"'))['checks'][0]['finding'])

    def test_comments_strings_textblocks_do_not_mint_assertions(self):
        t=consumer().decode().replace('void check() {','void check() { String x="getDeclaredMethods assertEquals"; String y="""\n fake List.of(\\"bad/0\\")\n"""; /* fake Owner.class.getDeclaredMethods() */')
        self.assertTrue(check(t=t.encode())['matches'])

    def test_nonliteral_escaped_and_computed_expected_are_incomplete(self):
        for expected in ['names','"resolve/3"+"x"','"resolve\\u002f3"','"resolve\\0573"']:
            with self.subTest(expected=expected):
                try:r=check(t=consumer(expected))
                except census.CensusError as error:self.assertEqual(3,error.code)
                else:self.assertFalse(r['complete']);self.assertIsNone(r['matches'])

    def test_custom_comparator_aliases_getMethods_and_distinct_are_incomplete(self):
        original=('Arrays.stream(Owner.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers()))'
                  '.map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList()')
        for expression in [original.replace('.sorted()','.sorted(custom)'),original.replace('getDeclaredMethods','getMethods'),
                           original.replace('.sorted()','.distinct().sorted()'),original.replace('Arrays.stream(Owner.class.getDeclaredMethods())','methods.stream()')]:
            with self.subTest(expression=expression):
                r=check(t=consumer(expression=expression));self.assertFalse(r['complete']);self.assertIsNone(r['matches'])

    def test_record_interface_enum_generic_inherited_are_incomplete(self):
        for o in [owner(header='class Owner<T>'),owner(header='class Owner extends Base'),owner(header='class Owner implements I'),
                  owner(header='record Owner()'),owner(header='interface Owner'),owner('A; public void f(){}',header='enum Owner'),
                  owner('public <T> T f(T x){return x;}')]:
            with self.subTest(owner=o):r=check(o);self.assertFalse(r['complete']);self.assertIsNone(r['matches'])

    def test_unresolved_import_scopes_keep_partial_mismatch_visible(self):
        for imports in [IMPORTS+' import static custom.Helpers.*;',IMPORTS+' import unknown.*;',
                        IMPORTS.replace('import java.util.List;','import java.util.*;')]:
            with self.subTest(imports=imports):
                r=check(t=consumer('"resolve/3"',imports=imports));self.assertFalse(r['complete']);self.assertIsNone(r['matches'])
                self.assertFalse(r['checks'][0]['observed_matches']);self.assertEqual(1,len(r['findings']))

    def test_fully_qualified_supported_roots(self):
        t=consumer().decode().replace('List.of','java.util.List.of').replace('Arrays.stream','java.util.Arrays.stream').replace('Modifier.isPublic','java.lang.reflect.Modifier.isPublic').replace('Owner.class','p.Owner.class').replace('assertEquals(','org.junit.jupiter.api.Assertions.assertEquals(')
        self.assertTrue(check(t=t.encode())['matches'])

    def test_owner_binding_is_explicit_mapping_not_resolution(self):
        r=check(t=consumer(imports=IMPORTS.replace('import p.Owner;','')))
        self.assertFalse(r['complete']);self.assertTrue(r['checks'][0]['observed_matches']);self.assertIn('caller-known',r['binding_basis'])

    def test_shadowed_helpers_and_inherited_consumer_are_incomplete(self):
        for t in [consumer().decode().replace('void check()','void check(int List)'),consumer(header='class Inventory extends Base').decode(),
                  consumer().decode().replace('void check()','void assertEquals()')]:
            with self.subTest(t=t):self.assertFalse(check(t=t.encode())['complete'])

    def test_multiple_selected_assertions_all_checked_other_owner_excluded(self):
        t=consumer().decode();statement=t[t.index('assertEquals('):t.rindex(';')+1]
        t=t.replace(statement,statement+statement.replace('"resolve/3","withAct3FutureGameOver/3"','"resolve/3"')+' assertEquals(List.of("alien/0"),Arrays.stream(Other.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());')
        r=check(t=t.encode());self.assertEqual(2,len(r['checks']));self.assertEqual(1,len(r['findings']))

    def test_no_selected_assertion_and_unanchored_reflection_are_incomplete(self):
        for t in [b'package q; class Inventory { void check(){} }',b'package q; class Inventory { void check(){Owner.class.getDeclaredMethods();} }']:
            with self.subTest(t=t):self.assertFalse(check(t=t)['complete'])

    def test_context_token_declaration_and_file_bounds(self):
        for bounds in [dict(max_tokens=10),dict(max_context_tokens=15),dict(max_declarations=1),dict(max_file_bytes=10)]:
            with self.subTest(bounds=bounds),self.assertRaises(census.CensusError) as e:check(**bounds)
            self.assertEqual(3,e.exception.code)

    def test_enclosing_generic_and_named_static_scopes_are_incomplete(self):
        nested=consumer().decode().replace('class Inventory {','class Outer<T> { class Inventory {')+'}'
        self.assertFalse(check(t=nested.encode())['complete'])
        self.assertFalse(check(t=consumer(imports=IMPORTS+' import static other.Owner.List;'))['complete'])

    def test_separator_expression_and_public_filter_must_be_exact(self):
        actual=('Arrays.stream(Owner.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers()))'
                '.map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList()')
        for expression in [actual.replace('isPublic','isStatic'),actual.replace('"/"','separator'),actual.replace('getName()','getName().toLowerCase()')]:
            with self.subTest(expression=expression):self.assertFalse(check(t=consumer(expression=expression))['complete'])

    def test_qualified_package_root_value_cannot_claim_clean_binding(self):
        t=consumer().decode().replace('Owner.class','p.Owner.class').replace('void check() {','void check() { int p=0;')
        self.assertFalse(check(t=t.encode())['complete'])

    def test_extra_literal_and_empty_public_class(self):
        r=check(t=consumer('"ghost/0","resolve/3","withAct3FutureGameOver/3"'))
        self.assertEqual(['ghost/0'],r['checks'][0]['extra_in_literal'])
        self.assertTrue(check(owner('private void secret(){}'),consumer(''))['matches'])

    def test_aggregate_output_limit_is_bounded(self):
        o=owner(''.join('public void f'+str(i)+'(){}' for i in range(20)))
        with self.assertRaises(census.CensusError) as e:check(o,consumer('"f0/0"'),max_declarations=25)
        self.assertEqual(3,e.exception.code)


    def test_enhanced_for_sensitive_shadow_is_incomplete(self):
        t=consumer().decode().replace('void check() {','void check(Factory[] factories) { for (Factory List : factories) {').replace(' } }',' } } }')
        r=check(t=t.encode());self.assertFalse(r['complete']);self.assertIsNone(r['matches'])

    def test_parenthesized_selected_literal_beside_supported_assertion_is_incomplete(self):
        t=consumer().decode();statement=t[t.index('assertEquals('):t.rindex(';')+1]
        extra=statement.replace('Owner.class.getDeclaredMethods()','(Owner.class).getDeclaredMethods()')
        r=check(t=t.replace(statement,statement+extra).encode());self.assertFalse(r['complete']);self.assertIsNone(r['matches'])

    def test_extra_members_reserve_both_output_row_copies(self):
        expected=','.join('"ghost'+str(i)+'/0"' for i in range(20))
        with self.assertRaises(census.CensusError) as e:check(owner(''),consumer(expected),max_declarations=21)
        self.assertEqual(3,e.exception.code)

    def test_extra_members_reserve_both_output_byte_copies(self):
        expected=','.join('"'+'X'*3500+str(i)+'/0"' for i in range(100))
        o,t=owner(''),consumer(expected)
        with self.assertRaises(census.CensusError) as e:check(o,t,max_total_bytes=len(o)+len(t)+5000)
        self.assertEqual(3,e.exception.code)

class CliContracts(unittest.TestCase):
    def setUp(self):
        self.o=owner();self.t=consumer();self.final_owner=None;self.final_test=None
        self.reads=[]
        def baseline(git,rev,path,cap):
            self.reads.append((path,cap));data=self.o if path=='src/Owner.java' else self.t
            census.require(len(data)<=cap,'Baseline byte budget exceeded before read')
            return data,dict(path=path,bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
        def final(path,cap):
            data=self.final_owner if str(path).endswith('Owner.java') else self.final_test
            census.require(data is not None and len(data)<=cap,'FINAL byte budget exceeded')
            return data,('immutable-test-stat',hashlib.sha256(data).hexdigest())
        for name,value in [('read_baseline',baseline),('read_final',final),('directory_id',lambda path:('directory',))]:
            patch=mock.patch.object(census,name,value);patch.start();self.addCleanup(patch.stop)
    def args(self,*extra):
        return ['--git-dir','/owned/git','--revision','a'*40,'--source','src/Owner.java',*extra]
    def cli(self,*extra):
        out=io.StringIO()
        with contextlib.redirect_stdout(out):code=census.main(self.args(*extra))
        return code,json.loads(out.getvalue())
    def opts(self):return ['--public-method-inventory','p.Owner','--inventory-test-source','test/Inventory.java']
    def test_old_default_surface_has_no_inventory_keys_or_changes(self):
        code,r=self.cli();self.assertEqual(0,code);self.assertNotIn('public_method_inventory',r);self.assertEqual({'version','status','limits','limitations','baseline'},set(r))
    def test_complete_cli_match_member_and_order_exit_codes(self):
        self.assertEqual(0,self.cli(*self.opts())[0])
        for expected,kind in [('"resolve/3"','member_mismatch'),('"withAct3FutureGameOver/3","resolve/3"','order_mismatch')]:
            self.t=consumer(expected);code,r=self.cli(*self.opts());self.assertEqual(1,code);self.assertEqual(kind,r['public_method_inventory']['findings'][0]['kind'])
    def test_incomplete_precedes_inventory_mismatch_and_source_diff_remains(self):
        self.final_owner=owner('public void resolve(int a,int b,int c){} public void added(){}',header='class Owner implements I')
        extra=['--final','/owned/Owner.java','--final-bytes',str(len(self.final_owner)),'--final-sha256',hashlib.sha256(self.final_owner).hexdigest()]
        code,r=self.cli(*self.opts(),*extra);self.assertEqual(3,code);self.assertTrue(r['diff']['has_changes']);self.assertIsNone(r['public_method_inventory']['matches']);self.assertTrue(r['public_method_inventory']['findings'])
    def test_optional_final_test_pins_and_selected_side(self):
        self.t=consumer('"resolve/3"');self.final_test=consumer()
        extra=['--inventory-test-final','/owned/Inventory.java','--inventory-test-final-bytes',str(len(self.final_test)),'--inventory-test-final-sha256',hashlib.sha256(self.final_test).hexdigest()]
        code,r=self.cli(*self.opts(),*extra);self.assertEqual(0,code);self.assertEqual('final',r['public_method_inventory']['test_source']);self.assertIn('final',r['inventory_test'])
        bad=extra.copy();bad[-1]='0'*64;self.assertEqual(2,self.cli(*self.opts(),*bad)[0])
    def test_orphan_mode_and_incompatible_flags_reject(self):
        for flags in [('--inventory-test-source','test/Inventory.java'),('--public-method-inventory','p.Owner'),
                      (*self.opts(),'--test-inventory','p.Owner'),(*self.opts(),'--inventory-test-final-bytes','1'),
                      (*self.opts(),'--inventory-test-final','/owned/Inventory.java'),('--public-method-inventory','bad/Owner','--inventory-test-source','test/I.java')]:
            with self.subTest(flags=flags):self.assertEqual(2,self.cli(*flags)[0])
    def test_combined_budget_reserves_final_test_before_read(self):
        self.final_test=consumer();extra=['--inventory-test-final','/owned/Inventory.java','--inventory-test-final-bytes',str(len(self.final_test)),'--inventory-test-final-sha256',hashlib.sha256(self.final_test).hexdigest(),'--max-total-bytes',str(len(self.o)+len(self.final_test)-1)]
        self.assertEqual(3,self.cli(*self.opts(),*extra)[0]);self.assertFalse(any(path=='test/Inventory.java' for path,cap in self.reads))
    def test_complete_source_diff_still_exits_one_when_inventory_matches(self):
        self.final_owner=owner('public void resolve(int a,int b,int c){} public void added(){}')
        self.t=consumer('"added/0","resolve/3"')
        extra=['--final','/owned/Owner.java','--final-bytes',str(len(self.final_owner)),'--final-sha256',hashlib.sha256(self.final_owner).hexdigest()]
        code,r=self.cli(*self.opts(),*extra)
        self.assertEqual(1,code);self.assertTrue(r['diff']['has_changes']);self.assertTrue(r['public_method_inventory']['matches'])

    def test_final_test_reobservation_changed_identity_is_incomplete(self):
        self.final_test=consumer();original=census.read_final;count=0
        def changing(path,cap):
            nonlocal count
            data,sig=original(path,cap);count+=1
            return data,('changed',) if count==2 else sig
        extra=['--inventory-test-final','/owned/Inventory.java','--inventory-test-final-bytes',str(len(self.final_test)),'--inventory-test-final-sha256',hashlib.sha256(self.final_test).hexdigest()]
        with mock.patch.object(census,'read_final',changing):self.assertEqual(3,self.cli(*self.opts(),*extra)[0])

    def test_added_test_recheck_fails_closed(self):
        original=census.read_baseline;count=0
        def changing(git,rev,path,cap):
            nonlocal count
            data,p=original(git,rev,path,cap)
            if path=='test/Inventory.java':
                count+=1
                if count==2:return data+b' ',p
            return data,p
        with mock.patch.object(census,'read_baseline',changing):self.assertEqual(3,self.cli(*self.opts())[0])


    def test_consumer_final_growth_recheck_keeps_remaining_pre_read_cap(self):
        self.final_test=consumer();caps=[];payload=self.final_test
        def growing(path,cap):
            caps.append(cap);data=payload+(b' '*6000 if len(caps)>1 else b'')
            census.require(len(data)<=cap,'FINAL grew beyond its allowed pre-read cap')
            return data,('signature',hashlib.sha256(data).hexdigest())
        extra=['--inventory-test-final','/owned/Inventory.java','--inventory-test-final-bytes',str(len(payload)),'--inventory-test-final-sha256',hashlib.sha256(payload).hexdigest(),'--max-total-bytes','5000']
        with mock.patch.object(census,'read_final',growing):self.assertEqual(3,self.cli(*self.opts(),*extra)[0])
        self.assertEqual(2,len(caps));self.assertLessEqual(caps[1],5000-len(self.o)-len(self.t))

if __name__=='__main__':unittest.main()
