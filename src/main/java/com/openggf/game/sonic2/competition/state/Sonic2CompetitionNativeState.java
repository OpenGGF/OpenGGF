package com.openggf.game.sonic2.competition.state;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.competition.Sonic2NativeCompetitionHost;
import com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVramService;
import com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionCheckpoints;
import com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle;
import com.openggf.game.sonic2.bumpers.Sonic2NativeCNZBumpers;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.rings.RingManagerInternalAccess;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import java.util.Objects;

/** Complete native transaction over the live owners supplied by the competition composition root. */
public final class Sonic2CompetitionNativeState implements Sonic2NativeCompetitionHost.NativeState {
    private final Sonic2TeleportPorts.BackgroundBanks backgrounds;
    private final Sonic2TeleportPorts.PlaneScroll planes;
    private final Sonic2SecondaryInvincibility secondaryInvincibility = new Sonic2SecondaryInvincibility();

    public Sonic2CompetitionNativeState(Sonic2TeleportPorts.BackgroundBanks backgrounds,
                                       Sonic2TeleportPorts.PlaneScroll planes) {
        this.backgrounds = Objects.requireNonNull(backgrounds, "live BG/copy/flag banks");
        this.planes = Objects.requireNonNull(planes, "live plane scroll owner");
    }
    public Sonic2SecondaryInvincibility initializeActorStatus(GameplayModeContext context, Sonic2CompetitionSession session) {
        secondaryInvincibility.initialize(context, session);
        return secondaryInvincibility;
    }

    public void retireActorStatus() { secondaryInvincibility.retire(); }

    @Override public boolean operational() {
        // Raw status_secondary.invincible must travel independently of Super_Sonic_flag before activation.
        return false;
    }

    @Override public boolean swapActorWorldState(GameplayModeContext context, Sonic2CompetitionSession session) {
        var module = context.getWorldSession().getGameModule();
        var lifecycle = Objects.requireNonNull(module.getGameService(Sonic2CompetitionLifecycle.class));
        // teleport_monitor (s2.asm:26003-26011): unsigned primary routines, even after ACT_RESULTS.
        if (lifecycle.nativeRoutineByte(0) >= 6 || lifecycle.nativeRoutineByte(1) >= 6) return false;
        var objects = context.getObjectManager();
        if (objects.getLastProcessSlotExclusive() < 0x90)
            throw new IllegalStateException("Native teleport requires the level-only object range");
        var objectFields = new java.util.ArrayList<Runnable>();
        for (var object : objects.getActiveObjects()) {
            if (!(object instanceof com.openggf.level.objects.AbstractObjectInstance nativeObject)
                    || nativeObject.getSlotIndex() < 16 || nativeObject.getSlotIndex() >= 128) continue;
            int id = nativeObject.getLiveObjectId();
            if (id == 3) {
                objectFields.add(() -> ObjectManagerInternalAccess.swapNativePlaneSwitcherPlayers(
                        objects, object.getSpawn(), session.player(0), session.player(1)));
            } else if (id == 0x84 || id == 0xD6 || id == 0x85) {
                if (!(object instanceof Sonic2TeleportPorts.ObjectPlayerFields fields))
                    throw new IllegalStateException("Native per-player teleport fields unbound for object " + id);
                objectFields.add(() -> ObjectManagerInternalAccess.mutateNativeObject(objects, object, fields::swapPlayers));
            }
        }
        var a = session.camera(0).capture(); var b = session.camera(1).capture();
        var p1 = session.player(0); var p2 = session.player(1);
        boolean priority1 = p1.isHighPriority(), priority2 = p2.isHighPriority();
        PlayableSpriteInternalAccess.swapNativeTeleportActors(p1, p2);
        secondaryInvincibility.swapPlayers();
        p1.setHighPriority(priority2); p2.setHighPriority(priority1);
        // teleport_swap_table (s2.asm:26169-26190). Progress, checkpoints and actor identities stay put.
        restoreCamera(session.camera(0), b, a, p1, true);
        restoreCamera(session.camera(1), a, b, p2, false);
        backgrounds.swapPlayers();
        planes.swapPlayers();
        lifecycle.swapCameraDifferences();
        ObjectManagerInternalAccess.swapNativeCompetitionStreamingSlots(objects);
        RingManagerInternalAccess.swapNativeCompetitionStreamingSlots(context.getLevelManager().getRingManager());
        var bumpers = com.openggf.game.session.WorldSessionPolicyAccess.getService(
                context.getWorldSession(), Sonic2NativeCNZBumpers.class);
        if (bumpers != null) bumpers.swapNativeCompetitionSlots();
        // swap_loop_objects (s2.asm:26068-26096): fixBugs=0, preserve its sequential writes.
        objectFields.forEach(Runnable::run);
        // swap_players (s2.asm:26033-26041,26098-26147): art stays with identity, priority travels.
        p1.publishRunAsPreviousAnimation(); p2.publishRunAsPreviousAnimation();
        Objects.requireNonNull(module.getGameService(Sonic2InterlaceVramService.class)).invalidatePlayerDplcs();
        if (!p1.getRolling()) p1.applyCustomRadii(9, 0x13);
        if (!p2.getRolling()) p2.applyCustomRadii(9, 0x0F);
        ObjectManagerInternalAccess.repairNativeTeleportStanding(objects, p1, p2);
        rebindEffects(context, p1, 0x86, 0x88);
        rebindEffects(context, p2, 0x87, 0x8C);
        return true;
    }

