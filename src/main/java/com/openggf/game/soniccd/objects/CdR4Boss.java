package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import java.util.*;

/**
 * TT semantic family, N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/objects/r4/boss.asm:3-1315. No FixBugs alternative. No World, clock or art owner.
 * Tables/mappings/animations MUST be borrowed from the actual programme/media owner;
 * constructing an Assets or Result authenticates neither source nor a live producer.
 * Each visit handles exactly one physical slot, retaining dirty allocation and reached stores.
 * Root owns increasing physical scheduling, complete environment/capture, actual source admission
 * and genuine current invocation checks. No completed value can invoke these ports later.
 */
public final class CdR4Boss {
    public enum Part { CHASE_MOBILE, CHASE_EGGMAN, EGGMAN, MOBILE, MISSILE, BUBBLE, DOOR }
    public enum Table { CHASE_MAIN, CHASE_UPPER, CHASE_LOWER, BUBBLE_THRESHOLDS,
        MISSILE_DECLARATIONS, EXPLOSION_OFFSETS, BUBBLE_SPACING, ESCAPE_BOUNDS, BUBBLE_POP_DIRECTION }
    /** Finite data proposal; Root must bind independently authenticated C/D and actual media spans. */
    public interface Assets {
        int mapping(Part part);
        CdObjectAnimation.AnimationTable animation(Part part);
        CdAssetBytes table(Table table);
        /** Exact native savedObjFlags operand; unknown is an error at the reached door init. */
        default int savedFlagsAddress() { throw new UnsupportedOperationException("Missing TT saved-bank operand"); }
    }
    public enum Field {
        PLAYER_X(16), PLAYER_Y(16), PLAYER_X_SPEED(16), PLAYER_Y_SPEED(16),
        PLAYER_HURT_TIMER(16), PLAYER_ROUTINE(8), PLAYER_FLAGS(8), PLAYER_ANIMATION(8),
        LEFT_BOUND(16), TARGET_LEFT_BOUND(16),
        RIGHT_BOUND(16), TARGET_RIGHT_BOUND(16), BOSS_STARTED(8), BOSS_FLAGS(8),
        GOOD_FUTURE(8), CAMERA_X(16), TIME_ZONE(8);
        final int bits;
        Field(int bits) { this.bits=bits; }
        public int widthBits() { return bits; }
    }
    public enum Sound { CHASE_HIT(0xAC), EXPLOSION(0x9E), BUBBLE(0xB6), INHALE(0xAD), BOSS_MUSIC(0x67),
        GOOD_MUSIC(0x16), BAD_MUSIC(0x17);
        private final int id;
        Sound(int id) { this.id=id; }
        public int nativeId() { return id; }
        public boolean subCpu() { return this==BOSS_MUSIC||this==GOOD_MUSIC||this==BAD_MUSIC; }
    }
    /** Ephemeral original-call identity, never serializable or constructible by a result consumer. */
    public static final class Call {
        private final Cursor cursor;
        private final CdObjectPlacementRuntime.Snapshot cut;
        private final String site;
        private boolean claimed;
        private CdObjectPool.Snapshot solidAfter;
        private CdObjectPool.Snapshot hurtAfter;
        private CdPlayerTouchResponse.HurtPair hurtPairAfter;
        private HurtPublication hurtPublication;
        private Call(Cursor c,String site) { cursor=c; cut=c.owner.placement.capture(); this.site=site; }
        public String site() { return site; }
        public CdObjectPlacementRuntime.Snapshot actualCut() { return cut; }
        public CdObjectState expectedSource() { return cut.pool().slots().get(cursor.whole-32); }
        public boolean isCurrent() { return cursor.pending==this; }
        /** Root must ALSO check original World invocation, exact source/pool and full State/Environment,
         * prepare all fallible work, then claim immediately before adoption. This claim alone is no grant. */
        public void claim() {
            if (!isCurrent()||claimed) throw new IllegalStateException("Stale/repeated TT call");
            claimed=true;
        }
        /** Adopt only the current original SolidObject's already prepared pool prefix. Root must
         * authenticate the actual kernel invocation and prebuild the full player/global publication
         * before calling. A public/equal/retained Outcome is never that authority. NativeStopped
         * prefixes may be adopted here before Root rethrows their original failure. */
        public void adoptSolid(CdSolidObject.Outcome outcome) {
            if(!isCurrent()||!claimed||solidAfter!=null||!site.equals("SolidObject:boss door"))
                throw new IllegalStateException("No current original TT door Solid call");
            Objects.requireNonNull(outcome);
            CdObjectState object;List<CdObjectState> others;CdSolidObject.Entry entry;
            if(outcome instanceof CdSolidObject.Completed done) {
                object=done.completedResult().object();others=done.completedResult().otherSlots();entry=done.completedResult().entry();
            } else if(outcome instanceof CdSolidObject.NativeStopped stopped) {
                object=stopped.prefix().object();others=stopped.prefix().otherSlots();entry=stopped.prefix().entry();
            } else throw new IllegalArgumentException("Unknown Solid outcome");
            if(entry!=CdSolidObject.Entry.SOLID_OBJECT||object.identity().wholeIndex()!=cursor.whole)
                throw new IllegalArgumentException("Different original TT Solid source/entry");
            cursor.fence(this,false);
            var original=expectedSource();
            if(((original.flags()^object.flags())&~0x28)!=0
                    ||!object.equals(original.moveByte(CdObjectState.ByteField.FLAGS,object.flags())
                        .moveByte(CdObjectState.ByteField.SOLID_TYPE,object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE)))
                    ||(object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE)!=0
                        &&object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE)!=original.unsignedByte(CdObjectState.ByteField.SOLID_TYPE)))
                throw new IllegalArgumentException("TT Solid changed unrelated source storage");
            for(var other:others) {
                var before=cut.pool().slots().get(other.identity().wholeIndex()-32);
                if(!other.equals(before)&&!other.equals(before.moveByte(CdObjectState.ByteField.FLAGS,before.flags()&~8)))
                    throw new IllegalArgumentException("TT Solid changed unrelated support storage");
            }
            var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(others);
            var prepared=CdObjectPool.overlay(cut.pool(),images); // validate EVERY image/owner/duplicate before adoption
            cursor.pool.validateRestore(prepared);
            cursor.pool.restore(prepared);
            solidAfter=prepared;
        }
    }
    /** All methods preserve the complete placement cut; draw may clear source sprite bit7, and the
     * original door Solid call may adopt its prevalidated typed kernel prefix through Call.adoptSolid.
     * Root fences its external owners, retains failed prefixes, and implements math with CdPlayerMath,
     * score with the shared score kernel and solid with CdSolidObject. Missing methods fail at reach. */
    public interface Ports {
        default int read(Call c,Field f) { throw missing(c); }
        default void store(Call c,Field f,int value) { throw missing(c); }
        default void sound(Call c,Sound s) { throw missing(c); }
        default void palette(Call c,int id) { throw missing(c); }
        default void capsulePalette(Call c) { throw missing(c); }
        default void queueBubbleArt(Call c) { throw missing(c); }
        default int random(Call c) { throw missing(c); }
        default CdPlayerMath.SineCosine sineCosine(Call c,int angle) { throw missing(c); }
        default void points100(Call c) { throw missing(c); }
        default void draw(Call c) { throw missing(c); }
        default void solidDoor(Call c) { throw missing(c); }

        default void resetDrown(Call c) { throw missing(c); }
        default CdPlayerTouchResponse.HurtPair hurtPair(Call c) { throw missing(c); }
        default PreparedHurtPair prepareHurtPair(HurtPublication p) { throw missing(p.originalCall()); }
        default void hurtSound(HurtPublication p,CdPlayerTouchResponse.FmSound sound) { throw missing(p.originalCall()); }

        private static UnsupportedOperationException missing(Call c) {
            return new UnsupportedOperationException("Missing original TT owner at "+c.site());
        }
    }
    /** Root's prepared actual full-pair publication. prepare must do every fallible conversion/check.
     * publish adopts that prebuilt original owner operation once; it must not replay prefix.calls(). */
    public interface PreparedHurtPair {
        HurtPublication original();
        CdPlayerTouchResponse.HurtPair before();
        CdPlayerTouchResponse.HurtPair after();
        void publish();
    }
    /** Private-issued live cut, never a constructible Result-based authority. Root must ALSO verify
     * actual private World/Frame/source/Environment and full raw player/global ownership before prepare. */
    public static final class HurtPublication {
        private final Call call;
        private final CdObjectPlacementRuntime.Snapshot cut;
        private final CdPlayerTouchResponse.HurtStep step;
        private final CdPlayerTouchResponse.HurtPair before;
        private boolean adopted;
        private HurtPublication(Call call,CdPlayerTouchResponse.HurtStep step,CdPlayerTouchResponse.HurtPair before) {
            this.call=call;this.step=step;this.before=before;this.cut=call.cursor.owner.placement.capture();
        }
        public Call originalCall() { return call; }
        public CdObjectPlacementRuntime.Snapshot actualCut() { return cut; }
        public CdPlayerTouchResponse.HurtPair before() { return before; }
        public CdPlayerTouchResponse.HurtPrefix reachedPrefix() { return step.prefix(); }
        public boolean isCurrent() { return call.isCurrent()&&call.hurtPublication==this&&step.isCurrent(); }
        public boolean adopted() { return adopted; }
    }
    public static final class UnownedHurtTail extends UnsupportedOperationException {
        private final int wholeIndex;
        private UnownedHurtTail(int whole) { super("TT Hurt returned target "+whole+"; first collision-status write is unowned");wholeIndex=whole; }
        public int wholeIndex() { return wholeIndex; }
    }
    public record Event(String site,String operation,int value) { }
    /** Completed observational prefix only, without a Call, callback or adoption method. */
    public record Result(CdObjectPlacementRuntime.Snapshot state,List<Event> completed) {
        public Result { completed=List.copyOf(completed); }
    }
    final CdObjectPlacementRuntime placement;
    final Assets assets;
    final Ports ports;
    private boolean running;
    public CdR4Boss(CdObjectPlacementRuntime placement,Assets assets,Ports ports) {
        this.placement=Objects.requireNonNull(placement); this.assets=Objects.requireNonNull(assets);
        this.ports=Objects.requireNonNull(ports);
    }
    /** Check against Root's cached component before claiming; an active foreign component is insufficient. */
    public boolean isCurrentCall(Call call) {
        return running&&call!=null&&call.cursor.owner==this&&call.cursor.pending==call&&!call.claimed;
    }
    public boolean isCurrentHurtCall(Call call) {
        return running&&call!=null&&call.cursor.owner==this&&call.isCurrent()&&call.site.equals("sub_20730C");
    }
    public boolean isCurrentHurtPublication(HurtPublication publication) {
        return publication!=null&&isCurrentHurtCall(publication.call)&&publication.isCurrent();
    }
    public Result run(int whole) {
        if(running) throw new IllegalStateException("Reentrant TT family");
        running=true;
        try {
            Cursor c=new Cursor(this,whole);
            switch(c.b(0)) {
                case 0x4A,0x4B -> CdR4BossChase.run(c);
                case 0x4C -> eggman(c);
                case 0x4D -> mobile(c);
                case 0x4E -> CdR4BossMissile.run(c);
                case 0x4F -> CdR4BossBubble.run(c);
                case 0x47 -> CdR4BossDoor.run(c);

                default -> throw new UnsupportedOperationException("Not a TT boss/door/capsule slot");
            }
            return new Result(placement.capture(),c.events);
        } finally { running=false; }
    }
    /** TT SPECIAL tail only, after the actual shared collision scan selected this source slot.
     * Original ingress/boss byte belong to Root. This method is no collision scan or frame admission. */
    public Result special(int whole) {
        if(running) throw new IllegalStateException("Reentrant TT family");
        running=true;
        try {
            Cursor c=new Cursor(this,whole); int raw=c.b(0x20);
            if((raw&0xC0)!=0xC0)throw new IllegalArgumentException("TT tail requires actual SPECIAL group");
            int type=raw&0x3F;
            // The original common SPECIAL owner handles these BEFORE reaching the TT tail.
            // Do not clone its algorithm or manufacture a successful common-prefix completion.
            if(type==0x1F||type==0x23||type==0x33||type==0x3A||type==0x3B)
                throw new UnsupportedOperationException("Original common SPECIAL prefix precedes TT tail");
            {
                int started=c.read(Field.BOSS_STARTED);
                if(started==4&&type==0x3F) {
                    int flags=c.read(Field.PLAYER_FLAGS);
                    if((flags&0x14)!=0) {
                        c.store(Field.PLAYER_FLAGS,flags&~4);
                        c.store(Field.PLAYER_FLAGS,c.read(Field.PLAYER_FLAGS)&~0x10);
                        c.b(0x20,0); c.store(Field.PLAYER_ANIMATION,0x15); c.sound(Sound.INHALE);
                        c.external("PlayerResetDrown",call->{ports.resetDrown(call);return 0;});
                        c.store(Field.PLAYER_Y_SPEED,0x400); c.store(Field.PLAYER_X_SPEED,0xFE00);
                        if(c.read(Field.PLAYER_X)>=c.w(8))c.store(Field.PLAYER_X_SPEED,0x200);
                    }
                } else if((started==4&&type==0x3E)||(started==5&&type==0x3D)) {
                    int target=c.hurt();
                    if(target==CdObjectPool.END_WHOLE_INDEX)throw new UnownedHurtTail(target);
                    c.otherB(target,0x21,c.pool.state(target).unsignedByte(CdObjectState.ByteField.COLLISION_STATUS)+1);
                } else if(started!=0&&started!=4&&started!=5&&type>=0x3C&&type<=0x3F) {
                    // Generic enemy reaction is an existing player-owner responsibility.
                    throw new UnsupportedOperationException("Non-TT SPECIAL enemy reaction belongs to player owner");
                }
            }
            return new Result(placement.capture(),c.events);
        } finally {running=false;}
    }
    static final class Cursor {
        final CdR4Boss owner; final CdObjectPool pool; final int whole; final List<Event> events=new ArrayList<>();
        Call pending;
        Cursor(CdR4Boss owner,int whole) { this.owner=owner;pool=owner.placement.pool();this.whole=whole;pool.state(whole); }
        CdObjectState s() { return pool.state(whole); }
        int b(int offset) { return Byte.toUnsignedInt(s().copyBytes()[offset]); }
        int w(int offset) { return word(s(),offset); }
        int sw(int offset) { return (short)w(offset); }
        int l(int offset) { return (w(offset)<<16)|w(offset+2); }
        void b(int offset,int value) { pool.write(byteStore(s(),offset,value)); }
        void w(int offset,int value) { pool.write(wordStore(s(),offset,value)); }
        void l(int offset,int value) {
            for(var f:CdObjectState.LongField.values())if(f.byteOffset()==offset){pool.write(s().moveLong(f,value));return;}
            w(offset,value>>>16);w(offset+2,value);
        }
        void ab(int offset,int value) { b(offset,b(offset)+value); }
        void aw(int offset,int value) { w(offset,w(offset)+value); }
        void al(int offset,int value) { l(offset,l(offset)+value); }
        void bit(int offset,int bit,boolean set) { b(offset,set?b(offset)|(1<<bit):b(offset)&~(1<<bit)); }
        void otherB(int target,int offset,int value) { pool.write(byteStore(pool.state(target),offset,value)); }
        void otherW(int target,int offset,int value) { pool.write(wordStore(pool.state(target),offset,value)); }
        void otherL(int target,int offset,int value) { otherW(target,offset,value>>>16);otherW(target,offset+2,value); }
        int link(int offset) { return linkWord(w(offset)); }
        int linkWord(int raw) {
            // MOVEA.W sign extension: only aligned dynamic addresses are in this semantic owner.
            int index=(raw-0xD000)/0x40;
            if(raw<0xD800||raw>0xEFC0||(raw&0x3F)!=0)
                throw new UnsupportedOperationException("Unowned raw TT link $"+Integer.toHexString(raw));
            return pool.state(index).identity().wholeIndex();
        }
        int raw(int index) { return pool.state(index).identity().nativeAddress24()&65535; }
        int allocate() { var found=pool.findObjSlot();events.add(new Event("SpawnObject","slot",found.orElse(-1)));return found.orElse(-1); }
        void delete() {pool.deleteObject(whole);events.add(new Event("DeleteObject","slot",whole));}
        <T>T external(String site,java.util.function.Function<Call,T> action) {
            Call call=new Call(this,site);pending=call;
            Throwable failure=null;
            try { T result=action.apply(call); if(!call.claimed)throw new IllegalStateException("Unclaimed TT call "+site);
                events.add(new Event(site,"returned",0));return result;
            } catch(RuntimeException|Error e) { failure=e;throw e;
            } finally {
                try { postCall(call,site.equals("DrawObject")); }
                catch(RuntimeException|Error e) {if(failure!=null){if(failure!=e)failure.addSuppressed(e);}else throw e;}
                finally {pending=null;}
            }
        }
        void fence(Call call,boolean draw) {
            var before=call.cut;var after=owner.placement.capture();
            if(!before.status().equals(after.status())||!Arrays.equals(before.savedFlags().copy(),after.savedFlags().copy()))
                throw new IllegalStateException("TT port changed placement metadata");
            for(int n=32;n<128;n++) {
                var expected=(call.hurtAfter!=null?call.hurtAfter:call.solidAfter==null?before.pool():call.solidAfter).slots().get(n-32);var actual=after.pool().slots().get(n-32);
                if(draw&&n==whole) {
                    if(!actual.equals(expected)&&!actual.equals(expected.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,expected.spriteFlags()&127)))
                        throw new IllegalStateException("TT draw changed source beyond bit7");
                }else if(!expected.equals(actual))throw new IllegalStateException("TT port changed pool slot "+n);
            }
        }
        CdPlayerTouchResponse.HurtPair hurtPair(Call call) {
            Throwable failure=null;
            try {return Objects.requireNonNull(owner.ports.hurtPair(call),"actual complete Hurt pair");}
            catch(RuntimeException|Error e){failure=e;throw e;}
            finally {try {fence(call,false);}catch(RuntimeException|Error e){if(failure!=null){if(failure!=e)failure.addSuppressed(e);}else throw e;}}
        }
        void hurtPairFence(Call call) {
            if(call.hurtPairAfter!=null&&!call.hurtPairAfter.equals(hurtPair(call)))
                throw new IllegalStateException("Original Hurt changed undeclared actor/global pair");
        }
        void postCall(Call call,boolean draw) {
            Throwable first=null;
            try {hurtPairFence(call);}catch(RuntimeException|Error e){first=e;}
            try {fence(call,draw);}catch(RuntimeException|Error e){if(first==null)first=e;else if(first!=e)first.addSuppressed(e);}
            if(first instanceof RuntimeException r)throw r;
            if(first!=null)throw (Error)first;
        }
        int hurt() {
            return external("sub_20730C",call->{
                var before=hurtPair(call);call.hurtPairAfter=before;
                int[] target={-1};
                var outcome=CdPlayerTouchResponse.visitGuardedHurt(before,call.expectedSource(),call.cut.pool(),call,step->{
                    step.claim(call);
                    var previous=call.hurtPairAfter;
                    if(!previous.equals(hurtPair(call)))throw new IllegalStateException("Stale original Hurt pair");
                    var prefix=step.prefix();
                    // Shared Hurt may write only a dirty ID-zero ring-loss slot's ID/X/Y words.
                    for(var image:prefix.changedSlots()) {
                        var original=call.cut.pool().slots().get(image.identity().dynamicIndex());
                        var expected=original.moveByte(CdObjectState.ByteField.ID,0x11)
                            .moveWord(CdObjectState.WordField.X,before.actor().motion().centreX16_16()>>>16)
                            .moveWord(CdObjectState.WordField.Y,before.actor().motion().centreY16_16()>>>16);
                        if(original.id()!=0||!image.equals(expected))throw new IllegalStateException("Undeclared shared Hurt pool write");
                    }
                    var preparedPool=CdObjectPool.overlay(call.cut.pool(),prefix.changedSlots());
                    pool.validateRestore(preparedPool);
                    var publication=new HurtPublication(call,step,previous);
                    call.hurtPublication=publication;
                    try {
                        var prepared=Objects.requireNonNull(owner.ports.prepareHurtPair(publication),"prepared original Hurt owner publication");
                        if(prepared.original()!=publication||!previous.equals(prepared.before())||!prefix.pair().equals(prepared.after()))
                            throw new IllegalStateException("Wrong prepared original Hurt pair");
                        if(!previous.equals(hurtPair(call)))throw new IllegalStateException("Hurt pair changed during preparation");
                        fence(call,false);
                        // All fallible validation/materialization above precedes actual owner adoption.
                        if(!call.claimed)call.claim();
                        pool.restore(preparedPool);call.hurtAfter=preparedPool;
                        call.hurtPairAfter=prefix.pair();publication.adopted=true;
                        Throwable failure=null;
                        try {prepared.publish();}
                        catch(RuntimeException|Error e){failure=e;throw e;}
                        finally { try {postCall(call,false);}
                            catch(RuntimeException|Error e){if(failure!=null){if(failure!=e)failure.addSuppressed(e);}else throw e;} }
                        if(step.cut()==CdPlayerTouchResponse.HurtCut.SOUND) {
                            Throwable soundFailure=null;
                            try {owner.ports.hurtSound(publication,step.sound().orElseThrow());}
                            catch(RuntimeException|Error e){soundFailure=e;throw e;}
                            finally {try {postCall(call,false);}
                                catch(RuntimeException|Error e){if(soundFailure!=null){if(soundFailure!=e)soundFailure.addSuppressed(e);}else throw e;} }
                        } else target[0]=step.returnedTargetWholeIndex();
                    } finally {call.hurtPublication=null;}
                });
                if(outcome.failure().isPresent()) {
                    var fault=outcome.failure().orElseThrow();
                    if(fault instanceof RuntimeException r)throw r;throw (Error)fault;
                }
                if(target[0]<32||target[0]>128)throw new IllegalStateException("No actual shared Hurt return");
                return target[0];
            });
        }
        int read(Field f) {int v=external("read:"+f,c->owner.ports.read(c,f));
            if((v&~(f.bits==8?255:65535))!=0)throw new IllegalArgumentException("Unknown/out-of-width TT "+f);return v;}
        void store(Field f,int v) {int value=v&(f.bits==8?255:65535);external("store:"+f,c->{owner.ports.store(c,f,value);return 0;});}
        void sound(Sound sound) {external("sound:"+sound,c->{owner.ports.sound(c,sound);return 0;});}
        void palette(int id) {external("LoadPalette",c->{owner.ports.palette(c,id);return 0;});}
        int random() {return external("Random",c->owner.ports.random(c));}
        CdPlayerMath.SineCosine sine(int angle) {return Objects.requireNonNull(external("SineCosine",c->owner.ports.sineCosine(c,angle&255)));}
        CdAssetBytes table(Table table) {return Objects.requireNonNull(owner.assets.table(table),"Missing "+table);}
        void init(Part part,int routine,int layer,int width,int height,int tile) {
            b(0x24,routine);b(1,4);b(0x18,layer);b(0x17,width);b(0x16,height);w(2,tile);
            // Mapping operand remains lazy, after all preceding native initialization stores.
            l(4,owner.assets.mapping(part));
        }
        void animate(Part part) {
            var s=s();var next=CdObjectAnimation.step(new CdObjectAnimation.State(b(0x1C),b(0x1D),b(0x1B),b(0x1E),b(0x1A),b(1),b(0x22),b(0x24),b(0x25)),owner.assets.animation(part));
            // Same immutable existing animation kernel, no second script interpreter.
            pool.write(s.moveByte(CdObjectState.ByteField.ANIMATION,next.animationId())
                .moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION,next.previousId())
                .moveByte(CdObjectState.ByteField.ANIMATION_FRAME,next.scriptIndex())
                .moveByte(CdObjectState.ByteField.ANIMATION_TIME,next.timer())
                .moveByte(CdObjectState.ByteField.MAP_FRAME,next.mapFrame())
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS,next.spriteFlags())
                .moveByte(CdObjectState.ByteField.FLAGS,next.statusFlags())
                .moveByte(CdObjectState.ByteField.ROUTINE,next.routine())
                .moveByte(CdObjectState.ByteField.ROUTINE_2,next.routine2()));
        }
        void draw() {external("DrawObject",c->{owner.ports.draw(c);return 0;});}
        void resetAnimation(int id) {b(0x1C,id);b(0x1A,0);b(0x1B,0);b(0x1E,0);b(0x1F,0);}
        void orbit() {
            var trig=sine(b(0x34));
            // boss.asm:1083-1100 MULS.L-result then ASR.W, NOT ASR.L. Preserve low-word overflow.
            int dx=(short)(sw(0x3C)*trig.cosine())>>8;
            int dy=(short)(sw(0x10)*trig.sine())>>8;
            w(0x38,dx);w(0x3A,dy);aw(8,dx);aw(0xC,dy);
        }
        void badRoutine() {throw new UnsupportedOperationException("TT native routine directory "+b(0x24));}
    }
    static int word(CdObjectState s,int at) {var bytes=s.copyBytes();return (Byte.toUnsignedInt(bytes[at])<<8)|Byte.toUnsignedInt(bytes[at+1]);}
    static CdObjectState wordStore(CdObjectState s,int at,int value) {
        for(var f:CdObjectState.WordField.values())if(f.byteOffset()==at)return s.moveWord(f,value);
        throw new IllegalArgumentException("Unowned TT storage word "+at);
    }
    static CdObjectState byteStore(CdObjectState s,int at,int value) {
        for(var f:CdObjectState.ByteField.values())if(f.byteOffset()==at)return s.moveByte(f,value);
        int base=at&~1,old=word(s,base);return wordStore(s,base,(at&1)==0?((value&255)<<8)|(old&255):(old&0xFF00)|(value&255));
    }
    private static void mobile(Cursor c) {
        if(c.b(0x24)==0)c.init(Part.MOBILE,2,4,0x20,0x1C,0x31E);
        else if(c.b(0x24)!=2)c.badRoutine();
        int parent=c.link(0x2E);c.w(8,word(c.pool.state(parent),8));c.w(0xC,word(c.pool.state(parent),0xC));c.draw();
    }
    private static void eggman(Cursor c) {
        c.bit(0x2C,6,false);
        if(c.b(0x2B)!=0){c.ab(0x2B,-1);if(c.b(0x2B)==0)c.resetAnimation(0);}
        else if(c.b(0x1C)==0&&(c.read(Field.PLAYER_HURT_TIMER)!=0||c.read(Field.PLAYER_ROUTINE)==6))reaction(c);
        switch(c.b(0x24)) {
            case 0 -> {c.init(Part.EGGMAN,2,4,0x20,0x2C,0x31E);c.b(0x21,2);int n=c.allocate();
                if(n>=0){c.w(0x30,c.raw(n));c.otherW(n,0x2E,c.raw(c.whole));c.otherB(n,0,0x4D);c.otherW(n,8,c.w(8));c.otherW(n,0xC,c.w(0xC));}entry(c);}
            case 2 -> entry(c);
            case 4 -> {if(escape(c))return;}
            case 6 -> {settle(c);c.ab(0x2A,1);int timer=c.b(0x2A);
                if(timer==10){int n=bubble(c); // Native unconditional write after failed helper: a1 is beyond pool.
                    if(n<0)throw new UnsupportedOperationException("sub_20CEA2 failed allocator output is outside dynamic pool");c.otherB(n,0x25,1);}
                if(timer==20||timer>=30){c.sound(Sound.BUBBLE);c.b(0x2A,0);int n=bubble(c);
                    if(n<0)throw new UnsupportedOperationException("loc_20C4E6 failed allocator output is outside dynamic pool");
                    c.otherB(n,0x2D,c.b(0x2D));c.ab(0x2D,1);if(c.b(0x2D)==16)c.b(0x24,8);}}
            case 8 -> {settle(c);c.ab(0x2A,1);if(c.b(0x2A)==0x96){c.b(0x2A,0);c.b(0x24,10);}}
            case 10 -> {c.ab(0x2A,1);if(c.b(0x2A)==0x96){c.b(0x24,12);c.w(0x3C,0x60);c.w(0x10,0x40);c.w(0x34,0);c.w(0x38,0x60);c.w(0x3A,0);c.b(0x2A,0);}}
            case 12,14 -> {int routine=c.b(0x24);hit(c);c.aw(0x34,routine==12?0x100:-0x100);
                if(c.w(0x34)==(routine==12?0x800:0x7800))missiles(c);
                if(c.w(0x34)==(routine==12?0x8000:0)){c.b(0x2A,0);c.b(0x24,16);c.bit(0x2C,4,true);}
                else{c.aw(8,-c.sw(0x38));c.aw(0xC,-c.sw(0x3A));c.orbit();}}
            case 16 -> pause(c);
            case 18 -> defeat(c);
            case 20 -> {c.ab(0x2A,1);if(c.b(0x2A)>=60){c.al(8,0x1C000);if(c.sw(8)>=0xB30){c.b(0x24,4);c.pool.deleteObject(c.link(0x30));}}}
            case 22 -> attack(c);
            case 24,26 -> { }
            default -> c.badRoutine();
        }
        c.animate(Part.EGGMAN);c.draw();
    }
    private static void reaction(Cursor c) {c.b(0x2B,0x78);c.resetAnimation(1);}
    private static void settle(Cursor c) {if(c.sw(0xC)<0x520)c.al(0xC,0x4650);else{c.l(8,0xAB00000);c.l(0xC,0x5200000);}}
    private static void entry(Cursor c) {
        c.store(Field.RIGHT_BOUND,0x9B0);c.store(Field.TARGET_RIGHT_BOUND,0x9B0);
        int x=c.read(Field.PLAYER_X),left=(x-0xA0)&65535;
        if((short)left<(short)c.read(Field.LEFT_BOUND))return;
        if((short)c.read(Field.PLAYER_X)>=0xA50){c.sound(Sound.BOSS_MUSIC);c.b(0x24,6);c.store(Field.RIGHT_BOUND,0x9B0);c.store(Field.TARGET_RIGHT_BOUND,0x9B0);left=0x9B0;}
        c.store(Field.LEFT_BOUND,left);c.store(Field.TARGET_LEFT_BOUND,left);
    }
    private static int bubble(Cursor c) {int n=c.allocate();if(n>=0){c.otherW(n,0x2E,c.raw(c.whole));c.otherB(n,0,0x4F);c.otherW(n,8,c.w(8));c.otherW(n,0xC,0x5B8);}return n;}
    private static void hit(Cursor c) {if(c.b(0x20)!=0)return;c.b(0x20,0x3C);if(c.b(0x21)!=1)return;
        c.b(0x2A,0);c.b(0x24,18);c.bit(0x2C,7,true);c.resetAnimation(2);}
    private static void pause(Cursor c) {
        c.bit(0x2C,4,false);c.bit(0x2C,5,false);hit(c);c.ab(0x2A,1);
        if(c.b(0x2A)==0x30){int n=c.link(0x30);c.otherB(n,1,c.pool.state(n).spriteFlags()^1);c.otherB(n,0x22,c.pool.state(n).flags()^1);c.bit(1,0,(c.b(1)&1)==0);c.bit(0x22,0,(c.b(0x22)&1)==0);}
        if(c.b(0x2A)!=0x60)return;c.b(0x2A,0);boolean flip=(c.b(0x22)&1)!=0;c.bit(0x2C,3,flip);c.b(0x24,flip?14:12);
        // Native finite threshold bytes are reached lazily; corrupt cursor faults, never loops forever.
        for(int n=0;n<256;n++){int threshold=c.table(Table.BUBBLE_THRESHOLDS).signedByte(c.b(0x33));if(threshold<0||threshold<(byte)c.b(0x2D))return;c.ab(0x33,1);c.b(0x24,22);}
        throw new UnsupportedOperationException("Unterminated TT bubble threshold table");
    }
    private static void attack(Cursor c) {
        c.ab(0x2A,1);int timer=c.b(0x2A);
        if(timer==20){reaction(c);c.b(0x32,0);c.bit(0x2C,5,true);}else if(timer==21)c.bit(0x2C,5,false);else if(timer==45)missiles(c);
        boolean right=(c.b(0x2C)&8)!=0;c.al(8,right?0x10000:-0x10000);
        if(right?c.sw(8)<0xAB0:c.sw(8)>0x9F0)return;
        c.l(0x38,-c.l(0x38));c.aw(0x34,-0x8000);c.l(8,0xA500000);c.al(8,c.l(0x38));c.l(0xC,0x5200000);c.b(0x2A,0);c.b(0x24,16);c.bit(0x2C,4,true);
    }
    private static void defeat(Cursor c) {
        c.ab(0x2A,1);int t=c.b(0x2A);
        if(t<60&&t%4==0){int at=((t/4)%10)*4,n=c.allocate();if(n>=0){c.otherB(n,0x25,255);c.otherB(n,0,0x18);c.otherW(n,8,c.w(8));c.otherW(n,0xC,c.w(0xC));
                c.otherW(n,8,word(c.pool.state(n),8)+(short)c.table(Table.EXPLOSION_OFFSETS).unsignedWord(at));
                c.otherW(n,0xC,word(c.pool.state(n),0xC)+(short)c.table(Table.EXPLOSION_OFFSETS).unsignedWord(at+2));c.sound(Sound.EXPLOSION);}}
        if(t==60){c.b(0x1C,3);c.b(0x2B,255);c.b(0x1A,0);c.b(0x1B,0);c.b(0x1E,0);c.b(0x1F,0);c.w(0x30);c.b(0x1A,1);}
        if(t==61){c.b(0x2A,0);c.b(0x24,20);c.bit(1,0,true);c.bit(0x22,0,true);int n=c.link(0x30);c.otherB(n,1,c.pool.state(n).spriteFlags()|1);c.otherB(n,0x22,c.pool.state(n).flags()|1);c.external("AddPoints100",call->{c.owner.ports.points100(call);return 0;});}
    }
    private static boolean escape(Cursor c) {
        // All four source WORD reads happen before the first bound write, even unused d0,d2,d3.
        var bounds=c.table(Table.ESCAPE_BOUNDS);bounds.unsignedWord(0);int right=(short)bounds.unsignedWord(2);bounds.unsignedWord(4);bounds.unsignedWord(6);
        c.store(Field.RIGHT_BOUND,c.read(Field.RIGHT_BOUND)+6);c.store(Field.TARGET_RIGHT_BOUND,c.read(Field.TARGET_RIGHT_BOUND)+6);
        if(right>(short)c.read(Field.RIGHT_BOUND))return true; // native stack cut suppresses animation/draw on either path
        c.store(Field.BOSS_STARTED,0);c.store(Field.RIGHT_BOUND,right);c.store(Field.TARGET_RIGHT_BOUND,right);
        c.sound(c.read(Field.GOOD_FUTURE)!=0?Sound.GOOD_MUSIC:Sound.BAD_MUSIC);
        c.external("LoadCapsulePalette",call->{c.owner.ports.capsulePalette(call);return 0;});c.delete();return true;
    }
    static void missiles(Cursor c) {
        for(int ordinal=0;ordinal<4;ordinal++){int n=c.allocate();if(n<0)return;
            c.otherW(n,0x2E,c.raw(c.whole));c.otherB(n,0,0x4E);c.otherW(n,8,c.w(8));c.otherW(n,0xC,c.w(0xC));
            int at=ordinal*10;var data=c.table(Table.MISSILE_DECLARATIONS);int dx=data.signedByte(at),dy=data.signedByte(at+1);int vx=(int)data.unsignedLong(at+2),vy=(int)data.unsignedLong(at+6);
            if((c.b(0x22)&1)!=0){dx=-dx;vx=-vx;}c.otherW(n,0x38,dx);c.otherW(n,0x3A,dy);c.otherL(n,0x3C,vx);c.otherL(n,0x10,vy);
        }
    }
}
