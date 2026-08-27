package de.momokli.citypost.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir
    Path tempDir;

    @Test
    void germanDefaults() throws IOException {
        Messages messages = Messages.load(tempDir, "de", getClass().getClassLoader(), LOGGER);

        assertEquals("<green>Abschicken", messages.raw("gui.backpack.send"));
        assertEquals("<gray>gerade eben", messages.raw("msg.age.now"));
        assertEquals("<green>Paket unterwegs… (ID #42)", messages.format("msg.send.on-way", "id", 42));
        assertTrue(Files.exists(tempDir.resolve("messages.yml")));
    }

    @Test
    void englishDefaults() throws IOException {
        Messages messages = Messages.load(tempDir, "en", getClass().getClassLoader(), LOGGER);

        assertEquals("<green>Send", messages.raw("gui.backpack.send"));
        assertEquals("<green>Package #7 claimed!", messages.format("msg.mail.claimed", "id", 7));
    }

    @Test
    void userOverridesTakePrecedence() throws IOException {
        Files.writeString(tempDir.resolve("messages.yml"),
                "gui:\n  backpack:\n    send: \"<green>Raus damit!\"\n",
                StandardCharsets.UTF_8);

        Messages messages = Messages.load(tempDir, "de", getClass().getClassLoader(), LOGGER);

        assertEquals("<green>Raus damit!", messages.raw("gui.backpack.send"));
        // Nicht überschriebene Keys fallen auf die Defaults zurück
        assertEquals("<red>Abbrechen", messages.raw("gui.backpack.cancel"));
    }

    @Test
    void unknownKeyFallsBackToPlaceholder() {
        Messages messages = Messages.load(tempDir, "de", getClass().getClassLoader(), LOGGER);
        assertTrue(messages.raw("does.not.exist").contains("Unbekannte Message"));
    }
}
