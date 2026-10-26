package de.codingair.tradesystem.spigot.trade.gui.layout.types.impl.basic;

import de.codingair.codingapi.tools.items.ItemBuilder;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.feedback.FinishResult;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.utils.ShowIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.utils.Perspective;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import de.codingair.tradesystem.spigot.localization.PlayerItems;
import de.codingair.tradesystem.spigot.localization.PlayerText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import java.util.List;

public class DecorationIcon extends ShowIcon {
    public DecorationIcon(@NotNull ItemStack itemStack) {
        super(itemStack);
    }

    @Override
    public ItemStack finishPresentation(ItemStack item, Trade trade, Perspective perspective, Player viewer) {
        PlayerText.View view = trade.presentationView(viewer);
        if (view == null || !PlayerItems.isDefaultHelp(getItemStack())) return item;
        TextColor lore = TextColor.color(0xC7FFC6);
        Component nativeName = item.getItemMeta() == null ? null : item.getItemMeta().displayName();
        Component name = nativeName == null || PlayerItems.plain(nativeName).isEmpty()
                ? null : view.render("decoration_title").color(NamedTextColor.WHITE);
        return PlayerItems.text(item, name,
                List.of(Component.empty(), view.render("decoration_help_offer").color(lore),
                        view.render("decoration_help_ready").color(lore)));
    }

    @Override
    public @NotNull FinishResult tryFinish(@NotNull Trade trade, @NotNull Perspective perspective, @NotNull Player viewer, boolean initiationServer) {
        return FinishResult.PASS;
    }

    @Override
    public boolean isClickable(@NotNull Trade trade, @NotNull Perspective perspective, @NotNull Player viewer) {
        return false;
    }

    @Override
    public final void onFinish(@NotNull Trade trade, @NotNull Perspective perspective, @NotNull Player viewer, boolean initiationServer) {
    }

    @Override
    public @NotNull ItemBuilder prepareItemStack(@NotNull ItemBuilder layout, @NotNull Trade trade, @NotNull Perspective perspective, @NotNull Player viewer) {
        return layout.setHideName(true);
    }
}
