package de.momokli.citypost;

/**
 * Betriebsmodus des Plugins, konfiguriert per {@code config.yml} ({@code mode}).
 */
public enum CityPostMode {
    /** World-Server (world1/world2): schreibt Pakete, stellt {@code /citysend} bereit. */
    SEND,
    /** City-Server: liest Pakete, stellt {@code /citymail} bereit. */
    RECEIVE;

    public static CityPostMode fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
