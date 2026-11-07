package com.openggf.game.soniccd.objects;

import static com.openggf.game.soniccd.objects.CdR4Boss.*;

/** N1ecc5f3 src/objects/r4/boss_chase.asm:3-793. Finite route operands are MEDIA data,
 * never a fitted battle script. One visit, raw links, no scheduling or campaign authority. */
public final class CdR4BossChase {
    private CdR4BossChase() { }
    static void run(Cursor c) {if(c.b(0)==0x4B){eggman(c);return;}
        switch(c.b(0x24)) {
            case 0 -> {
                c.b(0x20,0x3D);c.b(0x21,5);c.b(0x22,0);c.bit(0x22,0,true);
                c.init(Part.CHASE_MOBILE,8,6,0x24,0x38,0x31E);c.l(0x3C,0x1C000);c.l(0x10,0x13C68);c.palette(5);
                int n=c.allocate();if(n>=0){c.w(0x30,c.raw(n));c.otherW(n,0x30,c.raw(c.whole));c.otherB(n,0,0x4B);c.otherW(n,8,c.w(8));c.otherW(n,0xC,c.w(0xC));}
            }
            case 2 -> path(c);
            case 4 -> {
                c.al(0xC,c.l(0x3C));c.al(0x3C,0x3000);
                if(c.sw(0xC)>=0x580){int n=c.link(0x30);c.otherB(n,0x24,c.pool.state(n).routine()+2);
                    c.external("AddGfxQueue4",call->{c.owner.ports.queueBubbleArt(call);return 0;});c.delete();return;}
            }
            case 6 -> {
                explosions(c);
                int px=(short)c.read(Field.PLAYER_X),ox=c.sw(8),dx=(px-ox)&65535;
                int py=(short)c.read(Field.PLAYER_Y),oy=c.sw(0xC),dy=(py-oy)&65535;
                // SUB.W/BGE compares the signed operands even across overflow; NEG.W then wraps.
                int ax=px<ox?(-dx)&65535:dx,ay=py<oy?(-dy)&65535:dy;
                int squared=ax*ax+ay*ay;
                if((c.b(0x2E)&32)!=0){if(squared>=0x5100){waitFloat(c);break;}c.bit(0x2E,5,false);}
                if(squared>=0x11040){waitFloat(c);break;}
                if(squared>=0x4840){c.l(0x3C,0x1C000);c.l(0x10,0x13C68);}
                else if(squared>=0x1E40){c.l(0x3C,0x30000);c.l(0x10,0x21EF8);}
                else if((short)c.read(Field.PLAYER_Y_SPEED)>0x800||(short)c.read(Field.PLAYER_X_SPEED)>0x800){c.l(0x3C,0x100000);c.l(0x10,0xB4E88);}
                else{c.l(0x3C,0x80000);c.l(0x10,0x5A550);}path(c);
            }
            case 8 -> {if((short)c.read(Field.PLAYER_X)>=0x478){c.b(0x24,2);c.store(Field.BOSS_STARTED,4);c.l(0x10,0x32C80);}}
            default -> c.badRoutine();
        }
        if(c.b(0x2D)!=0){c.ab(0x2D,-1);c.ab(0x1C,3);c.animate(Part.CHASE_MOBILE);c.ab(0x1C,-3);}
        else c.animate(Part.CHASE_MOBILE);
        c.draw();
    }
    private static void waitFloat(Cursor c) {c.bit(0x2E,5,true);c.aw(0xC,-c.sw(0x38));c.b(0x1C,0);bob(c);}
    private static void path(Cursor c) {
        c.aw(0xC,-c.sw(0x38));damage(c);
        int state=c.b(0x25),at=(state&0x3F)*6;
        var route=c.table((state&128)!=0?Table.CHASE_UPPER:(state&64)!=0?Table.CHASE_LOWER:Table.CHASE_MAIN);
        int action=route.unsignedByte(at);boolean reached=false,horizontal=false;
        switch(action) {
            case 0,1 -> {
                c.b(0x1C,1);c.bit(0x22,0,action==1);c.al(8,action==0?c.l(0x3C):-c.l(0x3C));
                int x=(short)route.unsignedWord(at+2);reached=action==0?c.sw(8)>=x:c.sw(8)<=x;
                if(reached)c.w(8,x);horizontal=true;
            }
            case 2,5 -> {
                c.b(0x1C,0);c.al(0xC,action==5?c.l(0x3C):-c.l(0x3C));int y=(short)route.unsignedWord(at+4);
                reached=action==5?c.sw(0xC)>=y:c.sw(0xC)<=y;
                if(reached)c.w(0xC,y==0x880?0x80:y==(short)0xFF40?0x740:y);
            }
            case 3,4,6,7 -> {
                boolean down=action>=6,right=action==4||action==7;
                // 4 stores anim/flags AFTER both coordinate operations, as the native branch does.
                if(action!=4){c.b(0x1C,2);c.bit(0x22,0,!right);}
                int delta=down?c.l(0x10):c.l(0x3C);c.al(0xC,down?delta:-delta);
                int y=(short)route.unsignedWord(at+4);boolean ry=down?c.sw(0xC)>=y:c.sw(0xC)<=y;if(ry)c.w(0xC,y);
                c.al(8,right?delta:-delta);int x=(short)route.unsignedWord(at+2);boolean rx=right?c.sw(8)>=x:c.sw(8)<=x;if(rx)c.w(8,x);
                if(action==4){c.b(0x1C,2);c.bit(0x22,0,false);}reached=rx&&ry;
            }
            default -> throw new UnsupportedOperationException("Native nonreturning chase route opcode "+action);
        }
        if(reached) {
            int branch=route.unsignedByte(at+1);
            if(branch!=0&&c.b(0x21)==0){
                if(branch==1||branch==2){c.b(0x25,branch==1?128:64);int parent=c.link(0x30);int door=c.linkWord(CdR4Boss.word(c.pool.state(parent),branch==1?0x32:0x34));c.otherB(door,0x3F,1);}
                else{c.l(0x3C,0x38000);c.b(0x24,4);c.b(0x1C,0);}return;
            }
            c.ab(0x25,1);if((c.b(0x25)&0x3F)>=0x13)c.b(0x25,c.b(0x25)&0xC0);
        }else if(horizontal)bob(c);else c.aw(0xC,c.sw(0x38));
    }
    private static void bob(Cursor c) {c.ab(0x2F,2);int dy=c.sine(c.b(0x2F)).sine()>>5;c.w(0x38,dy);c.aw(0xC,dy);}
    private static void damage(Cursor c) {
        if(c.b(0x2C)!=0){c.ab(0x2C,-1);if(c.b(0x2C)==0){c.l(0x3C,0x1C000);c.l(0x10,0x13C68);}}
        if(c.b(0x21)==0)return;
        if(c.b(0x2B)!=0){c.ab(0x2B,-1);if(c.b(0x2B)==0)c.b(0x20,0x3D);return;}
        if(c.b(0x20)!=0)return;c.sound(Sound.CHASE_HIT);int parent=c.link(0x30);c.otherB(parent,0x2A,0x28);
        c.b(0x2C,0x46);c.l(0x3C,0x48000);c.l(0x10,0x32C80);c.b(0x2D,0x10);
        if(c.b(0x21)==1){c.b(0x24,6);c.b(0x21,0);}else c.b(0x2B,0x1E);
    }
    private static void explosions(Cursor c) {
        int parent=c.link(0x30);c.otherB(parent,0x2A,0x1E);
        if(c.b(0x2A)==0){c.b(0x2A,0x20);int n=c.allocate();if(n>=0){c.otherB(n,0x25,255);c.otherB(n,0,0x18);c.otherW(n,8,c.w(8));c.otherW(n,0xC,c.w(0xC));c.sound(Sound.EXPLOSION);}}
        c.ab(0x2A,-1);
    }
    private static void eggman(Cursor c) {
        if(c.b(0x24)==0){c.init(Part.CHASE_EGGMAN,2,6,0x24,0x38,0x31E);
            for(int ordinal=0;ordinal<2;ordinal++){int n=c.allocate();if(n<0)break;c.w(ordinal==0?0x32:0x34,c.raw(n));c.otherB(n,0,0x47);c.otherB(n,0x28,1);c.otherW(n,8,ordinal==0?0x760:0x6C0);c.otherW(n,0xC,ordinal==0?0x2C0:0x460);}}
        switch(c.b(0x24)) {
            case 2 -> {
                if(c.b(0x2A)!=0){c.ab(0x2A,-1);if(c.b(0x2A)==0)c.resetAnimation(0);else c.b(0x1C,1);}
                int parent=c.link(0x30);c.w(8,CdR4Boss.word(c.pool.state(parent),8));c.w(0xC,CdR4Boss.word(c.pool.state(parent),0xC));c.b(0x22,c.pool.state(parent).flags());c.animate(Part.CHASE_EGGMAN);c.draw();
            }
            case 4 -> {
                if((short)c.read(Field.PLAYER_X)<0x840||(short)c.read(Field.PLAYER_Y)<0x550)return;
                c.store(Field.BOSS_FLAGS,4);int n=c.link(0x32);
                if(c.pool.state(n).id()!=0){c.pool.deleteObject(n);return;}n=c.link(0x34);
                if(c.pool.state(n).id()!=0){c.pool.deleteObject(n);return;}c.delete();
            }
            default -> c.badRoutine();
        }
    }
}
