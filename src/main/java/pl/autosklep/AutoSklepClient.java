package pl.autosklep;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AutoSklepClient implements ClientModInitializer {

    enum State { IDLE, WAIT_MENU, WAIT_COPPER, COOLDOWN }

    static boolean enabled = false;
    private static State state = State.IDLE;
    private static int wait = 0;
    private static int checkTicks = 0;

    private static KeyBinding toggleKey;
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosklep.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.autosklep"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosklep.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.autosklep"));

        // /autosklep [start|stop|gui|status|cmd <komenda>]
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("autosklep")
                .then(ClientCommandManager.literal("start").executes(ctx -> { setEnabled(true); return 1; }))
                .then(ClientCommandManager.literal("stop").executes(ctx -> { setEnabled(false); return 1; }))
                .then(ClientCommandManager.literal("gui").executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new AutoSklepScreen()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    msg("Stan: " + (enabled ? "WŁĄCZONY" : "wyłączony")
                            + " | komenda: /" + Config.get().command);
                    return 1;
                }))
                .then(ClientCommandManager.literal("cmd")
                    .then(ClientCommandManager.argument("komenda", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String c = StringArgumentType.getString(ctx, "komenda").trim();
                            if (c.startsWith("/")) c = c.substring(1);
                            Config.get().command = c;
                            Config.save();
                            msg("Ustawiono komendę: /" + c);
                            return 1;
                        })))
                .executes(ctx -> { setEnabled(!enabled); return 1; })
            ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient mc) {
        while (toggleKey.wasPressed()) setEnabled(!enabled);
        while (guiKey.wasPressed()) mc.setScreen(new AutoSklepScreen());

        ClientPlayerEntity p = mc.player;
        if (!enabled || p == null || mc.interactionManager == null) return;
        Config c = Config.get();

        switch (state) {
            case IDLE -> {
                if (mc.currentScreen != null) return;
                if (++checkTicks < 10) return;
                checkTicks = 0;
                if (inventoryFull(p, c)) {
                    p.networkHandler.sendChatCommand(c.command);
                    state = State.WAIT_MENU;
                    wait = 0;
                }
            }
            case WAIT_MENU -> {
                wait++;
                GenericContainerScreenHandler h = container(mc);
                if (h != null && wait % (c.clickDelay + 1) == 0) {
                    int slot = findInShop(h, true);
                    if (slot >= 0) {
                        click(mc, p, h, slot);
                        state = State.WAIT_COPPER;
                        wait = 0;
                        return;
                    }
                }
                if (wait > c.timeoutTicks) abort(mc, p);
            }
            case WAIT_COPPER -> {
                wait++;
                GenericContainerScreenHandler h = container(mc);
                if (h != null && wait % (c.clickDelay + 1) == 0) {
                    int slot = findInShop(h, false);
                    if (slot >= 0) {
                        for (int i = 0; i < c.copperClicks; i++) click(mc, p, h, slot);
                        p.closeHandledScreen(); // jak ESC
                        state = State.COOLDOWN;
                        wait = 0;
                        return;
                    }
                }
                if (wait > c.timeoutTicks) abort(mc, p);
            }
            case COOLDOWN -> {
                if (++wait >= c.cooldownTicks) {
                    state = State.IDLE;
                    checkTicks = 0;
                }
            }
        }
    }

    private static GenericContainerScreenHandler container(MinecraftClient mc) {
        if (mc.currentScreen instanceof GenericContainerScreen s) return s.getScreenHandler();
        return null;
    }

    /** Szuka czerwonej welny (redWool=true) albo miedzi (false) w gornej czesci menu. */
    private static int findInShop(GenericContainerScreenHandler h, boolean redWool) {
        int size = h.getRows() * 9;
        for (int i = 0; i < size && i < h.slots.size(); i++) {
            ItemStack s = h.slots.get(i).getStack();
            if (s.isEmpty()) continue;
            if (redWool ? s.isOf(Items.RED_WOOL) : isCopper(s)) return i;
        }
        return -1;
    }

    private static void click(MinecraftClient mc, ClientPlayerEntity p,
                              GenericContainerScreenHandler h, int slot) {
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, p);
    }

    private static void abort(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null) p.closeHandledScreen();
        state = State.COOLDOWN;
        wait = 0;
    }

    private static boolean isCopper(ItemStack s) {
        return !s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().contains("copper");
    }

    /** Brak pustych slotow i co najmniej minCopperSlots slotow z miedzia. */
    private static boolean inventoryFull(ClientPlayerEntity p, Config c) {
        PlayerInventory inv = p.getInventory();
        int copper = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) return false;
            if (isCopper(s)) copper++;
        }
        return copper >= c.minCopperSlots;
    }

    static void setEnabled(boolean v) {
        enabled = v;
        state = State.IDLE;
        checkTicks = 10;
        msg(v ? "WŁĄCZONY (/" + Config.get().command + ")" : "wyłączony");
    }

    private static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[AutoSklep] " + s), false);
    }
}
