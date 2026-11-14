package com.openggf.game.soniccd.objects.common;

import com.openggf.game.soniccd.audio.CdSoundRequests;

import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;

/**
 * Shared Sonic CD object semantics on the shared object owner.
 *
 * <ul>
 *   <li>{@code oSprFlags} bit 7 is the previous frame's {@code DrawObject} result: DrawObject clears it
 *       and queues the object only when its {@code oWidth}/{@code oYRadius} box overlaps the 320 x $E0
 *       screen at the camera of that moment (Object Functions.asm:136-191); DrawObjects then sets the bit
 *       for queued sprites (Object Functions.asm:251-346). DrawObject runs inside RunObjects, before the
 *       frame's LevelScroll (Level_MainLoop, Level/Main.asm:402-421), so the test uses the camera from
 *       before that frame's scroll; {@link #renderFlagOnScreen()} keeps it.</li>
 *   <li>Objects delete themselves: {@link #despawnsByCheckObjDespawn()} objects call
 *       CheckObjDespawn after drawing (Object Spawner.asm:385-399): kept while on screen,
 *       otherwise despawned once the chunk distance exceeds $80+(320+$40)+$80. All others are
 *       never unloaded by range.</li>
 *   <li>{@code oPriority} n is render bucket n; bit 15 of the tile word is high priority.</li>
 * </ul>
 */
public abstract class CdObject extends AbstractObjectInstance {
    /** CheckObjDespawn2: cmpi.w #$80+(320+$40)+$80,d0; bls (Object Spawner.asm:398-399). */
    private static final int DESPAWN_RANGE = 0x80 + (320 + 0x40) + 0x80;

    protected int x;
    protected int y;
    protected int priority;
    protected int tileWord;
    protected int yRadius;
    protected int width;

    protected CdObject(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.x = spawn.x();
        this.y = spawn.y();
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return RenderPriority.clamp(priority); }
    @Override public boolean isHighPriority() { return CdObjectArtProvider.tilePriority(tileWord); }
    @Override public int getOnScreenHalfWidth() { return width; }
    @Override public int getOnScreenHalfHeight() { return yRadius; }
    @Override protected boolean usesCustomRenderHeight() { return true; }

    /** True once a DrawObjects pass has run since this object was created. */
    private boolean renderPassSeen;
    /** {@code oSprFlags} bit 7 as the last frame's {@code DrawObject} left it. */
    private boolean drawnOnScreen;
    /** True once the object has had its turn in the object loop since the last end-of-frame pass. */
    private boolean ranSincePass;
    /** The camera of the last object pass (the previous end-of-frame position), once known. */
    private boolean drawCameraKnown;
    private int drawCameraX;
    private int drawCameraY;

    /**
     * {@code tst.b oSprFlags(a0)}: the retained on-screen bit from the last frame's DrawObject. A new object's
     * slot was cleared (FindObjSlot/SpawnObjects), so the bit is clear until DrawObjects has run once.
     */
    protected boolean renderFlagOnScreen() {
        return renderPassSeen && drawnOnScreen;
    }

    /**
     * Sonic CD never edge-triggers a touch. Every stage's player object collision ({@code Player_ObjCollide}, Level/Palmtree
     * Panic/Player Object Collision.asm:7-126, the same loop in the Tidal Tempest and Wacky Workbench copies; r3/player_object_collide.asm
     * for the R3 stages) walks the object table on every frame and runs the type's routine for every overlapping object: the
     * {@code $C0} types add to {@code oColStatus} ({@code Player_TouchSpecial .FlagCollision}, :302-386), a type below $40
     * advances the object's routine ({@code addq.b #2,oRoutine}, :81-96), and the object tests the result on its own turn.
     * Nothing latches a first overlap. The engine's shared controller fires {@code SPECIAL} (the $40 and $C0 types) once
     * per overlap unless the provider asks for every frame, so every Sonic CD touch provider asks: an object the player
     * still overlaps a frame later is touched again (the breakable wall when the roll begins inside the box, R12A level
     * frame 1615; the bumper platform's second bump, R31B row 6297).
     */
    public boolean requiresContinuousTouchCallbacks() {
        return true;
    }

