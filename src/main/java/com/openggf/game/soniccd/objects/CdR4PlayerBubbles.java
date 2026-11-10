package com.openggf.game.soniccd.objects;

import java.util.Objects;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.Value.*;

/** Reserved air controller and its physical children. N=1ecc5f3,
 * src/objects/r4/player_bubbles.asm:3-332. No FixBugs alternate, no generic KillPlayer.
 * Root owns actual reserved/dynamic slots, RNG, shared native calls, current art and full capture.
 * Calls/stores execute in source order; completed metadata never replays them.
 */
public final class CdR4PlayerBubbles {
    private CdR4PlayerBubbles() { }
    /** Only the two PlayFmSound sites in PlayerBubblesObject_0_RoutineA,
     * N1ecc5f3 src/objects/r4/player_bubbles.asm:195-221. Values are not owner authority. */
    public enum FmSound {
        AIR_WARNING(0xDF), DROWNING(0xB6);
        private final int nativeId;
        FmSound(int nativeId){this.nativeId=nativeId;}
        public int nativeId(){return nativeId;}
    }
    /** Actual World supplies its private dispatch, same-reader source and complete-cut checks.
     * A caller-created implementation admits no gameplay by itself. */
    public interface FmOwner {
        long generation();
        void before(FmCall call);
        void sound(FmCall call);
        void after(FmCall call);
    }
    /** Private-issued synchronous occurrence; it cannot be manufactured from a sound ID,
     * captured, reopened or used on another thread/owner/generation. */
    public static final class FmCall {
        private final FmInvocation invocation;
        private final FmSound sound;
        private final long ordinal;
        private boolean open=true,consumed,completed;
        private FmCall(FmInvocation invocation,FmSound sound,long ordinal){this.invocation=invocation;this.sound=sound;this.ordinal=ordinal;}
        public void requireCurrent(FmOwner actual){
            if(actual!=invocation.owner||!open||!invocation.open||invocation.current!=this
                    ||Thread.currentThread()!=invocation.thread||actual.generation()!=invocation.generation)
                throw new IllegalStateException("Foreign/stale/off-thread original PlayerBubbles FM occurrence");
        }
        public Slot slot(){requireCurrent(invocation.owner);return invocation.slot;}
        public Calls calls(){requireCurrent(invocation.owner);return invocation.calls;}
        public BubbleData data(){requireCurrent(invocation.owner);return invocation.data;}
        public FmSound sound(){requireCurrent(invocation.owner);return sound;}
        public long ordinal(){requireCurrent(invocation.owner);return ordinal;}
        public void consume(FmOwner actual){requireCurrent(actual);if(consumed)throw new IllegalStateException("Repeated original PlayerBubbles FM request");consumed=true;}
        /** Inert completed-prefix observations; neither boolean can reopen or publish a call. */
        public boolean consumed(){return consumed;}
        public boolean completed(){return completed;}
    }
    private static final class FmInvocation {
        final Slot slot;final Calls calls;final BubbleData data;final FmOwner owner;
        final Thread thread=Thread.currentThread();final long generation;
        boolean open=true;long ordinal;FmCall current;
        FmInvocation(Slot slot,Calls calls,BubbleData data,FmOwner owner){
            this.slot=Objects.requireNonNull(slot);this.calls=Objects.requireNonNull(calls);
            this.data=Objects.requireNonNull(data);this.owner=Objects.requireNonNull(owner);generation=owner.generation();
        }
        void sound(FmSound sound){
            if(!open||current!=null||Thread.currentThread()!=thread||owner.generation()!=generation)
                throw new IllegalStateException("Retired or recursive original PlayerBubbles FM invocation");
            var call=new FmCall(this,sound,Math.incrementExact(ordinal));ordinal=call.ordinal;current=call;Throwable first=null;boolean accepted=false;
            try{owner.before(call);accepted=true;call.requireCurrent(owner);owner.sound(call);call.requireCurrent(owner);if(!call.consumed)throw new IllegalStateException("Original FM owner did not consume its occurrence");call.completed=true;}
            catch(RuntimeException|Error original){first=original;throw original;}
            finally{
                try{if(accepted)owner.after(call);}
                catch(RuntimeException|Error guard){if(first!=null){if(first!=guard)first.addSuppressed(guard);}else throw guard;}
                finally{call.open=false;current=null;}
            }
        }
        void close(){open=false;current=null;}
    }
    /** Original live World path. The existing algorithm is shared with the borrowed pure API;
     * only its actual sound leaf is bound to the private synchronous occurrence above. */
    public static void runOriginal(Slot o,Calls s,BubbleData data,FmOwner owner){
        var invocation=new FmInvocation(o,s,data,owner);
        try{run(o,s,data,invocation);}finally{invocation.close();}
    }
    private static void sound(Calls s,FmInvocation invocation,FmSound sound){
        if(invocation==null)s.sound(sound.nativeId());else invocation.sound(sound);
    }
    /** Finite PlayerResetDrown call, also usable at a corroborated boss bubble's genuine source cut. */
    public static void resetAir(Calls s) {
        if (read(s, DROWN_TIMER) <= 0xC) {
            int music = read(s, ZONE_WORD) == 0x103 ? 0x86 : 0x82;
            if (read(s, INVINCIBLE) != 0) music = 0x87;
            if (read(s, BOSS_STARTED) != 0) music = 0x8C;
            s.music(music);
        }
        write(s, DROWN_TIMER, 0x1E); write(s.reservedBubbles(), B.COUNTDOWN, 0);
    }
    public static void run(Slot o, Calls s, BubbleData data) { run(o,s,data,null); }
    private static void run(Slot o,Calls s,BubbleData data,FmInvocation invocation) {
        int routine = read(o, B.ROUTINE);
        switch (routine) {
            case 0 -> {
                write(o, B.ROUTINE, read(o, B.ROUTINE) + 2);
                o.write(L.MAPPINGS, data.mappingsBits()); write(o, W.TILE, read(o, W.TILE) | 0x848A);
                write(o, B.SPRITE_FLAGS, 0x84); write(o, B.WIDTH_2, 0x10); write(o, B.LAYER, 1);
                int subtype = read(o, B.SUBTYPE);
                if ((byte) subtype < 0) {
                    write(o, B.ROUTINE, read(o, B.ROUTINE) + 8);
                    o.write(L.MAPPINGS, data.mappingsBits()); write(o, W.TILE, read(o, W.TILE) | 0x48A);
                    write(o, B.RELOAD, subtype & 0x7F); controller(o, s, invocation); return;
                }
                write(o, B.ANIM, subtype); write(o, W.ANCHOR_X, read(o, W.X)); write(o, W.VY, -0x88);
                s.animate(o, Animation.PLAYER_BUBBLES); rise(o, s, data);
            }
            case 2 -> { s.animate(o, Animation.PLAYER_BUBBLES); rise(o, s, data); }
            case 4 -> rise(o, s, data);
            case 6, 14 -> burst(o, s);
            case 8, 16 -> s.delete(o);
            case 10 -> controller(o, s, invocation);
            case 12 -> {
                if (read(s, DROWN_TIMER) > 0xC) { s.delete(o); return; }
                int time = (read(o, W.TIMER) - 1) & 0xFFFF; write(o, W.TIMER, time);
                if (time == 0) {
                    write(o, B.ROUTINE, 14); write(o, B.ANIM, read(o, B.ANIM) + 7); burst(o, s);
                } else {
                    s.animate(o, Animation.PLAYER_BUBBLES);
                    if ((byte) read(o, B.SPRITE_FLAGS) < 0) s.draw(o); else s.delete(o);
                }
            }
            default -> throw new UnsupportedOperationException("PlayerBubbles routine " + routine);
        }
    }
    private static void rise(Slot o, Calls s, BubbleData data) {
        if (read(s, WATER_Y) >= read(o, W.Y)) {
            write(o, B.ROUTINE, 6); write(o, B.ANIM, read(o, B.ANIM) + 7);
            read(o, B.ANIM); // Native CMP.B #$D: both branches reach Routine6; retain the reached read.
            burst(o, s); return;
        }
        if (read(s, WATER_CURRENT) != 0) write(o, W.ANCHOR_X, read(o, W.ANCHOR_X) + 4);
        int angle = read(o, B.ANGLE); write(o, B.ANGLE, angle + 1);
        int wobble = data.wobble().signedByte(angle & 0x7F);
        write(o, W.X, wobble + read(o, W.ANCHOR_X));
        screenNumber(o, s); s.move(o);
        if ((byte) read(o, B.SPRITE_FLAGS) < 0) s.draw(o); else s.delete(o);
    }
    private static void burst(Slot o, Calls s) {
        screenNumber(o, s); s.animate(o, Animation.PLAYER_BUBBLES); s.draw(o);
    }
    private static void screenNumber(Slot o, Calls s) {
        if (read(o, W.TIMER) == 0) return;
        int time = (read(o, W.TIMER) - 1) & 0xFFFF; write(o, W.TIMER, time);
        if (time != 0 || read(o, B.ANIM) >= 7) return;
        write(o, W.TIMER, 0xF); write(o, W.VY, 0); write(o, B.SPRITE_FLAGS, 0x80);
        write(o, W.X, read(o, W.X) - read(s, CAMERA_X) + 0x80);
        // obj.x+2 is SCREEN Y WORD, never logical obj.y.
        write(o, W.SCREEN_Y, read(o, W.Y) - read(s, CAMERA_Y) + 0x80);
        write(o, B.ROUTINE, 0xC);
    }
    private static void controller(Slot o, Calls s,FmInvocation invocation) {
        if (read(o, W.DEATH_DELAY) != 0) {
            int delay = (read(o, W.DEATH_DELAY) - 1) & 0xFFFF; write(o, W.DEATH_DELAY, delay);
            if (delay == 0) { write(s.player(), B.ROUTINE, 6); return; }
            s.move(s.player()); write(s.player(), W.VY, read(s.player(), W.VY) + 0x10);
            emitIfDue(o, s); return;
        }
        if (read(s.player(), B.ROUTINE) >= 6 || (read(s.player(), B.FLAGS) & 0x40) == 0) return;
        int time = (read(o, W.TIMER) - 1) & 0xFFFF; write(o, W.TIMER, time);
        if ((short) time >= 0) { emitIfDue(o, s); return; }
        write(o, W.TIMER, 0x3B); write(o, W.BURST, 1);
        write(o, B.REMAINING, s.random() & 1);
        int air = read(s, DROWN_TIMER);
        if (air != 0x19 && air != 0x14 && air != 0xF && air <= 0xC) {
            if ((read(s, STAGE_FRAMES_LOW) & 0x20) == 0) sound(s,invocation,FmSound.AIR_WARNING);
            int count = (read(o, B.COUNTDOWN) - 1) & 0xFF; write(o, B.COUNTDOWN, count);
            if ((byte) count < 0) {
                write(o, B.COUNTDOWN, read(o, B.RELOAD));
                write(o, B.BURST_HIGH, read(o, B.BURST_HIGH) | 0x80);
            }
        }
        // SUBQ.W/BCC tests borrow, not sign; $8000 does not start drowning.
        int previous = read(s, DROWN_TIMER); write(s, DROWN_TIMER, previous - 1);
        if (previous == 0) {
            resetAir(s); write(s.player(), B.CONTROL, 0x81); sound(s,invocation,FmSound.DROWNING);
            write(o, B.REMAINING, 0xA); write(o, W.BURST, 1); write(o, W.DEATH_DELAY, 120);
            s.setPlayerGround();
            Slot p = s.player(); write(p, B.ANIM, 0x17); write(p, B.FLAGS, read(p, B.FLAGS) | 2);
            write(p, B.TILE_HIGH, read(p, B.TILE_HIGH) | 0x80); write(p, B.LAYER, 0);
            write(p, W.VY, 0); write(p, W.VX, 0); write(p, W.GROUND_SPEED, 0); write(s, SCROLL_LOCK, 1);
            return;
        }
        emit(o, s); // Second-boundary bypasses the emission timer test, even on allocation failure.
    }
    private static void emitIfDue(Slot o, Calls s) {
        if (read(o, W.BURST) == 0) return;
        int time = (read(o, W.EMIT_TIMER) - 1) & 0xFFFF; write(o, W.EMIT_TIMER, time);
        if ((short) time < 0) emit(o, s);
    }
    private static void emit(Slot o, Calls s) {
        write(o, W.EMIT_TIMER, s.random() & 0xF);
        var spawned = s.spawn(); if (spawned.isEmpty()) return;
        Slot c = spawned.get(); write(c, B.ID, 0x21); write(c, W.X, read(s.player(), W.X));
        int dx = 6;
        if ((read(s.player(), B.FLAGS) & 1) != 0) { dx = -6; write(c, B.ANGLE, 0x40); }
        write(c, W.X, read(c, W.X) + dx); write(c, W.Y, read(s.player(), W.Y)); write(c, B.SUBTYPE, 6);
        if (read(o, W.DEATH_DELAY) != 0) {
            write(o, W.EMIT_TIMER, read(o, W.EMIT_TIMER) & 7);
            write(o, W.EMIT_TIMER, read(o, W.EMIT_TIMER) + 0); // Native ADDI.W #0 is a second store.
            write(c, W.Y, read(s.player(), W.Y) - 0xC); write(c, B.ANGLE, s.random());
            if ((read(s, STAGE_FRAMES) & 3) == 0) write(c, B.SUBTYPE, 0xE);
        } else if ((read(o, B.BURST_HIGH) & 0x80) != 0) {
            write(c, W.TILE, 0x2000); int number = read(s, DROWN_TIMER) >>> 1;
            if ((s.random() & 3) == 0) {
                if (markNumber(o)) number(c, number);
                else { finishEmit(o); return; } // BNE goes directly to loc_20645C.
            }
            if (read(o, B.REMAINING) == 0 && markNumber(o)) number(c, number);
        }
        finishEmit(o);
    }
    private static boolean markNumber(Slot o) {
        int old = read(o, B.BURST_HIGH); write(o, B.BURST_HIGH, old | 0x40); return (old & 0x40) == 0;
    }
    private static void number(Slot c, int n) { write(c, B.SUBTYPE, n); write(c, W.TIMER, 0x1C); }
    private static void finishEmit(Slot o) {
        int left = (read(o, B.REMAINING) - 1) & 0xFF; write(o, B.REMAINING, left);
        if ((byte) left < 0) write(o, W.BURST, 0);
    }
}
