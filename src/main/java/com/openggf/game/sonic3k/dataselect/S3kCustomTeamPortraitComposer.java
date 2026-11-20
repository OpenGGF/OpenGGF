package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.save.SelectedTeam;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class S3kCustomTeamPortraitComposer {
    private static final int FRAME_SONIC = 5;
    private static final int FRAME_TAILS = 6;
    private static final int FRAME_KNUCKLES = 7;

    private static final int MAIN_SINGLE_X = -12;
    private static final int MAIN_SINGLE_WITH_SIDEKICK_X = -14;
    private static final int MAIN_SINGLE_BOTTOM = 0;
    private static final int MAIN_MULTI_X = 0;
    private static final int MAIN_MULTI_BOTTOM = 5;
    private static final int LEFT_SIDEKICK_X = -14;
    private static final int SINGLE_SIDEKICK_BOTTOM = 0;
    private static final int BOTTOM_ROW_BOTTOM = -5;
    private static final int RIGHT_SINGLE_SIDEKICK_X = 8;
    private static final int RIGHT_SIDEKICK_X = 14;
    private static final int EXTRA_STACK_X = 0;
    private static final int EXTRA_STACK_BOTTOM = -10;
    private static final int EXTRA_STACK_BOTTOM_STEP = 0;
    private static final int TARGET_SINGLE_SIDEKICK_OVERLAP = 10;

    private static final List<String> FIFTEEN_SONICS = Collections.nCopies(15, "sonic");
    private static final List<String> EIGHT_TAILS = Collections.nCopies(8, "tails");

    /**
     * Hand-placed rosters. Each was laid out against rendered NO SAVE and save-slot windows,
     * because no single spacing rule kept every mix of native poses readable inside the save
     * slot (56 px between its rails) and off the NO SAVE arrows. Layers are front to back:
     * the leader stands in front and the flanking characters face outwards behind it. Each
     * placement gives the pose's x shift in the slot and in the wider NO SAVE window, where the
     * flanks spread further out, then its feet line relative to the window anchor and whether
     * it is mirrored. Unlisted rosters use the generic layout.
     */
    private static final Map<Roster, List<Placement>> AUTHORED_LAYOUTS = Map.of(
            new Roster("sonic", List.of("tails", "knuckles")), List.of(
                    new Placement("sonic", 1, 1, 0, false),
                    new Placement("tails", -2, -7, -4, true),
                    new Placement("knuckles", 9, 14, -4, false)),
            new Roster("sonic", List.of("tails", "tails")), tailsFlankingSonic(),
            new Roster("sonic", EIGHT_TAILS), tailsFlankingSonic(),
            new Roster("sonic", FIFTEEN_SONICS), List.of(
                    new Placement("sonic", 1, 1, 0, false),
                    new Placement("sonic", -11, -16, -4, false),
                    new Placement("sonic", 15, 20, -4, true)));

    /** The window a composed portrait is drawn in. */
    enum Window {
        SAVE_SLOT,
        NO_SAVE
    }

    private final List<SpriteMappingFrame> mappings;
    private final java.util.function.Function<String, SpriteMappingFrame> customFrames;

    S3kCustomTeamPortraitComposer(List<SpriteMappingFrame> mappings) {
        this(mappings, code -> null);
    }

    S3kCustomTeamPortraitComposer(List<SpriteMappingFrame> mappings,
            java.util.function.Function<String, SpriteMappingFrame> customFrames) {
        this.mappings = List.copyOf(Objects.requireNonNull(mappings, "mappings"));
        this.customFrames = Objects.requireNonNull(customFrames);
    }

    List<PortraitLayer> layoutLayers(SelectedTeam team) {
        return layoutLayers(team, Window.SAVE_SLOT);
    }

    List<PortraitLayer> layoutLayers(SelectedTeam team, Window window) {
        if (team == null) {
            return List.of();
        }
        List<String> sidekicks = team.sidekicks();
        List<PortraitLayer> layers = new ArrayList<>();
        List<Placement> authored = AUTHORED_LAYOUTS.get(new Roster(canonical(team.mainCharacter()),
                sidekicks.stream().map(S3kCustomTeamPortraitComposer::canonical).toList()));
        if (authored != null) {
            for (Placement placement : authored) {
                int x = window == Window.NO_SAVE ? placement.noSaveX() : placement.slotX();
                layers.add(layerFor(placement.characterCode(), x, placement.feetY(), placement.hFlip()));
            }
            return List.copyOf(layers);
        }
        if (sidekicks.size() <= 1) {
            String main = canonical(team.mainCharacter());
            int mainX = sidekicks.isEmpty() ? MAIN_SINGLE_X : MAIN_SINGLE_WITH_SIDEKICK_X;
            layers.add(layerFor(main, mainX, MAIN_SINGLE_BOTTOM, shouldMirror(main, Position.LEFT)));
            if (!sidekicks.isEmpty()) {
                String sidekick = canonical(sidekicks.getFirst());
                layers.add(layerFor(sidekick, rightSingleSidekickX(mainX, main, sidekick), SINGLE_SIDEKICK_BOTTOM,
                        shouldMirror(sidekick, Position.RIGHT)));
            }
            return List.copyOf(layers);
        }

        String main = canonical(team.mainCharacter());
        String left = canonical(sidekicks.get(0));
        String right = canonical(sidekicks.get(1));
        layers.add(layerFor(main, MAIN_MULTI_X, MAIN_MULTI_BOTTOM, false));
        layers.add(layerFor(left, LEFT_SIDEKICK_X, BOTTOM_ROW_BOTTOM, shouldMirror(left, Position.LEFT)));
        layers.add(layerFor(right, RIGHT_SIDEKICK_X, BOTTOM_ROW_BOTTOM, shouldMirror(right, Position.RIGHT)));

        List<String> extras = uniqueOverflowCharacters(team);
        for (int i = 0; i < extras.size(); i++) {
            layers.add(layerFor(extras.get(i), EXTRA_STACK_X, EXTRA_STACK_BOTTOM + (i * EXTRA_STACK_BOTTOM_STEP),
                    false));
        }
        return List.copyOf(layers);
    }

    SpriteMappingFrame compose(SelectedTeam team) {
        return compose(team, Window.SAVE_SLOT);
    }

    SpriteMappingFrame compose(SelectedTeam team, Window window) {
        List<SpriteMappingPiece> combined = new ArrayList<>();
        for (PortraitLayer layer : layoutLayers(team, window)) {
            SpriteMappingFrame frame = baseFrameFor(layer.characterCode());
            if (frame == null) {
                continue;
            }
            FrameBounds bounds = frameBounds(frame);
            for (SpriteMappingPiece piece : frame.pieces()) {
                int pieceWidthPixels = piece.widthTiles() * 8;
                int xOffset = layer.hFlip()
                        ? bounds.minX() + bounds.maxXExclusive() - piece.xOffset() - pieceWidthPixels + layer.xOffset()
                        : piece.xOffset() + layer.xOffset();
                combined.add(new SpriteMappingPiece(
                        xOffset,
                        piece.yOffset() + layer.yOffset(),
                        piece.widthTiles(),
                        piece.heightTiles(),
                        piece.tileIndex(),
                        layer.hFlip() ^ piece.hFlip(),
                        piece.vFlip(),
                        piece.paletteIndex(),
                        piece.priority()));
            }
        }
        return new SpriteMappingFrame(List.copyOf(combined));
    }

    boolean requiresCustomComposition(SelectedTeam team) {
        if (team == null) {
            return false;
        }
        String main = canonical(team.mainCharacter());
        List<String> sidekicks = team.sidekicks().stream().map(S3kCustomTeamPortraitComposer::canonical).toList();
        if ("knuckles".equals(main) && sidekicks.isEmpty()) {
            return false;
        }
        if ("tails".equals(main) && sidekicks.isEmpty()) {
            return false;
        }
        if ("sonic".equals(main) && sidekicks.isEmpty()) {
            return false;
        }
        return !("sonic".equals(main) && sidekicks.equals(List.of("tails")));
    }

    private static List<Placement> tailsFlankingSonic() {
        return List.of(
                new Placement("sonic", 1, 1, 0, false),
                new Placement("tails", -2, -7, -4, true),
                new Placement("tails", 13, 18, -4, false));
    }

    private PortraitLayer layerFor(String characterCode, int xOffset, int targetBottom, boolean hFlip) {
        SpriteMappingFrame frame = baseFrameFor(characterCode);
        if (frame == null) {
            return new PortraitLayer(characterCode, xOffset, targetBottom, hFlip);
        }
        FrameBounds bounds = frameBounds(frame);
        return new PortraitLayer(characterCode, xOffset, targetBottom - bounds.maxYExclusive(), hFlip);
    }

    private int rightSingleSidekickX(int mainX, String mainCharacter, String sidekickCharacter) {
        SpriteMappingFrame mainFrame = baseFrameFor(mainCharacter);
        SpriteMappingFrame sidekickFrame = baseFrameFor(sidekickCharacter);
        if (mainFrame == null || sidekickFrame == null) {
            return RIGHT_SINGLE_SIDEKICK_X;
        }
        FrameBounds mainBounds = frameBounds(mainFrame);
        FrameBounds sidekickBounds = frameBounds(sidekickFrame);
        if (mainBounds.minX() >= 0 && sidekickBounds.minX() >= 0) {
            return RIGHT_SINGLE_SIDEKICK_X;
        }
        int mainRight = mainX + mainBounds.maxXExclusive();
        return mainRight - TARGET_SINGLE_SIDEKICK_OVERLAP - sidekickBounds.minX();
    }

    private List<String> uniqueOverflowCharacters(SelectedTeam team) {
        List<String> remainder = team.sidekicks().stream()
                .skip(2)
                .map(S3kCustomTeamPortraitComposer::canonical)
                .toList();
        if (remainder.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>(remainder);
        List<String> extras = new ArrayList<>(ordered);
        String main = canonical(team.mainCharacter());
        if (extras.remove(main)) {
            extras.add(0, main);
        }
        return List.copyOf(extras);
    }

    private SpriteMappingFrame baseFrameFor(String characterCode) {
        SpriteMappingFrame custom = customFrames.apply(characterCode);
        if (custom != null) return custom;
        int frameIndex = switch (canonical(characterCode)) {
            case "tails" -> FRAME_TAILS;
            case "knuckles" -> FRAME_KNUCKLES;
            default -> FRAME_SONIC;
        };
        if (frameIndex < 0 || frameIndex >= mappings.size()) {
            return null;
        }
        return mappings.get(frameIndex);
    }

    private static String canonical(String characterCode) {
        if (characterCode == null || characterCode.isBlank()) {
            return "sonic";
        }
        return characterCode.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean shouldMirror(String characterCode, Position position) {
        if (position == Position.CENTER) {
            return false;
        }
        String canonical = canonical(characterCode);
        return switch (canonical) {
            case "sonic" -> position == Position.RIGHT;
            case "tails" -> position != Position.CENTER;
            case "knuckles" -> position == Position.LEFT;
            default -> position == Position.RIGHT;
        };
    }

    private static FrameBounds frameBounds(SpriteMappingFrame frame) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (SpriteMappingPiece piece : frame.pieces()) {
            minX = Math.min(minX, piece.xOffset());
            minY = Math.min(minY, piece.yOffset());
            maxX = Math.max(maxX, piece.xOffset() + (piece.widthTiles() * 8));
            maxY = Math.max(maxY, piece.yOffset() + (piece.heightTiles() * 8));
        }
        if (minX == Integer.MAX_VALUE) {
            return new FrameBounds(0, 0, 0, 0);
        }
        return new FrameBounds(minX, minY, maxX, maxY);
    }

    private enum Position {
        LEFT,
        RIGHT,
        CENTER
    }

    private record FrameBounds(int minX, int minY, int maxXExclusive, int maxYExclusive) {
    }

    private record Roster(String main, List<String> sidekicks) {
    }

    private record Placement(String characterCode, int slotX, int noSaveX, int feetY, boolean hFlip) {
    }

    record PortraitLayer(String characterCode, int xOffset, int yOffset, boolean hFlip) {
    }
}
