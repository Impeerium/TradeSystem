package de.codingair.tradesystem.spigot.extras.external.placeholderapi;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.events.TradeFinishEvent;
import de.codingair.tradesystem.spigot.extras.external.PluginDependency;
import de.codingair.tradesystem.spigot.extras.tradelog.TradeLog;
import de.codingair.tradesystem.spigot.extras.tradelog.TradeLogService;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.utils.Lang;
import de.codingair.tradesystem.spigot.localization.PlayerText;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderDependency implements PluginDependency, Listener {
    private static PlaceholderDependency instance;
    static final String IDENTIFIER = "tradesystem";
    static final Pattern PATTERN = Pattern.compile("%" + IDENTIFIER + "_[a-z_]+%", Pattern.CASE_INSENSITIVE);
    private TradeSystemPlaceholder placeholder;
    private static final Map<UUID, Long> successfulTrades = new ConcurrentHashMap<>();

    public PlaceholderDependency() {
        instance = this;
    }

    @EventHandler
    public void onTradeFinish(TradeFinishEvent e) {
        if (e.getTradeResult() && TradeLogService.connected()) {
            if (e.getSendingPlayer() != null) increaseSuccessfulTrades(e.getSenderId());
            if (e.getReceivingPlayer() != null) increaseSuccessfulTrades(e.getReceiverId());
        }
    }

    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent e) {
        UniversalScheduler.getScheduler(TradeSystem.getInstance()).runTaskAsynchronously(() ->{
            long trades = TradeLogService.count(e.getName(), TradeLog.FINISHED.get());
            if (trades > 0) successfulTrades.put(e.getUniqueId(), trades);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        successfulTrades.remove(e.getPlayer().getUniqueId());
    }

    private void increaseSuccessfulTrades(@NotNull UUID senderId) {
        long trades = successfulTrades.getOrDefault(senderId, 0L);
        successfulTrades.put(senderId, trades + 1);
    }

    @NotNull
    public static String convert(@NotNull String s, @NotNull Player player) {
        if (instance != null && instance.isAvailable()) return PlaceholderAPI.setPlaceholders(player, s);
        else {
            Matcher m = PATTERN.matcher(s);
            StringBuffer output = new StringBuffer();
            while (m.find()) {
                String group = m.group();                                                      // includes "tradesystem" identifier
                String id = group.substring(1 + IDENTIFIER.length() + 1, group.length() - 1);  // remove plugin identifier

                String replacement = apply(player, id);
                m.appendReplacement(output, Matcher.quoteReplacement(replacement == null ? group : replacement));
            }
            m.appendTail(output);
            return output.toString();
        }
    }

    @Override
    public void onEnable() {
        placeholder = new TradeSystemPlaceholder();
        placeholder.register();
    }

    @Override
    public void onDisable() {
        if (placeholder != null) placeholder.unregister();
    }

    @Nullable
    static String apply(@Nullable Player player, @NotNull String id) {
        if (player == null) return null;
        Trade t = TradeSystem.handler().getTrade(player);

        switch (id.toLowerCase(Locale.ROOT)) {
            case "partner":
            case "trade_partner":
                if (t != null) return t.getOther(player.getName());
                else return "";

            case "countdown":
                if (t != null && t.getCountdown() != null) {
                    int remaining = (int) Math.ceil((TradeSystem.handler().getCountdownInterval() * (TradeSystem.handler().getCountdownRepetitions() - t.getCountdownTicks())) / 20F);
                    return String.valueOf(remaining);
                } else return "";

            case "countdown_fancy":
                if (t != null && t.getCountdown() != null) {
                    int remaining = (int) Math.ceil((TradeSystem.handler().getCountdownInterval() * (TradeSystem.handler().getCountdownRepetitions() - t.getCountdownTicks())) / 20F);
                    String nativeFormat = Lang.get("Fancy_Countdown");
                    // Only the shipped textual template is owned here. Keep custom art/formatting exact.
                    if (" &7- &e%seconds%s".equals(Lang.source("Fancy_Countdown"))) {
                        PlayerText.View view = t.presentationView(player);
                        if (view != null) return nativeFormat.replace("%seconds%s", PlainTextComponentSerializer.plainText()
                                .serialize(view.render("Fancy_Countdown", Map.of("seconds", remaining))));
                    }
                    return nativeFormat.replace("%seconds%", remaining + "");
                }
                return "";

            case "status":
                boolean disabled = TradeSystem.handler().isOffline(player);
                PlayerText.View view = t == null ? PlayerText.capture(player) : t.presentationView(player);
                if (view != null) return PlainTextComponentSerializer.plainText()
                        .serialize(view.render(disabled ? "requests_disabled" : "requests_enabled"));
                return Lang.get(disabled ? "Offline" : "Online");

            case "is_trading":
                return t != null ? "true" : "false";

            case "num_trades":
                return String.valueOf(successfulTrades.getOrDefault(player.getUniqueId(), 0L));
        }

        return null;
    }

    @Override
    public @NotNull String getPluginName() {
        return "PlaceholderAPI";
    }
}
