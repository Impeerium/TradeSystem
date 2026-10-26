package de.codingair.tradesystem.spigot.trade;

import de.codingair.codingapi.utils.ChatColor;
import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.impl.economy.EconomyIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.utils.Perspective;
import de.codingair.tradesystem.spigot.utils.Lang;
import de.codingair.tradesystem.spigot.localization.PlayerText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PlayerTradeResult extends TradeResult {
    private final Trade trade;
    private final Player player;

    public PlayerTradeResult(@NotNull Trade trade, @NotNull Player player, @NotNull Perspective perspective) {
        super(player.getUniqueId(), player.getWorld().getName(), TradeSystem.proxy().getServerName(), perspective);
        this.trade = trade;
        this.player = player;
    }

    @NotNull
    List<String> buildItemReport() {
        return buildItemReport(itemToNameMapper());
    }

    @NotNull
    public static Function<ItemStack, String> itemToNameMapper() {
        return item -> {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                assert meta != null;

                if (meta.hasDisplayName())
                    return TradeSystem.handler().isOnlyDisplayNameInMessage() ? meta.getDisplayName() : formatName(item.getType().name()) + " (" + ChatColor.stripColor(meta.getDisplayName()) + ")";
            }

            return formatName(item.getType().name());
        };
    }

    /**
     * @param mapper A function that maps an {@link ItemStack} to a {@link String} that is used in the report.
     * @return An unsorted list of lines that can be used in a report. Will be sorted lexicographically before printing.
     */
    @NotNull
    public List<String> buildItemReport(Function<ItemStack, String> mapper) {
        List<String> lines = new ArrayList<>();

        Map<ItemStack, Integer> receiving = aggregate(receivingItems);
        Map<ItemStack, Integer> sending = aggregate(sendingItems);

        receiving.forEach((item, amount) -> {
            ItemStack presentation = item.clone();
            presentation.setAmount(amount);

            String itemName = Lang.get("Trade_Finish_Report_Object_Item", player,
                    new Lang.P("amount", String.valueOf(amount)),
                    new Lang.P("item", mapper.apply(presentation))
            );

            Lang.P info = new Lang.P("object", itemName);
            lines.add(Lang.get("Trade_Finish_Report_Receive", player, info));
        });

        sending.forEach((item, amount) -> {
            ItemStack presentation = item.clone();
            presentation.setAmount(amount);

            String itemName = Lang.get("Trade_Finish_Report_Object_Item", player,
                    new Lang.P("amount", String.valueOf(amount)),
                    new Lang.P("item", mapper.apply(presentation))
            );

            Lang.P info = new Lang.P("object", itemName);
            lines.add(Lang.get("Trade_Finish_Report_Give", player, info));
        });

        return lines;
    }

    private static Map<ItemStack, Integer> aggregate(List<ItemStack> items) {
        Map<ItemStack, Integer> result = new HashMap<>();
        for (ItemStack item : items) {
            ItemStack key = item.clone();
            key.setAmount(1);
            result.merge(key, item.getAmount(), Integer::sum);
        }
        return result;
    }

    /** Preserve a proper item name as a component; never interpret its text as a template. */
    public static Component componentItemName(ItemStack item, String baseName) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            Component custom = meta.displayName();
            if (TradeSystem.handler().isOnlyDisplayNameInMessage()) return custom;
            return Component.text(baseName + " (" + PlainTextComponentSerializer.plainText().serialize(custom) + ")");
        }
        return Component.text(baseName);
    }

    public List<Component> buildComponentItemReport(PlayerText.View view, Function<ItemStack, Component> mapper) {
        List<Component> lines = new ArrayList<>();
        appendItems(lines, aggregate(receivingItems), view, mapper, "Trade_Finish_Report_Receive");
        appendItems(lines, aggregate(sendingItems), view, mapper, "Trade_Finish_Report_Give");
        return lines;
    }

    private static void appendItems(List<Component> lines, Map<ItemStack, Integer> items, PlayerText.View view,
                                    Function<ItemStack, Component> mapper, String direction) {
        items.forEach((item, amount) -> {
            ItemStack presentation = item.clone();
            presentation.setAmount(amount);
            Component object = view.render("Trade_Finish_Report_Object_Item", Map.of(
                    "amount", amount, "item", mapper.apply(presentation)));
            lines.add(view.render(direction, Map.of("object", object)));
        });
    }

    public List<Component> buildComponentEconomyReport(PlayerText.View view) {
        List<Component> lines = new ArrayList<>();
        for (EconomyIcon<?> icon : economyIcons) {
            BigDecimal diff = icon.getOverallDifference(trade, perspective);
            if (diff.signum() == 0) continue;
            boolean receive = diff.signum() > 0;
            boolean singular = diff.abs().compareTo(BigDecimal.ONE) == 0;
            Component object = Component.text(icon.makeString(trade, perspective, player, diff.abs(), !receive))
                    .append(Component.space()).append(icon.nameComponent(view, player, singular));
            lines.add(view.render(receive ? "Trade_Finish_Report_Receive" : "Trade_Finish_Report_Give",
                    Map.of("object", object)));
        }
        return lines;
    }

    @NotNull
    List<String> buildEconomyReport() {
        List<String> lines = new ArrayList<>();

        for (EconomyIcon<?> icon : economyIcons) {
            BigDecimal diff = icon.getOverallDifference(trade, perspective);
            if (diff.signum() == 0) continue;
            boolean receive = diff.signum() > 0;

            boolean singular = diff.abs().compareTo(BigDecimal.ONE) == 0;
            if (receive) {
                Lang.P info = new Lang.P("object",
                        icon.makeString(trade, perspective, player, diff, false) + " " + icon.getName(player, singular));
                lines.add(Lang.get("Trade_Finish_Report_Receive", player, info));
            } else {
                Lang.P info = new Lang.P("object",
                        icon.makeString(trade, perspective, player, diff.negate(), true) + " " + icon.getName(player, singular));
                lines.add(Lang.get("Trade_Finish_Report_Give", player, info));
            }
        }

        return lines;
    }

    @NotNull
    public static String formatName(@NotNull String name) {
        if (name.isEmpty()) return name;
        StringBuilder b = new StringBuilder(name.toLowerCase());

        int i = 0;
        do {
            b.replace(i, i + 1, b.substring(i, i + 1).toUpperCase());
            i = b.indexOf("_", i);
            if (i >= 0) b.replace(i, i + 1, " ");
            i++;
        } while (i > 0 && i < b.length());

        return b.toString().trim();
    }

    /**
     * @return The current player.
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /**
     * @return The trade.
     */
    @NotNull
    public Trade getTrade() {
        return trade;
    }
}
