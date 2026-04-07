package nl.amk.blocklookup.util;

import nl.amk.blocklookup.db.PlayerFilter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class PlayerFilters {

    private PlayerFilters() {
    }

    public static PlayerFilter parse(String raw) {
        if (raw == null) return PlayerFilter.any();
        String input = raw.trim();
        if (input.isEmpty() || input.equals("*")) return PlayerFilter.any();

        UUID uuid = tryParseUuid(input);
        if (uuid != null) return new PlayerFilter(uuid, null);

        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return new PlayerFilter(online.getUniqueId(), null);

        return new PlayerFilter(null, input);
    }

    private static UUID tryParseUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

