package de.codingair.tradesystem.spigot.localization;

import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.utils.Lang;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.codingapi.player.chat.ChatButton;
import de.codingair.codingapi.player.chat.SimpleMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Component-native player presentation; the native string API remains available to extensions. */
public final class PlayerText {
    public interface View {
        Component render(String key, Map<String, Object> arguments);
        boolean owns(String nativeKey);
        String language();

        default Component render(String key) {
            return render(key, new HashMap<>());
        }
    }

    interface Backend {
        View capture(Player player);
        void close();
    }

    private static Backend backend;
    private static final AtomicBoolean NOTICE_FAILURE_REPORTED = new AtomicBoolean();

    private PlayerText() {}

    public static void initialize(TradeSystem plugin) {
        close();
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ImpeeriumAPI")) return;
        // API classes occur only in this lazily loaded adapter, never in this facade's signatures.
        try {
            backend = ApiPlayerText.create(plugin);
        } catch (LinkageError unavailable) {
            plugin.getLogger().warning("Impeerium localization API is incompatible; retaining native language files.");
        }
    }

    public static void close() {
        if (backend != null) backend.close();
        backend = null;
    }

    public static View capture(Player player) {
        return backend == null ? null : backend.capture(player);
    }

    public static boolean send(Trade trade, Player viewer, String key, Map<String, Object> arguments) {
        View view = trade.presentationView(viewer);
        if (view == null || !view.owns(key)) return false;
        viewer.sendMessage(prefix("").append(view.render(key, arguments)));
        return true;
    }

    /** Prepare first, send once, and never let a notice interrupt cancellation/cleanup. */
    public static void notice(Trade trade, Player viewer, String key, Supplier<String> nativeFallback) {
        notice(viewer, key, () -> trade.presentationView(viewer), () -> viewer.sendMessage(nativeFallback.get()));
    }

    public static void notice(Trade trade, Player viewer, String key, Runnable nativeFallback) {
        notice(viewer, key, () -> trade.presentationView(viewer), nativeFallback);
    }

    /** Pre-trade integrations retain their exact native callback when no owned view is available. */
    public static void notice(Player viewer, String key, Runnable nativeFallback) {
        notice(viewer, key, () -> capture(viewer), nativeFallback);
    }

    private static void notice(Player viewer, String key, Supplier<View> capture, Runnable nativeFallback) {
        Component output = null;
        try {
            View view = capture.get();
            if (view != null && view.owns(key)) output = prefix("").append(view.render(key));
        } catch (RuntimeException | LinkageError failure) {
            noticeFailure(failure);
        }
        try {
            if (output != null) viewer.sendMessage(output);
            else nativeFallback.run();
        } catch (RuntimeException | LinkageError failure) {
            // A failed send may already have reached the recipient; do not replay it.
            noticeFailure(failure);
        }
    }

    private static void noticeFailure(Throwable failure) {
        if (NOTICE_FAILURE_REPORTED.compareAndSet(false, true)) TradeSystem.getInstance().getLogger().warning(
                "Trade notification unavailable (" + failure.getClass().getSimpleName()
                        + "); continuing native processing.");
    }

    /** Explicitly static, argument-free captions for native sign/anvil/Floodgate string APIs. */
    public static String caption(Trade trade, Player viewer, String key, Lang.P... nativeArguments) {
        if (!Set.of("GUI_Title", "Economy_Offer_Title", "Sign_Enter_Amount").contains(key))
            throw new IllegalArgumentException("Not a native input caption");
        View view = trade.presentationView(viewer);
        return view == null ? Lang.get(key, viewer, nativeArguments)
                : PlainTextComponentSerializer.plainText().serialize(view.render(key));
    }

    public static boolean send(CommandSender sender, String prefix, String key, Lang.P... placeholders) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;
        View view = capture(player);
        if (view == null || !view.owns(key)) return false;

