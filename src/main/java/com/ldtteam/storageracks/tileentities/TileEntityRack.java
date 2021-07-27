package com.ldtteam.storageracks.tileentities;

import com.ldtteam.storageracks.ItemStorage;
import com.ldtteam.storageracks.blocks.RackBlock;
import com.ldtteam.storageracks.blocks.RackType;
import com.ldtteam.storageracks.inv.ContainerRack;
import com.ldtteam.storageracks.utils.BlockPosUtil;
import com.ldtteam.storageracks.utils.ItemStackUtils;
import com.ldtteam.storageracks.utils.WorldUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.ldtteam.storageracks.utils.Constants.*;
import static com.ldtteam.storageracks.utils.NbtTagConstants.*;
import static net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND;

/**
 * Tile entity for the warehouse shelves.
 */
public class TileEntityRack extends AbstractTileEntityRack
{
    /**
     * The content of the chest.
     */
    private final Map<ItemStorage, Integer> content = new HashMap<>();

    /**
     * Size multiplier of the inventory. 0 = default value. 1 = 1*9 additional slots, and so on.
     */
    private int size = 0;

    /**
     * Amount of free slots
     */
    private int freeSlots = 0;

    /**
     * Offset to the controller.
     */
    private BlockPos controller;

    /**
     * Last optional we created.
     */
    private LazyOptional<IItemHandler> lastOptional;

    /**
     * New TileEntity.
     * @param pos position.
     * @param state initial state.
     */
    public TileEntityRack(final BlockPos pos, final BlockState state)
    {
        super(ModTileEntities.RACK, pos, state);
    }

    /**
     * Selfmade rotate method, called from block.
     * @param rotationIn the rotation.
     */
    public void rotate(final Rotation rotationIn)
    {
        if (controller != null)
        {
            controller = controller.rotate(rotationIn);
        }
    }

    @Override
    public int getFreeSlots()
    {
        return freeSlots;
    }

    @Override
    public boolean hasItemStack(final ItemStack stack, final int count)
    {
        final ItemStorage checkItem = new ItemStorage(stack);
        return content.getOrDefault(checkItem, 0) >= count;
    }

    @Override
    public int getCount(final ItemStack stack)
    {
        final ItemStorage checkItem = new ItemStorage(stack);
        return content.getOrDefault(checkItem, 0);
    }

