package com.pudding233.qrtransfer.model

data class QRPacket(
    val header: Header,
    val encoding: Encoding,
    val payload: String
)

data class Header(
    val magic: String,
    val fileSize: Long,
    val fileName: String,
    val totalBlocks: Int,
    val blockIndex: Int,
    val checksum: Int,
    val reserved: Int = 0
)

data class Encoding(
    val seed: Int,
    val degree: Int,
    val sourceBlocks: List<Int>,
    val checksum: Int
) 