package de.codingair.tradesystem.spigot.trade.gui;

import de.codingair.codingapi.player.gui.inventory.v2.GUI;
import de.codingair.codingapi.player.gui.inventory.v2.exceptions.AlreadyOpenedException;
import de.codingair.codingapi.player.gui.inventory.v2.exceptions.IsNotWaitingException;
import de.codingair.codingapi.player.gui.inventory.v2.exceptions.IsWaitingException;
import de.codingair.codingapi.player.gui.inventory.v2.exceptions.NoPageException;
import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.utils.FloodgateUtils;
import de.codingair.tradesystem.spigot.utils.Lang;
import de.codingair.tradesystem.spigot.localization.PlayerText;
import de.codingair.tradesystem.spigot.localization.TradeTitles;
import de.codingair.tradesystem.spigot.localization.ComponentTitleTransport;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TradingGUI extends GUI {
    private final Trade trade;
    private final int id;
    private final PlayerText.View presentation;
    private final String titleSource;
    private final String countdownSource;
    private final ComponentTitleTransport titleTransport;

    public TradingGUI(Player player, Trade trade, int id) {
        super(player, TradeSystem.getInstance(), trade.getLayout()[id].getPattern().getSize(), Lang.get("GUI_Title", player, new Lang.P("player", trade.getOther(player.getName()))),
                false  // check for plugins that already block items
        );

        this.trade = trade;
        this.id = id;
        this.presentation = trade.presentationView(player);
        this.titleSource = presentation == null ? null : Lang.source("GUI_Title");
        this.countdownSource = presentation == null ? null : Lang.source("Fancy_Countdown");
        Component initial = presentation == null || trade.getLayout()[id].getPattern().getSize() != 54 ? null
                : TradeTitles.create(presentation, titleSource, countdownSource, trade.getCountdown() != null);
        if (initial != null) {
            inventory = Bukkit.createInventory(null, trade.getLayout()[id].getPattern().getSize(), initial);
            titleTransport = new ComponentTitleTransport(TradeSystem.getInstance().getLogger());
        } else titleTransport = null;
    }

    public void synchronizeTitle() {
        // floodgate does not allow updating the GUI title; it just closes the inventory and cancels the trade
        boolean isBedrockPlayer = FloodgateUtils.isBedrockPlayer(getPlayer());
        if (isBedrockPlayer) return;

        if (titleTransport != null) {
            if (!isOpen() || isWaiting() || trade.isCancelling() || !getPlayer().isOnline()
                    || getPlayer().getOpenInventory().getTopInventory() != inventory) return;
            titleTransport.update(getPlayer(), inventory,
                    TradeTitles.create(presentation, titleSource, countdownSource, trade.getCountdown() != null));
            return;
        }

        updateTitle(Lang.get("GUI_Title", getPlayer(), new Lang.P("player", trade.getOther(getPlayer().getName()))));
    }

    @Override
    public void updateTitle(String title) {
        // CodingAPI pages reset their title with null after rebuilding. Its string cache
        // cannot track our component lock overlay, so always synchronize that reset.
        if (title == null && titleTransport != null) {
            synchronizeTitle();
            return;
        }
        if (titleTransport != null) titleTransport.invalidate();
        super.updateTitle(title);
    }

    public void prepareStart() {
        registerPage(new TradingPage(this, trade, id), true);
    }

    @Override
    protected void continueGUI() throws IsNotWaitingException {
        if (trade.isCancelling()) return;
        super.continueGUI();
        if (titleTransport != null) synchronizeTitle();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (!trade.getPause()[id]) trade.cancel();
    }

    public boolean isWaiting() {
        return waiting;
    }

    @Override
    public void openNestedGUI(GUI gui, boolean listenOnClose, boolean clickSound) throws AlreadyOpenedException, NoPageException, IsWaitingException {
        trade.acknowledgeGuiSwitch(player);
        super.openNestedGUI(gui, listenOnClose, clickSound);
    }
}
