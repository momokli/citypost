package de.momokli.citypost.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Lädt alle User-Strings aus {@code messages.yml} (überschreibbar) mit
 * Fallback auf die gebündelte Sprachdatei ({@code messages-<lang>.yml}).
 */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Map<String, Object> overrides;
    private final Map<String, Object> fallback;

    private Messages(Map<String, Object> overrides, Map<String, Object> fallback) {
        this.overrides = overrides;
        this.fallback = fallback;
    }

    public static Messages load(Path dataFolder, String language, ClassLoader classLoader, Logger logger) {
        String lang = (language == null || language.isBlank()) ? "de" : language.toLowerCase();

        Path messagesFile = dataFolder.resolve("messages.yml");
        try {
            if (!Files.exists(messagesFile)) {
                Files.createDirectories(dataFolder);
                InputStream in = classLoader.getResourceAsStream("messages-" + lang + ".yml");
                if (in == null) {
                    logger.warning("Sprachdatei messages-" + lang + ".yml nicht gefunden, verwende Deutsch.");
                    in = classLoader.getResourceAsStream("messages-de.yml");
                }
                if (in != null) {
                    try (InputStream is = in) {
                        Files.copy(is, messagesFile);
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("messages.yml konnte nicht erstellt werden: " + e.getMessage());
        }

        YamlConfiguration overrideCfg = new YamlConfiguration();
        if (Files.exists(messagesFile)) {
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(messagesFile), StandardCharsets.UTF_8)) {
                overrideCfg.load(reader);
            } catch (Exception e) {
                logger.warning("messages.yml konnte nicht geladen werden: " + e.getMessage());
            }
        }

        YamlConfiguration fallbackCfg = new YamlConfiguration();
        InputStream fallbackIn = classLoader.getResourceAsStream("messages-" + lang + ".yml");
        if (fallbackIn == null) {
            fallbackIn = classLoader.getResourceAsStream("messages-de.yml");
        }
        if (fallbackIn != null) {
            try (InputStreamReader reader = new InputStreamReader(fallbackIn, StandardCharsets.UTF_8)) {
                fallbackCfg.load(reader);
            } catch (Exception e) {
                logger.warning("Fallback-Messages konnten nicht geladen werden: " + e.getMessage());
            }
        }

        return new Messages(overrideCfg.getValues(true), fallbackCfg.getValues(true));
    }

    /** Roh-String (MiniMessage-Format) inkl. Fallback-Kette. */
    public String raw(String key) {
        Object override = overrides.get(key);
        if (override instanceof String s && !s.isEmpty()) {
            return s;
        }
        Object fallbackValue = fallback.get(key);
        if (fallbackValue instanceof String s && !s.isEmpty()) {
            return s;
        }
        return "<red>Unbekannte Message: " + key;
    }

    /** Roh-String mit %placeholder%-Ersetzung. */
    public String format(String key, Object... placeholders) {
        String message = raw(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
        }
        return message;
    }

    /** Fertige Adventure-Component (MiniMessage geparst) mit %placeholder%-Ersetzung. */
    public Component component(String key, Object... placeholders) {
        return MINI_MESSAGE.deserialize(format(key, placeholders));
    }
}
