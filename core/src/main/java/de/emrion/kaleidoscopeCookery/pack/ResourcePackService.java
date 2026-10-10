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
 * oder über eine externe URL an die Spieler geschickt. Addons (eigene Plugins wie KaleidoscopeGrilling)
 * bringen ein eigenes Pack mit, das zusammen mit diesem geschickt wird.
 */
public class ResourcePackService implements Listener {

    /** Ein Pack: Datei im Plugin-Verzeichnis, SHA1 aus dem Jar und optional eine externe URL */
    private record Pack(UUID id, Path path, String sha1, String externalUrl) {
    }

    private final Plugin plugin;
    /** Cookery zuerst, danach die Addons (spätere Packs liegen beim Client oben) */
    private final java.util.List<Pack> packs = new java.util.concurrent.CopyOnWriteArrayList<>();
    private ResourcePackSettings settings;
    private HttpServer server;

    public ResourcePackService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Pack des Plugins (&lt;Name&gt;-Pack.zip) aus dessen Jar entpacken. Muss vor {@link Lang#load} laufen, die
     * Sprachdateien kommen aus dem Pack.
     *
     * @param externalUrl externe Download-URL dieses Packs; leer = eingebauter Webserver
     */
    public Path extract(Plugin owner, String externalUrl) throws IOException {
        String file = owner.getName() + "-Pack.zip";
        Path path = owner.getDataPath().resolve(file);
        Files.createDirectories(path.getParent());
        try (InputStream in = owner.getResource(file); InputStream hash = owner.getResource(file + ".sha1")) {
            if (in == null || hash == null) {
                throw new IOException(file + " fehlt im Plugin-Jar");
            }
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            String sha1 = new String(hash.readAllBytes(), StandardCharsets.US_ASCII).trim();
            UUID id = UUID.nameUUIDFromBytes(file.getBytes(StandardCharsets.UTF_8));
            packs.removeIf(pack -> pack.id().equals(id));
            packs.add(new Pack(id, path, sha1, externalUrl == null ? "" : externalUrl));
        }
        if (settings != null) {
            startServerIfNeeded();
        }
        return path;
    }

    public void start(ResourcePackSettings settings) {
        this.settings = settings;
        startServerIfNeeded();
    }

    /** Eingebauter Webserver nur, wenn ein Pack keine externe URL hat */
    private void startServerIfNeeded() {
        if (settings == null || !settings.enabled() || server != null
                || packs.stream().noneMatch(pack -> pack == packs.getFirst() ? settings.externalUrl().isBlank() : pack.externalUrl().isBlank())) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(settings.bindAddress(), settings.port()), 0);
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newSingleThreadExecutor(Thread.ofPlatform().name("KaleidoscopeCookery-Pack").daemon().factory()));
            server.start();
            plugin.getLogger().info("Resourcepacks ohne externe URL werden über Port " + settings.port() + " ausgeliefert");
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
        if (old != null && (old.port() != settings.port() || !old.bindAddress().equals(settings.bindAddress()))) {
            plugin.getLogger().warning("Geänderte Webserver-Einstellungen werden erst nach einem Neustart aktiv");
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String requested = exchange.getRequestURI().getPath();
            Pack pack = packs.stream().filter(p -> requested.equals("/" + p.sha1() + ".zip")).findFirst().orElse(null);
            if (!"GET".equals(exchange.getRequestMethod()) || pack == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, Files.size(pack.path()));
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(pack.path(), out);
            }
        }
    }

    private String url(Pack pack) {
        if (!pack.externalUrl().isBlank()) {
            return pack.externalUrl();
        }
        return "http://" + settings.publicHost() + ":" + settings.port() + "/" + pack.sha1() + ".zip";
    }

    public void send(Player player) {
        if (settings == null || !settings.enabled() || packs.isEmpty()) {
            return;
        }
        // Cookery-Pack mit der URL aus der eigenen config.yml (bei /kc reload änderbar)
        java.util.List<ResourcePackInfo> infos = new java.util.ArrayList<>();
        for (Pack pack : packs) {
            String url = pack == packs.getFirst() && !settings.externalUrl().isBlank() ? settings.externalUrl() : url(pack);
            infos.add(ResourcePackInfo.resourcePackInfo(pack.id(), URI.create(url), pack.sha1()));
        }
        // Der zuerst geschickte Pack liegt oben: Addons zuerst, damit ihre Dateien (z. B. Ölkanne mit Ölsorten) gewinnen
        java.util.Collections.reverse(infos);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(infos)
                .required(settings.required())
                .replace(false)
                .prompt(Lang.get("pack.prompt"))
                .build());
    }

    /** SHA1 des Cookery-Packs */
    public String getSha1() {
        return packs.isEmpty() ? null : packs.getFirst().sha1();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (packs.stream().noneMatch(pack -> pack.id().equals(event.getID())) || settings == null || !settings.required()) {
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
