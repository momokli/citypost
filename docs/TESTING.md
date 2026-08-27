# CityPost — manueller Testplan (2 Paper-Server + MariaDB)

Ziel: One-way-Paketfluss World 1/2 → City verifizieren, inkl. Item-Integrität
(Stack-Größen, verzauberte Items, Shulker) und „kein Rückkanal".

## Vorbereitung

1. **MariaDB** (Docker oder lokal):
   ```bash
   docker run -d --name citypost-db -e MARIADB_DATABASE=citypost \
     -e MARIADB_USER=citypost -e MARIADB_PASSWORD=citypost \
     -e MARIADB_ROOT_PASSWORD=root -p 3306:3306 mariadb:11
   ```
2. **Zwei Paper-Server** (1.26.x, Java 25), jeweils `plugins/citypost-1.0.0.jar`.
3. **SEND-Server** (`plugins/CityPost/config.yml`):
   ```yaml
   mode: SEND
   server-tag: world1
   database: { host: localhost, port: 3306, name: citypost, user: citypost, password: citypost }
   ```
4. **RECEIVE-Server** (`plugins/CityPost/config.yml`): identisch, nur `mode: RECEIVE`.

## Testfälle

### 1. Senden

- Als Spieler auf dem SEND-Server `/citysend` ausführen.
- Backpack-GUI (27 Slots) öffnet sich; letzte Reihe: „Abschicken" + „Abbrechen".
- Items in die oberen Slots ziehen (Testset: 64× Stein, verzaubertes
  Diamantschwert, Shulker-Box mit Inhalt, benanntes Item mit Lore).
- „Abschicken" klicken → „Paket unterwegs… (ID #n)" → GUI schließt sich,
  Items sind weg.
- Kontrolle: `SELECT id, world, LENGTH(items_blob) FROM city_packages WHERE claimed=0;`
  → Zeile mit `world='world1'`, Blob nicht leer.

### 2. Ankunft & Abholen

- Auf dem RECEIVE-Server (City) `/citymail` ausführen.
- Paket erscheint als Chest-Item mit Lore „3 Stacks · von world1 · vor X".
- Paket anklicken → „Paket #n abgeholt!" → Items im Inventar.
- **Integrität prüfen**: Stack-Größen (64 Stein), Verzauberung (Sharpness V),
  Shulker-Inhalt, Name/Lore identisch zum Absender.
- `SELECT claimed FROM city_packages WHERE id=n;` → `1`.

### 3. Pagination & Refresh

- ≥ 28 Pakete erzeugen → zweite Seite über „Nächste Seite →".
- „Aktualisieren"-Button lädt neue Pakete ohne Neulogin.

### 4. Randfälle

- **Leeres Backpack** → „Keine Items im Backpack!".
- **Cooldown**: zweites `/citysend` direkt nach dem Senden → Cooldown-Message.
- **Abbrechen / GUI schließen** → Items landen wieder im Inventar.
- **Inventar voll beim Abholen** → Overflow wird am Spieler gedroppt + Hinweis.
- **Doppelklick** auf ein Paket → nur eine Auslieferung (Claim-Flag-Guard).

### 5. Kein Rückkanal

- Auf dem RECEIVE-Server: `/citysend` → „Unknown command" (nicht registriert).
- Auf dem SEND-Server: `/citymail` → „Unknown command".
- DB-Log: RECEIVE-Server führt ausschließlich `SELECT` + `UPDATE claimed` aus
  (general_log prüfen), keine `INSERT`s auf `city_packages`.

### 6. Selftest (ohne Spieler)

- Konsole: `/citypost selftest` → `ItemCodec: PASS` (echter Item-Roundtrip mit
  verzaubertem Item) und `DAO/MariaDB: PASS` (Paket wird angelegt, gefunden,
  als abgeholt markiert — anschließend in der DB aufgeräumt via `claimed=1`).

### 7. Modus-Fehlerkonfiguration

- `mode: BOGUS` in `config.yml` → Plugin deaktiviert sich mit klarer
  Log-Meldung.
- DB nicht erreichbar → Plugin deaktiviert sich mit „Datenbank-Initialisierung
  fehlgeschlagen".
