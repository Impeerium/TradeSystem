package de.codingair.tradesystem.spigot.events;

import de.codingair.tradesystem.spigot.events.utils.TradeEvent;
import de.codingair.tradesystem.spigot.trade.PlayerTradeResult;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Function;

/**
 * Called when a player receives items or other goods from another player after a trade was completed. Can be used to control the trade report which will be sent via chat.
 */
public class TradeReportEvent extends TradeEvent implements Cancellable {
    private static final HandlerList handlerList = new HandlerList();
    private final Player receiver;
    private final String other;
    private final UUID otherId;
    private final Player otherPlayer;
    private final PlayerTradeResult result;
    private List<String> itemReport;
    private Function<ItemStack, Component> componentItemNameMapper;
    private List<String> economyReport;
    private boolean playFinishSound = true;
    private boolean cancelled = false;

    /**
     * Indicates a proxy trade.
     *
     * @param receiver The {@link Player} who received the item.
     * @param other    The name of the player who trades the item.
     * @param otherId  The {@link UUID} of the player who trades the item.
     * @param result   The {@link PlayerTradeResult} of the trade.
     */
    public TradeReportEvent(@NotNull Player receiver, @NotNull String other, @NotNull UUID otherId, @NotNull PlayerTradeResult result) {
        this.receiver = receiver;
        this.other = other;
        this.otherId = otherId;

        this.result = result;
        this.otherPlayer = null;
    }

    /**
     * Indicates a bukkit trade.
     *
     * @param receiver    The {@link Player} who received the item.
     * @param otherPlayer The {@link Player} who trades the item.
     * @param result      The {@link PlayerTradeResult} of the trade.
     */
    public TradeReportEvent(@NotNull Player receiver, @NotNull Player otherPlayer, @NotNull PlayerTradeResult result) {
        this.receiver = receiver;
        this.otherPlayer = otherPlayer;
        this.otherId = otherPlayer.getUniqueId();

        this.result = result;
        this.other = otherPlayer.getName();
    }

    public static HandlerList getHandlerList() {
        return handlerList;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }

    /**
     * @return The {@link Player} who receives the item.
     */
    @NotNull
    public Player getReceiver() {
        return this.receiver;
    }

    /**
     * @return The {@link Player} who trades the item. Is null if this is a proxy trade and the sender is on another server.
     */
    @Nullable
    public Player getOtherPlayer() {
        return this.otherPlayer;
    }

    /**
     * @return The name of the player who trades the item.
     */
    @NotNull
    public String getOther() {
        return this.other;
    }

    /**
     * @return The {@link UUID} of the player who trades the item.
     */
    @NotNull
    public UUID getOtherId() {
        return otherId;
    }

    /**
     * @return {@link Boolean#TRUE} if the sender is on another server.
     */
    public boolean isProxyTrade() {
        return getOtherPlayer() == null;
    }

    /**
     * @return The {@link PlayerTradeResult result} of the trade for the given player.
     */
    @NotNull
    public PlayerTradeResult getResult() {
        return result;
    }

    /**
     * @return The current item report of the trade.
     */
    @Nullable
    public List<String> getItemReport() {
        return itemReport;
    }

    /**
     * @param itemReport The new item report of the trade. If null, the default report will be used.
     */
    public void setItemReport(@Nullable List<String> itemReport) {
        this.itemReport = itemReport;
        this.componentItemNameMapper = null;
    }

    /**
     * Supply the existing string report and an explicitly component-native item-name
     * mapper for recipient-aware default wording. A later setItemReport call takes
     * precedence and clears this association, even when it supplies the same list.
     */
    public void setComponentItemReport(@NotNull List<String> legacyReport,
                                       @NotNull Function<ItemStack, Component> itemNameMapper) {
        java.util.Objects.requireNonNull(itemNameMapper);
        this.itemReport = new AssociatedReport(legacyReport);
        this.componentItemNameMapper = itemNameMapper;
    }

    @Nullable
    public Function<ItemStack, Component> getComponentItemNameMapper() {
        return componentItemNameMapper;
    }

    /** Track external edits without guessing ownership from the rendered text. */
    private final class AssociatedReport extends AbstractList<String> {
        private final List<String> lines;

        private AssociatedReport(List<String> source) { lines = new ArrayList<>(source); }

        private void changed() {
            // A listener may still hold an older list after another listener replaced it.
            if (itemReport == this) componentItemNameMapper = null;
        }

        @Override public String get(int index) { return lines.get(index); }
        @Override public int size() { return lines.size(); }
        @Override public String set(int index, String value) { String previous = lines.set(index, value);changed();return previous; }
        @Override public void add(int index, String value) { lines.add(index, value);modCount++;changed(); }
        @Override public String remove(int index) { String previous = lines.remove(index);modCount++;changed();return previous; }
    }

    /**
     * @return The current economy report of the trade.
     */
    @Nullable
    public List<String> getEconomyReport() {
        return economyReport;
    }

    /**
     * @param economyReport The new economy report of the trade. If null, the default report will be used.
     */
    public void setEconomyReport(@Nullable List<String> economyReport) {
        this.economyReport = economyReport;
    }

    /**
     * @return True if the finish sound will be played.
     */
    public boolean isPlayFinishSound() {
        return playFinishSound;
    }

    /**
     * @param playFinishSound True if you wish to play the finish sound. Default is true.
     */
    public void setPlayFinishSound(boolean playFinishSound) {
        this.playFinishSound = playFinishSound;
    }

    /**
     * @return True if this event is cancelled.
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * @param cancel True if you wish to cancel this event. This will prevent the player from getting any report messages including that the trade was finished. The finish sound is not affected by this.
     */
    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
