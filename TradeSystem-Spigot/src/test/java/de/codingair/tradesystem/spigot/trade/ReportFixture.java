package de.codingair.tradesystem.spigot.trade;

import de.codingair.tradesystem.spigot.trade.gui.layout.TradeLayout;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.TradeIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.utils.Perspective;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

/** Real private completion chain with only game I/O/countdown boundaries replaced. */
public final class ReportFixture {
    private ReportFixture() {}

    public static Trade completing(Player first, Player second, TradeIcon icon, List<String> order) {
        return create(first, second, icon, order, true);
    }

    public static Trade cancelling(Player first, Player second, List<String> nativeReasons) {
        Trade trade = create(first, second, null, new java.util.ArrayList<>(), false);
        doAnswer(call -> {nativeReasons.add(call.getArgument(0));return null;})
                .when(trade).cancelling(nullable(String.class));
        return trade;
    }

    public static void droppedOnReturn(Trade trade, boolean first, boolean second) {
        doReturn(new boolean[]{first, second}).when(trade).returnItemsToOwner();
    }

    private static Trade create(Player first, Player second, TradeIcon icon, List<String> order, boolean initiationServer) {
        Trade trade = mock(Trade.class, withSettings().useConstructor("Alex", "Steve", initiationServer).defaultAnswer(CALLS_REAL_METHODS));
        doReturn(true).when(trade).isActive();
        doReturn(false).when(trade).isPaused();
        doReturn(false).when(trade).isInitiator(any()); // Keep asynchronous external trade-log I/O outside this fixture.
        doReturn(true).when(trade).tryFinish(any());
        doReturn(CompletableFuture.completedFuture(null)).when(trade).runCountdown();
        doReturn(CompletableFuture.completedFuture(true)).when(trade).canFinish();
        doReturn(first).when(trade).getPlayer(Perspective.PRIMARY);
        doReturn(second).when(trade).getPlayer(Perspective.SECONDARY);
        doReturn(first.getUniqueId()).when(trade).getUniqueId(Perspective.PRIMARY);
        doReturn(second.getUniqueId()).when(trade).getUniqueId(Perspective.SECONDARY);
        doAnswer(call -> Stream.of(first, second)).when(trade).getParticipants();
        doAnswer(call -> {order.add("items:" + call.getArgument(0));return false;})
                .when(trade).exchangeItems(any(), anyBoolean());
        TradeLayout layout = mock(TradeLayout.class);
        when(layout.getIcons()).thenReturn(icon == null ? new TradeIcon[0] : new TradeIcon[]{icon});
        trade.getLayout()[0] = trade.getLayout()[1] = layout;
        return trade;
    }

    @SuppressWarnings("unchecked")
    public static CompletableFuture<Boolean> finish(Trade trade) throws Exception {
        var method = Trade.class.getDeclaredMethod("finish");
        method.setAccessible(true);
        return (CompletableFuture<Boolean>) method.invoke(trade);
    }

    public static void report(Trade trade, Perspective perspective, boolean dropped, PlayerTradeResult result) throws Exception {
        var method = Trade.class.getDeclaredMethod("sendReport", Perspective.class, boolean.class, TradeResult.class);
        method.setAccessible(true);
        method.invoke(trade, perspective, dropped, result);
    }

    public static void add(PlayerTradeResult result, ItemStack item, boolean receiving) { result.add(item, receiving); }
    public static void economy(PlayerTradeResult result, TradeIcon icon) { result.add(icon); }
}
