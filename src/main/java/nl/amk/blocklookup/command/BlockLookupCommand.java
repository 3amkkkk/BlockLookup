package nl.amk.blocklookup.command;

import nl.amk.blocklookup.config.Settings;
import nl.amk.blocklookup.db.EventQueryService;
import nl.amk.blocklookup.db.PlayerFilter;
import nl.amk.blocklookup.db.model.BlockEventRecord;
import nl.amk.blocklookup.db.model.ChatEventRecord;
import nl.amk.blocklookup.rollback.RollbackOperation;
import nl.amk.blocklookup.rollback.RollbackService;
import nl.amk.blocklookup.util.DurationParser;
import nl.amk.blocklookup.util.GradientText;
import nl.amk.blocklookup.util.PlayerFilters;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class BlockLookupCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final Settings settings;
    private final EventQueryService queries;
    private final RollbackService rollbackService;
    private final DateTimeFormatter timeFormatter;

    public BlockLookupCommand(Plugin plugin, Settings settings, EventQueryService queries, RollbackService rollbackService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.rollbackService = Objects.requireNonNull(rollbackService, "rollbackService");
        this.timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "lookup" -> handleLookup(sender, slice(args, 1));
            case "chat" -> handleChat(sender, slice(args, 1));
            case "rollback" -> handleRollback(sender, RollbackOperation.ROLLBACK, slice(args, 1));
            case "restore" -> handleRollback(sender, RollbackOperation.RESTORE, slice(args, 1));
            default -> {
                info(sender, GradientText.red() + "Unknown subcommand." + GradientText.gray() + " Use " + GradientText.yellow() + "/" + label + " help" + GradientText.gray());
                yield true;
            }
        };
    }

    private boolean handleLookup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("blocklookup.lookup")) {
            info(sender, GradientText.red() + "No permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            info(sender, GradientText.red() + "Lookup requires an in-game player.");
            return true;
        }

        PlayerFilter filter = args.length >= 1 ? PlayerFilters.parse(args[0]) : PlayerFilter.any();
        Duration duration = args.length >= 2 ? DurationParser.parse(args[1]) : Duration.ofMinutes(settings.lookupDefaultMinutes());
        if (duration == null) duration = Duration.ofMinutes(settings.lookupDefaultMinutes());
        int radius = args.length >= 3 ? parseInt(args[2], settings.defaultRadius()) : settings.defaultRadius();
        int limit = args.length >= 4 ? parseInt(args[3], settings.lookupDefaultLimit()) : settings.lookupDefaultLimit();

        long sinceMs = System.currentTimeMillis() - Math.max(0L, duration.toMillis());

        int cx = player.getLocation().getBlockX();
        int cy = player.getLocation().getBlockY();
        int cz = player.getLocation().getBlockZ();

        info(sender, GradientText.darkGray() + "Searching logs...");
        supplyAsync(() -> queries.findNearbyBlockEvents(player.getWorld().getUID(), cx, cy, cz, radius, sinceMs, filter, limit))
                .thenAccept(records -> Bukkit.getScheduler().runTask(plugin, () -> sendBlockResults(sender, records, cx, cz)));
        return true;
    }

    private boolean handleChat(CommandSender sender, String[] args) {
        if (!sender.hasPermission("blocklookup.chat")) {
            info(sender, GradientText.red() + "No permission.");
            return true;
        }

        PlayerFilter filter = args.length >= 1 ? PlayerFilters.parse(args[0]) : PlayerFilter.any();
        Duration duration = args.length >= 2 ? DurationParser.parse(args[1]) : Duration.ofMinutes(settings.lookupDefaultMinutes());
        if (duration == null) duration = Duration.ofMinutes(settings.lookupDefaultMinutes());
        int limit = args.length >= 3 ? parseInt(args[2], settings.lookupDefaultLimit()) : settings.lookupDefaultLimit();

        long sinceMs = System.currentTimeMillis() - Math.max(0L, duration.toMillis());

        info(sender, GradientText.darkGray() + "Searching chat...");
        supplyAsync(() -> queries.findChatEvents(sinceMs, filter, limit))
                .thenAccept(records -> Bukkit.getScheduler().runTask(plugin, () -> sendChatResults(sender, records)));
        return true;
    }

    private boolean handleRollback(CommandSender sender, RollbackOperation operation, String[] args) {
        String perm = operation == RollbackOperation.ROLLBACK ? "blocklookup.rollback" : "blocklookup.restore";
        if (!sender.hasPermission(perm)) {
            info(sender, GradientText.red() + "No permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            info(sender, GradientText.red() + operation.name().toLowerCase(Locale.ROOT) + " requires an in-game player.");
            return true;
        }
        if (args.length < 2) {
            info(sender, GradientText.yellow() + "Usage:" + GradientText.gray() + " /bl " + operation.name().toLowerCase(Locale.ROOT) + " <player|*> <duration> [radius] [limit]");
            info(sender, GradientText.yellow() + "Example:" + GradientText.gray() + " /bl " + operation.name().toLowerCase(Locale.ROOT) + " * 30m 15 500");
            return true;
        }

        PlayerFilter filter = PlayerFilters.parse(args[0]);
        Duration duration = DurationParser.parse(args[1]);
        if (duration == null) {
            info(sender, GradientText.red() + "Invalid duration." + GradientText.gray() + " Use formats like " + GradientText.yellow() + "30m" + GradientText.gray() + ", " + GradientText.yellow() + "2h" + GradientText.gray() + ", " + GradientText.yellow() + "1d" + GradientText.gray() + ".");
            return true;
        }

        int radius = args.length >= 3 ? parseInt(args[2], settings.defaultRadius()) : settings.defaultRadius();
        int limit = args.length >= 4 ? parseInt(args[3], settings.maxActionsPerCommand()) : settings.maxActionsPerCommand();

        rollbackService.execute(player, operation, filter, duration, radius, limit);
        return true;
    }

    private void sendBlockResults(CommandSender sender, List<BlockEventRecord> records, int cx, int cz) {
        if (records.isEmpty()) {
            info(sender, GradientText.red() + "No matching block events found.");
            return;
        }
        info(sender, GradientText.gradient("Recent block events") + GradientText.gray() + ": " + GradientText.yellow() + records.size() + GradientText.gray());
        for (BlockEventRecord record : records) {
            String time = timeFormatter.format(Instant.ofEpochMilli(record.timeMs()));
            int dx = record.x() - cx;
            int dz = record.z() - cz;
            int dist2 = dx * dx + dz * dz;
            info(sender, GradientText.darkGray() + time +
                    GradientText.gray() + " " + GradientText.yellow() + safe(record.action()) +
                    GradientText.gray() + " " + GradientText.green() + safe(record.playerName()) +
                    GradientText.gray() + " @ " + record.x() + "," + record.y() + "," + record.z() +
                    GradientText.darkGray() + " d2=" + dist2 +
                    GradientText.gray() + " " + record.before().materialKey() + GradientText.darkGray() + " -> " + GradientText.gray() + record.after().materialKey());
        }
    }

    private void sendChatResults(CommandSender sender, List<ChatEventRecord> records) {
        if (records.isEmpty()) {
            info(sender, GradientText.red() + "No matching chat messages found.");
            return;
        }
        info(sender, GradientText.gradient("Recent chat messages") + GradientText.gray() + ": " + GradientText.yellow() + records.size() + GradientText.gray());
        for (ChatEventRecord record : records) {
            String time = timeFormatter.format(Instant.ofEpochMilli(record.timeMs()));
            info(sender, GradientText.darkGray() + time +
                    GradientText.gray() + " " + GradientText.green() + safe(record.playerName()) +
                    GradientText.darkGray() + ":" + GradientText.gray() + " " + record.message());
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(GradientText.gradient("BlockLookup Commands"));
        info(sender, GradientText.yellow() + "/bl lookup" + GradientText.gray() + " [player|*] [duration] [radius] [limit]");
        info(sender, GradientText.yellow() + "/bl chat" + GradientText.gray() + " [player|*] [duration] [limit]");
        info(sender, GradientText.yellow() + "/bl rollback" + GradientText.gray() + " <player|*> <duration> [radius] [limit]");
        info(sender, GradientText.yellow() + "/bl restore" + GradientText.gray() + " <player|*> <duration> [radius] [limit]");
        info(sender, GradientText.darkGray() + "Duration formats: " + GradientText.gray() + "30m, 2h, 1d. A plain number is minutes.");
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String safe(String s) {
        return s == null ? "?" : s;
    }

    private static String[] slice(String[] args, int from) {
        if (from >= args.length) return new String[0];
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }

    private <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("lookup", "chat", "rollback", "restore", "help"));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("lookup") || sub.equals("chat") || sub.equals("rollback") || sub.equals("restore")) && args.length == 2) {
            List<String> players = new ArrayList<>();
            players.add("*");
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            return partial(args[1], players);
        }
        if ((sub.equals("lookup") || sub.equals("chat") || sub.equals("rollback") || sub.equals("restore")) && args.length == 3) {
            return partial(args[2], List.of("10m", "30m", "2h", "1d"));
        }
        return Collections.emptyList();
    }

    private static List<String> partial(String token, List<String> options) {
        String t = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(t)) out.add(option);
        }
        return out;
    }

    private static void info(CommandSender sender, String message) {
        sender.sendMessage(GradientText.prefix() + message + GradientText.reset());
    }
}
