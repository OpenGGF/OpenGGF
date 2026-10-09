package practice;

import com.openggf.game.run.GhostPose;
import com.openggf.game.run.PlayerPose;
import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunHandle;
import com.openggf.game.run.RunHost;
import com.openggf.game.run.RunInput;
import com.openggf.game.run.RunLevelStart;
import com.openggf.game.run.RunStep;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneStorage;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.LevelOverlayCanvas;

import java.util.ArrayList;
import java.util.List;

/**
 * Times the act from the first input to the completion signal, records the player's pose every
 * step, and replays the previous attempt as a ghost. The best time per game is kept in storage.
 */
public final class PracticeHost implements RunHost {
    private RunHandle handle;
    private SceneStorage storage;
    private String game = "";
    private List<PlayerPose> previous = List.of();
    private final List<PlayerPose> current = new ArrayList<>();
    private int firstInputStep = -1;
    private int finishStep = -1;
    private int lastTimeFrames = -1;
    private int bestFrames = -1;

    void prepare(String game, SceneStorage storage) {
        this.game = game;
        this.storage = storage;
        bestFrames = storage.read(bestFile()).map(PracticeHost::parse).orElse(-1);
    }

    void attach(RunHandle handle) {
        this.handle = handle;
    }

    @Override
    public void onLevelReady(RunLevelStart start) {
        if (!current.isEmpty()) {
            previous = List.copyOf(current);
        }
        current.clear();
        firstInputStep = -1;
        finishStep = -1;
    }

    @Override
    public boolean admitStep(RunInput input) {
        if (handle != null && input.keyPressed(SceneKeys.R)) {
            handle.retry();
        }
        return true;
    }

    @Override
    public void afterStep(RunStep step) {
        if (finishStep >= 0) {
            return;
        }
        current.add(step.player());
        int index = current.size() - 1;
        if (firstInputStep < 0 && step.heldMask() != 0) {
            firstInputStep = index;
        }
        if (step.actComplete() && firstInputStep >= 0) {
            finishStep = index;
            lastTimeFrames = finishStep - firstInputStep;
            if (bestFrames < 0 || lastTimeFrames < bestFrames) {
                bestFrames = lastTimeFrames;
                if (storage != null) {
                    storage.write(bestFile(), Integer.toString(bestFrames));
                }
            }
        }
    }

    @Override
    public List<GhostPose> ghosts() {
        if (previous.isEmpty() || current.isEmpty()) {
            return List.of();
        }
        PlayerPose pose = previous.get(Math.min(current.size() - 1, previous.size() - 1));
        return List.of(new GhostPose("previous", "sonic", pose, "LAST", 1f));
    }

    @Override
    public void drawOverlay(LevelOverlayCanvas canvas) {
        int frames = finishStep >= 0 ? lastTimeFrames
                : firstInputStep >= 0 ? current.size() - 1 - firstInputStep : 0;
        String line = format(frames) + (bestFrames >= 0 ? "  BEST " + format(bestFrames) : "");
        CompactFont.shadowed(canvas, line, canvas.width() - 4 - CompactFont.width(line, 1), 4, 1,
                0xFFFFFFFF, 0xC0000000);
    }

    @Override
    public void onRunEnded(RunEndReason reason) {
        handle = null;
    }

    int lastTimeFrames() {
        return lastTimeFrames;
    }

    int bestFrames() {
        return bestFrames;
    }

    private String bestFile() {
        return "best-" + game + ".txt";
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** Frames at 60 Hz as m'ss"cc. */
    static String format(int frames) {
        if (frames < 0) {
            return "--'--\"--";
        }
        int centis = frames * 100 / 60;
        return "%d'%02d\"%02d".formatted(centis / 6000, centis / 100 % 60, centis % 100);
    }
}
