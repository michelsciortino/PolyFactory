package eu.pb4.polyfactory.block.mechanical.machines.crafting;

import com.kneelawk.graphlib.api.graph.user.BlockNode;
import eu.pb4.factorytools.api.block.FactoryBlock;
import eu.pb4.factorytools.api.block.MultiBlock;
import eu.pb4.factorytools.api.util.LazyItemStack;
import eu.pb4.factorytools.api.virtualentity.ItemDisplayElementUtil;
import eu.pb4.factorytools.api.virtualentity.LodItemDisplayElement;
import eu.pb4.polyfactory.block.mechanical.AxleBlock;
import eu.pb4.polyfactory.block.mechanical.RotationUser;
import eu.pb4.polyfactory.block.network.NetworkComponent;
import eu.pb4.polyfactory.models.RotationAwareModel;
import eu.pb4.polyfactory.nodes.generic.FunctionalAxisNode;
import eu.pb4.polyfactory.nodes.generic.SimpleAxisNode;
import eu.pb4.polyfactory.nodes.mechanical.RotationData;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static eu.pb4.polyfactory.util.FactoryUtil.id;

public class TrommelBlock extends MultiBlock implements FactoryBlock, EntityBlock, WorldlyContainerHolder, RotationUser {
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Identifier model;
    private final Identifier modelInner;

