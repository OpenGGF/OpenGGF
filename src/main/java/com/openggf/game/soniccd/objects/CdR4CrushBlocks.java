package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/crush_blocks.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4CrushBlocks {
    private CdR4CrushBlocks() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4CrushBlocks::body); }
    private static Tail body(Cut c) {
        if((byte)c.b(0x28)<0) {
            if(c.raw(c.w(0x32)).id()!=0x33) {c.clear();return Tail.NONE;}
            c.solid(SOLID_OBJECT);return Tail.DRAW;
        }
        if(c.b(0x24)==0) {
            c.ab(0x24,2);c.w(0x30,c.w(8));c.b(1,c.b(1)|4);c.b(0x18,3);c.b(0x17,0x10);c.b(0x19,0x10);c.b(0x16,0x10);
            c.w(2,0x446a);c.l(4,c.in.data().mapping(Mapping.CRUSH));c.b(0x1a,4);c.w(0x32,c.address());c.w(0x2a,0xc0);c.l(0x2c,-0x8000);
            int cursor=(c.b(0x28)+1)*8,link=0x38;
            spawnChildren: for(int row=3;row>=0;row--) for(int side=1;side>=0;side--) {
                var found=c.spawn(true);if(found.isEmpty()) {c.clear();break spawnChildren;} // Delete RTS returns through BSR into loc_20EC36
                int slot=found.getAsInt();
                c.b(link++,c.pool.state(slot).identity().nativeAddress24()>>6);
                c.child(slot,0x28,1,side-2);c.child(slot,12,2,c.w(12)-0x30+(row<<5));
                int frame=c.tb(Table.CRUSH_SHAPES,--cursor);c.child(slot,0x1a,1,frame);
                c.child(slot,0x17,1,(frame+1)<<4);c.child(slot,0x19,1,(frame+1)<<4);
                int d0=(frame<<4)&255,dx=0x40;if(side==0){dx=-dx;d0=(-d0)&65535;}
                c.child(slot,8,2,c.w(8)+dx-d0);c.child(slot,0x32,2,c.address());
                for(int[] f:new int[][]{{0,1},{1,1},{0x18,1},{0x16,1},{2,2},{4,4}})
                    c.child(slot,f[0],f[1],f[1]==1?c.b(f[0]):f[1]==2?c.w(f[0]):c.l(f[0]));
            }
        }
        int delta=c.l(0x2c);
        for(int row=0;row<4;row++) for(int side=0;side<2;side++) {
            var child=c.raw(((0xff00|c.b(0x38+row*2+side))<<6)&65535);
            int at=child.identity().wholeIndex();
            c.child(at,8,4,i32(child,8)+(side==0?-delta:delta));
            c.child(at,0x10,2,side==0?-(delta>>8):delta>>8);
        }
        c.aw(0x2a,-1);
        if(c.w(0x2a)==0) {
            int at=(short)c.w(0x34);c.w(0x2a,c.trl(Table.CRUSH_MOTION,at));c.l(0x2c,c.trl(Table.CRUSH_MOTION,at+4));c.aw(0x34,8);c.w(0x34,c.w(0x34)&31);
        }
        return Tail.DESPAWN_ANCHOR_30;
    }
}
