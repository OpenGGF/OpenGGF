package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import java.util.*;

/** Detached TT routine-body calculations, not a dispatcher, live publication or replay API.
 * Source pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/objects/r4/*.asm.
 * Root must bind operands from the original CdProgramAssets and own draw/despawn/live cuts.
 * Both players and ALL96 dirty images survive every stopped read. Public equal data grants no authority.
 * Native a1 is primary; secondary changes only at a reached shared Top counterpart FLAGS release.
 */
public final class CdR4SolidMover {
    private CdR4SolidMover() { }
    public enum Mapping { BLOCK, WOOD, TWIN, WALL, BREAK, CRUSH, COLLAPSE, SWING, ROTATE,
        H1, H2, H3, H4, H5, H6, V1, V2, V3, V4, V5, V6, V7 }
    public enum Table { BLOCK_SIZES, TWIN_MOTION, BREAK_OFFSETS, BREAK_DELTAS, CRUSH_SHAPES, CRUSH_MOTION,
        ROTATE_FORWARD, ROTATE_BACK, ROTATE_SIDE, ROTATE_TOP, H3_OFFSETS, H3_FAILED_ALLOCATION_OFFSETS, H5_START, H5_MOTION,
        V1_START, V6_START, V10_START, V11_START }
    /** Borrowed finite source operands. No authentication or fallback assets are supplied here. */
    public enum Address { SAVED_FLAGS, WOOD_RIGHT_PROBE, WOOD_LEFT_PROBE }
    /** Finite borrowed bytes labelled by their first offset relative to the native table root.
     * The two reached LONGs after signed ADDA.W can cover relative bytes -32768..32774.
     * The root may lie outside this span; no absent bytes are padded or authenticated here.
     */
    public record RelativeTable(CdAssetBytes bytes,int firstOffset) {
        public RelativeTable { Objects.requireNonNull(bytes);
            if(firstOffset < -32768 || firstOffset > 32775)
                throw new IllegalArgumentException("Finite signed-WORD table origin required"); }
    }
    public interface Data {
        int mapping(Mapping mapping); CdAssetBytes table(Table table); int address(Address address);
        /** Existing canonical views own only offsets0..length-1; a negative borrow is explicit. */
        default RelativeTable relativeTable(Table table) { return new RelativeTable(table(table),0); }
    }
    @FunctionalInterface public interface Sine { CdPlayerMath.SineCosine read(int angleByte); }
    /** Read-only object terrain service; Root binds the existing source-equivalent terrain owner.
     * Returned signed d1 and reached angle writes belong to that original probe, never a synthetic floor.
     */
    public interface Terrain { Probe floor(int xWord, int yWord); Probe wall(boolean right, int xWord, int yWord); }
    public record Probe(int distance, OptionalInt primaryAngle, OptionalInt secondaryAngle) {
        public Probe { if (distance != (short)distance) throw new IllegalArgumentException("d1 WORD");
            Objects.requireNonNull(primaryAngle); Objects.requireNonNull(secondaryAngle); }
    }
    /** A terrain owner may expose its actually reached angle prefix on a failed read. */
    public static final class TerrainStopped extends RuntimeException {
        private final Probe prefix;
        public TerrainStopped(Probe prefix, RuntimeException cause) { super(cause);this.prefix=Objects.requireNonNull(prefix); }
        public Probe prefix() { return prefix; }
    }
    /** Actual live byte19 from BOTH actor headers, independently of byte17 collision radius. */
    public record ActorWidths(int primary, int secondary) {
        public ActorWidths { if(((primary|secondary)&~255)!=0)throw new IllegalArgumentException("actor width BYTE"); }
    }
    public record Water(int height, int staticHeight, int target, int speed) {
        public Water { if(((height|staticHeight|target|speed)&~65535)!=0)throw new IllegalArgumentException("water WORD operands"); }
    }
    public record Inputs(CdObjectPool.Snapshot pool, int wholeIndex, CdSolidObject.Player primary,
                         CdSolidObject.Player secondary, CdSolidObject.Gates gates, CdSolidObject.Globals globals,
                         CdSolidObject solid, Data data, Sine sine, Terrain terrain, CdAssetBytes savedFlags,
                         int timeZone, Water water, ActorWidths widths) {
        public Inputs { Objects.requireNonNull(pool); Objects.requireNonNull(primary); Objects.requireNonNull(secondary);
            Objects.requireNonNull(gates); Objects.requireNonNull(globals); Objects.requireNonNull(solid);
            Objects.requireNonNull(data); Objects.requireNonNull(savedFlags); Objects.requireNonNull(water);Objects.requireNonNull(widths);
            Objects.checkIndex(wholeIndex-32,96);if((timeZone&~255)!=0)throw new IllegalArgumentException("timeZone BYTE"); }
    }
    /** Exact next native cut. DRAW variants require the real DrawObject before their later tail reads.
     * DESPAWN_THEN_DRAW_DESPAWN_X is the nested Init return, not a completed effect. NONE is a genuine RTS.
     */
    public enum Tail { NONE, DRAW, DRAW_DESPAWN_X, DRAW_DESPAWN_ANCHOR_36,
        DRAW_H_WINDOW, DRAW_SWING_WINDOW, DRAW_TWIN_PARENT, DRAW_V_PARENT_23,
        DRAW_V_PARENT_NONZERO, DRAW_WALL_WINDOW, DESPAWN_ANCHOR_30, DESPAWN_ANCHOR_36,
        DESPAWN_THEN_DRAW_DESPAWN_X }
    public sealed interface Observation permits Sound, Solid, Search, Cleared { }
    public record Sound(int nativeId, Prefix before) implements Observation { }
    public record Cleared(Prefix before) implements Observation { }
    public record Solid(CdSolidObject.Outcome outcome, Optional<CdSolidObject.CounterpartFlags> counterpart) implements Observation { }
    public record Search(boolean after, int startWhole, OptionalInt found) implements Observation { }
    public record Prefix(CdObjectPool.Snapshot pool, CdSolidObject.Player primary, CdSolidObject.Player secondary,
                         CdSolidObject.Globals globals, CdAssetBytes savedFlags, Water water,
                         List<Observation> observations) {
        public Prefix { observations=List.copyOf(observations); }
    }
    public record Outcome(Prefix prefix, Tail tail, Optional<Throwable> stopped) {
        /** Only a prefix actually supplied by the shared Solid owner; local/host faults supply none.
         * This descriptive value never grants live adoption or retry authority.
         */
        public Optional<CdSolidObject.NativeStopped> nativeSolidStop() {
            if(stopped.isPresent() && !prefix.observations().isEmpty()
                    && prefix.observations().getLast() instanceof Solid call
                    && call.outcome() instanceof CdSolidObject.NativeStopped nativeStop
                    && nativeStop.stop().originalException()==stopped.orElseThrow())return Optional.of(nativeStop);
            return Optional.empty();
        }
    }
    @FunctionalInterface interface Body { Tail run(Cut c); }
    static Outcome calculate(Inputs in, Body body) {
        Cut c=new Cut(in);
        try { return new Outcome(c.finishAfter(body),c.tail,Optional.empty()); }
        catch (RuntimeException | Error failure) { return new Outcome(c.prefix(),Tail.NONE,Optional.of(failure)); }
    }
    static final class Cut {
        final Inputs in; final CdObjectPool pool; final int index;
        CdSolidObject.Player player,secondary; CdSolidObject.Globals globals; Water water;
        byte[] saved; final List<Observation> observations=new ArrayList<>(); Tail tail;
        Cut(Inputs in) { this.in=in; pool=CdObjectPool.stagingCopy(in.pool()); index=in.wholeIndex();
            player=in.primary(); secondary=in.secondary(); globals=in.globals(); water=in.water(); saved=in.savedFlags().copy(); }
        Prefix finishAfter(Body body) { tail=body.run(this); return prefix(); }
        Prefix prefix() { return new Prefix(pool.capture(),player,secondary,globals,CdAssetBytes.copyOf(saved),water,observations); }
        CdObjectState s() { return pool.state(index); }
        int b(int at) { return u8(s(),at); } int w(int at) { return u16(s(),at); } int l(int at) { return i32(s(),at); }
        void b(int at,int v) { pool.write(put(s(),at,1,v)); }
        void w(int at,int v) { pool.write(put(s(),at,2,v)); }
        void l(int at,int v) { pool.write(put(s(),at,4,v)); }
        void ab(int at,int v) { b(at,b(at)+v); } void aw(int at,int v) { w(at,w(at)+v); }
        void al(int at,int v) { l(at,l(at)+v); }
        CdAssetBytes table(Table t) { return Objects.requireNonNull(in.data().table(t),"reached source table"); }
        int tb(Table t,int at) { return table(t).unsignedByte(at); }
        int tw(Table t,int at) { return table(t).unsignedWord(at); }
        int tl(Table t,int at) { return (int)table(t).unsignedLong(at); }
        int trl(Table t,int at) { var view=Objects.requireNonNull(in.data().relativeTable(t),"reached relative table");
            return (int)view.bytes().unsignedLong(Math.subtractExact(at,view.firstOffset())); }
        int sine() { return Objects.requireNonNull(in.sine(),"reached sine owner").read(b(0x3a)).sine(); }
        OptionalInt spawn(boolean after) {
            var found=after?pool.findNextObjSlotAfterWholeIndex(index):pool.findObjSlot();
            observations.add(new Search(after,index,found)); return found;
        }
        void child(int slot,int at,int size,int value) { pool.write(put(pool.state(slot),at,size,value)); }
        CdObjectState raw(int low) {
            int address=low&0xffff;
            if(address<0xd800||address>0xefc0||(address&63)!=0)
                throw new IllegalArgumentException("Unowned/misaligned reached raw object link: "+Integer.toHexString(address));
            return pool.state((address-0xd000)/64);
        }
        int address() { return s().identity().nativeAddress24()&0xffff; }
        boolean solid(CdSolidObject.Entry entry) {
            CdSolidObject.Outcome out;
            Optional<CdSolidObject.CounterpartFlags> counterpart=Optional.empty();
            if(entry==CdSolidObject.Entry.TOP_SOLID_OBJECT) {
                var selected=in.solid().runWithPrefix(entry,s(),player,in.gates(),globals,pool.capture(),
                    new CdSolidObject.PlayerSelection(CdSolidObject.PlayerSlot.PRIMARY,secondary.actor().motion().flags()));
                out=selected.outcome();var flags=selected.counterpart();counterpart=Optional.of(flags);
                if(flags.stored())secondary=withFlags(secondary,flags.value());
            } else out=in.solid().runWithPrefix(entry,s(),player,in.gates(),globals,pool.capture());
            observations.add(new Solid(out,counterpart));
            if(out instanceof CdSolidObject.Completed ok) {
                var r=ok.completedResult(); pool.write(r.object()); for(var other:r.otherSlots()) pool.write(other);
                player=r.player(); globals=r.globals(); return r.d0().orElse(0)!=0;
            }
            var stop=(CdSolidObject.NativeStopped)out; var p=stop.prefix(); pool.write(p.object());
            for(var other:p.otherSlots()) pool.write(other); player=p.player(); globals=p.globals();
            throw stop.stop().originalException();
        }
        CdPlayerMotionState m() { return player.actor().motion(); }
        int px() { return m().centreX16_16()>>>16; } int py() { return m().centreY16_16()>>>16; }
        void player(Integer x,Integer y,Integer xv,Integer yv,Integer gv) {
            var m=m(); var next=new CdPlayerMotionState(x==null?m.centreX16_16():((x&0xffff)<<16)|(m.centreX16_16()&0xffff),
                y==null?m.centreY16_16():((y&0xffff)<<16)|(m.centreY16_16()&0xffff),
                xv==null?m.xVelocity():(short)(int)xv,yv==null?m.yVelocity():(short)(int)yv,gv==null?m.groundVelocity():(short)(int)gv,
                m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),
                m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
            var a=player.actor(); player=new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(next,a.tile(),a.priority(),a.hurt(),a.rotationDistance()),
                player.slotId(),player.pushObject(),player.standObject());
        }
        int floor(int x,int y) {
            try { return probe(in.terrain().floor(x,y)); }
            catch(TerrainStopped stopped) { probe(stopped.prefix());throw stopped; }
        }
        int wall(boolean right,int x,int y) {
            try { return probe(in.terrain().wall(right,x,y)); }
            catch(TerrainStopped stopped) { probe(stopped.prefix());throw stopped; }
        }
        int probe(Probe p) { globals=new CdSolidObject.Globals(globals.invincible(),globals.scoreChain(),
                p.primaryAngle().orElse(globals.primaryAngle()),p.secondaryAngle().orElse(globals.secondaryAngle())); return p.distance(); }
        void sound(int id) { observations.add(new Sound(id,prefix())); }
        void clear() { var before=prefix();pool.deleteObject(index);observations.add(new Cleared(before)); }
    }
    static CdSolidObject.Player withFlags(CdSolidObject.Player player,int flags) {
        var a=player.actor();var m=a.motion();
        var next=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),
            m.routine(),m.angle(),flags,m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),
            m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(next,a.tile(),a.priority(),a.hurt(),a.rotationDistance()),
            player.slotId(),player.pushObject(),player.standObject());
    }
    static int u8(CdObjectState s,int at) { return s.copyBytes()[Objects.checkIndex(at,64)]&255; }
    static int u16(CdObjectState s,int at) { return u8(s,at)*256+u8(s,at+1); }
    static int i32(CdObjectState s,int at) { return u16(s,at)<<16|u16(s,at+2); }
    /** Storage-only adapter: use existing ABI aliases without expanding the shared enum in another lane. */
    static CdObjectState put(CdObjectState s,int at,int size,int value) {
        Objects.checkFromIndexSize(at,size,64);
        for(int i=0;i<size;i++) {
            int offset=at+i, v=(value>>>((size-1-i)*8))&255;
            boolean done=false;
            for(var f:CdObjectState.ByteField.values()) if(f.byteOffset()==offset) { s=s.moveByte(f,v);done=true;break; }
            if(done) continue;
            for(var f:CdObjectState.WordField.values()) if(offset>=f.byteOffset()&&offset<f.byteOffset()+2) {
                int shift=(1-offset+f.byteOffset())*8;
                s=s.moveWord(f,(s.word(f)&~(255<<shift))|(v<<shift));done=true;break;
            }
            if(done) continue;
            for(var f:CdObjectState.LongField.values()) if(offset>=f.byteOffset()&&offset<f.byteOffset()+4) {
                int shift=(3-offset+f.byteOffset())*8;
                s=s.moveLong(f,(s.longBits(f)&~(255<<shift))|(v<<shift));done=true;break;
            }
            if(!done) throw new IllegalArgumentException("Unrepresented object storage byte "+offset);
        }
        return s;
    }
}