    @Override
    public boolean hasItemStack(@NotNull final Predicate<ItemStack> itemStackSelectionPredicate)
    {
        for (final Map.Entry<ItemStorage, Integer> entry : content.entrySet())
        {
            if (itemStackSelectionPredicate.test(entry.getKey().getItemStack()))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasSimilarStack(@NotNull final ItemStack stack)
    {
        final ItemStorage checkItem = new ItemStorage(stack);
        if (content.containsKey(checkItem))
        {
            return true;
        }

        for (final ItemStorage storage : content.keySet())
        {
           if (checkItem.getPrimaryCreativeTabIndex() == storage.getPrimaryCreativeTabIndex())
           {
               return true;
           }
        }

        return false;
    }

    /**
     * Gets the content of the Rack
     *
     * @return the map of content.
     */
    public Map<ItemStorage, Integer> getAllContent()
    {
        return content;
    }

    @Override
    public void upgradeItemStorage()
    {
        final RackInventory tempInventory = new RackInventory(DEFAULT_SIZE + size * SLOT_PER_LINE);
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            tempInventory.setStackInSlot(slot, inventory.getStackInSlot(slot));
        }

        inventory = tempInventory;
        invalidateCap();
    }

    @Override
    public int getItemCount(final Predicate<ItemStack> predicate)
    {
        for (final Map.Entry<ItemStorage, Integer> entry : content.entrySet())
        {
            if (predicate.test(entry.getKey().getItemStack()))
            {
                return entry.getValue();
            }
        }
        return 0;
    }

    @Override
    public void updateItemStorage()
    {
        if (level != null && !level.isClientSide)
        {
            final boolean empty = content.isEmpty();
            updateContent();

            if ((empty && !content.isEmpty()) || !empty && content.isEmpty())
            {
                updateBlockState();
            }
            setChanged();
        }
    }

    /**
     * Just do the content update.
     */
    private void updateContent()
    {
        content.clear();
        freeSlots = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);

            if (ItemStackUtils.isEmpty(stack))
            {
                freeSlots++;
                continue;
            }

            final ItemStorage storage = new ItemStorage(stack.copy());
            int amount = ItemStackUtils.getSize(stack);
            if (content.containsKey(storage))
            {
                amount += content.remove(storage);
            }
            content.put(storage, amount);
        }
    }

    @Override
    public void updateBlockState()
    {
        if (level != null && level.getBlockState(getBlockPos()).getBlock() instanceof RackBlock)
        {
            if (content.isEmpty())
            {
                level.setBlock(this.getBlockPos(), level.getBlockState(this.getBlockPos()).setValue(RackBlock.VARIANT, RackType.DEFAULT), 0x03);
            }
            else
            {
                level.setBlock(this.getBlockPos(), level.getBlockState(this.getBlockPos()).setValue(RackBlock.VARIANT, RackType.FULL), 0x03);
            }
        }
    }

    @Override
    public ItemStackHandler createInventory(final int slots)
    {
        return new RackInventory(slots);
    }

    @Override
    public boolean isEmpty()
    {
        return content.isEmpty();
    }


    @Override
    public void load(@NotNull final CompoundTag compound)
    {
        super.load(compound);

        int oldSize = compound.getInt(TAG_SIZE);
        checkForUpgrade(getBlockState(), oldSize);

        inventory = createInventory(DEFAULT_SIZE + size * SLOT_PER_LINE);

        final ListTag inventoryTagList = compound.getList(TAG_INVENTORY, TAG_COMPOUND);
        for (int i = 0; i < inventoryTagList.size(); i++)
        {
            final CompoundTag inventoryCompound = inventoryTagList.getCompound(i);
            if (!inventoryCompound.contains(TAG_EMPTY))
            {
                final ItemStack stack = ItemStack.of(inventoryCompound);
                inventory.setStackInSlot(i, stack);
            }
        }

        updateContent();

        this.controllerPos = BlockPosUtil.readFromNBT(compound, TAG_POS);
        invalidateCap();
    }

    //Make a dag between rack -> controller

    public void checkForUpgrade(final BlockState state, final int oldSize)
    {
        if (state.getBlock().getRegistryName().getPath().contains("stone"))
        {
            size = 1;
        }
        else if (state.getBlock().getRegistryName().getPath().contains("iron"))
        {
            size = 2;
        }
        else if (state.getBlock().getRegistryName().getPath().contains("gold"))
        {
            size = 3;
        }
        else if (state.getBlock().getRegistryName().getPath().contains("emerald"))
        {
            size = 4;
        }
        else if (state.getBlock().getRegistryName().getPath().contains("diamond"))
        {
            size = 5;
        }

        if (oldSize != size)
        {
            upgradeItemStorage();
        }
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull final CompoundTag compound)
    {
        super.save(compound);
        compound.putInt(TAG_SIZE, size);

        @NotNull final ListTag inventoryTagList = new ListTag();
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            @NotNull final CompoundTag inventoryCompound = new CompoundTag();
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty())
            {
                inventoryCompound.putBoolean(TAG_EMPTY, true);
            }
            else
            {
                stack.save(inventoryCompound);
            }
            inventoryTagList.add(inventoryCompound);
        }
        compound.put(TAG_INVENTORY, inventoryTagList);
        BlockPosUtil.writeToNBT(compound, TAG_POS, controllerPos);
        return compound;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        final CompoundTag compound = new CompoundTag();
        return new ClientboundBlockEntityDataPacket(this.getBlockPos(), 0, this.save(compound));
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag()
    {
        return this.save(new CompoundTag());
    }

    @Override
    public void onDataPacket(final Connection net, final ClientboundBlockEntityDataPacket packet)
    {
        this.load(packet.getTag());
    }

    @Override
    public void handleUpdateTag(final CompoundTag tag)
    {
        this.load(tag);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull final Capability<T> capability, final Direction dir)
    {
        if (!remove && capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
        {
            if (lastOptional != null && lastOptional.isPresent())
            {
                return lastOptional.cast();
            }

            lastOptional = LazyOptional.of(() ->
            {
                if (this.isRemoved())
                {
                    return new RackInventory(0);
                }

                return getInventory();
            });
            return lastOptional.cast();
        }
        return super.getCapability(capability, dir);
    }

    @Override
    public void setChanged()
    {
        WorldUtil.markChunkDirty(level, getBlockPos());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(final int id, @NotNull final Inventory inv, @NotNull final Player player)
    {
        return new ContainerRack(id, inv, getBlockPos());
    }

    @NotNull
    @Override
    public Component getDisplayName()
    {
        return new TranslatableComponent("container.title.rack");
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        invalidateCap();
    }

    /**
     * Invalidates the cap
     */
    private void invalidateCap()
    {
        if (lastOptional != null && lastOptional.isPresent())
        {
            lastOptional.invalidate();
        }

        lastOptional = null;
    }

    public int getSize()
    {
        return size;
    }

    /**
     * Return false if not successful.
     * @return false if so.
     */
    public void neighborChange()
    {
        final Set<BlockPos> visitedPositions = new HashSet<>();
        final BlockPos controller = visitPositions(level, visitedPositions, this.getBlockPos());
        if (controller != BlockPos.ZERO && controller != null)
        {
            this.controller = getBlockPos().subtract(controller);
            ((TileEntityController) level.getBlockEntity(controller)).addAll(visitedPositions);
            for (final BlockPos pos : visitedPositions)
            {
                if (!pos.equals(controller))
                {
                    ((TileEntityRack) level.getBlockEntity(pos)).controller = pos.subtract(controller);
                }
            }
        }
        else if (controller == null)
        {
            BlockPos oldController = null;
            for (final BlockPos pos : visitedPositions)
            {
                final TileEntityRack rack = ((TileEntityRack) level.getBlockEntity(pos));
                if (rack.controller != null)
                {
                    oldController = rack.getBlockPos().subtract(rack.controller);
                    rack.controller = null;
                }
            }

            if (oldController != null)
            {
                final TileEntityController te = ((TileEntityController) level.getBlockEntity(oldController));
                if (te != null)
                {
                    te.removeAll(visitedPositions);
                }
            }
        }
    }

    @Override
    public void setBlockState(@NotNull final BlockState state)
    {
        super.setBlockState(state);
        invalidateCap();
    }

    public static BlockPos visitPositions(final Level level, final Set<BlockPos> visitedPositions, final BlockPos current)
    {
        BlockPos controller = null;
        if (level.getBlockEntity(current) instanceof TileEntityController)
        {
            controller = current;
        }

        visitedPositions.add(current);

        for (final Direction dir : Direction.values())
        {
            final BlockPos next = current.relative(dir);
            if (!visitedPositions.contains(next))
            {
                final BlockEntity te = level.getBlockEntity(next);
                if (te instanceof TileEntityRack || te instanceof TileEntityController)
                {
                    final BlockPos cont = visitPositions(level, visitedPositions, next);
                    if (cont != null)
                    {
                        if (cont.equals(BlockPos.ZERO) || (controller != null && !cont.equals(controller)))
                        {
                            return BlockPos.ZERO;
                        }
                        controller = cont;
                    }
                }
            }
        }
        return controller;
    }
}
