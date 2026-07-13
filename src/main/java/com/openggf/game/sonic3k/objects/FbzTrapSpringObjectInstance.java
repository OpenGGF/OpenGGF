package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Locked-on {@code Obj_FBZTrapSpring} ($E3), $3CB34-$3CC56. */
public final class FbzTrapSpringObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {
    private final FbzParticipantStateTable participants = new FbzParticipantStateTable(1);
    private int animation;
    private int previousAnimation = -1;
    private int mappingFrame = 1;
    private int animationTimer = 3;

    public FbzTrapSpringObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZTrapSpring");
    }

    @Override
    public void update(int frameCounter, PlayableEntity mainPlayer) {
        if (mainPlayer != null) {
            int relativeY = (short) (mainPlayer.getCentreY() - spawn.y());
            if (relativeY >= 0x20) animation = 1;
            else if (relativeY < -0x10) animation = 0;
        }
        services().playerQuery().visitPlayers(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,
                this, FbzTrapSpringObjectInstance::launchLatchedParticipant);
        updateAnimation();
    }

    private static void launchLatchedParticipant(FbzTrapSpringObjectInstance spring, PlayableEntity player) {
        int slot = spring.participants.slot(player);
        if (!spring.participants.flag(slot, 0)) return;
        spring.participants.flag(slot, 0, false);
        launchPlayer(player, spring.spawn.subtype(), spring.services());
    }

    static void launchPlayer(PlayableEntity player, int subtype, ObjectServices services) {
        player.setYSpeed((short) (((subtype & 2) == 0) ? -0x1000 : -0xA00));
        player.setAir(true);
        player.setOnObject(false);
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setSpindash(false);
            sprite.setJumping(false);
            sprite.setAnimationId(0x10);
            if ((subtype & 1) != 0) {
                boolean facingLeft = sprite.getDirection() == Direction.LEFT;
                sprite.setGSpeed((short) (facingLeft ? -1 : 1));
                sprite.setFlipAngle(facingLeft ? -1 : 1);
                sprite.setAnimationId(0);
                sprite.setFlipsRemaining((subtype & 2) == 0 ? 1 : 0);
                sprite.setFlipSpeed(4);
            }
        }
        services.playSfx(Sonic3kSfx.SPRING.id);
    }

    private void updateAnimation() {
        if (animation != previousAnimation) {
            previousAnimation = animation;
            mappingFrame = 1;
            animationTimer = 3;
            return;
        }
        if (mappingFrame != 1 || --animationTimer >= 0) return;
        mappingFrame = animation == 0 ? 2 : 0;
    }

    public int launchVelocity() { return (spawn.subtype() & 2) == 0 ? -0x1000 : -0xA00; }
    public boolean flipLaunch() { return (spawn.subtype() & 1) != 0; }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public int getPriorityBucket() { return 1; }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x1B, 8, 9); }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public SolidRoutineProfile getSolidRoutineProfile() { return SolidRoutineProfile.topSolid(false); }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (contact.standing()) participants.flag(participants.slot(player), 0, true);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_TRAP_SPRING);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
        }
    }
}
