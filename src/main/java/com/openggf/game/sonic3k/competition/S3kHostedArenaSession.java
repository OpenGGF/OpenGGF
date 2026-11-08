package com.openggf.game.sonic3k.competition;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.arena.ArenaStep;
import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.game.arena.HostedArenaSession;
import com.openggf.game.arena.ArenaPose;
import com.openggf.game.arena.RaceClock;
import com.openggf.game.arena.TrackGeometry;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Solo Match Race adapter; all gameplay and lap timing remain with the native owners. */
public final class S3kHostedArenaSession implements HostedArenaSession {
    private final S3kCompetitionNativeHost host;
    private final Runnable closeViews;
    private boolean closed;
    private boolean respawned;
    private final S3kArenaSwapApplier raceSwaps;

    public S3kHostedArenaSession(S3kCompetitionNativeHost host, HostedArenaRequest request, Runnable closeViews) {
        this.host = Objects.requireNonNull(host);
        this.closeViews = Objects.requireNonNull(closeViews);
        if (request.arenaIndex() >= S3kCompetitionSession.GRAND_PRIX_ORDER.size())
            throw new IllegalArgumentException("Unknown arena menu index");
        var character = S3kCompetitionPlayer.Character.valueOf(request.character().toUpperCase(Locale.ROOT));
        host.openArena(new S3kCompetitionScreen.Launch(new S3kCompetitionSession.Config(
                S3kCompetitionSession.Mode.MATCH_RACE,
                S3kCompetitionSession.GRAND_PRIX_ORDER.get(request.arenaIndex()), request.itemsEnabled(), 1),
                List.of(character)));
        raceSwaps = new S3kArenaSwapApplier(host,this::geometry);
        host.session().setHostedSwapRequest(raceSwaps::requestNative);
        host.world().context().getRewindRegistry().register(raceSwaps);
    }

    @Override public ArenaStep step(PlayerInputState input) {
        if (closed) throw new IllegalStateException("Hosted arena has closed");
        Objects.requireNonNull(input);
        // Start belongs to the run host, including on replay. P2 remains an empty native slot.
        var masked = PlayerInputState.of(input.heldMask(), input.pressedMask(),
                input.actionHeldMask(), input.actionPressedMask(), false, false);
        raceSwaps.executeQueued(host.world().context());
        boolean dead = host.session().player(0).routine() == 6;
        host.stepArena(LogicalInputSnapshot.ofPlayers(masked, PlayerInputState.neutral()), host.session());
        respawned = dead && host.session().player(0).routine() != 6;
        return observation();
    }

    ArenaStep observation() {
        var session = host.session();
        var player = session.player(0);
        int shoes = (player.statusSecondary() & 4) == 0 ? 0 : player.speeds().maxSpeed() == 0x300 ? 2 : 1;
        var pose = new ArenaPose(player.centreX(), player.centreY(), player.mappingFrame(),
                (player.nativeRenderFlags() & 1) != 0, (player.nativeRenderFlags() & 2) != 0,
                2, player.highPriority(), player.routine() == 4, player.topSolidBit() == 0xE, shoes);
        // Raw lap falls on backwards crossings/death. Only highestLap books a completed lap.
        var clock = new RaceClock(session.timerRunning(0), session.lap(0), session.highestLap(0),
                session.timeOver(0) ? RaceClock.Failure.TIME_OVER : RaceClock.Failure.NONE);
        return new ArenaStep(pose, clock);
    }

    @Override public TrackGeometry geometry() {
        var profile = host.scroll().profile();
        return new TrackGeometry(profile.xWrapMask() + 1,
                host.session().arena() == S3kCompetitionSession.Arena.CHROME_GADGET ? profile.yWrapMask() + 1 : 0, lapLineX(), S3kCompetitionSession.TOTAL_LAPS);
    }
    private int lapLineX() {
        return host.world().context().getObjectManager().getActiveObjects().stream()
                .filter(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::isInstance)
                .findFirst().orElseThrow(() -> new IllegalStateException("Arena has no native goal")).getX();
    }
    @Override public AbstractPlayableSprite localPlayer() { return host.player(0); }
    @Override public boolean trackFinished() {
        return host.session().finished(0) && host.session().lap(0) > S3kCompetitionSession.TOTAL_LAPS;
    }
    @Override public boolean respawned() { return respawned; }
    @Override public boolean resultsRequested() { return host.session().resultsRequested(); }
    @Override public com.openggf.game.arena.GhostArtSource ghostArtSource() {
        return new com.openggf.game.arena.GhostArtSource() {
            @Override public com.openggf.sprites.art.SpriteArtSet art(String character) {
                var sheet = host.assets().miniature(S3kCompetitionPlayer.Character.valueOf(character.toUpperCase(Locale.ROOT)));
                var frames = java.util.stream.IntStream.range(0,sheet.getFrameCount()).mapToObj(sheet::getFrame).toList();
                // Assets already resolved ROM DPLCs into absolute mapping tile offsets.
                // Populate the private static bank once; empty later plans retain those tiles.
                var initial = new com.openggf.level.render.SpriteDplcFrame(List.of(
                        new com.openggf.level.render.TileLoadRequest(0,sheet.getPatterns().length,0)));
                var retained = new com.openggf.level.render.SpriteDplcFrame(List.of());
                var plans = java.util.stream.IntStream.range(0,frames.size())
                        .mapToObj(frame -> frame == 0 ? initial : retained).toList();
                return new com.openggf.sprites.art.SpriteArtSet(sheet.getPatterns(),frames,plans,
                        "knuckles".equals(character) ? 1 : 0,0,0,sheet.getPatterns().length,null,null);
            }
            @Override public com.openggf.level.Palette palette(String character) {
                // The miniatures share the native competition player palette line.
                try { return host.assets().arenaPalette(host.session().arena())["knuckles".equals(character) ? 1 : 0]; }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
            @Override public int cameraX() {
                return projectedOrigin(host.session().player(0).centreX(),host.scroll().view(0).camera().x(),
                        host.scroll().profile().xWrapMask());
            }
            @Override public int cameraY() {
                return projectedOrigin(host.session().player(0).centreY(),S3kSoloArenaViews.displayY(host.scroll()),
                        host.scroll().profile().yWrapMask());
            }
        };
    }
    private static int projectedOrigin(int player,int camera,int mask) {
        // Select the same image as S3kCompetitionSpritePages' miniature source (12px half extent).
        // The mod places ghosts nearest P1; native camera words can occupy another loop image.
        int screen = ((player-camera+12)&mask)-12;
        return player-screen;
    }
    @Override public com.openggf.game.arena.RaceSwapApplier raceSwaps() { return raceSwaps; }
    @Override public void draw() { host.drawArena(); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        try { host.closeArena(); } finally { closeViews.run(); }
    }
}
