package de.codingair.tradesystem.spigot.localization;

import de.codingair.tradesystem.spigot.trade.ReportFixture;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.trade.TradeThreads;
import de.codingair.tradesystem.spigot.trade.gui.TradeGUIListener;
import de.codingair.tradesystem.spigot.trade.gui.TradingGUI;
import de.codingair.tradesystem.spigot.utils.Lang;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.impeerium.api.localization.Language;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Canvas (Folia) Survival, 2026-11-09: the countdown ran on the global region, so closing the traders'
 * menus at the finish threw "Cannot close container async" and every trade hung with the lock overlay.
 * Here a fake region model decides which traders the current thread owns.
 */
class FoliaTradeThreadTest {
    PlayerReportTest reports;
    PlayerTextTest fixture;
    MockedStatic<TradeThreads> threads;
    final Set<Player> owned = new HashSet<>();
    final List<Map.Entry<Player, Runnable>> queued = new ArrayList<>();
    final List<Runnable> repeating = new ArrayList<>();
    final List<Player> repeatingOn = new ArrayList<>();
    ScheduledTask repeatTask;
    Player first, second;
    PlayerInventory secondInventory;

    @BeforeAll static void registry() { MockBukkit.mock(); }
    @AfterAll static void registryCleanup() { MockBukkit.unmock(); }

    @BeforeEach void setup() throws Exception {
        reports = new PlayerReportTest();reports.setup();fixture = reports.fixture;
        fixture.lang.when(() -> Lang.get(anyString(), nullable(Player.class), any(Lang.P[].class))).thenReturn("Native notice");
        when(fixture.owner.isEnabled()).thenReturn(true);
        when(reports.handler.getCountdownInterval()).thenReturn(20);
        when(reports.handler.getCountdownRepetitions()).thenReturn(3);

        threads = mockStatic(TradeThreads.class, CALLS_REAL_METHODS);
        threads.when(TradeThreads::regionized).thenReturn(true);
        threads.when(() -> TradeThreads.owns(any(Player.class))).thenAnswer(call -> owned.contains(call.<Player>getArgument(0)));
        threads.when(() -> TradeThreads.later(any(Player.class), any(Runnable.class), anyLong())).thenAnswer(call -> {
            queued.add(Map.entry(call.getArgument(0), call.getArgument(1)));
            return true;
        });
        repeatTask = mock(ScheduledTask.class);
        threads.when(() -> TradeThreads.repeat(any(Player.class), any(Runnable.class), anyLong())).thenAnswer(call -> {
            repeatingOn.add(call.getArgument(0));
            repeating.add(call.getArgument(1));
            return repeatTask;
        });

        first = trader(Language.EN, "Alex");
        second = trader(Language.ET, "Steve");
        secondInventory = mock(PlayerInventory.class);
        when(secondInventory.getContents()).thenReturn(new ItemStack[41]);
        when(second.getInventory()).thenReturn(secondInventory);
    }

    @AfterEach void close() {
        threads.close();
        reports.close();
    }

    Player trader(Language language, String name) {
        Player player = reports.player(language, name);
        InventoryView view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        return player;
    }

    TradingGUI gui(Trade trade, int id, Player player) {
        TradingGUI gui = mock(TradingGUI.class);
        when(gui.getPlayer()).thenReturn(player);
        when(gui.isOpen()).thenReturn(true);
        trade.getGUIs()[id] = gui;
        return gui;
    }

    CompletableFuture<Boolean> countdown(Trade trade) throws Exception {
        ReportFixture.realCountdown(trade);
        trade.getReady()[0] = trade.getReady()[1] = true;
        return ReportFixture.finish(trade);
    }

    @Test void countdownRunsOnTheInitiatingTradersThreadAndFinishesBothSides() throws Exception {
        owned.addAll(List.of(first, second));
        List<String> order = new ArrayList<>();
        Trade trade = ReportFixture.completing(first, second, null, order);

        CompletableFuture<Boolean> done = countdown(trade);
        assertEquals(List.of(first), repeatingOn);
        assertNotNull(trade.getCountdown());

        for (int tick = 0; tick < 4; tick++) repeating.get(0).run();

        assertTrue(done.get());
        verify(first).closeInventory();verify(second).closeInventory();
        assertEquals(List.of("items:PRIMARY", "items:SECONDARY"), order);
        verify(repeatTask).cancel();
        assertNull(trade.getCountdown());
        assertTrue(queued.isEmpty());
    }

