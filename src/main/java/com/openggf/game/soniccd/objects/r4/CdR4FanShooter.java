package com.openggf.game.soniccd.objects.r4;

/** Actual historical Fan A/B versus C/D and Shooter families. Native
 * src/objects/r4/fan_a_b.asm,fan_c_d.asm,shooter.asm at 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. */
public final class CdR4FanShooter {
    private CdR4FanShooter() {}
    public enum Mapping { FAN_AB_1, FAN_AB_2, FAN_CD_1, FAN_CD_2, SHOOTER, MISSILE }
    public interface Ports extends CdR4MechanismServices {
        int mapping(Mapping mapping);
        /** Reached BYTE read at the raw sign-extended WORD identity pointer. Not a bus API. */
        int fanIdentityByte(int rawWord);
        void animateFanAB(CdR4MechanismImage actor);
        void animateMissile(CdR4MechanismImage actor);
    }
    public static void fan(CdR4MechanismCut c,int whole,boolean future,Ports p) {
        var a=c.actor(whole);
        if(a.b(0x24)==0) {
            if(future) a.b(0x2C,p.fanIdentityByte(a.w(0x2A)));
            a.ab(0x24,2); a.b(1,a.b(1)|4); a.b(0x18,3); a.w(2,0x4380);
            int subtype=a.b(0x28);
            if((subtype&128)!=0) { if(future) a.ab(0x1A,1); else a.b(0x3F,255); }
            a.b(0x19,16); a.b(0x16,8);
            boolean vertical=(subtype&2)!=0;
            if(vertical) { a.b(0x19,8); a.b(0x16,16); }
            a.l(4,p.mapping(future?(vertical?Mapping.FAN_CD_2:Mapping.FAN_CD_1):(vertical?Mapping.FAN_AB_2:Mapping.FAN_AB_1)));
            if((subtype&1)!=0) { a.b(1,a.b(1)|3); a.b(0x22,a.b(0x22)|3); }
        }
        if(future && a.b(0x2C)!=p.fanIdentityByte(a.w(0x2A))) { p.delete(a); return; }
        if(a.sb(0x29)>=0 && a.b(0x3E)==0 && c.switches().read(a.b(0x29))!=0) {
            a.b(0x3E,255); if(future) a.b(0x1A,a.b(0x1A)^1); else a.b(0x3F,~a.b(0x3F));
        }
        if(!future) { if(a.b(0x3F)==0) a.b(0x1A,0); else p.animateFanAB(a); }
        p.draw(a); if(!future) p.checkDespawn(a,a.w(8));
    }
    public static void shooter(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole);
        if(a.sb(0x28)<0) {
            var player=c.actor(0);
            int y=(player.w(12)-a.w(12)+12)&65535;
            if(y<24) { int x=(player.w(8)-a.w(8)+20)&65535; if(x<40) player.w(0x14,a.l(0x2C)>>8); }
            a.al(8,a.l(0x2C)); p.animateMissile(a); p.draw(a); p.checkDespawn(a,a.w(8)); return;
        }
        if(a.b(0x24)==0) {
            a.ab(0x24,2); a.b(1,a.b(1)|4); a.b(0x18,3); a.b(0x17,8); a.b(0x19,8); a.b(0x16,16);
            a.w(2,0x4318); a.l(4,p.mapping(Mapping.SHOOTER)); a.l(0x2C,-0x30000);
            int dx=32; if(a.b(0x28)!=0) { a.b(1,a.b(1)|1); a.b(0x22,a.b(0x22)|1); a.l(0x2C,-a.l(0x2C)); dx=-32; }
            a.w(0x30,a.w(8)); a.aw(0x30,dx);
        }
        a.aw(0x2A,-1);
        if(a.sw(0x2A)<0) {
            a.w(0x2A,30); var child=c.allocate(a,false);
            if(child!=null) {
                child.b(0,a.b(0)); child.b(0x28,255); child.w(8,a.w(0x30)); child.w(12,a.w(12)); child.aw(12,4); child.l(0x2C,a.l(0x2C));
                child.b(1,a.b(1)); child.b(0x18,a.b(0x18)); child.ab(0x18,1); child.b(0x17,32); child.b(0x19,32); child.b(0x16,6);
                child.w(2,0x318); child.l(4,p.mapping(Mapping.MISSILE)); child.b(1,a.b(1)); child.b(0x22,a.b(0x22));
            }
        }
        p.draw(a); p.checkDespawn(a,a.w(8));
    }
}
