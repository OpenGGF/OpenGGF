package com.openggf.sprites.playable;

import com.openggf.game.PoweredEffects;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.ObjectVisualGraphicsAccess;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.physics.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Creator particles for an authored powered form. Emission runs on the logic tick from live
 * player state; drawing only reads. Particles are presentation: they never feed gameplay,
 * are not captured by rewind (a restore clears them) and do not survive a reload. They are
 * drawn as native sprite pieces on the character's own palette line and current priority.
 */
public final class PoweredFormEffects {
    /** Moving faster than this, not rolling, is {@code FAST}: 6 px/frame. */
    static final int FAST_SPEED = 0x600;
    /** A roll or jump ball faster than this is {@code ROLLING}: 2 px/frame. */
    static final int ROLLING_SPEED = 0x200;
    /** Distinct particle art sets kept resident in the shared presentation range. */
    private static final int MAX_RESIDENT_ART = 4;

    private static final class Particle {
        float x, y, vx, vy;
        final float gravity;
        final int sprite, life, phase;
        int age;

        Particle(float x, float y, float vx, float vy, float gravity, int sprite, int life, int phase) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.gravity = gravity; this.sprite = sprite; this.life = life; this.phase = phase;
        }
    }

    /** Uploaded pieces for one particle list, shared by every character drawing the same pixels. */
    private static final class Art {
        final List<PoweredEffects.Particle> particles;
        final int[] firstFrame;
        final PatternSpriteRenderer renderer;
        final int patternCount;
        ObjectVisualGraphicsAccess.ArtBank bank;

        Art(List<PoweredEffects.Particle> particles, GraphicsManager graphics) {
            this.particles = particles;
            firstFrame = new int[particles.size()];
            var patterns = new ArrayList<Pattern>();
            var frames = new ArrayList<SpriteMappingFrame>();
            for (int i = 0; i < particles.size(); i++) {
                firstFrame[i] = frames.size();
                for (var frame : particles.get(i).frames()) {
                    var image = frame.image();
                    int columns = (image.width() + 7) / 8, rows = (image.height() + 7) / 8;
                    // One native piece per frame; tiles are column-major as the VDP reads them.
                    frames.add(new SpriteMappingFrame(List.of(new SpriteMappingPiece(-frame.hotspotX(),
                            -frame.hotspotY(), columns, rows, patterns.size(), false, false, 0, false))));
                    patterns.addAll(List.of(tiles(image)));
                }
            }
            patternCount = patterns.size();
            renderer = new PatternSpriteRenderer(new ObjectSpriteSheet(patterns.toArray(Pattern[]::new), frames, 0, 1),
                    graphics);
        }

        int base(GraphicsManager graphics) {
            if (!ObjectVisualGraphicsAccess.validArt(bank, graphics, this)) {
                bank = ObjectVisualGraphicsAccess.acquireArt(graphics, this, patternCount);
            }
            int base = ObjectVisualGraphicsAccess.artBase(bank, graphics, this);
            renderer.ensurePatternsCached(graphics, base);
            return base;
        }
    }

    private record ArtKey(GraphicsManager graphics, String pixels) { }
    // Keyed by pixel content, so a reloaded profile with the same art reuses its upload.
    private static final Map<ArtKey, Art> RESIDENT = new LinkedHashMap<>(8, 0.75f, true);

    private final List<Particle> particles = new ArrayList<>();
    // Presentation-only noise; never the game's RNG.
    private final SplittableRandom noise = new SplittableRandom(0x6111DED);
    private int clock;
    private PoweredEffects keyed;
    private String artKey = "";

    /** Logic tick: age live particles, then run continuous emitters for the powered form. */
    void tick(AbstractPlayableSprite player, PoweredEffects effects, boolean powered) {
        clock++;
        for (var iterator = particles.iterator(); iterator.hasNext(); ) {
            var particle = iterator.next();
            particle.x += particle.vx;
            particle.y += particle.vy;
            particle.vy += particle.gravity;
            if (++particle.age >= particle.life) iterator.remove();
        }
        if (effects != keyed) {
            keyed = effects;
            artKey = effects == null ? "" : contentKey(effects.particles());
        }
        if (!powered || effects == null || player.getDead()) return;
        boolean striking = striking(player);
        boolean rolling = player.getRolling() && speed(player) >= ROLLING_SPEED;
        boolean flying = player.getTailsFlightController() != null && player.getTailsFlightController().isActive();
        for (var emitter : effects.emitters()) {
            boolean active = switch (emitter.trigger()) {
                case FAST -> !striking && !player.getRolling() && speed(player) >= FAST_SPEED;
                case ROLLING -> !striking && rolling;
                case STRIKING -> striking;
                case FLYING -> flying;
                // Tails_Move_FlySwim state 2 is the single tick of a fresh flap.
                case FLAP -> flying && (player.getDoubleJumpFlag() & 0xFF) == 2;
                case TRANSFORM, STRIKE_HIT -> false;
            };
            boolean continuous = emitter.trigger() != PoweredEffects.Trigger.FLAP;
            if (active && (!continuous || clock % emitter.interval() == 0)) emit(player, effects, emitter);
        }
    }

    /** Logic tick: fire every one-shot emitter for {@code trigger}. */
    void fire(AbstractPlayableSprite player, PoweredEffects effects, PoweredEffects.Trigger trigger) {
        if (effects == null) return;
        for (var emitter : effects.emitters())
            if (emitter.trigger() == trigger) emit(player, effects, emitter);
    }

    void clear() {
        particles.clear();
    }

    int liveCount() {
        return particles.size();
    }

    private void emit(AbstractPlayableSprite player, PoweredEffects effects, PoweredEffects.Emitter emitter) {
        int facing = player.getDirection() == Direction.LEFT ? -1 : 1;
        float originX = player.getCentreX() + facing * emitter.offsetX();
        float originY = player.getCentreY() + emitter.offsetY();
        for (int i = 0; i < emitter.count() && particles.size() < PoweredEffects.MAX_LIVE_PARTICLES; i++) {
            float x, y, vx, vy;
            if (emitter.spread() == PoweredEffects.Spread.RING) {
                double angle = Math.PI * 2 * i / emitter.count() + clock * 0.37;
                float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                x = originX + cos * emitter.radius();
                y = originY + sin * emitter.radius();
                vx = cos * emitter.speedX() / 256f;
                vy = sin * emitter.speedX() / 256f;
            } else {
                x = originX + (float) (noise.nextDouble() * 2 - 1) * emitter.radius();
                y = originY + (float) (noise.nextDouble() * 2 - 1) * emitter.radius();
                vx = facing * emitter.speedX() / 256f;
                vy = emitter.speedY() / 256f;
            }
            vx += (float) (noise.nextDouble() * 2 - 1) * emitter.jitter() / 256f;
            vy += (float) (noise.nextDouble() * 2 - 1) * emitter.jitter() / 256f;
            int sprite = emitter.particles().get(noise.nextInt(emitter.particles().size()));
            // A cycle spanning the whole life plays once; a shorter one loops from a random phase.
            int cycle = effects.particles().get(sprite).frames().size() * effects.particles().get(sprite).frameTicks();
            particles.add(new Particle(x, y, vx, vy, emitter.gravity() / 256f, sprite, emitter.life(),
                    cycle >= emitter.life() ? 0 : noise.nextInt(cycle)));
        }
    }

    /** Draw thread, after the live body: particles sit behind it at the same priority. */
    void draw(AbstractPlayableSprite player, PoweredEffects effects, GraphicsManager graphics) {
        if (particles.isEmpty() || effects == null || effects != keyed || graphics == null
                || player.getSpriteRenderer() == null) return;
        var art = art(new ArtKey(graphics, artKey), effects.particles(), graphics);
        art.base(graphics);
        var renderer = player.getSpriteRenderer();
        var context = renderer.getRenderContext();
        int line = context == null ? renderer.paletteLine() : context.getEffectivePaletteLine(renderer.paletteLine());
        for (var particle : particles) {
            // The last quarter of a life flickers out, as native sprites fade.
            if (particle.age * 4 >= particle.life * 3 && (particle.age & 1) != 0) continue;
            var sprite = effects.particles().get(particle.sprite);
            int step = (particle.age + particle.phase) / sprite.frameTicks(), frames = sprite.frames().size();
            int frame = frames * sprite.frameTicks() >= particle.life ? Math.min(step, frames - 1) : step % frames;
            art.renderer.drawFrameIndex(art.firstFrame[particle.sprite] + frame, Math.round(particle.x),
                    Math.round(particle.y), false, false, line);
        }
    }

    private static Art art(ArtKey key, List<PoweredEffects.Particle> particles, GraphicsManager graphics) {
        synchronized (RESIDENT) {
            var art = RESIDENT.get(key);
            if (art != null) return art;
            art = new Art(particles, graphics);
            RESIDENT.put(key, art);
            if (RESIDENT.size() > MAX_RESIDENT_ART) {
                var eldest = RESIDENT.entrySet().iterator().next();
                ObjectVisualGraphicsAccess.releaseArt(eldest.getValue().bank, eldest.getValue());
                RESIDENT.remove(eldest.getKey());
            }
            return art;
        }
    }

    private static String contentKey(List<PoweredEffects.Particle> particles) {
        var key = new StringBuilder();
        var encoder = java.util.Base64.getEncoder();
        for (var particle : particles) {
            key.append('|').append(particle.frameTicks());
            for (var frame : particle.frames())
                key.append(';').append(frame.image().width()).append('x').append(frame.image().height())
                        .append('@').append(frame.hotspotX()).append(',').append(frame.hotspotY())
                        .append(':').append(encoder.encodeToString(frame.image().pixels()));
        }
        return key.toString();
    }

    private static int speed(AbstractPlayableSprite player) {
        return player.getAir() ? Math.max(Math.abs(player.getXSpeed()), Math.abs(player.getYSpeed()))
                : Math.abs(player.getGSpeed());
    }

    static boolean striking(AbstractPlayableSprite player) {
        var homing = PlayableSpriteInternalAccess.homingAttack(player);
        return homing != null && homing.isStriking(player);
    }

    private static Pattern[] tiles(com.openggf.game.CharacterPresentation.Image image) {
        int columns = (image.width() + 7) / 8, rows = (image.height() + 7) / 8;
        Pattern[] patterns = new Pattern[columns * rows];
        java.util.Arrays.setAll(patterns, ignored -> new Pattern());
        byte[] pixels = image.pixels();
        for (int y = 0; y < image.height(); y++)
            for (int x = 0; x < image.width(); x++)
                patterns[x / 8 * rows + y / 8].setPixel(x % 8, y % 8, pixels[y * image.width() + x]);
        return patterns;
    }

    /** A landed homing or divebomb strike, from the world's homing runtime. */
    public static void strikeLanded(AbstractPlayableSprite player) {
        if (player != null && player.getSuperStateController() instanceof ProfileSuperStateController profile)
            profile.strikeLanded();
    }
}
