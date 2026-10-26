package de.codingair.tradesystem.spigot.localization;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import de.codingair.codingapi.API;
import de.codingair.codingapi.nms.NmsCheck;
import de.codingair.codingapi.player.chat.*;
import de.codingair.codingapi.server.reflections.IReflection;
import de.codingair.codingapi.server.specification.Version;
import de.codingair.tradesystem.spigot.trade.listeners.TradeListener;
import net.impeerium.api.localization.Language;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real producer/registry/global-then-bound dispatcher; packet injection and scheduler I/O are excluded. */
class PlayerCallbackTest {
    public static final class CommandPacket { public String command; }
    PlayerMenuTest menu;
    PlayerTextTest fixture;
    MockedStatic<NmsCheck> preflight;
    MockedStatic<Version> version;
    MockedStatic<IReflection> reflection;
    MockedStatic<UniversalScheduler> scheduling;
    final List<Runnable> queued = new ArrayList<>();
    final List<Runnable> timers = new ArrayList<>();
    final List<MyScheduledTask> timerTasks = new ArrayList<>();
    ChatListener listener;
    TradeListener foreignListener;
    Method dispatch;
    Set<UUID> dead;

    @BeforeAll static void registry() { MockBukkit.mock(); }
    @AfterAll static void registryCleanup() { MockBukkit.unmock(); }

