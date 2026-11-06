"""ROM-free regression coverage for immutable source and file-lifetime generation."""
import copy
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from generate import Git, build, compact_history, extract_history, extract_lifetimes, scan
from model import compact_types, component, parse_java, source_path


class JavaInventoryTests(unittest.TestCase):
    def test_comments_strings_and_text_blocks_do_not_declare_types(self):
        source = '''package demo;
        // class Phantom {}
        public class Visible {
          String text = "class Pretend {}";
          String block = """\ninterface Imaginary {}\n""";
          record State(int value) {}
          enum Mode { READY }
          interface Port {}
          void run() { class Local {} }
        }
        @interface Marker {}
        '''
        package, types, imports = parse_java(source)
        self.assertEqual('demo', package)
        self.assertEqual([['Visible',0,1],['State',3,0],['Mode',2,0],['Port',1,0],['Local',0,0],['Marker',4,1]], types)
        self.assertEqual([], imports)

    def test_declaration_compression_is_lossless(self):
        original = [['Widget',0,1],['State',3,0],['Other',2,1],['Marker',4,0]]
        record = ['Widget',0,0,0,0,1,copy.deepcopy(original),[], '']
        compact_types(record)
        decoded = [[record[0],t,1] if isinstance(t,int) else [t[1:],int(t[0],36)%8,int(int(t[0],36)>=8)] for t in record[6]]
        self.assertEqual(original, decoded)

    def test_resource_java_package_is_not_a_resource_fixture(self):
        self.assertEqual(2, component('src/main/java/com/openggf/game/sonic3k/resources/Art.java', 'com.openggf.game.sonic3k.resources'))
        self.assertEqual(4, component('src/main/java/com/openggf/level/resources/Art.java', 'com.openggf.level.resources'))
        self.assertEqual(24, component('src/test/resources/mods/Sample.java', 'demo'))


class GitHistoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp.name)/'repo'
        self.repo.mkdir()
        self.git = Git(self.repo)
        self.command('init','-q')
        self.command('config','user.name','Atlas fixture')
        self.command('config','user.email','fixture@example.invalid')
        self.command('config','commit.gpgsign','false')
        self.command('config','core.hooksPath',str(Path(self.temp.name)/'no-hooks'))
        self.seed = 'src/main/java/demo/Seed.java'
        self.future = 'src/main/java/demo/Future.java'
        self.renamed = 'src/main/java/demo/Renamed.java'
        self.old = 'src/main/java/demo/OldOnly.java'
        self.write(self.seed,'package demo; public class Seed {}\n')
        self.refs = [self.commit('origin',1)]
        self.write(self.future,'package demo; public class Future { record State(int n) {} }\n')
        self.refs.append(self.commit('add future',2))
        (self.repo/self.seed).unlink()
        self.command('mv',self.future,self.renamed)
        self.write(self.old,'package demo; public class OldOnly {}\n')
        self.refs.append(self.commit('remove, rename, add',3))
        self.write(self.seed,'package demo; public class Seed { interface Port {} }\n')
        (self.repo/self.old).unlink()
        self.refs.append(self.commit('reintroduce and retire',4))

    def tearDown(self):
        self.temp.cleanup()

    def command(self,*args,**kwargs):
        return subprocess.check_output(['git',*args],cwd=self.repo,stderr=subprocess.PIPE,**kwargs).decode().strip()

    def write(self,path,text):
        file = self.repo/path
        file.parent.mkdir(parents=True,exist_ok=True)
        file.write_text(text)

    def commit(self,message,day):
        self.command('add','-A')
        env = dict(os.environ,GIT_AUTHOR_DATE=f'2024-01-{day:02}T12:00:00Z',GIT_COMMITTER_DATE=f'2024-01-{day:02}T12:00:00Z')
        self.command('commit','-qm',message,env=env)
        return self.command('rev-parse','HEAD')

    def test_scan_reads_pinned_blobs_and_ignores_dirty_untracked_files(self):
        self.write(self.seed,'not the committed source')
        self.write('src/main/java/demo/Untracked.java','class Untracked {}')
        inventory = scan(self.git,self.refs[-1],'fixture')
        self.assertEqual(2,inventory['stats']['files'])
        self.assertEqual(4,inventory['stats']['types'])
        self.assertEqual(2,inventory['stats']['lines'])
        self.assertNotIn('Untracked',[r[0] for r in inventory['files']])

    def test_lifetimes_cover_deletion_reintroduction_and_path_moves(self):
        inventory = scan(self.git,self.refs[-1],'fixture')
        temporal = extract_lifetimes(self.git,self.refs[-1],inventory)
        packages = inventory['packages']+temporal['packages']
        roots = inventory['roots']+temporal['roots']
        records = inventory['files']+temporal['removed']
        spans = {source_path(r,packages,roots):s for r,s in zip(records,temporal['lifetimes'])}
        self.assertEqual([0,2,3,4],spans[self.seed])
        self.assertEqual([1,2],spans[self.future])
        self.assertEqual([2,4],spans[self.renamed])
        self.assertEqual([2,3],spans[self.old])
        self.assertEqual(2,len(temporal['removed']))
        self.assertEqual(4,len(temporal['steps']))
        self.assertEqual(3,temporal['steps'][-1][0])
        self.assertEqual(self.refs[-1][:12],temporal['steps'][-1][2])
        self.assertEqual(4,len(temporal['checked']))

    def test_diff_totals_reconcile_and_daily_counts_do_not_drop_commits(self):
        inventory = scan(self.git,self.refs[-1],'fixture')
        history = extract_history(self.git,self.refs[-1],inventory)
        inline = compact_history(history)
        self.assertEqual(4,history['firstParentCommits'])
        self.assertEqual(inventory['stats']['lines'],history['totals'][1][0]-history['totals'][1][1])
        self.assertEqual(4,sum(r[1] for r in inline['daily']))
        self.assertEqual(inline['totals'],history['totals'])

    def namespace_move(self, new_body='package com.openggf; public class Engine {}\n', old_body='package uk.co.jamesj999.sonic; public class Engine {}\n'):
        old='src/main/java/uk/co/jamesj999/sonic/Engine.java'
        new='src/main/java/com/openggf/Engine.java'
        self.write(old,old_body)
        before=self.commit('old namespace',5)
        (self.repo/new).parent.mkdir(parents=True,exist_ok=True)
        self.command('mv',old,new)
        self.write(new,new_body)
        after=self.commit('namespace migration',6)
        return old,new,before,after

    def test_namespace_move_preserves_one_current_identity_across_boundary(self):
        old,new,before,after=self.namespace_move()
        inventory=scan(self.git,after,'fixture')
        temporal=extract_lifetimes(self.git,after,inventory)
        names=[source_path(r,inventory['packages'],inventory['roots']) for r in inventory['files']]
        fid=names.index(new)
        self.assertEqual([4,6],temporal['lifetimes'][fid])
        aliases=dict(temporal['aliases'])
        self.assertEqual([[old,[4,5]]],aliases[fid])
        all_packages=inventory['packages']+temporal['packages'];all_roots=inventory['roots']+temporal['roots']
        self.assertNotIn(old,[source_path(r,all_packages,all_roots) for r in temporal['removed']])
        self.assertIn(4,[s[0] for s in temporal['steps']])
        self.assertIn(5,[s[0] for s in temporal['steps']])

    def test_namespace_move_followed_by_deletion_remains_one_historical_item(self):
        old,new,before,after=self.namespace_move()
        (self.repo/new).unlink();pin=self.commit('retire migrated class',7)
        inventory=scan(self.git,pin,'fixture');temporal=extract_lifetimes(self.git,pin,inventory)
        packages=inventory['packages']+temporal['packages'];roots=inventory['roots']+temporal['roots']
        names=[source_path(r,packages,roots) for r in inventory['files']+temporal['removed']]
        self.assertNotIn(old,names)
        self.assertEqual([4,6],temporal['lifetimes'][names.index(new)])

    def test_namespace_pair_with_different_declared_type_is_not_joined(self):
        old,new,before,after=self.namespace_move('package com.openggf; public interface Engine {}\n')
        inventory=scan(self.git,after,'fixture');temporal=extract_lifetimes(self.git,after,inventory)
        self.assertEqual([],temporal['aliases'])

    def test_record_to_class_conversion_during_namespace_move_keeps_identity(self):
        old,new,before,after=self.namespace_move(old_body='package uk.co.jamesj999.sonic; public record Engine(int value) {}\n')
        inventory=scan(self.git,after,'fixture');temporal=extract_lifetimes(self.git,after,inventory)
        names=[source_path(r,inventory['packages'],inventory['roots']) for r in inventory['files']]
        self.assertEqual([4,6],temporal['lifetimes'][names.index(new)])

    def test_namespace_shaped_move_without_package_change_is_not_joined(self):
        old,new,before,after=self.namespace_move('package uk.co.jamesj999.sonic; public class Engine {}\n')
        inventory=scan(self.git,after,'fixture');temporal=extract_lifetimes(self.git,after,inventory)
        self.assertEqual([],temporal['aliases'])

    def test_overlapping_legacy_copy_is_not_collapsed_into_current_class(self):
        old,new,before,after=self.namespace_move()
        self.write(old,'package uk.co.jamesj999.sonic; public class Engine {}\n')
        pin=self.commit('retain independent compatibility copy',7)
        inventory=scan(self.git,pin,'fixture');temporal=extract_lifetimes(self.git,pin,inventory)
        self.assertEqual([],temporal['aliases'])

    def test_feature_files_first_exist_at_their_first_parent_merge(self):
        branch = self.command('branch','--show-current')
        self.command('checkout','-qb','feature',self.refs[0])
        feature = 'src/main/java/demo/BranchOnly.java'
        self.write(feature,'package demo; public class BranchOnly {}\n')
        self.commit('feature branch work',2)
        self.command('checkout','-q',branch)
        env = dict(os.environ,GIT_AUTHOR_DATE='2024-01-06T12:00:00Z',GIT_COMMITTER_DATE='2024-01-06T12:00:00Z')
        self.command('merge','--no-ff','-qm','integrate feature','feature',env=env)
        pin = self.command('rev-parse','HEAD')
        inventory = scan(self.git,pin,'fixture')
        history = extract_history(self.git,pin,inventory)
        temporal = extract_lifetimes(self.git,pin,inventory)
        self.assertEqual(5,history['firstParentCommits'])
        self.assertEqual(6,history['reachableCommits'])
        names = [source_path(r,inventory['packages'],inventory['roots']) for r in inventory['files']]
        self.assertEqual([4,5],temporal['lifetimes'][names.index(feature)])

    def test_build_emits_portable_html_and_leaves_the_checkout_unchanged(self):
        before = self.command('status','--porcelain')
        output = Path(self.temp.name)/'atlas.html'
        build(self.git,self.refs[-1],output,t3_limit=True)
        html = output.read_text()
        self.assertIn('id="source-time-slider"',html)
        self.assertNotIn('__TEMPORAL__',html)
        self.assertNotIn('__INVENTORY__',html)
        self.assertNotIn('/home/farrell',html)
        self.assertEqual(before,self.command('status','--porcelain'))


if __name__ == '__main__':
    unittest.main()
