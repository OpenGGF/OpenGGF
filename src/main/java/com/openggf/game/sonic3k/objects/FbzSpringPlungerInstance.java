package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Locked-on {@code Obj_FBZSpringPlunger} ($D0), {@code loc_89C86-loc_89CDE}. */
public final class FbzSpringPlungerInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {
    private final FbzParticipantStateTable standing = new FbzParticipantStateTable(1);
    private int mappingFrame = 5;

    public FbzSpringPlungerInstance(ObjectSpawn spawn) { super(spawn, "FBZSpringPlunger"); }

    @Override public void update(int frameCounter, PlayableEntity player) {
        checkpointAll();
        ObjectPlayerQuery query = tryServices() == null ? null : services().playerQuery();
        if (query == null) {
            coarseXCull(spawn.x(), 0x280);
            return;
        }
        List<PlayableEntity> natives = query.playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2);
        List<PlayableEntity> all = query.playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
        boolean anyStanding = all.stream().anyMatch(candidate -> standing.flag(standing.slot(candidate), 0));
        mappingFrame = anyStanding ? 6 : 5;
        for (PlayableEntity nativePlayer : natives) {
            int slot = standing.slot(nativePlayer);
            if (standing.flag(slot, 0)) mappingFrame = 0xC;
        }
        for (PlayableEntity nativePlayer : natives) launchIfStanding(this, nativePlayer);
        for (PlayableEntity extra : all) {
            if (natives.stream().noneMatch(nativePlayer -> nativePlayer == extra)) launchIfStanding(this, extra);
        }
        coarseXCull(spawn.x(), 0x280);
    }

    private static void launchIfStanding(FbzSpringPlungerInstance plunger, PlayableEntity player) {
        int slot = plunger.standing.slot(player);
        if (!plunger.standing.flag(slot, 0)) return;
        plunger.standing.flag(slot, 0, false);
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

    @Override public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        standing.flag(standing.slot(player), 0, contact != null && contact.standing());
    }
    @Override public void onSolidContactCleared(PlayableEntity player, int frameCounter) {
        standing.flag(standing.slot(player), 0, false);
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
