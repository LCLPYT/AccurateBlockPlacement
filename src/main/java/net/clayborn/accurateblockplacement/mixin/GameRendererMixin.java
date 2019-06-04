package net.clayborn.accurateblockplacement.mixin;

import java.lang.reflect.Method;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.clayborn.accurateblockplacement.AccurateBlockPlacementMod;
import net.clayborn.accurateblockplacement.IKeyBindingAccessor;
import net.clayborn.accurateblockplacement.IMinecraftClientAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	Hand handOfCurrentItemInUse;
	
	private Item getItemInUse(MinecraftClient client) {
		// have to check each hand
		Hand[] hands = Hand.values();
		int numHands = hands.length;

		for (int i = 0; i < numHands; ++i) {
			Hand thisHand = hands[i];
			ItemStack itemInHand = client.player.getStackInHand(thisHand);

			if (itemInHand.isEmpty()) {
				// hand is empty try the next one
				continue;
			} else {
				handOfCurrentItemInUse = thisHand;
				return itemInHand.getItem();				
			}
			
		}
		
		return null;
	}	
	
	private static String getBlockActivateMethodName()
	{
		Method[] methods = Block.class.getMethods();
		
		for (Method method: methods)
		{
			Class<?>[] types = method.getParameterTypes();
			if (types.length != 6) continue;
			if (types[0] != BlockState.class) continue;
			if (types[1] != World.class) continue;
			if (types[2] != BlockPos.class) continue;
			if (types[3] != PlayerEntity.class) continue;
			if (types[4] != Hand.class) continue;
			if (types[5] != BlockHitResult.class) continue;		
			
			return method.getName();
		}
		
		return null;		
	}
	
	private static final String blockActivateMethodName = getBlockActivateMethodName();
	
	private Boolean doesBlockHaveOverriddenActivateMethod(Block block) {		
		if (blockActivateMethodName == null)
		{
			System.out.println("[ERROR] blockActivateMethodName is null!");
		}
		
		// TODO: consider cache of results
		
		try {
			return !block.getClass()
					.getMethod(blockActivateMethodName, BlockState.class, World.class, BlockPos.class, PlayerEntity.class, Hand.class, BlockHitResult.class)
					.getDeclaringClass().equals(Block.class);
		} catch (Exception e) {
			System.out.println("Unable to find block " + block.getClass().getName() + " activate method!");
			return false;
		}
	}
	
	private static String getItemUseMethodName()
	{
		Method[] methods = Item.class.getMethods();
		
		for (Method method: methods)
		{
			Class<?>[] types = method.getParameterTypes();
			if (types.length != 3) continue;
			if (types[0] != World.class) continue;
			if (types[1] != PlayerEntity.class) continue;
			if (types[2] != Hand.class) continue;
			
			return method.getName();
		}
		
		return null;		
	}
	
	private static final String itemUseMethodName = getItemUseMethodName();
	
	private Boolean doesItemHaveOverriddenUseMethod(Item item) {		
		if (itemUseMethodName == null)
		{
			System.out.println("[ERROR] itemUseMethodName is null!");
		}
		
		// TODO: consider cache of results
		
		try {
			return !item.getClass()
					.getMethod(itemUseMethodName, World.class, PlayerEntity.class, Hand.class)
					.getDeclaringClass().equals(Item.class);
		} catch (Exception e) {
			System.out.println("Unable to find item " + item.getClass().getName() + " use method!");
			return false;
		}
	}
	
	private static String getItemUseOnBlockMethodName()
	{
		Method[] methods = Item.class.getMethods();
		
		for (Method method: methods)
		{
			Class<?>[] types = method.getParameterTypes();
			if (types.length != 1) continue;
			if (types[0] != ItemUsageContext.class) continue;
			
			return method.getName();
		}
		
		return null;
	}
	
	private static final String itemUseOnBlockMethodName = getItemUseOnBlockMethodName();
	
	private Boolean doesItemHaveOverriddenUseOnBlockMethod(Item item) {		
		if (itemUseOnBlockMethodName == null)
		{
			System.out.println("[ERROR] itemUseOnBlockMethodName is null!");
		}
		
		// TODO: consider cache of results
		
		try {
			return !item.getClass()
					.getMethod(itemUseOnBlockMethodName, ItemUsageContext.class)
					.getDeclaringClass().equals(BlockItem.class);
		} catch (Exception e) {
			System.out.println("Unable to find item " + item.getClass().getName() + " useOnBlock method!");
			return false;
		}
	}	
	
	Item lastItemInUse = null;

	@Inject(method = "net/minecraft/client/render/GameRenderer.updateTargetedEntity(F)V", at = @At("RETURN"))
	private void onUpdateTargetedEntityComplete(CallbackInfo info) {

		MinecraftClient client = MinecraftClient.getInstance();

		// safety checks
		if (client == null ||
			client.options == null ||
			client.options.keyUse == null ||
			client.hitResult == null ||
			client.player == null ||
			client.world == null)
		{
			return;
		}

		// will be set to true only if needed
		AccurateBlockPlacementMod.disableNormalItemUse = false;
		IKeyBindingAccessor keyUseAccessor = (IKeyBindingAccessor)(Object)client.options.keyUse;
		Boolean freshKeyPress = keyUseAccessor.accurateblockplacement_GetTimesPressed() > 0;
		
		Item currentItem = getItemInUse(client);	
		
		// reset state if the key was actually pressed
		// note: at very low frame rates they might have let go and hit it again before we get back here
		if (freshKeyPress)
		{		
			// clear history since they let go of the button
			AccurateBlockPlacementMod.lastSeenBlockPos = null;
			AccurateBlockPlacementMod.lastPlacedBlockPos = null;
			
			// a fresh keypress is required each time the item being used changes
			lastItemInUse = currentItem;
		}
		
		// nothing do it if nothing in hand.. let vanilla minecraft do it's normal flow
		if (currentItem == null) return;
		
		// this this item isn't a block, let vanilla take over
		if (!(currentItem instanceof BlockItem)) return;
		
		Boolean isItemUsable = currentItem.isFood() || doesItemHaveOverriddenUseMethod(currentItem) || doesItemHaveOverriddenUseOnBlockMethod(currentItem);
		
		// if the item we are holding is activatable, let vanilla take over
		if (isItemUsable) return;
		
		// if we aren't looking a block (so we can place), let vanilla take over
		if (client.hitResult.getType() != HitResult.Type.BLOCK) return;		

		BlockHitResult blockHitResult = (BlockHitResult) client.hitResult;
		BlockPos blockHitPos = blockHitResult.getBlockPos();
		Boolean isTargetBlockActivatable = doesBlockHaveOverriddenActivateMethod(client.world.getBlockState(blockHitPos).getBlock());	
		
		// don't override behavior of clicking activatable blocks
		// unless holding SNEAKING to replicate vanilla behaviors
		if (isTargetBlockActivatable && !client.player.isSneaking()) return;
		
		// are they holding the use key and is the item to use a block?
		// also is the the SAME item we started with if we are in repeat mode?
		// note: check both freshKey and current state in cause of shitty frame rates
		if ((freshKeyPress || client.options.keyUse.isPressed())) {

			// it's a block!! it's go time!
			AccurateBlockPlacementMod.disableNormalItemUse = true;

			// if [ we are still holding the same block we starting pressing 'use' with] AND
			// [ [ we have a fresh key press ] OR
			// [ [ we have no 'seen' history or the 'seen' history isn't a match ] AND
			// [ we have no 'place' history or the 'place' history isn't a match ] ] ]
			// we can try to place a block
			if (lastItemInUse == currentItem && // note: this is always true on a fresh keypress
					(freshKeyPress ||
					(
						(AccurateBlockPlacementMod.lastSeenBlockPos == null
							|| !AccurateBlockPlacementMod.lastSeenBlockPos.equals(blockHitPos))
						&& (AccurateBlockPlacementMod.lastPlacedBlockPos == null
							|| !AccurateBlockPlacementMod.lastPlacedBlockPos.equals(blockHitPos))
					))) {					

				IMinecraftClientAccessor clientAccessor = (IMinecraftClientAccessor) client;
				
				// always run at least once if we reach here
				// if this isn't a freshkey press, turn on the run once flag
				Boolean runOnceFlag = !freshKeyPress;
				
				// in case they manage to push the button multiple times per frame
				// note: we already subtracted one from the press count earlier so the total should be the same
				while(runOnceFlag || client.options.keyUse.wasPressed()) {
				
					// use item
					clientAccessor.accurateblockplacement_DoItemUseBypassDisable();

					// update last placed
					// TODO: don't update if placement failed
					AccurateBlockPlacementMod.lastPlacedBlockPos = new ItemPlacementContext(
							new ItemUsageContext(client.player, handOfCurrentItemInUse, blockHitResult)).getBlockPos();
					
					runOnceFlag = false;
				}

			}
			
			// update the last block we looked at
			AccurateBlockPlacementMod.lastSeenBlockPos = blockHitResult.getBlockPos();

		}

	}
}
