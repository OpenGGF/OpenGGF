package infinite;

import com.openggf.game.PlayableEntity;
import com.openggf.game.mutation.*;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Scalar origin is captured by the ordinary mod-object rewind codec. */
public final class CourseController extends AbstractObjectInstance implements RewindRecreatable {
    // S1 Sonic normal maximum is 0x600 in PhysicsProfile; the mod scrolls at 75%.
    private static final int NORMAL_RUN_SPEED = 0x600;
    private static final int MINIMUM_SCROLL = NORMAL_RUN_SPEED * 3 / 4;
    private static final int FOLLOW_PERCENT = 60;
    // Ignore a jump already held while dying; about one second before a restart is accepted.
    private static final int RESTART_DELAY_FRAMES = 60;
    private int scrollFraction;
    private int scoreFraction;
    private boolean started;
    private boolean gameOver;
    private int gameOverFrames;
    private boolean restartRequested;
    // Death menu: CONTINUE (resume the run) or RESTART; edge-detected so a held direction moves once.
    private boolean restartSelected;
    private boolean menuUpHeld;
    private boolean menuDownHeld;
    public boolean gameOver() { return gameOver; }
    public boolean restartReady() { return gameOver && gameOverFrames >= RESTART_DELAY_FRAMES; }
    /** Lives remain after this death, so the menu offers CONTINUE as well as RESTART. */
    public boolean canContinue() { return gameOver && session().livesLeft() > 0; }
    public boolean restartSelected() { return restartSelected; }
    /** Session lives: the life just lost is already gone while the death menu is up. */
    public int displayLives() { return gameOver ? session().livesLeft() : services().gameState().getLives(); }
    public double speedMultiplier() { return clock().displayMultiplier(); }
    public int secondsRemaining() { return clock().secondsRemaining(); }
    private long origin;
    private long visited; // One bit per retained 512px section, captured with origin.
    // One section bit per ring position lets allocation retry without duplicating a partial row.
    private long rings0;
    private long rings1;
    private long rings2;
    private long rings3;
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
        if (player == null) return;
        services().levelGamestate().pauseTimer();
        services().levelManager().setForceHudSuppressed(true);
        if (gameOver) { awaitChoice(player); return; }
        if (player.getDead()) { endRun(player); return; }
        if (!started) {
            started = true;
            var state = services().gameState();
            int lives = session().livesLeft();
            int resumeScore = session().consumeContinueScore();
            if (resumeScore < 0) {
                // A new run, including RESTART from the death menu, begins from a fresh session.
                state.resetSession();
                session().reset();
            } else {
                // CONTINUE keeps the score and session lives; the clock was restored by the reload.
                state.addScore(resumeScore - state.getScore());
                setLives(lives);
            }
            // A running start gives the player room to react before the scrolling edge arrives.
            player.setGSpeed((short) NORMAL_RUN_SPEED);
            player.setXSpeed((short) NORMAL_RUN_SPEED);
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
        // Enemy points count too, so test the threshold against the whole score.
        if (session().awardsLife(services().gameState().getScore())) {
            services().gameState().addLife();
            var profile = services().audioManager().getAudioProfile();
            if (profile != null) services().audioManager().playMusic(profile.getExtraLifeMusicId());
        }
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

    private void endRun(PlayableEntity player) {
        gameOver = true;
        var state = services().gameState();
        session().died(state.getLives() - 1, state.getScore(), clock().capture());
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
        if (canContinue() && moved) {
            restartSelected = !restartSelected;
            services().audioManager().playSfx(ZoneMenu.SFX_SWITCH);
        }
        // Player 1 A, SPACE by default. Lives stay at zero until the reload so a
        // corpse still falling cannot queue an ordinary death restart; the reload
        // re-enters loadLevelOverride and a fresh controller.
        if (sprite.isJumpJustPressed()) {
            restartRequested = true;
            if (canContinue() && !restartSelected) session().requestContinue();
            services().levelManager().requestRespawn();
        }
    }

    private void recycleTerrain(PlayableEntity player) {
        int delta = player.getCentreX() >= 8192 ? 4096
                : origin > 0 && player.getCentreX() < 2048 ? -4096 : 0;
        if (delta == 0) {
            populateEncounters();
            populateRings();
            return;
        }
        origin += delta / 256;
        visited = shiftSections(visited, delta);
        rings0 = shiftSections(rings0, delta);
        rings1 = shiftSections(rings1, delta);
        rings2 = shiftSections(rings2, delta);
        rings3 = shiftSections(rings3, delta);
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
            for (int i = 0; i < row.count(); i++) {
                long mask = switch (i) { case 0 -> rings0; case 1 -> rings1; case 2 -> rings2; default -> rings3; };
                if ((mask & bit) != 0) continue;
                long worldX = row.worldX() + i * RingPlan.SPACING;
                int localX = (int) (worldX - originPixels());
                if (localX < camera.getX() - 192 || localX > camera.getX() + camera.getWidth() + 192) continue;
                if (!services().objectManager().hasFreeDynamicSlot()) return;
                int y = library.floorAt(worldX) - RingPlan.CLEARANCE;
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
