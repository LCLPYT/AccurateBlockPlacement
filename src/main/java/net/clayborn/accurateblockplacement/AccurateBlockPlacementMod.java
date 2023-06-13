package net.clayborn.accurateblockplacement;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class AccurateBlockPlacementMod implements ClientModInitializer {

    public static boolean disableNormalItemUse = false;
    public static boolean isAccurateBlockPlacementEnabled = true;

    @Override
    public void onInitializeClient() {
        KeyBinding keybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "net.clayborn.accurateblockplacement.togglevanillaplacement",
                InputUtil.Type.KEYSYM,
                -1,
                "Accurate Block Placement"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keybind.wasPressed()) {
                isAccurateBlockPlacementEnabled = !isAccurateBlockPlacementEnabled;

                final MutableText message;

                if (isAccurateBlockPlacementEnabled) {
                    message = Text.translatable("net.clayborn.accurateblockplacement.modplacementmodemessage");
                } else {
                    message = Text.translatable("net.clayborn.accurateblockplacement.vanillaplacementmodemessage");
                }

                client.inGameHud.getChatHud().addMessage(message);
            }
        });
    }
}