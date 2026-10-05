package infinite;

import com.openggf.game.PlayableEntity;
import com.openggf.game.mutation.*;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Scalar origin is captured by the ordinary mod-object rewind codec. */
public final class CourseController extends AbstractObjectInstance implements RewindRecreatable {
    // Mod design: Sonic's top running speed on the course, 7/8 of the stock S1 0x600.
    public static final int COURSE_MAX_SPEED = 0x540;
    // Mod design: Sonic_Animate shows the full-speed Run (and fast Roll2) from inertia $600, which the
    // lower top speed never reaches; the course shows them from 0x500, just under its top speed.
    public static final int RUN_ANIMATION_SPEED = 0x500;
    // GLFW_KEY_ESCAPE: a tap leaves the course for the Sonic 1 title (holding it still reaches the
    // engine's master title).
    private static final int KEY_ESCAPE = 256;
    /** Death menu options. */
    public static final String CONTINUE = "CONTINUE", RESTART = "RESTART", EXIT = "EXIT";
    // The camera's minimum scroll: 4px per tick, two thirds of the stock 0x600.
    private static final int MINIMUM_SCROLL = 0x400;
    private static final int FOLLOW_PERCENT = 30;
    // Ignore a jump already held while dying; about one second before a restart is accepted.
    private static final int RESTART_DELAY_FRAMES = 60;
    // Sonic's standing radius: his centre sits 19px above the floor.
    private static final int STANDING_RADIUS = 19;
    // A safe spot has floor from 32px behind to 64px ahead, varying by at most 24px.
    private static final int SAFE_BEHIND = 32;
    private static final int SAFE_AHEAD = 64;
    private static final int SAFE_RELIEF = 24;
    // Mod design: the window waits for loaded stock platforms up to this local X (window 16384).
    private static final int FORCED_REBASE_X = 12288;
    private static final int STONE_WINDOW = 64;
    // Post-continue blink, as long as the stock post-hit invulnerability ($78).
    private static final int RESUME_INVULNERABLE_FRAMES = 0x78;
    private int scrollFraction;
    private int scoreFraction;
    private boolean started;
    private boolean gameOver;
    private int gameOverFrames;
    private boolean restartRequested;
    private boolean exitRequested;
    // Death menu: index into menuOptions(); edge-detected so a held direction moves once.
    private int menuChoice;
    private boolean menuUpHeld;
    private boolean menuDownHeld;
    // Logical world X of the last grounded, pit-free spot; -1 until one is seen.
    private long safeWorldX = -1;
    // Ring count last frame: a life is earned each time it reaches a new multiple of 100.
    private int ringsSeen;
    public boolean gameOver() { return gameOver; }
    public boolean restartReady() { return gameOver && gameOverFrames >= RESTART_DELAY_FRAMES; }
    /** A spare life remains, so the menu offers CONTINUE (spending it) as well as RESTART. */
    public boolean canContinue() { return gameOver && session().livesLeft() > 0; }
    /** CONTINUE (when a spare life remains), RESTART and EXIT, top to bottom. */
    public List<String> menuOptions() {
        return canContinue() ? List.of(CONTINUE, RESTART, EXIT) : List.of(RESTART, EXIT);
    }
    public String menuSelection() {
        var options = menuOptions();
        return options.get(Math.min(menuChoice, options.size() - 1));
    }
    public boolean restartSelected() { return RESTART.equals(menuSelection()); }
    public boolean exitRequested() { return exitRequested; }
    /** Spare lives: the session starts with none and earns one per 100 rings. */
    public int displayLives() { return gameOver ? session().livesLeft() : services().gameState().getLives(); }
    public long safeWorldX() { return safeWorldX; }
    public double speedMultiplier() { return clock().displayMultiplier(); }
    public int secondsRemaining() { return clock().secondsRemaining(); }
    private long origin;
    private long visited; // One bit per retained 512px section, captured with origin.
    // One section bit per ring position lets allocation retry without duplicating a partial row.
    private long rings0;
    private long rings1;
    private long rings2;
    private long rings3;
    // One section bit per stone index, as for rings.
    private long stones0;
    private long stones1;
    private long stones2;
    // One section bit per placed monitor.
    private long monitors;
    // One section bit per placed zone hazard.
    private long hazards;
    public long originPixels() { return origin * 256; }
    public CourseController(ObjectSpawn spawn) { super(spawn, "Infinite Sonic course"); }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 0; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        CourseHud.draw(services(), this);
    }
    private ChallengeClock clock() { return services().gameService(ChallengeClock.class); }
    private CourseSession session() { return services().gameService(CourseSession.class); }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new CourseController(context.spawn());
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (player == null || exitRequested) return;
        services().levelGamestate().pauseTimer();
        services().levelManager().setForceHudSuppressed(true);
        showRunAtCourseSpeed(player);
        if (escapePressed()) { exitToTitle(); return; }
        if (gameOver) { awaitChoice(player); return; }
        if (player.getDead()) { endRun(player); return; }
        if (!started) {
            started = true;
            // Every load, including RESTART from the death menu, begins a fresh session with
            // no spare lives; CONTINUE never reloads, so it never reaches this.
            services().gameState().resetSession();
            session().reset();
            setLives(0);
            ringsSeen = services().levelGamestate().getRings();
            // A running start gives the player room to react before the scrolling edge arrives.
            player.setGSpeed((short) COURSE_MAX_SPEED);
            player.setXSpeed((short) COURSE_MAX_SPEED);
        }
        var camera = services().camera();
        // Let Sonic bank a lead before following him just right of centre.
        // Matching velocity everywhere consumes downhill gains immediately, while
        // the minimum scroll still takes away ground whenever he slows down.
        scrollFraction += MINIMUM_SCROLL;
        int minimumX = camera.getX() + scrollFraction / 256;
        int followX = player.getCentreX() - (camera.getWidth() * FOLLOW_PERCENT / 100);
        camera.setX((short) Math.max(minimumX, followX));
        scrollFraction %= 256;
        if (player.getCentreX() + player.getXRadius() < camera.getX()) {
            endRun(player);
            return;
        }
        // One point per minimum-scroll pixel: faster whole-game pacing increases points per second.
        scoreFraction += MINIMUM_SCROLL;
        services().gameState().addScore(scoreFraction / 256);
        scoreFraction %= 256;
        awardRingLives();
        rememberSafeSpot(player);
        var config = services().configuration();
        int fps = "PAL".equalsIgnoreCase(config.getString(com.openggf.configuration.SonicConfiguration.REGION))
                ? 50 : config.getInt(com.openggf.configuration.SonicConfiguration.FPS);
        int previousSeconds = secondsRemaining();
        clock().tick(fps);
        if (speedMultiplier() < 32 && secondsRemaining() <= 5
                && secondsRemaining() < previousSeconds) {
            services().audioManager().playSfx(com.openggf.audio.GameSound.AIR_DING);
        }
        recycleTerrain(player);
        // Hold our horizontal position through the normal camera step; retain vertical tracking.
        camera.requestForcedScroll(camera.getX() + camera.getWidth() / 2, player.getCentreY());
    }

    /** Lowers the profile's Run/Roll2 threshold so cruising Sonic shows his full-speed frames. */
    private static void showRunAtCourseSpeed(PlayableEntity player) {
        if (player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite
                && sprite.getAnimationProfile()
                        instanceof com.openggf.sprites.animation.ScriptedVelocityAnimationProfile profile
                && profile.getRunSpeedThreshold() != RUN_ANIMATION_SPEED) {
            sprite.setAnimationProfile(profile.withRunSpeedThreshold(RUN_ANIMATION_SPEED));
        }
    }

    /** Escape, or the gamepad Back button, this frame. The module captures the live input at the title. */
    private boolean escapePressed() {
        var input = services().gameService(com.openggf.control.InputHandler.class);
        return input != null && (input.isKeyPressed(KEY_ESCAPE) || input.isGamepadBackButtonPressed());
    }

    /**
     * EXIT: leave the course for the Sonic 1 title and its zone picker, through the engine's
     * GAME OVER exit (fade to black, then the title screen), as the stock card does with no continues.
     */
    private void exitToTitle() {
        exitRequested = true;
        clock().end();
        services().levelManager().requestGameOverExit(com.openggf.game.GameOverExit.TITLE_SCREEN);
    }

    /**
     * Lives come only from rings: one each time the counter reaches 100, 200, 300 and so on.
     * The stock 100/200 awards are pre-claimed every frame so they never add a second life.
     */
    private void awardRingLives() {
        var level = services().levelGamestate();
        level.setRingExtraLifeFlags(0x06);
        int rings = level.getRings();
        int earned = rings / CourseSession.RINGS_PER_LIFE - ringsSeen / CourseSession.RINGS_PER_LIFE;
        ringsSeen = rings;
        if (earned <= 0) return;
        for (int i = 0; i < earned; i++) services().gameState().addLife();
        var profile = services().audioManager().getAudioProfile();
        if (profile != null) services().audioManager().playMusic(profile.getExtraLifeMusicId());
    }

    private void rememberSafeSpot(PlayableEntity player) {
        if (player.getAir() || player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite
                && sprite.isHurt()) return;
        long worldX = originPixels() + player.getCentreX();
        if (safeFloor(worldX)) safeWorldX = worldX;
    }

    private boolean safeFloor(long worldX) {
        var library = services().gameService(TerrainLibrary.class);
        int floor = library.floorAt(worldX);
        if (floor < 0) return false;
        for (int dx = -SAFE_BEHIND; dx <= SAFE_AHEAD; dx += 4) {
            int other = library.floorAt(worldX + dx);
            if (other < 0 || Math.abs(other - floor) > SAFE_RELIEF) return false;
        }
        return true;
    }

    private void endRun(PlayableEntity player) {
        gameOver = true;
        var state = services().gameState();
        session().died(state.getLives(), clock().capture());
        clock().end();
        // The death routine subtracts the life itself once the corpse falls. Leaving exactly
        // one makes that subtraction reach zero, so the corpse is held for the death menu
        // (the course's game-over flow) instead of counting down to a stock restart.
        setLives(1);
        player.applyCrushDeath();
    }

    private void setLives(int lives) {
        var state = services().gameState();
        while (state.getLives() > lives) state.loseLife();
        while (state.getLives() < lives) state.addLife();
    }

    private void awaitChoice(PlayableEntity player) {
        if (!(player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite)) return;
        boolean up = sprite.isUpPressed();
        boolean down = sprite.isDownPressed();
        boolean moved = (up && !menuUpHeld) || (down && !menuDownHeld);
        menuUpHeld = up;
        menuDownHeld = down;
        if (!restartReady()) { gameOverFrames++; return; }
        if (restartRequested) return;
        if (moved) {
            int last = menuOptions().size() - 1;
            int next = Math.max(0, Math.min(last, menuChoice + (down && !up ? 1 : up && !down ? -1 : 0)));
            if (next != menuChoice) {
                menuChoice = next;
                services().audioManager().playSfx(ZoneMenu.SFX_SWITCH);
            }
        }
        // Player 1 A, SPACE by default.
        if (!sprite.isJumpJustPressed()) return;
        switch (menuSelection()) {
            case CONTINUE -> { resume(sprite); return; }
            case EXIT -> { exitToTitle(); return; }
            default -> { }
        }
        // Lives stay at zero until the reload so a corpse still falling cannot queue an
        // ordinary death restart; the reload re-enters loadLevelOverride and a fresh controller.
        restartRequested = true;
        services().levelManager().requestRespawn();
    }

    /**
     * CONTINUE: revive Sonic in place at the last safe spot, without reloading the level.
     * Score, speed stage and countdown position, terrain and cleared enemies all carry on.
     */
    private void resume(com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        setLives(session().resume(clock()));
        var camera = services().camera();
        long worldX = resumeSpot(camera);
        int localX = (int) (worldX - originPixels());
        int y = services().gameService(TerrainLibrary.class).floorAt(worldX) - STANDING_RADIUS;
        // resetState is the level-start reset: it clears the death routine, hurt, object
        // control, rolling and the dead animation without touching rings or score.
        sprite.resetState();
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(sprite, localX);
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(sprite, y);
        sprite.resetPositionAndStatTableHistoryAtCentre((short) localX, (short) y);
        sprite.setDirection(com.openggf.physics.Direction.RIGHT);
        sprite.setGSpeed((short) COURSE_MAX_SPEED);
        sprite.setXSpeed((short) COURSE_MAX_SPEED);
        sprite.setYSpeed((short) 0);
        sprite.setInvulnerableFrames(RESUME_INVULNERABLE_FRAMES);
        // As a stock respawn, the run resumes with no rings.
        services().levelGamestate().setRings(0);
        ringsSeen = 0;
        // Applying the death froze the camera; put Sonic a quarter of the way across.
        camera.setFrozen(false);
        camera.setX((short) Math.max(0, localX - camera.getWidth() / 4));
        camera.setY((short) Math.max(0, y - camera.getHeight() / 2));
        scrollFraction = 0;
        gameOver = false;
        gameOverFrames = 0;
        menuChoice = 0;
        // The death flow faded the music out.
        services().audioManager().playMusic(services().levelManager().getCurrentLevelMusicId());
    }

    /** The last safe spot, or the first safe floor ahead of the screen's left edge. */
    private long resumeSpot(com.openggf.camera.Camera camera) {
        if (safeWorldX >= 0 && safeWorldX - originPixels() >= 64) return safeWorldX;
        long x = originPixels() + Math.max(64, camera.getX() + 64);
        for (int i = 0; i < 1024 && !safeFloor(x); i++) x += 16;
        return x;
    }

    private void recycleTerrain(PlayableEntity player) {
        int delta = player.getCentreX() >= 8192 ? 4096
                : origin > 0 && player.getCentreX() < 2048 ? -4096 : 0;
        // Stock platforms keep their spawn coordinates and do not follow the shift, so the
        // window waits until none are loaded. The 16384px window leaves thousands of pixels
        // of slack; past the hard limit any still-loaded stones are dropped and re-spawned.
        var live = liveStones();
        // Spilled rings keep their local coordinates too; let them settle before shifting.
        if (delta != 0 && live.isEmpty() && spilledRings()) {
            boolean forced = delta > 0 ? player.getCentreX() >= FORCED_REBASE_X : player.getCentreX() < 1024;
            if (!forced) delta = 0;
        }
        if (delta != 0 && !live.isEmpty()) {
            boolean forced = delta > 0 ? player.getCentreX() >= FORCED_REBASE_X : player.getCentreX() < 1024;
            if (!forced) delta = 0;
            else {
                for (var stone : live) stone.setDestroyed(true);
                stones0 = stones1 = stones2 = 0;
            }
        }
        if (delta == 0) {
            populateEncounters();
            populateRings();
            populateStones();
            populateMonitors();
            populateHazards();
            return;
        }
        origin += delta / 256;
        visited = shiftSections(visited, delta);
        rings0 = shiftSections(rings0, delta);
        rings1 = shiftSections(rings1, delta);
        rings2 = shiftSections(rings2, delta);
        rings3 = shiftSections(rings3, delta);
        stones0 = shiftSections(stones0, delta);
        stones1 = shiftSections(stones1, delta);
        stones2 = shiftSections(stones2, delta);
        monitors = shiftSections(monitors, delta);
        hazards = shiftSections(hazards, delta);
        services().objectManager().applyLevelRepeatOffsetToActiveObjects(-delta, 0);
        var library = services().gameService(TerrainLibrary.class);
        var level = services().currentLevel();
        var surface = LevelMutationSurface.forLevel(level);
        for (int x = 0; x < TerrainLibrary.WIDTH; x++) {
            for (int y = 0; y < library.height(); y++) {
                surface.setBlockInMap(0, x, y, library.cell(origin + x, y));
            }
        }
        // PlayableEntity.shiftX is the public native-position delta operation:
        // it preserves 16:16 fractions, velocity and the current pose.
        player.shiftX(-delta);
        var camera = services().camera();
        camera.setX((short) (camera.getX() - delta));
        camera.setXCopy((short) (camera.getXCopy() - delta));
        services().levelManager().invalidateAllTilemaps();
        populateEncounters();
        populateRings();
        populateStones();
        populateMonitors();
        populateHazards();
    }

    private void populateMonitors() {
        var library = services().gameService(TerrainLibrary.class);
        var camera = services().camera();
        for (int section = 0; section < TerrainLibrary.WIDTH / 2; section++) {
            long bit = 1L << section;
            if ((monitors & bit) != 0) continue;
            var monitor = MonitorPlan.at(library, origin / 2 + section);
            if (monitor == null) { monitors |= bit; continue; }
            int localX = (int) (monitor.worldX() - originPixels());
            if (localX < camera.getX() - 192 || localX > camera.getX() + camera.getWidth() + 192) continue;
            if (!services().objectManager().hasFreeDynamicSlot()) return;
            var spawn = new ObjectSpawn(localX, monitor.y(), 0, monitor.kind(), 0, false, monitor.y(), -1,
                    "infinite-sonic", "infinite-sonic:monitor");
            spawnFreeChild(() -> new CourseMonitor(spawn));
            monitors |= bit;
        }
    }

    private void populateHazards() {
        var library = services().gameService(TerrainLibrary.class);
        var camera = services().camera();
        for (int section = 0; section < TerrainLibrary.WIDTH / 2; section++) {
            long bit = 1L << section;
            if ((hazards & bit) != 0) continue;
            var hazard = HazardPlan.at(library, origin / 2 + section);
            if (hazard == null) { hazards |= bit; continue; }
            int localX = (int) (hazard.worldX() - originPixels());
            // Swinging hazards reach about 100px either side, so they arrive a little earlier.
            if (localX < camera.getX() - 256 || localX > camera.getX() + camera.getWidth() + 256) continue;
            if (!services().objectManager().hasFreeDynamicSlot()) return;
            var spawn = new ObjectSpawn(localX, hazard.floor(), 0, hazard.kind() | hazard.phase() << 4, 0, false,
                    hazard.floor(), -1, "infinite-sonic", "infinite-sonic:hazard");
            spawnFreeChild(() -> new CourseHazard(spawn));
            hazards |= bit;
        }
    }

    private boolean spilledRings() {
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof com.openggf.level.rings.LostRingObjectInstance && !object.isDestroyed()) return true;
        }
        return false;
    }

    /** Loaded stock platforms. The course places no other stock objects with these ids. */
    private List<AbstractObjectInstance> liveStones() {
        var live = new java.util.ArrayList<AbstractObjectInstance>();
        for (var object : services().objectManager().getActiveObjects()) {
            // Player-owned power-ups (the shield) have no spawn.
            if (object.getSpawn() == null) continue;
            int id = object.getSpawn().objectId();
            if (CoursePlatforms.isCoursePlatform(id)
                    && object instanceof AbstractObjectInstance stock && !stock.isDestroyed()) live.add(stock);
        }
        return live;
    }

    /**
     * Spawns the stock platforms of upcoming platform stretches. Stock objects delete
     * themselves outside the out_of_range window (camera-128 to camera+width+192, in 128px
     * steps), so stones only spawn within 64px of the screen.
     */
    private void populateStones() {
        var library = services().gameService(TerrainLibrary.class);
        var camera = services().camera();
        for (int section = 0; section < TerrainLibrary.WIDTH / 2; section++) {
            long bit = 1L << section;
            if ((stones0 & stones1 & stones2 & bit) != 0) continue;
            var stones = PlatformPlan.at(library, origin / 2 + section);
            for (int i = 0; i < PlatformPlan.MAX_STONES; i++) {
                long mask = switch (i) { case 0 -> stones0; case 1 -> stones1; default -> stones2; };
                if ((mask & bit) != 0) continue;
                if (i < stones.length) {
                    var stone = stones[i];
                    int localX = (int) (stone.worldX() - originPixels());
                    if (localX < camera.getX() - STONE_WINDOW
                            || localX > camera.getX() + camera.getWidth() + STONE_WINDOW) continue;
                    if (!services().objectManager().hasFreeDynamicSlot()) return;
                    var registry = services().gameService(ObjectRegistry.class);
                    var spawn = new ObjectSpawn(localX, stone.y(), stone.objectId(), stone.subtype(), 0, false,
                            stone.y(), -1);
                    spawnFreeChild(() -> (AbstractObjectInstance) registry.create(spawn));
                }
                switch (i) { case 0 -> stones0 |= bit; case 1 -> stones1 |= bit; default -> stones2 |= bit; }
            }
        }
    }

    private static long shiftSections(long bits, int delta) {
        return delta > 0 ? bits >>> 8 : (bits << 8) & 0xffffffffL;
    }

    private void populateRings() {
        var library = services().gameService(TerrainLibrary.class);
        var camera = services().camera();
        for (int section = 0; section < TerrainLibrary.WIDTH / 2; section++) {
            long bit = 1L << section;
            if ((rings0 & rings1 & rings2 & rings3 & bit) != 0) continue;
            var row = RingPlan.at(library, origin / 2 + section);
            if (row == null) {
                rings0 |= bit; rings1 |= bit; rings2 |= bit; rings3 |= bit;
                continue;
            }
            // Shorter rows (above stones) mark their unused ring indices as already placed.
            for (int i = row.count(); i < RingPlan.COUNT; i++) {
                switch (i) { case 0 -> rings0 |= bit; case 1 -> rings1 |= bit;
                    case 2 -> rings2 |= bit; default -> rings3 |= bit; }
            }
            for (int i = 0; i < row.count(); i++) {
                long mask = switch (i) { case 0 -> rings0; case 1 -> rings1; case 2 -> rings2; default -> rings3; };
                if ((mask & bit) != 0) continue;
                long worldX = row.x()[i];
                int localX = (int) (worldX - originPixels());
                if (localX < camera.getX() - 192 || localX > camera.getX() + camera.getWidth() + 192) continue;
                if (!services().objectManager().hasFreeDynamicSlot()) return;
                int y = row.y()[i];
                var spawn = new ObjectSpawn(localX, y, 0, 0, 0, false, y, -1,
                        "infinite-sonic", "infinite-sonic:ring");
                spawnFreeChild(() -> new CourseRing(spawn));
                switch (i) { case 0 -> rings0 |= bit; case 1 -> rings1 |= bit;
                    case 2 -> rings2 |= bit; default -> rings3 |= bit; }
            }
        }
    }

    private void populateEncounters() {
        var library = services().gameService(TerrainLibrary.class);
        var camera = services().camera();
        for (int i = 0; i < TerrainLibrary.WIDTH / 2; i++) {
            long bit = 1L << i;
            if ((visited & bit) != 0) continue;
            var encounter = EncounterPlan.at(library, origin / 2 + i);
            if (encounter == null) { visited |= bit; continue; }
            int localX = (int) (encounter.worldX() - originPixels());
            if (localX < camera.getX() - 192 || localX > camera.getX() + camera.getWidth() + 192) continue;
            if (!services().objectManager().hasFreeDynamicSlot()) continue;
            var spawn = new ObjectSpawn(localX, encounter.y(), 0, encounter.species(),
                    0, false, encounter.y(), -1, "infinite-sonic", "infinite-sonic:badnik");
            spawnChild(() -> new CourseBadnik(spawn, encounter.worldX()));
            visited |= bit;
        }
    }
}
