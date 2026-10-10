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
 * Translations: all texts are translation keys. The language files are in the resource pack
 * (the client uses them to translate e.g. item names) and are also registered server-side with the
 * GlobalTranslator so that chat, action bar and console are readable even without the pack.
 * English is the fallback for missing keys.
 */
public final class Lang {

    private static final Pattern LANG_FILE = Pattern.compile("assets/[a-z0-9_.-]+/lang/([a-z_]+)\\.json");
    private static final Map<String, Locale> LANGUAGES = Map.of("en_us", Locale.US, "de_de", Locale.GERMANY);
    private static final Pattern MINECRAFT_ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?[sd]");

    private static TranslationStore.StringBased<MessageFormat> store;

    private Lang() {
    }

    /**
     * Loads the language files from the resource pack zip.
     */
    public static void load(ZipFile pack, Logger logger) throws IOException {
        unload();
        store = TranslationStore.messageFormat(Key.key(Keys.NAMESPACE, "lang"));
        store.defaultLocale(Locale.US);
        GlobalTranslator.translator().addSource(store);
        add(pack, logger);
    }

    /**
     * Add the language files of another pack (addon); keys that are already known stay.
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
     * Convert Minecraft placeholders (%s, %1$s) to MessageFormat ({0}).
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
     * Translatable text from the plugin namespace (kaleidoscope.&lt;key&gt;).
     */
    public static Component get(String key, Object... args) {
        return Component.translatable(Keys.NAMESPACE + "." + key, arguments(args));
    }

    /**
     * Translatable text with a full key (e.g. from the mod language files).
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
     * Text for searching: rendered in the player's language, without formatting (unknown keys stay as they are).
     */
    public static String plain(Component component, Locale locale) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(net.kyori.adventure.translation.GlobalTranslator.render(component, locale));
    }
}
