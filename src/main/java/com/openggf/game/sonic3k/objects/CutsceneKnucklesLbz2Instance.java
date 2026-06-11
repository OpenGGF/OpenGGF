package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.ArrayList;
import java.util.List;

public final class CutsceneKnucklesLbz2Instance extends AbstractObjectInstance {
    private static final int OBJ_CUTSCENE_KNUCKLES = 0x82;
    private static final int SUBTYPE_LBZ2 = 0x18;
    private static final int LIGHT_GRAVITY = 0x18;

    private final List<SwingChild> swingChildren = new ArrayList<>(4);
    private int x;
    private int y;
    private int xVel;
    private int yVel;
    private boolean initialized;
    private boolean triggered;
    private boolean screenShakeObserved;
    private boolean flingRequested;
    private boolean flung;
    private boolean splashed;
    private int previousY;
    private int mappingFrame = 0x20;

    public CutsceneKnucklesLbz2Instance(ObjectSpawn spawn) {
        super(spawn, "CutsceneKnucklesLBZ2");
        this.x = spawn.x();
        this.y = spawn.y();
        this.previousY = y;
        updateDynamicSpawn(x, y);
    }

    @Override
    public void setServices(ObjectServices services) {
        super.setServices(services);
        initializeAfterServices();
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        initializeAfterServices();
        if (isDestroyed()) {
            return;
        }
        if (isLaunchActive()) {
            screenShakeObserved = true;
        }
        if (!flung && flingRequested) {
            beginFling();
        }
        if (flung) {
            updateFlung();
        } else if (triggered) {
            mappingFrame = screenShakeObserved ? 1 : 0x1C;
            y = (y + launchYDelta()) & 0xFFFF;
            updateDynamicSpawn(x, y);
        }
        if (!isInRangeAt(x)) {
            setDestroyedByOffscreen();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CUTSCENE_KNUCKLES);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false);
        }
    }

    public void triggerFromShip() {
        triggered = true;
    }

    public void markFlungFromSwingForTest() {
        flingRequested = true;
    }

    void requestFlingFromSwing() {
        flingRequested = true;
    }

    void copyLeaderX(int leaderX) {
        x = leaderX & 0xFFFF;
        updateDynamicSpawn(x, y);
    }

    public int getCentreX() {
        return x;
    }

    public int getCentreY() {
        return y;
    }

    public boolean isTriggeredForTest() {
        return triggered;
    }

    public boolean isScreenShakeObservedForTest() {
        return screenShakeObserved;
    }

    public boolean isFlingRequestedForTest() {
        return flingRequested;
    }

    public boolean hasSplashedForTest() {
        return splashed;
    }

    public List<SwingChild> swingChildrenForTest() {
        return List.copyOf(swingChildren);
    }

    private void initializeAfterServices() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (spawn.subtype() != SUBTYPE_LBZ2 || playerCharacter() == PlayerCharacter.KNUCKLES) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        services().playMusic(Sonic3kMusic.KNUCKLES.id);
        AizIntroArtLoader.applyKnucklesPalette(services());
        for (int subtype = 0; subtype <= 6; subtype += 2) {
            int childSubtype = subtype;
            SwingChild child = spawnChild(() -> new SwingChild(this, childSubtype));
            swingChildren.add(child);
        }
    }

    private void beginFling() {
        flung = true;
        mappingFrame = 9;
        xVel = 0x0200;
        yVel = -0x0100;
        services().playMusic(Sonic3kMusic.LBZ2.id);
    }

    private void updateFlung() {
        previousY = y;
        x = (x + (xVel >> 8)) & 0xFFFF;
        y = (y + (yVel >> 8) + launchYDelta()) & 0xFFFF;
        yVel += LIGHT_GRAVITY;
        checkSplash();
        updateDynamicSpawn(x, y);
    }

    private void checkSplash() {
        if (splashed) {
            return;
        }
        int waterLevel = waterLevel();
        if (waterLevel <= 0) {
            return;
        }
        int prev = previousY & 0xFFFF;
        int current = y & 0xFFFF;
        if (prev < waterLevel && current >= waterLevel) {
            splashed = true;
            services().playSfx(Sonic3kSfx.SPLASH.id);
        }
    }

    private int waterLevel() {
        WaterSystem water = services().waterSystem();
        if (water == null) {
            return 0;
        }
        return water.getWaterLevelY(Sonic3kZoneIds.ZONE_LBZ, 1);
    }

    private boolean isLaunchActive() {
        return services().zoneRuntimeRegistry()
                .currentAs(LbzZoneRuntimeState.class)
                .map(LbzZoneRuntimeState::isLaunchActive)
                .orElse(false);
    }

    private int launchYDelta() {
        return services().zoneRuntimeRegistry()
                .currentAs(LbzZoneRuntimeState.class)
                .map(LbzZoneRuntimeState::getLaunchYDelta)
                .orElse(0);
    }

    private PlayerCharacter playerCharacter() {
        return services().zoneRuntimeRegistry()
                .currentAs(LbzZoneRuntimeState.class)
                .map(LbzZoneRuntimeState::playerCharacter)
                .orElse(PlayerCharacter.SONIC_ALONE);
    }

    public static final class SwingChild extends AbstractObjectInstance {
        private static final int[][] INITIAL_PARAMS = {
                {0x100, 0x10},
                {0x0C0, 0x0C},
                {0x080, 0x08},
                {0x040, 0x04}
        };
        private static final int[][] FAST_PARAMS = {
                {0x200, 0x20},
                {0x180, 0x18},
                {0x100, 0x10},
                {0x080, 0x08}
        };

        private final CutsceneKnucklesLbz2Instance parent;
        private final int subtype;
        private final int index;
        private final int anchorX;
        private int x;
        private int y;
        private int xVel;
        private int yVel;
        private int maxSpeed;
        private int accel;
        private int reversals;
        private boolean movingRight = true;
        private boolean freeFalling;

        private SwingChild(CutsceneKnucklesLbz2Instance parent, int subtype) {
            super(new ObjectSpawn(parent.getCentreX() + 2,
                    parent.getCentreY() + 0x24 + (subtype * 8),
                    OBJ_CUTSCENE_KNUCKLES, subtype, 0, false, parent.getCentreY()),
                    "CutsceneKnucklesLBZ2Swing");
            this.parent = parent;
            this.subtype = subtype;
            this.index = Math.min(subtype / 2, INITIAL_PARAMS.length - 1);
            this.anchorX = parent.getCentreX() + 2;
            this.x = anchorX;
            this.y = parent.getCentreY() + 0x24 + (subtype * 8);
            this.maxSpeed = INITIAL_PARAMS[index][0];
            this.accel = INITIAL_PARAMS[index][1];
            updateDynamicSpawn(x, y);
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (freeFalling) {
                updateFreeFall();
            } else if (parent.isLaunchActive()) {
                updateSwinging();
            } else {
                y = (y + parent.launchYDelta()) & 0xFFFF;
            }
            if (subtype == 0) {
                parent.copyLeaderX(x);
            }
            updateDynamicSpawn(x, y);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LBZ_KNUX_PILLAR);
            if (renderer != null) {
                renderer.drawFrameIndex(0, x, y, false, false);
            }
        }

        public int getCentreX() {
            return x;
        }

        public int getCentreY() {
            return y;
        }

        public boolean isFreeFallingForTest() {
            return freeFalling;
        }

        public void forceNextCrossingForTest() {
            x = anchorX - 1;
            xVel = maxSpeed;
            movingRight = true;
        }

        private void updateSwinging() {
            if (movingRight) {
                xVel = Math.min(maxSpeed, xVel + accel);
            } else {
                xVel = Math.max(-maxSpeed, xVel - accel);
            }
            x = (x + (xVel >> 8)) & 0xFFFF;
            y = (y + parent.launchYDelta()) & 0xFFFF;
            boolean crossed = movingRight ? unsigned(x) >= unsigned(anchorX) : unsigned(x) <= unsigned(anchorX);
            if (crossed) {
                reverse();
            }
        }

        private void reverse() {
            reversals++;
            movingRight = !movingRight;
            if (reversals == 3) {
                maxSpeed = FAST_PARAMS[index][0];
                accel = FAST_PARAMS[index][1];
            }
            if (reversals >= 6) {
                parent.requestFlingFromSwing();
                freeFalling = true;
                yVel = -0x0100;
            }
        }

        private void updateFreeFall() {
            x = (x + (xVel >> 8)) & 0xFFFF;
            y = (y + (yVel >> 8) + parent.launchYDelta()) & 0xFFFF;
            yVel += LIGHT_GRAVITY;
        }

        private static int unsigned(int value) {
            return value & 0xFFFF;
        }
    }
}
