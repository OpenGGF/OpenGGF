package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/h_move_block.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4HMoveBlock {
    private CdR4HMoveBlock() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4HMoveBlock::body); }
    private static Tail body(Cut c) {
        int type=c.b(0x28)&127;if(type==7)type=0;
        if(type>8)throw new IllegalArgumentException("Unowned H subtype target");
        int routine=c.b(0x24);
        if(routine==0) init(c,type);
        else if(routine==2) move(c,type);
        else if(type==5&&routine==4)c.solid(SOLID_OBJECT);
        else throw new IllegalArgumentException("Unowned H routine offset");
        return Tail.DRAW_H_WINDOW;
    }
    private static void child(Cut c,int slot,int type,int part,int x,int y) {
        c.child(slot,0,1,0x24);c.child(slot,0x28,1,type);c.child(slot,0x3c,1,part);
        c.child(slot,0x36,2,c.w(0x36));c.child(slot,8,2,x);c.child(slot,12,2,y);
    }
    private static void init(Cut c,int type) {
        c.ab(0x24,2);c.b(1,c.b(1)|4);c.w(2,0x446a);c.b(0x18,3);
        Mapping map=switch(type){case 0,1,8->Mapping.H1;case 2->Mapping.H2;case 3->Mapping.H3;case 4->Mapping.H4;case 5->Mapping.H5;case 6->Mapping.H6;default->throw new AssertionError();};
        c.l(4,c.in.data().mapping(map));
        switch(type) {
            case 0,1 -> {
                c.w(0x32,c.w(8));c.b(0x19,0x30);c.b(0x16,0x10);c.b(0x1a,1);
                if(c.b(0x3c)==0) {
                    c.w(0x36,c.w(8));
                    if(c.b(0x3d)==0)for(int n=1;n<=5;n++) {
                        var f=c.spawn(false);if(f.isEmpty())continue;int s=f.getAsInt();
                        c.child(s,0,1,0x24);c.child(s,0x28,1,1);c.child(s,0x3d,1,n);c.child(s,8,2,c.w(8));
                        c.child(s,0x3a,1,(n<<5)+16);c.child(s,12,2,c.w(12)+n*0x60);
                    }
                    var f=c.spawn(false);if(f.isPresent()) {
                        int s=f.getAsInt();child(c,s,1,1,c.w(8)+0xe0,c.w(12));c.child(s,0x3a,1,c.b(0x3a));
                    }
                }
                move(c,type);
            }
            case 2 -> {
                c.b(0x19,0x20);c.b(0x16,0x10);
                if(c.b(0x3c)==0) {
                    c.w(0x36,c.w(8));c.w(0x32,c.w(8)-0x20);var f=c.spawn(false);
                    if(f.isPresent()) {int s=f.getAsInt();child(c,s,2,1,c.w(8)+0x40,c.w(12));
                        c.child(s,0x32,2,c.w(8)+0x60);c.child(s,0x3a,1,c.b(0x3a)-0x80);}
                }
                move(c,type);
            }
            case 3 -> {
                c.b(0x19,0x20);c.b(0x16,0x20);
                boolean lastSearchFailed=false;
                if(c.b(0x3c)==0) {c.w(0x36,c.w(8));for(int n=1;n<=5;n++) {
                    var f=c.spawn(false);lastSearchFailed=f.isEmpty();
                    if(f.isPresent())child(c,f.getAsInt(),3,n,c.w(8),c.w(12));
                }}
                // loc_20C50C MOVE.B retains the allocator's FFFF high byte after its last failed search.
                // Parent part0 therefore addresses word_20C53C-$100; no generic register/CPU model.
                Table table=lastSearchFailed?Table.H3_FAILED_ALLOCATION_OFFSETS:Table.H3_OFFSETS;
                int at=lastSearchFailed?0:((c.b(0x3c)*6)&255);
                c.aw(8,c.tw(table,at));c.aw(12,c.tw(table,at+2));c.w(0x10,c.tw(table,at+4));
            }
            case 4 -> {
                c.b(0x19,0x40);c.b(0x16,0x40);
                if(c.b(0x3d)!=0) {c.l(4,c.in.data().mapping(Mapping.H1));c.b(0x16,0x10);}
                if(c.b(0x3c)==0) {
                    c.w(0x36,c.w(8));var f=c.spawn(false);
                    if(f.isPresent()) {int s=f.getAsInt();c.child(s,0,1,0x24);c.child(s,0x28,1,4);c.child(s,0x3c,1,1);
                        c.child(s,0x3d,1,c.b(0x3d));c.child(s,0x36,2,c.w(0x36));c.child(s,8,2,c.w(0x36)+0x80);
                        c.child(s,12,2,c.w(12));c.w(8,c.w(0x36)-0x80);}
                }
                c.w(0x10,-0x200);
            }
            case 5 -> {
                c.w(0x36,c.w(8));c.b(0x19,0x20);c.b(0x16,0x20);
                if(c.b(0x3c)==0)for(int n=1;n<=4;n++) {
                    var f=c.spawn(false);if(f.isPresent()) {int s=f.getAsInt();c.child(s,0,1,0x24);c.child(s,0x28,1,5);
                        c.child(s,0x3c,1,n);c.child(s,8,2,c.w(8));c.child(s,12,2,c.w(12)+n*0x40);}
                }
                int at=(c.b(0x3c)*2)&255;
                c.aw(8,c.tb(Table.H5_START,at));c.b(0x3b,c.tb(Table.H5_START,at+1));
            }
            case 6 -> {
                c.b(0x16,16);
                if(c.b(0x3c)==0) {c.w(0x36,c.w(8));for(int n=1;n<=3;n++) {
                    var f=c.spawn(false);if(f.isPresent())child(c,f.getAsInt(),6,n,c.w(8)-(byte)(n<<4),c.w(12)+n*0x20);
                }}
                int part=c.b(0x3c);c.b(0x1a,part);c.b(0x3b,part+1);int width=((part+1)*16)&255;
                c.b(0x19,width);c.w(0x32,c.w(8)+width);c.b(0x3a,0x80);move(c,type);
            }
            case 8 -> {
                c.b(0x19,0x40);c.b(0x16,16);
                if(c.b(0x3c)==0) {
                    c.w(0x36,c.w(8));var first=c.spawn(false);
                    if(first.isPresent()) {int s=first.getAsInt();c.child(s,0,1,0x24);c.child(s,0x28,1,4);c.child(s,0x3d,1,8);
                        c.child(s,8,2,c.w(8));c.child(s,12,2,c.w(12)-0x60);}
                    var f=c.spawn(false);
                    if(f.isPresent()) {child(c,f.getAsInt(),8,1,c.w(0x36)-0x80,c.w(12));c.w(8,c.w(0x36)+0x80);}
                }
                c.w(0x10,0x200);
            }
            default -> throw new AssertionError();
        }
    }
    private static void move(Cut c,int type) {
        if(type==0||type==1||type==2||type==6) {
            int old=c.w(8),sin=c.sine(),delta;
            if(type==6)delta=((short)(sin*c.b(0x3b)))>>4;else delta=((short)sin)>>3;
            if(type!=6)c.ab(0x3a,1);
            c.w(8,c.w(0x32)+delta);c.w(0x10,(c.w(8)-old)<<8);
            if(type==6)c.ab(0x3a,1);c.solid(SOLID_OBJECT);return;
        }
        if(type==5) {
            if(c.b(0x3a)!=0) {
                c.w(0x10,c.w(0x30));c.al(8,((short)c.w(0x30))<<8);c.ab(0x3a,-1);
                if(c.b(0x3a)==0) {c.ab(0x3b,1);if(c.b(0x3b)==6)c.b(0x3b,0);}
                else {c.solid(SOLID_OBJECT);return;}
            }
            int at=(c.b(0x3b)*4)&255;c.b(0x3a,c.tb(Table.H5_MOTION,at));
            c.tb(Table.H5_MOTION,at+1); // reached unused byte, before the WORD speed read
            c.w(0x30,c.tw(Table.H5_MOTION,at+2));c.solid(SOLID_OBJECT);return;
        }
        c.al(8,((short)c.w(0x10))<<8);
        if(type==3) {
            int x=c.w(8),anchor=c.w(0x36);
            if((short)c.w(0x10)<0) {if(anchor>=x&&anchor-x>=0xe0)c.w(8,anchor+0xe0);}
            else if(x>=anchor&&x-anchor>=0xe0)c.w(8,anchor-0xe0);
            int diff=(c.py()-c.w(12))&65535;if(c.py()<c.w(12))diff=(-diff)&65535;
            if((c.b(1)&128)!=0&&diff<0x40)c.solid(SOLID_OBJECT);return;
        }
        c.solid(SOLID_OBJECT);
        int x=c.w(8),anchor=c.w(0x36);
        if(type==4&&anchor>=x&&anchor-x==0x100)c.w(8,anchor+0x100);
        if(type==8&&x>=anchor&&x-anchor==0x100)c.w(8,anchor-0x100);
    }
}
