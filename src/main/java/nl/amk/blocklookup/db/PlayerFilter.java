package nl.amk.blocklookup.db;

import java.util.UUID;

public record PlayerFilter(UUID uuid, String name) {

    public static PlayerFilter any() {
        return new PlayerFilter(null, null);
    }

    public boolean isAny() {
        return uuid == null && (name == null || name.isBlank());
    }
}

