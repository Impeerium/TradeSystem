package de.codingair.tradesystem.spigot.localization;

import de.codingair.codingapi.utils.ChatColor;
import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.events.TradeReportEvent;
import de.codingair.tradesystem.spigot.trade.PlayerTradeResult;
import de.codingair.tradesystem.spigot.trade.Trade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Prepare the whole report before output; external string overrides retain their native sink. */
public final class TradeReports {
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private TradeReports() {}

    public record Line(Component component, String legacy) {
        public Line {
            if ((component == null) == (legacy == null)) throw new IllegalArgumentException("One report representation required");
        }

        public static Line nativeText(String text) { return new Line(null, text); }
        public static Line component(Component text) { return new Line(text, null); }

        String sortKey() {
            String plain = component == null ? ChatColor.stripColor(legacy)
                    : PlainTextComponentSerializer.plainText().serialize(component);
            return plain.replaceFirst("\\d+(x)?", "");
        }

        public void send(Player player) {
            if (component != null) player.sendMessage(component);
            else player.sendMessage(legacy);
        }
    }

    /** Null means the optional localization backend is absent; use the native report. */
    public static List<Line> prepare(Trade trade, Player viewer, boolean droppedItems,
                                     PlayerTradeResult result, TradeReportEvent event) {
        PlayerText.View view = trade.presentationView(viewer);
        if (view == null) return null;
        List<Line> messages = new ArrayList<>();
        messages.add(Line.component(PlayerText.prefix("").append(view.render("Trade_Was_Finished"))));
        List<Line> details = new ArrayList<>();
        if (TradeSystem.handler().isTradeReportEconomy()) {
            if (event.getEconomyReport() != null) event.getEconomyReport().forEach(text -> details.add(Line.nativeText(text)));
            else result.buildComponentEconomyReport(view).forEach(text -> details.add(Line.component(text)));
        }
        if (TradeSystem.handler().isTradeReportItems()) {
            Function<org.bukkit.inventory.ItemStack, Component> mapper = event.getComponentItemNameMapper();
            if (event.getItemReport() != null && mapper == null)
                event.getItemReport().forEach(text -> details.add(Line.nativeText(text)));
            else {
                if (mapper == null) mapper = item -> PlayerTradeResult.componentItemName(item,
                        PlayerTradeResult.formatName(item.getType().name()));
                result.buildComponentItemReport(view, mapper).forEach(text -> details.add(Line.component(text)));
            }
        }
        details.sort(Comparator.comparing(Line::sortKey));
        messages.addAll(details);
        if (droppedItems) {
            messages.add(Line.component(Component.empty()));
            messages.add(Line.component(PlayerText.prefix("").append(view.render("Items_Dropped"))));
        }
        return messages;
    }

    /** Do not expose names, mapper data or catalog arguments in presentation diagnostics. */
    public static void failure(Throwable failure) {
        if (REPORTED.compareAndSet(false, true)) TradeSystem.getInstance().getLogger().warning(
                "Trade report presentation unavailable (" + failure.getClass().getSimpleName()
                        + "); native settlement continues.");
    }
}
