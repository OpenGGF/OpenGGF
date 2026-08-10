package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PowerUpObject;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Exact aggregate owner for the four consecutive ROM Hyper-star slots. */
public final class HyperSonicStarsObjectInstance extends AbstractObjectInstance
        implements PowerUpObject, RewindRecreatable {
    private AbstractPlayableSprite owner;

    private record RewindExtra(PlayerRefId ownerId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    // Each child retains the ROM angle, init delay/frame timer, mapping frame and
    // $30/$34 word accumulators independently.
    private int delay0 = 1, delay1 = 2, delay2 = 3, delay3 = 4;
    private int angle0, angle1 = 0x40, angle2 = 0x80, angle3 = 0xC0;
    private int timer0, timer1, timer2, timer3;
    private int frame0 = 6, frame1 = 6, frame2 = 6, frame3 = 6;
    private int xAcc0, xAcc1, xAcc2, xAcc3;
    private int yAcc0, yAcc1, yAcc2, yAcc3;
    private int x0, x1, x2, x3;
    private int y0, y1, y2, y3;

    // Four native Obj_LightningShield_Spark states.
    private boolean sparkTriggerPending;
    private boolean sparksActive;
    private int sparkAnimTimer;
    private int sparkFrame;
    private int sx0, sx1, sx2, sx3;
    private int sy0, sy1, sy2, sy3;
    private int syv0 = -0x200, syv1 = -0x200, syv2 = 0x200, syv3 = 0x200;

    public HyperSonicStarsObjectInstance(AbstractPlayableSprite owner) {
        super(new ObjectSpawn(owner.getCentreX(), owner.getCentreY(), 0, 0, 0, false, 0),
                "HyperSonicStars");
        this.owner = owner;
    }

    private HyperSonicStarsObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HyperSonicStars");
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return context == null ? null : new HyperSonicStarsObjectInstance(context.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        PlayerRefId ownerId = context.identityTable()
                .map(table -> table.encodePlayer(owner)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(ownerId));
    }

    @Override
    public void restoreRewindState(
            PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra
                && extra.ownerId() != null) {
            owner = (AbstractPlayableSprite) context.requireIdentityTable()
                    .resolvePlayer(extra.ownerId(), true);
        }
    }

    public void triggerDashSparks() {
        sparkTriggerPending = true;
    }

    private void startDashSparks() {
        sparksActive = true;
        sparkAnimTimer = 3;
        sparkFrame = 3;
        sx0 = sx1 = sx2 = sx3 = owner.getCentreX() << 8;
        sy0 = sy1 = sy2 = sy3 = owner.getCentreY() << 8;
        syv0 = syv1 = -0x200;
        syv2 = syv3 = 0x200;
    }

    public boolean isBoundTo(AbstractPlayableSprite player) { return owner == player; }

    @Override
    public void update(int vIntRunCount, PlayableEntity ignored) {
        if (owner.getSuperStateController() == null
                || !owner.getSuperStateController().isHyperFormActive()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        // Obj_HyperSonic_Stars queues its Kosinski module before its child
        // slots begin waiting on Kos_modules_left. Request the ROM art from the
        // update owner rather than depending on a render pass to initiate it.
        boolean artReady = renderer(true) != null;
        for (int child = 0; child < 4; child++) updateChild(child, artReady);
        updateSparks();
    }

    private void updateChild(int child, boolean artReady) {
        int delay = delay(child);
        if (!artReady) return; // Kos_modules_left gate
        if (delay > 0) {
            setDelay(child, delay - 1);
            if (delay > 1) return;
        }
        int timer = timer(child) - 1;
        setTimer(child, timer);
        if (timer < 0) {
            setTimer(child, 1);
            int frame = frame(child) + 1;
            if (frame >= 3) {
                frame = 0;
                setXAcc(child, 0);
                setYAcc(child, 0);
            }
            setFrame(child, frame);
        }
        int angle = angle(child);
        setAngle(child, (angle - 0x10) & 0xFF);
        // GetSineCosine returns d0=sine and d1=cosine. loc_1941C writes
        // those values to x_vel and y_vel respectively.
        int xVelocity = TrigLookupTable.sinHex(angle) << 3;
        int yVelocity = TrigLookupTable.cosHex(angle) << 3;
        setXAcc(child, (short) (xAcc(child) + xVelocity));
        setYAcc(child, (short) (yAcc(child) + yVelocity));
        // The 68000 stores the word accumulator big-endian, then move.b $30
        // reads its high byte as the signed screen-space displacement.
        int dx = (byte) (xAcc(child) >> 8);
        if (owner.getDirection() == Direction.LEFT) dx = -dx;
        setX(child, owner.getCentreX() + dx);
        setY(child, owner.getCentreY() + (byte) (yAcc(child) >> 8));
    }

    private void updateSparks() {
        if (sparkTriggerPending) {
            sparkTriggerPending = false;
            startDashSparks();
        }
        if (!sparksActive) return;
        sx0 -= 0x200; sx1 += 0x200; sx2 -= 0x200; sx3 += 0x200;
        sy0 += syv0; sy1 += syv1; sy2 += syv2; sy3 += syv3;
        syv0 += 0x18; syv1 += 0x18; syv2 += 0x18; syv3 += 0x18;
        if (--sparkAnimTimer < 0) {
            sparkAnimTimer = 3;
            sparkFrame++;
            if (sparkFrame > 5) sparksActive = false;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = renderer(true);
        if (renderer == null) return;
        for (int child = 0; child < 4; child++) {
            if (delay(child) == 0 && frame(child) < 3) {
                renderer.drawFrameIndex(frame(child), x(child), y(child), false, false);
            }
        }
        if (sparksActive) {
            renderer.drawFrameIndex(sparkFrame, sx0 >> 8, sy0 >> 8, false, false);
            renderer.drawFrameIndex(sparkFrame, sx1 >> 8, sy1 >> 8, false, false);
            renderer.drawFrameIndex(sparkFrame, sx2 >> 8, sy2 >> 8, false, false);
            renderer.drawFrameIndex(sparkFrame, sx3 >> 8, sy3 >> 8, false, false);
        }
    }

    private PatternSpriteRenderer renderer(boolean ensure) {
        if (getRenderManager() == null) return null;
        if (ensure && getRenderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.HYPER_SONIC_STARS);
        }
        return getRenderer(Sonic3kObjectArtKeys.HYPER_SONIC_STARS);
    }

    @Override public boolean isHighPriority() { return owner.isHighPriority(); }
    @Override public int getPriorityBucket() { return RenderPriority.clamp(owner.getPriorityBucket()); }
    // Obj_HyperSonic_Stars occupies the fixed Invincibility_stars slots and has
    // no out_of_range tail. Its explicit Hyper-flag check above owns expiry.
    @Override public boolean isPersistent() { return true; }
    @Override public void destroy() { setDestroyed(true); }
    @Override public void setVisible(boolean visible) { }
    @Override public boolean isInvincibilityStars() { return true; }
    @Override public PlayableEntity boundPlayer() { return owner; }
    int orbitingStarCount() { return 4; }
    int visibleSparkCount() { return sparksActive ? 4 : 0; }

    private int delay(int i){return switch(i){case 0->delay0;case 1->delay1;case 2->delay2;default->delay3;};}
    private void setDelay(int i,int v){switch(i){case 0->delay0=v;case 1->delay1=v;case 2->delay2=v;default->delay3=v;}}
    private int angle(int i){return switch(i){case 0->angle0;case 1->angle1;case 2->angle2;default->angle3;};}
    private void setAngle(int i,int v){switch(i){case 0->angle0=v;case 1->angle1=v;case 2->angle2=v;default->angle3=v;}}
    private int timer(int i){return switch(i){case 0->timer0;case 1->timer1;case 2->timer2;default->timer3;};}
    private void setTimer(int i,int v){switch(i){case 0->timer0=v;case 1->timer1=v;case 2->timer2=v;default->timer3=v;}}
    private int frame(int i){return switch(i){case 0->frame0;case 1->frame1;case 2->frame2;default->frame3;};}
    private void setFrame(int i,int v){switch(i){case 0->frame0=v;case 1->frame1=v;case 2->frame2=v;default->frame3=v;}}
    private int xAcc(int i){return switch(i){case 0->xAcc0;case 1->xAcc1;case 2->xAcc2;default->xAcc3;};}
    private void setXAcc(int i,int v){switch(i){case 0->xAcc0=v;case 1->xAcc1=v;case 2->xAcc2=v;default->xAcc3=v;}}
    private int yAcc(int i){return switch(i){case 0->yAcc0;case 1->yAcc1;case 2->yAcc2;default->yAcc3;};}
    private void setYAcc(int i,int v){switch(i){case 0->yAcc0=v;case 1->yAcc1=v;case 2->yAcc2=v;default->yAcc3=v;}}
    private int x(int i){return switch(i){case 0->x0;case 1->x1;case 2->x2;default->x3;};}
    private void setX(int i,int v){switch(i){case 0->x0=v;case 1->x1=v;case 2->x2=v;default->x3=v;}}
    private int y(int i){return switch(i){case 0->y0;case 1->y1;case 2->y2;default->y3;};}
    private void setY(int i,int v){switch(i){case 0->y0=v;case 1->y1=v;case 2->y2=v;default->y3=v;}}
}
