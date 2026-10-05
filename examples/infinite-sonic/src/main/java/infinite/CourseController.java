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
    // Sonic is held just left of centre: enough lookahead, and room behind him to recover.
    static final int FOLLOW_PERCENT = 45;
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
    // The zone's top 10 shows for the first five seconds of a run, fading over the last half second.
    static final int OPENING_BOARD_FRAMES = 300, OPENING_BOARD_FADE = 30;
    // Overtaking the zone's top score flashes a banner for three seconds.
    static final int CELEBRATE_FRAMES = 180;
    // Mod design: CONTINUE replays Sonic's way back as a ghost (45-90 frames by distance), then
    // holds him at the restart spot until the player goes (up to two seconds), then shows GO!.
    static final int GLIDE_MIN_FRAMES = 45, GLIDE_MAX_FRAMES = 90;
    static final int READY_FRAMES = 120, READY_INPUT_DELAY = 20, GO_FRAMES = 40;
    // A restart spot needs pit-free floor this far ahead (about 1.4s at the course top speed).
    static final int RESUME_RUNWAY = 448;
    // Sweat: a drop leaves Sonic's head every 28 updates while in danger, every 14 with no rings;
    // each takes 18 updates to arc away and fall.
    static final int SWEAT_PERIOD = 28, SWEAT_PERIOD_BROKE = 14, SWEAT_LIFE = 18;
    /** CONTINUE phases. */
    static final int RESUME_NONE = 0, RESUME_GLIDE = 1, RESUME_READY = 2;
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
    // Updates since the run started, counted up to the end of the opening leaderboard.
    private int openingFrames;
    // The zone's top score from earlier runs when this run started; -1 until then.
    private int scoreToBeat = -1;
    private int celebrateFrames;
    // This run's leaderboard rank once it has been recorded (at a death or exit), 0 if unplaced.
    private int rank;
    // CONTINUE sequence: phase, frames into it, the glide's length, and its endpoints (local).
    private int resumePhase;
    private int resumeFrame;
    private int glideFrames;
    private int ghostFromX;
    private int ghostFromY;
    private int ghostToX;
    private int ghostToY;
    private int cameraFromX;
    private int cameraToX;
    private int goFrames;
    // Updates spent in danger (fewer than 20 rings, no shield); drives the sweat drops.
    private int dangerFrames;
    // Sonic's last living pose and where he died, which the ghost starts from.
    private int ghostFrame;
    private boolean ghostFlip;
    private int deathX;
    private int deathY;
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
    /** The registry zone the course was built from, which keys its leaderboard. */
    public int zone() { return services().levelManager().getCurrentZone(); }
    /** This run's 1-based place in the zone's top 10 after its last recorded death or exit; 0 if unplaced. */
    public int rank() { return rank; }
    /** Opacity of the zone's top-10 board shown as the run starts; 0 once it has gone. */
    public float openingBoardAlpha() {
        if (!started || gameOver || openingFrames >= OPENING_BOARD_FRAMES) return 0f;
        return Math.min(1f, (float) (OPENING_BOARD_FRAMES - openingFrames) / OPENING_BOARD_FADE);
    }
    /**
     * Sonic is in danger: no shield and too few rings to pay the 20-ring toll, so the next hit
     * knocks him back and takes every ring (or, with none, is fatal).
     */
    public boolean inDanger() {
        if (gameOver || resumePhase != RESUME_NONE) return false;
        var player = services().camera().getFocusedSprite();
        return player != null && !player.getDead() && !player.hasShield()
                && services().levelGamestate().getRings() < CourseGuard.RING_TOLL;
    }
    /** Updates spent in danger, for the sweat and the flashing ring count. */
    public int dangerFrames() { return dangerFrames; }
    /** The zone's top score, counting this run once it is ahead. */
    public int topScore() {
        return Math.max(services().gameService(Leaderboard.class).best(zone(), runId()), services().gameState().getScore());
    }
    /** RESUME_NONE, RESUME_GLIDE (the ghost glides back) or RESUME_READY (waiting to go). */
    public int resumePhase() { return resumePhase; }
    /** Frames of GO! left after a resumed run sets off. */
    public int goFrames() { return goFrames; }
    /** True for a few seconds after the run's score overtakes the zone's previous top score. */
    public boolean celebrating() { return celebrateFrames > 0; }
    public int celebrateFrames() { return celebrateFrames; }
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
    private long stones3;
    private long stones4;
    private long stones5;
    private long stones6;
    private long stones7;
    // Stone slots per section: platform stretches use three, a high road up to seven (64px platforms 8px apart).
    private static final int SECTION_STONES = 8;
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
        drawGhost();
        drawSweat();
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
        if (resumePhase != RESUME_NONE
                && player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
            if (!player.getDead()) {
                continueResume(sprite);
                return;
            }
            // Nothing in the course can kill him here, but a death still ends the sequence cleanly.
            resumePhase = RESUME_NONE;
            sprite.setHidden(false);
            sprite.setObjectControlled(false);
        }
        if (player.getDead()) { endRun(player); return; }
        if (goFrames > 0) goFrames--;
        if (player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
            ghostFrame = sprite.getMappingFrame();
            ghostFlip = sprite.getRenderHFlip();
        }
        if (!started) {
            started = true;
            // Every load, including RESTART from the death menu, begins a fresh session with
            // no spare lives; CONTINUE never reloads, so it never reaches this.
            services().gameState().resetSession();
            session().reset();
            setLives(0);
            ringsSeen = services().levelGamestate().getRings();
            scoreToBeat = services().gameService(Leaderboard.class).best(zone(), runId());
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
        celebrateTopScore();
        dangerFrames = inDanger() ? dangerFrames + 1 : 0;
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

    private long runId() { return services().gameService(CourseRun.class).id(); }

    /** Counts down the opening board, and cheers the moment the run takes the zone's top score. */
    private void celebrateTopScore() {
        if (openingFrames < OPENING_BOARD_FRAMES) openingFrames++;
        if (celebrateFrames > 0) celebrateFrames--;
        // A first run on an empty board has nobody to overtake, so it is congratulated when it ends.
        if (scoreToBeat <= 0 || services().gameState().getScore() <= scoreToBeat) return;
        scoreToBeat = Integer.MAX_VALUE;
        celebrateFrames = CELEBRATE_FRAMES;
        services().audioManager().playSfx(com.openggf.audio.GameSound.CHECKPOINT);
    }

    /**
     * Records this run's score on the zone's leaderboard (replacing its own earlier entry), and
     * cheers a new top score. Called at every death and when leaving, so the board always holds
     * the run's best even if it continues and later quits.
     */
    private void recordScore() {
        int score = services().gameState().getScore();
        int previousTop = services().gameService(Leaderboard.class).best(zone(), runId());
        rank = services().gameService(Leaderboard.class)
                .submit(zone(), runId(), score, clock().displayMultiplier());
        if (rank == 1 && score > previousTop) {
            services().audioManager().playSfx(com.openggf.audio.GameSound.CHECKPOINT);
        }
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
        // A live run is recorded here; a dead one was recorded when Sonic died.
        if (!gameOver && started) recordScore();
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
        deathX = player.getCentreX();
        deathY = player.getCentreY();
        var state = services().gameState();
        session().died(state.getLives(), clock().capture());
        recordScore();
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
     * CONTINUE: spends the life and starts the way back. Sonic is revived at the restart spot
     * (hidden, held still and protected), and a ghost of him glides there from where he died
     * while the camera follows. Then he waits, visible, until the player sets off (or two
     * seconds pass); only then does the run, its clock and its scrolling resume. Score, speed
     * stage and countdown, terrain, rings and cleared enemies all carry on. No level reload.
     */
    private void resume(com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        setLives(session().livesLeft() - 1);
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
        sprite.setGSpeed((short) 0);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setObjectControlled(true);
        sprite.setHidden(true);
        sprite.setInvulnerableFrames(RESUME_INVULNERABLE_FRAMES);
        // Unlike a stock respawn, the run keeps its rings; only RESTART and EXIT clear them.
        // Ring lives already earned for the count are not awarded again.
        ringsSeen = services().levelGamestate().getRings();
        // The death froze the camera; the glide moves it back to the follow point.
        camera.setFrozen(false);
        cameraFromX = camera.getX();
        cameraToX = Math.max(0, localX - camera.getWidth() * FOLLOW_PERCENT / 100);
        // A pit death ends below the screen: the ghost rises from its bottom edge.
        ghostFromX = Math.max(camera.getX() + 16, Math.min(camera.getX() + camera.getWidth() - 16, deathX));
        ghostFromY = Math.max(camera.getY() + 16, Math.min(camera.getY() + camera.getHeight() - 16, deathY));
        ghostToX = localX;
        ghostToY = y;
        int distance = Math.abs(ghostToX - ghostFromX) + Math.abs(ghostToY - ghostFromY);
        glideFrames = Math.max(GLIDE_MIN_FRAMES, Math.min(GLIDE_MAX_FRAMES, GLIDE_MIN_FRAMES / 2 + distance / 12));
        resumePhase = RESUME_GLIDE;
        resumeFrame = 0;
        scrollFraction = 0;
        gameOver = false;
        gameOverFrames = 0;
        menuChoice = 0;
        // The death flow faded the music out.
        services().audioManager().playMusic(services().levelManager().getCurrentLevelMusicId());
    }

    /** Advances the CONTINUE glide and ready hold; the clock, score and scrolling wait. */
    private void continueResume(com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        var camera = services().camera();
        sprite.setInvulnerableFrames(RESUME_INVULNERABLE_FRAMES);
        resumeFrame++;
        if (resumePhase == RESUME_GLIDE) {
            double t = glideProgress(resumeFrame);
            camera.setX((short) Math.round(cameraFromX + (cameraToX - cameraFromX) * t));
            // Camera Y follows the ghost through the camera's own vertical tracking.
            camera.requestForcedScroll(camera.getX() + camera.getWidth() / 2, ghostY());
            if (resumeFrame >= glideFrames) {
                resumePhase = RESUME_READY;
                resumeFrame = 0;
                sprite.setHidden(false);
            }
            return;
        }
        camera.setX((short) cameraToX);
        camera.requestForcedScroll(camera.getX() + camera.getWidth() / 2, sprite.getCentreY());
        boolean go = resumeFrame > READY_INPUT_DELAY && (sprite.isRightPressed() || sprite.isJumpPressed());
        if (!go && resumeFrame < READY_FRAMES) return;
        // Set off: the clock eases back up to the speed the run died at, from a running start.
        session().resume(clock());
        resumePhase = RESUME_NONE;
        sprite.setObjectControlled(false);
        sprite.setGSpeed((short) COURSE_MAX_SPEED);
        sprite.setXSpeed((short) COURSE_MAX_SPEED);
        goFrames = GO_FRAMES;
    }

    /** Smoothstep through the glide: slow out of the death spot, slow into the restart spot. */
    private double glideProgress(int frame) {
        double t = Math.min(1.0, (double) frame / Math.max(1, glideFrames));
        return t * t * (3 - 2 * t);
    }
    /** The ghost's local position this frame. */
    public int ghostX() { return ghostX(resumeFrame); }
    public int ghostY() { return ghostY(resumeFrame); }
    private int ghostX(int frame) { return (int) Math.round(ghostFromX + (ghostToX - ghostFromX) * glideProgress(frame)); }
    private int ghostY(int frame) { return (int) Math.round(ghostFromY + (ghostToY - ghostFromY) * glideProgress(frame)); }

    /**
     * Draws the gliding ghost with Sonic's own sprite art and last living pose, translucent,
     * with two fainter echoes trailing back towards where he died.
     */
    private void drawGhost() {
        if (resumePhase != RESUME_GLIDE) return;
        if (!(services().camera().getFocusedSprite()
                instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite)) return;
        var renderer = sprite.getSpriteRenderer();
        if (renderer == null) return;
        var graphics = services().graphicsManager();
        for (int echo = 2; echo >= 0; echo--) {
            int frame = Math.max(0, resumeFrame - echo * 4);
            graphics.flushPatternBatch();
            graphics.beginGhostRenderEffect(0.55f - echo * 0.18f);
            graphics.beginPatternBatch();
            try {
                renderer.drawFrame(ghostFrame, ghostX(frame), ghostY(frame), ghostFlip, false);
            } finally {
                graphics.flushPatternBatch();
                graphics.endGhostRenderEffect();
            }
        }
    }

    /**
     * Sweat while in danger: light-blue drops that leave the back of Sonic's head, arc away and
     * fall. Code-drawn pixels in world space, in front of Sonic.
     */
    private void drawSweat() {
        if (dangerFrames == 0 || !inDanger()) return;
        var player = services().camera().getFocusedSprite();
        if (player == null || player.isHidden()) return;
        int period = services().levelGamestate().getRings() == 0 ? SWEAT_PERIOD_BROKE : SWEAT_PERIOD;
        // Away from the way he faces: behind his head.
        int away = player.getRenderHFlip() ? 1 : -1;
        for (int born = dangerFrames - dangerFrames % period; born > dangerFrames - SWEAT_LIFE; born -= period) {
            int age = dangerFrames - born;
            if (born <= 0 || age < 0) continue;
            // Alternate drops fly from either side of his head.
            int side = (born / period) % 2 == 0 ? away : -away;
            int x = player.getCentreX() + side * (6 + age);
            int y = player.getCentreY() - 14 - 2 * age + age * age / 6;
            drawDrop(x, y, age < SWEAT_LIFE - 4 ? 1f : (SWEAT_LIFE - age) / 4f);
        }
    }

    /** A 4x5 teardrop with a white glint. */
    private void drawDrop(int x, int y, float alpha) {
        String[] rows = {".bb.", ".bb.", "bwbb", "bbbb", ".bb."};
        var graphics = services().graphicsManager();
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < 4; column++) {
                char c = rows[row].charAt(column);
                if (c == '.') continue;
                float r = c == 'w' ? 1f : 0.55f, g = c == 'w' ? 1f : 0.85f;
                int px = x - 2 + column, py = y - 2 + row;
                graphics.registerCommand(new GLCommand(GLCommand.CommandType.RECTI, 0,
                        GLCommand.BlendType.ONE_MINUS_SRC_ALPHA, r, g, 1f, alpha, px, py, px + 1, py + 1));
            }
        }
    }

    /**
     * Where CONTINUE restarts: the last safe spot at or before the death, or the nearest one
     * behind it, that has {@link #RESUME_RUNWAY} pixels of pit-free floor ahead, so the run
     * never resumes on the lip of a pit. If the retained window has none behind, the first
     * such spot ahead of the screen's left edge.
     */
    private long resumeSpot(com.openggf.camera.Camera camera) {
        long death = originPixels() + deathX;
        long start = safeWorldX >= 0 ? Math.min(safeWorldX, death) : death;
        long limit = originPixels() + 64;
        for (long x = start; x >= limit; x -= 16) {
            if (safeFloor(x) && runwayClear(x)) return x;
        }
        long x = originPixels() + Math.max(64, camera.getX() + 64);
        for (int i = 0; i < 2048; i++, x += 16) {
            if (safeFloor(x) && runwayClear(x)) return x;
        }
        return safeWorldX >= 0 && safeWorldX - originPixels() >= 64 ? safeWorldX : x;
    }

    private boolean runwayClear(long worldX) {
        var library = services().gameService(TerrainLibrary.class);
        for (int dx = 0; dx <= RESUME_RUNWAY; dx += 8) {
            if (library.floorAt(worldX + dx) < 0) return false;
        }
        return true;
    }

    private void recycleTerrain(PlayableEntity player) {
        // The camera never scrolls back, so the window only ever moves forward.
        int delta = player.getCentreX() >= 8192 ? 4096 : 0;
        // Stock platforms keep their spawn coordinates and do not follow the shift, so the
        // window waits until none are loaded. The 16384px window leaves thousands of pixels
        // of slack; past the hard limit any still-loaded stones are dropped and re-spawned.
        var live = liveStones();
        // Spilled rings keep their local coordinates too; let them settle before shifting.
        boolean forced = player.getCentreX() >= FORCED_REBASE_X;
        if (delta != 0 && live.isEmpty() && spilledRings() && !forced) delta = 0;
        if (delta != 0 && !live.isEmpty()) {
            if (!forced) delta = 0;
            else {
                for (var stone : live) stone.setDestroyed(true);
                stones0 = stones1 = stones2 = stones3 = stones4 = stones5 = stones6 = stones7 = 0;
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
        visited = shiftSections(visited);
        rings0 = shiftSections(rings0);
        rings1 = shiftSections(rings1);
        rings2 = shiftSections(rings2);
        rings3 = shiftSections(rings3);
        stones0 = shiftSections(stones0);
        stones1 = shiftSections(stones1);
        stones2 = shiftSections(stones2);
        stones3 = shiftSections(stones3);
        stones4 = shiftSections(stones4);
        stones5 = shiftSections(stones5);
        stones6 = shiftSections(stones6);
        stones7 = shiftSections(stones7);
        monitors = shiftSections(monitors);
        hazards = shiftSections(hazards);
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
            spawnFreeChild(() -> new CourseHazard(spawn, hazard.worldX()));
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
            if ((stones0 & stones1 & stones2 & stones3 & stones4 & stones5 & stones6 & stones7 & bit) != 0) continue;
            var stones = PlatformPlan.at(library, origin / 2 + section);
            for (int i = 0; i < SECTION_STONES; i++) {
                long mask = switch (i) {
                    case 0 -> stones0; case 1 -> stones1; case 2 -> stones2; case 3 -> stones3; case 4 -> stones4;
                    case 5 -> stones5; case 6 -> stones6; default -> stones7;
                };
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
                switch (i) {
                    case 0 -> stones0 |= bit; case 1 -> stones1 |= bit; case 2 -> stones2 |= bit;
                    case 3 -> stones3 |= bit; case 4 -> stones4 |= bit; case 5 -> stones5 |= bit;
                    case 6 -> stones6 |= bit; default -> stones7 |= bit;
                }
            }
        }
    }

    /** Drops the 16 sections the forward shift leaves behind. */
    private static long shiftSections(long bits) {
        return bits >>> 8;
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
