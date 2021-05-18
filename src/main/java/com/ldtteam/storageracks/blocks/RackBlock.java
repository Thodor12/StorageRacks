package com.ldtteam.storageracks.blocks;

import com.ldtteam.storageracks.TileEntityRack;
import com.ldtteam.storageracks.utils.Constants;
import com.ldtteam.storageracks.utils.InventoryUtils;
import com.ldtteam.structurize.blocks.types.WoodType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashSet;

/**
 * Block for the shelves of the warehouse.
 */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RackBlock extends UpgradeableBlock
{
    /**
     * Variant of rack (full, empty).
     */
    public static final EnumProperty<RackType> VARIANT = EnumProperty.create("variant", RackType.class);

    /**
     * This blocks name.
     */
    private static final String BLOCK_NAME = "rack";

    /**
     * Smaller shape.
     */
    private static final VoxelShape SHAPE = VoxelShapes.box(0.1, 0.1, 0.1, 0.9, 0.9, 0.9);

    /**
     * The two types.
     */
    private final FrameType frameType;
    private final WoodType woodType;

    public RackBlock(final WoodType wood, final FrameType frame, final Item upgradeMaterial)
    {
        super(upgradeMaterial);
        this.registerDefaultState(this.defaultBlockState().setValue(VARIANT, RackType.DEFAULT));
        this.woodType = wood;
        this.frameType = frame;

        setRegistryName(Constants.MOD_ID.toLowerCase() + ":" + wood.getSerializedName() + "_" + frame.getSerializedName() + "_" + BLOCK_NAME);
    }


    @Override
    public boolean propagatesSkylightDown(final BlockState state, @NotNull final IBlockReader reader, @NotNull final BlockPos pos)
    {
        return true;
    }

    @NotNull
    @Override
    public VoxelShape getShape(final BlockState state, final IBlockReader worldIn, final BlockPos pos, final ISelectionContext context)
    {
        return SHAPE;
    }

    @Override
    public void spawnAfterBreak(final BlockState state, final ServerWorld worldIn, final BlockPos pos, final ItemStack stack)
    {
        final TileEntity tileentity = worldIn.getBlockEntity(pos);
        if (tileentity instanceof TileEntityRack)
        {
            final IItemHandler handler = ((TileEntityRack) tileentity).getInventory();
            InventoryUtils.dropItemHandler(handler, worldIn, pos.getX(), pos.getY(), pos.getZ());
        }
        super.spawnAfterBreak(state, worldIn, pos, stack);
    }

    @NotNull
    @Override
    public ActionResultType use(
      final BlockState state,
      final World world,
      final BlockPos pos,
      final PlayerEntity player,
      final Hand hand,
      final BlockRayTraceResult ray)
    {
        final TileEntity tileEntity = world.getBlockEntity(pos);

        if (tileEntity instanceof TileEntityRack)
        {
            final TileEntityRack rack = (TileEntityRack) tileEntity;
            if (!world.isClientSide)
            {
                rack.checkForUpgrade(state, rack.getSize());

                NetworkHooks.openGui((ServerPlayerEntity) player,
                  rack,
                  buf -> buf.writeBlockPos(rack.getBlockPos()));
            }
            return ActionResultType.SUCCESS;
        }
        return ActionResultType.FAIL;
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder)
    {
        builder.add(VARIANT);
    }

    @Override
    public boolean hasTileEntity(final BlockState state)
    {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(final BlockState state, final IBlockReader world)
    {
        return new TileEntityRack();
    }

    @Override
    public void onRemove(BlockState state, @NotNull World worldIn, @NotNull BlockPos pos, BlockState newState, boolean isMoving)
    {
        if (state.getBlock() != newState.getBlock())
        {
            TileEntity tileEntity = worldIn.getBlockEntity(pos);
            if (tileEntity instanceof TileEntityRack)
            {
                TileEntityRack tileEntityRack = (TileEntityRack) tileEntity;
                InventoryUtils.dropItemHandler(tileEntityRack.getInventory(),
                  worldIn,
                  tileEntityRack.getBlockPos().getX(),
                  tileEntityRack.getBlockPos().getY(),
                  tileEntityRack.getBlockPos().getZ());
                worldIn.updateNeighbourForOutputSignal(pos, this);
            }

            super.onRemove(state, worldIn, pos, newState, isMoving);
        }
    }

    /**
     * Get the associated wood, used for data gen
     *
     * @return the wood type
     */
    public WoodType getWoodType()
    {
        return this.woodType;
    }

    /**
     * Get the registered TimberFrameCentreType, used by the Data Generators
     *
     * @return the registered TimberFrameCentreType
     */
    public FrameType getFrameType()
    {
        return this.frameType;
    }

    @Override
    public void setPlacedBy(final World world, final BlockPos pos, final BlockState state, @Nullable final LivingEntity placer, final ItemStack stack)
    {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!world.isClientSide && state.getBlock() instanceof RackBlock && placer instanceof PlayerEntity)
        {
            ((TileEntityRack) world.getBlockEntity(pos)).neighborChange((PlayerEntity) placer);
        }
    }

    @Override
    public boolean removedByPlayer(final BlockState state, final World world, final BlockPos pos, final PlayerEntity player, final boolean willHarvest, final FluidState fluid)
    {
        final boolean rem = super.removedByPlayer(state, world, pos, player, willHarvest, fluid);
        if (!world.isClientSide && state.getBlock() instanceof RackBlock)
        {
            for (final Direction direction : Direction.values())
            {
                final TileEntity te = world.getBlockEntity(pos.relative(direction));
                if (te instanceof TileEntityRack)
                {
                    ((TileEntityRack) te).neighborChange(player);
                }
            }
        }
        return rem;
    }

    @SubscribeEvent
    public static void on(final BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getPlacedBlock().getBlock() instanceof RackBlock || event.getPlacedBlock().getBlock() instanceof ControllerBlock) || !(event.getEntity() instanceof PlayerEntity))
        {
            return;
        }

        final HashSet<BlockPos> posSet = new HashSet<>();
        final BlockPos result = TileEntityRack.visitPositions((World) event.getWorld(), posSet, event.getPos());
        if (result == null || result.equals(BlockPos.ZERO))
        {
            event.getEntity().sendMessage(new TranslationTextComponent("gui.storageracks.notconnected"), event.getEntity().getUUID());
            event.setCanceled(true);
        }
        else if (event.getPlacedBlock().getBlock() instanceof RackBlock)
        {
            final ControllerBlock controller = (ControllerBlock) event.getEntity().level.getBlockState(result).getBlock();
            if (posSet.size() > controller.getTier() * 20)
            {
                event.getEntity().sendMessage(new TranslationTextComponent("gui.storageracks.limitreached"), event.getEntity().getUUID());
                event.setCanceled(true);
            }
        }
    }
}
