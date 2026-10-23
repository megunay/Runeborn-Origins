package com.runeborn.claims.mixin;

import com.runeborn.claims.RunebornClaimsMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class SystemMarketMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void runeborn$handleSystemMarketClick(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (RunebornClaimsMod.handleSystemMarketMenuClick((AbstractContainerMenu) (Object) this, slot, button, input, player)) {
            ci.cancel();
        }
    }
}
