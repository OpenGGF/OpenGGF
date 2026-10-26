"""ROM-free controls for the external source-event checker, 2026-10-24."""
import copy
import unittest
from charge_reference_check import episodes, release_kind


def sample(q, frame, *, hold=1, tap=0, inertia=0):
    return dict(frame=frame,active_player=True,fields=dict(oPlayerCharge=q,oFlags=0,oXRadius=9,oYRadius=19),globals=dict(playerCtrlHold=hold,p1CtrlHold=hold,playerCtrlTap=tap,p1CtrlTap=tap,sonicTopSpeed=1536),x_16_16_unsigned=64*65536,y_16_16_unsigned=1898*65536,centre_y=1898,inertia_s16=inertia,x_velocity_s16=0,y_velocity_s16=0)


def early():
    return [sample(0,1),sample(1,2,tap=64),sample(2,3,inertia=100),sample(0,4,hold=0)]


class Controls(unittest.TestCase):
    def test_release_threshold_is_before_dispatch_increment(self):
        self.assertEqual(release_kind(28,30),'early')
        self.assertEqual(release_kind(29,30),'mature')
        self.assertEqual(release_kind(43,45),'early')
        self.assertEqual(release_kind(44,45),'mature')

    def test_valid_early_cancellation(self):
        self.assertEqual(episodes(early(),'peelout')[0]['kind'],'early')

    def test_missing_zero_or_start_refused(self):
        with self.assertRaisesRegex(ValueError,'pre-charge'):
            episodes(early()[1:],'peelout')

    def test_skipped_counter_refused(self):
        r=early();r[2]['fields']['oPlayerCharge']=3
        with self.assertRaisesRegex(ValueError,'counter'):
            episodes(r,'peelout')

    def test_native_velocity_is_not_inertia(self):
        r=early();r[-1]['x_velocity_s16']=1
        with self.assertRaisesRegex(ValueError,'velocity'):
            episodes(r,'peelout')

    def test_latched_input_mismatch_refused(self):
        r=early();r[1]['globals']['p1CtrlTap']=0
        with self.assertRaisesRegex(ValueError,'latch'):
            episodes(r,'peelout')

    def test_cancellation_latched_input_mismatch_refused(self):
        for key,value in [('p1CtrlHold',1),('p1CtrlTap',64)]:
            with self.subTest(key=key):
                r=early();r[-1]['globals'][key]=value
                with self.assertRaisesRegex(ValueError,'latch'):
                    episodes(r,'peelout')

    def test_movement_or_radius_changes_refused(self):
        for change in ['position','radius']:
            r=copy.deepcopy(early())
            if change=='position':r[2]['x_16_16_unsigned']+=1
            else:r[2]['fields']['oXRadius']=7
            with self.assertRaises(ValueError):episodes(r,'peelout')

    def test_unfinished_charge_not_success(self):
        with self.assertRaisesRegex(ValueError,'Unfinished'):
            episodes(early()[:-1],'peelout')

    def test_lost_actor_not_cancellation(self):
        r=early();r[-1]['active_player']=False
        with self.assertRaisesRegex(ValueError,'qualification'):
            episodes(r,'peelout')

def mature_spin():
    stream=[sample(0,1,hold=2)]
    for i in range(1,48):
        r=sample(min(i,45),i+1,hold=2,tap=64 if i==1 else 0,inertia=min(512+(i-1)*75,3072))
        r['fields'].update(oFlags=4,oXRadius=7,oYRadius=14)
        stream.append(r)
    r=sample(0,49,hold=0,inertia=3066)
    r['fields'].update(oFlags=4,oXRadius=7,oYRadius=14);r['x_velocity_s16']=3042
    r['x_16_16_unsigned']+=3042*256
    stream.append(r)
    return stream


class SpinControls(unittest.TestCase):
    def test_spindash_44_45_saturation_and_rolling_release(self):
        e=episodes(mature_spin(),'spindash')[0]
        self.assertEqual(e['kind'],'mature')
        self.assertEqual(e['first_near_cap']['fields']['oPlayerCharge'],44)
        self.assertEqual(e['first_at_cap']['fields']['oPlayerCharge'],45)
        self.assertEqual(e['maximum_observed_charge'],45)

    def test_overcap_native_counter_refused(self):
        r=mature_spin();r[-2]['fields']['oPlayerCharge']=46
        with self.assertRaisesRegex(ValueError,'counter'):episodes(r,'spindash')

    def test_charge_and_xy_velocity_are_distinct(self):
        r=mature_spin()
        for x in r[1:-1]:x['x_velocity_s16']=495;x['y_velocity_s16']=-49
        self.assertEqual(episodes(r,'spindash')[0]['kind'],'mature')

    def test_mature_counter_without_launch_refused(self):
        r=mature_spin();r[-1]['x_velocity_s16']=0
        with self.assertRaisesRegex(ValueError,'launch'):episodes(r,'spindash')

    def test_stored_velocity_without_postrelease_displacement_refused(self):
        r=mature_spin()
        for axis in ['x','y']:r[-1][axis+'_16_16_unsigned']=r[-2][axis+'_16_16_unsigned']
        r[-1].update(inertia_s16=3072,x_velocity_s16=495,y_velocity_s16=-49)
        for _ in range(79):
            tail=copy.deepcopy(r[-1]);tail['frame']+=1;r.append(tail)
        with self.assertRaisesRegex(ValueError,'Mature release did not move'):
            episodes(r,'spindash')

    def test_postrelease_motion_may_be_delayed_and_fractional(self):
        for axis in ['x','y']:
            with self.subTest(axis=axis):
                r=mature_spin()
                r[-1]['x_16_16_unsigned']=r[-2]['x_16_16_unsigned']
                for _ in range(79):
                    tail=copy.deepcopy(r[-1]);tail['frame']+=1;r.append(tail)
                r[-1][axis+'_16_16_unsigned']+=1
                self.assertEqual(episodes(r,'spindash')[0]['kind'],'mature')

    def test_inactive_displacement_is_not_launch_evidence(self):
        r=mature_spin();r[-1]['x_16_16_unsigned']=r[-2]['x_16_16_unsigned']
        tail=copy.deepcopy(r[-1]);tail.update(frame=50,active_player=False)
        tail['x_16_16_unsigned']+=65536;r.append(tail)
        with self.assertRaisesRegex(ValueError,'Mature release did not move'):
            episodes(r,'spindash')

    def test_later_charge_cannot_supply_launch_displacement(self):
        r=mature_spin();r[-1]['x_16_16_unsigned']=r[-2]['x_16_16_unsigned']
        later=mature_spin()
        for row in later:
            row['frame']+=49;row['x_16_16_unsigned']+=65536
        later[0]['x_16_16_unsigned']=r[-1]['x_16_16_unsigned']
        r.extend(later)
        with self.assertRaisesRegex(ValueError,'Mature release did not move'):
            episodes(r,'spindash')

    def test_release_latched_input_mismatch_refused(self):
        for key,value in [('p1CtrlHold',2),('p1CtrlTap',64)]:
            with self.subTest(key=key):
                r=mature_spin();r[-1]['globals'][key]=value
                with self.assertRaisesRegex(ValueError,'latch'):
                    episodes(r,'spindash')

    def test_raw_and_logical_held_is_not_release(self):
        r=mature_spin();r[-1]['globals'].update(p1CtrlHold=2,playerCtrlHold=2)
        with self.assertRaisesRegex(ValueError,'ordinary release'):
            episodes(r,'spindash')

if __name__=='__main__':unittest.main()