    /**
     * {@code PlayerObjectCollide} tests only objects whose {@code obj.sprite_flags} bit 7 is set
     * ({@code tst.b obj.sprite_flags(a1); bpl}, r6/player_object_collide.asm:31-33; the same loop in every
     * stage's copy): the bit the object's last DrawObject left, {@link #renderFlagOnScreen()}.
     */
    @Override
    public boolean isOnScreenForTouch() {
        return renderFlagOnScreen();
    }

    /**
     * The end-of-frame pass: the frame's DrawObject tested the object's position at the camera before this frame's
     * scroll, which is the camera this pass saw last frame. An object's first pass has no earlier camera and uses the
     * current one. Objects move only in their own routine, so the position is the one DrawObject saw.
     */
    @Override
    public void snapshotPreUpdatePosition() {
        super.snapshotPreUpdatePosition();
        ranSincePass = true;
    }

    @Override
    public void snapshotTouchResponseState() {
        super.snapshotTouchResponseState();
        ranSincePass = true;
    }

    @Override
    public void refreshPostCameraRenderState() {
        // DrawObject runs inside the object's own routine, so an object that has not run since the last pass has not
        // drawn: a child that FindObjSlot put in a slot the object loop had already passed (the collapsing platform's
        // pieces, in slots below the platform's) keeps oSprFlags clear for one more frame, and its SolidObject skips
        // the player until then (Object Solid.asm .CheckFloor; SolidObject's tst.b oSprFlags). snapshotPreUpdatePosition
        // runs at the object's turn: at the frame start for an existing object, at once for a child the loop will reach.
        if (!ranSincePass) return;
        ranSincePass = false;
        renderPassSeen = true;
        var camera = services().camera();
        if (camera == null) {
            if (calledDrawObjectThisFrame()) drawnOnScreen = isWithinSolidContactBounds();
            return;
        }
        int cameraX = camera.getX() & 0xFFFF, cameraY = camera.getY() & 0xFFFF;
        if (!drawCameraKnown) {
            drawCameraX = cameraX;
            drawCameraY = cameraY;
            drawCameraKnown = true;
        }
        // A routine that did not call DrawObject this frame leaves oSprFlags bit 7 as the last DrawObjects set it.
        if (calledDrawObjectThisFrame()) drawnOnScreen = drawObjectOnScreen(drawCameraX, drawCameraY);
        drawCameraX = cameraX;
        drawCameraY = cameraY;
    }

    /**
     * Whether this frame's routine called {@code DrawObject}. DrawObject clears {@code oSprFlags} bit 7
     * ({@code bclr #7}, Object Functions.asm:138) and only {@code DrawObjects} sets it again for a queued sprite at the
     * frame's end (:346), so a frame without the call keeps the bit the last drawn frame left. True by default; an
     * object that draws on some frames only (the Stardust Speedway searchlight's dither) says which.
     */
    protected boolean calledDrawObjectThisFrame() {
        return true;
    }

    /**
     * Whether the routine calls {@code DrawObject} before its {@code CheckObjDespawn}: the {@code tst.b oSprFlags} there
     * (Object Spawner.asm:388-390) then reads the bit DrawObject has just cleared (Object Functions.asm:138), so on a
     * frame the object drew it despawns by range alone, on screen or not. False by default (the check reads the last
     * frame's bit, as {@code ObjRing_Main} tests it before drawing, Ring/Main.asm:214-232).
     */
    protected boolean drawsBeforeDespawnCheck() {
        return false;
    }

    /** {@code DrawObject}'s on-screen test (Object Functions.asm:136-191) at camera ({@code cameraX}, {@code cameraY}). */
    private boolean drawObjectOnScreen(int cameraX, int cameraY) {
        int dx = (short) (x - cameraX);
        if ((short) (dx + width) < 0 || (short) (dx - width) >= 320) return false;
        int objectY = y & 0xFFFF;
        if (cameraY < 0x100) {
            if (objectY >= 0x800) objectY -= 0x800;                    // wrap to the top of the screen
        } else if (cameraY >= 0x700 && objectY < 0x100) {
            objectY += 0x800;                                           // wrap to the bottom of the screen
        }
        int dy = (short) (objectY - cameraY);
        return (short) (dy + yRadius) >= 0 && (short) (dy - yRadius) < 0xE0;
    }

