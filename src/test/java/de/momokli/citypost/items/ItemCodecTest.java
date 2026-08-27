package de.momokli.citypost.items;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testet die Blob-Mechanik (Java-Serialisierung + Base64) auf Map-Ebene.
 *
 * <p>Echte {@link org.bukkit.inventory.ItemStack}-Roundtrips sind ohne laufenden
 * Server nicht möglich (Paper 1.26 braucht eine RegistryAccess) — das übernimmt
 * der Live-Selftest ({@code /citypost selftest}) auf einem echten Paper-Server.</p>
 */
class ItemCodecTest {

    @Test
    void mapRoundTripPreservesEntries() {
        List<Map<String, Object>> maps = new ArrayList<>();
        maps.add(Map.of("type", "DIAMOND"));
        Map<String, Object> stone = new LinkedHashMap<>();
        stone.put("type", "STONE");
        stone.put("amount", 64);
        maps.add(stone);

        String blob = ItemCodec.encodeMaps(maps);

        assertEquals(maps, ItemCodec.decodeMaps(blob));
    }

    @Test
    void nestedMetaSurvivesRoundTrip() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("meta-type", "UNSPECIFIC");
        meta.put("display-name", "§bSelftest");
        meta.put("lore", List.of("Zeile 1", Map.of("komplex", 42)));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "DIAMOND_SWORD");
        item.put("amount", 1);
        item.put("meta", meta);

        List<Map<String, Object>> decoded = ItemCodec.decodeMaps(ItemCodec.encodeMaps(List.of(item)));

        assertEquals(item, decoded.get(0));
        assertEquals("§bSelftest", ((Map<?, ?>) decoded.get(0).get("meta")).get("display-name"));
    }

    @Test
    void emptyListRoundTrips() {
        assertEquals(List.of(), ItemCodec.decodeMaps(ItemCodec.encodeMaps(List.of())));
    }

    @Test
    void invalidBase64Throws() {
        assertThrows(ItemCodec.ItemCodecException.class, () -> ItemCodec.decode("kein-base64!!"));
    }

    @Test
    void validBase64WithGarbageThrows() {
        String garbage = Base64.getEncoder().encodeToString("kein-java-serialisiertes-object".getBytes());
        assertThrows(ItemCodec.ItemCodecException.class, () -> ItemCodec.decode(garbage));
    }

    @Test
    void wrongBlobShapeThrows() {
        // Base64 eines leeren Java-Strings (kein List-Objekt)
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
            out.writeObject("hallo");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        String blob = Base64.getEncoder().encodeToString(bytes.toByteArray());
        assertThrows(ItemCodec.ItemCodecException.class, () -> ItemCodec.decode(blob));
    }
}
