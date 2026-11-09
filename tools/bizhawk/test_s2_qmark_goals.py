"""Negative controls for cold monitor outcome qualification; no emulator needed."""
import copy
import unittest
from check_s2_qmark_goals import analyse

class QmarkGoals(unittest.TestCase):
    def samples(self):
        frames=[dict(frame=i,mode=12,lfc=i,lag=0,two_player=1) for i in range(1,411)]
        obs=[dict(frame=i,lfc=i,content=0,shell_routine=6 if i>=10 else 2,parent=0xb000,
            icon_x=960,items=0,p1_timer=1,p2_timer=1,p1_monitors=int(i>=45),p1_lives=3+int(i>=45))
            for i in range(1,411)]
        obs[16].update(content=1,lfc=16)
        return frames,obs
    def test_named_life_and_full_hold(self):
        f,o=self.samples();self.assertTrue(analyse(f,o,1)['passed'])
    def test_wrong_phase_lag_and_short_hold_are_rejected(self):
        f,o=self.samples()
        for change in ('phase','lag','hold'):
            a,b=copy.deepcopy((f,o))
            if change=='phase': b[16]['lfc']=17
            if change=='lag': a[8]['lag']=1
            if change=='hold': a=a[:200];b=b[:200]
            with self.assertRaises(ValueError): analyse(a,b,1)
    def test_named_life_owner_cannot_be_substituted(self):
        f,o=self.samples()
        for r in o:r['p1_lives']=3
        with self.assertRaises(StopIteration):analyse(f,o,1)
if __name__=='__main__':unittest.main()
