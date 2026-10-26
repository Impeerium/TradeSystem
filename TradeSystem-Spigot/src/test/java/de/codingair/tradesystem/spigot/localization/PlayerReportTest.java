package de.codingair.tradesystem.spigot.localization;

import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.events.TradeFinishEvent;
import de.codingair.tradesystem.spigot.events.TradeReportEvent;
import de.codingair.tradesystem.spigot.extras.external.mmoitems.MMOItemsDependency;
import de.codingair.tradesystem.spigot.trade.*;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.impl.economy.EconomyIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.utils.Perspective;
import de.codingair.tradesystem.spigot.transfer.ProxyDataHandler;
import de.codingair.tradesystem.spigot.utils.Lang;
import de.codingair.tradesystem.spigot.utils.InputGUI;
import net.impeerium.api.localization.Language;
import net.impeerium.api.localization.LanguageSnapshot;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerReportTest {
    PlayerMenuTest menu;
    PlayerTextTest fixture;
    TradeHandler handler;
    PluginManager plugins;
    private static final String HOSTILE = "<click:run_command:/op>Alex</click>§4&c%value%";

    @BeforeAll static void registry() { MockBukkit.mock(); }
    @AfterAll static void registryCleanup() { MockBukkit.unmock(); }

    @BeforeEach void setup() throws Exception {
        menu = new PlayerMenuTest();menu.setup();fixture = menu.fixture;
        handler = mock(TradeHandler.class);
        fixture.tradeSystem.when(TradeSystem::handler).thenReturn(handler);
        when(handler.isTradeReportItems()).thenReturn(true);
        when(handler.isTradeReportEconomy()).thenReturn(true);
        when(handler.isOnlyDisplayNameInMessage()).thenReturn(true);
        when(handler.getInputGUI()).thenReturn(InputGUI.ANVIL);
        ProxyDataHandler proxy = mock(ProxyDataHandler.class);
        when(proxy.getServerName()).thenReturn("fixture");
        fixture.tradeSystem.when(TradeSystem::proxy).thenReturn(proxy);
        plugins = mock(PluginManager.class);
        fixture.bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        // Native fallback remains available; it must not call the failing component mapper again.
        fixture.lang.when(() -> Lang.get(anyString(), any(Player.class), any(Lang.P[].class))).thenReturn("Native notice");
    }

    @AfterEach void close() { menu.close(); }

    Player player(Language language, String name) {
        Player player = fixture.player(language);
        when(player.getName()).thenReturn(name);
        World world = mock(World.class);when(world.getName()).thenReturn("fixture");
        when(player.getWorld()).thenReturn(world);
        return player;
    }

    PlayerTradeResult result(Trade trade, Player viewer) { return new PlayerTradeResult(trade, viewer, Perspective.PRIMARY); }
    TradeReportEvent event(PlayerTradeResult result) { return new TradeReportEvent(result.getPlayer(), "Steve", UUID.randomUUID(), result); }
    static List<String> texts(List<TradeReports.Line> lines) {
        return lines.stream().map(line -> line.component() == null ? line.legacy() : PlayerTextTest.plain(line.component())).toList();
    }
    static List<ItemStack> copy(List<ItemStack> items) { return items.stream().map(ItemStack::clone).toList(); }

    @Test void actualReportsUseRetainedLanguageLiteralStyledNamesAndImmutableResultStacks() throws Exception {
        for (Language language : Language.values()) {
            Player viewer = player(language, "Alex");Trade trade = menu.trade();
            PlayerTradeResult result = result(trade, viewer);
            ItemStack source = menu.item("Native", "Keep lore");
            var meta = source.getItemMeta();
            meta.displayName(Component.text(HOSTILE, NamedTextColor.AQUA).font(Key.key("fixture:name")));
            source.setItemMeta(meta);source.setAmount(3);
            ReportFixture.add(result, source, true);
            source.setAmount(4);ReportFixture.add(result, source, true);
            source.setAmount(2);ReportFixture.add(result, source, false);
            var receiving = copy(result.getReceivingItems());var sending = copy(result.getSendingItems());
            var lines = TradeReports.prepare(trade, viewer, true, result, event(result));
            assertEquals(5, lines.size());
            assertEquals(language == Language.EN ? "The trade is complete." : "Tehing on tehtud.", texts(lines).getFirst());
            assertTrue(texts(lines).stream().anyMatch(text -> text.contains("7 × " + HOSTILE)));
            assertTrue(texts(lines).stream().anyMatch(text -> text.contains("2 × " + HOSTILE)));
            assertTrue(lines.stream().anyMatch(line -> line.component() != null && line.component().toString().contains("fixture:name")));
            assertFalse(lines.stream().filter(line -> line.component() != null).anyMatch(line -> PlayerTextTest.hasClick(line.component())));
            trade.presentationView(viewer);
            fixture.preferences.put(viewer.getUniqueId(), new LanguageSnapshot(
                    language == Language.EN ? Language.ET : Language.EN, LanguageSnapshot.State.EXPLICIT, 2));
            assertEquals(lines, TradeReports.prepare(trade, viewer, true, result, event(result)));
            result.buildItemReport(item -> {item.setAmount(99);return HOSTILE;});
            result.buildComponentItemReport(trade.presentationView(viewer), item -> {
                item.setAmount(123);item.setItemMeta(null);return Component.text(HOSTILE);
            });
            assertEquals(receiving, result.getReceivingItems());assertEquals(sending, result.getSendingItems());
            assertEquals(2, source.getAmount());
            when(handler.isOnlyDisplayNameInMessage()).thenReturn(false);
            Component name = PlayerTradeResult.componentItemName(source, "Paper");
            assertEquals("Paper (" + HOSTILE + ")", PlayerTextTest.plain(name));
            assertFalse(PlayerTextTest.hasClick(name));
            when(handler.isOnlyDisplayNameInMessage()).thenReturn(true);
        }
    }

    @Test void explicitNullEmptyAndNativeOverridesKeepTheirContractsAndSink() {
        Player viewer = player(Language.EN, "Alex");Trade trade = menu.trade();
        PlayerTradeResult result = result(trade, viewer);ReportFixture.add(result, menu.item("Item"), true);
        TradeReportEvent event = event(result);
        assertEquals(2, TradeReports.prepare(trade, viewer, false, result, event).size());
        event.setItemReport(List.of());event.setEconomyReport(List.of());
        assertEquals(1, TradeReports.prepare(trade, viewer, false, result, event).size());
        String owner = "§9OWNER <arg:object> " + HOSTILE;
        List<String> nativeList = new ArrayList<>(List.of(owner));event.setItemReport(nativeList);
        assertSame(nativeList, event.getItemReport());
        var lines = TradeReports.prepare(trade, viewer, false, result, event);
        assertEquals(owner, lines.get(1).legacy());assertNull(lines.get(1).component());
        lines.forEach(line -> line.send(viewer));verify(viewer).sendMessage(owner);
        event.setItemReport(null);event.setEconomyReport(null);
        assertEquals(2, TradeReports.prepare(trade, viewer, false, result, event).size());
    }

    @Test void actualMmoListenerProvidesOwnedNamesAndLaterOverrideTakesPrecedence() {
        Player viewer = player(Language.EN, "Alex");Trade trade = menu.trade();
        PlayerTradeResult result = result(trade, viewer);
        ItemStack item = menu.item("Branded item");ReportFixture.add(result, item, true);
        TradeReportEvent event = event(result);
        MMOItemsDependency integration = new MMOItemsDependency() {
            @Override protected String getMmoId(ItemStack ignored) { return "BRANDED_SWORD"; }
        };
        integration.onReport(event);
        assertNotNull(event.getItemReport());assertNotNull(event.getComponentItemNameMapper());
        when(handler.isOnlyDisplayNameInMessage()).thenReturn(false);
        assertTrue(texts(TradeReports.prepare(trade, viewer, false, result, event)).get(1).contains("Branded Sword (Branded item)"));
        event.setItemReport(List.of("owner replacement"));
        assertNull(event.getComponentItemNameMapper());
        assertEquals("owner replacement", TradeReports.prepare(trade, viewer, false, result, event).get(1).legacy());
    }

    @Test void ownedAssociationTracksEveryListMutationAndIgnoresOldListAliases() {
        Player viewer = player(Language.EN, "Alex");Trade trade = menu.trade();
        TradeReportEvent event = event(result(trade, viewer));
        Function<ItemStack, Component> mapper = item -> Component.text("owned");
        List<Consumer<List<String>>> mutations = List.of(
                list -> list.set(0, "edited"), list -> list.add("added"), List::clear,
                list -> {var iterator = list.iterator();iterator.next();iterator.remove();},
                list -> {var iterator = list.listIterator();iterator.next();iterator.set("edited");},
                list -> list.subList(0, 1).clear(), list -> list.replaceAll(String::toUpperCase),
                list -> list.sort(Comparator.reverseOrder()), list -> list.removeIf(value -> true));
        for (var mutation : mutations) {
            List<String> original = new ArrayList<>(List.of("legacy", "second"));
            event.setComponentItemReport(original, mapper);original.clear();
            assertEquals(2, event.getItemReport().size());assertSame(mapper, event.getComponentItemNameMapper());
            mutation.accept(event.getItemReport());assertNull(event.getComponentItemNameMapper());
        }
        event.setComponentItemReport(List.of("old"), mapper);List<String> old = event.getItemReport();
        event.setComponentItemReport(List.of("current"), mapper);old.clear();
        assertSame(mapper, event.getComponentItemNameMapper());assertEquals(List.of("current"), event.getItemReport());
        assertThrows(IndexOutOfBoundsException.class, () -> event.getItemReport().set(5, "invalid"));
        assertThrows(IndexOutOfBoundsException.class, () -> event.getItemReport().add(5, "invalid"));
        assertThrows(IndexOutOfBoundsException.class, () -> event.getItemReport().remove(5));
        assertSame(mapper, event.getComponentItemNameMapper());
        event.setItemReport(event.getItemReport());assertNull(event.getComponentItemNameMapper());
    }

    @Test void economyReportsKeepDirectionNativeFormattingAndScaleIndependentSingular() {
        for (Language language : Language.values()) for (String input : List.of("0", "1.00", "-1.00", "0.25", "-2.5")) {
            Player viewer = player(language, "Alex");Trade trade = menu.trade();PlayerTradeResult result = result(trade, viewer);
            EconomyIcon<?> icon = mock(EconomyIcon.class, withSettings().useConstructor(menu.item("Native"), "Coin", "Coins", true)
                    .defaultAnswer(CALLS_REAL_METHODS));
            BigDecimal diff = new BigDecimal(input);
            doReturn(diff).when(icon).getOverallDifference(trade, Perspective.PRIMARY);
            doAnswer(call -> ((BigDecimal) call.getArgument(3)).toPlainString()).when(icon)
                    .makeString(eq(trade), eq(Perspective.PRIMARY), same(viewer), any(), anyBoolean());
            ReportFixture.economy(result, icon);
            var lines = result.buildComponentEconomyReport(trade.presentationView(viewer));
            if (diff.signum() == 0) {assertTrue(lines.isEmpty());continue;}
            boolean singular = diff.abs().compareTo(BigDecimal.ONE) == 0;
            String unit = language == Language.EN ? singular ? "Coin" : "Coins" : singular ? "Münt" : "Mündid";
            assertEquals("  " + (diff.signum() > 0 ? "+" : "−") + " " + diff.abs().toPlainString() + " " + unit,
                    PlayerTextTest.plain(lines.getFirst()));
            verify(icon).makeString(same(trade), eq(Perspective.PRIMARY), same(viewer), eq(diff.abs()), eq(diff.signum() < 0));
        }
    }

    @Test void nativeReportCancellationDoesNotChangeIndependentSoundFlag() throws Exception {
        for (boolean sound : List.of(false, true)) {
            Player first = player(Language.EN, "Alex"), second = player(Language.ET, "Steve");
            Trade trade = ReportFixture.completing(first, second, null, new ArrayList<>());
            doAnswer(call -> {TradeReportEvent event = call.getArgument(0);event.setCancelled(true);event.setPlayFinishSound(sound);return null;})
                    .when(plugins).callEvent(isA(TradeReportEvent.class));
            ReportFixture.report(trade, Perspective.PRIMARY, true, result(trade, first));
            verify(first, never()).sendMessage(any(Component.class));
            verify(first, never()).sendMessage(anyString());
            verify(handler, times(sound ? 1 : 0)).playFinishSound(first);
        }
    }

    @Test void actualFinishContinuesSettlementAndCleanupAfterRenderSendOrSoundFailure() throws Exception {
        for (String failure : List.of("render", "send", "sound")) {
            List<String> order = new ArrayList<>();
            Player first = player(Language.EN, "Alex"), second = player(Language.ET, "Steve");
            EconomyIcon<?> icon = mock(EconomyIcon.class);
            when(icon.getOverallDifference(any(), any())).thenReturn(BigDecimal.ZERO);
            doAnswer(call -> {order.add("economy:" + call.getArgument(1));return null;})
                    .when(icon).onFinish(any(), any(), any(), anyBoolean());
            Trade trade = ReportFixture.completing(first, second, icon, order);
            if (failure.equals("render")) doThrow(new IllegalStateException("synthetic"))
                    .when(trade).presentationView(first);
            if (failure.equals("send")) doThrow(new IllegalStateException("synthetic"))
                    .when(first).sendMessage("first external detail");
            if (failure.equals("sound")) doThrow(new IllegalStateException("synthetic"))
                    .when(handler).playFinishSound(first);
            doAnswer(call -> {
                if (call.getArgument(0) instanceof TradeReportEvent report) {
                    order.add("report:" + report.getReceiver().getName());
                    if (failure.equals("send") && report.getReceiver() == first)
                        report.setEconomyReport(List.of("first external detail", "second external detail"));
                }
                if (call.getArgument(0) instanceof TradeFinishEvent finish) {assertTrue(finish.getTradeResult());order.add("finish");}
                return null;
            }).when(plugins).callEvent(any());
            assertTrue(ReportFixture.finish(trade).join());
            assertEquals(List.of("items:PRIMARY", "items:SECONDARY", "report:Alex", "report:Steve",
                    "economy:PRIMARY", "economy:SECONDARY", "finish"), order);
            verify(handler).unregisterTrade("Alex");verify(handler).unregisterTrade("Steve");
            verify(second).sendMessage(any(Component.class));
            if (failure.equals("render")) verify(first).sendMessage("Native notice");
            if (failure.equals("send")) {
                verify(first, times(1)).sendMessage(any(Component.class));
                verify(first, times(1)).sendMessage("first external detail");
                verify(first, never()).sendMessage("second external detail");
                verify(first, never()).sendMessage("Native notice");
            }
            verify(icon).onFinish(same(trade), eq(Perspective.PRIMARY), same(first), eq(true));
            verify(icon).onFinish(same(trade), eq(Perspective.SECONDARY), same(second), eq(true));
            clearInvocations(handler);
        }
    }
}
