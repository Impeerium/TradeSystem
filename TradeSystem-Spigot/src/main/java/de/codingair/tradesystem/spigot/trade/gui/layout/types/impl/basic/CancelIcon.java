package de.codingair.tradesystem.spigot.trade.gui.layout.types.impl.basic;

import de.codingair.codingapi.tools.items.ItemBuilder;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.SimpleTradeIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.feedback.FinishResult;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.feedback.IconResult;
import de.codingair.tradesystem.spigot.trade.gui.layout.utils.Perspective;
import de.codingair.tradesystem.spigot.utils.Lang;
import de.codingair.tradesystem.spigot.localization.PlayerItems;
import de.codingair.tradesystem.spigot.localization.PlayerText;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class CancelIcon extends SimpleTradeIcon {
    public CancelIcon(@NotNull ItemStack itemStack) {
        super(itemStack);
    }

    @Override
    public ItemStack finishPresentation(ItemStack item, Trade trade, Perspective perspective, Player viewer) {
        PlayerText.View view = trade.presentationView(viewer);
        return view == null ? item : PlayerItems.named(item, view.render("cancel_trade").color(NamedTextColor.RED));
    }

    @Override
    public @NotNull ItemBuilder prepareItemStack(@NotNull ItemBuilder layout, @NotNull Trade trade, @NotNull Perspective perspective, @NotNull Player viewer) {
        layout.setName("§c" + Lang.get("Cancel_Trade", viewer));
        return layout;
    }

    @Override
    public boolean isClickable(@NotNull Trade trade, @NotNull Perspective perspective, @NotNull Player viewer) {
        return true;
    }

    @Override
    public @NotNull IconResult onClick(@NotNull Trade trade, @NotNull Perspective perspective, @NotNull Player viewer, @NotNull InventoryClickEvent event) {
        return IconResult.CANCEL;
    }

    @Override
    public @NotNull FinishResult tryFinish(@NotNull Trade trade, @NotNull Perspective perspective, @NotNull Player viewer, boolean initiationServer) {
        return FinishResult.PASS;
    }

    @Override
    public void onFinish(@NotNull Trade trade, @NotNull Perspective perspective, @NotNull Player viewer, boolean initiationServer) {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public void serialize(@NotNull DataOutputStream out) throws IOException {
    }

    @Override
    public void deserialize(@NotNull DataInputStream in) throws IOException {
    }
}
