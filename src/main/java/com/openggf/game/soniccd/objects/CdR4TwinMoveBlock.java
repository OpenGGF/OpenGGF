package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/twin_move_block.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4TwinMoveBlock {
    private CdR4TwinMoveBlock() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4TwinMoveBlock::body); }
    private static Tail body(Cut c) {
        switch(c.b(0x24)) {
            case 0 -> {
                c.ab(0x24,2); c.b(1,c.b(1)|4);c.w(2,0x446a);c.b(0x18,3);
                c.l(4,c.in.data().mapping(Mapping.TWIN));c.b(0x19,0x10);c.b(0x16,0x20);
                if(c.b(0x3c)==0) {
                    var slot=c.spawn(false);
                    if(slot.isPresent()) { int n=slot.getAsInt();c.child(n,0,1,0x48);c.child(n,8,2,c.w(8)+0x20);
                        c.child(n,12,2,c.w(12));c.child(n,0x3c,1,1);c.child(n,0x34,2,c.address()); }
                }
            }
            case 2 -> {
                if((short)c.w(0x12)<0)c.solid(SOLID_OBJECT);
                if(c.b(0x3a)==0) {
                    int at=c.b(0x3b)*4; c.b(0x3a,c.tb(Table.TWIN_MOTION,at));
                    int accel=c.tb(Table.TWIN_MOTION,at+1), velocity=c.tw(Table.TWIN_MOTION,at+2);
                    if(c.b(0x3c)!=0) { accel=(-accel)&255; velocity=-velocity; }
                    c.w(0x3e,(byte)accel);c.w(0x12,velocity);
                } else {
                    c.al(12,((short)c.w(0x12))<<8); c.aw(0x12,c.w(0x3e));c.ab(0x3a,-1);
                    if(c.b(0x3a)==0) { c.ab(0x3b,1);if(c.b(0x3b)==4)c.b(0x3b,0); }
                }
                if((short)c.w(0x12)>=0)c.solid(SOLID_OBJECT);
            }
            default -> throw new IllegalArgumentException("Unowned Twin routine offset");
        }
        return Tail.DRAW_TWIN_PARENT;
    }
}
