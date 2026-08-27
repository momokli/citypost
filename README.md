# CityPost

**One-way Item-Package-System (World 1/2 → City)** als Paper-Plugin.

Spieler auf den World-Servern (world1/world2) schicken Items per GUI-Backpack
in die City — dort werden die Pakete per GUI abgeholt. **Es gibt keinen
Rückkanal**: Nur SEND-Server schreiben Item-Daten in die Datenbank.

- **Paper 1.26.x** · **Java 25** (Quellcode Java-21-kompatibel) · **Gradle**
- **MariaDB/MySQL** (eine Tabelle: `city_packages`)
- Ein Plugin, zwei Modi — gesteuert über `config.yml` (`mode: SEND | RECEIVE`)

## Befehle

| Befehl | Seite | Funktion |
|---|---|---|
| `/citysend` | SEND (world1/world2) | Backpack-GUI (27 Slots). Items reinziehen → „Abschicken" → MariaDB-INSERT → „Paket unterwegs…" |
| `/citymail` | RECEIVE (city) | Paginiertes Postfach-GUI. Paket anklicken → Items ins Inventar → als abgeholt markiert |
| `/citypost selftest` | beide (Konsole/Op) | Diagnose: ItemCodec-Roundtrip + echter MariaDB-Pfad (INSERT → SELECT → Claim) |

Der jeweils andere Befehl wird auf einer Seite **komplett aus der CommandMap
entfernt** — auf dem City-Server existiert `/citysend` schlicht nicht.

Berechtigungen: `citypost.send` / `citypost.mail` (Default: `true`).

## Aufbau / Architektur

```
/citysend (SEND)                         /citymail (RECEIVE)
     │                                        │
  Backpack-GUI (27 Slots)               Postfach-GUI (paginiert, 36 Slots)
  Items → ItemCodec                     Poll alle 10 s (claimed=0) → Cache
  (ItemStack.serialize + Base64)        Klick auf Paket → Items ins Inventar
     │                                        │
     └──────────►  MariaDB  ◄─────────────────┘
                  city_packages
```

- **Senden**: Items aus dem Backpack serialisieren (Bukkit `ItemStack.serialize()`
  + Java-Serialisierung + Base64) → `INSERT` → GUI leeren. Bewahrt Meta inkl.
  Verzauberungen und Shulker-Inhalten. NBT wäre eine Cross-Version-robustere
  Alternative, ist hier aber nicht nötig (alle Server fahren dieselbe Paper-Version).
- **Empfang**: Scheduler pollt alle `poll-interval` Sekunden offene Pakete
  (`claimed = 0`, älteste zuerst) und hält sie im Cache. Das GUI zeigt pro Paket
  ein Chest-Item mit Lore: „3 Stacks · von world1 · vor 2h".
- **One-way garantiert**: `PackageDao` kennt nur `INSERT` (SEND), `findUnclaimed`
  und `markClaimed` (RECEIVE). Es gibt keinen Code-Pfad für Item-Daten zurück.
- **Anti-Spam**: `cooldown-seconds` (Default 30 s) pro Spieler zwischen zwei Paketen.
- **GUI**: Sender = Stardew-Stil (letzte Reihe: „Abschicken" [Emerald] + „Abbrechen"
  [Barrier], Abbruch/Close gibt Items zurück). Empfänger = paginiert mit
  Vor/Zurück/Aktualisieren-Navigation.

## Datenbank

Die Tabelle wird beim Plugin-Start automatisch angelegt (`CREATE TABLE IF NOT EXISTS`):

```sql
CREATE TABLE IF NOT EXISTS city_packages (
  id INT AUTO_INCREMENT PRIMARY KEY,
  player_uuid CHAR(36) NOT NULL,
  items_blob LONGBLOB NOT NULL,       -- serialisierte ItemStacks (Base64)
  world VARCHAR(32) NOT NULL,         -- Quelle: world1 / world2 (server-tag)
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  claimed TINYINT(1) DEFAULT 0
);
```

## Installation & Konfiguration

1. `gradle build` → JAR liegt unter `build/libs/citypost-1.0.0.jar`.
2. JAR nach `plugins/` kopieren, Server starten, `plugins/CityPost/config.yml` anpassen.
3. **SEND-Server** (world1/world2):
   ```yaml
   mode: SEND
   server-tag: world1        # world1 bzw. world2 — Quelle im DB-Feld "world"
   database:
     host: <db-host>
     port: 3306
     name: citypost
     user: citypost
     password: "…"
   ```
4. **RECEIVE-Server** (city): gleiche `database`-Werte, `mode: RECEIVE`.
5. MariaDB-Benutzer mit `CREATE`/`INSERT`/`SELECT`/`UPDATE` auf der DB `citypost`.

Weitere Optionen: `backpack-size`, `delivery-delay` (Sekunden, „Paket-Flair"),
`poll-interval`, `cooldown-seconds`, `language: de|en` (alle Strings in
`messages.yml`, MiniMessage-Format).

## Build & Tests

```bash
./gradlew build      # kompiliert gegen Paper 1.26.x (JDK 25) + führt Tests aus
```

Tests (JUnit 5):
- `ItemCodecTest` — Blob-Mechanik (Java-Serialisierung + Base64) auf Map-Ebene
- `PackageDaoTest` — DAO + SQL gegen H2 im MySQL-Modus (Schema-Validierung)
- `MessagesTest` — de/en-Defaults, Overrides, Fallbacks
- `PagerTest` — Paginierungs-Mathematik

Zusätzlich verifiziert auf einem echten Paper-1.26.2-Server + echter MariaDB
(Live-Test): Plugin-Load in beiden Modi, automatisches Anlegen der Tabelle,
`/citypost selftest` → `ItemCodec: PASS` + `DAO/MariaDB: PASS`, sowie
Command-Presence (`/citysend` nur SEND, `/citymail` nur RECEIVE).

Manueller Zwei-Server-Test: siehe [docs/TESTING.md](docs/TESTING.md).

## Bekannte Grenzen

- `delivery-delay > 0` hält die serialisierten Items bis zum INSERT im Speicher
  des SEND-Servers — ein Restart in diesem Fenster verwirft das Paket.
- Blobs werden für die Stack-Anzeige im Postfach beim Poll dekodiert; extrem
  große Pakete (viele Shulker) können den Poll minimal verzögern.
- Beim Abholen blockiert ein korruptes Paket (Blob nicht lesbar) die Queue nicht
  dauerhaft, erzeugt aber wiederholte Log-Warnungen bis zur manuellen Bereinigung.

## Lizenz

MIT — siehe [LICENSE](LICENSE).
