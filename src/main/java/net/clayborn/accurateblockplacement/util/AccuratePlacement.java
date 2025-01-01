package net.clayborn.accurateblockplacement.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.clayborn.accurateblockplacement.AccurateBlockPlacementMod;
import net.clayborn.accurateblockplacement.mixin.KeyBindingAccessor;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class AccuratePlacement {

    private static final String itemUseMethodName;
    private static final String blockActivateMethodName;
    private static final LoadingCache<Item, Boolean> itemCache;
    private static final LoadingCache<Block, Boolean> blockCache;
    private static final Logger logger = LoggerFactory.getLogger(AccuratePlacement.class);

    static {
        Method[] methods = Item.class.getMethods();

        String targetMethod = null;

        for (Method method : methods) {
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 3 && types[0] == World.class && types[1] == PlayerEntity.class && types[2] == Hand.class) {
                targetMethod = method.getName();
                break;
            }
        }

        itemUseMethodName = targetMethod;

        if (itemUseMethodName == null) {
            logger.error("Could not find item use method");
        }

        itemCache = CacheBuilder.newBuilder().maximumSize(256).build(new CacheLoader<>() {
            @Override
            public @NotNull Boolean load(@NotNull Item item) {
                if (itemUseMethodName == null) return false;

                try {
                    return !item.getClass().getMethod(itemUseMethodName, World.class, PlayerEntity.class, Hand.class).getDeclaringClass().equals(Item.class);
                } catch (Exception e) {
                    return false;
                }
            }
        });

        // now check for block activation methods
        methods = AbstractBlock.class.getDeclaredMethods();
        targetMethod = null;

        for (Method method : methods) {
            Class<?>[] types = method.getParameterTypes();

            if (types.length == 5 && types[0] == BlockState.class && types[1] == World.class
                && types[2] == BlockPos.class && types[3] == PlayerEntity.class
                && types[4] == BlockHitResult.class) {
                targetMethod = method.getName();
                break;
            }
        }

        blockActivateMethodName = targetMethod;

        if (blockActivateMethodName == null) {
            logger.error("Could not find block activate method");
        }

        blockCache = CacheBuilder.newBuilder().maximumSize(256).build(new CacheLoader<>() {
            @Override
            public @NotNull Boolean load(@NotNull Block block) {
                if (blockActivateMethodName == null) return false;

                try {
                    return !block.getClass().getDeclaredMethod(blockActivateMethodName, BlockState.class, World.class, BlockPos.class, PlayerEntity.class, BlockHitResult.class).getDeclaringClass().equals(AbstractBlock.class);
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }

    private final ArrayList<HitResult> backFillList = new ArrayList<>();
    private BlockPos lastSeenBlockPos = null;
    private BlockPos lastPlacedBlockPos = null;
    private Vec3d lastPlayerPlacedBlockPos = null;
    private Boolean autoRepeatWaitingOnCooldown = true;
    private Vec3d lastFreshPressMouseRatio = null;
    private Item lastItemInUse = null;
    private Hand handOfCurrentItemInUse;

    private static boolean doesItemHaveOverriddenUseMethod(Item item) {
        if (itemUseMethodName == null) return false;

        try {
            return itemCache.get(item);
        } catch (ExecutionException e) {
            return false;
        }
    }

    private static Boolean isBlockActivatable(Block block) {
        if (blockActivateMethodName == null) return false;

        try {
            return blockCache.get(block);
        } catch (ExecutionException e) {
            return false;
        }
    }

    public void update() {
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

        tryPlace(client);
    }

    private void tryPlace(MinecraftClient client) {
        AccurateBlockPlacementMod.disableNormalItemUse = false;

        final ClientPlayerEntity player = client.player;
        if (player == null) return;

        Item currentItem = this.getItemInUse(player);

        final boolean freshKeyPress = ((KeyBindingAccessor) client.options.useKey).getTimesPressed() > 0;

        if (freshKeyPress) {
            freshKeyPress(client, currentItem);
        }

        if (!isPlacementItem(currentItem) || isTargetingSomethingElse(client) || player.isUsingItem()) return;

        Hand otherHand = this.handOfCurrentItemInUse == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack otherHandItemStack = player.getStackInHand(otherHand);

        if (isInteractingWithOtherHand(player, otherHandItemStack)) return;

        BlockHitResult blockHitResult = (BlockHitResult) client.crosshairTarget;
        if (blockHitResult == null) return;

        BlockPos blockHitPos = blockHitResult.getBlockPos();

        World world = client.world;
        if (world == null) return;

        Block targetBlock = world.getBlockState(blockHitPos).getBlock();

        if (isBlockActivatable(targetBlock) && !(targetBlock instanceof StairsBlock) && !player.isSneaking()
            || !freshKeyPress && !client.options.useKey.isPressed()) return;

        AccurateBlockPlacementMod.disableNormalItemUse = true;

        ItemPlacementContext targetPlacement = new ItemPlacementContext(new ItemUsageContext(player, this.handOfCurrentItemInUse, blockHitResult));
        Block oldBlock = world.getBlockState(targetPlacement.getBlockPos()).getBlock();

        double facingAxisPlayerPos = 0.0;
        double facingAxisPlayerLastPos = 0.0;
        double facingAxisLastPlacedPos = 0.0;

        if (this.lastPlacedBlockPos != null && this.lastPlayerPlacedBlockPos != null) {
            Direction.Axis axis = targetPlacement.getSide().getAxis();

            facingAxisPlayerPos = player.getPos().getComponentAlongAxis(axis);
            facingAxisPlayerLastPos = this.lastPlayerPlacedBlockPos.getComponentAlongAxis(axis);
            facingAxisLastPlacedPos = new Vec3d(this.lastPlacedBlockPos.getX(), this.lastPlacedBlockPos.getY(), this.lastPlacedBlockPos.getZ()).getComponentAlongAxis(axis);

            if (targetPlacement.getSide().getName().equals("west") || targetPlacement.getSide().getName().equals("north")) {
                ++facingAxisLastPlacedPos;
            }
        }

        Vec3d currentMouseRatio = null;

        if (client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0) {
            currentMouseRatio = new Vec3d(
                    client.mouse.getX() / (double) client.getWindow().getWidth(),
                    client.mouse.getY() / (double) client.getWindow().getHeight(),
                    0.0
            );
        }

        IMinecraftClientAccessor clientAccessor = (IMinecraftClientAccessor) client;

        boolean isPlacementTargetFresh =
                ((this.lastSeenBlockPos == null || !this.lastSeenBlockPos.equals(blockHitPos))
                 && (this.lastPlacedBlockPos == null || !this.lastPlacedBlockPos.equals(blockHitPos))
                ) || (this.lastPlacedBlockPos != null
                      && this.lastPlayerPlacedBlockPos != null
                      && this.lastPlacedBlockPos.equals(blockHitPos)
                      && Math.abs(facingAxisPlayerLastPos - facingAxisPlayerPos) >= 0.99
                      && Math.abs(facingAxisPlayerLastPos - facingAxisLastPlacedPos) < Math.abs(facingAxisPlayerPos - facingAxisLastPlacedPos)
                );

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

            this.lastSeenBlockPos = blockHitResult.getBlockPos();
            return;
        }

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

        for (boolean runOnceFlag = !freshKeyPress; runOnceFlag || client.options.useKey.wasPressed(); runOnceFlag = false) {
            clientAccessor.accurateblockplacement_DoItemUseBypassDisable();

            if (!oldBlock.equals(world.getBlockState(targetPlacement.getBlockPos()).getBlock())) {
                this.lastPlacedBlockPos = targetPlacement.getBlockPos();

                if (this.lastPlayerPlacedBlockPos == null) {
                    this.lastPlayerPlacedBlockPos = player.getPos();
                } else {
                    Vec3d pos = Vec3d.of(targetPlacement.getSide().getVector());
                    Vec3d summedLastPlayerPos = this.lastPlayerPlacedBlockPos.add(pos);

                    this.lastPlayerPlacedBlockPos = switch (targetPlacement.getSide().getAxis()) {
                        case X -> new Vec3d(summedLastPlayerPos.x, player.getPos().y, player.getPos().z);
                        case Y -> new Vec3d(player.getPos().x, summedLastPlayerPos.y, player.getPos().z);
                        case Z -> new Vec3d(player.getPos().x, player.getPos().y, summedLastPlayerPos.z);
                    };
                }
            }
        }

        this.lastSeenBlockPos = blockHitResult.getBlockPos();
    }

    private void freshKeyPress(MinecraftClient client, Item currentItem) {
        this.lastSeenBlockPos = null;
        this.lastPlacedBlockPos = null;
        this.lastPlayerPlacedBlockPos = null;
        this.autoRepeatWaitingOnCooldown = true;
        this.backFillList.clear();

        if (client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0) {
            this.lastFreshPressMouseRatio = new Vec3d(
                    client.mouse.getX() / (double) client.getWindow().getWidth(),
                    client.mouse.getY() / (double) client.getWindow().getHeight(),
                    0.0
            );
        } else {
            this.lastFreshPressMouseRatio = null;
        }

        this.lastItemInUse = currentItem;
    }

    public Item getItemInUse(PlayerEntity player) {
        for (Hand thisHand : Hand.values()) {
            ItemStack itemInHand = player.getStackInHand(thisHand);

            if (!itemInHand.isEmpty()) {
                this.handOfCurrentItemInUse = thisHand;
                return itemInHand.getItem();
            }
        }

        return null;
    }

    public boolean isPlacementItem(Item currentItem) {
        return (currentItem instanceof BlockItem || currentItem instanceof MiningToolItem)
               && !doesItemHaveOverriddenUseMethod(currentItem);
    }

    private boolean isInteractingWithOtherHand(PlayerEntity player, ItemStack otherHandStack) {
        return !otherHandStack.isEmpty() && (otherHandStack.contains(DataComponentTypes.FOOD)
                                             || doesItemHaveOverriddenUseMethod(otherHandStack.getItem())) && player.isUsingItem();
    }

    private boolean isTargetingSomethingElse(MinecraftClient client) {
        return client.crosshairTarget != null && client.crosshairTarget.getType() != HitResult.Type.BLOCK;
    }
}
