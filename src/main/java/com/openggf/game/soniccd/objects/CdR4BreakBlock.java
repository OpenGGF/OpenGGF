package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/break_block.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4BreakBlock {
    private CdR4BreakBlock() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4BreakBlock::body); }
    private static Tail body(Cut c) {
        int routine=c.b(0x24);
        if(routine==0) {
            c.ab(0x24,2);c.b(1,c.b(1)|4);c.b(0x18,1);c.b(0x17,0x10);c.b(0x19,0x10);c.b(0x16,0x10);
            c.b(0x20,0xf3);c.w(2,0x4364);c.l(4,c.in.data().mapping(Mapping.BREAK));routine=2;
        }
        if(routine==2) {
            if(c.b(0x21)==0) {c.solid(SOLID_OBJECT);return Tail.DRAW_DESPAWN_X;}
            c.w(0x20,0);c.ab(0x24,2);c.w(0x2a,c.m().xVelocity());c.w(0x2e,c.m().yVelocity());
            if(c.solid(SOLID_OBJECT))c.solid(GET_OFF_OBJECT);
            c.sound(0xb0);
            int x=(short)c.w(0x2a),y=(short)c.w(0x2e),ax=(short)(x<0?-x:x),ay=(short)(y<0?-y:y);
            int deltaAt=ax<ay?0:x<0?64:32;
            c.player(null,null,0,0,0);
            for(int row=0;row<4;row++) {
                int slot=c.index;
                if(row>0) {
                    var found=c.spawn(false);if(found.isEmpty())break;slot=found.getAsInt();
                    // Re-read the modified original for each child: row0 offsets affect every later base.
                    for(int[] field:new int[][]{{0,1},{0x24,1},{8,2},{12,2},{1,1},{0x18,1},{4,4},{2,2}})
                        c.child(slot,field[0],field[1],read(c,field[0],field[1]));
                }
                c.child(slot,0x1a,1,1);c.child(slot,0x17,1,8);c.child(slot,0x19,1,8);c.child(slot,0x16,1,8);
                int dx=c.tw(Table.BREAK_OFFSETS,row*4),dy=c.tw(Table.BREAK_OFFSETS,row*4+2);
                c.child(slot,8,2,u16(c.pool.state(slot),8)+dx);c.child(slot,12,2,u16(c.pool.state(slot),12)+dy);
                c.child(slot,0x2a,4,c.tl(Table.BREAK_DELTAS,deltaAt+row*8));
                c.child(slot,0x2e,4,c.tl(Table.BREAK_DELTAS,deltaAt+row*8+4));
            }
        } else if(routine!=4)throw new IllegalArgumentException("Unowned Break routine offset");
        c.al(0x2e,0x4000);c.al(8,c.l(0x2a));c.al(12,c.l(0x2e));
        if((short)(c.py()-c.w(12))<=-0xe0) {c.clear();return Tail.NONE;}
        return Tail.DRAW;
    }
    private static int read(Cut c,int at,int size) { return size==1?c.b(at):size==2?c.w(at):c.l(at); }

}
