package com.openggf.game.sonic2.specialstage;

import com.openggf.graphics.SpriteMaskReplayRole;
import com.openggf.graphics.SpritePiece;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.level.render.SpritePieceRenderer.PreparedPiece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRomMappings.Family;
import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.ArtRole;
import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.Kind;

/** ROM geometry in BuildSprites bucket/slot/piece order, before H32 admission. */
final class Sonic2SpecialStageSpriteAssembly {
    // DisplaySprite rejects at126 bytes in each128-byte list (count word +
    // 63 object addresses), before BuildSprites expands any mapping pieces.
    private static final int DISPLAY_OWNERS_PER_BUCKET = 63;
    record ArtBases(int player, int hud, int start, int messages, int tailsText,
                    int ring, int bomb, int stars, int explosion, int emerald,
                    int shadowFlat, int shadowDiag, int shadowSide) { }

    record Group(Sonic2SpecialStageSlotPool.Handle owner, boolean shadow,
                 List<SpriteSatEntry> geometry, List<PreparedPiece> fragments) {
        Group {
            geometry = List.copyOf(geometry);
            fragments = List.copyOf(fragments);
        }
    }

    private final Sonic2SpecialStageDataLoader loader;
    private final ArtBases art;
    private final int originX;

    Sonic2SpecialStageSpriteAssembly(Sonic2SpecialStageDataLoader loader, ArtBases art, int originX) {
        this.loader = java.util.Objects.requireNonNull(loader);
        this.art = art;
        this.originX = originX;
    }

    List<Group> assemble(List<Sonic2SpecialStageSlotPool.RenderState> states,
                         Sonic2SpecialStageRenderer.RingHudState composition,
                         List<Sonic2SpecialStageIntro.NativeCounterPayload> counters,
                         int ringsToGoBcd, int ringsToGoChildCount) {
        Map<Sonic2SpecialStageSlotPool.Handle, Sonic2SpecialStageIntro.NativeCounterPayload> payloads =
                counters.stream().collect(Collectors.toMap(
                        Sonic2SpecialStageIntro.NativeCounterPayload::handle, value -> value));
        List<Group> result = new ArrayList<>();
        int[] bucketOwners = new int[8];
        // DisplaySprite appends during ascending RunObjects. BuildSprites then
        // drains lower buckets first; painter order is not hardware head order.
        states.stream().sorted(Comparator
                .comparingInt((Sonic2SpecialStageSlotPool.RenderState s) -> s.identity().priorityBucket())
                .thenComparingInt(s -> s.identity().slot())).filter(Sonic2SpecialStageSlotPool.RenderState::visible)
                .filter(state -> bucketOwners[state.identity().priorityBucket()]++ < DISPLAY_OWNERS_PER_BUCKET)
                .forEach(state -> {
                    if (state.artRole() == ArtRole.HUD) {
                        hud(state, composition, result);
                    } else if (state.mappingFrame() < 0 && state.artRole() == ArtRole.NUMBER) {
                        if (state.identity().kind() == Kind.RINGS_TO_GO_NUMBER) {
                            for (int i = 0; i < ringsToGoChildCount; i++) {
                                mapping(state, Family.DIGITS, (ringsToGoBcd >>> (i * 4)) & 0xF,
                                        0x5A - i * 8, 0x38, 2, tile -> art.hud + tile, result);
                            }
                        } else {
                            var payload = payloads.get(state.identity().handle());
                            if (payload != null && payload.displayed()) {
                                for (var digit : payload.digits()) {
                                    mapping(state, Family.DIGITS, digit.mappingFrame(), digit.x(), digit.y(),
                                            2, tile -> art.hud + tile, result);
                                }
                            }
                        }
                    } else if (state.artRole() == ArtRole.SONIC || state.artRole() == ArtRole.TAILS
                            || state.artRole() == ArtRole.TAILS_TAIL) {
                        player(state, result);
                    } else {
                        Family family = switch (state.artRole()) {
                            case START_BANNER -> Family.START_BANNER;
                            case MESSAGES -> Family.MESSAGE;
                            case NUMBER -> Family.DIGITS;
                            case RING, STARS -> Family.RING;
                            case BOMB, EXPLOSION -> Family.BOMB;
                            case EMERALD -> Family.EMERALD;
                            case SHADOW -> Family.SHADOW;
                            default -> throw new IllegalArgumentException("Unknown SS art role " + state.artRole());
                        };
                        int palette = switch (state.artRole()) {
                            case START_BANNER -> 0;
                            case SHADOW, EMERALD, RING -> 3;
                            case BOMB -> loader.getBombPaletteLine();
                            case MESSAGES -> state.identity().kind() == Kind.WING || state.identity().kind() == Kind.HAND
                                    ? composition.tailsActive() && !composition.sonicActive() ? 2 : 1 : 2;
                            default -> 2;
                        };
                        mapping(state, family, state.mappingFrame(), state.x(), state.y(), palette,
                                tile -> resolveArt(state, tile), result);
                    }
                });
        return List.copyOf(result);
    }

