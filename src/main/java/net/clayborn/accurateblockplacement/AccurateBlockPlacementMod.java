package net.clayborn.accurateblockplacement;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class AccurateBlockPlacementMod implements ModInitializer {

	// global state
	public static Boolean  disableNormalItemUse = false;
	public static boolean  isAccurateBlockPlacementEnabled = true;

	private static KeyBinding keyBinding;

	private static boolean wasAccurateBlockPlacementToggleKeyPressed = false;
	
	final static String KEY_CATEGORY_NAME = "Accurate Block Placement";
	
	@Override
	public void onInitialize() {

		keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			    "key.accurateblockplacement.togglevanillaplacement",
			    InputUtil.Type.KEYSYM,
			    GLFW.GLFW_KEY_UNKNOWN,
			    KEY_CATEGORY_NAME
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client == null || client.inGameHud == null) return;

			if (keyBinding.isPressed()) {
				if (!wasAccurateBlockPlacementToggleKeyPressed) {
					isAccurateBlockPlacementEnabled = !isAccurateBlockPlacementEnabled;

					MutableText message;

					if (isAccurateBlockPlacementEnabled) {
						message = Text.translatable("net.clayborn.accurateblockplacement.modplacementmodemessage");
					} else {
						message = Text.translatable("net.clayborn.accurateblockplacement.vanillaplacementmodemessage");
					}

					message.formatted(Formatting.DARK_AQUA);

					client.inGameHud.getChatHud().addMessage(message);
				}
				wasAccurateBlockPlacementToggleKeyPressed = true;
			} else {
				wasAccurateBlockPlacementToggleKeyPressed = false;
			}
		});
	}
}