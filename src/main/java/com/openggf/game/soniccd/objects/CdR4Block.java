package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/block.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4Block {
    private CdR4Block() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4Block::body); }
    private static Tail body(Cut c) {
        if(c.b(0x24)==0) {
            c.ab(0x24,2); c.b(1,c.b(1)|4); c.b(0x18,1); c.w(2,0x446a); c.l(4,c.in.data().mapping(Mapping.BLOCK));
            int n=c.b(0x28); c.b(0x1a,n);
            int width=c.tb(Table.BLOCK_SIZES,n*2), height=c.tb(Table.BLOCK_SIZES,n*2+1);
            c.b(0x17,width);c.b(0x19,width);c.b(0x16,height);
        }
        c.solid(SOLID_OBJECT); return Tail.DRAW_DESPAWN_X;
    }
}
