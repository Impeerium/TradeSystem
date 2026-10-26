package de.codingair.tradesystem.spigot.localization;

import de.codingair.tradesystem.spigot.events.TradeRequestEvent;
import de.codingair.tradesystem.spigot.events.TradeRequestPreResponseEvent;
import de.codingair.tradesystem.spigot.events.utils.TradeEvent;
import de.codingair.tradesystem.spigot.extras.external.worldguard.WorldGuardAdapter;
import de.codingair.tradesystem.spigot.extras.external.worldguard.WorldGuardDependency;
import de.codingair.tradesystem.spigot.trade.ReportFixture;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.trade.gui.Actions;
import de.codingair.tradesystem.spigot.trade.gui.TradeGUIListener;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.impl.economy.EconomyIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.utils.Perspective;
import de.codingair.tradesystem.spigot.utils.Lang;
import net.impeerium.api.localization.Language;
import net.impeerium.api.localization.LanguageSnapshot;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerIntegrationTest {
    PlayerReportTest reports;
    PlayerTextTest fixture;
    WorldGuardDependency regions;

    @BeforeAll static void registry() { MockBukkit.mock(); }
    @AfterAll static void registryCleanup() { MockBukkit.unmock(); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BeforeEach void setup() throws Exception {
        reports = new PlayerReportTest();reports.setup();fixture = reports.fixture;
        regions = new WorldGuardDependency();
        WorldGuardAdapter adapter = new WorldGuardAdapter() {
            @Override public Stream<String> getRegion(Location location) {
                return location.getX() == 1 ? Stream.of("blocked") : Stream.empty();
            }
        };
        PlayerMenuTest.field(regions, WorldGuardDependency.class, "adapter", adapter);
        PlayerMenuTest.field(regions, WorldGuardDependency.class, "regions", Set.of("blocked"));
        Class mode = Class.forName(WorldGuardDependency.class.getName() + "$RegionMode");
        PlayerMenuTest.field(regions, WorldGuardDependency.class, "mode", Enum.valueOf(mode, "BLACKLIST"));
        fixture.lang.when(() -> Lang.get(anyString(), any(Lang.P[].class))).thenReturn("Native remote notice");
    }
    @AfterEach void close() { reports.close(); }

    Player player(Language language, String name, boolean blocked) {
        Player player = reports.player(language, name);
        Location location = new Location(player.getWorld(), blocked ? 1 : 0, 64, 0);
        when(player.getLocation()).thenReturn(location);
        return player;
    }

    <T extends TradeEvent> T nativeSink(T event) {
        T spy = spy(event);doNothing().when(spy).sendMessage(nullable(Player.class), anyString(), anyString());return spy;
    }

    void owned(Player target, String key) {
        verify(target).sendMessage(argThat((Component text) -> PlayerTextTest.plain(text)
                .equals(PlayerTextTest.plain(fixture.wording.capture(target).render(key)))));
        verify(target, never()).sendMessage(anyString());
    }

    @Test void actualRegionRequestAndAcceptDenialsReachTheirLocalRecipientInBothLanguages() {
        for (Language language : Language.values()) for (boolean senderBlocked : List.of(false, true)) {
            Player sender = player(language, "Alex", senderBlocked), receiver = player(language, "Steve", !senderBlocked);
            TradeRequestEvent request = nativeSink(new TradeRequestEvent(sender, receiver, 10));
            regions.onRequest(request);assertTrue(request.isCancelled());
            owned(sender, senderBlocked ? "Cannot_trade_in_region" : "Other_cannot_trade_in_region");
            verify(request, never()).sendMessage(any(), anyString(), anyString());
            clearInvocations(sender, receiver);
            TradeRequestPreResponseEvent response = nativeSink(new TradeRequestPreResponseEvent("Alex", sender.getUniqueId(), sender,
                    "Steve", receiver.getUniqueId(), receiver, true));
            regions.onPreResponse(response);assertTrue(response.isCancelled());
            owned(receiver, senderBlocked ? "Other_cannot_trade_in_region" : "Cannot_trade_in_region");
            verify(response, never()).sendMessage(any(), anyString(), anyString());
        }
    }

    @Test void nullRemoteRecipientRetainsNativeEventCallbackAndStringPayload() {
        Player receiver = player(Language.EN, "Steve", true);
        TradeRequestEvent request = nativeSink(new TradeRequestEvent("Alex", UUID.randomUUID(), receiver, 10));
        regions.onRequest(request);assertTrue(request.isCancelled());
        verify(request).sendMessage(isNull(), eq("Alex"), eq("Native remote notice"));
        verify(receiver, never()).sendMessage(any(Component.class));
        Player sender = player(Language.EN, "Alex", true);
        TradeRequestPreResponseEvent response = nativeSink(new TradeRequestPreResponseEvent("Alex", sender.getUniqueId(), sender,
                "Steve", UUID.randomUUID(), null, true));
        regions.onPreResponse(response);assertTrue(response.isCancelled());
        verify(response).sendMessage(isNull(), eq("Steve"), eq("Native remote notice"));
        verify(sender, never()).sendMessage(any(Component.class));
    }

    @Test void absentBackendKeepsLocalNativeCallbackAndFailedSendCannotUncancelRegionGate() {
        Player sender = player(Language.EN, "Alex", true), receiver = player(Language.ET, "Steve", false);
        TradeRequestEvent failed = nativeSink(new TradeRequestEvent(sender, receiver, 10));
        doThrow(new IllegalStateException("synthetic")).when(sender).sendMessage(any(Component.class));
        regions.onRequest(failed);assertTrue(failed.isCancelled());
        verify(failed, never()).sendMessage(any(), anyString(), anyString());
        PlayerText.close();
        TradeRequestEvent nativeEvent = nativeSink(new TradeRequestEvent(sender, receiver, 10));
        regions.onRequest(nativeEvent);assertTrue(nativeEvent.isCancelled());
        verify(nativeEvent).sendMessage(same(sender), eq("Alex"), eq("Native notice"));
    }

    @Test void rejectedResponseDoesNotRunRegionChecksOrSendFeedback() {
        Player sender = player(Language.EN, "Alex", true), receiver = player(Language.ET, "Steve", true);
        TradeRequestPreResponseEvent response = nativeSink(new TradeRequestPreResponseEvent("Alex", sender.getUniqueId(), sender,
                "Steve", receiver.getUniqueId(), receiver, false));
        regions.onPreResponse(response);assertFalse(response.isCancelled());
        verify(sender, never()).sendMessage(any(Component.class));verify(receiver, never()).sendMessage(any(Component.class));
    }

    @Test void actualItemAdmissionWarningsKeepTradeSnapshotAfterPreferenceChange() throws Exception {
        when(fixture.owner.getTradeManager()).thenReturn(reports.handler);
        var configure = TradeGUIListener.class.getDeclaredMethod("getConfiguration", Player.class, Trade.class, Perspective.class);
        configure.setAccessible(true);
        for (Language language : Language.values()) for (boolean blocked : List.of(false, true)) {
            Player viewer = reports.player(language, "Alex"), other = reports.player(Language.EN, "Steve");
            Trade trade = ReportFixture.completing(viewer, other, null, new ArrayList<>());trade.presentationView(viewer);
            fixture.preferences.put(viewer.getUniqueId(), new LanguageSnapshot(
                    language == Language.EN ? Language.ET : Language.EN, LanguageSnapshot.State.EXPLICIT, 2));
            UUID otherId = other.getUniqueId();
            when(reports.handler.isBlocked(same(trade), same(viewer), same(other), eq("Steve"),
                    eq(otherId), any(ItemStack.class))).thenReturn(blocked);
            doReturn(false).when(trade).fitsTrade(eq(Perspective.PRIMARY), anyList());
            Actions.Configuration configuration = (Actions.Configuration) configure.invoke(new TradeGUIListener(), viewer, trade, Perspective.PRIMARY);
            ItemStack item = reports.menu.item("Offered item", "Keep");ItemStack before = item.clone();
            assertFalse(configuration.isItemAllowedInInventory.apply(List.of(item), List.of(10)));
            assertEquals(before, item);
            var output = org.mockito.ArgumentCaptor.forClass(Component.class);verify(viewer).sendMessage(output.capture());
            String expected = blocked ? language == Language.EN ? "This item cannot be traded." : "Seda eset ei saa vahetada."
                    : language == Language.EN ? "The other player does not have enough inventory space." : "Teise mängija seljakotis pole piisavalt ruumi.";
            assertEquals(expected, PlayerTextTest.plain(output.getValue()));verify(reports.handler).playBlockSound(viewer);
        }
    }

    @Test void optionalGenericCurrencyUnitsAreOwnedWhilePersistentItemDataStaysNative() {
        for (String[] names : List.of(new String[]{"Token", "Tokens", "Žetoon", "Žetoonid"},
                new String[]{"PlayerPoint", "PlayerPoints", "Mängijapunkt", "Mängijapunktid"})) {
            EconomyIcon<?> icon = mock(EconomyIcon.class, withSettings().useConstructor(reports.menu.item("Native"), names[0], names[1], true)
                    .defaultAnswer(CALLS_REAL_METHODS));
            Player viewer = reports.player(Language.ET, "Alex");PlayerText.View view = fixture.wording.capture(viewer);
            assertTrue(view.owns(names[0]));assertTrue(view.owns(names[1]));
            assertEquals(names[2], PlayerTextTest.plain(icon.nameComponent(view, viewer, true)));
            assertEquals(names[3], PlayerTextTest.plain(icon.nameComponent(view, viewer, false)));
        }
    }

    @Test void nativeEmptyAdmissionWarningRemainsSuppressedWhileRejectingAndPlayingSound() throws Exception {
        PlayerText.close();when(fixture.owner.getTradeManager()).thenReturn(reports.handler);
        var configure = TradeGUIListener.class.getDeclaredMethod("getConfiguration", Player.class, Trade.class, Perspective.class);
        configure.setAccessible(true);
        for (boolean blocked : List.of(false, true)) {
            Player viewer = reports.player(Language.EN, "Alex"), other = reports.player(Language.ET, "Steve");
            Trade trade = ReportFixture.completing(viewer, other, null, new ArrayList<>());
            UUID otherId = other.getUniqueId();
            when(reports.handler.isBlocked(same(trade), same(viewer), same(other), eq("Steve"),
                    eq(otherId), any(ItemStack.class))).thenReturn(blocked);
            doReturn(false).when(trade).fitsTrade(eq(Perspective.PRIMARY), anyList());
            String key = blocked ? "Trade_Placed_Blocked_Item" : "Trade_Partner_No_Space";
            fixture.lang.when(() -> Lang.get(eq(key), same(viewer), any(Lang.P[].class))).thenReturn("");
            Actions.Configuration configuration = (Actions.Configuration) configure.invoke(new TradeGUIListener(), viewer, trade, Perspective.PRIMARY);
            assertFalse(configuration.isItemAllowedInInventory.apply(List.of(reports.menu.item("Native")), List.of(10)));
            verify(viewer, never()).sendMessage(anyString());verify(viewer, never()).sendMessage(any(Component.class));
            verify(reports.handler).playBlockSound(viewer);
        }
    }
}
