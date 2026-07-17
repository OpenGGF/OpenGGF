package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.IdentityHashMap;
import java.util.List;

/** Locked-on {@code Obj_FBZSpringPlunger} ($D0), {@code loc_89C86-loc_89CDE}. */
public final class FbzSpringPlungerInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    private int mappingFrame = 5;

    public FbzSpringPlungerInstance(ObjectSpawn spawn) { super(spawn, "FBZSpringPlunger"); }

    @Override public void update(int frameCounter, PlayableEntity player) {
        // loc_89C86 calls sub_86A3E (SolidObjectFull) and immediately tests the
        // standing bits established by that same call before returning from the
        // object's SST entry (sonic3k.asm:187094-187119). Manual checkpoints
        // return those fresh per-player bits directly; listener callbacks are
        // intentionally reserved for the compatibility auto-solid path.
        SolidCheckpointBatch checkpoint = checkpointAll();
        ObjectPlayerQuery query = tryServices() == null ? null : services().playerQuery();
        if (query == null) {
            coarseXCull(spawn.x(), 0x280);
            return;
        }
        List<PlayableEntity> natives = query.playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2);
        List<PlayableEntity> all = query.playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
        validateCheckpointParticipants(checkpoint, natives, all);
        boolean anyStanding = all.stream().anyMatch(candidate -> isStanding(checkpoint, candidate));
        mappingFrame = anyStanding ? 6 : 5;
        PlayableEntity p1 = natives.isEmpty() ? null : natives.getFirst();
        PlayableEntity p2 = natives.size() < 2 ? null : natives.get(1);
        boolean p1Standing = p1 != null && isStanding(checkpoint, p1);
        boolean p2Standing = p2 != null && isStanding(checkpoint, p2);
        if (p1Standing || p2Standing) mappingFrame = 0xC;
        if (p1 != null) launchIfStanding(this, p1, p1Standing);
        if (p2 != null) {
            // Retail instruction-order quirk: d0 initially holds status(a0),
            // but the P1 launch calls sub_8635E, which replaces d0 with
            // sfx_Spring=$B1 before tail-jumping Play_SFX. loc_89CC4 then
            // tests p2_standing_bit (bit 4) in that clobbered $B1, so a P1
            // launch also launches Player_2 even when only status bit 3 was
            // set. Preserve the quirk locally without synthesizing a P2 solid
            // contact or transferring an unrelated interaction owner.
            boolean p2Launch = p2Standing || p1Standing;
            launchIfStanding(this, p2, p2Launch);
        }
        for (PlayableEntity extra : all) {
            if (natives.stream().noneMatch(nativePlayer -> nativePlayer == extra)) {
                launchIfStanding(this, extra, isStanding(checkpoint, extra));
            }
        }
        coarseXCull(spawn.x(), 0x280);
    }

    private void validateCheckpointParticipants(
            SolidCheckpointBatch checkpoint,
            List<PlayableEntity> natives,
            List<PlayableEntity> queriedPlayers) {
        if (checkpoint.object() != this) {
            throw new IllegalStateException("FBZ spring-plunger checkpoint owner mismatch");
        }
        IdentityHashMap<PlayableEntity, Boolean> queried = new IdentityHashMap<>();
        for (PlayableEntity participant : queriedPlayers) queried.put(participant, Boolean.TRUE);
        for (PlayableEntity nativePlayer : natives) {
            if (!queried.containsKey(nativePlayer)) {
                throw new IllegalStateException(
                        "FBZ spring-plunger native participant is absent from the all-player query");
            }
        }
        if (queried.size() != checkpoint.perPlayer().size()) {
            throw new IllegalStateException(
                    "FBZ spring-plunger checkpoint/query participant mismatch");
        }
        for (PlayableEntity participant : queriedPlayers) {
            if (!checkpoint.perPlayer().containsKey(participant)) {
                throw new IllegalStateException(
                        "FBZ spring-plunger query participant is absent from its fresh checkpoint");
            }
        }
    }

    private static boolean isStanding(SolidCheckpointBatch checkpoint, PlayableEntity player) {
        PlayerSolidContactResult fresh = checkpoint.perPlayer().get(player);
        if (fresh == null) {
            throw new IllegalStateException(
                    "FBZ spring-plunger participant has no authoritative checkpoint result");
        }
        return fresh.standingNow();
    }

    private static void launchIfStanding(
            FbzSpringPlungerInstance plunger, PlayableEntity player, boolean standingNow) {
        if (!standingNow) return;
        ObjectManager objectManager = plunger.services().objectManager();
        if (objectManager != null) objectManager.releaseRidingObject(player, plunger);
        player.setYSpeed((short) -0xA00);
        player.setAir(true);
        player.setOnObject(false);
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setHurt(false);
            sprite.setJumping(false);
            sprite.setSpindash(false);
            sprite.setAnimationId(0x10);
        }
        plunger.services().playSfx(Sonic3kSfx.SPRING.id);
    }

    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x1B, 4, 6); }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public boolean usesInclusiveRightEdge() { return true; }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public int getPriorityBucket() { return 3; }
    int mappingFrame() { return mappingFrame; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_EGG_CAPSULE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
