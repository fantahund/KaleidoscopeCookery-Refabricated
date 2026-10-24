package de.emrion.kaleidoscopeCookery.pack;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The server resource pack: lives in the plugin jar (created during the build, see tools/build_pack.py) and is
 * extracted into the plugin folder, where the language files and cookbook images are read from. Sending the packs
 * to the players is done by the separate KaleidoscopePack plugin (Paper or Velocity).
 */
public final class ResourcePackService {

    private ResourcePackService() {
    }

    /**
     * Extract a plugin's pack (&lt;Name&gt;-Pack.zip) from its jar. Must run before {@link de.emrion.kaleidoscopeCookery.util.Lang#load},
     * the language files come from the pack.
     */
    public static Path extract(Plugin owner) throws IOException {
        String file = owner.getName() + "-Pack.zip";
        Path path = owner.getDataPath().resolve(file);
        Files.createDirectories(path.getParent());
        try (InputStream in = owner.getResource(file)) {
            if (in == null) {
                throw new IOException(file + " fehlt im Plugin-Jar");
            }
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        }
        return path;
    }
}
