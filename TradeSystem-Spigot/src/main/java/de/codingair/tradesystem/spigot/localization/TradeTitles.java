package de.codingair.tradesystem.spigot.localization;

import net.impeerium.api.text.GuiText;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.regex.Pattern;

/** Optional-API title producer for the exact installed Impeerium trade frame. */
public final class TradeTitles {
    public static final String NATIVE_TITLE = ":offset_-19:&f\uF813:offset_-188:%tradesystem_countdown_fancy%";
    private static final GuiText.LabelFont LABEL = GuiText.LabelFont.HOUSE.at(Key.key("impeerium:gui_label_p27"));
    private static final Key FRAMES = Key.key("impeerium:tradesystem_frames");
    private static final Pattern SOURCE = Pattern.compile(Pattern.quote(":offset_-19:&f\uF813:offset_")
            + "(-?[0-9]{1,3})" + Pattern.quote(":%tradesystem_countdown_fancy%"));

    private TradeTitles() {}

    public static Component create(PlayerText.View view, String source, String countdownSource, boolean locked) {
        if (source == null || !"\uF810".equals(countdownSource)) return null;
        var match = SOURCE.matcher(source);
        if (!match.matches()) return null;
        int nativeOffset = Integer.parseInt(match.group(1));
        if (Math.abs(nativeOffset) > 512) return null;
        Component heading = view.render("gui_title").color(NamedTextColor.WHITE);
        var line = GuiText.line().move(-19)
                .glyph(Component.text("\uE000", NamedTextColor.WHITE).font(FRAMES), 199)
                .text(heading, LABEL, 21, 148, GuiText.Align.LEFT);
        // Keep the installed overlay's exact native origin and font. No synthetic counter/state.
        if (locked) line.moveTo(180 + nativeOffset).glyph(Component.text("\uF810", NamedTextColor.WHITE)
                .font(Key.key("minecraft:default")), 176);
        return line.build();
    }

    public static boolean headingFits(PlayerText.View view) {
        return LABEL.fits(PlayerItems.plain(view.render("gui_title")), 148);
    }
}
