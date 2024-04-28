package net.clayborn.accurateblockplacement.mixin;

import net.clayborn.accurateblockplacement.AccurateBlockPlacementMod;
import net.clayborn.accurateblockplacement.util.IMinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements IMinecraftClientAccessor {

    @Shadow
    private int itemUseCooldown;

    @Shadow
    protected abstract void doItemUse();

    @Override
    public void accurateblockplacement_DoItemUseBypassDisable() {
        boolean oldValue = AccurateBlockPlacementMod.disableNormalItemUse;
        AccurateBlockPlacementMod.disableNormalItemUse = false;
        doItemUse();
        AccurateBlockPlacementMod.disableNormalItemUse = oldValue;
    }

    @Inject(
			method = "doItemUse()V",
			at = @At("HEAD"),
			cancellable = true
	)
    void OnDoItemUse(CallbackInfo info) {
        if (AccurateBlockPlacementMod.disableNormalItemUse) {
            info.cancel();
        }
    }

    @Override
    public int accurateblockplacement_GetItemUseCooldown() {
        return itemUseCooldown;
    }
}