        Map<String, Object> args = new HashMap<>();
        for (Lang.P placeholder : placeholders) args.put(placeholder.name(), placeholder.value());
        String command = tradeCommand();
        switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "command_how_to":
                args.put("command", Component.text(command));
                args.put("accept", acceptAlias());
                args.put("deny", denyAlias());
                args.put("toggle", TradeSystem.getInstance().getCommandManager().getToggleAliases()[0]);
                break;
            case "command_how_to_accept":
                args.put("command", Component.text(command + " " + acceptAlias()));
                break;
            case "command_how_to_deny":
                args.put("command", Component.text(command + " " + denyAlias()));
                break;
            case "trade_you_are_offline":
                return offlineNotice(player);
            default:
                break;
        }
        player.sendMessage(prefix(prefix).append(view.render(key, args)));
        return true;
    }

    public static boolean invitation(String playerName, Player recipient, boolean bedrock) {
        View view = capture(recipient);
        if (view == null) return false;
        String acceptCommand = tradeCommand() + " " + acceptAlias() + " " + playerName;
        String denyCommand = tradeCommand() + " " + denyAlias() + " " + playerName;
        Component accept = bedrock ? Component.text(acceptCommand) : view.render("invitation_accept");
        Component deny = bedrock ? Component.text(denyCommand) : view.render("invitation_deny");
        accept = accept.clickEvent(ClickEvent.runCommand(acceptCommand))
                .hoverEvent(view.render("invitation_accept_hover"));
        deny = deny.clickEvent(ClickEvent.runCommand(denyCommand))
                .hoverEvent(view.render("invitation_deny_hover"));
        Map<String, Object> args = new HashMap<>();
        args.put("player", playerName);
        args.put("accept", accept);
        args.put("deny", deny);
        recipient.sendMessage(prefix("").append(view.render(bedrock ? "want_to_trade_bedrock" : "want_to_trade", args)));
        return true;
    }

    public static boolean offlineNotice(Player recipient) {
        return offlineNotice(recipient, System::nanoTime);
    }

    static boolean offlineNotice(Player recipient, LongSupplier clock) {
        View view = capture(recipient);
        if (view == null) return false;
        String command = tradeCommand() + " " + TradeSystem.getInstance().getCommandManager().getToggleAliases()[0];
        SimpleMessage message = new SimpleMessage(recipient, "", TradeSystem.getInstance());
        CallbackGuard guard = new CallbackGuard(recipient.getUniqueId(), 10_000_000_000L, clock);
        ChatButton toggle = new ChatButton("") {
            @Override
            public void onClick(Player player) {
                if (!recipient.isOnline() || !guard.claim(player.getUniqueId())) return;
                try {
                    recipient.performCommand(command.substring(1));
                } finally {
                    message.destroy();
                }
            }
        }.setType("IMPEERIUM_TRADE_TOGGLE");
        message.add(toggle);
        message.setTimeOut(10);
        // Keep CodingAPI's registered single-use/timeout callback. Its visible dummy text is unused.
        Component button = Component.text(command)
                .clickEvent(ClickEvent.runCommand("/" + ChatButton.PREFIX + toggle.getUniqueId() + "#" + toggle.getType()))
                .hoverEvent(view.render("want_to_trade_hover"));
        Map<String, Object> args = new HashMap<>();
        args.put("command", button);
        Component output = prefix("").append(view.render("trade_you_are_offline", args));
        message.send(recipient, (player, ignored) -> player.sendMessage(output));
        return true;
    }

    /** Parse only the administrator-authored legacy prefix, before appending rendered player text. */
    public static Component prefix(String extra) {
        return LegacyComponentSerializer.legacySection().deserialize(Lang.getPrefix() + extra);
    }

    public static Component accent(Component text) {
        return text.colorIfAbsent(TextColor.color(0xD0EB06));
    }

    private static String tradeCommand() {
        return "/" + TradeSystem.getInstance().getCommandManager().getTradeAliases()[0];
    }

    private static String acceptAlias() {
        return TradeSystem.getInstance().getCommandManager().getAcceptAliases()[0];
    }

    private static String denyAlias() {
        return TradeSystem.getInstance().getCommandManager().getDenyAliases()[0];
    }
}
