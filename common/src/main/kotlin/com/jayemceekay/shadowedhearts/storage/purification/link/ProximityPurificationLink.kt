package com.jayemceekay.shadowedhearts.storage.purification.link

import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import com.jayemceekay.shadowedhearts.content.blocks.PurificationChamberBlock
import com.jayemceekay.shadowedhearts.content.blocks.entity.PurificationChamberBlockEntity
import com.jayemceekay.shadowedhearts.integration.ftbchunks.FTBChunksClaimBridgeHolder
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * A PurificationLink tied to a specific in-world Purification Chamber position and world.
 * Mirrors Cobblemon's ProximityPCLink for PC.
 */
class ProximityPurificationLink(
    store: PurificationChamberStore,
    playerID: UUID,
    /** Bottom (base) position of the chamber. */
    pos: BlockPos,
    /** World where the chamber exists. */
    val world: ServerLevel
) : PurificationLink(store = store, playerID = playerID) {
    val pos: BlockPos = pos.immutable()
    private val openingPlayer = world.server.playerList.getPlayer(playerID)
    private val chamber = if (world.hasChunkAt(this.pos))
        world.getBlockEntity(this.pos) as? PurificationChamberBlockEntity else null

    override fun isPermitted(player: ServerPlayer): Boolean {
        if (openingPlayer !== player || player.serverLevel() !== world ||
            player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE * MAX_DISTANCE ||
            !world.hasChunkAt(pos) || !world.hasChunkAt(pos.above())) return false
        if (chamber == null || chamber.isRemoved || world.getBlockEntity(pos) !== chamber) return false

        val bottom = world.getBlockState(pos)
        val top = world.getBlockState(pos.above())
        return bottom.block is PurificationChamberBlock && top.`is`(bottom.block) &&
            bottom.getValue(PurificationChamberBlock.PART) == PurificationChamberBlock.Part.BOTTOM &&
            top.getValue(PurificationChamberBlock.PART) == PurificationChamberBlock.Part.TOP &&
            bottom.getValue(PurificationChamberBlock.FACING) == top.getValue(PurificationChamberBlock.FACING) &&
            // Recheck on opening, every action and the link manager's idle-session tick.
            FTBChunksClaimBridgeHolder.INSTANCE.canInteractBlock(player, pos, InteractionHand.MAIN_HAND)
    }

    companion object {
        const val MAX_DISTANCE = 10.0
    }
}
