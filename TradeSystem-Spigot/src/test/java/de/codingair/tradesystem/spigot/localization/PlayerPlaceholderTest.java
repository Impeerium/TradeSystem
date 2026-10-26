package de.codingair.tradesystem.spigot.localization;

import com.github.Anon8281.universalScheduler.UniversalRunnable;
import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.extras.external.placeholderapi.PlaceholderDependency;
import de.codingair.tradesystem.spigot.extras.external.placeholderapi.TradeSystemPlaceholder;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.trade.TradeHandler;
import de.codingair.tradesystem.spigot.utils.Lang;
import net.impeerium.api.localization.Language;
import net.impeerium.api.localization.LanguageSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerPlaceholderTest {
    PlayerMenuTest menu;
    PlayerTextTest fixture;
    TradeHandler handler;
    TradeSystemPlaceholder expansion;

    @BeforeAll static void registry() { MockBukkit.mock(); }
    @AfterAll static void registryCleanup() { MockBukkit.unmock(); }
    @BeforeEach void setup() throws Exception {
        menu = new PlayerMenuTest();menu.setup();fixture = menu.fixture;
        handler = mock(TradeHandler.class);fixture.tradeSystem.when(TradeSystem::handler).thenReturn(handler);
        when(handler.getCountdownInterval()).thenReturn(20);when(handler.getCountdownRepetitions()).thenReturn(3);
        PluginManager plugins = mock(PluginManager.class);
        fixture.bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        new PlaceholderDependency();expansion = new TradeSystemPlaceholder();
    }
    @AfterEach void close() { menu.close(); }

    Trade countdown(Player player) {
        Trade trade = menu.trade();when(handler.getTrade(player)).thenReturn(trade);
        doReturn(mock(UniversalRunnable.class)).when(trade).getCountdown();doReturn(1).when(trade).getCountdownTicks();
        return trade;
    }

    @Test void statusMeansRequestAvailabilityAndUsesRecipientOrRetainedTradeLanguage() {
        for (Language language : Language.values()) {
            Player viewer = fixture.player(language);
            assertEquals(language == Language.EN ? "On" : "Sees", expansion.onPlaceholderRequest(viewer, "status"));
            when(handler.isOffline(viewer)).thenReturn(true);
            assertEquals(language == Language.EN ? "Off" : "Väljas", expansion.onPlaceholderRequest(viewer, "STATUS"));
            Trade trade = countdown(viewer);trade.presentationView(viewer);
            fixture.preferences.put(viewer.getUniqueId(), new LanguageSnapshot(
                    language == Language.EN ? Language.ET : Language.EN, LanguageSnapshot.State.EXPLICIT, 2));
            assertEquals(language == Language.EN ? "Off" : "Väljas", expansion.onPlaceholderRequest(viewer, "status"));
        }
    }

    @Test void actualCountdownKeepsConfiguredArtAndCustomFormatsAndLocalizesOnlyShippedText() {
        for (Language language : Language.values()) {
            Player viewer = fixture.player(language);Trade trade = countdown(viewer);
            fixture.lang.when(() -> Lang.source("Fancy_Countdown")).thenReturn("\uF810");
            fixture.lang.when(() -> Lang.get("Fancy_Countdown")).thenReturn("\uF810");
            assertEquals("\uF810", expansion.onPlaceholderRequest(viewer, "countdown_fancy"));
            assertEquals("2", expansion.onPlaceholderRequest(viewer, "countdown"));
            fixture.lang.when(() -> Lang.source("Fancy_Countdown")).thenReturn("Owner %seconds% <custom>");
            fixture.lang.when(() -> Lang.get("Fancy_Countdown")).thenReturn("Owner %seconds% <custom>");
            assertEquals("Owner 2 <custom>", expansion.onPlaceholderRequest(viewer, "countdown_fancy"));
            fixture.lang.when(() -> Lang.source("Fancy_Countdown")).thenReturn(" &7- &e%seconds%s");
            fixture.lang.when(() -> Lang.get("Fancy_Countdown")).thenReturn(" §7- §e%seconds%s");
            assertEquals(language == Language.EN ? " §7- §e2s" : " §7- §e2 s", expansion.onPlaceholderRequest(viewer, "countdown_fancy"));
            doReturn(null).when(trade).getCountdown();
            assertEquals("", expansion.onPlaceholderRequest(viewer, "countdown_fancy"));
        }
    }

    @Test void fallbackConversionHandlesMultipleLengthsUnknownsAndLiteralReplacementMetacharacters() {
        Player viewer = fixture.player(Language.EN);when(viewer.getName()).thenReturn("Alex");Trade trade = countdown(viewer);
        doReturn("Partner$\\literal").when(trade).getOther("Alex");
        assertEquals("Partner$\\literal | On | 0 | %tradesystem_unknown%", PlaceholderDependency.convert(
                "%tradesystem_partner% | %TRADESYSTEM_STATUS% | %tradesystem_num_trades% | %tradesystem_unknown%", viewer));
        assertEquals("true", expansion.onPlaceholderRequest(viewer, "is_trading"));
    }

    @Test void nullPapiRecipientDoesNotReachTradeOrLanguageServices() {
        assertNull(expansion.onPlaceholderRequest(null, "status"));
        assertNull(expansion.onPlaceholderRequest(null, "countdown_fancy"));
        verifyNoInteractions(handler);
    }

    @Test void absentBackendRetainsNativeAvailabilityAndTextCountdownFallbacks() {
        PlayerText.close();Player viewer = fixture.player(Language.EN);countdown(viewer);
        fixture.lang.when(() -> Lang.get("Online")).thenReturn("Native on");
        fixture.lang.when(() -> Lang.get("Offline")).thenReturn("Native off");
        fixture.lang.when(() -> Lang.source("Fancy_Countdown")).thenReturn(" &7- &e%seconds%s");
        fixture.lang.when(() -> Lang.get("Fancy_Countdown")).thenReturn(" §7- §e%seconds%s");
        assertEquals("Native on", expansion.onPlaceholderRequest(viewer, "status"));
        when(handler.isOffline(viewer)).thenReturn(true);
        assertEquals("Native off", expansion.onPlaceholderRequest(viewer, "status"));
        assertEquals(" §7- §e2s", expansion.onPlaceholderRequest(viewer, "countdown_fancy"));
    }
}
