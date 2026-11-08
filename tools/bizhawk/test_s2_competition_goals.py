import unittest
from check_s2_competition_goals import analyse

def state(i,**kw):
    return dict(frame=i,mode=12,zoneact=12<<8,two_player=1,lfc=i,vint=i,game_paused=0,
                zone_2p=2,act_2p=0,act1_result=65535,act2_result=65535,results=0,ss_stage=0,**kw)
def goal(i):
    return dict(frame=i,slot_inuse=65535,slot_visible=1,slot_player=1,slot_object_slot=109,
                slot_screen_x=144,slot_screen_y=162,flip_visible=1,player=1,pose=1,
                x=58,y=168,pixel_x=58,pixel_y=168,word=0xe3a0,art_address=0xe800)
class NativeGoals(unittest.TestCase):
    def test_lag_ends_comparison_but_does_not_reject_observed_goals(self):
        a,b=state(1),state(2);b['lfc']=a['lfc']
        result=analyse([a,b],[goal(1),goal(2)],'slot-flip')
        self.assertTrue(result['passed']);self.assertEqual(2,result['acceptedComparisonBoundary'])
    def test_counter_without_visible_cage_or_flat_art_cannot_pass(self):
        g=goal(1);g['slot_visible']=0;g['pose']=0
        result=analyse([state(1)],[g],'slot-flip')
        self.assertFalse(result['passed']);self.assertEqual(2,len(result['missing']))
        g=goal(1);g['pixel_x']=g['x']+8
        self.assertFalse(analyse([state(1)],[g],'slot-flip')['passed'])
    def test_zone_and_actual_act_win_tie_have_distinct_obligations(self):
        a,b,c=state(1),state(2),state(3)
        a['zoneact']=0;b['zoneact']=1
        c.update(mode=24,zone_2p=0,act_2p=2,act1_result=0x0500,act2_result=0x0005,results=1)
        self.assertTrue(analyse([a,b,c],[],'zone-results')['passed'])
        self.assertFalse(analyse([a,b,c],[],'tie-special')['passed'])
        c.update(mode=16,results=3,ss_stage=1)
        self.assertTrue(analyse([a,b,c],[],'tie-special')['passed'])
        c['act2_result']=0x0500
        self.assertFalse(analyse([a,b,c],[],'tie-special')['passed'])
if __name__=='__main__':unittest.main()
