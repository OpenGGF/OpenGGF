package com.openggf;

import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunHandle;
import com.openggf.game.run.RunHost;
import com.openggf.game.run.RunSpec;
import com.openggf.mods.scene.SceneGameplay;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Engine side of {@link SceneGameplay} for one open mod scene: validates a launch, begins the
 * hosted run with the owner's fault boundary around its host, and starts the session at the
 * next frame boundary. When the run ends the scene is resumed.
 */
final class SceneRunLauncher implements SceneGameplay {
    private final GameLoop gameLoop;
    private final UnaryOperator<RunHost> guard;
    private final List<String> games;
    private final Consumer<RunSpec> startSession;
    private final Consumer<RunEndReason> returnToScene;
    private final java.util.function.Function<String, Optional<String>> fingerprints;

    /**
     * @param guard         wraps the creator's host in the scene owner's fault boundary
     * @param startSession  opens the run's gameplay session (at a frame boundary, screen black)
     * @param returnToScene tears the session down and resumes the scene
     */
    SceneRunLauncher(GameLoop gameLoop, UnaryOperator<RunHost> guard, List<String> games,
            Consumer<RunSpec> startSession, Consumer<RunEndReason> returnToScene) {
        this(gameLoop, guard, games, startSession, returnToScene, gameId -> Optional.empty());
    }

    /** @param fingerprints the determinism fingerprint of a game's run, empty without its ROM */
    SceneRunLauncher(GameLoop gameLoop, UnaryOperator<RunHost> guard, List<String> games,
            Consumer<RunSpec> startSession, Consumer<RunEndReason> returnToScene,
            java.util.function.Function<String, Optional<String>> fingerprints) {
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
        this.gameLoop = Objects.requireNonNull(gameLoop, "gameLoop");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.games = List.copyOf(games);
        this.startSession = Objects.requireNonNull(startSession, "startSession");
        this.returnToScene = Objects.requireNonNull(returnToScene, "returnToScene");
    }

    @Override
    public List<String> availableGames() {
        return games;
    }

    @Override
    public Optional<String> determinismFingerprint(String gameId) {
        return games.contains(gameId) ? fingerprints.apply(gameId) : Optional.empty();
    }

    @Override
    public RunHandle launch(RunSpec spec, RunHost host) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(host, "host");
        if (!games.contains(spec.gameId())) {
            throw new IllegalArgumentException("Game is not available: " + spec.gameId());
        }
        if (!stockCharacters(spec.gameId()).contains(spec.character())) {
            throw new IllegalArgumentException(spec.character() + " is not a stock character of " + spec.gameId());
        }
        if (gameLoop.hostedRuns.isActive()) {
            throw new IllegalStateException("A run is already active");
        }
        RunHandle handle = gameLoop.beginHostedRun(spec, guard.apply(host), reason -> {
            if (reason == RunEndReason.LOAD_FAILED) {
                returnToScene.accept(reason); // the launch fade already left the screen black
            } else {
                gameLoop.fadeOutTo(() -> returnToScene.accept(reason));
            }
        });
        gameLoop.requestHostedRunLaunch(() -> startSession.accept(spec));
        return handle;
    }

    static List<String> stockCharacters(String gameId) {
        return switch (gameId) {
            case "s1" -> List.of("sonic");
            case "s2" -> List.of("sonic", "tails");
            case "s3k" -> List.of("sonic", "tails", "knuckles");
            default -> List.of();
        };
    }
}
