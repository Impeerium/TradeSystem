package de.codingair.tradesystem.spigot.localization;

import de.codingair.codingapi.player.gui.inventory.InventoryUtils;
import de.codingair.codingapi.server.reflections.IReflection;
import de.codingair.codingapi.server.reflections.PacketUtils;
import de.codingair.codingapi.server.specification.Version;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Mirrors native same-container title synchronization without a component-to-legacy conversion. */
public final class ComponentTitleTransport {
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private final Logger logger;
    private Handles handles;
    private boolean failed;
    private Object lastMenu;
    private Component lastTitle;

    public ComponentTitleTransport(Logger logger) {
        this.logger = logger;
    }

    /** A native string update may have replaced our last component on the same menu. */
    public void invalidate() {
        lastMenu = null;
        lastTitle = null;
    }

    public void update(Player player, Inventory inventory, Component title) {
        if (failed || !player.isOnline() || player.getOpenInventory().getTopInventory() != inventory
                || inventory.getSize() < 9 || inventory.getSize() > 54 || inventory.getSize() % 9 != 0) return;
        try {
            if (handles == null) handles = new Handles();
            Object menu = InventoryUtils.getActiveContainer(PacketUtils.getEntityPlayer(player));
            if (menu == lastMenu && title.equals(lastTitle)) return;
            Object nativeTitle = PacketUtils.getRawIChatBaseComponent(GsonComponentSerializer.gson().serialize(title));
            Object packet = handles.packet.newInstance(InventoryUtils.getWindowId(menu), handles.menuType.invoke(menu), nativeTitle);
            // Build everything before mutating the live menu. Keep the existing menu and container ID.
            handles.title.set(menu, nativeTitle);
            try {
                PacketUtils.sendPacket(player, packet);
            } finally {
                // Native title updates also resync all slots and the carried cursor stack.
                player.updateInventory();
            }
            lastMenu = menu;
            lastTitle = title;
        } catch (RuntimeException | LinkageError unavailable) {
            failed = true;
            if (REPORTED.compareAndSet(false, true)) logger.warning("Component trade-title updates unavailable ("
                    + unavailable.getClass().getSimpleName() + "); retaining the valid initial title.");
        }
    }

    /** Ordinary lazy instance: failure must never poison a static initializer or plugin startup. */
    private static final class Handles {
        private final IReflection.FieldAccessor<?> title;
        private final IReflection.MethodAccessor menuType;
        private final IReflection.ConstructorAccessor packet;

        private Handles() {
            Class<?> menu = IReflection.getClass(IReflection.ServerPacket.INVENTORY, Version.choose("AbstractContainerMenu", "Container"));
            Class<?> packetClass = IReflection.getClass(IReflection.ServerPacket.PACKETS,
                    Version.choose("PacketPlayOutOpenWindow", 21.11, "ClientboundOpenScreenPacket"));
            title = IReflection.getField(menu, "title");
            menuType = IReflection.getMethod(menu, "getType", new Class[0]);
            packet = IReflection.getConstructor(packetClass, int.class, InventoryUtils.CONTAINERS_CLASS, PacketUtils.IChatBaseComponentClass);
            if (title == null || menuType == null || packet == null) throw new IllegalStateException("Missing component title transport");
        }
    }
}
