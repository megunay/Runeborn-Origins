package com.runeborn.claims.mixin;

import com.runeborn.claims.RunebornClaimsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent active parkour participants from picking up dropped items during the race. */
@Mixin(ItemEntity.class)
public abstract class ParkourItemPickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void runeborn$blockPickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer
                && RunebornClaimsMod.shouldBlockParkourItemPickup(serverPlayer)) {
            ci.cancel();
        }
    }
}
