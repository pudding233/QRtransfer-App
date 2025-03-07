package com.pudding233.qrtransfer.model

data class TransferPacket(
    val header: Header,
    val encoding: Encoding,
    val payload: String
) {
    data class Header(
        val magic: String,
        val fileSize: Long,
        val fileName: String,
        val blockIndex: Int,
        val totalBlocks: Int,
        val checksum: Int
    )

    data class Encoding(
        val degree: Int,
        val sourceBlocks: List<Int>,
        val checksum: Int
    )
} 