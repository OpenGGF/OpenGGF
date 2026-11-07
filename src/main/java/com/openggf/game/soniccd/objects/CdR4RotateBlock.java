package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/rotate_block.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4RotateBlock {
    private CdR4RotateBlock() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4RotateBlock::body); }
    private static Tail body(Cut c) {
        if(c.b(0x3c)==255)return child(c);
        switch(c.b(0x24)) {
            case 0 -> {
                c.ab(0x24,2);c.b(1,c.b(1)|4);c.b(0x18,3);c.l(4,c.in.data().mapping(Mapping.ROTATE));c.w(2,0x4300);
                c.b(0x16,0x40);c.b(0x19,0x40);int frame=c.b(0x28)&3;c.b(0x1a,frame);c.b(0x3d,frame);
                for(int n=0;n<2;n++) {
                    var found=c.spawn(false);if(found.isEmpty())return Tail.DESPAWN_THEN_DRAW_DESPAWN_X;int slot=found.getAsInt();
                    c.child(slot,0,1,0x3d);c.child(slot,0x36,2,c.w(8));c.child(slot,0x34,2,c.address());
                    c.child(slot,0x3c,1,255);c.child(slot,0x3d,1,n);
                    c.w(n==0?0x30:0x34,c.pool.state(slot).identity().nativeAddress24());
                }
            }
            case 2 -> {
                if((byte)u8(c.raw(c.w(0x30)),0x3e)>=0)break;
                int px=c.px(),width=(byte)((c.in.widths().primary()+2)&255);
                int d0,d1;
                if(c.b(0x3d)<2) {d0=c.w(8);d1=(px+width)&65535;}
                else {d1=c.w(8);d0=(px+(byte)(-((c.in.widths().primary()+2)&255)))&65535;}
                if(d0<d1)break;
                // sub_20EDE8 clears primaryAngle before its reached CheckBlockY.
                c.globals=new CdSolidObject.Globals(c.globals.invincible(),c.globals.scoreChain(),0,c.globals.secondaryAngle());
                int dist=c.floor(c.w(8),(c.w(12)+16)&65535);
                if((byte)dist<0||(dist&255)<16) {
                    if(u8(c.raw(c.w(0x34)),0x3e)==0)break;
                    c.b(0x24,6);
                } else c.ab(0x24,2);
                c.b(0x3a,16);c.b(0x3b,0);
            }
            case 4,6 -> {
                int routine=c.b(0x24),at=((c.b(0x3d)*4)&255)+c.b(0x3b);
                int frame=c.tb(routine==4?Table.ROTATE_FORWARD:Table.ROTATE_BACK,at&255);
                if((byte)frame<0) {c.b(0x3d,c.b(0x1a));if(routine==4)c.ab(0x24,-2);else c.b(0x24,2);break;}
                c.b(0x1a,frame);
                if(routine==6)c.player(null,c.w(12)-(byte)c.b(0x16)-(byte)c.m().yRadius(),null,null,null);
                c.ab(0x3a,-1);
                if(c.b(0x3a)==0) {
                    if(routine==4) {c.sound(0xbf);c.b(0x3a,4);} else c.b(0x3a,8);
                    c.ab(0x3b,1);
                    if(routine==6) {
                        if(c.b(0x3b)==1)c.player(c.w(8),null,null,null,null);
                        else if(c.b(0x3b)==2)c.player(c.w(8)+((c.b(0x3d)&2)==0?16:-16),null,null,null,null);
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unowned Rotate routine offset");
        }
        return Tail.DRAW_DESPAWN_X;
    }
    private static Tail child(Cut c) {
        if(c.b(0x24)==0) {
            c.ab(0x24,2);c.b(1,c.b(1)|4);c.b(0x18,3);c.l(4,c.in.data().mapping(Mapping.ROTATE));c.w(2,0x4300);
            c.b(0x1a,8);c.b(0x19,c.b(0x3d)==0?0x20:0x10);c.b(0x16,0x10);
        } else if(c.b(0x24)!=2)throw new IllegalArgumentException("Unowned Rotate child routine offset");
        var parent=c.raw(c.w(0x34));int frame=u8(parent,0x1a);
        if(frame>=4) {c.solid(GET_OFF_OBJECT);return Tail.DRAW_DESPAWN_ANCHOR_36;}
        Table t=c.b(0x3d)==0?Table.ROTATE_SIDE:Table.ROTATE_TOP;
        int x=(byte)c.tb(t,frame*2),y=(byte)c.tb(t,frame*2+1);
        c.w(8,u16(parent,8)+x);c.w(12,u16(parent,12)+y);c.solid(SOLID_OBJECT);
        if(c.b(0x3d)==0)c.b(0x3e,(c.b(0x22)&8)!=0?255:0);
        else if((c.b(0x22)&32)!=0) {
            if(c.b(0x3f)==0) {c.w(0x3e,65535);c.b(0x22,c.b(0x22)&~32);}
        } else c.w(0x3e,0);
        return Tail.DRAW_DESPAWN_ANCHOR_36;
    }
}
