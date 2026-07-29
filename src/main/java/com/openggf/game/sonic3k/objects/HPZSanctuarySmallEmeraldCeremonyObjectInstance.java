package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * ROM {@code ChildObjDat_90FEA/90FF0}: the seven Chaos Emeralds that appear
 * while the controller's {@code $21F} conversion countdown is running.
 */
public final class HPZSanctuarySmallEmeraldCeremonyObjectInstance
        extends AbstractObjectInstance implements RewindRecreatable {
    private static final int[] START_ANGLES = {0x00, 0x24, 0x48, 0x6C, 0x90, 0xB4, 0xD8};
    private static final int[] TARGET_ANGLES = {0x80, 0x86, 0x7A, 0x8C, 0x74, 0x92, 0x6E};
    private static final int[] PALETTES_SONIC = {2, 0, 2, 0, 0, 0, 3};
    private static final int[] PALETTES_KNUCKLES = {2, 1, 2, 1, 1, 1, 3};

    private int centreX = 0x1640;
    private int centreYFixed = 0x3AC << 8;
    private int riseTimer = 0x7F;
    private int radius;
    private final int[] angles = START_ANGLES.clone();
    private int arrivedMask;
    private boolean completionSoundPlayed;
    private int departureTimer = -1;
    private final int[] departureXFixed = new int[7];
    private final int[] departureYFixed = new int[7];
    private static final int[] DEPARTURE_X_VELOCITY =
            {0, -0xF8, 0xF8, -0x1E0, 0x1E0, -0x2AC, 0x2AC};
    private static final int[] DEPARTURE_Y_VELOCITY =
            {-0x400, -0x3E0, -0x3E0, -0x384, -0x384, -0x2F4, -0x2F4};

    private record RewindExtra(
            int centreX, int centreYFixed, int riseTimer, int radius,
            int[] angles, int arrivedMask, boolean completionSoundPlayed,
            int departureTimer, int[] departureXFixed, int[] departureYFixed)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {
        private RewindExtra {
            angles = angles.clone();
            departureXFixed = departureXFixed.clone();
            departureYFixed = departureYFixed.clone();
        }
    }

    public HPZSanctuarySmallEmeraldCeremonyObjectInstance() {
        this(new ObjectSpawn(0x1640, 0x3AC, 0xB5, 0, 0, false, 0));
    }

    private HPZSanctuarySmallEmeraldCeremonyObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HPZSanctuarySmallEmeraldCeremony");
    }

    @Override
    public HPZSanctuarySmallEmeraldCeremonyObjectInstance recreateForRewind(
            RewindRecreateContext ctx) {
        return new HPZSanctuarySmallEmeraldCeremonyObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (riseTimer == 0x7F && tryServices() != null) {
            services().playSfx(Sonic3kSfx.SIGNPOST.id);
        }
        if (riseTimer >= 0) {
            centreYFixed -= 0x80; // MoveSprite2 with y_vel=-$80.
            riseTimer--;
            if (riseTimer < 0) {
                applyRiseCompletePlayerMappings();
            }
        }
        if (departureTimer >= 0) {
            for (int i = 0; i < departureXFixed.length; i++) {
                departureXFixed[i] += DEPARTURE_X_VELOCITY[i];
                departureYFixed[i] += DEPARTURE_Y_VELOCITY[i];
            }
            if (++departureTimer >= 0x100) {
                setDestroyed(true);
            }
            return;
        }
        if (radius < 0x1800) {
            radius = Math.min(0x1800, radius + 0x10);
        }
        for (int i = 0; i < angles.length; i++) {
            if ((arrivedMask & (1 << i)) != 0) {
                continue;
            }
            angles[i] = (angles[i] + 4) & 0xFF;
            if (radius == 0x1800 && byteAngleDistance(angles[i], TARGET_ANGLES[i]) < 4) {
                arrivedMask |= 1 << i;
            }
        }
        if (arrivedMask == 0x7F && !completionSoundPlayed) {
            completionSoundPlayed = true;
            if (tryServices() != null) {
                services().playSfx(Sonic3kSfx.SUPER_EMERALD.id);
            }
            beginDeparture();
        }
    }

    private void applyRiseCompletePlayerMappings() {
        if (tryServices() == null) return;
        for (PlayableEntity entity : services().playerQuery().playersFor(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (entity instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
                String name = sprite.getClass().getSimpleName();
                sprite.setMappingFrame(name.contains("Tails") ? 0xB0
                        : name.contains("Knuckles") ? 0xD6 : 0xC4);
            }
        }
    }

    private void beginDeparture() {
        int pixelRadius = radius >> 8;
        for (int i = 0; i < angles.length; i++) {
            departureXFixed[i] = (centreX
                    + (TrigLookupTable.sinHex(angles[i]) * pixelRadius >> 8)) << 8;
            departureYFixed[i] = (getY()
                    + (TrigLookupTable.cosHex(angles[i]) * pixelRadius >> 8)) << 8;
        }
        departureTimer = 0;
    }

    private static int byteAngleDistance(int left, int right) {
        return Math.abs((byte) (left - right));
    }

    @Override public int getX() { return centreX; }
    @Override public int getY() { return centreYFixed >> 8; }
    @Override public int getOutOfRangeReferenceX() { return centreX; }
    @Override public int getPriorityBucket() { return 3; }
    @Override public boolean isHighPriority() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_SMALL_EMERALDS);
        if (renderer == null) {
            return;
        }
        boolean knuckles = services().playerQuery().playersFor(
                        com.openggf.level.objects.ObjectPlayerParticipationPolicy.MAIN_ONLY_NATIVE)
                .stream().anyMatch(p -> p.getClass().getSimpleName().contains("Knuckles"));
        int[] palettes = knuckles ? PALETTES_KNUCKLES : PALETTES_SONIC;
        int pixelRadius = radius >> 8;
        for (int i = 0; i < angles.length; i++) {
            int angle = angles[i] & 0xFF;
            int x = departureTimer >= 0 ? departureXFixed[i] >> 8
                    : centreX + (TrigLookupTable.sinHex(angle) * pixelRadius >> 8);
            int y = departureTimer >= 0 ? departureYFixed[i] >> 8
                    : getY() + (TrigLookupTable.cosHex(angle) * pixelRadius >> 8);
            renderer.drawFrameIndex(i, x, y, false, false, palettes[i]);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState() {
        return super.captureRewindState().withObjectSubclassExtra(new RewindExtra(
                centreX, centreYFixed, riseTimer, radius, angles, arrivedMask,
                completionSoundPlayed, departureTimer, departureXFixed,
                departureYFixed));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot) {
        super.restoreRewindState(snapshot);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            centreX = extra.centreX();
            centreYFixed = extra.centreYFixed();
            riseTimer = extra.riseTimer();
            radius = extra.radius();
            System.arraycopy(extra.angles(), 0, angles, 0, angles.length);
            arrivedMask = extra.arrivedMask();
            completionSoundPlayed = extra.completionSoundPlayed();
            departureTimer = extra.departureTimer();
            System.arraycopy(extra.departureXFixed(), 0, departureXFixed, 0,
                    departureXFixed.length);
            System.arraycopy(extra.departureYFixed(), 0, departureYFixed, 0,
                    departureYFixed.length);
        }
    }
}
