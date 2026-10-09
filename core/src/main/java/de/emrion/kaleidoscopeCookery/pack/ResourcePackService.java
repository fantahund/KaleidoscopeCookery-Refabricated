package de.emrion.kaleidoscopeCookery.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.emrion.kaleidoscopeCookery.config.CookeryConfig.ResourcePackSettings;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Das Server-Resourcepack: liegt im Plugin-Jar (beim Bauen erzeugt, siehe tools/build_pack.py),
 * wird ins Plugin-Verzeichnis entpackt und entweder über einen kleinen eingebauten Webserver
 * oder über eine externe URL an die Spieler geschickt.
 */
public class ResourcePackService implements Listener {

    public static final String PACK_FILE = "KaleidoscopeCookery-Pack.zip";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("kaleidoscope-cookery-pack".getBytes(StandardCharsets.UTF_8));

    private final Plugin plugin;
    private final Path packPath;
    private ResourcePackSettings settings;
    private String sha1;
    private HttpServer server;

    public ResourcePackService(Plugin plugin) {
        this.plugin = plugin;
        this.packPath = plugin.getDataPath().resolve(PACK_FILE);
    }

    /**
     * Pack aus dem Jar entpacken. Muss vor {@link Lang#load} laufen, die Sprachdateien kommen aus dem Pack.
     */
    public Path extract() throws IOException {
        Files.createDirectories(packPath.getParent());
        try (InputStream in = plugin.getResource(PACK_FILE); InputStream hash = plugin.getResource(PACK_FILE + ".sha1")) {
            if (in == null || hash == null) {
                throw new IOException(PACK_FILE + " fehlt im Plugin-Jar");
            }
            Files.copy(in, packPath, StandardCopyOption.REPLACE_EXISTING);
            sha1 = new String(hash.readAllBytes(), StandardCharsets.US_ASCII).trim();
        }
        return packPath;
    }

    public void start(ResourcePackSettings settings) {
        this.settings = settings;
        if (!settings.enabled() || !settings.externalUrl().isBlank()) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(settings.bindAddress(), settings.port()), 0);
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newSingleThreadExecutor(Thread.ofPlatform().name("KaleidoscopeCookery-Pack").daemon().factory()));
            server.start();
            plugin.getLogger().info("Resourcepack wird unter " + url() + " ausgeliefert (SHA1 " + sha1 + ")");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Webserver für das Resourcepack konnte nicht starten (Port " + settings.port() + ")", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
            server = null;
        }
    }

    /**
     * Neue Einstellungen nach /kc reload übernehmen (Webserver-Adresse/-Port erst nach Neustart).
     */
    public void updateSettings(ResourcePackSettings settings) {
        ResourcePackSettings old = this.settings;
        this.settings = settings;
        if (old != null && (old.port() != settings.port() || !old.bindAddress().equals(settings.bindAddress()) || old.externalUrl().isBlank() != settings.externalUrl().isBlank())) {
            plugin.getLogger().warning("Geänderte Webserver-Einstellungen werden erst nach einem Neustart aktiv");
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod()) || !exchange.getRequestURI().getPath().equals("/" + sha1 + ".zip")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, Files.size(packPath));
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(packPath, out);
            }
        }
    }

    private String url() {
        if (!settings.externalUrl().isBlank()) {
            return settings.externalUrl();
        }
        return "http://" + settings.publicHost() + ":" + settings.port() + "/" + sha1 + ".zip";
    }

    public void send(Player player) {
        if (settings == null || !settings.enabled() || sha1 == null) {
            return;
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url()), sha1);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(settings.required())
                .replace(false)
                .prompt(Lang.get("pack.prompt"))
                .build());
    }

    public String getSha1() {
        return sha1;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID()) || settings == null || !settings.required()) {
            return;
        }
        switch (event.getStatus()) {
            case DECLINED -> event.getPlayer().kick(Lang.get("pack.declined"));
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> event.getPlayer().kick(Lang.get("pack.failed"));
            default -> {
            }
        }
    }
}
