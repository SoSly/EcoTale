package org.sosly.ecotale.entities.ai.behavior.bat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
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
    private static final int CLOSE_ENOUGH = 1;

    @GameTest(template = "bat_roost", timeoutTicks = 2400)
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

        waitForFlowFieldThenSpawnBat(helper, level, roost, absoluteRoost, 0);
    }

    private void waitForFlowFieldThenSpawnBat(
            GameTestHelper helper,
            ServerLevel level,
            RoostBlockEntity roost,
            BlockPos absoluteRoost,
            int attempts
    ) {
        if (attempts > 100) {
            helper.fail("Flow field generation timed out after 100 ticks");
            return;
        }

        FlowFieldSolution solution = roost.getFlowFieldSolution();
        if (solution == null) {
            helper.runAfterDelay(1, () -> waitForFlowFieldThenSpawnBat(helper, level, roost, absoluteRoost, attempts + 1));
            return;
        }

        if (solution.isFailed()) {
            helper.fail("Flow field generation failed - no valid exit path found");
            return;
        }

        EcoTaleBat bat = EntityRegistry.BAT.get().create(level);
        if (bat == null) {
            helper.fail("Failed to create bat");
            return;
        }

        BlockPos spawnPos = absoluteRoost.offset(0, 0, SPAWN_DISTANCE);
        bat.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5, 0, 0);
        bat.setHome(GlobalPos.of(level.dimension(), absoluteRoost));
        bat.setResting(false);
        level.addFreshEntity(bat);

        helper.runAfterDelay(450, () -> {
            BlockPos hangPos = absoluteRoost.below();
            if (bat.blockPosition().closerThan(hangPos, CLOSE_ENOUGH)) {
                helper.succeed();
            } else {
                double distance = Math.sqrt(bat.blockPosition().distSqr(hangPos));
                helper.fail("Bat did not return to roost. Distance: " + String.format("%.1f", distance) + " blocks");
            }
        });
    }
}
