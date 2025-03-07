package com.pudding233.qrtransfer.decoder

import android.util.Log
import com.pudding233.qrtransfer.model.TransferPacket
import java.util.*
import kotlin.math.min

class FountainDecoder(
    private val fileSize: Long,
    private val totalBlocks: Int,
    private val blockSize: Int = 1024
) {
    private val receivedPackets = mutableListOf<TransferPacket>()
    private val decodedBlocks = arrayOfNulls<ByteArray>(totalBlocks)
    private var decodedCount = 0
    private val remainingIndices = (0 until totalBlocks).toMutableSet()

    fun addPacket(packet: TransferPacket): Boolean {
        if (isComplete()) return false
        
        // 验证校验和
        val calculatedChecksum = calculateChecksum(Base64.getDecoder().decode(packet.payload))
        if (calculatedChecksum != packet.header.checksum) {
            Log.w("FountainDecoder", "校验和不匹配，丢弃数据包")
            return false
        }

        // 处理度数为1的包（直接解码）
        if (packet.encoding.degree == 1) {
            val blockIndex = packet.encoding.sourceBlocks[0]
            if (blockIndex < totalBlocks && decodedBlocks[blockIndex] == null) {
                val blockData = Base64.getDecoder().decode(packet.payload)
                decodedBlocks[blockIndex] = blockData
                decodedCount++
                remainingIndices.remove(blockIndex)
                processReceivedPackets() // 尝试解码其他包
                return true
            }
            return false
        }

        // 存储其他度数的包
        receivedPackets.add(packet)
        return processReceivedPackets()
    }

    private fun processReceivedPackets(): Boolean {
        var decoded = false
        var continueDecoding = true

        while (continueDecoding && !isComplete()) {
            continueDecoding = false
            val iterator = receivedPackets.iterator()

            while (iterator.hasNext()) {
                val packet = iterator.next()
                val sourceBlocks = packet.encoding.sourceBlocks
                val unknownBlocks = sourceBlocks.filter { it in remainingIndices }

                when (unknownBlocks.size) {
                    0 -> iterator.remove() // 所有块都已解码，移除这个包
                    1 -> { // 只有一个未知块，可以解码
                        val blockIndex = unknownBlocks[0]
                        val blockData = decodeBlock(packet, sourceBlocks.filter { it !in remainingIndices })
                        decodedBlocks[blockIndex] = blockData
                        decodedCount++
                        remainingIndices.remove(blockIndex)
                        iterator.remove()
                        decoded = true
                        continueDecoding = true
                    }
                }
            }
        }

        return decoded
    }

    private fun decodeBlock(packet: TransferPacket, knownBlockIndices: List<Int>): ByteArray {
        val data = Base64.getDecoder().decode(packet.payload)
        val result = data.clone()

        // XOR已知块的数据
        for (index in knownBlockIndices) {
            val knownBlock = decodedBlocks[index] ?: continue
            for (i in 0 until min(result.size, knownBlock.size)) {
                result[i] = (result[i].toInt() xor knownBlock[i].toInt()).toByte()
            }
        }

        return result
    }

    fun getProgress(): Float = decodedCount.toFloat() / totalBlocks

    fun isComplete(): Boolean = decodedCount == totalBlocks

    fun getDecodedData(): ByteArray? {
        if (!isComplete()) return null

        val result = ByteArray(fileSize.toInt())
        var offset = 0

        for (block in decodedBlocks) {
            block?.let {
                val size = min(it.size, result.size - offset)
                System.arraycopy(it, 0, result, offset, size)
                offset += size
            }
        }

        return result
    }

    private fun calculateChecksum(data: ByteArray): Int {
        return data.fold(0) { acc, byte ->
            (acc * 31 + byte.toInt()) and 0x7FFFFFFF
        }
    }
} 