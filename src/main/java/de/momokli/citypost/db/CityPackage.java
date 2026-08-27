package de.momokli.citypost.db;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine Zeile aus {@code city_packages}.
 *
 * @param id         Auto-Increment-Primärschlüssel
 * @param playerUuid Absender-UUID
 * @param itemsBlob  serialisierte ItemStacks (Base64)
 * @param world      Quelle (world1 / world2, konfigurierbar via server-tag)
 * @param createdAt  Ankunftszeit in der DB
 * @param claimed    abgeholt (nur RECEIVE-Seite setzt dieses Flag)
 */
public record CityPackage(
        int id,
        UUID playerUuid,
        String itemsBlob,
        String world,
        Instant createdAt,
        boolean claimed
) {
}
