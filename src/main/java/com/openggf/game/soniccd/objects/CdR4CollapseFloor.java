package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/collapse_floor.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4CollapseFloor {
    private CdR4CollapseFloor() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4CollapseFloor::body); }
    private static Tail body(Cut c) {
        if(c.b(0x29)!=0) return piece(c);
        int routine=c.b(0x24);
        if(routine==0) {
            c.ab(0x24,2);c.b(1,4);c.w(2,0x4364);c.l(4,c.in.data().mapping(Mapping.COLLAPSE));c.b(0x18,3);c.b(0x16,0x11);
            c.b(0x1a,c.b(0x28)-1);c.b(0x17,c.b(0x28)<<3);c.b(0x19,c.b(0x28)<<3);routine=2;
        }
        if(routine==2) {
            if(c.solid(TOP_SOLID_OBJECT)) {
                c.ab(0x24,2);c.b(0x23,0);c.w(0x2a,8);c.w(0x2c,c.m().xVelocity()<0?-8:8);c.sound(0xa3);
            }
            return Tail.DRAW_DESPAWN_X;
        }
        if(routine==4) {c.solid(TOP_SOLID_OBJECT);c.aw(0x2a,-1);if((short)c.w(0x2a)<0)c.ab(0x24,2);return Tail.DRAW;}
        if(routine!=6)throw new IllegalArgumentException("Unowned Collapse routine offset");
        // MOVEM.L copies exactly bytes0..15; no routine/subtype/delta/width initialization.
        var header=c.s();int dx=(short)((c.b(0x28)-1)<<3);if((short)c.w(0x2c)>=0)dx=(short)-dx;
        for(int row=0;row<2;row++) {
            var found=c.spawn(false);if(found.isEmpty())break;int slot=found.getAsInt();
            for(int at=0;at<16;at+=4)c.child(slot,at,4,i32(header,at));
            c.child(slot,0x29,1,255);c.child(slot,12,2,u16(c.pool.state(slot),12)+(row==0?-8:8));
            c.child(slot,8,2,u16(c.pool.state(slot),8)+dx);
            if(row==0)c.child(slot,0x2a,2,4); // second piece keeps dirty delay
        }
        if(c.solid(TOP_SOLID_OBJECT))c.solid(GET_OFF_OBJECT);
        int subtype=(byte)c.b(0x28);c.ab(0x28,-1);if(subtype<=1) {c.clear();return Tail.NONE;} // SUBQ/BLE, including80→7F overflow
        c.ab(0x24,-2);c.ab(0x1a,-1);c.ab(0x17,-8);c.ab(0x19,-8);c.w(0x2a,7);c.aw(8,c.w(0x2c));
        c.solid(TOP_SOLID_OBJECT);return Tail.DRAW;
    }
    private static Tail piece(Cut c) {
        switch(c.b(0x24)) {
            case 0 -> {c.ab(0x24,2);c.b(0x1a,8);c.b(0x16,8);c.b(0x17,8);c.b(0x18,3);c.b(0x19,8);}
            case 2 -> {c.aw(0x2a,-1);if((short)c.w(0x2a)<0)c.ab(0x24,2);}
            case 4 -> {
                if((byte)c.b(1)>=0) {c.clear();return Tail.NONE;}
                int delta=c.l(0x2c)+0x4000;if(delta>0x160000)delta=0x160000;c.l(0x2c,delta);c.al(12,delta);
            }
            default -> throw new IllegalArgumentException("Unowned Collapse piece routine offset");
        }
        return Tail.DRAW;
    }
}
