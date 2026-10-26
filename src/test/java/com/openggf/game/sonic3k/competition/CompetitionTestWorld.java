package com.openggf.game.sonic3k.competition;

import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionItemObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.rings.SpillAnimationState;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Independent bounded SST/terrain fixture; never used as production terrain. */
final class CompetitionTestWorld implements S3kCompetitionSession.Host,
        CompetitionArenaObjectInstance.NativeContext, S3kCompetitionSession.ItemTerrain,
        S3kCompetitionDamage.Context {
    // Input oracle transcribed from byte_36FDA and Ani_2PItem, not production defaults.
    static final byte[] SEQUENCE = {0,1,2,3,4,5,6,0,1,2,3,4,5,6,2,3,0,1,2,3,4,5,6,0};
    static final byte[] ITEM_ANIMATION = java.util.HexFormat.of().parseHex(
            "0018001b001e00210027002a002d00300033003900420047"
            + "1f00ff1f01ff1f02ff0703040519ff1f11ff1f06ff1f07ff1f1aff"
            + "05090a0b0cfc050d0e0f10fc1afe010b0807fd09051b1c1d1e1ffc0040");
    static final byte[] GOAL_ANIMATION = {0,2,5,0,1,2,3,(byte) 0xFF};

    final List<S3kCompetitionPlayer> players = new ArrayList<>();
    final List<AbstractObjectInstance> objects = new ArrayList<>();
    final List<Sonic3kSfx> sounds = new ArrayList<>();
    final List<S3kCompetitionSession.Arena> arenaRequests = new ArrayList<>();
    final List<S3kCompetitionSession.Result> results = new ArrayList<>();
    final List<S3kCompetitionSession.PlayerResult> savedTimeAttackRecords = new ArrayList<>();
    final boolean[] occupied = new boolean[128];
    final SpillAnimationState spillAnimation = new SpillAnimationState();
    final int[] lifeAwards = new int[2];
    int extraLifeMusicCalls;
    S3kCompetitionSession session;
    AbstractObjectInstance reservedProjectile;
    int floorY = 256;
    int leftWallDistance;
    int hurtCalls;
    java.util.Map<String, SpriteAnimationSet> animationSets = java.util.Map.of(
            S3kCompetitionRingLoss.ART_KEY,ringSparkleOracle());

    CompetitionTestWorld(S3kCompetitionSession.Mode mode) {
        players.add(new S3kCompetitionPlayer(0, S3kCompetitionPlayer.Character.SONIC, 984, 200));
        if (mode != S3kCompetitionSession.Mode.TIME_ATTACK)
            players.add(new S3kCompetitionPlayer(1, S3kCompetitionPlayer.Character.TAILS, 980, 200));
        java.util.Arrays.fill(occupied,0,4,true);
        java.util.Arrays.fill(occupied,94,occupied.length,true);
        session = new S3kCompetitionSession(new S3kCompetitionSession.Config(mode,
                S3kCompetitionSession.Arena.AZURE_LAKE, true), this,
                SEQUENCE, ITEM_ANIMATION, GOAL_ANIMATION);
    }

    @Override public List<S3kCompetitionPlayer> players() { return players; }
    @Override public void rebindPlayers(List<S3kCompetitionPlayer> rebound) {
        players.clear(); players.addAll(rebound);
    }
    @Override public List<CompetitionItemObjectInstance> itemWheels() {
        return objects.stream().filter(CompetitionItemObjectInstance.class::isInstance)
                .map(CompetitionItemObjectInstance.class::cast).filter(CompetitionItemObjectInstance::isWheel).toList();
    }
    @Override public AbstractObjectInstance allocateAfter(AbstractObjectInstance parent,
            Supplier<? extends AbstractObjectInstance> factory) {
        return allocateFrom(Math.max(4,parent.getSlotIndex()+1),factory);
    }
    @Override public AbstractObjectInstance allocateFree(Supplier<? extends AbstractObjectInstance> factory) {
        return allocateFrom(4,factory);
    }
    private AbstractObjectInstance allocateFrom(int first,Supplier<? extends AbstractObjectInstance> factory) {
        for (int slot = first; slot < 94; slot++) {
            if (occupied[slot]) continue;
            occupied[slot] = true;
            try {
                AbstractObjectInstance object = factory.get(); object.setSlotIndex(slot); objects.add(object);
                return object;
            } catch(RuntimeException | Error failure) {occupied[slot]=false;throw failure;}
        }
        return null;
    }
    @Override public AbstractObjectInstance overwriteProjectile(Supplier<? extends AbstractObjectInstance> factory) {
        reservedProjectile = factory.get(); reservedProjectile.setSlotIndex(94); return reservedProjectile;
    }
    @Override public SpillAnimationState spillAnimation() {return spillAnimation;}
    @Override public void awardLife(int slot) {lifeAwards[slot]++;}
    @Override public void playExtraLifeMusic() {extraLifeMusicCalls++;}
    @Override public CompetitionArenaObjectInstance.NativeContext nativeObjectContext() { return this; }
    @Override public S3kCompetitionSession.ItemTerrain itemTerrain() { return this; }
    @Override public int xWrapMask() { return 0x3FF; }
    @Override public int projectileYWrapMask() { return 0x7FF; }
    @Override public boolean playerHighPriority(int slot) { return false; }
    @Override public int oscillation(int offset) { throw new AssertionError("Item/goal must not use a synthetic oscillator"); }
    @Override public void requestArena(S3kCompetitionSession.Arena arena) { arenaRequests.add(arena); }
    @Override public void requestResults(S3kCompetitionSession.Result result) { results.add(result); }
    @Override public void saveTimeAttackRecord(S3kCompetitionSession.PlayerResult result, S3kCompetitionSession.Arena arena) {
        savedTimeAttackRecords.add(result);
    }
    @Override public void playSfx(Sonic3kSfx sound) { sounds.add(sound); }
    @Override public int levelFrameCounter() { return session.levelFrameCounter(); }
    @Override public int zoneId() { return session.arena().zoneId(); }
    @Override public int trigger(int index) { return session.trigger(index); }
    @Override public void setTrigger(int index, int value) { session.setTrigger(index, value); }
    @Override public SpriteAnimationSet animations(String key) {
        var animation = animationSets.get(key);
        if (animation == null) throw new AssertionError("Missing ROM animation: " + key);
        return animation;
    }
    @Override public List<CompetitionArenaObjectInstance.RockDebrisPiece> rockDebris() {
        throw new AssertionError("Item/goal must not load rock debris");
    }
    @Override public int oscillationByte(int offset) { return oscillation(offset); }
    @Override public int floorDistance(int x, int y) { return floorY - y; }
    @Override public int randomWord() { throw new AssertionError("Native wheel replenishment has no RNG read"); }
    @Override public int bottomY() { return 512; }
    @Override public int wrapYMask() { return -1; }
    @Override public void playSfx(int sound) { throw new AssertionError("Use typed native sounds in item fixture"); }
    @Override public void hurt(S3kCompetitionPlayer player, int sourceX) {
        hurtCalls++; S3kCompetitionDamage.contact(session,this,player,sourceX);
    }
    @Override public int paletteColor(int line, int index) { throw new AssertionError("Goal/item has no palette writes"); }
    @Override public void setPaletteColor(int line, int index, int color) { throw new AssertionError("Goal/item has no palette writes"); }
    @Override public int floorDistance(int x, int y, int radius, int topBit) { return floorY - y - radius; }
    @Override public int leftWallDistance(int x, int y, int radius, int lrbBit) { return leftWallDistance; }
    @Override public S3kCompetitionSession.ItemGround followGround(int x, int y, int rx, int ry,
            int angle, int groundVelocity, int topBit, int lrbBit) {
        return new S3kCompetitionSession.ItemGround(x, floorY - ry, 0, false);
    }

    void step(int ticks) {
        for (int i = 0; i < ticks; i++) {
            session.beforeFrame(session.levelFrameCounter() + 1);
            session.afterFrame();
        }
    }
    void cross(int slot, boolean right) {
        players.get(slot).setCentre(right ? 1001 : 999, 200);
        session.observeGoal(1000, 200, 8);
    }
    void completeLap(int slot, int ticks) {
        // Return outside the post's Y range, then pass through the valid gate.
        players.get(slot).setCentre(999, 100);
        session.observeGoal(1000, 200, 8);
        step(ticks);
        cross(slot, true);
    }
    private static SpriteAnimationSet ringSparkleOracle() {
        // Literal ROM $1A992..$1A999: 0002 05 04 05 06 07 FC.
        var set=new SpriteAnimationSet();
        set.addScript(0,new SpriteAnimationScript(5,List.of(4,5,6,7),SpriteAnimationEndAction.HOLD,0,List.of(0xFC)));
        return set;
    }
}
