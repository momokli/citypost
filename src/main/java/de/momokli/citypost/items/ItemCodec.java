package de.momokli.citypost.items;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Item-Serialisierung: Bukkit {@link ItemStack#serialize()} → Java-Serialisierung → Base64.
 *
 * <p>Bewahrt Item-Meta (Name, Lore, Verzauberungen, Stacks) inklusive
 * BlockEntity-Daten (z.B. Shulker-Inhalte). NBT wäre eine robustere Alternative
 * für Cross-Version-Kompatibilität, ist hier aber nicht nötig, da alle
 * beteiligten Server dieselbe Paper-Version fahren.</p>
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(List<ItemStack> items) {
        List<Map<String, Object>> serialized = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            serialized.add(item.serialize());
        }
        return encodeMaps(serialized);
    }

    /** Serialisiert bereits erzeugte {@code ItemStack.serialize()}-Maps (testbar ohne Server). */
    static String encodeMaps(List<Map<String, Object>> serialized) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new ArrayList<>(serialized));
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new ItemCodecException("Items konnten nicht serialisiert werden", e);
        }
    }

    public static List<ItemStack> decode(String blob) {
        List<Map<String, Object>> serialized = decodeMaps(blob);
        try {
            List<ItemStack> items = new ArrayList<>(serialized.size());
            for (Map<String, Object> map : serialized) {
                items.add(ItemStack.deserialize(map));
            }
            return items;
        } catch (RuntimeException e) {
            throw new ItemCodecException("Items konnten nicht deserialisiert werden", e);
        }
    }

    /** Liest die serialisierten Maps zurück (testbar ohne Server). */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> decodeMaps(String blob) {
        try {
            byte[] data = Base64.getDecoder().decode(blob);
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
                Object raw = in.readObject();
                if (!(raw instanceof List)) {
                    throw new ItemCodecException("Unerwartetes Blob-Format");
                }
                return (List<Map<String, Object>>) raw;
            }
        } catch (IllegalArgumentException e) {
            throw new ItemCodecException("Ungültiges Base64 im Paket-Blob", e);
        } catch (IOException | ClassNotFoundException e) {
            throw new ItemCodecException("Items konnten nicht deserialisiert werden", e);
        }
    }

    public static final class ItemCodecException extends RuntimeException {
        public ItemCodecException(String message) {
            super(message);
        }

        public ItemCodecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
