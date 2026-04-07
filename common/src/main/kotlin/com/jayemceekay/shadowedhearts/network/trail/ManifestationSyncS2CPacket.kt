package com.jayemceekay.shadowedhearts.network.trail

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Server -> Client: sync manifestation buildup state.
 * Carries phase, progress, and convergence target for client-side VFX.
 */
class ManifestationSyncS2CPacket(
    val phase: Int,          // 0=inactive, 1=signal spike, 2=convergence, 3=crescendo, 4=ready/spawned
    val progress: Float,     // 0.0 to 1.0
    val convergenceTarget: BlockPos?, // where the trail converges toward
    val tension: Float
) : NetworkPacket<ManifestationSyncS2CPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(phase)
        buf.writeFloat(progress)
        buf.writeBoolean(convergenceTarget != null)
        if (convergenceTarget != null) buf.writeBlockPos(convergenceTarget)
        buf.writeFloat(tension)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "manifestation_sync")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): ManifestationSyncS2CPacket {
            val phase = buf.readVarInt()
            val progress = buf.readFloat()
            val hasTarget = buf.readBoolean()
            val target = if (hasTarget) buf.readBlockPos() else null
            val tension = buf.readFloat()
            return ManifestationSyncS2CPacket(phase, progress, target, tension)
        }
    }
}