    /** True for routines that end in CheckObjDespawn; the reference X is {@link #despawnX()}. */
    protected boolean despawnsByCheckObjDespawn() {
        return false;
    }

    protected int despawnX() {
        return x;
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public int getOutOfRangeReferenceX() { return despawnX(); }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return despawnsByCheckObjDespawn() && outsideDespawnRange(despawnX(), cameraX,
                !(drawsBeforeDespawnCheck() && calledDrawObjectThisFrame()) && renderFlagOnScreen());
    }

    /** CheckObjDespawn2 (Object Spawner.asm:388-399): on screen keeps; else chunk distance > range. */
    static boolean outsideDespawnRange(int objX, int cameraX, boolean onScreen) {
        if (onScreen) return false;
        int chunk = objX & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > DESPAWN_RANGE;
    }

    /** The stage's object art owner, or {@code null} before art is loaded. */
    protected CdObjectArtProvider artProvider() {
        var renderManager = services().renderManager();
        return renderManager != null && renderManager.getArtProvider() instanceof CdObjectArtProvider provider
                && provider.image() != null ? provider : null;
    }

    /** An animator over one of the stage's script tables, or {@code null} before art is loaded. */
    protected CdAnimator animator(java.util.function.ToIntFunction<com.openggf.game.soniccd.objects.art.CdObjectCatalogue.Entry> table,
                                  int anim) {
        CdObjectArtProvider provider = artProvider();
        return provider == null ? null : new CdAnimator(provider.image(), table.applyAsInt(provider.entry()), anim);
    }

    protected PatternSpriteRenderer art(String key) {
        var renderManager = services().renderManager();
        PatternSpriteRenderer renderer = renderManager == null ? null : renderManager.getRenderer(key);
        return renderer == null || !renderer.isReady() ? null : renderer;
    }

    /** Draws frame {@code frame} at the object's position with its tile word's palette line. */
    protected void drawFrame(String key, int frame, boolean hFlip, boolean vFlip) {
        PatternSpriteRenderer renderer = art(key);
        if (renderer != null) {
            renderer.drawFrameIndex(frame, x, y, hFlip, vFlip, CdObjectArtProvider.tilePalette(tileWord));
        }
    }

    /**
     * The player's {@code layer} where this stage's objects test it against {@code oLayer}
     * ({@link com.openggf.game.soniccd.player.CdZonePlayerProfile#playerLayerForObjects}); -1 where none does.
     */
    protected int playerLayerForObjects() {
        var level = cdLevel();
        var module = services().gameModule();
        if (level == null || module == null
                || !(module.getPhysicsProvider() instanceof com.openggf.game.soniccd.player.SonicCdPhysicsProvider p)) {
            return -1;
        }
        return p.stage().profile(level).playerLayerForObjects();
    }

    /**
     * Whether this object's {@code oLayer} ({@link #subtype2()}) is the player's layer where the stage tests layers
     * (the {@code .Layer1} pairs of Monitor and Time Post/Main.asm:145-153, 245-253): true on a stage without layers.
     */
    protected boolean onPlayerLayer() {
        int layer = playerLayerForObjects();
        return layer < 0 || (layer == 0) == (subtype2() == 0);
    }

    /** {@link #onPlayerLayer()} for the player's touch routine. */
    public boolean onPlayerLayerForTouch() {
        return onPlayerLayer();
    }

    /** Whether the stage tests layers and this object is on layer 1 ({@code tst.b oLayer(a0); bne .NotMMZ}). */
    protected boolean frontLayerOfLayeredStage() {
        return playerLayerForObjects() >= 0 && subtype2() == 0;
    }

    /** {@code oSubtype2} of a layout entry (SpawnObject, Object Spawner.asm:312-314). */
    protected int subtype2() {
        var level = services().levelManager() != null ? services().levelManager().getCurrentLevel() : null;
        return level instanceof com.openggf.game.soniccd.level.CdLevel cd ? cd.subtype2(getSpawn()) : 0;
    }