    /** Restore already captured fixed occupants without executing their inactive-status routines. */
    public void restoreFrozenEffects(GameplayModeContext context, Sonic2CompetitionSession session) {
        for (int slot = 0; slot < 2; slot++) {
            var objects = context.getObjectManager();
            var shield = com.openggf.level.objects.NativeSstSlotAccess.currentOwner(objects, 0x86 + slot);
            var stars = com.openggf.level.objects.NativeSstSlotAccess.currentOwner(objects, slot == 0 ? 0x88 : 0x8C);
            var shieldHandle = shield instanceof com.openggf.game.PowerUpObject handle ? handle
                    : ObjectManagerInternalAccess.restoreNativeTeleportFixedEffect(objects, 0x86 + slot, session.player(slot), false);
            var starsHandle = stars instanceof com.openggf.game.PowerUpObject handle ? handle
                    : ObjectManagerInternalAccess.restoreNativeTeleportFixedEffect(objects, slot == 0 ? 0x88 : 0x8C, session.player(slot), true);
            PlayableSpriteInternalAccess.applyNativeTeleportEffects(session.player(slot), shieldHandle, starsHandle);
        }
    }

    private static void rebindEffects(GameplayModeContext context,
            com.openggf.sprites.playable.AbstractPlayableSprite player, int shieldSlot, int starsSlot) {
        // swap_players (s2.asm:26098-26104,26124-26128): fixed IDs/parents are written even without status.
        var objects = context.getObjectManager();
        var existingShield = com.openggf.level.objects.NativeSstSlotAccess.currentOwner(objects, shieldSlot);
        var existingStars = com.openggf.level.objects.NativeSstSlotAccess.currentOwner(objects, starsSlot);
        // ID/parent writes do not clear an already-live effect's animation words or identity.
        var shield = existingShield instanceof com.openggf.level.objects.ShieldObjectInstance live
                && !live.isDestroyed() && live.isShieldFor(player, com.openggf.game.ShieldType.BASIC) ? live
                : ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(objects, shieldSlot,
                        () -> new com.openggf.level.objects.ShieldObjectInstance(player));
        var stars = existingStars instanceof com.openggf.level.objects.InvincibilityStarsObjectInstance live
                && !live.isDestroyed() && live.boundPlayer() == player ? live
                : ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(objects, starsSlot,
                        () -> new com.openggf.level.objects.InvincibilityStarsObjectInstance(player));
        PlayableSpriteInternalAccess.applyNativeTeleportEffects(player, shield, stars);
    }

    private static void restoreCamera(Camera camera, CameraSnapshot incoming, CameraSnapshot own,
            com.openggf.sprites.playable.AbstractPlayableSprite player, boolean primary) {
        // Only Camera-owned table entries. Target/easing/presentation state does not travel.
        camera.restore(new CameraSnapshot(incoming.x(), (short) (incoming.y() - 0x180),
                incoming.minX(), incoming.minY(), incoming.maxX(), incoming.maxY(),
                incoming.renderCopyX(), incoming.renderCopyY(), own.shakeOffsetX(), own.shakeOffsetY(),
                own.minXTarget(), own.minYTarget(), own.maxXTarget(), own.maxYTarget(), own.maxXBeforeBoundaryEasing(),
                primary || own.maxYChanging(), incoming.horizScrollDelayFrames(), own.frozen(),
                own.deferHorizontalBoundaryClampOnce(), own.levelStarted(), own.verticalWrapEnabled(),
                own.verticalWrapRange(), own.verticalWrapMask(), own.lastFrameWrapped(), own.wrapDeltaY(),
                incoming.yPosBias(), own.fastScrollCap(), own.customMaxXBoundaryEasingClaimed(), own.viewportMaxX()), player);
    }

    @Override public void freezeObjects(GameplayModeContext context, int ticks) {
        var session = com.openggf.game.session.WorldSessionPolicyAccess.getService(
                context.getWorldSession(), Sonic2CompetitionSession.class);
        Objects.requireNonNull(session, "native session").teleportClock().start(ticks);
        context.getObjectManager().getObjectServices().audioManager().playSfx(
                com.openggf.game.sonic2.audio.Sonic2Sfx.TELEPORT.id);
    }

    public Sonic2CompetitionCheckpoints.PrimaryEnvironment primaryEnvironment(
            GameplayModeContext context, Sonic2CompetitionSession session) {
        var bg = backgrounds.primaryPosition(); var camera = session.camera(0);
        // Level_ClrRam and the no-water competition routes retain cleared water globals.
        // Level (s2.asm:4858-4876) writes water words only under Water_flag (HPZ/CPZ/ARZ).
        // Native competition events never enter a resize routine; their owner is routine zero.
        return new Sonic2CompetitionCheckpoints.PrimaryEnvironment(0, camera.getMaxY(), camera.getX(), camera.getY(),
                bg.x(), bg.y(), bg.x2(), bg.y2(), bg.x3(), bg.y3(), 0, 0, 0);
    }
}
