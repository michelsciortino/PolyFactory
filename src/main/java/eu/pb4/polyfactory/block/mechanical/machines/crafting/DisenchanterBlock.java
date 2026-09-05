package eu.pb4.polyfactory.block.mechanical.machines.crafting;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import eu.pb4.polyfactory.block.FactoryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import eu.pb4.polyfactory.block.mechanical.machines.crafting.DisenchanterBlockEntity;
import net.minecraft.world.level.block.Block;

public class DisenchanterBlock extends TallItemMachineBlock implements MovingItemConsumer, MovingItemProvider {
    public DisenchanterBlock(Properties settings) {
        super(settings);
    }

    @Override
    public boolean pushItemTo(WorldPointer self, Direction pushDirection, Direction relative, BlockPos conveyorPos, MovingItemContainerHolder conveyor) {
        if (self.getBlockState().getValue(INPUT_FACING) == pushDirection || self.getBlockState().getValue(PART) == Part.TOP) {
            return false;
        }

        var be = (DisenchanterBlockEntity) self.getBlockEntity();

        if (self.getBlockState().getValue(INPUT_FACING).getOpposite() != pushDirection) {
            var stack = be.getItem(1);
            if (stack.isEmpty()) {
                be.setItem(1, conveyor.pullAndDestroy().get());
                return true;
            }

            var container = conveyor.getContainer();

            if (ItemStack.isSameItemSameComponents(container.get(), stack)) {
                var i = Math.min(container.get().getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(i);
                container.get().shrink(i);
            }

            if (container.get().isEmpty()) {
                conveyor.clearContainer();
            }

            return true;
        }

        var container = be.getContainerHolder(0);

        if (container.isContainerEmpty()) {
            container.pushAndAttach(conveyor.pullAndRemove());
        } else {
            var targetStack = container.getContainer().get();
            var sourceStack = conveyor.getContainer().get();

            if (ItemStack.isSameItemSameComponents(container.getContainer().get(), conveyor.getContainer().get())) {
                var count = Math.min(targetStack.getCount() + sourceStack.getCount(), container.getMaxStackCount(sourceStack));
                if (count != targetStack.getCount()) {
                    var dec = count - targetStack.getCount();
                    targetStack.grow(dec);
                    sourceStack.shrink(dec);
                }

                if (sourceStack.isEmpty()) {
                    conveyor.clearContainer();
                }
            }
        }

        return true;
    }

    @Override
    public void getItemFrom(WorldPointer self, Direction pushDirection, Direction relative, BlockPos conveyorPos, MovingItemContainerHolder conveyor) {
        var inputDir = self.getBlockState().getValue(INPUT_FACING);
        if (!conveyor.isContainerEmpty() || pushDirection == inputDir || inputDir.getOpposite() != relative || self.getBlockState().getValue(PART) == Part.TOP) {
            return;
        }

        var be = (DisenchanterBlockEntity) self.getBlockEntity();

        var out = be.getContainerHolder(2);

        if (out.isContainerEmpty()) {
            return;
        }
        var stack = out.getContainer().get();

        var amount = Math.min(stack.getCount(), out.getMaxStackCount(stack));

        if (stack.getCount() == amount) {
            conveyor.pushAndAttach(out.pullAndRemove());
        } else {
            stack.shrink(amount);
            conveyor.setMovementPosition(pushDirection == inputDir.getOpposite() ? 0 : 0.5);
            conveyor.pushNew(stack.copyWithCount(amount));
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world instanceof ServerLevel && type == FactoryBlockEntities.PRESS ? DisenchanterBlockEntity::ticker : null;
    }

    @Override
    protected BlockEntity createSourceBlockEntity(BlockPos pos, BlockState state) {
        return new DisenchanterBlockEntity(pos, state);
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
        return net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState();
    }
}
