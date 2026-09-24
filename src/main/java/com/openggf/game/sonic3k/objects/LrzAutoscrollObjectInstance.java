package com.openggf.game.sonic3k.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.*;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.resources.PlcParser;
import com.openggf.physics.SwingMotion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Obj_LRZ3Autoscroll ($9E), including the native flash, missiles and target/debris graph. */
public final class LrzAutoscrollObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider, RomObjectCodePointerProvider {
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private LrzAutoscrollObjectInstance parent;
    private LrzAutoscrollPaletteFade fade;
    private int code = 0x78F50, timer, routine, subtype, dx, dy, savedX, savedY;
    private int art, priority, width, height, collision;
    private boolean visible, displayed, renderOnScreen, nativeStatus7, pendingDelete, swingDown, impact, flicker;
    private long flashArt = -1, debrisArt = -1, missileArt = -1;
    private transient S3kRawAnimation raw;

    public LrzAutoscrollObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZ3Autoscroll");
        motion.x = spawn.x();
        motion.y = spawn.y();
        subtype = spawn.subtype();
    }
    private LrzAutoscrollObjectInstance(LrzAutoscrollObjectInstance parent, int code, int subtype, int dx, int dy) {
        this(new ObjectSpawn(parent.getX() + dx, parent.getY() + dy, 0, subtype, 0, false, 0));
        this.parent = (code == 0x793E2 || code == 0x793A8) ? null : parent;
        this.code = code;
        this.dx = dx;
        this.dy = dy;
        art = parent.art;
    }
    @Override
    public LrzAutoscrollObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new LrzAutoscrollObjectInstance(context.spawn());
    }
    private LrzBossActState state() {
        return S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow().bossAct();
    }
    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (pendingDelete) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        flashArt = claim(flashArt);
        debrisArt = claim(debrisArt);
        missileArt = claim(missileArt);
        switch (code) {
            case 0x78F50 -> {
                code = 0x78F82;
                timer = 59;
                flashArt = queueArt(0x170F4E, 0x3AB);
                debrisArt = queueArt(0x1714C0, 0x487);
            }
            case 0x78F82 -> {
                lockPlayers(true);
                if (expired()) {
                    code = 0x78FAE;
                    spawnFreeChild(() -> new LrzAutoscrollObjectInstance(this, 0x793E2, 0, 0, 0));
                }
            }
            case 0x78FAE -> {
                if (!state().flashComplete())
                    return;
                code = 0x79002;
                lockPlayers(false);
                attributes(0x795D8);
                motion.x = 0xFFC0;
                motion.y = 0x460;
                motion.xVel = 0x100;
                motion.yVel = 0xC0;
                timer = 0x11F;
                table(0x7961A, false);
            }
            case 0x79002 -> {
                if (routine == 0)
                    siren(vIntRunCount);
                swing();
                SubpixelMotion.moveSprite2(motion);
                if (expired()) {
                    if (routine == 0) {
                        routine = 2;
                        state().publishMissilesReleased();
                        motion.xVel = 0;
                        timer = 0xBF;
                    } else {
                        code = 0x79072;
                        state().requestForegroundAdvance();
                        timer = 0;
                        motion.xVel = 0x200;
                        table(0x79652, true);
                    }
                }
                draw();
            }
            case 0x79072 -> {
                if (!renderOnScreen) {
                    goDelete();
                    return;
                }
                siren(vIntRunCount);
                swing();
                SubpixelMotion.moveSprite2(motion);
                timer = (timer + 1) & 65535;
                if (timer >= 0x1B0)
                    motion.y = (motion.y + 1) & 65535;
                draw();
            }
            case 0x790AC -> {
                attributes(0x795F0);
                code = 0x790D8;
                motion.x = word(0x790D0 + subtype);
                motion.y = 0x4C0;
            }
            case 0x790D8 -> {
                if (((services().camera().getX() + 0x120) & 65535) < (motion.x & 65535))
                    return;
                code = 0x7910A;
                services().playSfx(Sonic3kSfx.TARGETING.id);
                table(0x79672, true);
                table(0x79658, false);
            }
            case 0x7910A -> {
                if (!impact) {
                    cull(false);
                    return;
                }
                state().requestChunkEdit(getX() - 0x40, 0x480);
                services().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id);
                table(0x79686, true);
                goDelete();
            }
            case 0x79142 -> {
                attributes(0x795F6);
                code = 0x79192;
                savedX = motion.x;
                savedY = motion.y;
                animation.mappingFrame = byteAt(0x7917E + (subtype >>> 1));
                motion.xVel = (short) word(0x79182 + subtype * 2);
                motion.yVel = (short) word(0x79184 + subtype * 2);
                timer = 0xB;
            }
            case 0x79192 -> {
                SubpixelMotion.moveSprite2(motion);
                if (expired()) {
                    code = 0x791B0;
                    timer = 0xF;
                }
                drawChild();
            }
            case 0x791B0 -> {
                if (expired()) {
                    code = 0x79192;
                    motion.x = savedX;
                    motion.y = savedY;
                    timer = 0xB;
                }
                checkParent();
            }
            case 0x791D4 -> {
                attributes(0x795FC);
                code = 0x791FE;
                motion.y = word(0x791FA + subtype);
                table(0x79678, true);
            }
            case 0x791FE -> {
                motion.y = (motion.y + 4) & 65535;
                if ((motion.y & 65535) < ((parent.getY() - 0x10) & 65535)) {
                    draw();
                    return;
                }
                code = 0x79266;
                renderOnScreen = false;
                collision = 0x91;
                timer = 0x1F;
                motion.y = (motion.y + 8) & 65535;
                hitboxes();
                motion.y = (motion.y + 0x18) & 65535;
                spawnChild(() -> new LrzEndBossExplosion(this, 0));
                if (subtype != 0)
                    parent.impact = true;
            }
            case 0x79266 -> {
                if (expired())
                    goDelete();
            }
            case 0x79278 -> {
                attributes(0x795E4);
                code = 0x79290;
                S3kRawAnimation.set(animation, 0x796B5);
                flame();
            }
            case 0x79290 -> flame();
            case 0x792AC -> {
                attributes(0x795DE);
                code = 0x792F0;
                timer = word(0x792D0 + subtype);
                motion.xVel = (short) word(0x792E0 + subtype);
            }
            case 0x792F0 -> {
                follow();
                if (state().missilesReleased() && expired()) {
                    code = 0x79334;
                    timer = 0x14;
                }
                draw();
            }
            case 0x79334 -> {
                SubpixelMotion.moveSprite(motion, 0x20);
                if (expired()) {
                    code = 0x7935E;
                    services().playSfx(Sonic3kSfx.MISSILE_SHOOT.id);
                    table(0x7964C, true);
                }
                cull(true);
            }
            case 0x7935E -> {
                motion.yVel = (short) (motion.yVel - 0x40);
                SubpixelMotion.moveSprite2(motion);
                if (motion.yVel < 0 && (vIntRunCount & 3) == 0)
                    table(0x7967E, false);
                cull(true);
            }
            case 0x79374 -> {
                attributes(0x795E4);
                code = 0x7938C;
                S3kRawAnimation.set(animation, 0x79694);
                rocketFlame();
            }
            case 0x7938C -> rocketFlame();
            case 0x793A8 -> {
                attributes(0x795EA);
                code = 0x793C6;
                motion.yVel = 0x100;
                trail();
            }
            case 0x793C6 -> trail();
            case 0x793E2 -> {
                art = 1;
                attributes(0x79608);
                code = 0x79406;
                motion.x = 0xB0;
                motion.y = 0x449;
                flash();
            }
            case 0x79406 -> flash();
            case 0x79486 -> {
                if (fade == null || !fade.finished())
                    return;
                state().setPaletteMode(1);
                state().publishFlashComplete();
                state().requestForegroundAdvance();
                services().playSfx(Sonic3kSfx.SUPER_EMERALD.id);
                spawnFreeChild(() -> new LrzAutoscrollPaletteFade(true));
                spawnFreeChild(
                        () -> new CollapsingBridgeObjectInstance(new ObjectSpawn(0x60, 0x4D0, 0x0F, 0, 0, false, 0)));
                ObjectLifetimeOps.expireDynamic(this);
            }
            case 0x794DE -> {
                art = 2;
                attributes(0x79614);
                code = 0x85102;
                animation.mappingFrame = byteAt(0x79554 + (subtype >>> 1));
                motion.x = parent.getX() + (byte) byteAt(0x79566 + subtype);
                motion.y = parent.getY() + (byte) byteAt(0x79567 + subtype);
                motion.xVel = (short) word(0x7958A + subtype * 2);
                motion.yVel = (short) word(0x7958C + subtype * 2);
                parent = null;
                draw();
            }
            case 0x85102 -> { // Obj_FlickerMove: move, range check, then toggle the display bit.
                SubpixelMotion.moveSprite(motion, 0x38);
                cull(true);
                flicker = !flicker;
                if (flicker)
                    visible = false;
            }
            default -> throw new IllegalStateException("LRZ autoscroll code " + Integer.toHexString(code));
        }
    }
    private void flash() {
        raw().animateNoSst(animation, 0x7968C, this::startFlashFade);
        draw();
    }
    private void startFlashFade() {
        code = 0x79486;
        state().setPaletteMode(0x80);
        timer = 0xF;
        services().playSfx(Sonic3kSfx.SUPER_TRANSFORM.id);
        var registry = services().paletteOwnershipRegistryOrNull();
        if (registry != null) {
            S3kPaletteWriteSupport.resolvePendingWritesNow(
                    registry, services().currentLevel(), services().graphicsManager());
            registry.applyTargetPatch(
                    "lrz.autoscroll.target", 0, 0, LrzAutoscrollPaletteFade.normalLine(services(), 0));
            for (int line = 1; line < 4; line++)
                registry.applyTargetPatch("lrz.autoscroll.target", line, 0, bytes(0x79726 + (line - 1) * 32, 32));
        }
        fade = spawnFreeChild(LrzAutoscrollPaletteFade::new);
        missileArt = queueArt(0x17093C, 0x424);
        loadExplosionPlc();
    }
    private void flame() {
        motion.x = parent.getX();
        motion.y = parent.getY();
        raw().animateNoSst(animation, 0x796B5, this::goDelete);
        drawChild();
    }
    private void rocketFlame() {
        motion.x = parent.getX();
        motion.y = parent.getY();
        raw().animateMultiDelay(animation, this::goDelete);
        drawChild();
    }
    private void trail() {
        raw().animateNoSstMultiDelayFlipX(animation, 0x796B9, this::goDelete, () -> {});
        motion.yVel = (short) (motion.yVel - 0x10);
        SubpixelMotion.moveSprite2(motion);
        draw();
    }
    private void table(int address, boolean repeated) {
        int count = word(address) + 1;
        for (int i = 0; i < count; i++) {
            int row = address + 2 + (repeated ? 0 : i * 6);
            int childCode = longAt(row), childSubtype = i * 2;
            int childDx = repeated ? 0 : (byte) byteAt(row + 4), childDy = repeated ? 0 : (byte) byteAt(row + 5);
            var child =
                    spawnChild(() -> new LrzAutoscrollObjectInstance(this, childCode, childSubtype, childDx, childDy));
            if (child == null || child.isDestroyed() || child.getSlotIndex() < 0)
                break;
        }
    }
    private void hitboxes() {
        int count = word(0x690D8) + 1;
        for (int i = 0; i < count; i++) {
            int row = 0x690DA + i * 6, sub = i * 2;
            int x = getX() + (byte) byteAt(row + 4), y = getY() + (byte) byteAt(row + 5);
            // Existing port of the same BossExplosionHitbox routine, including its subtype delay.
            var child = spawnChild(() -> new AizMinibossNapalmExplosionChild(x, y, sub, true));
            if (child == null || child.isDestroyed() || child.getSlotIndex() < 0)
                break;
        }
    }
    private void attributes(int address) {
        priority = word(address) / 0x80;
        width = byteAt(address + 2);
        height = byteAt(address + 3);
        animation.mappingFrame = byteAt(address + 4);
        collision = byteAt(address + 5);
    }
    private void follow() {
        motion.x = parent.getX() + dx;
        motion.y = parent.getY() + dy;
    }
    private void swing() {
        var result = SwingMotion.update(0x10, motion.yVel, 0xC0, swingDown);
        motion.yVel = result.velocity();
        swingDown = result.directionDown();
    }
    private boolean expired() {
        timer = (short) (timer - 1);
        return timer < 0;
    }
    private void siren(int vIntRunCount) {
        if ((vIntRunCount & 15) == 0)
            services().playSfx(Sonic3kSfx.ROBOTNIK_SIREN.id);
    }
    private void lockPlayers(boolean locked) {
        for (var player : services().playerQuery().playersFor(
                     ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED))
            if (player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
                sprite.setControlLocked(locked);
                if (locked)
                    sprite.clearLogicalInputState();
            }
    }
    private void goDelete() {
        nativeStatus7 = true;
        pendingDelete = true;
        visible = false;
        collision = 0;
    }
    private void draw() {
        if (!pendingDelete) {
            visible = true;
            displayed = true;
        }
    }
    private void checkParent() {
        if (parent == null || parent.nativeStatus7 || parent.isDestroyed())
            goDelete();
    }
    private void drawChild() {
        checkParent();
        draw();
    }
    private void cull(boolean vertical) {
        int back = (services().camera().getX() - 0x80) & 0xFF80;
        if (((((getX() & 0xFF80) - back) & 65535) > 0x280)
                || (vertical && ((getY() - services().camera().getY() + 0x80) & 65535) > 0x200))
            goDelete();
        else
            draw();
    }
    private byte[] bytes(int address, int length) {
        try {
            return services().rom().readBytes(address, length);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
    private int byteAt(int address) {
        return bytes(address, 1)[0] & 255;
    }
    private int word(int address) {
        byte[] b = bytes(address, 2);
        return ((b[0] & 255) << 8) | (b[1] & 255);
    }
    private int longAt(int address) {
        return (word(address) << 16) | word(address + 2);
    }
    private S3kRawAnimation raw() {
        if (raw == null)
            try {
                raw = S3kRawAnimation.load(RomByteReader.fromRom(services().rom()), 0x7968C, 0x3C);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        return raw;
    }
    private long queueArt(int address, int tile) {
        try {
            var queue = services().kosinskiModuleQueue();
            if (queue == null)
                return -1;
            Sonic3kPlcLoader.bindRuntimePatternDmaTarget(queue, services());
            queue.enqueue(services().rom(), address, tile * 32);
            return com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.from(services())
                    .moduleQueue()
                    .queue(services().rom(), address, tile)
                    .ordinal();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
    private long claim(long ordinal) {
        if (ordinal < 0)
            return ordinal;
        var queue = com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.from(services()).moduleQueue();
        var handle = services()
                             .hardwareTiming()
                             .pendingHandle(com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE, ordinal)
                             .orElseThrow();
        if (!queue.isReady(handle))
            return ordinal;
        queue.claim(handle);
        return -1;
    }
    private void loadExplosionPlc() {
        var entries = new ArrayList<PlcParser.PlcEntry>();
        int count = (short) word(0x83D64) + 1;
        for (int i = 0; i < count; i++)
            entries.add(new PlcParser.PlcEntry(longAt(0x83D66 + 6 * i) & 0xFFFFFF, word(0x83D6A + 6 * i) / 32));
        if (services().currentLevel() instanceof Sonic3kLevel level)
            try {
                var changed = Sonic3kPlcLoader.applyToLevel(
                        new PlcParser.PlcDefinition(-1, entries), level, services().rom());
                Sonic3kPlcLoader.refreshAffectedRenderers(changed, services().levelManager());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
    }
    @Override
    public void refreshPostCameraRenderState() {
        if (displayed) {
            displayed = false;
            renderOnScreen = isWithinRenderSpriteBounds(width, height);
        }
    }
    @Override
    public int getX() {
        return (short) motion.x;
    }
    @Override
    public int getY() {
        return (short) motion.y;
    }
    @Override
    public int getPriorityBucket() {
        return priority;
    }
    @Override
    public boolean isHighPriority() {
        return art == 2;
    }
    @Override
    public int getOnScreenHalfWidth() {
        return width;
    }
    @Override
    public int getOnScreenHalfHeight() {
        return height;
    }
    @Override
    public int getCollisionFlags() {
        return collision;
    }
    @Override
    public int getCollisionProperty() {
        return 0;
    }
    @Override
    public boolean isPersistent() {
        return true;
    }
    @Override
    public int romObjectCodePointerHighWord() {
        return code >>> 16;
    }
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible)
            return;
        String key = switch (art) {
            case 1 -> Sonic3kObjectArtKeys.LRZ3_DEATH_EGG_FLASH;
            case 2 -> Sonic3kObjectArtKeys.LRZ3_PLATFORM_DEBRIS;
            default -> Sonic3kObjectArtKeys.LRZ3_AUTOSCROLL;
        };
        var renderer = getRenderer(key);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), false, false);
    }
}
