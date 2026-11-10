package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/swing.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4Swing {
    private CdR4Swing() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4Swing::body); }
    /** Original primary-only occurrence; same native body, synchronous shared publications. */
    public static void run(PrimaryInputs in,LiveOwner owner) {
        if(in.placement().pool().state(in.wholeIndex()).id()!=0x29)
            throw new IllegalArgumentException("Original Swing requires current ID29");
        runPrimary(in,owner,CdR4Swing::body);
    }
    private static Tail body(Cut c) {
        if(c.b(0x24)==0) {
            c.ab(0x24,2);c.b(1,c.b(1)|4);c.b(0x18,4);c.l(4,c.in.data().mapping(Mapping.SWING));c.w(2,0x340);
            if(c.b(0x1a)==1)c.aw(2,0x4000);c.b(0x19,0x20);c.b(0x16,10);c.b(0x3a,0xc0);
            if(c.b(0x3c)==6) {
                var found=c.spawn(true);if(found.isPresent()) {int n=found.getAsInt();c.child(n,0,1,0x27);c.child(n,0x34,2,c.address());
                    c.child(n,0x39,1,0xf0);c.child(n,0x38,1,0x10);c.child(n,0x28,1,c.b(0x28)&15);}
            }
            if(c.b(0x3c)==0) {
                c.w(0x36,c.w(8));c.w(0x32,c.w(12));
                for(int part=6;part>=1;part--) {
                    var found=c.spawn(false);if(found.isEmpty())continue;int n=found.getAsInt();c.child(n,0,1,0x29);
                    c.child(n,0x36,2,c.w(0x36));c.child(n,0x32,2,c.w(0x32));c.child(n,0x28,1,c.b(0x28));
                    c.child(n,0x3c,1,part);c.child(n,0x1a,1,2);if(part==6)c.child(n,0x1a,1,1);
                }
            }
        } else if(c.b(0x24)!=2)throw new IllegalArgumentException("Unowned Swing routine offset");
        int oldX=c.l(8),oldY=c.l(12);
        c.aw(0x3a,(c.b(0x28)&16)==0?-0x80:0x100);
        var trig=c.in.sine().read(c.b(0x3a));int sin=(short)(trig.sine()*c.b(0x3c)),cos=(short)(trig.cosine()*c.b(0x3c));
        c.w(12,(sin>>4)+c.w(0x32));c.w(8,(cos>>4)+c.w(0x36));
        if(c.b(0x3c)==6) {
            c.w(0x10,(c.l(8)-oldX)>>8);c.w(0x12,(c.l(12)-oldY)>>8);
            if(c.solid(TOP_SOLID_OBJECT))c.player(null,c.w(12)-(byte)c.b(0x16)-(byte)c.m().yRadius()+2,null,null,null);
        }
        return Tail.DRAW_SWING_WINDOW;
    }
}