    private com.openggf.level.objects.ReevaluatingSolidObject.Entry solidEntry =
            com.openggf.level.objects.ReevaluatingSolidObject.Entry.SOLID;

    /**
     * Calls {@code SolidObject}, {@code TopSolidObject} or {@code BtmSolidObject} for the player
     * from the object's own routine (Object Solid.asm:159-479); the object must implement
     * {@link com.openggf.level.objects.ReevaluatingSolidObject}. Returns d0: true for a top or
     * bottom contact, false for none or a side contact.
     */
    protected boolean solidObject(com.openggf.level.objects.ReevaluatingSolidObject.Entry entry) {
        solidEntry = entry;
        try {
            var batch = services().solidExecution().resolveSolidNowAll();
            for (var result : batch.perPlayer().values()) {
                if (result != null && (result.kind() == com.openggf.game.solid.ContactKind.TOP
                        || result.kind() == com.openggf.game.solid.ContactKind.BOTTOM)) {
                    return true;
                }
            }
            return false;
        } finally {
            solidEntry = com.openggf.level.objects.ReevaluatingSolidObject.Entry.SOLID;
        }
    }

    /** The entry of the solid call in progress (see {@link #solidObject}). */
    public com.openggf.level.objects.ReevaluatingSolidObject.Entry solidEntry() { return solidEntry; }

    /** {@code oWidth}. */
    public int solidWidth() { return width; }

    /** {@code oYRadius}. */
    public int solidYRadius() { return yRadius; }

    /** {@code tst.b oSprFlags}. */
    public boolean solidRenderedOnScreen() { return renderFlagOnScreen(); }

    /** {@code btst #3,oFlags(a0)}: the player stands on this object. */
    protected boolean stoodOn(com.openggf.game.PlayableEntity player) {
        var objects = services().objectManager();
        return objects != null && player != null && objects.hasObjectStandingBit(player, this);
    }

    /** {@code btst #5,oFlags(a0)}: the player pushes this object. */
    protected boolean pushedBy(com.openggf.game.PlayableEntity player) {
        var objects = services().objectManager();
        return objects != null && player != null
                && com.openggf.level.objects.ObjectManagerInternalAccess.objectPushedBy(objects, player, this);
    }

    /** {@code bclr #3,oFlags(a0)}. */
    protected void clearStoodOn() {
        var objects = services().objectManager();
        if (objects != null) objects.solidContacts().releaseObjectStandingLatchForAllPlayers(this);
    }

    /** {@code bclr #5,oFlags(a0)} for the player. */
    protected void clearPushedBy(com.openggf.game.PlayableEntity player) {
        var objects = services().objectManager();
        if (objects != null && player != null) objects.solidContacts().releaseObjectPushLatch(player, this);
    }

    /**
     * {@code Player_TouchEnemy .KillEnemy} (Palmtree Panic/Player Object Collision.asm:155-187): the
     * kill count is the shared badnik chain (EnemyPoints 10, 20, 50, 100 :200-204, times AddPoints'
     * 10; 1000 from the sixteenth kill, :160-172) and {@code oVar3E} its table index (0, 2, 4, 6 or $A),
     * which the explosion turns into the points sprite. The slot becomes explosion $18 with subtype 1
     * and keeps {@code oLayer} ({@code layer}); {@code fromBadnik} is {@code oExplodeBadnik} clear
     * (Explosion/Main.asm:7). The shared touch owner applies Sonic 1's bounce
     * ({@code .BounceDown}/{@code .BounceUp}, :189-198).
     */
    protected void killAsEnemy(com.openggf.game.PlayableEntity player, int layer, boolean fromBadnik) {
        int score = player.incrementBadnikChain();
        var game = services().gameState();
        if (game != null) game.addScore(score);
        int pointsIndex = switch (score) {
            case 100 -> 0;
            case 200 -> 2;
            case 500 -> 4;
            case 1000 -> 6;
            default -> 0xA;               // the sixteenth kill on: move.w #$A,oVar3E(a1)
        };
        becomeExplosion(pointsIndex, layer, 1, fromBadnik);
    }

