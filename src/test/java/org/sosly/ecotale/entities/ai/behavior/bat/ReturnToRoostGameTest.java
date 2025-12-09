package org.sosly.ecotale.entities.ai.behavior.bat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.blocks.RoostBlockEntity;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.EntityRegistry;
import org.sosly.ecotale.navigation.FlowFieldManager;
import org.sosly.ecotale.navigation.FlowFieldSolution;
import org.sosly.ecotale.utils.TestUtils;

@PrefixGameTestTemplate(false)
@GameTestHolder(EcoTale.MOD_ID)
public class ReturnToRoostGameTest {
    private static final int SPAWN_DISTANCE = 15;
    private static final int BAT_COUNT = 4;

    private static final BlockPos[] SPAWN_OFFSETS = {
        new BlockPos(SPAWN_DISTANCE, 0, 0),
        new BlockPos(-SPAWN_DISTANCE, 0, 0),
        new BlockPos(0, 0, SPAWN_DISTANCE),
        new BlockPos(0, 0, -SPAWN_DISTANCE)
    };

    @GameTest(template = "bat_roost", timeoutTicks = 500)
    public void batReturnsToRoostFromOutside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos roostPos = TestUtils.findRoostInStructure(helper);
        if (roostPos == null) {
            helper.fail("No roost block found in structure");
            return;
        }

        BlockPos absoluteRoost = helper.absolutePos(roostPos);
        BlockEntity blockEntity = level.getBlockEntity(absoluteRoost);
        if (!(blockEntity instanceof RoostBlockEntity roost)) {
            helper.fail("Roost block entity not found at " + absoluteRoost);
            return;
        }

        roost.setFlowFieldSolution(null);
        FlowFieldManager.getInstance().requestGeneration(roost);

        List<EcoTaleBat> bats = new ArrayList<>();

        helper.succeedWhen(() -> {
            hasValidSolution(roost);

            if (bats.isEmpty()) {
                for (int i = 0; i < BAT_COUNT; i++) {
                    EcoTaleBat bat = EntityRegistry.BAT.get().create(level);
                    if (bat == null) {
                        throw new GameTestAssertException("Failed to create bat " + i);
                    }
                    BlockPos spawnPos = absoluteRoost.offset(SPAWN_OFFSETS[i]);
                    bat.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5, 0, 0);
                    bat.setHome(GlobalPos.of(level.dimension(), absoluteRoost));
                    bat.setResting(false);
                    level.addFreshEntity(bat);
                    bats.add(bat);
                }
                throw new GameTestAssertException("Bats spawned, waiting for them to roost");
            }

            for (int i = 0; i < bats.size(); i++) {
                isResting(absoluteRoost, bats.get(i), i);
            }
        });
    }

    private void hasValidSolution(RoostBlockEntity roost) {
        FlowFieldSolution solution = roost.getFlowFieldSolution();

        if (solution == null) {
            throw new GameTestAssertException("Solution not yet computed");
        }

        if (solution.isFailed()) {
            throw new GameTestAssertException("Solution generation failed");
        }
    }

    private void isResting(BlockPos roost, EcoTaleBat bat, int index) {
        if (!bat.blockPosition().closerThan(roost, 2)) {
            throw new GameTestAssertException("Bat " + index + " is not at roosting position");
        }

        if (!bat.isResting()) {
            throw new GameTestAssertException("Bat " + index + " is not resting at roosting position");
        }
    }
}
