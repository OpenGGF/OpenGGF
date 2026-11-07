package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/wood_block.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4WoodBlock {
    private CdR4WoodBlock() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4WoodBlock::body); }
    private static Tail body(Cut c) {
        int routine=c.b(0x24);
        if(routine==0) {
            c.ab(0x24,2);c.b(1,c.b(1)|4);c.b(0x18,3);c.b(0x17,0x10);c.b(0x19,0x10);c.b(0x16,8);
            c.w(2,0x350);c.l(4,c.in.data().mapping(Mapping.WOOD));c.w(0x36,c.w(8));
            if(c.water.target()!=0x780&&c.water.target()!=0x5e0) {c.clear();return Tail.DRAW_DESPAWN_ANCHOR_36;}
            routine=2;
        }
        if(routine==2) {
            c.w(12,c.water.height());
            if(c.water.staticHeight()!=c.water.target())return Tail.DRAW_DESPAWN_ANCHOR_36;
            if(c.water.staticHeight()==0x780) {c.b(0x24,4);routine=4;}
            else if(c.water.staticHeight()==0x5e0) {c.b(0x24,6);routine=6;} else {c.clear();return Tail.DRAW_DESPAWN_ANCHOR_36;}
        }
        if(routine==4) {
            c.w(12,c.water.height());
            if(c.solid(TOP_SOLID_OBJECT)) {c.ab(0x24,2);c.w(8,c.px());
                c.water=new Water(c.water.height(),c.water.staticHeight(),0x5e0,c.water.speed());}
        } else if(routine==6||routine==8) {
            if(routine==6) {c.ab(0x24,2);c.w(0x12,-c.water.speed());}
            c.w(12,c.water.height());
            // MOVE.B x_speed reads its HIGH byte, then EXT.W. No normalization to speed/256.
            int d4=(short)((byte)(c.m().xVelocity()>>>8)+c.px()-c.w(8));
            if(d4!=0) {
                boolean right=d4>=0;int step=right?1:-1,width=right?c.b(0x17):-c.b(0x17);
                // Native stores a6 here. Its source address must be borrowed before publication.
                int pointer=c.in.data().address(right?Address.WOOD_RIGHT_PROBE:Address.WOOD_LEFT_PROBE);
                c.l(0x2a,pointer);c.w(0x2e,step);c.w(0x30,width);c.w(0x32,-d4>0?-d4:d4);c.w(0x34,c.w(12));c.aw(0x34,7);
                do {
                    int x=(c.w(8)+c.w(0x30))&65535; if(!right)x^=15;
                    int dist=c.wall(right,x,c.w(0x34));
                    if(dist<0)break;
                    int remaining=(short)c.w(0x32);c.aw(0x32,-1);if(remaining<=1)break; // SUBQ/BLE includes signed overflow
                    c.aw(8,c.w(0x2e));
                } while(true);
            }
            if(c.solid(TOP_SOLID_OBJECT))c.player(c.w(8),null,null,null,null);
        } else throw new IllegalArgumentException("Unowned Wood routine offset");
        return Tail.DRAW_DESPAWN_ANCHOR_36;
    }
}
