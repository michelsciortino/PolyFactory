package eu.pb4.polyfactory.item.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public interface AttackActionItem {
    boolean onAttackAction(ServerPlayer player, ItemStack stack, InteractionHand hand);
}
