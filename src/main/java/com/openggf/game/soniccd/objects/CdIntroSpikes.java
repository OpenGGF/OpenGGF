package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31IntroAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.Objects;
import java.util.function.Supplier;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Complete A-only $30 mechanism, including all three shards and native Hurt prefix.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/objects/r3/intro.asm:536-749 (actual historical A include).
 * Shipped source, no FixBugs alternative in this closure. Writes preserve dirty bytes.
 * Each call is synchronous, and a fault retains the reached pool/player/global prefix.
 * This is a component, NOT a World admission: Root must authenticate the original private
 * operation, exact source/pool, full State/Environment and ordered ledger before publishing.
 * All fallible preparation precedes adoption. A returned image never authorizes replay.
 */
public final class CdIntroSpikes {
    public enum Read {
        TIME_ATTACK, AMY_CAPTURED, TIME_WARP, INVINCIBLE,
        PLAYER_X, PLAYER_Y, PLAYER_Y_LONG, PLAYER_X_VELOCITY, PLAYER_Y_VELOCITY,
        PLAYER_GROUND_VELOCITY, PLAYER_ROUTINE, PLAYER_HURT_WORD, PLAYER_ANIMATION,
        PLAYER_ANGLE, PLAYER_FLAGS, DEBUG_MODE_HIGH, DEBUG_CHEAT_HIGH, ZONE, HELD
    }
    public enum PlayerStore { Y_LONG, X_VELOCITY, Y_VELOCITY, GROUND_VELOCITY, FLAGS,
        Y_RADIUS, X_RADIUS, Y_WORD, ANIMATION }
    /** Exact actual component source/pool cut. Value equality is not invocation authority. */
    public record Cut(CdR31IntroAssets assets,CdObjectPool.Snapshot pool,CdObjectState source) { }
    /**
     * Root-owned live cuts, no default successes. Reads, Frame stores, sound, PLC and palette
     * must not mutate dynamic objects. Solid/GetOff/Draw/Despawn are original mutating helpers;
     * Root must validate their exact writes, complete Frame pair and Environment even on throws.
     * Solid returns the native nonzero D0 predicate, not a fabricated collision receipt.
     * playerStore is ONLY the listed finite family stores, never arbitrary RAM access.
     */
    public interface Ports {
        int read(Read field,Cut cut);
        void playerStore(PlayerStore field,int value,Cut cut);
        boolean solid(Cut cut);
        void getOff(Cut cut);
        void hurt(Cut cut);
        void fm(int nativeId,Cut cut);
        void loadPlc(int nativeId,Cut cut);
        void subCpu(int nativeId,Cut cut);
        void setAmyCaptured(Cut cut);
        void palette(CdAssetBytes actual32Bytes,Cut cut);
        void draw(Cut cut);
        void despawn(Cut cut);
    }
    private final CdR31IntroAssets assets;
    public CdIntroSpikes(CdR31IntroAssets assets) { this.assets=Objects.requireNonNull(assets); }
    public CdObjectState run(CdObjectPool pool,int whole,Ports ports) {
        var c=new P5Call(assets,pool,whole,ports,0x30);
        if(c.read(Read.TIME_ATTACK)!=0) { c.delete(); return c.s(); }
        int routine=c.s().routine(); P5Call.routine(routine,4);
        if(routine==0) {
            if(c.read(Read.AMY_CAPTURED)!=0) { c.delete(); c.draw(); return c.s(); }
            c.b(0x24,2);c.b(1,c.s().spriteFlags()|4);c.w(2,0x31E);c.b(0x18,3);
            c.l(4,assets.introMappings());c.b(0x19,0x12);c.b(0x16,8);
            int subtype=c.s().subtype();
            if(subtype!=1) {
                c.b(0x16,0x20);
                if(subtype!=0) { c.b(0x1A,9);c.b(0x20,0x86);c.b(0x19,0xC);if(subtype!=2)c.b(0x1A,0xE); }
            }
            if(c.b(0x3C)!=0) {
                c.b(0x20,0);c.b(0x24,c.s().routine()+2);
                motion(c);
            }
        }
        if(c.s().routine()==2) {
            if(c.s().subtype()==1) solidHurt(c);
            else {
                CdObjectState metal=null;
                for(int n=32;n<128;n++)if(pool.state(n).id()==0x31){metal=pool.state(n);break;}
                if(metal!=null && range(metal.centreX(),c.s().centreX(),(byte)c.b(0x19)+0x10)
                        && range(metal.centreY(),c.s().centreY(),(byte)c.b(0x16)+0x10)) {
                    c.b(0x24,c.s().routine()+2); shards(c);c.ports.getOff(c.cut());
                } else c.ports.solid(c.cut());
            }
        } else if(c.s().routine()==4) {
            c.moveX();c.moveY();c.w(0x12,c.w(0x12)+c.w(0x3E));c.b(0x3A,c.b(0x3A)-1);
            if(c.b(0x3A)==0)c.delete();
        }
        c.draw();return c.s();
    }
    private static boolean range(int target,int origin,int radius) {
        int d=(target-origin+radius)&0xFFFF;
        return (short)d>=0 && d<((radius+radius)&0xFFFF);
    }
    private void shards(P5Call c) {
        int subtype=c.s().subtype(),base;
        if(subtype==0){c.b(0x1A,4);base=0;}else if(subtype==2){c.b(0x1A,8);base=4;}else{c.b(0x1A,0xD);base=9;}
        for(int n=3;n>0;n--) {
            var free=c.pool.findObjSlot();
            if(free.isPresent()) {
                int slot=free.getAsInt();
                c.childB(slot,0,0x30);c.childW(slot,8,c.s().centreX());c.childW(slot,0xC,c.s().centreY());
                c.childB(slot,0x1A,n+base);c.childB(slot,0x3C,n);
            }
        }
        if((c.s().spriteFlags()&0x80)!=0)c.fm(0xA3);
        // Historical SpawnShards falls through into SetMotion for the broken parent.
        motion(c);
    }
    private void motion(P5Call c) {
        int child=c.b(0x3C);
        c.w(0x10,assets.shardX(child));c.w(0x12,assets.shardY(child));c.w(0x3E,0x60);c.b(0x3A,0x78);
    }
    private static void solidHurt(P5Call c) {
        if(!c.ports.solid(c.cut()) || (c.s().flags()&8)==0)return;
        if(c.read(Read.TIME_WARP)!=0 || c.read(Read.INVINCIBLE)!=0)return;
        if(c.read(Read.PLAYER_ROUTINE)>=4 || c.read(Read.PLAYER_HURT_WORD)!=0)return;
        int y=c.read(Read.PLAYER_Y_LONG),vy=(short)c.read(Read.PLAYER_Y_VELOCITY);
        c.player(PlayerStore.Y_LONG,y-(vy<<8));
        c.ports.hurt(c.cut());
    }
    /** $2B is DeleteObject, not teleport logic, in A/B/C/D. No art or Frame access. */
    public static CdObjectState teleporterDelete(CdObjectPool pool,int whole) {
        if(pool.state(whole).id()!=0x2B)throw new IllegalArgumentException("Expected CC Teleporter $2B");
        pool.deleteObject(whole);return pool.state(whole);
    }
}

