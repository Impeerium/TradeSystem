package de.codingair.tradesystem.spigot.localization;

import de.codingair.codingapi.server.DefaultFontInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Owned components are applied only after the native builder finishes its legacy fields. */
public final class PlayerItems {
    private PlayerItems() {}

    public static ItemStack named(ItemStack nativeItem, Component name) {
        return text(nativeItem, name, null);
    }

    /** Clone a decorative item and change only its explicitly supplied text fields. */
    public static ItemStack text(ItemStack nativeItem, Component name, List<Component> lines) {
        if (nativeItem == null) return null;
        ItemStack item = nativeItem.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (name != null) meta.displayName(menuText(name));
        if (lines != null) meta.lore(lines.stream().map(PlayerItems::menuText).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack replaceTail(ItemStack nativeItem, Component name,
                                       List<String> expectedTail, List<Component> translatedTail) {
        ItemMeta meta = nativeItem.getItemMeta();
        List<Component> lore = meta == null ? null : meta.lore();
        if (lore == null || lore.size() < expectedTail.size()) return named(nativeItem, name);
        int start = lore.size() - expectedTail.size();
        for (int i = 0; i < expectedTail.size(); i++) {
            if (!plain(lore.get(start + i)).equals(expectedTail.get(i))) return named(nativeItem, name);
        }
        List<Component> lines = new ArrayList<>(lore.subList(0, start));
        lines.addAll(translatedTail);
        return text(nativeItem, name, lines);
    }

    public static boolean isDefaultHelp(ItemStack source) {
        if (source == null || !source.hasItemMeta()) return false;
        ItemMeta meta = source.getItemMeta();
        if (meta == null || meta.displayName() == null || !plain(meta.displayName()).equals("Kaubandus")) return false;
        List<Component> lore = meta.lore();
        return lore != null && lore.stream().map(PlayerItems::plain).toList().equals(List.of("",
                "Otsid viisi kaasmängijatega kaubelda?", "Siin avaneb sulle see võimalus!"));
    }

    /** Argument-free static captions only. This measures plain words and never invokes a legacy parser. */
    public static List<Component> wrappedCaption(Component caption, int width) {
        String plain = plain(caption);
        List<Component> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : plain.split(" +")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && advance(candidate) > width) {
                result.add(Component.text(line.toString(), NamedTextColor.RED));
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) result.add(Component.text(line.toString(), NamedTextColor.RED));
        return result;
    }

    private static int advance(String value) {
        int width = 0;
        for (char letter : value.toCharArray()) width += DefaultFontInfo.getDefaultFontInfo(letter).getLength() + 1;
        return width;
    }

    public static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static Component menuText(Component text) {
        return text.decoration(TextDecoration.ITALIC, false);
    }
}
