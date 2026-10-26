package de.codingair.tradesystem.spigot.trade.gui.layout.shulker;

import de.codingair.codingapi.player.gui.inventory.v2.GUI;
import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.trade.gui.layout.utils.Perspective;
import de.codingair.tradesystem.spigot.utils.Lang;
import de.codingair.tradesystem.spigot.localization.PlayerText;
import org.bukkit.Bukkit;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShulkerPeekGUI extends GUI {
    private final Perspective owner;
    private final PlayerText.View presentation;

    public ShulkerPeekGUI(@NotNull Player player, @NotNull ItemStack item, @NotNull Perspective owner) {
        this(player, item, owner, PlayerText.capture(player));
    }

    public ShulkerPeekGUI(Player player, ItemStack item, Perspective owner, PlayerText.View presentation) {
        super(player, TradeSystem.getInstance(), 36, Lang.get("Shulker_Box", player), true);
        this.owner = owner;
        this.presentation = presentation;
        if (presentation != null) inventory = Bukkit.createInventory(null, 36, presentation.render("shulker_box"));

        ShulkerBox box = getBoxFrom(item);
        if (box != null) {
            super.registerPage(new ShulkerPage(this, box), true);
            return;
        }

        throw new IllegalArgumentException("Not a shulker box!");
    }

    @Nullable
    private ShulkerBox getBoxFrom(@NotNull ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta) {
            BlockStateMeta b = (BlockStateMeta) item.getItemMeta();

            if (b.getBlockState() instanceof ShulkerBox) {
                return (ShulkerBox) b.getBlockState();
            }
        }

        return null;
    }

    /**
     * @return the perspective of the owner of the shulker box.
     */
    @NotNull
    public Perspective getOwner() {
        return owner;
    }

    PlayerText.View presentation() { return presentation; }

    public static boolean isShulkerBox(@Nullable ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta && ((BlockStateMeta) item.getItemMeta()).getBlockState() instanceof ShulkerBox;
    }
}
