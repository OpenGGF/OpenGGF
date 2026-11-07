package com.openggf.game.soniccd.objects;

import static com.openggf.game.soniccd.objects.CdR4Boss.*;

/** Native R43 bubble graph: src/objects/r4/boss.asm:598-1100 at N1ecc5f3.
 * Uses shared animation/math. Collision/pop helper retains caller continuation and double calls. */
public final class CdR4BossBubble {
    private CdR4BossBubble() { }
    static void run(Cursor c) {
        if(c.b(0x24)==0){c.init(Part.BUBBLE,2,3,0x10,0x10,0x31E);
            // EXT.L follows ANDI.L: sign-extend the low WORD before DIVS.
            int remainder=((short)c.random())%0x500;c.w(0x36,remainder<0?remainder-0x80:remainder+0x80);
            c.w(0x3C,(c.random()&65535)%0x10+10);
        }
        switch(c.b(0x24)) {
            case 2 -> {if(!rise(c))return;}
            case 4 -> attach(c);
            case 6 -> expand(c);
            case 8 -> orbit(c);
            case 10 -> contract(c);
            case 12 -> c.animate(Part.BUBBLE);
            case 14 -> {c.delete();return;}
            case 16 -> redistribute(c);
            default -> c.badRoutine();
        }
        c.draw();
    }
    private static int parent(Cursor c) {return c.link(0x2E);}
    private static int pb(Cursor c,int parent,int at) {return Byte.toUnsignedInt(c.pool.state(parent).copyBytes()[at]);}
    private static void copyPosition(Cursor c,int parent,boolean full) {
        if(full){c.l(8,(CdR4Boss.word(c.pool.state(parent),8)<<16)|CdR4Boss.word(c.pool.state(parent),10));c.l(12,(CdR4Boss.word(c.pool.state(parent),12)<<16)|CdR4Boss.word(c.pool.state(parent),14));}
        else{c.w(8,CdR4Boss.word(c.pool.state(parent),8));c.w(12,CdR4Boss.word(c.pool.state(parent),12));}
    }
    private static boolean rise(Cursor c) {
        c.al(12,-0x18000);
        if(c.b(0x25)!=0){if(c.sw(12)<0x470){c.delete();return false;}}
        else {
            int p=parent(c);int y=(short)CdR4Boss.word(c.pool.state(p),12);
            if(y>=c.sw(12)) {
                // loc_20CA72 BGE compares the original signed operands; retain wrapped SUB.W separately.
                int x=c.sw(8),parentX=(short)CdR4Boss.word(c.pool.state(p),8);
                int dx=(short)(x-parentX);c.w(0x34,x>=parentX?0x8000:0);
                c.w(0x3C,dx);c.w(0x10,dx);c.w(0x38,-dx);c.w(0x3A,0);copyPosition(c,p,false);c.b(0x24,4);c.b(0x1C,2);return true;
            }
        }
        c.aw(0x34,c.sw(0x36));c.aw(8,-c.sw(0x38));c.orbit();
        if(c.b(0x1B)==4){c.b(0x1C,1);c.b(0x1B,0);}c.animate(Part.BUBBLE);return true;
    }
    private static void attach(Cursor c) {
        int p=parent(c);copyPosition(c,p,false);c.orbit();int delta=c.sw(0x36);if(delta>=0)delta=(short)-delta;c.aw(0x34,delta);
        p=parent(c);if(pb(c,p,0x24)!=10)return;c.w(0x38,0);c.w(0x3A,0);c.b(0x24,6);copyPosition(c,p,true);
        c.w(0x34,c.b(0x2D)*0x1000);c.w(0x3C,0x10);c.w(0x10,0x10);
    }
    private static void expand(Cursor c) {
        if((c.b(0x2C)&2)!=0)collide(c);
        int p=parent(c),ready=0;copyPosition(c,p,true);
        if(c.sw(0x3C)<0x30)c.al(0x3C,0x4000);else{c.w(0x3C,0x30);ready++;}
        if(c.sw(0x10)<0x30)c.al(0x10,0x4000);else{c.w(0x10,0x30);ready++;}
        c.aw(0x34,(pb(c,p,0x2C)&8)!=0?0x480:-0x480);c.orbit();
        if(c.b(0x1B)==2){c.b(0x1C,3);c.b(0x1B,0);}
        if(ready==2){c.b(0x24,8);boolean was=(c.b(0x2C)&2)!=0;c.bit(0x2C,1,true);if(!was){c.b(0x20,255);c.b(0x21,2);}}
        c.animate(Part.BUBBLE);
    }
    private static void orbit(Cursor c) {
        int p=parent(c);copyPosition(c,p,true);
        if((pb(c,p,0x2C)&16)!=0){c.b(0x24,10);contract(c);return;}
        if((pb(c,p,0x2C)&32)!=0){c.b(0x24,16);c.b(0x18,3);redistribute(c);return;}
        boolean popped=collide(c); // pop returns through the selected animation's source continuation
        c.aw(0x34,(continuationDirection(c,p,popped)&8)!=0?0x180:-0x180);c.orbit();
    }
    private static void contract(Cursor c) {
        collide(c);int p=parent(c);copyPosition(c,p,true);
        boolean second=c.b(0x25)!=0;int priorRadius=c.l(0x3C);c.al(0x3C,-0x10000);
        if(!second){if(priorRadius<0x10000)c.ab(0x25,1);}
        else if(c.l(0x3C)<=-0x300000){c.l(0x3C,0x300000);c.b(0x24,8);c.w(0x34,0x8000-c.w(0x34));spinDepth(c,p);c.b(0x25,0);return;}
        spinDepth(c,p);
    }
    private static void spinDepth(Cursor c,int p) {
        c.aw(0x34,(pb(c,p,0x2C)&8)!=0?0x480:-0x480);int phase=(c.b(0x34)-0x40)&255;
        p=parent(c);if((pb(c,p,0x2C)&8)!=0)phase^=128;c.b(0x18,(phase&128)==0?3:5);c.orbit();
    }
    private static void redistribute(Cursor c) {
        collide(c);int p=parent(c);copyPosition(c,p,true);c.al(0x3C,-0x10000);c.al(0x10,-0x10000);
        if(c.l(0x3C)<=0x100000){int count=pb(c,p,0x2D);int spacing=c.table(Table.BUBBLE_SPACING).unsignedWord(count*2);
            int index=pb(c,p,0x32);c.w(0x34,spacing*index);c.otherB(p,0x32,index+1);c.l(0x3C,0x100000);c.l(0x10,0x100000);c.b(0x24,6);}
        boolean popped=collide(c); // source calls twice; parent bit6 arbitrates first versus second pop
        c.aw(0x34,(continuationDirection(c,p,popped)&8)!=0?0x480:-0x480);c.orbit();
    }
    private static int continuationDirection(Cursor c,int p,boolean popped) {
        // loc_20CC32/loc_20CDA0: only a pop leaves the source at selected Anim4.
        // Read its independently authenticated adjacent byte at the actual reached cut.
        return popped?c.table(Table.BUBBLE_POP_DIRECTION).unsignedByte(0):pb(c,p,0x2C);
    }
    private static boolean collide(Cursor c) {
        int p=parent(c);
        if(pb(c,p,0x24)!=20){if(c.b(0x20)!=0)return false;boolean bit=(pb(c,p,0x2C)&64)!=0;
            c.otherB(p,0x2C,pb(c,p,0x2C)|64);if(bit){c.b(0x20,255);c.b(0x21,2);return false;}}
        c.b(0x24,12);c.resetAnimation(4);c.otherB(p,0x2D,pb(c,p,0x2D)-1);
        // loc_20CE0C falls into RoutineC's AnimateObject before returning to the calling routine.
        c.animate(Part.BUBBLE);
        return true;
    }
}