    /**
     * The slot's object ID changes to the explosion in place: the slot is handed to the new object and
     * the layout entry stays loaded (the spawner's loaded bit is never cleared), which the shared
     * lifetime owner models as remembered for a saved-flags entry and removed otherwise.
     */
    protected void becomeExplosion(int pointsIndex, int layer, int explosionSubtype, boolean fromBadnik) {
        var objects = services().objectManager();
        int slot = com.openggf.level.objects.ObjectLifetimeOps.detachSlotForTransfer(this);
        com.openggf.level.objects.ObjectLifetimeOps.destroyLatched(this);
        if (objects == null) return;
        if (spawn.respawnTracked()) {
            com.openggf.level.objects.ObjectLifetimeOps.markSpawnRemembered(objects, spawn);
        } else {
            com.openggf.level.objects.ObjectLifetimeOps.removeSpawnFromActive(objects, spawn);
        }
        com.openggf.level.objects.ObjectLifetimeOps.addReplacementAtTransferredSlot(objects,
                new CdExplosion(x, y, explosionSubtype, fromBadnik, pointsIndex, layer), slot);
    }

    // ---- Slots ------------------------------------------------------------------------------------------

    /**
     * {@code FindObjSlot} ({@code SpawnObject} in the stage dialect): the first free slot from the first dynamic slot
     * (Object Spawner.asm:329-339; common/object_spawn.asm:279-291). It can lie below this object's own slot, so
     * the child's first turn comes in the next frame's pass. False when every slot is taken ({@code bne}).
     */
    protected final boolean findObjSlot(AbstractObjectInstance child) {
        var objects = services().objectManager();
        if (objects == null) return false;
        objects.addDynamicObject(child);
        return !child.isDestroyed();
    }

    /**
     * {@code FindNextObjSlot} ({@code SpawnObjectAfter}): the first free slot after this object's own (Object
     * Spawner.asm:352-373; common/object_spawn.asm:295-312), so the child's first turn comes later in this pass.
     * False when no later slot is free ({@code bne}).
     */
    protected final boolean findNextObjSlot(AbstractObjectInstance child) {
        var objects = services().objectManager();
        if (objects == null) return false;
        int own = getSlotIndex();
        if (own >= 0) objects.addDynamicObjectAfterSlot(child, own);
        else objects.addDynamicObjectAfterCurrent(child);
        return !child.isDestroyed();
    }

    /**
     * A child's test of its parent: native keeps the parent's slot address ({@code move.w a0,obj.var_xx(a1)}) and
     * reads the ID byte there on each of its own turns ({@code movea.w obj.var_xx(a0),a1; cmpi.b #id,obj.id(a1)}, as
     * in objects/r6/spike_chain.asm:176-179 and objects/r6/piston.asm:118-121). A parent that was deleted or despawned
     * (CheckObjDespawn clears the slot, Object Spawner.asm:385-399) fails it, in the same pass when the child's slot
     * is the later one; an object with the same ID that has taken the slot since passes it.
     */
    protected final boolean slotHoldsId(int slot, int id) {
        var objects = services().objectManager();
        return objects != null && slot >= 0 && objects.objectIdInSlot(slot) == (id & 0xFF);
    }

