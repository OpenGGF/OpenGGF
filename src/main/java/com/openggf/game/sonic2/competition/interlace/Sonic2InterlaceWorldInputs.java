package com.openggf.game.sonic2.competition.interlace;

import java.util.List;
import java.util.Objects;

/**
 * Engine-private, immutable inputs to the shipped S2 display routines. These are
 * projections of the actual live owners, never ordinary render commands or art keys.
 * No record here advances animation, camera, rings, HUD or a hardware clock.
 */
public final class Sonic2InterlaceWorldInputs {
    private Sonic2InterlaceWorldInputs() { }

    /** Unsigned stored camera position words, before native subtraction/wrapping. */
    public record Camera(int x, int y) {
        public Camera { word(x); word(y); }
    }

    /** ROM mappings pointer and stored frame byte; static_mappings selects a lone piece. */
    public record Mapping(int address, int frame) {
        public Mapping {
            if (address < 0 || address > 0xFFFFFF || (address & 1) != 0)
                throw new IllegalArgumentException("Invalid native ROM mapping pointer");
            octet(frame);
        }
    }

    public record Child(int x, int y, int frame) {
        public Child { word(x); word(y); octet(frame); }
    }

    /** Actual mainspr fields and subspr_data triples, in native memory order. */
    public record MultiSprite(int mainFrame, int width, int height, List<Child> children) {
        public MultiSprite {
            octet(mainFrame); octet(width); octet(height);
            children = List.copyOf(children);
            if (children.size() > 255) throw new IllegalArgumentException("Child count is a byte");
        }
    }

    /**
     * A late-resolved SST occupant. artTile is the full already-adjusted native
     * art_tile word, NOT an ordinary renderer's virtual pattern-bank address.
     * For screen-space objects x/y are the stored x_pixel/y_pixel hardware words.
     */
    public record ObjectSlot(int id, int renderFlags, int x, int y, int width, int yRadius,
                             Mapping mapping, int artTile, MultiSprite multiSprite) {
        public ObjectSlot {
            octet(id); octet(renderFlags); word(x); word(y); octet(width); octet(yRadius); word(artTile);
            Objects.requireNonNull(mapping, "mapping");
            if (((renderFlags & 0x40) != 0) != (multiSprite != null))
                throw new IllegalArgumentException("multi_sprite flag and actual child state differ");
        }
    }

    /** Null occupant retains an admitted orphan; id zero retains a cleared actual owner. */
    public record Admission(int slot, int bucket, int ordinal, ObjectSlot occupant) {
        public Admission {
            if (slot < 0 || bucket < 0 || bucket > 7 || ordinal < 0)
                throw new IllegalArgumentException("Invalid accepted DisplaySprite reference");
        }
    }

    /** A live six-byte Ring_Positions row, including its consumed/frame status word. */
    public record Ring(int index, int status, int x, int y) {
        public Ring {
            if (index < 0) throw new IllegalArgumentException("Negative native ring index");
            word(status); word(x); word(y);
        }
    }

    /** Real per-player [Ring_start_addr, Ring_end_addr) order; no union-of-views fallback. */
    public record RingWindow(List<Ring> rows) {
        public RingWindow {
            rows = List.copyOf(rows);
            int previous = -1;
            for (var ring : rows) {
                if (previous >= 0 && ring.index() != previous + 1)
                    throw new IllegalArgumentException("Ring window must contain every native row in order");
                previous = ring.index();
            }
        }
    }

    /** Raw HUD inputs; the actual timer owner supplies bytes, not formatted text. */
    public record PlayerHud(int rings, int minute, int second, int timerEnabled) {
        public PlayerHud { word(rings); octet(minute); octet(second); octet(timerEnabled); }
    }

    public record Hud(boolean levelStarted, int levelFrameCounter, PlayerHud first,
                      PlayerHud second, int loserTimeLeft) {
        public Hud {
            word(levelFrameCounter); word(loserTimeLeft);
            Objects.requireNonNull(first, "first HUD"); Objects.requireNonNull(second, "second HUD");
        }
    }

    /** One actual BuildSprites call. Repeated prepare/draw calls must not manufacture this input. */
    public record Build(Camera firstCamera, Camera secondCamera, Hud hud, RingWindow firstRings,
                        RingWindow secondRings, int ringsAnimFrame, List<Admission> admissions,
                        boolean teleportFlag) {
        public Build {
            Objects.requireNonNull(firstCamera); Objects.requireNonNull(secondCamera); Objects.requireNonNull(hud);
            Objects.requireNonNull(firstRings); Objects.requireNonNull(secondRings); octet(ringsAnimFrame);
            admissions = List.copyOf(admissions);
        }
    }

    /** Final on_screen bit after both passes; root applies it to the actual resolved SST owner. */
    public record Visibility(int slot, boolean onScreen) { }

    static void word(int value) {
        if (value < 0 || value > 0xFFFF) throw new IllegalArgumentException("Expected a stored 16-bit word");
    }
    static void octet(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("Expected a stored byte");
    }
}
