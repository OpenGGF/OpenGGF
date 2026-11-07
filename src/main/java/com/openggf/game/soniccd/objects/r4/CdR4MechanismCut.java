package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import java.util.List;
import java.util.Objects;

/** Complete borrowed dynamic96 and explicit player-pair DATA. Never touches the originating pool.
 * Retain this staging cut on a reached fault: the already completed prefix must not be rolled back or replayed.
 * Root owns all live joins, full-state/environment validation, fallible preparation and atomic adoption.
 * Allocator delegates to the existing ID-only CdObjectPool kernel; no implicit clear on allocation. */
public final class CdR4MechanismCut {
    private final CdObjectPool allocation;
    private final CdObjectPool.Snapshot borrowedPool;
    private final CdR4MechanismImage[] dynamic = new CdR4MechanismImage[96];
    private final CdR4MechanismImage player, secondaryPlayer;
    private CdR4SwitchBank switches;
    public CdR4MechanismCut(CdObjectPool.Snapshot pool, byte[] primaryPlayer, byte[] secondaryPlayer, CdR4SwitchBank switches) {
        borrowedPool = Objects.requireNonNull(pool);
        allocation = CdObjectPool.stagingCopy(borrowedPool);
        for (CdObjectState s:pool.slots()) dynamic[s.identity().dynamicIndex()] = new CdR4MechanismImage(s.identity().wholeIndex(),s.copyBytes());
        player = new CdR4MechanismImage(0,primaryPlayer); this.secondaryPlayer = new CdR4MechanismImage(1,secondaryPlayer); this.switches = Objects.requireNonNull(switches);
    }
    public CdR4MechanismImage actor(int whole) { return whole==0 ? player : whole==1 ? secondaryPlayer : dynamic[Objects.checkIndex(whole-32,96)]; }
    public CdObjectPool.Snapshot borrowedPool() { return borrowedPool; }
    public List<CdR4MechanismImage> dynamicImages() { return List.of(dynamic); }
    public CdR4SwitchBank switches() { return switches; }
    void switchByte(int index,int value) { switches = switches.write(index,value); }
    /** WORD links are sign-extended native addresses, LONG links retain all bits; no stale-ID filtering. */
    CdR4MechanismImage linkWord(int raw) { return linkLong((short)raw); }
    CdR4MechanismImage linkLong(int raw) {
        int offset = (raw & 0xFFFFFF) - 0xFFD000;
        if (offset < 0 || (offset & 63)!=0 || offset>=128*64) throw new IllegalStateException("Unowned raw R4 actor link " + Integer.toHexString(raw));
        return actor(offset/64);
    }
    CdR4MechanismImage allocate(CdR4MechanismImage source, boolean after) {
        for (var image:dynamic) allocation.write(allocation.state(image.wholeIndex()).moveByte(CdObjectState.ByteField.ID,image.b(0)));
        var found = after ? allocation.findNextObjSlotAfterWholeIndex(source.wholeIndex()) : allocation.findObjSlot();
        return found.isPresent()?actor(found.getAsInt()):null;
    }
}
