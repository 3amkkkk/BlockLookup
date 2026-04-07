package nl.amk.blocklookup;

import nl.amk.blocklookup.command.BlockLookupCommand;
import nl.amk.blocklookup.config.Settings;
import nl.amk.blocklookup.db.DatabaseWriter;
import nl.amk.blocklookup.db.EventQueryService;
import nl.amk.blocklookup.db.SqliteDatabase;
import nl.amk.blocklookup.listener.BlockLoggingListener;
import nl.amk.blocklookup.listener.ChatLoggingListener;
import nl.amk.blocklookup.rollback.RollbackService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;

public final class BlockLookup extends JavaPlugin {

    private Settings settings;
    private SqliteDatabase database;
    private DatabaseWriter writer;
    private EventQueryService queryService;
    private RollbackService rollbackService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create plugin folder: " + getDataFolder().getAbsolutePath());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        database = new SqliteDatabase(new File(getDataFolder(), settings.databaseFilename()), settings.databaseBusyTimeoutMs());
        database.initialize();

        writer = new DatabaseWriter(getLogger(), database, settings.writeBatchSize(), settings.flushIntervalTicks());
        writer.start();

        queryService = new EventQueryService(database);
        rollbackService = new RollbackService(this, queryService, settings);

        getServer().getPluginManager().registerEvents(new BlockLoggingListener(writer), this);
        getServer().getPluginManager().registerEvents(new ChatLoggingListener(writer), this);

        BlockLookupCommand executor = new BlockLookupCommand(this, settings, queryService, rollbackService);
        PluginCommand command = getCommand("blocklookup");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        DatabaseWriter writerRef = writer;
        writer = null;
        if (writerRef != null) {
            writerRef.shutdownAndFlush(Duration.ofSeconds(5));
        }

        SqliteDatabase databaseRef = database;
        database = null;
        if (databaseRef != null) {
            databaseRef.close();
        }
    }
}