    private void hud(Sonic2SpecialStageSlotPool.RenderState state,
                     Sonic2SpecialStageRenderer.RingHudState composition,
                     List<Group> result) {
        boolean team = composition.sonicActive() && composition.tailsActive();
        int x = team ? 0x80 : composition.tailsActive() ? 0x38 : 0xD4;
        if (composition.sonicActive()) {
            mapping(state, Family.HUD, 0, x, 0, 0,
                    tile -> !loader.showMainPlayerHudName() && tile < 5 ? -1 : art.hud + tile,
                    result);
        }
        if (composition.tailsActive()) {
            // World/English Graphics_Flags selects TAILS: $1FA+$5AA=$7A4.
            mapping(state, Family.HUD, 2, x, 0, 0,
                    tile -> tile >= 0x5AA ? art.tailsText + tile - 0x5AA : art.hud + tile,
                    result);
        }
        if (team) mapping(state, Family.HUD, 3, x, 0, 0, tile -> art.hud + tile, result);
    }

    private int resolveArt(Sonic2SpecialStageSlotPool.RenderState state, int tile) {
        return switch (state.artRole()) {
            case START_BANNER -> art.start + tile;
            case MESSAGES -> tile >= 0x58 ? art.hud + tile - 0x58 : art.messages + tile;
            case NUMBER -> art.hud + tile;
            case RING -> art.ring + tile;
            case BOMB -> art.bomb + tile;
            case STARS -> art.stars + tile;
            case EXPLOSION -> art.explosion + tile;
            case EMERALD -> art.emerald + tile;
            case SHADOW -> switch (state.mappingFrame() % 3) {
                case 0 -> art.shadowFlat + tile;
                case 1 -> art.shadowDiag + tile;
                default -> art.shadowSide + tile;
            };
            default -> throw new IllegalArgumentException("Not resident SS art " + state.artRole());
        };
    }

    private void mapping(Sonic2SpecialStageSlotPool.RenderState state, Family family, int frame,
                         int x, int y, int palette, IntUnaryOperator source,
                         List<Group> result) {
        for (var piece : loader.getNativeSpriteFrame(family, frame).pieces()) {
            int px = originX + x + (state.xFlip() ? -piece.xOffset() - piece.widthTiles() * 8 : piece.xOffset());
            int py = y + (state.yFlip() ? -piece.yOffset() - piece.heightTiles() * 8 : piece.yOffset());
            boolean hFlip = piece.hFlip() ^ state.xFlip(), vFlip = piece.vFlip() ^ state.yFlip();
            int resolvedPalette = (piece.paletteIndex() + palette) & 3;
            var geometry = entry(state, px, py, piece.widthTiles(), piece.heightTiles(),
                    source.applyAsInt(piece.tileIndex()), piece.tileIndex(), resolvedPalette,
                    hFlip, vFlip, piece.priority());
            List<PreparedPiece> fragments = new ArrayList<>();
            for (int col = 0; col < piece.widthTiles(); col++) {
                for (int row = 0; row < piece.heightTiles(); row++) {
                    int tile = piece.tileIndex() + (hFlip ? piece.widthTiles() - 1 - col : col) * piece.heightTiles()
                            + (vFlip ? piece.heightTiles() - 1 - row : row);
                    int pattern = source.applyAsInt(tile);
                    if (pattern >= 0) fragments.add(prepared(px + col * 8, py + row * 8,
                            pattern, tile, resolvedPalette, hFlip, vFlip, piece.priority()));
                }
            }
            result.add(new Group(state.identity().handle(), state.artRole() == ArtRole.SHADOW,
                    List.of(geometry), fragments));
        }
    }

