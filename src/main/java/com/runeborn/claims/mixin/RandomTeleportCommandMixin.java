package com.runeborn.claims.mixin;

import com.fibermc.essentialcommands.commands.RandomTeleportCommand;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import com.runeborn.claims.RtpWarmupManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomTeleportCommand.class)
public abstract class RandomTeleportCommandMixin {
    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void runeborn$rtpWarmup(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (!RtpWarmupManager.handleRtpCommand(player, context.getSource().getServer())) {
                cir.setReturnValue(1);
            }
        } catch (Exception ignored) {
            // Preserve Essential Commands' normal error handling for non-player sources.
        }
    }
}
