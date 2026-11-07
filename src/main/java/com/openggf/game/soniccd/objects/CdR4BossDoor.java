package com.openggf.game.soniccd.objects;

import static com.openggf.game.soniccd.objects.CdR4Boss.*;

/** Boss-only ID47. N1ecc5f3 src/objects/r4/boss_door.asm:3-143.
 * Non-boss R4 doors belong to C2. Retains the source saved-bank pointer, not current era. */
public final class CdR4BossDoor {
    private CdR4BossDoor() { }
    static void run(Cursor c) {
        switch(c.b(0x24)) {
            case 0 -> {
                c.bit(1,2,true);c.b(0x18,3);c.b(0x19,0x40);c.b(0x17,0x40);c.b(0x16,0x40);c.w(2,0x446A);c.l(4,c.owner.assets.mapping(Part.DOOR));
                int id=c.b(0x23),period=c.read(Field.TIME_ZONE);
                c.l(0x2C,c.owner.assets.savedFlagsAddress()+2+3*id+period);
                int flag=savedFlag(c);
                if((flag&~255)!=0)throw new IllegalArgumentException("Unknown door saved byte");
                if((flag&1)!=0){c.aw(12,c.b(0x28)==0?0x40:0x80);c.ab(0x24,4);}
                else{c.w(0x2A,c.b(0x28)==0?0x10:0x20);c.b(0x24,(c.b(0x28)+1)*2);}
            }
            case 2 -> {
                int dy=(c.read(Field.PLAYER_Y)-c.w(12))&65535;
                if(dy<0x80){int dx=(c.read(Field.PLAYER_X)-c.w(8)-0xA0)&65535;
                    if(dx<0x20){c.b(0x3F,255);c.ab(0x24,2);}}
            }
            case 4 -> {if(c.b(0x3F)!=0){savedBit(c,0,true);c.ab(0x24,2);}}
            case 6 -> {c.al(12,0x40000);c.aw(0x2A,-1);if(c.w(0x2A)==0){c.b(0x3F,0);c.ab(0x24,-2);}}
            default -> c.badRoutine();
        }
        c.external("SolidObject:boss door",call->{c.owner.ports.solidDoor(call);return 0;});c.draw();
        if(c.b(0x28)==1)return;
        int delta=((c.w(8)&0xFF80)-((c.read(Field.CAMERA_X)-0x80)&0xFF80))&65535;
        if(delta>0x280){savedBit(c,7,false);c.delete();}
    }
    private static int bankIndex(Cursor c) {
        long delta=Integer.toUnsignedLong(c.l(0x2C))-Integer.toUnsignedLong(c.owner.assets.savedFlagsAddress());
        if(delta<2||delta>=CdObjectPlacementRuntime.SAVED_BANK_BYTES)
            throw new UnsupportedOperationException("Unowned raw boss-door saved-bank pointer");
        return (int)delta;
    }
    private static int savedFlag(Cursor c) {
        int at=bankIndex(c)-2;return c.owner.placement.savedFlag(at/3,at%3);
    }
    private static void savedBit(Cursor c,int bit,boolean set) {
        int at=bankIndex(c)-2;
        var selected=bit==0?CdObjectPlacementRuntime.SavedBit.MEMBER_1:CdObjectPlacementRuntime.SavedBit.LOADED;
        if(set)c.owner.placement.bsetSavedFlag(at/3,at%3,selected);
        else c.owner.placement.bclrSavedFlag(at/3,at%3,selected);
    }
}
