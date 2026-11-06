package com.openggf.i18n;

import java.nio.file.Path;

/** Offline authoring validation using exactly the production JSON and ICU boundaries. */
public final class CatalogTool {
    private CatalogTool() { }

    public static void main(String[] args) throws Exception {
        CatalogRepository catalogs = args.length == 0 ? CatalogRepository.bundled()
                : CatalogRepository.directory(Path.of(args[0]));
        System.out.println("Validated " + catalogs.ids().size() + " source messages and "
                + catalogs.languages().size() + " locale catalogs using the runtime ICU parser.");
        for (UiLanguage language : catalogs.languages())
            System.out.println(language.tag() + ": " + language.translated() + "/" + language.total()
                    + " current entries, " + language.stale() + " stale; " + language.quality());
    }
}
