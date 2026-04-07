package nl.amk.blocklookup.config;

import org.bukkit.configuration.file.FileConfiguration;

public record Settings(
        String databaseFilename,
        int databaseBusyTimeoutMs,
        int writeBatchSize,
        int flushIntervalTicks,
        int defaultRadius,
        int maxActionsPerCommand,
        int applyPerTick,
        boolean loadChunks,
        int lookupDefaultMinutes,
        int lookupDefaultLimit
) {

    public static Settings load(FileConfiguration config) {
        String databaseFilename = config.getString("database.filename", "blocklookup.db");
        int busyTimeoutMs = clamp(config.getInt("database.busyTimeoutMs", 5000), 250, 60000);
        int writeBatchSize = clamp(config.getInt("database.writeBatchSize", 250), 1, 5000);
        int flushIntervalTicks = clamp(config.getInt("database.flushIntervalTicks", 20), 1, 200);

        int defaultRadius = clamp(config.getInt("rollback.defaultRadius", 15), 0, 500);
        int maxActions = clamp(config.getInt("rollback.maxActionsPerCommand", 20000), 1, 200000);
        int applyPerTick = clamp(config.getInt("rollback.applyPerTick", 200), 1, 10000);
        boolean loadChunks = config.getBoolean("rollback.loadChunks", false);

        int lookupMinutes = clamp(config.getInt("lookup.defaultMinutes", 30), 1, 525600);
        int lookupLimit = clamp(config.getInt("lookup.defaultLimit", 20), 1, 200);

        return new Settings(
                databaseFilename,
                busyTimeoutMs,
                writeBatchSize,
                flushIntervalTicks,
                defaultRadius,
                maxActions,
                applyPerTick,
                loadChunks,
                lookupMinutes,
                lookupLimit
        );
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}

