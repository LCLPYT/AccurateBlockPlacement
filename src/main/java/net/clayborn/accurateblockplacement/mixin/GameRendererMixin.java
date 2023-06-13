package net.clayborn.accurateblockplacement.mixin;

import net.clayborn.accurateblockplacement.AccurateBlockPlacementMod;
import net.clayborn.accurateblockplacement.IMinecraftClientAccessor;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	private static final String blockActivateMethodName = getBlockActivateMethodName();
	private static final String itemUseMethodName = getItemUseMethodName();

	@Unique private BlockPos lastSeenBlockPos = null;
	@Unique private BlockPos lastPlacedBlockPos = null;
	@Unique private Vec3d lastPlayerPlacedBlockPos = null;
	@Unique private Boolean autoRepeatWaitingOnCooldown = true;
	@Unique private Vec3d lastFreshPressMouseRatio = null;
	@Unique private final ArrayList<HitResult> backFillList = new ArrayList<>();
	@Unique private Item lastItemInUse = null;
	@Unique Hand handOfCurrentItemInUse;

	@Unique
	private static String getBlockActivateMethodName() {
		Method[] methods = Block.class.getMethods();

		for (Method method : methods) {
			Class<?>[] types = method.getParameterTypes();
			if (types.length == 6 && types[0] == BlockState.class && types[1] == World.class && types[2] == BlockPos.class && types[3] == PlayerEntity.class && types[4] == Hand.class && types[5] == BlockHitResult.class) {
				return method.getName();
			}
		}

		return null;
	}

	@Unique
	private static String getItemUseMethodName() {
		Method[] methods = Item.class.getMethods();

		for (Method method : methods) {
			Class<?>[] types = method.getParameterTypes();
			if (types.length == 3 && types[0] == World.class && types[1] == PlayerEntity.class && types[2] == Hand.class) {
				return method.getName();
			}
		}

		return null;
	}

	@Unique
	private Item getItemInUse(MinecraftClient client) {
		if (client.player == null) return null;

		Hand[] hands = Hand.values();

		for (Hand thisHand : hands) {
			ItemStack itemInHand = client.player.getStackInHand(thisHand);

			if (!itemInHand.isEmpty()) {
				this.handOfCurrentItemInUse = thisHand;
				return itemInHand.getItem();
			}
		}

		return null;
	}

	@Unique
	private Boolean doesBlockHaveOverriddenActivateMethod(Block block) {
		if (blockActivateMethodName == null) {
			System.out.println("[ERROR] blockActivateMethodName is null!");
			return false;
		}

		try {
			return !block.getClass().getMethod(blockActivateMethodName, BlockState.class, World.class, BlockPos.class, PlayerEntity.class, Hand.class, BlockHitResult.class).getDeclaringClass().equals(AbstractBlock.class);
		} catch (Exception var3) {
			System.out.println("[ERROR] Unable to find block " + block.getClass().getName() + " activate method!");
			return false;
		}
	}

	private Boolean doesItemHaveOverriddenUseMethod(Item item) {
		if (itemUseMethodName == null) {
			System.out.println("[ERROR] itemUseMethodName is null!");
			return false;
		}

		try {
			return !item.getClass().getMethod(itemUseMethodName, World.class, PlayerEntity.class, Hand.class).getDeclaringClass().equals(Item.class);
		} catch (Exception var3) {
			System.out.println("[ERROR] Unable to find item " + item.getClass().getName() + " use method!");
			return false;
		}
	}

	@Inject(
			method = {"updateTargetedEntity"},
			at = {@At("RETURN")}
	)
	private void onUpdateTargetedEntityComplete(CallbackInfo info) {
		if (!AccurateBlockPlacementMod.isAccurateBlockPlacementEnabled) {
			AccurateBlockPlacementMod.disableNormalItemUse = false;
			this.lastSeenBlockPos = null;
			this.lastPlacedBlockPos = null;
			this.lastPlayerPlacedBlockPos = null;
			this.autoRepeatWaitingOnCooldown = true;
			this.backFillList.clear();
			this.lastFreshPressMouseRatio = null;
			this.lastItemInUse = null;
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.options == null || client.options.useKey == null || client.crosshairTarget == null
				|| client.player == null || client.world == null || client.mouse == null || client.getWindow() == null) {
			return;
		}

		AccurateBlockPlacementMod.disableNormalItemUse = false;
		KeyBindingAccessor keyUseAccessor = (KeyBindingAccessor) client.options.useKey;
		boolean freshKeyPress = keyUseAccessor.getTimesPressed() > 0;
		Item currentItem = this.getItemInUse(client);

		if (freshKeyPress) {
			this.lastSeenBlockPos = null;
			this.lastPlacedBlockPos = null;
			this.lastPlayerPlacedBlockPos = null;
			this.autoRepeatWaitingOnCooldown = true;
			this.backFillList.clear();

			if (client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0) {
				this.lastFreshPressMouseRatio = new Vec3d(
						client.mouse.getX() / (double)client.getWindow().getWidth(),
						client.mouse.getY() / (double)client.getWindow().getHeight(),
						0.0
				);
			} else {
				this.lastFreshPressMouseRatio = null;
			}

			this.lastItemInUse = currentItem;
		}

		if (currentItem == null) return;

		if (!(currentItem instanceof BlockItem) && !(currentItem instanceof MiningToolItem)) return;

		if ((currentItem.isFood() && !(currentItem instanceof AliasedBlockItem)) || this.doesItemHaveOverriddenUseMethod(currentItem)) {
			return;
		}

		if (client.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

		Hand otherHand = this.handOfCurrentItemInUse == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
		ItemStack otherHandItemStack = client.player.getStackInHand(otherHand);

		if (!otherHandItemStack.isEmpty() && (otherHandItemStack.getItem().isFood()
				|| this.doesItemHaveOverriddenUseMethod(otherHandItemStack.getItem())) && client.player.isUsingItem()) {
			return;
		}

		BlockHitResult blockHitResult = (BlockHitResult)client.crosshairTarget;
		BlockPos blockHitPos = blockHitResult.getBlockPos();
		Block targetBlock = client.world.getBlockState(blockHitPos).getBlock();
		Boolean isTargetBlockActivatable = this.doesBlockHaveOverriddenActivateMethod(targetBlock);

		if (isTargetBlockActivatable && !(targetBlock instanceof StairsBlock) && !client.player.isSneaking()) return;

		if (!freshKeyPress && !client.options.useKey.isPressed()) return;

		AccurateBlockPlacementMod.disableNormalItemUse = true;

		ItemPlacementContext targetPlacement = new ItemPlacementContext(new ItemUsageContext(client.player, this.handOfCurrentItemInUse, blockHitResult));
		Block oldBlock = client.world.getBlockState(targetPlacement.getBlockPos()).getBlock();

		double facingAxisPlayerPos = 0.0;
		double facingAxisPlayerLastPos = 0.0;
		double facingAxisLastPlacedPos = 0.0;

		if (this.lastPlacedBlockPos != null && this.lastPlayerPlacedBlockPos != null) {
			Direction.Axis axis = targetPlacement.getSide().getAxis();

			facingAxisPlayerPos = client.player.getPos().getComponentAlongAxis(axis);
			facingAxisPlayerLastPos = this.lastPlayerPlacedBlockPos.getComponentAlongAxis(axis);
			facingAxisLastPlacedPos = new Vec3d(this.lastPlacedBlockPos.getX(), this.lastPlacedBlockPos.getY(), this.lastPlacedBlockPos.getZ()).getComponentAlongAxis(axis);

			if (targetPlacement.getSide().getName().equals("west") || targetPlacement.getSide().getName().equals("north")) {
				++facingAxisLastPlacedPos;
			}
		}

		Vec3d currentMouseRatio = null;

		if (client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0) {
			currentMouseRatio = new Vec3d(
					client.mouse.getX() / (double)client.getWindow().getWidth(),
					client.mouse.getY() / (double)client.getWindow().getHeight(),
					0.0
			);
		}

		IMinecraftClientAccessor clientAccessor = (IMinecraftClientAccessor) client;

		boolean isPlacementTargetFresh = (this.lastSeenBlockPos == null || !this.lastSeenBlockPos.equals(blockHitPos)) && (this.lastPlacedBlockPos == null || !this.lastPlacedBlockPos.equals(blockHitPos)) || this.lastPlacedBlockPos != null && this.lastPlayerPlacedBlockPos != null && this.lastPlacedBlockPos.equals(blockHitPos) && Math.abs(facingAxisPlayerLastPos - facingAxisPlayerPos) >= 0.99 && Math.abs(facingAxisPlayerLastPos - facingAxisLastPlacedPos) < Math.abs(facingAxisPlayerPos - facingAxisLastPlacedPos);
		boolean hasMouseMoved = currentMouseRatio != null && this.lastFreshPressMouseRatio != null && this.lastFreshPressMouseRatio.distanceTo(currentMouseRatio) >= 0.1;
		boolean isOnCooldown = this.autoRepeatWaitingOnCooldown && clientAccessor.accurateblockplacement_GetItemUseCooldown() > 0 && !hasMouseMoved;

		if (this.lastItemInUse != currentItem) {
			this.lastSeenBlockPos = blockHitResult.getBlockPos();
			return;
		}

		if (!freshKeyPress && (!isPlacementTargetFresh || isOnCooldown)) {
			if (isPlacementTargetFresh) {
				this.backFillList.add(client.crosshairTarget);
			}
		} else {
			if (this.autoRepeatWaitingOnCooldown && !freshKeyPress) {
				this.autoRepeatWaitingOnCooldown = false;
				HitResult currentHitResult = client.crosshairTarget;

				for (HitResult prevHitResult : this.backFillList) {
					client.crosshairTarget = prevHitResult;
					clientAccessor.accurateblockplacement_DoItemUseBypassDisable();
				}

				this.backFillList.clear();
				client.crosshairTarget = currentHitResult;
			}

			for(boolean runOnceFlag = !freshKeyPress; runOnceFlag || client.options.useKey.wasPressed(); runOnceFlag = false) {
				clientAccessor.accurateblockplacement_DoItemUseBypassDisable();

				if (!oldBlock.equals(client.world.getBlockState(targetPlacement.getBlockPos()).getBlock())) {
					this.lastPlacedBlockPos = targetPlacement.getBlockPos();

					if (this.lastPlayerPlacedBlockPos == null) {
						this.lastPlayerPlacedBlockPos = client.player.getPos();
					} else {
						Vec3d pos = Vec3d.of(targetPlacement.getSide().getVector());
						Vec3d summedLastPlayerPos = this.lastPlayerPlacedBlockPos.add(pos);

						this.lastPlayerPlacedBlockPos = switch (targetPlacement.getSide().getAxis()) {
							case X ->
									new Vec3d(summedLastPlayerPos.x, client.player.getPos().y, client.player.getPos().z);
							case Y ->
									new Vec3d(client.player.getPos().x, summedLastPlayerPos.y, client.player.getPos().z);
							case Z ->
									new Vec3d(client.player.getPos().x, client.player.getPos().y, summedLastPlayerPos.z);
						};
					}
				}
			}
		}

		this.lastSeenBlockPos = blockHitResult.getBlockPos();
	}
}