    /** The live object in {@code slot} ({@code movea.w obj.var_xx(a0),a1}), or {@code null} when it is free. */
    protected final com.openggf.level.objects.ObjectInstance slotOccupant(int slot) {
        var objects = services().objectManager();
        if (objects == null || slot < 0) return null;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof AbstractObjectInstance live && live.getSlotIndex() == slot && !live.isDestroyed()) {
                return object;
            }
        }
        return null;
    }

    /**
     * A copy made by copying the source's slot bytes (as the snake blocks' {@code rept oSnakePath/4; move.l (a2)+,(a3)+},
     * Level/Wacky Workbench/Objects/Snake Blocks/Main.asm:296-298) carries {@code oSprFlags} (byte 1,
     * Level/_Definitions.inc:34), so its first {@code SolidObject} and touch test see the source's last on-screen bit
     * instead of the clear bit of a slot from FindObjSlot.
     */
    protected final void copyDrawState(CdObject source) {
        renderPassSeen = source.renderPassSeen;
        drawnOnScreen = source.drawnOnScreen;
        drawCameraKnown = source.drawCameraKnown;
        drawCameraX = source.drawCameraX;
        drawCameraY = source.drawCameraY;
    }

    /** The loaded stage, or {@code null} outside a Sonic CD level. */
    protected com.openggf.game.soniccd.level.CdLevel cdLevel() {
        var levels = services().levelManager();
        return levels != null && levels.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel cd ? cd : null;
    }

    /** The stage's level flags, or a detached default set outside a level. */
    protected CdLevelFlags flags() {
        var level = cdLevel();
        return level != null ? level.flags() : new CdLevelFlags(false);
    }

    /** {@code timeZone} without bit 7: 0 past, 1 present, 2 future. */
    protected int timeZone() {
        var level = cdLevel();
        return level == null ? 1 : com.openggf.game.soniccd.level.CdLevel.nativeTimeZone(level.payload().stage().period());
    }

    /** {@code zone}: the zone byte, 0 Palmtree Panic to 6 Metallic Madness. */
    protected int zone() {
        var level = cdLevel();
        return level == null ? 0 : level.payload().stage().zone().ordinal();
    }

    /** {@code act}: 0, 1, 2 for acts 1-3. */
    protected int act() {
        var level = cdLevel();
        return level == null ? 0 : level.payload().stage().actNumber() - 1;
    }

    protected boolean hFlip() { return (getSpawn().renderFlags() & 1) != 0; }
    protected boolean vFlip() { return (getSpawn().renderFlags() & 2) != 0; }

    /** {@code StartResults}' bonuses: {@code timeBonus.w} and {@code ringBonus.w}. */
    public record ResultsBonuses(int time, int ring) { }

    /**
     * {@code StartResults} once its timer has run out (Level End/Main.asm:816-866), shared by the signpost, the flower
     * capsule and Stardust Speedway act 3's Amy: in the past {@code SCMD_FADEPCM}, then {@code SCMD_RESULTMUS}
     * (:821-828); {@code ctrlLocked} with {@code playerCtrlHold} $808, 0 in Stardust Speedway act 3 ({@code cmpi.w
     * #$502,zoneAct}); the results object $3A with its delay 16 ({@code LevelEndSpawn}, :838-842); the time bonus by
     * minutes * 60 + seconds over 15, capped at entry 20 (:844-857), and 100 per ring (:859-861). The caller sets its
     * own timer and routine ({@code move.b #180,oLvlEndTimer; addq.b #2,oRoutine}).
     */
    protected final ResultsBonuses startResults() {
        var services = services();
        if (timeZone() == 0) services.playMusic(CdSoundRequests.FADE_PCM);          // tst.b timeZone; bne .NotPast
        services.playMusic(CdSoundRequests.RESULTS_MUSIC);
        CdLevelFlags flags = flags();
        flags.ctrlLocked = true;                                                     // bset #0,ctrlLocked.w
        flags.playerCtrlHold = zone() == 5 && act() == 2 ? 0 : 0x808;
        var state = services.levelGamestate();
        long frames = state == null ? 0 : state.getTimerFrames();
        int seconds = (int) Math.min(9, frames / 3600) * 60 + (int) (frames / 60 % 60);
        int index = Math.min(seconds / 15, 20);                     // moveq #(.TimeBonusesEnd-.TimeBonuses)/2-1,d1
        CdObjectArtProvider provider = artProvider();
        int table = provider == null ? -1 : provider.entry().offset("timeBonuses");
        int timeBonus = table < 0 ? 0 : provider.image().u16(table + index * 2);
        int ringBonus = ((state == null ? 0 : state.getRings()) * 100) & 0xFFFF;
        var objects = services.objectManager();
        if (objects != null) objects.addDynamicObject(new CdResults(16, timeBonus, ringBonus));
        return new ResultsBonuses(timeBonus, ringBonus);
    }
}
