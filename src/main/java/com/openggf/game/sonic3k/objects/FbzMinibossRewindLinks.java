package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.*;

/** Deterministic graph relinker for the two independent cyclic arm chains. */
final class FbzMinibossRewindLinks {
    private FbzMinibossRewindLinks() { }

    static FbzMinibossInstance boss(ObjectManager manager, int familySlot) {
        if (manager == null) return null;
        for (ObjectInstance object : manager.getActiveObjects()) {
            if (object instanceof FbzMinibossInstance candidate
                    && (familySlot < 0 || candidate.getSlotIndex() == familySlot)) return candidate;
        }
        return null;
    }
    static void settle(ObjectManager manager, int familySlot) {
        FbzMinibossInstance boss = boss(manager, familySlot);
        if (boss == null) return;
        FbzMinibossArmChild[] arms = new FbzMinibossArmChild[2];
        FbzMinibossChainLink[][] links = new FbzMinibossChainLink[2][5];
        for (ObjectInstance object : manager.getActiveObjects()) {
            if (object instanceof FbzMinibossAimerChild child && child.familySlot() == familySlot) {
                child.setBoss(boss);
            } else if (object instanceof FbzMinibossCoverChild child && child.familySlot() == familySlot) {
                child.setBoss(boss);
            } else if (object instanceof FbzMinibossPlungerChild child && child.familySlot() == familySlot) {
                child.setBoss(boss);
            } else if (object instanceof FbzMinibossPaletteChild child && child.familySlot() == familySlot) {
                child.setBoss(boss);
            } else if (object instanceof FbzMinibossExplosionController child
                    && child.familySlot() == familySlot) {
                child.setBoss(boss);
            } else if (object instanceof FbzMinibossPrisonChild child && child.familySlot() == familySlot) {
                child.setBoss(boss);
            } else if (object instanceof FbzMinibossAnimalChild child && child.familySlot() == familySlot) {
                child.setBoss(boss);
            } else if (object instanceof FbzMinibossFragmentChild child && child.familySlot() == familySlot) {
                child.setBoss(boss);
            } else if (object instanceof FbzMinibossArmChild arm && arm.familySlot() == familySlot) {
                arms[arm.side()] = arm;
            } else if (object instanceof FbzMinibossChainLink link && link.familySlot() == familySlot) {
                links[link.side()][link.linkIndex()] = link;
            }
        }
        settle(boss, arms, links);
    }
    static void settleForTest(FbzMinibossInstance boss, Object[] objects) {
        FbzMinibossArmChild[] arms = new FbzMinibossArmChild[2];
        FbzMinibossChainLink[][] links = new FbzMinibossChainLink[2][5];
        for (Object object : objects) {
            if (object instanceof FbzMinibossArmChild arm) arms[arm.side()] = arm;
            else if (object instanceof FbzMinibossChainLink link) links[link.side()][link.linkIndex()] = link;
        }
        settle(boss, arms, links);
    }
    private static void settle(FbzMinibossInstance boss, FbzMinibossArmChild[] arms,
                               FbzMinibossChainLink[][] links) {
        for (int side = 0; side < 2; side++) {
            FbzMinibossArmChild arm = arms[side];
            for (int index = 0; index < 5; index++) {
                FbzMinibossChainLink link = links[side][index];
                if (link != null) {
                    link.setBoss(boss);
                    link.setArm(arm);
                    link.setPrevious(null);
                    link.setNext(null);
                }
            }
            if (arm == null) continue;
            arm.setBoss(boss);
            boss.relinkArm(arm);
            FbzMinibossChainLink first = links[side][0];
            arm.setNext(first);
            FbzMinibossChainLink previous = null;
            for (int index = 0; index < 5; index++) {
                FbzMinibossChainLink link = links[side][index];
                if (link == null) break;
                link.setPrevious(previous == null ? arm : previous);
                link.setArm(arm);
                if (previous != null) previous.setNext(link);
                previous = link;
            }
            arm.setTerminal(previous);
            if (previous != null) previous.setNext(previous.linkIndex() == 4 ? arm : null);
        }
    }
}
