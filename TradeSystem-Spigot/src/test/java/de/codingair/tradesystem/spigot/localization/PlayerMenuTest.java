package de.codingair.tradesystem.spigot.localization;

import com.github.Anon8281.universalScheduler.UniversalRunnable;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import de.codingair.codingapi.player.gui.inventory.v2.GUI;
import de.codingair.codingapi.player.gui.inventory.v2.InventoryBuilder;
import de.codingair.codingapi.tools.items.ItemBuilder;
import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.trade.TradeHandler;
import de.codingair.tradesystem.spigot.trade.gui.TradingGUI;
import de.codingair.tradesystem.spigot.trade.gui.TradingPage;
import de.codingair.tradesystem.spigot.trade.gui.layout.TradeLayout;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.TradeIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.feedback.IconResult;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.ItemPrepareIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.gui.SimpleSignGUIIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.impl.basic.*;
import de.codingair.tradesystem.spigot.trade.gui.layout.types.impl.economy.EconomyIcon;
import de.codingair.tradesystem.spigot.trade.gui.layout.utils.Perspective;
import de.codingair.tradesystem.spigot.utils.InputGUI;
import de.codingair.tradesystem.spigot.utils.FloodgateUtils;
import de.codingair.tradesystem.spigot.utils.Lang;
import net.impeerium.api.localization.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerMenuTest {
    PlayerTextTest fixture;

    @BeforeAll static void registry() { MockBukkit.mock(); }
    @AfterAll static void registryCleanup() { MockBukkit.unmock(); }

    @BeforeEach void setup() throws Exception {
        fixture = new PlayerTextTest();
        fixture.setup();
        fixture.bukkit.close();
        fixture.bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        fixture.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
    }

    @AfterEach void close() { fixture.close(); }

    Trade trade() {
        return mock(Trade.class, withSettings().useConstructor("Alex", "Steve", true).defaultAnswer(CALLS_REAL_METHODS));
    }

    static void field(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    record OpenMenu(Trade trade, Player viewer, TradingGUI gui, TradingPage page,
                    Inventory inventory, InventoryView openView, ComponentTitleTransport transport) {}

    OpenMenu openMenu(Language language) throws Exception {
        Trade trade = trade();
        Player viewer = fixture.player(language);
        when(viewer.getName()).thenReturn("Alex");
        when(viewer.isOnline()).thenReturn(true);
        Inventory inventory = Bukkit.createInventory(null, 54, Component.text("fixture"));
        InventoryView openView = mock(InventoryView.class);
        when(openView.getTopInventory()).thenReturn(inventory);
        when(viewer.getOpenInventory()).thenReturn(openView);
        TradingGUI gui = mock(TradingGUI.class, CALLS_REAL_METHODS);
        ComponentTitleTransport transport = mock(ComponentTitleTransport.class);
        field(gui, InventoryBuilder.class, "player", viewer);
        field(gui, InventoryBuilder.class, "inventory", inventory);
        field(gui, TradingGUI.class, "trade", trade);
        field(gui, TradingGUI.class, "presentation", trade.presentationView(viewer));
        field(gui, TradingGUI.class, "titleSource", TradeTitles.NATIVE_TITLE);
        field(gui, TradingGUI.class, "countdownSource", "\uF810");
        field(gui, TradingGUI.class, "titleTransport", transport);
        // These stay equal throughout the test, reproducing the native cache's early return.
        field(gui, GUI.class, "title", "native original");
        field(gui, GUI.class, "originalTitle", "native original");
        doReturn(true).when(gui).isOpen();
        TradeLayout layout = mock(TradeLayout.class);
        when(layout.getIcons()).thenReturn(new TradeIcon[54]);
        trade.getLayout()[0] = layout;
        trade.getGUIs()[0] = gui;
        TradingPage page = new TradingPage(gui, trade, 0);
        doReturn(page).when(gui).getActive();
        return new OpenMenu(trade, viewer, gui, page, inventory, openView, transport);
    }

    ItemStack item(String name, String... lore) {
        ItemStack item = new ItemStack(Material.PAPER, 3);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(Arrays.stream(lore).map(Component::text).toList());
        meta.setCustomModelData(42);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(new NamespacedKey("fixture", "keep"), PersistentDataType.STRING, "synthetic");
        item.setItemMeta(meta);
        return item;
    }

    static List<String> lore(ItemStack item) {
        var lore = item.getItemMeta().lore();
        return lore == null ? List.of() : lore.stream().map(PlayerItems::plain).toList();
    }

    static ItemStack withoutText(ItemStack original) {
        ItemStack copy = original.clone();
        var meta = copy.getItemMeta();
        meta.displayName(null);meta.lore(null);copy.setItemMeta(meta);
        return copy;
    }

    @Test void actualTradeOwnsOneSnapshotAcrossNestedInputsAndChangingPreference() {
        Trade trade = trade();
        Player en = fixture.player(Language.EN), et = fixture.player(Language.ET);
        var retained = trade.presentationView(en);
        assertEquals("Your offer", PlayerText.caption(trade, en, "Economy_Offer_Title"));
        fixture.preferences.put(en.getUniqueId(), new LanguageSnapshot(Language.ET, LanguageSnapshot.State.EXPLICIT, 2));
        assertSame(retained, trade.presentationView(en));
        assertEquals("Your offer", PlayerText.caption(trade, en, "Economy_Offer_Title"));
        assertEquals("Sinu pakkumine", PlayerText.caption(trade, et, "Economy_Offer_Title"));
        assertEquals("Sinu pakkumine", PlayerText.caption(trade(), en, "Economy_Offer_Title"));
        assertThrows(IllegalArgumentException.class, () -> PlayerText.caption(trade, en, "request_was_accepted"));
    }

    @Test void finalNamesAndLorePreserveEveryOtherItemPropertyAndLiteralInput() {
        ItemStack source = item("Native", "Native lore");
        ItemStack before = source.clone();
        String hostile = "<click:run_command:/op>Alex</click>§4&c%value%";
        ItemStack translated = PlayerItems.text(source, Component.text(hostile), List.of(Component.text(hostile)));
        assertEquals(before, source);
        assertEquals(hostile, PlayerItems.plain(translated.getItemMeta().displayName()));
        assertEquals(List.of(hostile), lore(translated));
        assertFalse(PlayerTextTest.hasClick(translated.getItemMeta().displayName()));
        assertEquals(TextDecoration.State.FALSE, translated.getItemMeta().displayName().decoration(TextDecoration.ITALIC));
        assertEquals(withoutText(before), withoutText(translated));
    }

    @Test void readyControlPreservesCustomLoreAndUsesWholeViewLanguage() {
        Trade trade = trade();Player viewer = fixture.player(Language.EN);
        fixture.lang.when(() -> Lang.get("Wait_For_Other_Player", viewer)).thenReturn("Oota teist mängijat...");
        ItemStack nativeItem = item("Native", "Custom information", "", "Oota teist mängijat...");
        var icon = new StatusIcon.ReadyIcon(nativeItem);
        ItemStack result = icon.finishPresentation(nativeItem, trade, Perspective.PRIMARY, viewer);
        assertEquals("Status: ready", PlayerItems.plain(result.getItemMeta().displayName()));
        assertEquals(List.of("Custom information", "", "Wait for the other player…"), lore(result));
        assertEquals(withoutText(nativeItem), withoutText(result));
        ItemStack changed = item("Native", "Custom information", "Owner changed this hint");
        assertEquals(lore(changed), lore(icon.finishPresentation(changed, trade, Perspective.PRIMARY, viewer)));
    }

    @Test void allNativeStatusVariantsUseTheActualRecipient() {
        for (Language language : Language.values()) {
            Trade trade = trade();Player viewer = fixture.player(language);ItemStack source = item("Native", "Keep");
            String suffix = language == Language.EN ? "not ready" : "pole valmis";
            var own = new StatusIcon.NotReadyIcon(source).finishPresentation(source, trade, Perspective.PRIMARY, viewer);
            var other = new ShowStatusIcon.ShowNotReadyIcon(source).finishPresentation(source, trade, Perspective.PRIMARY, viewer);
            assertTrue(PlayerItems.plain(own.getItemMeta().displayName()).endsWith(suffix));
            assertEquals(own.getItemMeta().displayName(), other.getItemMeta().displayName());
            var cancel = new CancelIcon(source).finishPresentation(source, trade, Perspective.PRIMARY, viewer);
            assertEquals(language == Language.EN ? "Cancel trade" : "Tühista tehing", PlayerItems.plain(cancel.getItemMeta().displayName()));
            assertEquals(List.of("Keep"), lore(cancel));
        }
    }

    @Test void fixedHelpBindingPreservesHiddenNameAndCustomOwnerCopy() {
        Player viewer = fixture.player(Language.EN);Trade trade = trade();
        ItemStack definition = item("Kaubandus", "", "Otsid viisi kaasmängijatega kaubelda?", "Siin avaneb sulle see võimalus!");
        var icon = new DecorationIcon(definition);
        ItemStack nativeItem = item("", "", "Otsid viisi kaasmängijatega kaubelda?", "Siin avaneb sulle see võimalus!");
        ItemStack result = icon.finishPresentation(nativeItem, trade, Perspective.PRIMARY, viewer);
        assertEquals("", PlayerItems.plain(result.getItemMeta().displayName()));
        assertEquals(List.of("", "Place the items you want to trade here.", "When the offer is right, mark yourself ready."), lore(result));
        assertEquals(withoutText(nativeItem), withoutText(result));
        var custom = new DecorationIcon(item("Kaubandus", "Owner-authored instructions"));
        assertSame(nativeItem, custom.finishPresentation(nativeItem, trade, Perspective.PRIMARY, viewer));
    }

    @Test void nativeBuilderFinishesBeforeTheComponentSink() {
        ItemStack nativeItem = item("Native", "Native lore");
        ItemBuilder builder = mock(ItemBuilder.class);
        when(builder.getItem()).thenReturn(nativeItem);
        List<String> order = new ArrayList<>();
        var icon = new ItemPrepareIcon() {
            @Override public ItemBuilder prepareItemStack(ItemBuilder layout, Trade t, Perspective p, Player v) {
                order.add("native");return layout;
            }
            @Override public ItemStack finishPresentation(ItemStack item, Trade t, Perspective p, Player v) {
                order.add("component");assertSame(nativeItem, item);
                return PlayerItems.named(item, Component.text("<red>literal"));
            }
        };
        var result = icon.buildItemStack(builder, trade(), Perspective.PRIMARY, fixture.player(Language.EN));
        assertEquals(List.of("native", "component"), order);
        assertEquals("<red>literal", PlayerItems.plain(result.getItemMeta().displayName()));
        verify(builder, times(1)).getItem();
    }

    @SuppressWarnings("unchecked")
    @Test void actualSignProducerKeepsNativeEditableAmountAndBoundedInstructionRows() {
        for (Language language : Language.values()) {
            Player viewer = fixture.player(language);Trade trade = trade();
            SimpleSignGUIIcon<BigDecimal> sign = mock(SimpleSignGUIIcon.class,
                    withSettings().useConstructor(item("Native")).defaultAnswer(CALLS_REAL_METHODS));
            doReturn(new BigDecimal("1234.5")).when(sign).getValue();
            doReturn("1,234.5K").when(sign).makeString(any(), any(), any(), any(), anyBoolean());
            String[] lines = sign.buildSignLines(trade, Perspective.PRIMARY, viewer);
            assertEquals(4, lines.length);assertEquals("1,234.5K", lines[0]);
            assertEquals(language == Language.EN ? "Enter amount" : "Sisesta kogus", lines[2]);
            assertTrue(Arrays.stream(lines).allMatch(Objects::nonNull));
        }
    }

    @SuppressWarnings("unchecked")
    @Test void nativeMoneyFormatRoundTripsIdenticallyForBothLanguages() {
        TradeHandler handler = mock(TradeHandler.class);
        fixture.tradeSystem.when(TradeSystem::handler).thenReturn(handler);
        when(handler.getInputGUI()).thenReturn(InputGUI.ANVIL);
        DecimalFormat format = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.US));
        format.setParseBigDecimal(true);when(handler.getMoneyPattern()).thenReturn(format);
        EconomyIcon<?> icon = mock(EconomyIcon.class, withSettings().useConstructor(item("Native"), "Coin", "Coins", true)
                .defaultAnswer(CALLS_REAL_METHODS));
        BigDecimal value = new BigDecimal("1234.5");
        for (Language language : Language.values()) {
            Player viewer = fixture.player(language);Trade trade = trade();
            String display = icon.makeString(trade, Perspective.PRIMARY, viewer, value, true);
            assertEquals("1,234.5", display);
            assertEquals(0, value.compareTo(icon.convertInput(display)));
        }
    }

    @Test void productionTitlesExportBothOffsetsLanguagesAndLockStates() throws Exception {
        Map<String, String> titles = new LinkedHashMap<>();
        for (Language language : Language.values()) for (int offset : List.of(188,194)) for (boolean locked : List.of(false,true)) {
            var view = fixture.wording.capture(fixture.player(language));
            String source = TradeTitles.NATIVE_TITLE.replace("_-188:", "_-" + offset + ":");
            Component title = TradeTitles.create(view, source, "\uF810", locked);
            assertNotNull(title);assertTrue(TradeTitles.headingFits(view));
            assertTrue(PlayerItems.plain(title).contains(language == Language.EN ? "TRADE" : "KAUBANDUS"));
            String json = GsonComponentSerializer.gson().serialize(title);
            assertEquals(title, GsonComponentSerializer.gson().deserialize(json));
            assertTrue(json.contains("impeerium:tradesystem_frames"));assertTrue(json.contains("impeerium:gui_label_p27"));
            assertEquals(locked, PlayerItems.plain(title).contains("\uF810"));
            titles.put(language.id() + "-offset" + offset + "-" + (locked ? "locked" : "open"), json);
        }
        assertNull(TradeTitles.create(fixture.wording.capture(fixture.player(Language.EN)), "Owner custom title", "\uF810", false));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/trade-titles.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(titles));
    }

    @Test void nativeReadyAndOfferChangesClearLockThroughRealPageRebuild() throws Exception {
        try (var scheduler = mockStatic(UniversalScheduler.class, RETURNS_DEEP_STUBS)) {
            TradeHandler handler = mock(TradeHandler.class);
            fixture.tradeSystem.when(TradeSystem::handler).thenReturn(handler);
            when(handler.isRevokeReadyOnChange()).thenReturn(true);
            for (Language language : Language.values()) for (boolean offerChange : List.of(false, true)) {
                OpenMenu menu = openMenu(language);
                ItemStack offer = item("Offered item", "Keep this item");
                menu.inventory.setItem(10, offer);
                ItemStack cursor = item("Carried item");
                when(menu.viewer.getItemOnCursor()).thenReturn(cursor);
                UniversalRunnable countdown = mock(UniversalRunnable.class);
                field(menu.trade, Trade.class, "countdown", countdown);
                menu.trade.getReady()[0] = menu.trade.getReady()[1] = true;
                menu.gui.synchronizeTitle();

                if (offerChange) menu.trade.onTradeOfferChange(true);
                else menu.trade.handleClickResult(null, Perspective.PRIMARY, Perspective.PRIMARY,
                        menu.gui, IconResult.NOT_READY);

                assertNull(menu.trade.getCountdown());
                assertFalse(menu.trade.getReady()[0]);
                verify(countdown).cancel();
                var titles = org.mockito.ArgumentCaptor.forClass(Component.class);
                verify(menu.transport, times(2)).update(same(menu.viewer), same(menu.inventory), titles.capture());
                assertTrue(PlayerItems.plain(titles.getAllValues().get(0)).contains("\uF810"));
                assertFalse(PlayerItems.plain(titles.getAllValues().get(1)).contains("\uF810"));
                assertTrue(PlayerItems.plain(titles.getAllValues().get(1))
                        .contains(language == Language.EN ? "TRADE" : "KAUBANDUS"));
                assertSame(menu.inventory, menu.gui.getInventory());
                assertEquals(offer, menu.inventory.getItem(10));
                assertSame(cursor, menu.viewer.getItemOnCursor());
                assertEquals("native original", menu.gui.getTitle());
                verify(menu.gui).updateTitle(isNull());
                verify(menu.viewer, never()).openInventory(any(Inventory.class));
                verify(menu.viewer, never()).setItemOnCursor(any());
            }
        }
    }

    @Test void nativePageResetRetainsClosedNestedCancelledOfflineAndBedrockGuards() throws Exception {
        OpenMenu menu = openMenu(Language.EN);
        doReturn(false).when(menu.gui).isOpen();
        menu.page.rebuild();
        doReturn(true).when(menu.gui).isOpen();
        field(menu.gui, GUI.class, "waiting", true);
        menu.page.rebuild();
        field(menu.gui, GUI.class, "waiting", false);
        field(menu.trade, Trade.class, "cancelling", true);
        menu.page.rebuild();
        field(menu.trade, Trade.class, "cancelling", false);
        when(menu.viewer.isOnline()).thenReturn(false);
        menu.page.rebuild();
        when(menu.viewer.isOnline()).thenReturn(true);
        Inventory otherInventory = Bukkit.createInventory(null, 9);
        when(menu.openView.getTopInventory()).thenReturn(otherInventory);
        menu.page.rebuild();
        when(menu.openView.getTopInventory()).thenReturn(menu.inventory);
        try (var floodgate = mockStatic(FloodgateUtils.class)) {
            floodgate.when(() -> FloodgateUtils.isBedrockPlayer(menu.viewer)).thenReturn(true);
            menu.page.rebuild();
        }
        verifyNoInteractions(menu.transport);
    }

    @Test void explicitNativeTitleInvalidatesComponentCacheBeforePageReset() throws Exception {
        OpenMenu menu = openMenu(Language.EN);
        menu.gui.synchronizeTitle();
        // Use the superclass's matching-string branch: no NMS packet sender exists in MockBukkit.
        // The component route must invalidate even when the native string cache returns early.
        menu.gui.updateTitle("native original");
        menu.page.rebuild();
        var order = inOrder(menu.transport);
        var titles = org.mockito.ArgumentCaptor.forClass(Component.class);
        order.verify(menu.transport).update(same(menu.viewer), same(menu.inventory), titles.capture());
        order.verify(menu.transport).invalidate();
        order.verify(menu.transport).update(same(menu.viewer), same(menu.inventory), titles.capture());
        assertEquals(titles.getAllValues().get(0), titles.getAllValues().get(1));
        assertSame(menu.inventory, menu.gui.getInventory());
        verify(menu.viewer, never()).openInventory(any(Inventory.class));
    }
}
