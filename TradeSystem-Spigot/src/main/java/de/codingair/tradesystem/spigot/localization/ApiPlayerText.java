package de.codingair.tradesystem.spigot.localization;

import de.codingair.tradesystem.spigot.TradeSystem;
import net.impeerium.api.ImpeeriumApi;
import net.impeerium.api.localization.CommonText;
import net.impeerium.api.localization.Language;
import net.impeerium.api.localization.LocalizationService;
import net.impeerium.api.localization.TranslationArgument;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Loaded only when the optional API is available. Invalid owned catalogs fail loudly. */
final class ApiPlayerText implements PlayerText.Backend {
    private final TradeSystem owner;
    private final LocalizationService service;
    private final Map<String, Map<String, String>> signatures = new HashMap<>();
    private static final Map<String, String> COMMON = new HashMap<>();

    static {
        COMMON.put("no_permissions", CommonText.NO_PERMISSION);
        COMMON.put("back", CommonText.BACK);
        COMMON.put("close", CommonText.CLOSE);
        COMMON.put("cancel", CommonText.CANCEL);
        COMMON.put("online", CommonText.ONLINE);
        COMMON.put("offline", CommonText.OFFLINE);
        COMMON.put("requests_enabled", CommonText.ENABLED);
        COMMON.put("requests_disabled", CommonText.DISABLED);
        COMMON.put("leftclick", CommonText.CLICK_LEFT);
        COMMON.put("rightclick", CommonText.CLICK_RIGHT);
        COMMON.put("shift_leftclick", CommonText.CLICK_SHIFT_LEFT);
    }

    static PlayerText.Backend create(TradeSystem owner) {
        ImpeeriumApi api;
        try {
            api = ImpeeriumApi.require(owner, 1, 11);
        } catch (IllegalStateException unavailable) {
            owner.getLogger().warning("ImpeeriumAPI 1.11 service is unavailable; retaining native language files.");
            return null;
        }
        return new ApiPlayerText(owner, api.localization());
    }

    ApiPlayerText(TradeSystem owner, LocalizationService service) {
        this.owner = owner;
        this.service = service;
        try (InputStream catalog = owner.getResource("locales/messages.yml")) {
            if (catalog == null) throw new IllegalStateException("Missing owned localization catalog");
            service.registerCatalog(owner, catalog);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read owned localization catalog", failure);
        }
        try (InputStream catalog = owner.getResource("locales/messages.yml")) {
            if (catalog == null) throw new IllegalStateException("Missing owned localization catalog");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(catalog, StandardCharsets.UTF_8));
            ConfigurationSection messages = yaml.getConfigurationSection("messages");
            if (messages == null) throw new IllegalStateException("Missing owned messages");
            for (String key : messages.getKeys(false)) {
                Map<String, String> arguments = new HashMap<>();
                ConfigurationSection args = messages.getConfigurationSection(key + ".args");
                if (args != null) for (String name : args.getKeys(false)) arguments.put(name, args.getString(name));
                signatures.put(key, arguments);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read catalog signatures", failure);
        }
    }

    @Override
    public PlayerText.View capture(Player player) {
        // The snapshot is retained for this presentation; it never writes a preference.
        Language language = service.snapshot(player.getUniqueId()).language();
        return new PlayerText.View() {
            @Override
            public Component render(String nativeKey, Map<String, Object> arguments) {
                String key = nativeKey.toLowerCase(Locale.ROOT);
                String common = COMMON.get(key);
                if (common != null) return service.render(language, common);
                Map<String, String> signature = signatures.get(key);
                if (signature == null) return service.render(language, "tradesystem." + key);
                Map<String, TranslationArgument> typed = new HashMap<>();
                for (Map.Entry<String, String> entry : signature.entrySet()) {
                    String name = entry.getKey();
                    Object value = arguments.get(name);
                    if (value == null) continue; // Let the runtime return its bounded controlled failure.
                    try {
                      switch (entry.getValue()) {
                        case "component":
                            typed.put(name, TranslationArgument.component(value instanceof Component
                                    ? (Component) value : Component.text(value.toString())));
                            break;
                        case "integer":
                            typed.put(name, TranslationArgument.integer(Long.parseLong(value.toString())));
                            break;
                        case "text":
                            typed.put(name, TranslationArgument.text(value.toString()));
                            break;
                        default:
                            throw new IllegalStateException("Unsupported owned argument type");
                      }
                    } catch (IllegalArgumentException invalidValue) {
                        // Omission deliberately preserves the mismatch for the runtime's safe diagnostics.
                    }
                }
                return service.render(language, "tradesystem." + key, typed);
            }

            @Override
            public boolean owns(String nativeKey) {
                String key = nativeKey.toLowerCase(Locale.ROOT);
                return signatures.containsKey(key) || COMMON.containsKey(key);
            }

            @Override
            public String language() {
                return language.id();
            }
        };
    }

    @Override
    public void close() {
        service.unregister(owner);
    }
}
