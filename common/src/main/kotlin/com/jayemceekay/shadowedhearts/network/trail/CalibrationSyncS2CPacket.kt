package com.jayemceekay.shadowedhearts.network.trail

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Server -> Client: sync calibration sequence state so the client can render
 * the directional input prompts and progress feedback.
 */
class CalibrationSyncS2CPacket(
    val sequence: List<CalibrationSequence.Direction>,
    val variant: CalibrationSequence.Variant,
    val timeLimitTicks: Int,
    val currentInputIndex: Int,
    val mistakes: Int,
    val elapsedTicks: Int,
    val completed: Boolean,
    val failed: Boolean,
    val grade: CalibrationSequence.Grade? = null
) : NetworkPacket<CalibrationSyncS2CPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(sequence.size)
        for (dir in sequence) {
            buf.writeVarInt(dir.toId())
        }
        buf.writeVarInt(variant.toId())
        buf.writeVarInt(timeLimitTicks)
        buf.writeVarInt(currentInputIndex)
        buf.writeVarInt(mistakes)
        buf.writeVarInt(elapsedTicks)
        buf.writeBoolean(completed)
        buf.writeBoolean(failed)
        if (grade != null) {
            buf.writeBoolean(true)
            buf.writeVarInt(grade.toId())
        } else {
            buf.writeBoolean(false)
        }
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "calibration_sync")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): CalibrationSyncS2CPacket {
            val count = buf.readVarInt()
            val sequence = ArrayList<CalibrationSequence.Direction>(count)
            repeat(count) {
                sequence.add(CalibrationSequence.Direction.fromId(buf.readVarInt()))
            }
            val variant = CalibrationSequence.Variant.fromId(buf.readVarInt())
            val timeLimitTicks = buf.readVarInt()
            val currentInputIndex = buf.readVarInt()
            val mistakes = buf.readVarInt()
            val elapsedTicks = buf.readVarInt()
            val completed = buf.readBoolean()
            val failed = buf.readBoolean()
            val hasGrade = buf.readBoolean()
            val grade = if (hasGrade) CalibrationSequence.Grade.fromId(buf.readVarInt()) else null
            return CalibrationSyncS2CPacket(
                sequence, variant, timeLimitTicks,
                currentInputIndex, mistakes, elapsedTicks,
                completed, failed, grade
            )
        }

        /**
         * Build a sync packet from a live CalibrationSequence.
         */
        @JvmStatic
        fun fromSequence(seq: CalibrationSequence, grade: CalibrationSequence.Grade? = null): CalibrationSyncS2CPacket {
            return CalibrationSyncS2CPacket(
                sequence = seq.sequence,
                variant = seq.variant,
                timeLimitTicks = seq.timeLimitTicks,
                currentInputIndex = seq.currentInputIndex,
                mistakes = seq.mistakes,
                elapsedTicks = seq.elapsedTicks,
                completed = seq.isCompleted,
                failed = seq.isFailed,
                grade = grade
            )
        }
    }
}
