package org.sosly.ecotale.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.EntityRegistry;
import org.sosly.ecotale.navigation.FlowFieldSolution;

public class RoostBlockEntity extends BlockEntity {
    private FlowFieldSolution flowFieldSolution;

    public RoostBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.ROOST.get(), pos, state);
    }

    public FlowFieldSolution getFlowFieldSolution() {
        return flowFieldSolution;
    }

    public void setFlowFieldSolution(FlowFieldSolution solution) {
        this.flowFieldSolution = solution;
    }

    public void spawnColony(WorldGenLevel level, RandomSource random) {
        ServerLevel serverLevel = level.getLevel();
        BlockPos roostPos = this.getBlockPos();
        GlobalPos home = GlobalPos.of(serverLevel.dimension(), roostPos);
        int count = random.nextIntBetweenInclusive(1, 4);

        for (int i = 0; i < count; i++) {
            EcoTaleBat bat = EntityRegistry.BAT.get().create(serverLevel);
            if (bat == null) {
                continue;
            }

            double x = roostPos.getX() + 0.1 + random.nextDouble() * 0.8;
            double y = roostPos.getY() - 0.1;
            double z = roostPos.getZ() + 0.1 + random.nextDouble() * 0.8;

            bat.moveTo(x, y, z, random.nextFloat() * 360F, 0F);
            bat.setResting(true);
            bat.setHome(home);
            level.addFreshEntity(bat);
        }
    }
}
