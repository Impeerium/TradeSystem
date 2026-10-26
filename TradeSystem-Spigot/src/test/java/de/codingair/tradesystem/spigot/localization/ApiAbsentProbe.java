package de.codingair.tradesystem.spigot.localization;

import de.codingair.codingapi.server.specification.Version;
import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.trade.Trade;
import de.codingair.tradesystem.spigot.trade.gui.TradingGUI;
import de.codingair.tradesystem.spigot.trade.gui.layout.Pattern;
import de.codingair.tradesystem.spigot.trade.gui.layout.TradeLayout;
import de.codingair.tradesystem.spigot.utils.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Child JVM entry point; deliberately has no Impeerium API references or imports. */
public final class ApiAbsentProbe {
    public static void main(String[] ignored) throws Exception {
        for (String type : List.of("net.impeerium.api.ImpeeriumApi", "net.impeerium.api.localization.LocalizationService",
                "net.impeerium.api.text.GuiText")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
        }
        MockBukkit.mock();
        try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
             var nativeText = mockStatic(Lang.class, CALLS_REAL_METHODS);
             var nativeOwner = mockStatic(TradeSystem.class);
             var nativeVersion = mockStatic(Version.class)) {
            TradeSystem owner = mock(TradeSystem.class);
            Server server = mock(Server.class);PluginManager plugins = mock(PluginManager.class);
            when(owner.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(plugins);
            when(owner.getLogger()).thenReturn(Logger.getAnonymousLogger());
            nativeOwner.when(TradeSystem::getInstance).thenReturn(owner);
            Player viewer = mock(Player.class);when(viewer.getName()).thenReturn("Alex");
            when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
            nativeText.when(Lang::getPrefix).thenReturn("");
            nativeText.when(() -> Lang.get(anyString(), same(viewer), any(Lang.P[].class))).thenReturn("Native fallback");
            // Test both normal absence and a stale enabled flag with missing classes, then recover/retry.
            for (boolean enabled : List.of(false, true, false, true)) {
                when(plugins.isPluginEnabled("ImpeeriumAPI")).thenReturn(enabled);
                PlayerText.initialize(owner);assertNull(PlayerText.capture(viewer));
                assertFalse(PlayerText.invitation("Steve", viewer, false));assertFalse(PlayerText.offlineNotice(viewer));
                Lang.send(viewer, "Trade_Was_Cancelled");verify(viewer).sendMessage("Native fallback");
                Trade trade = mock(Trade.class, withSettings().useConstructor("Alex", "Steve", true).defaultAnswer(CALLS_REAL_METHODS));
                TradeLayout layout = mock(TradeLayout.class);Pattern pattern = mock(Pattern.class);
                when(layout.getPattern()).thenReturn(pattern);when(pattern.getSize()).thenReturn(54);
                trade.getLayout()[0] = layout;
                TradingGUI gui = new TradingGUI(viewer, trade, 0);
                assertEquals(54, gui.getInventory().getSize());assertEquals("Native fallback", gui.getTitle());
                assertNull(trade.presentationView(viewer));
                PlayerText.close();clearInvocations(viewer);
            }
            verify(owner, never()).getResource(anyString());
            System.out.println("API_ABSENT_BOOTSTRAP_AND_NATIVE_GUI_PASSED");
        } finally {
            PlayerText.close();MockBukkit.unmock();
        }
    }
}
