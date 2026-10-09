package de.emrion.kaleidoscopeCookery;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Lädt das mitgelieferte Datapack (Verzauberung "Schnelles Messer" aus Kaleidoscope Cookery).
 * Die Verzauberung hängt technisch an den Schwertern, auf denen die Küchenmesser basieren;
 * {@link de.emrion.kaleidoscopeCookery.item.QuickKnifeListener} hält sie von normalen Schwertern fern.
 */
@SuppressWarnings("UnstableApiUsage")
public class KaleidoscopeCookeryBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                URI uri = Objects.requireNonNull(KaleidoscopeCookeryBootstrap.class.getResource("/datapack")).toURI();
                event.registrar().discoverPack(uri, "kaleidoscope_cookery", configurer -> configurer.autoEnableOnServerStart(true));
            } catch (URISyntaxException | IOException e) {
                context.getLogger().error("Datapack konnte nicht geladen werden", e);
            }
        });
    }
}