/** P5-only source-ordered storage vocabulary; no new shared State aliases or generic public RAM API. */
final class P5Call {
    final CdR31IntroAssets assets;final CdObjectPool pool;final int whole;final CdIntroSpikes.Ports ports;
    P5Call(CdR31IntroAssets a,CdObjectPool p,int n,CdIntroSpikes.Ports ports,int id) {
        assets=Objects.requireNonNull(a);pool=Objects.requireNonNull(p);whole=n;this.ports=Objects.requireNonNull(ports);
        if(s().id()!=id)throw new IllegalArgumentException("Expected P5 ID $"+Integer.toHexString(id));
    }
    CdObjectState s(){return pool.state(whole);}
    CdIntroSpikes.Cut cut(){return new CdIntroSpikes.Cut(assets,pool.capture(),s());}
    int read(CdIntroSpikes.Read f){return stable(()->ports.read(f,cut()));}
    <T>T stable(Supplier<T> call) {
        var before=pool.capture();
        T result;
        try { result=call.get(); }
        catch(RuntimeException|Error fault) {
            if(!before.slots().equals(pool.capture().slots()))fault.addSuppressed(new IllegalStateException("P5 external port changed dynamic pool"));
            throw fault;
        }
        if(!before.slots().equals(pool.capture().slots()))throw new IllegalStateException("P5 external port changed dynamic pool");
        return result;
    }
    void player(CdIntroSpikes.PlayerStore f,int value){stable(()->{ports.playerStore(f,value,cut());return null;});}
    void fm(int id){stable(()->{ports.fm(id,cut());return null;});}
    void draw(){ports.draw(cut());}
    void delete(){pool.deleteObject(whole);}
    static void routine(int raw,int max){if((raw&1)!=0||raw>max)throw new UnsupportedOperationException("P5 raw routine $"+Integer.toHexString(raw));}
    int b(int at){return Byte.toUnsignedInt(s().copyBytes()[at]);}int w(int at){return s().word(wf(at));}int l(int at){return s().longBits(lf(at));}
    void b(int at,int value){
        // State has no BYTE alias for30. Compose its immutable image through the existing
        // WORD alias, retaining31 exactly; this is still only one native high-BYTE store.
        if(at==0x30)pool.write(s().moveWord(wf(0x30),((value&255)<<8)|(w(0x30)&255)));
        else pool.write(s().moveByte(bf(at),value));
    }
    void w(int at,int value){pool.write(s().moveWord(wf(at),value));}
    void l(int at,int value){pool.write(s().moveLong(lf(at),value));}
    void childB(int slot,int at,int value){pool.write(pool.state(slot).moveByte(bf(at),value));}
    void childW(int slot,int at,int value){pool.write(pool.state(slot).moveWord(wf(at),value));}
    void moveX(){l(8,l(8)+((short)w(0x10)<<8));}void moveY(){l(0xC,l(0xC)+((short)w(0x12)<<8));}
    void face(int target){flip(false);if(s().centreX()>=target)flip(true);}
    void flip(boolean flipped){b(0x22,flipped?s().flags()|1:s().flags()&~1);b(1,flipped?s().spriteFlags()|1:s().spriteFlags()&~1);}
    int linkedId(int raw){return linkedByte(raw,0);}
    int linkedFlags(int raw){return linkedByte(raw,0x22);}
    int linkedCaptureFlag(int raw){return linkedByte(raw,0x3D);}
    int linkedX(int raw){return (linkedByte(raw,8)<<8)|linkedByte(raw,9);}
    int linkedY(int raw){return (linkedByte(raw,0xC)<<8)|linkedByte(raw,0xD);}
    private int linkedByte(int raw,int field) {
        // MOVEA.W sign-extends the raw link. Do not round it to an object identity:
        // dirty links can address interior bytes or span physical slot boundaries.
        // Only these source-reached fields are exposed, never a public RAM reader.
        int offset=(short)raw+field-(int)0xFFFFD800;
        if(offset<0 || offset>=96*64)
            throw new UnsupportedOperationException("P5 linked read outside owned dynamic pool: raw $"+Integer.toHexString(raw)+", field $"+Integer.toHexString(field));
        return Byte.toUnsignedInt(pool.state(32+offset/64).copyBytes()[offset%64]);
    }
    /** Historical IntroAnimateObject: pairs of frame/duration, not the common delay header. */
    void animateIntro(CdObjectAnimation.AnimationTable table) {
        int id=b(0x1C);
        if(id!=b(0x1D)){b(0x1D,id);b(0x1B,0);b(0x1E,0);}
        b(0x1E,b(0x1E)-1);if((byte)b(0x1E)>=0)return;
        if(id>=table.scriptCount())throw new UnsupportedOperationException("P5 intro animation outside original directory");
        int script=table.tableOffset()+(short)table.bytes().unsignedWord(table.tableOffset()+id*2);
        int index=b(0x1B),frame=table.bytes().unsignedByte(script+index);
        if(frame>=0x80) {
            b(0x1B,0);index=0;frame=table.bytes().unsignedByte(script);
            if(frame>=0x80)throw new UnsupportedOperationException("Native IntroAnimateObject does not return: negative first frame");
        }
        b(0x1A,frame&31);
        int rotated=((frame<<3)|(frame>>>5))&255;
        b(1,s().spriteFlags()&0xFC);b(1,s().spriteFlags()|((rotated^s().flags())&3));
        // Duration is read AFTER frame/flip stores. A missing byte retains those writes.
        b(0x1E,table.bytes().unsignedByte(script+index+1));b(0x1B,b(0x1B)+2);
    }
    void animate(CdObjectAnimation.AnimationTable table) {
        var o=s();
        // Preserve Object Animate.asm:20-33 and loop-index writes before any missing
        // table/frame byte faults. The existing shared kernel still owns the algorithm.
        if(o.unsignedByte(ANIMATION)!=o.unsignedByte(PREVIOUS_ANIMATION)) {
            b(0x1D,o.unsignedByte(ANIMATION));b(0x1B,0);b(0x1E,0);
        }
        b(0x1E,b(0x1E)-1);
        if((byte)b(0x1E)<0) {
            int id=o.unsignedByte(ANIMATION);
            if(id>=table.scriptCount())throw new UnsupportedOperationException("P5 selected animation outside source directory");
            int script=table.tableOffset()+(short)table.bytes().unsignedWord(table.tableOffset()+id*2);
            b(0x1E,table.bytes().unsignedByte(script));
            int index=b(0x1B),frame=table.bytes().unsignedByte(script+1+index);
            if(frame==0xFF)b(0x1B,0);
            else if(frame==0xFE)b(0x1B,index-table.bytes().unsignedByte(script+2+index));
        }
        var a=CdObjectAnimation.step(new CdObjectAnimation.State(o.unsignedByte(ANIMATION),o.unsignedByte(PREVIOUS_ANIMATION),
                o.unsignedByte(ANIMATION_FRAME),o.unsignedByte(ANIMATION_TIME),o.unsignedByte(MAP_FRAME),
                o.spriteFlags(),o.flags(),o.routine(),o.unsignedByte(ROUTINE_2)),table);
        // Adopt the shared value result after all reached reads succeeded; no returned-effect replay.
        pool.write(o.moveByte(ANIMATION,a.animationId()).moveByte(PREVIOUS_ANIMATION,a.previousId())
                .moveByte(ANIMATION_FRAME,a.scriptIndex()).moveByte(ANIMATION_TIME,a.timer()).moveByte(MAP_FRAME,a.mapFrame())
                .moveByte(SPRITE_FLAGS,a.spriteFlags()).moveByte(ROUTINE,a.routine()).moveByte(ROUTINE_2,a.routine2()));
    }
    static CdObjectState.ByteField bf(int at){for(var f:CdObjectState.ByteField.values())if(f.byteOffset()==at)return f;throw new IllegalArgumentException("P5 BYTE "+at);}
    static CdObjectState.WordField wf(int at){for(var f:CdObjectState.WordField.values())if(f.byteOffset()==at)return f;throw new IllegalArgumentException("P5 WORD "+at);}
    static CdObjectState.LongField lf(int at){for(var f:CdObjectState.LongField.values())if(f.byteOffset()==at)return f;throw new IllegalArgumentException("P5 LONG "+at);}
}
