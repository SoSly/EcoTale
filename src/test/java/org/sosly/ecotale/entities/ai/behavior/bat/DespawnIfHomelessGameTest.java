package org.sosly.ecotale.entities.ai.behavior.bat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.EntityRegistry;
import org.sosly.ecotale.utils.TestUtils;

@PrefixGameTestTemplate(false)
@GameTestHolder(EcoTale.MOD_ID)
public class DespawnIfHomelessGameTest {

    @GameTest(template = "bat_roost", timeoutTicks = 100, attempts = 3, required = true, requiredSuccesses = 3)
    public void batDespawnsWhenRoostDestroyed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos roostPos = TestUtils.findRoostInStructure(helper);
        if (roostPos == null) {
            helper.fail("No roost block found in structure");
            return;
        }

        EcoTaleBat bat = EntityRegistry.BAT.get().create(level);
        if (bat == null) {
            helper.fail("Failed to create bat");
            return;
        }

        BlockPos absoluteRoost = helper.absolutePos(roostPos);
        BlockPos spawnPos = absoluteRoost.below();

        bat.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5, 0, 0);
        bat.setHome(GlobalPos.of(level.dimension(), absoluteRoost));
        level.addFreshEntity(bat);

        helper.runAfterDelay(20, () -> {
            helper.setBlock(roostPos, Blocks.AIR.defaultBlockState());

            helper.runAfterDelay(40, () -> {
                if (bat.isRemoved()) {
                    helper.succeed();
                } else {
                    helper.fail("Bat did not despawn after roost was destroyed");
                }
            });
        });
    }

}
