package net.clayborn.accurateblockplacement.mixin;

import net.clayborn.accurateblockplacement.util.AccuratePlacement;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Unique
    private final AccuratePlacement accuratePlacement = new AccuratePlacement();

    @Inject(
            method = "updateTargetedEntity",
            at = @At("RETURN")
    )
    private void onUpdateTargetedEntityComplete(CallbackInfo info) {
        accuratePlacement.update();
    }
}
