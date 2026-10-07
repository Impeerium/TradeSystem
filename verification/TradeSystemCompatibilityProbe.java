import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Only for a disposable localhost server. Never install this probe on a live server. */
public final class TradeSystemCompatibilityProbe extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                verify();
                Files.writeString(Path.of("compatibility-result.txt"), "PASS " + Bukkit.getMinecraftVersion() + "\n");
                getLogger().info("Compatibility checks passed");
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(Path.of("compatibility-result.txt"), "FAIL " + failure + "\n"); }
                catch (Exception ignored) { }
            } finally {
                Bukkit.shutdown();
            }
        }, 40L);
    }

    private void verify() throws Exception {
        if (!Bukkit.getOnlinePlayers().isEmpty()) throw new AssertionError("Test server must have no players");
        Plugin trade = Bukkit.getPluginManager().getPlugin("TradeSystem");
        if (trade == null || !trade.isEnabled()) throw new AssertionError("TradeSystem did not enable");
        ClassLoader loader = trade.getClass().getClassLoader();
        String prefix = "de.codingair.tradesystem.lib.codingapi.";
        Class<?> version = loader.loadClass(prefix + "server.specification.Version");
        double number = (double) version.getMethod("get").invoke(null);
        boolean modern = number >= 2603;
        if (number != 2602 && number != 2603) throw new AssertionError("Unexpected test version: " + number);

        Class<?> gui = loader.loadClass(prefix + "player.gui.sign.SignGUI");
        Constructor<?> wrapper = loader.loadClass(gui.getName() + "$NmsWrapper").getDeclaredConstructor();
        wrapper.setAccessible(true);
        Object sign = wrapper.newInstance();
        String[] lines = {"123.45", "Trade test", "", "four"};
        Field text = gui.getDeclaredField("lines");
        text.setAccessible(true);
        text.set(sign, lines);
        Location location = Bukkit.getWorlds().getFirst().getSpawnLocation();
        Class<?> material = loader.loadClass(prefix + "tools.items.XMaterial");
        Object oakSign = material.getField("OAK_SIGN").get(null);
        Method create = gui.getDeclaredMethod("createUpdatePacket", Location.class, material);
        create.setAccessible(true);
        // Creates client-only sign content; does not place a block or touch a player inventory.
        Object update = create.invoke(sign, location, oakSign);
        if (update == null || !update.getClass().getSimpleName().equals("ClientboundBlockEntityDataPacket"))
            throw new AssertionError("Could not construct sign contents packet");

        Class<?> posType = Class.forName("net.minecraft.core.BlockPos");
        Object pos = posType.getConstructor(int.class, int.class, int.class).newInstance(0, 64, 0);
        Class<?> incoming = Class.forName("net.minecraft.network.protocol.game.ServerboundSignUpdatePacket");
        Object packet;
        if (modern) {
            Class<?> slot = Class.forName("net.minecraft.world.level.block.entity.SignTextSlot");
            Object front = slot.getField("FRONT").get(null);
            packet = incoming.getConstructor(posType, List.class, slot).newInstance(pos, Arrays.asList(lines), front);
        } else {
            packet = incoming.getConstructor(posType, boolean.class, String.class, String.class, String.class, String.class)
                    .newInstance(pos, true, lines[0], lines[1], lines[2], lines[3]);
        }
        // Exercise CodingAPI's actual version branch and Packet.initialize; no players receive it.
        Method openEditor = gui.getDeclaredMethod("openEditor", Location.class);
        openEditor.setAccessible(true);
        openEditor.invoke(sign, location);

        Field packetLines = gui.getDeclaredField("packetLines");
        packetLines.setAccessible(true);
        Object accessor = packetLines.get(null);
        Method get = accessor.getClass().getMethod("get", Object.class);
        get.setAccessible(true);
        Object raw = get.invoke(accessor, packet);
        String[] decoded = modern ? ((List<?>) raw).toArray(new String[0]) : (String[]) raw;
        if (!Arrays.equals(lines, decoded)) throw new AssertionError("Sign packet accessor returned incorrect text");
    }
}
