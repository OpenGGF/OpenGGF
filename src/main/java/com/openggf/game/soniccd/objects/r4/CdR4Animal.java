package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Read.*;
/** R4 AnimalObject and CheckAnimalPrescence (objects/r4/animal.asm:3-139; common/time_check.asm:34-58). */
public final class CdR4Animal {
    private CdR4Animal() { }
    public static CdObjectState run(CdR4ObjectScope c,int n){return c.run(n,0x2f,()->{
        if((byte)c.b(n,0x28)>=0){
            if((byte)c.read(TIME_ZONE)>=2){if(c.read(GOOD_FUTURE)==0){c.delete(n);return;}}
            else if(c.read(PROJECTOR_DESTROYED)==0){c.despawn(n,null);return;}
        }
        int routine=(byte)c.b(n,0x24);
        if(routine==0){
            c.b(n,1,c.b(n,1)|4);c.b(n,0x18,5);c.b(n,0x19,8);c.b(n,0x17,8);c.b(n,0x16,12);
            // ADD.B time_zone affects only low BYTE after ASL.W act, then ADD.W d0,d0.
            int index=c.read(ACT)<<2;index=(index&0xff00)|((index+c.read(TIME_ZONE))&255);
            c.w(n,2,c.tile((index*2)&65535));if((c.b(n,0x28)&1)!=0)c.aw(n,2,0x2000);
            c.l(n,4,c.pointer((c.b(n,0x28)&1)==0?ANIMAL_MAP1:ANIMAL_MAP2));c.w(n,0x2e,c.w(n,12));
            if((byte)c.b(n,0x28)<0){
                c.ab(n,0x24,-2);c.flip(n);c.w(n,0x38,c.w(n,8));c.l(n,0x34,0x200);
                if((c.b(n,0x28)&1)!=0){c.flip(n);c.l(n,0x34,-c.l(n,0x34));c.l(n,0x30,0x3000);}return;
            }
            c.ab(n,0x24,2);c.b(n,0x1c,1);c.l(n,0x2a,0x10000);c.l(n,0x34,0x100);return;
        }
        if(routine<0){
            int parent=c.link(c.w(n,0x3e));if(c.b(parent,0)!=0x52||c.b(parent,0x3f)!=0){c.delete(n);return;}
            var sc=c.sine(c.b(n,0x32));
            // Canonical signed WORDs suffice: the discarded old register half cannot affect
            // the final rounded integer after SWAP/ASR by 3 or 4 and +$8000 (proof in handoff).
            int cosine=(sc.cosine()<<16)|((sc.cosine()>>>16)&65535);
            int sine=(sc.sine()<<16)|((sc.sine()>>>16)&65535);
            c.w(n,8,((cosine>>3)+0x8000>>16)+c.w(n,0x38));
            c.w(n,12,((sine>>4)+0x8000>>16)+c.w(n,0x2e));
            c.al(n,0x30,c.l(n,0x34));if((c.l(n,0x30)&0x7fff)==0){c.flip(n);c.b(n,0x18,c.b(n,0x18)==5?3:5);}
            c.animate(n,ANIMAL_ANIM);c.draw(n);return;
        }
        c.al(n,8,c.l(n,0x2a));int sine=c.sine(c.b(n,0x32)).sine();
        int swapped=(sine<<16)|((sine>>>16)&65535);
        c.w(n,12,((swapped>>3)+0x8000>>16)+c.w(n,0x2e));c.al(n,0x30,c.l(n,0x34));
        if(c.l(n,0x30)<0||c.l(n,0x30)>=0x10000){c.l(n,0x34,-c.l(n,0x34));c.l(n,0x2a,-c.l(n,0x2a));c.flip(n);}
        c.animate(n,ANIMAL_ANIM);c.draw(n);c.despawn(n,null);
    });}
}