    @SuppressWarnings("unchecked")
    @BeforeEach void setup() throws Exception {
        menu = new PlayerMenuTest();menu.setup();fixture = menu.fixture;
        preflight = mockStatic(NmsCheck.class); // Do not initialize actual server packet loaders in MockBukkit.
        version = mockStatic(Version.class, call -> call.getMethod().getName().equals("get")
                ? 2602D : call.callRealMethod());
        reflection = mockStatic(IReflection.class, call -> {
            if (call.getMethod().getName().equals("getSaveClass") && call.getArguments().length == 2
                    && call.getArgument(0) == IReflection.ServerPacket.PACKETS
                    && "ServerboundChatCommandPacket".equals(call.getArgument(1))) return CommandPacket.class;
            return call.callRealMethod();
        });
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.runTask(any(Runnable.class))).thenAnswer(call -> {
            queued.add(call.getArgument(0));return mock(MyScheduledTask.class);
        });
        when(scheduler.runTaskTimer(any(Runnable.class), eq(20L), eq(20L))).thenAnswer(call -> {
            timers.add(call.getArgument(0));MyScheduledTask task = mock(MyScheduledTask.class);timerTasks.add(task);return task;
        });
        scheduling = mockStatic(UniversalScheduler.class);
        scheduling.when(() -> UniversalScheduler.getScheduler(fixture.owner)).thenReturn(scheduler);
        API.getInstance().getPlugins().add(fixture.owner);
        foreignListener = new TradeListener();ChatButtonManager.getInstance().addListener(foreignListener);
        listener = new ChatListener(); // No online players: no native packet-reader injection.
        dispatch = ChatListener.class.getDeclaredMethod("handleSimpleMessages", String.class, UUID.class, Player.class, List.class);
        dispatch.setAccessible(true);
        var deadField = ChatListener.class.getDeclaredField("DEAD_BUTTONS");deadField.setAccessible(true);
        dead = (Set<UUID>) deadField.get(null);dead.clear();
    }

    @AfterEach void close() {
        if (fixture != null && preflight != null) {
            for (SimpleMessage message : new ArrayList<>(API.getRemovables(SimpleMessage.class))) message.destroy();
            API.getInstance().getPlugins().remove(fixture.owner);
        }
        if (foreignListener != null) ChatButtonManager.getInstance().removeListener(foreignListener);
        if (dead != null) dead.clear();
        if (scheduling != null) scheduling.close();
        if (reflection != null) reflection.close();
        if (version != null) version.close();
        if (preflight != null) preflight.close();
        if (menu != null) menu.close();
    }

    record Notice(Player player, SimpleMessage message, ChatButton button, AtomicLong now) {}

    Notice notice() {
        Player recipient = fixture.player(Language.EN);when(recipient.getName()).thenReturn("Alex");
        when(recipient.isOnline()).thenReturn(true);
        AtomicLong now = new AtomicLong(100);
        assertTrue(PlayerText.offlineNotice(recipient, now::get));
        List<SimpleMessage> messages = API.getRemovables(recipient, SimpleMessage.class);
        assertEquals(1, messages.size());SimpleMessage message = messages.getFirst();
        assertSame(recipient, message.getPlayer());assertEquals(1, message.getButtons().size());
        ChatButton button = message.getButtons().getFirst();assertEquals("IMPEERIUM_TRADE_TOGGLE", button.getType());
        var sent = org.mockito.ArgumentCaptor.forClass(Component.class);verify(recipient).sendMessage(sent.capture());
        assertEquals(List.of("/" + ChatButton.PREFIX + button.getUniqueId() + "#" + button.getType()),
                PlayerTextTest.clickTargets(sent.getValue()));
        assertEquals(1, timers.size());assertTrue(API.getRemovables((Player) null, SimpleMessage.class).isEmpty());
        return new Notice(recipient, message, button, now);
    }

    boolean dispatch(Notice notice, Player clicking) throws Exception {
        // Native readPacket uses |=, so the bound dispatch runs even if a foreign listener handled globals.
        boolean used = (boolean) dispatch.invoke(listener, notice.button.getType(), notice.button.getUniqueId(), clicking,
                API.getRemovables((Player) null, SimpleMessage.class));
        used |= (boolean) dispatch.invoke(listener, notice.button.getType(), notice.button.getUniqueId(), clicking,
                API.getRemovables(clicking, SimpleMessage.class));
        return used;
    }

    @Test void emittedClickBypassesLegacyForeignHandlerAndExecutesConfiguredAliasOnce() throws Exception {
        Notice notice = notice();assertTrue(dispatch(notice, notice.player));assertEquals(1, queued.size());
        verify(notice.player, never()).performCommand(anyString());
        queued.forEach(Runnable::run);
        verify(notice.player, times(1)).performCommand("vaheta luba");
        verify(notice.player, never()).performCommand("trade toggle");
        assertTrue(API.getRemovables(notice.player, SimpleMessage.class).isEmpty());
        assertTrue(dead.contains(notice.button.getUniqueId()));verify(timerTasks.getFirst()).cancel();
        assertFalse(dispatch(notice, notice.player));assertEquals(1, queued.size());
    }

    @Test void foreignRecipientCannotUseEmittedTokenAndDuplicateQueuedClicksConsumeOnlyOnce() throws Exception {
        Notice notice = notice();Player foreign = fixture.player(Language.ET);when(foreign.getName()).thenReturn("Steve");
        assertFalse(dispatch(notice, foreign));assertTrue(queued.isEmpty());
        verify(foreign, never()).performCommand(anyString());
        assertTrue(dispatch(notice, notice.player));assertTrue(dispatch(notice, notice.player));
        assertEquals(2, queued.size());verify(notice.player, never()).performCommand(anyString());
        queued.forEach(Runnable::run);verify(notice.player, times(1)).performCommand("vaheta luba");
        verify(timerTasks.getFirst(), times(1)).cancel();
    }

    @Test void alreadyQueuedNativeCallbackChecksExactExecutionDeadline() throws Exception {
        Notice notice = notice();assertTrue(dispatch(notice, notice.player));
        notice.now.set(10_000_000_100L);queued.forEach(Runnable::run);
        verify(notice.player, never()).performCommand(anyString());
        for (int i = 0; i < 10; i++) timers.getFirst().run();
        assertTrue(API.getRemovables(notice.player, SimpleMessage.class).isEmpty());
        assertTrue(dead.contains(notice.button.getUniqueId()));
    }

    @Test void disconnectedRecipientCannotExecuteAlreadyQueuedAction() throws Exception {
        Notice notice = notice();assertTrue(dispatch(notice, notice.player));
        when(notice.player.isOnline()).thenReturn(false);queued.forEach(Runnable::run);
        verify(notice.player, never()).performCommand(anyString());
    }

    @Test void nativeTenSecondTimerDestroysMessageAndDeadTokenWithoutExecutingAction() {
        Notice notice = notice();
        for (int i = 0; i < 9; i++) timers.getFirst().run();
        assertEquals(List.of(notice.message), API.getRemovables(notice.player, SimpleMessage.class));
        timers.getFirst().run();assertTrue(API.getRemovables(notice.player, SimpleMessage.class).isEmpty());
        assertTrue(dead.contains(notice.button.getUniqueId()));verify(timerTasks.getFirst()).cancel();
        verify(notice.player, never()).performCommand(anyString());
    }
}
