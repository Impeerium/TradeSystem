package de.codingair.tradesystem.spigot.localization;

import de.codingair.tradesystem.spigot.TradeSystem;
import de.codingair.tradesystem.spigot.trade.managers.CommandManager;
import de.codingair.tradesystem.spigot.utils.Lang;
import net.impeerium.api.ImpeeriumApi;
import net.impeerium.api.localization.*;
import net.impeerium.api.runtime.LocalizationCatalogValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerTextTest {
    TradeSystem owner;
    Plugin apiOwner;
    LocalizationService service;
    ApiPlayerText wording;
    Map<UUID, LanguageSnapshot> preferences;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<TradeSystem> tradeSystem;
    MockedStatic<Lang> lang;
    private static final String HOSTILE = "<click:run_command:/op>Alex</click>&c§4%other%";

    @BeforeEach void setup() throws Exception {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        owner = mock(TradeSystem.class);
        apiOwner = mock(Plugin.class);
        when(owner.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(owner.getResource(anyString())).thenAnswer(call -> resource(call.getArgument(0)));
        Constructor<?> constructor = Class.forName("net.impeerium.api.runtime.RuntimeLocalization")
                .getDeclaredConstructor(Plugin.class);
        constructor.setAccessible(true);
        service = (LocalizationService) constructor.newInstance(owner);
        try (InputStream common = resource("locales/common.yml")) {
            assertNotNull(common);
            service.registerCatalog(apiOwner, common);
        }
        wording = new ApiPlayerText(owner, service);
        preferences = new HashMap<>();
        service.registerProvider(apiOwner, id -> preferences.getOrDefault(id, LanguageSnapshot.unavailable()));
        tradeSystem = mockStatic(TradeSystem.class);
        tradeSystem.when(TradeSystem::getInstance).thenReturn(owner);
        CommandManager commands = mock(CommandManager.class);
        when(owner.getCommandManager()).thenReturn(commands);
        when(commands.getTradeAliases()).thenReturn(new String[]{"vaheta"});
        when(commands.getAcceptAliases()).thenReturn(new String[]{"jah"});
        when(commands.getDenyAliases()).thenReturn(new String[]{"ei"});
        when(commands.getToggleAliases()).thenReturn(new String[]{"luba"});
        lang = mockStatic(Lang.class, CALLS_REAL_METHODS);
        lang.when(Lang::getPrefix).thenReturn("");
        Field backend = PlayerText.class.getDeclaredField("backend");
        backend.setAccessible(true);
        backend.set(null, wording);
    }

    @AfterEach void close() {
        PlayerText.close();
        if (lang != null) lang.close();
        if (tradeSystem != null) tradeSystem.close();
        bukkit.close();
    }

    static InputStream resource(String name) {
        return PlayerTextTest.class.getClassLoader().getResourceAsStream(name);
    }

    static String plain(Component value) {
        return PlainTextComponentSerializer.plainText().serialize(value);
    }

    Player player(Language language) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        preferences.put(id, new LanguageSnapshot(language, LanguageSnapshot.State.EXPLICIT, 1));
        return player;
    }

    void enabledApiServer() {
        Server server = mock(Server.class);PluginManager plugins = mock(PluginManager.class);
        when(owner.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.isPluginEnabled("ImpeeriumAPI")).thenReturn(true);
    }

    @Test void realBootstrapConnectsAvailableServiceAndCanReinitializeWithoutChangingPreference() {
        enabledApiServer();Player viewer = player(Language.EN);LanguageSnapshot before = preferences.get(viewer.getUniqueId());
        try (var api = mockStatic(ImpeeriumApi.class)) {
            ImpeeriumApi facade = mock(ImpeeriumApi.class);when(facade.localization()).thenReturn(service);
            api.when(() -> ImpeeriumApi.require(owner, 1, 11)).thenReturn(facade);
            for (int i = 0; i < 2; i++) {
                PlayerText.initialize(owner);
                assertEquals("The trade is complete.", plain(PlayerText.capture(viewer).render("Trade_Was_Finished")));
                assertEquals(before, preferences.get(viewer.getUniqueId()));
            }
        }
    }

    @Test void unavailableOrIncompatibleServiceKeepsActualNativeSendRouting() {
        enabledApiServer();Player viewer = player(Language.EN);
        lang.when(() -> Lang.get(anyString(), same(viewer), any(Lang.P[].class))).thenReturn("Native fallback");
        for (Throwable failure : List.of(new IllegalStateException("synthetic"), new NoClassDefFoundError("synthetic"))) {
            try (var api = mockStatic(ImpeeriumApi.class)) {
                api.when(() -> ImpeeriumApi.require(owner, 1, 11)).thenThrow(failure);
                PlayerText.initialize(owner);assertNull(PlayerText.capture(viewer));
                Lang.send(viewer, "Trade_Was_Cancelled");verify(viewer).sendMessage("Native fallback");
                clearInvocations(viewer);
            }
        }
    }

    @Test void exactRuntimeValidatorAndAllPairedEntriesRender() throws Exception {
        try (InputStream input = resource("locales/messages.yml")) {
            LocalizationCatalogValidator.validate(input);
        }
        YamlConfiguration catalog;
        try (InputStream input = resource("locales/messages.yml")) {
            catalog = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        int count = 0;
        for (String key : catalog.getConfigurationSection("messages").getKeys(false)) {
            count++;
            var entry = catalog.getConfigurationSection("messages." + key);
            assertTrue(entry.contains("et"), key);
            assertTrue(entry.contains("en"), key);
            Map<String, Object> args = new HashMap<>();
            var signature = entry.getConfigurationSection("args");
            if (signature != null) for (String name : signature.getKeys(false)) {
                args.put(name, switch (signature.getString(name)) {
                    case "integer" -> 2;
                    case "component" -> Component.text(HOSTILE);
                    default -> HOSTILE;
                });
            }
            for (Language language : Language.values()) {
                String text = plain(wording.capture(player(language)).render(key, args));
                assertFalse(text.contains("Sõnum pole praegu saadaval"), key);
                assertFalse(text.contains("<arg:"), key);
            }
        }
        assertEquals(85, count);
    }

    @Test void recipientsAndRetainedViewAreIndependent() {
        Player et = player(Language.ET), en = player(Language.EN);
        PlayerText.View captured = wording.capture(en);
        assertEquals("Tehing on tühistatud.", plain(wording.capture(et).render("Trade_Was_Cancelled")));
        assertEquals("The trade was cancelled.", plain(captured.render("Trade_Was_Cancelled")));
        preferences.put(en.getUniqueId(), new LanguageSnapshot(Language.ET, LanguageSnapshot.State.EXPLICIT, 2));
        assertEquals("The trade was cancelled.", plain(captured.render("Trade_Was_Cancelled")));
        assertEquals("Tehing on tühistatud.", plain(wording.capture(en).render("Trade_Was_Cancelled")));
    }

    @Test void defaultLoadingFailureAndCachedPreferenceDoNotPretendToSave() {
        Player viewer = player(Language.EN);
        for (LanguageSnapshot state : List.of(LanguageSnapshot.loading(), LanguageSnapshot.unavailable(),
                new LanguageSnapshot(Language.ET, LanguageSnapshot.State.DEFAULT, 0))) {
            preferences.put(viewer.getUniqueId(), state);
            assertEquals("Tehing on tehtud.", plain(wording.capture(viewer).render("Trade_Was_Finished")));
        }
        preferences.put(viewer.getUniqueId(), new LanguageSnapshot(Language.EN, LanguageSnapshot.State.CACHED, 3));
        assertEquals("The trade is complete.", plain(wording.capture(viewer).render("Trade_Was_Finished")));
        assertFalse(preferences.get(viewer.getUniqueId()).confirmed());
    }

    @Test void literalNamesDoNotCreateFormattingOrClickEvents() {
        Component result = wording.capture(player(Language.EN)).render("request_was_accepted", Map.of("player", HOSTILE));
        assertEquals(HOSTILE + " accepted your trade request.", plain(result));
        assertFalse(hasClick(result));
    }

    @Test void structuredButtonsRetainEventsAndFonts() {
        Component button = Component.text("YES").font(net.kyori.adventure.key.Key.key("test:button"))
                .clickEvent(ClickEvent.runCommand("/vaheta jah Alex"));
        Component output = wording.capture(player(Language.EN)).render("want_to_trade",
                Map.of("player", HOSTILE, "accept", button, "deny", Component.text("NO")));
        assertEquals(HOSTILE + " wants to trade with you. YES NO", plain(output));
        assertTrue(output.toString().contains("test:button"));
        assertTrue(clickTargets(output).contains("/vaheta jah Alex"));
        assertEquals(1, clickTargets(output).size());
    }

    @Test void actualInvitationProducerUsesRecipientAndConfiguredAliases() {
        for (Language language : Language.values()) {
            Player recipient = player(language);
            assertTrue(PlayerText.invitation("Alex", recipient, false));
            var sent = org.mockito.ArgumentCaptor.forClass(Component.class);
            verify(recipient).sendMessage(sent.capture());
            Component output = sent.getValue();
            assertTrue(plain(output).contains(language == Language.ET ? "soovib sinuga kaubelda" : "wants to trade with you"));
            assertEquals(Set.of("/vaheta jah Alex", "/vaheta ei Alex"), new HashSet<>(clickTargets(output)));
        }
    }

    @Test void bedrockInvitationShowsWorkingAliasCommands() {
        Player recipient = player(Language.EN);
        assertTrue(PlayerText.invitation("Alex", recipient, true));
        var sent = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(recipient).sendMessage(sent.capture());
        assertTrue(plain(sent.getValue()).contains("Accept: /vaheta jah Alex; decline: /vaheta ei Alex"));
    }

    @Test void ownedChatFiltersNativeExtraArgumentsAndUsesCommonWording() {
        Player recipient = player(Language.EN);
        assertTrue(PlayerText.send(recipient, "", "Player_Not_Online", new Lang.P("player", HOSTILE)));
        verify(recipient).sendMessage(argThat((Component text) -> plain(text).equals("The player is offline.")));
        assertEquals("Back", plain(wording.capture(recipient).render("Back")));
        assertFalse(wording.capture(recipient).owns("Layout_Editor"));
        assertEquals("Sõnum pole praegu saadaval.", plain(wording.capture(recipient).render("request_was_accepted")));
        assertEquals("Sõnum pole praegu saadaval.", plain(wording.capture(recipient)
                .render("fancy_countdown", Map.of("seconds", HOSTILE))));
        assertEquals("Sõnum pole praegu saadaval.", plain(wording.capture(recipient).render("missing_key")));
    }

    @Test void actualHelpProducerShowsOnlyConfiguredAliases() {
        Player recipient = player(Language.EN);
        assertTrue(PlayerText.send(recipient, "", "Command_How_To"));
        var sent = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(recipient).sendMessage(sent.capture());
        assertEquals("Usage: /vaheta [player / jah / ei / luba]", plain(sent.getValue()));
    }

    @Test void queuedDuplicateCallbacksCanOnlyClaimOnce() {
        UUID recipient = UUID.randomUUID();
        var now = new java.util.concurrent.atomic.AtomicLong(1);
        CallbackGuard guard = new CallbackGuard(recipient, 10, now::get);
        assertFalse(guard.claim(UUID.randomUUID()));
        assertTrue(guard.claim(recipient));
        assertFalse(guard.claim(recipient));
    }

    @Test void queuedCallbackChecksExecutionTimeAgainstExactDeadline() {
        UUID recipient = UUID.randomUUID();
        var now = new java.util.concurrent.atomic.AtomicLong(100);
        CallbackGuard guard = new CallbackGuard(recipient, 10, now::get);
        now.set(110);
        assertFalse(guard.claim(recipient));
    }

    @Test void absentBackendAndConsoleKeepNativeFallback() {
        PlayerText.close();
        assertNull(PlayerText.capture(player(Language.EN)));
        assertFalse(PlayerText.send(player(Language.EN), "", "Trade_Was_Cancelled"));
        assertFalse(PlayerText.send(mock(org.bukkit.command.ConsoleCommandSender.class), "", "Trade_Was_Cancelled"));
    }

    static boolean hasClick(Component value) {
        return value.clickEvent() != null || value.children().stream().anyMatch(PlayerTextTest::hasClick);
    }

    static List<String> clickTargets(Component value) {
        List<String> values = new ArrayList<>();
        if (value.clickEvent() != null) values.add(((ClickEvent.Payload.Text) value.clickEvent().payload()).value());
        for (Component child : value.children()) values.addAll(clickTargets(child));
        return values;
    }
}
