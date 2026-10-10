package de.emrion.kaleidoscopeCookery.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Übersetzungen: Alle Texte sind Übersetzungs-Keys. Die Sprachdateien liegen im Resourcepack
 * (der Client übersetzt damit z. B. Item-Namen) und werden zusätzlich serverseitig beim
 * GlobalTranslator registriert, damit Chat, Actionbar und Konsole auch ohne Pack lesbar sind.
 * Englisch ist Fallback für fehlende Keys.
 */
public final class Lang {

    private static final Pattern LANG_FILE = Pattern.compile("assets/[a-z0-9_.-]+/lang/([a-z_]+)\\.json");
    private static final Map<String, Locale> LANGUAGES = Map.of("en_us", Locale.US, "de_de", Locale.GERMANY);
    private static final Pattern MINECRAFT_ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?[sd]");

    private static TranslationStore.StringBased<MessageFormat> store;

    private Lang() {
    }

    /**
     * Lädt die Sprachdateien aus dem Resourcepack-Zip.
     */
    public static void load(ZipFile pack, Logger logger) throws IOException {
        unload();
        store = TranslationStore.messageFormat(Key.key(Keys.NAMESPACE, "lang"));
        store.defaultLocale(Locale.US);
        GlobalTranslator.translator().addSource(store);
        add(pack, logger);
    }

    /**
     * Sprachdateien eines weiteren Packs (Addon) dazunehmen; schon bekannte Schlüssel bleiben.
     */
    public static void add(ZipFile pack, Logger logger) throws IOException {
        int count = 0;
        for (ZipEntry entry : java.util.Collections.list(pack.entries())) {
            Matcher name = LANG_FILE.matcher(entry.getName());
            Locale locale = name.matches() ? LANGUAGES.get(name.group(1)) : null;
            if (locale == null) {
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(pack.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (String key : json.keySet()) {
                    if (!store.contains(key, locale)) {
                        store.register(key, locale, toMessageFormat(json.get(key).getAsString(), locale));
                        count++;
                    }
                }
            }
        }
        logger.info(count + " Übersetzungen geladen");
    }

    public static void unload() {
        if (store != null) {
            GlobalTranslator.translator().removeSource(store);
            store = null;
        }
    }

    /**
     * Minecraft-Platzhalter (%s, %1$s) in MessageFormat ({0}) umwandeln.
     */
    static MessageFormat toMessageFormat(String text, Locale locale) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = MINECRAFT_ARGUMENT.matcher(text);
        int next = 0;
        int last = 0;
        while (matcher.find()) {
            result.append(escape(text.substring(last, matcher.start())));
            int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
            result.append('{').append(index).append('}');
            last = matcher.end();
        }
        result.append(escape(text.substring(last)));
        return new MessageFormat(result.toString().replace("%%", "%"), locale);
    }

    private static String escape(String text) {
        return text.replace("'", "''").replace("{", "'{'").replace("}", "'}'");
    }

    /**
     * Übersetzbarer Text aus dem Plugin-Namespace (kaleidoscope.&lt;key&gt;).
     */
    public static Component get(String key, Object... args) {
        return Component.translatable(Keys.NAMESPACE + "." + key, arguments(args));
    }

    /**
     * Übersetzbarer Text mit vollem Key (z. B. aus den Mod-Sprachdateien).
     */
    public static Component raw(String key, Object... args) {
        return Component.translatable(key, arguments(args));
    }

    private static ComponentLike[] arguments(Object[] args) {
        ComponentLike[] result = new ComponentLike[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = args[i] instanceof ComponentLike component ? component : Component.text(String.valueOf(args[i]));
        }
        return result;
    }

    /**
     * Text für die Suche: in der Sprache des Spielers gerendert, ohne Formatierung (unbekannte Schlüssel bleiben stehen).
     */
    public static String plain(Component component, Locale locale) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(net.kyori.adventure.translation.GlobalTranslator.render(component, locale));
    }
}
