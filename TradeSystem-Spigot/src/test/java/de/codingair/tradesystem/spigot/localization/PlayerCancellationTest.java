package de.codingair.tradesystem.spigot.localization;

import de.codingair.tradesystem.spigot.events.TradeFinishEvent;
import de.codingair.tradesystem.spigot.trade.ReportFixture;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.trade.listeners.TradeListener;
import de.codingair.tradesystem.spigot.utils.Lang;
import net.impeerium.api.localization.Language;
import net.impeerium.api.localization.LanguageSnapshot;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerCancellationTest {
    PlayerReportTest reports;
    PlayerTextTest fixture;

    @BeforeAll static void registry() { MockBukkit.mock(); }
    @AfterAll static void registryCleanup() { MockBukkit.unmock(); }
    @BeforeEach void setup() throws Exception {
        reports = new PlayerReportTest();reports.setup();fixture = reports.fixture;
        fixture.lang.when(() -> Lang.get(anyString(), nullable(Player.class), any(Lang.P[].class))).thenReturn("Native notice");
    }
    @AfterEach void close() { reports.close(); }

    @Test void ownedReasonsUseEachRetainedLanguageAndUnchangedNativeProxyPayload() {
        for (String key : List.of("Economy_Error", "Trade_cancelled_by_attack")) {
            Player first = reports.player(Language.EN, "Alex"), second = reports.player(Language.ET, "Steve");
            List<String> wire = new ArrayList<>();Trade trade = ReportFixture.cancelling(first, second, wire);
            trade.presentationView(first);
            fixture.preferences.put(first.getUniqueId(), new LanguageSnapshot(Language.ET, LanguageSnapshot.State.EXPLICIT, 2));
            AtomicInteger finishes = new AtomicInteger();
            doAnswer(call -> {if (call.getArgument(0) instanceof TradeFinishEvent event) {
                assertFalse(event.getTradeResult());finishes.incrementAndGet();
            }return null;}).when(reports.plugins).callEvent(any());
            trade.cancelWithReason(key, key.equals("Economy_Error") ? null : first);
            trade.cancel();
            assertEquals(List.of("Native notice"), wire);assertEquals(1, finishes.get());
            var en = org.mockito.ArgumentCaptor.forClass(Component.class);
            var et = org.mockito.ArgumentCaptor.forClass(Component.class);
            verify(first).sendMessage(en.capture());verify(second).sendMessage(et.capture());
            assertTrue(PlayerTextTest.plain(en.getValue()).contains(key.equals("Economy_Error") ? "payment could not be completed" : "because of an attack"));
            assertTrue(PlayerTextTest.plain(et.getValue()).contains(key.equals("Economy_Error") ? "maksevea" : "rünnaku tõttu"));
            verify(first, never()).sendMessage(anyString());verify(second, never()).sendMessage(anyString());
            verify(reports.handler).unregisterTrade("Alex");verify(reports.handler).unregisterTrade("Steve");
            clearInvocations(reports.handler);
        }
    }

    @Test void explicitExternalAndReceivedProxyReasonsRemainLiteralWithoutEcho() {
        for (String external : List.of("§4OWNER <arg:reason> %value%", "Native notice")) for (boolean received : List.of(false, true)) {
            Player first = reports.player(Language.EN, "Alex"), second = reports.player(Language.ET, "Steve");
            List<String> wire = new ArrayList<>();Trade trade = ReportFixture.cancelling(first, second, wire);
            trade.cancel(external, received);
            assertEquals(received ? List.of() : List.of(external), wire);
            verify(first).sendMessage(external);verify(second).sendMessage(external);
            verify(first, never()).sendMessage(any(Component.class));verify(second, never()).sendMessage(any(Component.class));
        }
    }

    @Test void ordinaryCancellationAndDroppedNoticesUseWholeTradeViewForBothRecipients() {
        Player first = reports.player(Language.EN, "Alex"), second = reports.player(Language.ET, "Steve");
        Trade trade = ReportFixture.cancelling(first, second, new ArrayList<>());
        ReportFixture.droppedOnReturn(trade, true, true);
        trade.cancel();
        var en = org.mockito.ArgumentCaptor.forClass(Component.class);
        var et = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(first, times(2)).sendMessage(en.capture());verify(second, times(2)).sendMessage(et.capture());
        assertEquals("The trade was cancelled.", PlayerTextTest.plain(en.getAllValues().get(0)));
        assertTrue(PlayerTextTest.plain(en.getAllValues().get(1)).contains("items were dropped"));
        assertEquals("Tehing on tühistatud.", PlayerTextTest.plain(et.getAllValues().get(0)));
        assertTrue(PlayerTextTest.plain(et.getAllValues().get(1)).contains("kukku"));
    }

    @Test void noticePreparationAndSendFailuresDoNotInterruptOtherRecipientOrFinishEvent() {
        for (boolean renderFailure : List.of(false, true)) {
            Player first = reports.player(Language.EN, "Alex"), second = reports.player(Language.ET, "Steve");
            Trade trade = ReportFixture.cancelling(first, second, new ArrayList<>());
            if (renderFailure) doThrow(new IllegalStateException("synthetic")).when(trade).presentationView(first);
            else doThrow(new IllegalStateException("synthetic")).when(first).sendMessage(any(Component.class));
            AtomicInteger finished = new AtomicInteger();
            doAnswer(call -> {if (call.getArgument(0) instanceof TradeFinishEvent) finished.incrementAndGet();return null;})
                    .when(reports.plugins).callEvent(any());
            trade.cancel();
            assertEquals(1, finished.get());verify(second).sendMessage(any(Component.class));
            if (renderFailure) verify(first).sendMessage("Native notice");
            else verify(first, never()).sendMessage(anyString());
            verify(reports.handler).unregisterTrade("Alex");verify(reports.handler).unregisterTrade("Steve");
            clearInvocations(reports.handler);
        }
    }

    @Test void actualAttackListenerRoutesItsReasonThroughRecipientAwareCancellation() {
        Player first = reports.player(Language.EN, "Alex"), second = reports.player(Language.ET, "Steve");
        List<String> wire = new ArrayList<>();Trade trade = ReportFixture.cancelling(first, second, wire);
        when(fixture.owner.getTradeManager()).thenReturn(reports.handler);
        when(reports.handler.getTrade(first)).thenReturn(trade);when(reports.handler.isCancelOnDamage()).thenReturn(true);
        when(first.getHealth()).thenReturn(20D);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(first);when(damage.getFinalDamage()).thenReturn(1D);
        new TradeListener().onDeath(damage);
        assertEquals(List.of("Native notice"), wire);
        verify(first).sendMessage(argThat((Component output) -> PlayerTextTest.plain(output).contains("because of an attack")));
        verify(second).sendMessage(argThat((Component output) -> PlayerTextTest.plain(output).contains("rünnaku tõttu")));
    }
}
