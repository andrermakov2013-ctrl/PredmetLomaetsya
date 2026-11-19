package ru.s7.itemwarning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@Mod(ItemWarningMod.MOD_ID)
public class ItemWarningMod {
    public static final String MOD_ID = "itemwarning";
    private static final int[] WARNING_LEVELS = {20, 15, 10, 5};

    private static final Map<UUID, Map<String, ItemState>> PLAYER_STATES = new HashMap<>();

    public ItemWarningMod() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        if (player.level().isClientSide() || player.isCreative() || player.isSpectator()) {
            return;
        }

        Map<String, ItemState> states =
                PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());

        checkStack(player, states, "main hand", player.getMainHandItem());
        checkStack(player, states, "off hand", player.getOffhandItem());

        int armorSlot = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            checkStack(player, states, "armor " + armorSlot, armor);
            armorSlot++;
        }
    }

    private static void checkStack(Player player, Map<String, ItemState> states,
                                   String slot, ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            states.remove(slot);
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        ItemState state = states.get(slot);

        if (state == null || !state.itemId.equals(itemId)) {
            state = new ItemState(itemId);
            states.put(slot, state);
        }

        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        int percent = (int) Math.ceil(remaining * 100.0 / stack.getMaxDamage());

        // После ремонта выше 20% предупреждения для этого предмета сбрасываются.
        if (percent > 20) {
            state.warnedLevels.clear();
            return;
        }

        for (int level : WARNING_LEVELS) {
            if (percent <= level && state.warnedLevels.add(level)) {
                String itemName = stack.getHoverName().getString();
                player.sendSystemMessage(Component.literal(
                        "§c[Предмет ломается] §f" + itemName
                        + " §7(" + slot + "): осталось §c" + percent
                        + "% §7прочности!"
                ));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_STATES.remove(event.getEntity().getUUID());
    }

    private static class ItemState {
        private final String itemId;
        private final Set<Integer> warnedLevels = new HashSet<>();

        private ItemState(String itemId) {
            this.itemId = itemId;
        }
    }
}
