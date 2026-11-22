package com.openggf.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Shared stock game codes and media kinds. This catalogue has no runtime dependencies. */
public final class StockGameCatalog {
    public enum Game {
        S1(true),
        S2(true),
        S3K(true),
        SCD(false);

        private final String code = name().toLowerCase(Locale.ROOT);
        private final boolean cartridge;

        Game(boolean cartridge) {
            this.cartridge = cartridge;
        }

        public String code() {
            return code;
        }
    }

    private static final List<String> STOCK_CODES = Arrays.stream(Game.values())
            .map(Game::code).toList();
    private static final List<String> CARTRIDGE_CODES = Arrays.stream(Game.values())
            .filter(game -> game.cartridge).map(Game::code).toList();

    private StockGameCatalog() {
    }

    public static List<String> stockCodes() {
        return STOCK_CODES;
    }

    public static List<String> cartridgeCodes() {
        return CARTRIDGE_CODES;
    }

    public static boolean isStockCode(String code) {
        return code != null && STOCK_CODES.contains(code);
    }

    public static boolean isCartridgeCode(String code) {
        return code != null && CARTRIDGE_CODES.contains(code);
    }
}