    private void player(Sonic2SpecialStageSlotPool.RenderState state,
                        List<Group> result) {
        var frame = switch (state.artRole()) {
            case SONIC -> loader.getNativeMainPlayerFrame(state.mappingFrame());
            case TAILS -> loader.getNativeSpriteFrame(Family.TAILS, state.mappingFrame());
            default -> loader.getNativeSpriteFrame(Family.TAILS_TAILS, state.mappingFrame());
        };
        int palette = state.artRole() == ArtRole.SONIC ? 1 : 2;
        var render = switch (state.artRole()) {
            case SONIC -> loader.getMainPlayerFrame(state.mappingFrame());
            case TAILS -> loader.getTailsPlayerFrame(state.mappingFrame());
            default -> loader.getTailsTailFrame(state.mappingFrame());
        };
        int fragmentCursor = 0;
        for (var piece : frame.pieces()) {
            int px = originX + state.x() + (state.xFlip() ? -piece.xOffset() - piece.widthTiles() * 8 : piece.xOffset());
            int py = state.y() + (state.yFlip() ? -piece.yOffset() - piece.heightTiles() * 8 : piece.yOffset());
            int resolvedPalette = (palette + piece.paletteIndex()) & 3;
            var geometry = entry(state, px, py, piece.widthTiles(), piece.heightTiles(), art.player,
                    piece.tileIndex(), resolvedPalette,
                    piece.hFlip() ^ state.xFlip(), piece.vFlip() ^ state.yFlip(), piece.priority());
            List<PreparedPiece> fragments = new ArrayList<>();
            for (int tile = 0; tile < piece.widthTiles() * piece.heightTiles(); tile++) {
                var fragment = render.pieces[fragmentCursor++];
                int fx = originX + state.x() + (state.xFlip()
                        ? -fragment.xOffset - fragment.widthTiles * 8 : fragment.xOffset);
                int fy = state.y() + (state.yFlip()
                        ? -fragment.yOffset - fragment.heightTiles * 8 : fragment.yOffset);
                fragments.add(new PreparedPiece(fx, fy, fragment.widthTiles, fragment.heightTiles,
                        art.player + fragment.tileIndex, fragment.tileIndex & 0x7FF, resolvedPalette,
                        fragment.hFlip ^ state.xFlip(), fragment.vFlip ^ state.yFlip(),
                        piece.priority(), false, 0, fragment.heightTiles));
            }
            // Separate replay groups keep overlapping native pieces' own art
            // attached to their admission entry, never a neighbouring piece.
            result.add(new Group(state.identity().handle(), false, List.of(geometry), fragments));
        }
        if (fragmentCursor != render.pieces.length) {
            throw new IllegalStateException("SS player art fragments do not match native geometry");
        }
    }

    private static SpriteSatEntry entry(Sonic2SpecialStageSlotPool.RenderState state, int x, int y,
                                        int width, int height, int pattern, int rawTile, int palette,
                                        boolean hFlip, boolean vFlip, boolean priority) {
        return new SpriteSatEntry(state.identity().priorityBucket(),
                new SpritePiece(x, y, width, height, Math.max(0, pattern), rawTile & 0x7FF,
                        palette, hFlip, vFlip, priority, false, SpriteMaskReplayRole.NORMAL,
                        0, width, 0, height, null));
    }

    private static PreparedPiece prepared(int x, int y, int pattern, int rawTile, int palette,
                                          boolean hFlip, boolean vFlip, boolean priority) {
        return new PreparedPiece(x, y, 1, 1, pattern, rawTile & 0x7FF,
                palette, hFlip, vFlip, priority, false, 0, 1);
    }
}
