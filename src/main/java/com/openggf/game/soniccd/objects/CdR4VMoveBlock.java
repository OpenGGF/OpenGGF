package com.openggf.game.soniccd.objects;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;
/** Native routine body at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/objects/r4/v_move_block.asm (whole parent/child graph). Draw/despawn is the returned Root cut.
 * Detached data only: never replays observations or adopts into a live World.
 */
public final class CdR4VMoveBlock {
    private CdR4VMoveBlock() { }
    public static Outcome run(Inputs inputs) { return calculate(inputs, CdR4VMoveBlock::body); }
    private static Tail body(Cut c) {
        int type=c.b(0x28)&127;
        if(type==2)return Tail.NONE; // Native Null-like subtype leaf: no draws, reads or writes.
        if(type>11)throw new IllegalArgumentException("Unowned V subtype target");
        if(type==6) { generator(c);return Tail.DESPAWN_ANCHOR_36; }
        boolean init=c.b(0x24)==0;
        if(init)init(c,type);
        else if(c.b(0x24)!=2)throw new IllegalArgumentException("Unowned V routine offset");
        // Types9/11 Init RTS; all other initializers fall into their motion routine.
        if(!init||type!=9&&type!=11)move(c,type);
        return type==8?Tail.DRAW_V_PARENT_23:type>=9?Tail.DRAW_V_PARENT_NONZERO:Tail.DRAW_DESPAWN_ANCHOR_36;
    }
    private static void child(Cut c,int s,int type,int part,int x,int y) {
        c.child(s,0,1,0x23);c.child(s,0x28,1,type);c.child(s,0x3c,1,part);
        c.child(s,12,2,y);c.child(s,8,2,x);c.child(s,0x36,2,c.w(0x36));
    }
    private static void generator(Cut c) {
        if(c.b(0x24)!=0)return;
        c.ab(0x24,2);c.w(0x36,c.w(8));
        for(int n=0;n<7;n++) {
            var found=c.spawn(false);if(found.isEmpty())continue;int s=found.getAsInt();
            c.child(s,0,1,0x23);c.child(s,0x28,1,9);c.child(s,12,2,c.w(12));c.child(s,0x34,2,c.address());c.child(s,0x3d,1,255);
            c.child(s,0x3a,1,c.tb(Table.V6_START,n*2));int x=(byte)c.tb(Table.V6_START,n*2+1);c.child(s,8,2,c.w(8)+x);
        }
    }
    private static void init(Cut c,int type) {
        c.ab(0x24,2);c.b(1,c.b(1)|4);c.w(2,0x446a);c.b(0x18,3);
        Mapping mapping=switch(type){case 0,1->Mapping.V1;case 3->Mapping.V2;case 4->Mapping.V3;
            case 5,7->Mapping.V4;case 8->Mapping.V5;case 9,11->Mapping.V6;case 10->Mapping.V7;default->throw new AssertionError();};
        c.l(4,c.in.data().mapping(mapping));
        switch(type) {
            case 0,1 -> {
                c.w(0x32,c.w(12)+0x40);c.b(0x19,16);c.b(0x16,0x40);
                if(c.b(0x3c)==0) {c.w(0x36,c.w(8));for(int n=1;n<=6;n++) {
                    var f=c.spawn(false);if(f.isPresent()) {int s=f.getAsInt();c.child(s,0,1,0x23);c.child(s,0x28,1,1);
                        c.child(s,8,2,c.w(8));c.child(s,12,2,c.w(12));c.child(s,0x36,2,c.w(0x36));c.child(s,0x3c,1,n);}
                }}
                int at=(c.b(0x3c)*2)&255;c.b(0x3a,c.tb(Table.V1_START,at));c.aw(8,(byte)c.tb(Table.V1_START,at+1));
            }
            case 3 -> {c.w(0x36,c.w(8));c.w(0x32,c.w(12)-0x50);c.b(0x19,0x20);c.b(0x16,0x60);c.b(0x3a,0x40);}
            case 4 -> {
                c.w(0x32,c.w(12)+0x30);c.b(0x19,0x12);c.b(0x16,0x30);c.b(0x3a,0xc0);
                if(c.b(0x3c)==0)c.w(0x36,c.w(8));
                var spikes=c.spawn(false);if(spikes.isPresent()) {
                    int s=spikes.getAsInt();c.child(s,0,1,0x22);c.child(s,8,2,c.w(8));c.child(s,12,2,c.w(12));
                    c.child(s,0x36,2,c.w(0x36));c.child(s,0x34,2,c.address());c.child(s,0x39,1,0x40);c.child(s,0x28,1,1);
                    // Native does NOT write a parent's var34 here.
                }
                if(c.b(0x3c)==0)for(int n=1;n<=2;n++) {var f=c.spawn(false);if(f.isPresent())child(c,f.getAsInt(),4,n,c.w(8)+(n==1?-0x50:0x50),c.w(12));}
            }
            case 5,7 -> {
                c.w(0x32,c.w(12)+0x40);c.b(0x19,type==5?0x14:0x10);c.b(0x16,type==5?0x41:0x40);
                if(c.b(0x3c)==0) {
                    c.w(0x36,c.w(8)+(type==7?0x30:0));c.b(0x3a,type==5?0:0xc0);
                    for(int n=1;n<=3;n++) {
                        var f=c.spawn(false);if(f.isPresent()) {
                            int s=f.getAsInt();c.child(s,0,1,0x23);c.child(s,0x28,1,type);c.child(s,0x3c,1,1);
                            c.child(s,0x3a,1,type==5?-n*16:0xc0+n*32);
                            c.child(s,8,2,c.w(8)+n*32);c.child(s,12,2,c.w(12)+(type==5?-n*32:0));c.child(s,0x36,2,c.w(0x36));
                        }
                    }
                }
            }
            case 8 -> {
                c.w(0x32,c.w(12)-0x20);c.b(0x19,0x20);c.b(0x16,0x30);
                if(c.b(0x3d)==0)c.w(0x36,c.w(8));
                if(c.b(0x3c)==0) {
                    var f=c.spawn(false);if(f.isEmpty())return;int s=f.getAsInt();
                    c.child(s,0,1,0x23);c.child(s,0x3c,1,1);c.child(s,0x28,1,8);c.child(s,8,2,c.w(8));c.child(s,0x36,2,c.w(0x36));
                    c.child(s,12,2,c.w(12)+0xa0);c.child(s,0x3a,1,c.b(0x3a));c.child(s,0x3d,1,c.b(0x3d));
                    if(c.b(0x3d)==0)for(int n=1;n<=2;n++) {
                        var other=c.spawn(false);if(other.isPresent()) {
                            s=other.getAsInt();c.child(s,0,1,0x23);c.child(s,0x28,1,8);c.child(s,0x3d,1,n);c.child(s,0x3a,1,0x80);
                            c.child(s,8,2,c.w(8)+(n==1?-0x40:0x40));c.child(s,12,2,c.w(12));c.child(s,0x36,2,c.w(0x36));
                        }
                    }
                }
            }
            case 9 -> {
                c.w(0x36,c.w(8));c.w(0x32,c.w(12));c.b(0x19,16);c.b(0x16,0x20);
                if(c.b(0x3c)==0) {
                    var f=c.spawn(false);if(f.isPresent()) {
                        int s=f.getAsInt();c.child(s,0,1,0x23);c.child(s,0x3c,1,1);c.child(s,0x28,1,9);c.child(s,8,2,c.w(8));
                        int y=0x80,phase=c.b(0x3a);if((byte)c.b(0x3d)<0){y=0xa0;phase-=0x80;}
                        c.child(s,0x3a,1,phase);c.child(s,12,2,c.w(12)+y);
                    }
                }
            }
            case 10 -> {
                c.w(0x32,c.w(12));c.b(0x19,16);c.b(0x16,16);
                if(c.b(0x3c)==0) {c.w(0x36,c.w(8));for(int n=1;n<=6;n++) {
                    var f=c.spawn(false);if(f.isPresent()) {
                        int s=f.getAsInt();c.child(s,0,1,0x23);c.child(s,0x28,1,10);c.child(s,12,2,c.w(12));c.child(s,0x36,2,c.w(0x36));c.child(s,0x3c,1,n);
                        c.child(s,0x3a,1,c.tb(Table.V10_START,n*2));int x=(byte)c.tb(Table.V10_START,n*2+1);c.child(s,8,2,c.w(8)+x);
                    }
                }}
            }
            case 11 -> {
                c.b(0x19,16);c.b(0x16,0x20);c.b(0x1a,0);
                if(c.b(0x3c)==0) {c.w(0x36,c.w(8));c.w(0x32,c.w(12));for(int n=1;n<=5;n++) {
                    var f=c.spawn(false);if(f.isPresent()) {
                        int s=f.getAsInt();c.child(s,0,1,0x23);c.child(s,0x28,1,11);c.child(s,0x3c,1,n);c.child(s,0x36,2,c.w(0x36));c.child(s,0x32,2,c.w(0x32));
                    }
                }}
                int at=(c.b(0x3c)*4)&255;c.w(8,c.w(0x36)+(byte)c.tb(Table.V11_START,at));
                c.w(12,c.w(0x32)+(byte)c.tb(Table.V11_START,at+1));c.w(0x12,c.tw(Table.V11_START,at+2));
            }
            default -> throw new AssertionError();
        }
    }
    private static void move(Cut c,int type) {
        if(type==11) {
            c.al(12,((short)c.w(0x12))<<8);int y=c.w(12),a=c.w(0x32);
            if((short)c.w(0x12)<0) {if(a>=y&&a-y>=0x80)c.w(12,a+0x80);}
            else if(y>=a&&y-a>=0x80)c.w(12,a-0x80);
            c.solid(SOLID_OBJECT);return;
        }
        if(type==4||type==8) {
            int old=c.w(12),sin=c.sine(),d;
            if(type==4) {d=(short)(sin*3);d>>=4;}
            else {d=(short)(sin*2);d>>=4;}
            if(type==8)c.aw(0x3a,0x80); // ADDI.W, including dirty low byte; NOT phase BYTE increment.
            c.w(12,c.w(0x32)+d);if(type==4)c.ab(0x3a,1);
            int velocity=((c.w(12)-old)<<8)&65535;c.w(0x12,velocity);
            if(type==4&&c.w(0x34)!=0) {var linked=c.raw(c.w(0x34));c.child(linked.identity().wholeIndex(),0x12,2,velocity);}
            c.solid(SOLID_OBJECT);return;
        }
        if((short)c.w(0x12)<0)c.solid(SOLID_OBJECT);
        int old=c.l(12),sin=c.sine();int shift=type==9||type==10?5:6,second=type==3||type==10?4:0;
        int d=(short)((short)(sin<<shift)+(short)(sin<<second));d>>=8;
        c.ab(0x3a,1);c.w(12,c.w(0x32)+d);
        // MOVEQ cleared d0 before SineCosine; the later SWAP leaves low WORD0, not old fraction.
        int velocity=(((c.w(12)<<16)-old)>>8)&65535;c.w(0x12,velocity);
        if((short)velocity>=0)c.solid(SOLID_OBJECT);
    }
}