    public TrommelBlock(Properties settings) {
        super(2, 1, 2, settings);
        this.model = settings.blockIdOrThrow().identifier().withPrefix("block/");
        this.modelInner = this.model.withSuffix("_inner");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected void onPlacedMultiBlock(Level world, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        NetworkComponent.Rotational.updateRotationalAt(world, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onPlace(state, world, pos, oldState, notify);
        NetworkComponent.Rotational.updateRotationalAt(world, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        NetworkComponent.Rotational.updateRotationalAt(world, pos);
    }

    @Override
    public int getMaxX(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? 1 : 0;
    }

    @Override
    public int getMaxZ(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? 1 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCenter(state) ? new TrommelBlockEntity(pos, state) : null;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() && world.getBlockEntity(getCenter(state, pos)) instanceof TrommelBlockEntity be) {
            be.openGui((ServerPlayer) player);
            return InteractionResult.SUCCESS_SERVER;
        }

        return super.useWithoutItem(state, world, pos, player,  hit);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return isCenter(state) ? TrommelBlockEntity::ticker : null;
    }

    @Override
    public BlockState getPolymerBlockState(BlockState blockState, @Nullable PacketContext packetContext) {
        return Blocks.BARRIER.defaultBlockState();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
        return Blocks.IRON_BLOCK.defaultBlockState();
    }

    @Override
    public void updateRotationalData(RotationData.State modifier, BlockState state, ServerLevel world, BlockPos pos) {
        if (isCenter(state) && world.getBlockEntity(pos) instanceof TrommelBlockEntity be) {
            modifier.stress(be.getStress());
        }
    }

    @Override
    public Collection<BlockNode> createRotationalNodes(BlockState state, ServerLevel world, BlockPos pos) {
        return List.of(isCenter(state) ? new FunctionalAxisNode(state.getValue(FACING).getAxis()) : new SimpleAxisNode(state.getValue(FACING).getAxis()));
    }

    @Override
    public WorldlyContainer getContainer(BlockState state, LevelAccessor world, BlockPos pos) {
        var center = this.getCenter(state, pos);
        var dir = state.getValue(FACING);
        var value = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1;

        return world.getBlockEntity(center) instanceof TrommelBlockEntity be
                ? (state.getValue(dir.getAxis() == Direction.Axis.X ? this.partX : this.partZ) == value ? be.worldInputContainer : be.worldOutputContainer) : null;
    }

    @Override
    public ElementHolder createElementHolder(ServerLevel serverWorld, BlockPos pos, BlockState initialBlockState) {
        return isCenter(initialBlockState) ? new Model(initialBlockState) : null;
    }

    @Override
    public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
        return true;
    }

    public final class Model extends RotationAwareModel {
        private final ItemDisplayElement main;
        private final ItemDisplayElement inner;
        private final ItemDisplayElement item;
        private boolean active;
        private float progress;
        private float progressCurrent;

        private Model(BlockState state) {
            this.main = ItemDisplayElementUtil.createSimple(model);
            this.main.setScale(new Vector3f(2));
            this.inner = LodItemDisplayElement.createSimple(ItemDisplayElementUtil.getModel(modelInner).get(), this.getUpdateRate(), 0.3f, 0.7f);
            this.item = LodItemDisplayElement.createSimple(ItemStack.EMPTY, this.getUpdateRate(), 0.3f, 0.5f);

            this.inner.setViewRange(0.7f);
            this.item.setViewRange(0.5f);

            this.updateStatePos(state);
            this.updateAnimation(0, state.getValue(FACING));
            this.addElement(this.main);
            this.addElement(this.inner);
            this.addElement(this.item);
        }

        private void updateStatePos(BlockState state) {
            var direction = state.getValue(FACING);
            this.main.setTranslation(new Vector3f(0, 0, direction.getAxisDirection().getStep() * 0.5f));
            this.inner.setTranslation(this.main.getTranslation());
            this.main.setYaw(direction.toYRot());
            this.inner.setYaw(direction.toYRot());
            this.item.setYaw(direction.toYRot());
        }

        private void updateAnimation(float rotation, Direction facing) {
            var mat = matStack();
            mat.translate(0, 0, facing.getAxisDirection().getStep() * 0.5f);
            mat.pushMatrix();
            mat.rotateZ(rotation * facing.getAxisDirection().getStep());
            mat.scale(2, 2f, 2);
            this.inner.setTransformation(mat);
            mat.popMatrix();

            mat.translate(0, -0.3f, Mth.lerp(this.progress, -0.65f, 0.65f));
            mat.scale(Mth.lerp(this.progress, 0.65f, 0.4f)).scale(1, Mth.lerp(this.progress, 1f, 0.6f), 1);
            mat.translate(0, 0.25f + (this.active ? (float) (Math.sin(this.getTick() * 200 / 160f) + 1) * 0.04f : 0), 0);
            mat.rotateZ(rotation * facing.getAxisDirection().getStep());
            this.progressCurrent = this.progress;
            this.item.setTransformation(mat);

            /*var translate = new Vector3f(this.item.getTranslation()).rotateY(this.item.getYaw() * Mth.DEG_TO_RAD);

            var packet = new ClientboundLevelParticlesPacket(
                    new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(this.item.getItem())),
                    false, false,
                    this.getPos().x + translate.x(),
                    this.getPos().y + translate.y(),
                    this.getPos().z + translate.z(),
                    (float) Math.random(), (float) Math.random(), (float) Math.random(), 0.01f, 0);

            this.sendPacket(packet);*/
        }

        @Override
        public void notifyUpdate(HolderAttachment.UpdateType updateType) {
            if (updateType == BlockBoundAttachment.BLOCK_STATE_UPDATE) {
                updateStatePos(this.blockState());
            }
        }

        @Override
        protected void onTick() {
            var b = this.getTick() % this.getUpdateRate() == 0;

            if (b) {
                var dir = this.blockState().getValue(FACING);
                var interpolate = this.progress >= this.progressCurrent;
                this.updateAnimation(RotationUser.getRotation(this.getAttachment().getWorld(), this.blockPos()).rotation(), dir);

                this.inner.startInterpolationIfDirty();
                if (interpolate) {
                    this.item.startInterpolationIfDirty();
                }
            }
        }

        public void setItem(ItemStack item) {
            this.item.setItem(item.copy());
        }

        public void setProgress(float progress) {
            this.progress = progress;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
