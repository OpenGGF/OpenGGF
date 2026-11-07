package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/move_wall.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4MoveWall {
    private CdR4MoveWall() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4MoveWall::body); }
    private static Tail body(Cut c) {
        switch(c.b(0x24)) {
            case 0 -> {
                c.b(1,c.b(1)|4);c.b(0x18,3);c.b(0x17,0x20);c.b(0x19,0x20);c.b(0x16,0x40);
                c.w(2,0x446a);c.l(4,c.in.data().mapping(Mapping.WALL));
                int offset=2+c.b(0x23)*3+c.in.timeZone();
                int base=c.in.data().address(Address.SAVED_FLAGS);c.l(0x2c,base+offset);
                int flags=c.saved[java.util.Objects.checkIndex(offset,c.saved.length)]&255;
                if((flags&1)==0) {c.saved[offset]=(byte)(flags|1);c.w(0x2a,0x3c);c.ab(0x24,2);}
                else {c.aw(8,0xc0);c.ab(0x24,6);}
            }
            case 2 -> { c.aw(0x2a,-1);if(c.w(0x2a)==0) {c.w(0x2a,0x180);c.ab(0x24,2);} c.solid(SOLID_OBJECT); }
            case 4 -> {
                if(c.solid(SOLID_OBJECT)) c.player(null,null,null,null,0x80);
                c.al(8,0x8000);c.aw(0x2a,-1);if(c.w(0x2a)==0)c.ab(0x24,2);
            }
            case 6 -> c.solid(SOLID_OBJECT);
            default -> throw new IllegalArgumentException("Unowned Wall routine offset");
        }
        return Tail.DRAW_WALL_WINDOW;
    }

}
