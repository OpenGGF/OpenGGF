package hardened;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * Authored, stationary MHZ spore sentry. This is a mod object, not a patch of stock
 * Mushmeanie timers. ObjDat_Mushmeanie supplies resident frames 1..3, palette 1
 * and priority $280; the authored tell/aim/volleys run on admitted object ticks.
 */
public final class Sentry extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider {
    /** Resident SKL Mushmeanie key; S3KL object $8D is a different species. */
    public static final String ART_KEY = "mhz_mushmeanie";
    public static final String KEY = "hardened-s3k:spore-sentry";
    private EncounterState.Phase phase = EncounterState.Phase.WAITING;
    private int phaseTick;
    private int aimX;
    private int aimY;
    private int volleys;
    private boolean cueReady;

    /** Spawn-only construction is also the rewind recreation path. */
    public Sentry(ObjectSpawn spawn) { super(spawn, "Spore sentry"); }

    public static ObjectSpawn spawnAt(int x, int y) {
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, "hardened-s3k", KEY);
    }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new Sentry(context.spawn());
    }
    public EncounterState.Phase phase() { return phase; }
    public int phaseTick() { return phaseTick; }
    public int aimX() { return aimX; }
    public int aimY() { return aimY; }
    public int volleys() { return volleys; }

    private EncounterState encounter() { return services().gameService(EncounterState.class); }
    private boolean artReady() {
        var renderer = getRenderer(ART_KEY);
        var manager = services().renderManager();
        var sheet = manager == null ? null : manager.getSheet(ART_KEY);
        return renderer != null && sheet != null && sheet.getFrameCount() == 4;
    }
    private boolean fullyVisible() {
        var camera = services().camera();
        return getX() - 16 >= camera.getX() && getX() + 16 < camera.getX() + camera.getWidth()
                && getY() - 24 >= camera.getY() && getY() + 16 < camera.getY() + camera.getHeight();
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var state = encounter();
        if (state == null || state.failed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        if (!state.active()) return;
        // A recreated layout entry shares the run's ledger. Re-entry cannot
        // allocate a third volley or erase a still-required recovery period.
        volleys = Math.max(volleys, state.volleys());
        if (!artReady() || !fullyVisible()) {
            // No harmful offscreen spawn. Re-entry repeats the complete tell, never a
            // frozen late volley; existing shots independently expire offscreen.
            phase = EncounterState.Phase.WAITING;
            phaseTick = 0;
            cueReady = false;
            state.publish(phase, phaseTick, aimX, aimY, volleys);
            return;
        }
        cueReady = true;
        if (phase == EncounterState.Phase.WAITING) {
            if (player == null || player.getDead() || (player.getCentreX() & 0xffff) < EncounterPlan.TRIGGER_X) {
                state.publish(phase, phaseTick, aimX, aimY, volleys);
                return;
            }
            phase = EncounterState.Phase.TELL;
            phaseTick = 0;
            if (phase == EncounterState.Phase.TELL) {
                aimX = player.getCentreX() & 0xffff; aimY = player.getCentreY() & 0xffff;
                services().playSfx(0x53);
            } // S3K sfx_Charging.
        } else {
            phaseTick++;
            switch (phase) {
                case TELL -> {
                    if (player != null) {
                        aimX = player.getCentreX() & 0xffff;
                        aimY = player.getCentreY() & 0xffff;
                    }
                    if (phaseTick >= EncounterPlan.TELL_TICKS) enter(EncounterState.Phase.LOCKED);
                }
                case LOCKED -> {
                    // The marker stops following Sonic at the end of TELL. Both
                    // volleys use these same coordinates, even if Sonic moves away.
                    if (phaseTick >= EncounterPlan.LOCK_TICKS) {
                        if (volleys == EncounterPlan.VOLLEY_COUNT) enter(EncounterState.Phase.RECOVERY);
                        else if (fireVolley()) enter(volleys == EncounterPlan.VOLLEY_COUNT
                                ? EncounterState.Phase.VOLLEY_TWO : EncounterState.Phase.VOLLEY_ONE);
                    }
                }
                case VOLLEY_ONE -> {
                    if (phaseTick >= EncounterPlan.VOLLEY_GAP_TICKS) {
                        if (fireVolley()) enter(EncounterState.Phase.VOLLEY_TWO);
                    }
                }
                case VOLLEY_TWO -> {
                    if (phaseTick >= EncounterPlan.VOLLEY_GAP_TICKS) enter(EncounterState.Phase.RECOVERY);
                }
                case RECOVERY -> {
                    if (phaseTick >= EncounterPlan.RECOVERY_TICKS) enter(EncounterState.Phase.RESTING);
                }
                default -> { }
            }
        }
        state.publish(phase, phaseTick, aimX, aimY, volleys);
    }

    private void enter(EncounterState.Phase next) { phase = next; phaseTick = 0; }
    private boolean fireVolley() {
        var manager = services().objectManager();
        long live = manager.getActiveObjects().stream()
                .filter(o -> o instanceof Spore && !o.isDestroyed()).count();
        if (live + 2 > EncounterPlan.PROJECTILE_CAP) {
            encounter().abort("Too many spores; retry the encounter");
            return false;
        }
        var first = spawnChild(() -> Spore.aimed(getX(), getY() - 12, aimX, aimY - 10));
        var second = !allocated(first) ? null
                : spawnChild(() -> Spore.aimed(getX(), getY() - 12, aimX, aimY + 10));
        if (!allocated(second)) {
            if (first != null) ObjectLifetimeOps.expireDynamic(first);
            encounter().abort("The ambush could not start safely; retry");
            return false;
        }
        volleys++;
        services().playSfx(0x4d); // S3K sfx_Projectile, through the current ROM audio owner.
        return true;
    }

    private static boolean allocated(Spore shot) {
        return shot != null && shot.getSlotIndex() >= 0 && !shot.isDestroyed();
    }

    /** Native hurt handling keeps ordinary ring spill, shields and invulnerability. */
    @Override public int getCollisionFlags() {
        var state = encounter();
        return !isDestroyed() && state != null && state.active() && cueReady && artReady() && fullyVisible()
                && (phase == EncounterState.Phase.LOCKED || phase == EncounterState.Phase.VOLLEY_ONE
                || phase == EncounterState.Phase.VOLLEY_TWO) ? 0x8b : 0;
    }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getPriorityBucket() { return 5; } // ObjDat_Mushmeanie priority $280 / $80.
    @Override public int getOnScreenHalfWidth() { return 16; }
    @Override public int getOnScreenHalfHeight() { return 24; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var renderer = getRenderer(ART_KEY);
        if (renderer == null) return;
        boolean telling = phase == EncounterState.Phase.TELL || phase == EncounterState.Phase.LOCKED;
        int frame = telling ? 1 + phaseTick / 6 % 3 : phase == EncounterState.Phase.RECOVERY ? 2 : 1;
        renderer.drawFrameIndex(frame, getX(), getY(), aimX < getX(), false);
        renderer.drawFrameIndex(0, getX(), getY(), aimX < getX(), false, 2);
        // An always-pointed crown distinguishes this hazardous authored variant from
        // native Mushmeanie. The pulsing size makes its wind-up visible without audio.
        int crown = telling ? 8 + phaseTick / 6 % 2 * 3 : 8;
        pointedCue(getX(), getY() - 18, crown, 0xffdda0);
        if (telling) {
            pointedCue(aimX, aimY, phase == EncounterState.Phase.LOCKED ? 12 : 8, 0xffe6aa);
        }
    }

    /** Four pointed arms around ROM art, using the engine's ordinary primitive queue. */
    void pointedCue(int x, int y, int radius, int rgb) {
        drawPointedCue(services(), x, y, radius, rgb);
    }
    static void drawPointedCue(com.openggf.level.objects.ObjectServices services,
                               int x, int y, int radius, int rgb) {
        for (int arm = 0; arm < 4; arm++) {
            for (int step = 0; step < 4; step++) {
                int width = 4 - step;
                int offset = radius - 3 + step;
                int px = x + (arm == 0 ? offset : arm == 1 ? -offset : -width / 2);
                int py = y + (arm == 2 ? offset : arm == 3 ? -offset : -width / 2);
                int w = arm < 2 ? 1 : width, h = arm < 2 ? width : 1;
                services.graphicsManager().registerCommand(new GLCommand(GLCommand.CommandType.RECTI, 0,
                        (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f,
                        px, py, px + w, py + h));
            }
        }
    }
}
