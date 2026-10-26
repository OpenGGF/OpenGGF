"""Portable ROM-free negative controls for player-reference-r1."""
import copy
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import player_reference as reference
from player_reference import check_schedule, new_external
from player_reference_check import check_av, field_check, summary_check

class ReferenceGuards(unittest.TestCase):
    def setUp(self):
        self.scenario=dict(frames=1,inputs=[dict(first=1,last=1,button='A')],screenshots=[1])
        self.av=[dict(frame=1,input=['P1 A'],audio_samples=735,audio_sha256='a'*64,video_sha256='b'*64)]
        self.cfg=dict(phase='postframe;exact_unknown',windows=[dict(name='header',sha256='c'*64)],object_fields={
            'oID':0,'oMap':4,'oX':8,'oXSub':10,'oY':12,'oYSub':14,'oXVel':16,'oYVel':18,'oPlayerGVel':20,'oRoutine':36,'oPlayerCtrl':44},map_pointer=0x210100)
        b=bytearray(64);b[0]=1;b[4:8]=(0x210100).to_bytes(4,'big');b[8:12]=bytes.fromhex('00401234');b[12:16]=bytes.fromhex('076b5678');b[16:18]=bytes.fromhex('ff80');b[36]=2;b[44]=0x80;b[45]=0x12
        f={n:int.from_bytes(b[o:o+(4 if n=='oMap' else 1 if n in ('oID','oRoutine','oPlayerCtrl') else 2)],'big') for n,o in self.cfg['object_fields'].items()}
        self.row=dict(frame=1,phase=self.cfg['phase'],accepted=True,active_player=True,identity_window_sha256={'header':'c'*64},player_raw_hex=b.hex(),fields=f,
            globals=dict(paused=0,demoMode=0,debugMode=0,ctrlLocked=0),centre_x=64,centre_y=1899,x_velocity_s16=-128,y_velocity_s16=0,inertia_s16=0,x_16_16_unsigned=0x401234,y_16_16_unsigned=0x76b5678)
    def test_valid(self):
        check_schedule(self.scenario);check_av(self.av,self.scenario,1,1);field_check(self.row,self.cfg)
    def test_total_calls(self):
        for frames in [0,5761,6000]:
            s=copy.deepcopy(self.scenario);s['frames']=frames
            with self.assertRaises(ValueError):check_schedule(s)
    def test_unknown_button(self):
        self.scenario['inputs'][0]['button']='Cheat'
        with self.assertRaises(ValueError):check_schedule(self.scenario)
    def test_outside_input(self):
        self.scenario['inputs'][0]['last']=122
        with self.assertRaises(ValueError):check_schedule(self.scenario)
    def test_controller_mismatch(self):
        self.av[0]['input']=[]
        with self.assertRaises(ValueError):check_av(self.av,self.scenario,1,1)
    def test_missing_or_reordered_calls(self):
        for av in [[],self.av*2]:
            with self.assertRaises(ValueError):check_av(av,self.scenario,1,1)
    def test_wrong_program(self):
        self.row['identity_window_sha256']['header']='d'*64
        with self.assertRaises(ValueError):field_check(self.row,self.cfg)
    def test_wrong_actor(self):
        b=bytearray.fromhex(self.row['player_raw_hex']);b[0]=2;self.row['player_raw_hex']=b.hex();self.row['fields']['oID']=2
        with self.assertRaises(ValueError):field_check(self.row,self.cfg)
    def test_player_control_is_byte(self):
        self.row['fields']['oPlayerCtrl']=0x8012
        with self.assertRaises(ValueError):field_check(self.row,self.cfg)
    def test_swapped_endian(self):
        self.row['fields']['oX']=0x4000
        with self.assertRaises(ValueError):field_check(self.row,self.cfg)
    def test_lost_fraction(self):
        self.row['x_16_16_unsigned']=64*65536
        with self.assertRaises(ValueError):field_check(self.row,self.cfg)
    def test_exact_phase_mislabel(self):
        self.row['phase']='exact_VInt'
        with self.assertRaises(ValueError):field_check(self.row,self.cfg)
    def test_locked_player(self):
        self.row['globals']['ctrlLocked']=1
        with self.assertRaises(ValueError):field_check(self.row,self.cfg)
    def test_unknown_identity_active(self):
        self.row['accepted']=False
        with self.assertRaises(ValueError):field_check(self.row,self.cfg)
    def test_restore_and_raw_state_contract(self):
        s=dict(frames=1,total_frameadvance_calls=241,restore_120_equal=True,observation_savestate_unchanged=True,
               deterministic=True,is_mega_cd=True,mode='observed-1',phase=self.cfg['phase'],raw_final_states_equal=False,
               final_state_sha256='a'*64,replay_final_state_sha256='b'*64)
        summary_check(s,self.cfg,self.scenario,'observed-1')
        for key,value in [('restore_120_equal',False),('observation_savestate_unchanged',False),('total_frameadvance_calls',6001),
                          ('raw_final_states_equal',True),('mode','control')]:
            bad=dict(s);bad[key]=value
            with self.assertRaises(ValueError):summary_check(bad,self.cfg,self.scenario,'observed-1')
    def test_protected_and_existing_outputs(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            with self.assertRaises(ValueError):new_external(root,[])
            with self.assertRaises(ValueError):new_external(root/'new',[root])
            real=root/'real';real.mkdir();(root/'alias').symlink_to(real)
            with self.assertRaises(ValueError):new_external(root/'alias'/'new',[real])
class RepositoryOutputGuards(unittest.TestCase):
    """Real Git metadata lives only in each test's external temporary fixture.

    Removing repository discovery must admit the main/sibling outputs; binding
    to an ancestor instead must reject the legitimate Downloads output.
    """
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='player reference guards ')
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name).resolve()
        self.env={k:v for k,v in os.environ.items() if not k.startswith('GIT_')}
        self.env.update(GIT_CONFIG_GLOBAL=os.devnull,GIT_CONFIG_SYSTEM=os.devnull,
                        GIT_CONFIG_NOSYSTEM='1',GIT_TERMINAL_PROMPT='0')
        self.outer=self.root/'unrelated ancestor'
        self.outer.mkdir()
        self.git(self.outer,'init','--template=')
        self.main=self.outer/'projects'/'engine checkout'
        self.main.mkdir(parents=True)
        self.git(self.main,'init','--template=')
        self.git(self.main,'-c','user.name=Guard fixture','-c','user.email=fixture@example.invalid',
                 '-c','core.hooksPath='+str(self.root/'no hooks'),'commit','--allow-empty','-m','fixture')
        self.linked=self.root/'linked producer checkout'
        self.other=self.root/'another linked checkout'
        for worktree in [self.linked,self.other]:
            self.git(self.main,'worktree','add','--detach',str(worktree),'HEAD')
        self.producer=self.linked/'tools'/'soniccd'
        self.producer.mkdir(parents=True)
        self.external=self.outer/'Downloads'
        self.external.mkdir()
        self.scenario=self.external/'scenario.json'
        self.scenario.write_text('{"frames":1,"inputs":[],"screenshots":[]}')

    def git(self,cwd,*args):
        return subprocess.run(['git','--no-optional-locks','-C',str(cwd),*args],env=self.env,
                              stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=10)

    def argv(self,output,producer=None):
        paths={'producer-directory':producer or self.producer,'source':self.external/'source',
               'inventory':self.external/'inventory.json','bizhawk-home':self.external/'runtime',
               'cue':self.external/'media'/'disc.cue','firmware-directory':self.external/'firmware',
               'scenario':self.scenario,'output':output,'bios-sha1':'0'*40}
        return ['player_reference.py',*[v for k,p in paths.items() for v in ('--'+k,str(p))]]

    def refuse(self,output,message='Output overlaps protected input',producer=None):
        # A guard refusal must precede source/media reads, compilation,
        # native execution, and every output-directory creation.
        with patch.object(sys,'argv',self.argv(output,producer)), \
             patch.object(reference,'prepare',side_effect=AssertionError('prepare reached')) as prepare, \
             patch.object(Path,'read_bytes',side_effect=AssertionError('media read reached')), \
             patch.object(Path,'mkdir',side_effect=AssertionError('output creation reached')):
            with self.assertRaisesRegex(ValueError,message):
                reference.main()
            prepare.assert_not_called()
        self.assertFalse(os.path.lexists(output))

    def test_main_checkout_target_refused_before_inputs(self):
        self.refuse(self.main/'target'/'player-reference-new')

    def test_other_linked_checkout_target_refused_before_inputs(self):
        self.refuse(self.other/'target'/'player-reference-new')

    def test_producer_checkout_refused_before_inputs(self):
        self.refuse(self.linked/'target'/'player-reference-new')

    def test_physical_output_symlink_parents(self):
        for name,target in [('main alias',self.main),('linked alias',self.other)]:
            with self.subTest(name=name):
                alias=self.external/name
                alias.symlink_to(target,target_is_directory=True)
                self.refuse(alias/'target'/'player-reference-new')

    def test_physical_producer_symlink_parents(self):
        alias=self.external/'producer alias'
        alias.symlink_to(self.producer,target_is_directory=True)
        self.refuse(self.main/'target'/'player-reference-new',producer=alias)

    def test_containing_ancestor_of_missing_registered_worktree(self):
        container=self.root/'missing registered container'
        missing=container/'registered checkout'
        self.git(self.main,'worktree','add','--detach',str(missing),'HEAD')
        shutil.rmtree(container)  # Keep its registration: no Git prune/repair.
        self.refuse(container)

    def test_external_downloads_under_unrelated_ancestral_git_allowed(self):
        output=self.external/'player-reference-new'
        with patch.object(sys,'argv',self.argv(output)), \
             patch.object(reference,'prepare',side_effect=RuntimeError('preflight accepted')) as prepare:
            with self.assertRaisesRegex(RuntimeError,'preflight accepted'):
                reference.main()
            prepare.assert_called_once()
        self.assertFalse(output.exists())

    def test_main_checkout_can_be_bound_producer(self):
        producer=self.main/'tools'/'soniccd'
        producer.mkdir(parents=True)
        self.refuse(self.other/'target'/'new',producer=producer)

    def test_unrelated_ancestor_cannot_supply_missing_checkout_metadata(self):
        # A loose producer inside another Git repository must not bind to it.
        loose=self.outer/'loose producer'/'tools'/'soniccd'
        loose.mkdir(parents=True)
        self.refuse(self.external/'new',message='Cannot bind producer checkout',producer=loose)

    def test_git_environment_cannot_redirect_repository(self):
        with patch.dict(os.environ,{'GIT_DIR':str(self.outer/'.git'),
                                    'GIT_WORK_TREE':str(self.outer),'GIT_COMMON_DIR':str(self.outer/'.git')}):
            self.refuse(self.main/'target'/'new')

    def test_git_missing_failure_and_timeout_fail_closed(self):
        for failure in [FileNotFoundError('git unavailable'),
                        subprocess.CalledProcessError(128,['git']),
                        subprocess.TimeoutExpired(['git'],10)]:
            with self.subTest(failure=type(failure).__name__), \
                 patch.object(reference.subprocess,'run',side_effect=failure):
                self.refuse(self.external/'new',message='Cannot discover producer repository')

    def test_incomplete_or_malformed_worktree_discovery_fails_closed(self):
        run=subprocess.run
        for listing in [b'',b'worktree relative\0\0',
                        b'worktree '+os.fsencode(self.main)+b'\0\0',
                        b'worktree '+os.fsencode(self.linked)+b'\0']:
            def discover(command,**kwargs):
                if command[-4:]==['worktree','list','--porcelain','-z']:
                    return subprocess.CompletedProcess(command,0,stdout=listing,stderr=b'')
                return run(command,**kwargs)
            with self.subTest(listing=listing),patch.object(reference.subprocess,'run',side_effect=discover):
                self.refuse(self.external/'new',message='Cannot discover producer repository')

    def test_git_top_level_must_match_bound_checkout(self):
        run=subprocess.run
        def discover(command,**kwargs):
            if command[-2:]==['rev-parse','--show-toplevel']:
                return subprocess.CompletedProcess(command,0,stdout=os.fsencode(self.outer)+b'\n',stderr=b'')
            return run(command,**kwargs)
        with patch.object(reference.subprocess,'run',side_effect=discover):
            self.refuse(self.external/'new',message='Cannot bind producer checkout')

    def test_existing_media_source_runtime_and_producer_guards_retained(self):
        for name in ['media','source','runtime','firmware']:
            with self.subTest(protected=name):
                self.refuse(self.external/name/'new')
        with patch.object(sys,'argv',self.argv(self.external)):
            with self.assertRaisesRegex(ValueError,'Output already exists'):
                reference.main()

    def test_tool_source_directory_remains_protected(self):
        # Never create anything beside the source, even in this external suite.
        self.refuse(Path(reference.__file__).parent/'guard-test-output-must-not-exist')

    def test_newline_and_spaces_in_registered_worktree_path(self):
        other=self.root/'space and\nnewline checkout'
        self.git(self.main,'worktree','add','--detach',str(other),'HEAD')
        self.refuse(other/'target'/'new')

    def test_discovery_commands_are_read_only_and_leave_metadata_unchanged(self):
        def metadata():
            return {str(p):p.read_bytes() for p in (self.main/'.git').rglob('*') if p.is_file()}
        before=metadata()
        run=subprocess.run
        def discover(command,**kwargs):
            self.assertEqual(command[:4],['git','--no-optional-locks','-C',str(self.linked)])
            self.assertIn(command[4:],[['rev-parse','--show-toplevel'],['worktree','list','--porcelain','-z']])
            self.assertEqual(kwargs['env']['GIT_OPTIONAL_LOCKS'],'0')
            self.assertFalse(any(k in kwargs['env'] for k in ('GIT_DIR','GIT_WORK_TREE','GIT_COMMON_DIR')))
            return run(command,**kwargs)
        with patch.object(reference.subprocess,'run',side_effect=discover):
            self.refuse(self.main/'target'/'new')
        self.assertEqual(metadata(),before)

if __name__=='__main__':unittest.main()
