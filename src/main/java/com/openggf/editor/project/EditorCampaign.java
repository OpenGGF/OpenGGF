package com.openggf.editor.project;

import com.openggf.io.ModAssetRoot;
import com.openggf.mods.code.ModCampaignDefinition;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Saved ordered project references, relative to the containing project's own directory. */
public record EditorCampaign(List<String> projectPaths) {
    public static final EditorCampaign EMPTY = new EditorCampaign(List.of());

    public EditorCampaign {
        projectPaths = List.copyOf(projectPaths);
        if (projectPaths.size() > ModCampaignDefinition.MAX_LEVELS)
            throw new IllegalArgumentException("Campaign supports at most 256 projects");
        var distinct = new HashSet<String>();
        for (String path : projectPaths) {
            ModAssetRoot.requireNormalizedEntry(path);
            if (!path.endsWith(".ogfproject") || path.length() > 512 || !distinct.add(path))
                throw new IllegalArgumentException("Campaign paths must be unique bounded relative .ogfproject files");
        }
    }

    /** An unlinked project still exports as a one-act campaign. */
    public List<Path> resolve(Path savedProjectFile) {
        Path current = Objects.requireNonNull(savedProjectFile, "Save the project before exporting its campaign")
                .toAbsolutePath().normalize();
        if (projectPaths.isEmpty()) return List.of(current);
        Path parent = Objects.requireNonNull(current.getParent(), "Project parent");
        return projectPaths.stream().map(parent::resolve).map(Path::normalize).toList();
    }
}