    @Test void traderMovedToAnotherRegionCancelsEachHalfOnItsOwnThreadWithoutLossOrCopy() throws Exception {
        owned.addAll(List.of(first, second));
        Trade trade = ReportFixture.cancelling(first, second, new ArrayList<>());
        trade.getSlots().add(0);trade.getOtherSlots().add(5);
        TradingGUI own = gui(trade, 0, first), other = gui(trade, 1, second);
        ItemStack offer = mock(ItemStack.class), back = mock(ItemStack.class);
        when(offer.getType()).thenReturn(Material.DIAMOND);when(offer.clone()).thenReturn(back);
        when(back.getType()).thenReturn(Material.DIAMOND);when(back.getAmount()).thenReturn(3);
        when(other.getItem(0)).thenReturn(offer);

        CompletableFuture<Boolean> done = countdown(trade);
        repeating.get(0).run();
        owned.remove(second); // Steve was moved to another region; this thread now owns only Alex
        repeating.get(0).run();

        assertFalse(done.isDone());
        verify(repeatTask).cancel();
        assertNull(trade.getCountdown());
        assertTrue(trade.isCancelling());
        verify(first).closeInventory();verify(own).destroy();verify(reports.handler).unregisterTrade("Alex");
        verify(second, never()).closeInventory();verify(other, never()).destroy();verify(secondInventory, never()).addItem(any());
        verify(reports.handler, never()).unregisterTrade("Steve");
        assertEquals(1, queued.size());assertSame(second, queued.get(0).getKey());

        // Steve's clicks are refused until his half has run on his thread
        when(reports.handler.getTrade(second)).thenReturn(trade);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(second);
        owned.clear();owned.add(second);
        new TradeGUIListener().onClick(click);
        verify(click).setCancelled(true);

        queued.get(0).getValue().run();
        InOrder returned = inOrder(other, secondInventory, second);
        returned.verify(other).setItem(0, null);
        returned.verify(secondInventory).addItem(back);
        returned.verify(second).closeInventory();
        verify(other).setItem(5, null);verify(other).destroy();
        verify(reports.handler).unregisterTrade("Steve");
        verify(second).sendMessage(argThat((net.kyori.adventure.text.Component text) -> PlayerTextTest.plain(text).contains("Tehing on tühistatud")));

        // a later quit or close of either trader settles nothing twice
        trade.cancel();
        owned.clear();owned.add(first);
        trade.cancel();
        verify(secondInventory, times(1)).addItem(any());
        verify(second, times(1)).closeInventory();verify(first, times(1)).closeInventory();
    }

    @Test void quitBeforeTheScheduledHalfRunsSettlesThatTraderOnTheQuitThread() {
        owned.add(first);
        Trade trade = ReportFixture.cancelling(first, second, new ArrayList<>());
        trade.getSlots().add(0);
        gui(trade, 0, first);
        TradingGUI other = gui(trade, 1, second);
        ItemStack offer = mock(ItemStack.class), back = mock(ItemStack.class);
        when(offer.getType()).thenReturn(Material.DIAMOND);when(offer.clone()).thenReturn(back);
        when(back.getType()).thenReturn(Material.DIAMOND);when(back.getAmount()).thenReturn(1);
        when(other.getItem(0)).thenReturn(offer);

        trade.cancel(); // e.g. Alex closed his menu while Steve was in another region
        assertEquals(1, queued.size());
        verify(secondInventory, never()).addItem(any());

        owned.clear();owned.add(second);
        trade.cancel(); // Steve quits: TradeHandler#quit on his thread, before he is saved
        verify(secondInventory).addItem(back);
        verify(reports.handler).unregisterTrade("Steve");

        queued.get(0).getValue().run(); // the scheduled half finds nothing left to do
        verify(secondInventory, times(1)).addItem(any());
        verify(second, times(1)).closeInventory();
    }

    @Test void colocatedTradersCancelInlineAsBefore() {
        owned.addAll(List.of(first, second));
        Trade trade = ReportFixture.cancelling(first, second, new ArrayList<>());
        gui(trade, 0, first);gui(trade, 1, second);
        ReportFixture.droppedOnReturn(trade, false, false);

        trade.cancel();
        verify(first).closeInventory();verify(second).closeInventory();
        verify(reports.handler).unregisterTrade("Alex");verify(reports.handler).unregisterTrade("Steve");
        // only the delayed "close again" passes go to each viewer's own scheduler, never the global region
        assertEquals(List.of(first, second), queued.stream().map(Map.Entry::getKey).toList());
    }
}
