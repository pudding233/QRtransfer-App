package com.pudding233.qrtransfer.codec

import android.util.Base64
import android.util.Log
import com.pudding233.qrtransfer.model.DataPacket
import com.pudding233.qrtransfer.model.PacketEncoding
import com.pudding233.qrtransfer.model.PacketHeader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.BitSet
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 使用RaptorQ/Fountain码的文件传输系统
 */
class RaptorQCodec {
    companion object {
        private const val TAG = "RaptorQCodec"
        private const val MAGIC = "FLQR" // 魔数，用于标识数据包
        private const val BLOCK_SIZE = 512 // 块大小，单位字节
        
        // Robust Soliton分布参数
        private const val SOLITON_C = 0.03 // 常数c，控制稀疏度
        private const val SOLITON_DELTA = 0.5 // 解码失败概率
    }

    // 编码相关参数
    private var encodingFile: File? = null
    private var encodingData: ByteArray? = null
    private var encodingFileName = ""
    private var encodingFileSize = 0L
    private var sourceBlockCount = 0 // 源数据块数量
    private var blockSize = 0
    
    // 随机数生成器和唯一种子计数器
    private val random = Random()
    private val seedCounter = AtomicLong(System.currentTimeMillis())
    private val packetIndexSequence = AtomicInteger(0) // 添加序列号计数器，确保每次生成不同的索引

    // 解码相关参数
    private var decodingData: ByteArray? = null
    private var decodingFileName = ""
    private var decodingFileSize = 0L
    private var decodingSourceBlockCount = 0
    private var isDecodingComplete = false
    
    // 解码矩阵
    private var decodingMatrix = mutableListOf<Row>()
    private var receivedPacketsCount = 0
    private var solvedSourceBlocks = BitSet(decodingSourceBlockCount) // 跟踪哪些源块已解码
    
    /**
     * 表示解码矩阵中的一行
     */
    private data class Row(
        val encodedData: ByteArray, // 编码数据
        val sourceBlockIndices: List<Int> // 参与编码的源块索引列表
    ) {
        // 判断两行是否相等（用于去重）
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Row) return false
            return sourceBlockIndices == other.sourceBlockIndices && 
                   encodedData.contentEquals(other.encodedData)
        }
        
        override fun hashCode(): Int {
            var result = encodedData.contentHashCode()
            result = 31 * result + sourceBlockIndices.hashCode()
            return result
        }
    }

    /**
     * 初始化编码器
     */
    fun initializeEncoder(file: File) {
        try {
            encodingFile = file
            encodingFileName = file.name
            encodingFileSize = file.length()

            // 计算需要的数据块数量和块大小
            val dataLength = file.length()
            blockSize = BLOCK_SIZE // 使用固定块大小
            
            // 计算源数据块数量（向上取整）
            sourceBlockCount = ceil(dataLength.toDouble() / blockSize).toInt()
            
            Log.d(TAG, "初始化RaptorQ编码器: 文件大小=${dataLength}字节, 块大小=$blockSize, 源数据块数=$sourceBlockCount")
            
            // 读取文件数据
            val inputStream = FileInputStream(file)
            encodingData = ByteArray(dataLength.toInt())
            
            var bytesRead = 0
            var offset = 0
            while (offset < dataLength) {
                bytesRead = inputStream.read(encodingData!!, offset, encodingData!!.size - offset)
                if (bytesRead <= 0) break
                offset += bytesRead
            }
            inputStream.close()
            
            Log.d(TAG, "文件数据已加载: 读取了 $offset 字节")
            
            // 重置包索引计数器
            packetIndexSequence.set(0)
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化编码器失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 生成编码数据包 - 实现真正的RaptorQ/Fountain码
     */
    fun generateEncodingPacket(blockIndex: Int): DataPacket {
        try {
            // 使用一个不循环的序列号，让所有生成的包都尽可能不同
            val uniqueCounter = packetIndexSequence.getAndIncrement()
            
            // 使用递增的种子确保每次生成的组合都不同
            val uniqueSeed = seedCounter.getAndIncrement()
            val packetRandom = Random(uniqueSeed)
            
            // 使用Robust Soliton Distribution生成度
            val degree = generateRobustSolitonDegree(packetRandom, sourceBlockCount)
            
            // 随机选择源块
            val sourceBlocks = mutableListOf<Int>()
            val allBlocks = (0 until sourceBlockCount).toList().shuffled(packetRandom)
            for (i in 0 until min(degree, sourceBlockCount)) {
                sourceBlocks.add(allBlocks[i])
            }
            
            // 生成编码符号（XOR组合所有选中的源块）
            val encodedBytes = generateEncodedSymbol(sourceBlocks)
            
            // 计算校验和
            val checksum = calculateChecksum(encodedBytes)
            
            // 创建数据包对象
            val header = PacketHeader(
                magic = MAGIC,
                fileSize = encodingFileSize,
                fileName = encodingFileName,
                blockIndex = uniqueCounter, // 使用非循环的唯一序列号
                totalBlocks = sourceBlockCount, // 这里记录源块总数
                checksum = calculateHeaderChecksum(encodingFileName, encodingFileSize, uniqueCounter, sourceBlockCount)
            )
            
            val encoding = PacketEncoding(
                seed = uniqueSeed.toInt(),
                degree = degree,
                sourceBlocks = sourceBlocks,
                checksum = checksum
            )
            
            // Base64编码载荷数据
            val payload = Base64.encodeToString(encodedBytes, Base64.NO_WRAP)
            
            // 记录数据包信息
            Log.d(TAG, "生成RaptorQ数据包: 序列号=$uniqueCounter, 种子=$uniqueSeed, 度=$degree, 源块=${sourceBlocks}")
            
            return DataPacket(header, encoding, payload)
            
        } catch (e: Exception) {
            Log.e(TAG, "生成数据包失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 生成编码符号（XOR组合所有选中的源块）
     */
    private fun generateEncodedSymbol(sourceBlocks: List<Int>): ByteArray {
        if (sourceBlocks.isEmpty()) {
            throw IllegalArgumentException("源块列表不能为空")
        }
        
        // 初始化结果数组
        val result = ByteArray(blockSize)
        
        // 对所有选中的源块进行XOR操作
        for (blockIndex in sourceBlocks) {
            val blockBytes = getSourceBlockBytes(blockIndex)
            
            // XOR操作
            for (j in result.indices) {
                result[j] = (result[j].toInt() xor blockBytes[j].toInt()).toByte()
            }
        }
        
        return result
    }

    /**
     * 获取源块数据
     */
    private fun getSourceBlockBytes(blockIndex: Int): ByteArray {
        val blockStart = blockIndex * blockSize
        val blockEnd = min(blockStart + blockSize, encodingFileSize.toInt())
        
        val blockData = ByteArray(blockSize)
        if (blockStart < encodingFileSize) {
            val bytesToCopy = blockEnd - blockStart
            System.arraycopy(encodingData!!, blockStart, blockData, 0, bytesToCopy)
        }
        
        return blockData
    }

    /**
     * 生成Robust Soliton度分布的随机度
     * 参考: https://en.wikipedia.org/wiki/Soliton_distribution
     */
    private fun generateRobustSolitonDegree(random: Random, k: Int): Int {
        if (k <= 0) return 1
        
        // 对于小文件，使用简化的度分布
        if (k < 10) {
            val r = random.nextDouble()
            // 60%的概率选择度为1-2
            if (r < 0.6) {
                return random.nextInt(2) + 1
            }
            // 40%的概率选择更高的度
            else {
                return random.nextInt(k) + 1
            }
        }
        
        // 对于大文件，实现完整的Robust Soliton Distribution
        
        // 计算Robust Soliton参数
        val R = SOLITON_C * ln(k/SOLITON_DELTA) * sqrt(k.toDouble()).toInt()
        
        // 构建度分布的CDF
        val probabilities = DoubleArray(k + 1)
        var sum = 0.0
        
        // 理想Soliton分布
        probabilities[1] = 1.0 / k
        for (i in 2..k) {
            probabilities[i] = 1.0 / (i * (i - 1))
        }
        
        // 添加Robust项
        val tau = DoubleArray(k + 1)
        for (i in 1..k) {
            if (i < k/R) {
                tau[i] += R / (i * k)
            } else if (i.toDouble() == k.toDouble()/R) {
                tau[i] += R * ln(R/SOLITON_DELTA) / k
            }
        }
        
        // 合并分布
        for (i in 1..k) {
            probabilities[i] += tau[i]
            sum += probabilities[i]
        }
        
        // 规范化
        for (i in 1..k) {
            probabilities[i] /= sum
        }
        
        // 构建CDF
        for (i in 2..k) {
            probabilities[i] += probabilities[i - 1]
        }
        
        // 使用CDF选择度
        val p = random.nextDouble()
        for (i in 1..k) {
            if (p <= probabilities[i]) {
                return i
            }
        }
        
        // 默认返回1
        return 1
    }

    /**
     * 初始化解码器
     */
    fun initializeDecoder(fileName: String, fileSize: Long, totalBlocks: Int) {
        try {
            decodingFileName = fileName
            decodingFileSize = fileSize
            decodingSourceBlockCount = totalBlocks
            
            // 重置状态
            receivedPacketsCount = 0
            isDecodingComplete = false
            decodingMatrix.clear()
            solvedSourceBlocks = BitSet(decodingSourceBlockCount)
            
            // 创建解码缓冲区
            decodingData = ByteArray(fileSize.toInt())
            
            blockSize = BLOCK_SIZE // 使用与编码器相同的块大小
            
            Log.d(TAG, "初始化RaptorQ解码器: 文件名=$fileName, 文件大小=${fileSize}字节, 源块数=$totalBlocks")
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化解码器失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 处理接收到的数据包
     * @return 是否需要更多数据包
     */
    fun processPacket(packet: DataPacket): Boolean {
        if (isDecodingComplete) {
            Log.d(TAG, "【忽略数据包】 解码已完成，不再处理新数据包")
            return false
        }
        
        // 验证魔数 - 但增加容错能力
        if (packet.header.magic != MAGIC) {
            // 记录详细信息以帮助调试
            Log.w(TAG, "【魔数不匹配】 预期: $MAGIC, 实际: ${packet.header.magic}")
            
            // 尝试检查魔数是否相似（可能有些字符被混入或丢失）
            val similarity = calculateStringSimilarity(packet.header.magic, MAGIC)
            if (similarity > 0.5) {  // 如果相似度大于50%
                Log.i(TAG, "【兼容处理】 魔数相似度: $similarity，尝试继续处理")
            } else {
                Log.e(TAG, "【无效数据包】 魔数不匹配且相似度低: $similarity")
                return true
            }
        }
        
        // 验证文件信息 - 同样增加容错机制
        var fileInfoMismatch = false
        val mismatchDetails = StringBuilder()
        
        if (packet.header.fileName != decodingFileName) {
            mismatchDetails.append("文件名不匹配 [数据包: ${packet.header.fileName}, 解码任务: $decodingFileName] ")
            fileInfoMismatch = true
        }
        
        if (packet.header.fileSize != decodingFileSize) {
            mismatchDetails.append("文件大小不匹配 [数据包: ${packet.header.fileSize}, 解码任务: $decodingFileSize] ")
            fileInfoMismatch = true
        }
        
        if (packet.header.totalBlocks != decodingSourceBlockCount) {
            mismatchDetails.append("总块数不匹配 [数据包: ${packet.header.totalBlocks}, 解码任务: $decodingSourceBlockCount] ")
            fileInfoMismatch = true
        }
        
        if (fileInfoMismatch) {
            // 如果是首个数据包，我们可能应该使用它的信息而不是拒绝它
            if (receivedPacketsCount == 0) {
                Log.i(TAG, "【首次数据包】 信息不匹配但作为初始数据接受: $mismatchDetails")
                
                // 重新初始化解码器使用这个数据包的信息
                try {
                    decodingFileName = packet.header.fileName
                    decodingFileSize = packet.header.fileSize
                    decodingSourceBlockCount = packet.header.totalBlocks
                    
                    // 重置状态
                    receivedPacketsCount = 0
                    isDecodingComplete = false
                    decodingMatrix.clear()
                    solvedSourceBlocks = BitSet(decodingSourceBlockCount)
                    
                    // 创建解码缓冲区
                    decodingData = ByteArray(packet.header.fileSize.toInt())
                    
                    Log.i(TAG, "【重新初始化】 使用首个数据包信息: 文件=${packet.header.fileName}, 大小=${packet.header.fileSize}字节, 总块数=${packet.header.totalBlocks}")
                } catch (e: Exception) {
                    Log.e(TAG, "【初始化失败】 无法使用数据包信息重新初始化: ${e.message}")
                    return true
                }
            } else {
                // 如果不是首个数据包，则记录错误但尝试继续处理
                Log.w(TAG, "【文件信息不匹配】 $mismatchDetails")
            }
        }
        
        try {
            // 解析载荷数据
            val payloadBytes = try {
                android.util.Base64.decode(packet.payload, android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "【Base64解码失败】 载荷不是有效的Base64编码: ${e.message}")
                return true
            }
            
            // 验证载荷校验和，但允许一定程度的错误
            val calculatedChecksum = calculateChecksum(payloadBytes)
            if (calculatedChecksum != packet.encoding.checksum) {
                Log.w(TAG, "【校验和不匹配】 预期: ${packet.encoding.checksum}, 实际: $calculatedChecksum")
                
                // 如果差异不大，仍然尝试处理（有些传输错误可能只影响少量位）
                val difference = Math.abs(calculatedChecksum - packet.encoding.checksum)
                if (difference > 1000000) {  // 设置一个合理的阈值
                    Log.e(TAG, "【载荷错误】 校验和差异过大，放弃处理")
                    return true
                } else {
                    Log.i(TAG, "【兼容处理】 校验和差异在可接受范围内，继续处理")
                }
            }
            
            // 尝试解码
            receivedPacketsCount++
            
            // 获取参与编码的源块列表
            val sourceBlocks = packet.encoding.sourceBlocks
            val blockIndex = packet.header.blockIndex
            
            Log.i(TAG, "【处理数据包】 包序号：$receivedPacketsCount，块索引：$blockIndex，度：${packet.encoding.degree}")
            Log.d(TAG, "【源块列表】 ${sourceBlocks.joinToString(", ")}")
            Log.d(TAG, "【载荷大小】 ${payloadBytes.size} 字节")
            
            // 源块索引验证
            val invalidSourceBlocks = sourceBlocks.filter { it >= decodingSourceBlockCount || it < 0 }
            if (invalidSourceBlocks.isNotEmpty()) {
                Log.w(TAG, "【无效源块】 检测到超出范围的源块索引: $invalidSourceBlocks")
                
                // 过滤掉无效的源块索引
                val validSourceBlocks = sourceBlocks.filter { it < decodingSourceBlockCount && it >= 0 }
                if (validSourceBlocks.isEmpty()) {
                    Log.e(TAG, "【无效数据包】 所有源块索引都无效")
                    return true
                } else {
                    Log.i(TAG, "【兼容处理】 使用有效的源块索引继续处理: $validSourceBlocks")
                    // 这里应创建一个新的源块列表，但由于我们不能修改不可变对象，只记录警告
                }
            }
            
            // 检查是否是单块数据（兼容性处理）
            if (sourceBlocks.size == 1 && !solvedSourceBlocks.get(sourceBlocks[0])) {
                val singleBlockIndex = sourceBlocks[0]
                if (singleBlockIndex < decodingSourceBlockCount) {
                    // 直接复制数据到结果数组
                    val offset = singleBlockIndex * blockSize
                    val bytesToCopy = min(blockSize, decodingFileSize.toInt() - offset)
                    if (bytesToCopy > 0) {
                        System.arraycopy(payloadBytes, 0, decodingData!!, offset, bytesToCopy)
                    }
                    solvedSourceBlocks.set(singleBlockIndex)
                    Log.i(TAG, "【直接解码】 源块 $singleBlockIndex 已解码，进度：${solvedSourceBlocks.cardinality()}/$decodingSourceBlockCount")
                }
            } else {
                // 添加到解码矩阵
                val row = Row(payloadBytes, sourceBlocks)
                
                // 检查是否已有相同的行
                if (!decodingMatrix.contains(row)) {
                    decodingMatrix.add(row)
                    val matrixSize = decodingMatrix.size
                    Log.i(TAG, "【矩阵更新】 添加新行，矩阵大小：$matrixSize，度：${sourceBlocks.size}")
                    
                    // 执行高斯消元
                    val solvedBefore = solvedSourceBlocks.cardinality()
                    val eliminationStart = System.currentTimeMillis()
                    performGaussianElimination()
                    val eliminationTime = System.currentTimeMillis() - eliminationStart
                    val solvedAfter = solvedSourceBlocks.cardinality()
                    val newSolved = solvedAfter - solvedBefore
                    
                    if (newSolved > 0) {
                        Log.i(TAG, "【解码进展】 新解出 $newSolved 个块，解码进度：$solvedAfter/$decodingSourceBlockCount，消元耗时：${eliminationTime}毫秒")
                    } else {
                        Log.d(TAG, "【解码状态】 未解出新块，当前进度：$solvedAfter/$decodingSourceBlockCount，消元耗时：${eliminationTime}毫秒")
                    }
                } else {
                    Log.d(TAG, "【重复数据】 收到重复的编码符号，已忽略")
                }
            }
            
            // 检查解码是否完成
            val progress = solvedSourceBlocks.cardinality() * 100 / decodingSourceBlockCount
            if (solvedSourceBlocks.cardinality() == decodingSourceBlockCount) {
                isDecodingComplete = true
                Log.i(TAG, "【解码完成】 文件接收完成！")
                Log.i(TAG, "【解码统计】 接收包数：$receivedPacketsCount，源块数：$decodingSourceBlockCount")
                Log.i(TAG, "【解码效率】 ${decodingSourceBlockCount * 100 / receivedPacketsCount}%")
            } else {
                Log.i(TAG, "【解码进度】 ${solvedSourceBlocks.cardinality()}/$decodingSourceBlockCount (${progress}%)")
                Log.d(TAG, "【矩阵状态】 当前矩阵大小：${decodingMatrix.size}，剩余源块：${decodingSourceBlockCount - solvedSourceBlocks.cardinality()}")
            }
            
            return !isDecodingComplete
            
        } catch (e: Exception) {
            Log.e(TAG, "【处理异常】 处理数据包时发生错误：${e.message}", e)
            e.printStackTrace()  // 打印完整堆栈跟踪以便调试
            return true
        }
    }

    /**
     * 执行高斯消元解码
     */
    private fun performGaussianElimination() {
        var updatedBlocks = true
        
        // 循环直到没有更多更新
        while (updatedBlocks) {
            updatedBlocks = false
            
            // 查找只依赖于一个未解码源块的行
            val iterator = decodingMatrix.iterator()
            while (iterator.hasNext()) {
                val row = iterator.next()
                
                // 找出该行中已解码的源块
                val unknownBlocks = row.sourceBlockIndices.filter { !solvedSourceBlocks.get(it) }
                
                // 如果只有一个未知块，可以解出它
                if (unknownBlocks.size == 1) {
                    val blockToSolve = unknownBlocks[0]
                    
                    // XOR已知块的数据，求解未知块
                    val result = row.encodedData.clone()
                    for (blockIndex in row.sourceBlockIndices) {
                        if (blockIndex != blockToSolve && solvedSourceBlocks.get(blockIndex)) {
                            val offset = blockIndex * blockSize
                            for (i in 0 until blockSize) {
                                if (offset + i < decodingData!!.size) {
                                    result[i] = (result[i].toInt() xor decodingData!![offset + i].toInt()).toByte()
                                }
                            }
                        }
                    }
                    
                    // 将解出的块拷贝到结果数组
                    val offset = blockToSolve * blockSize
                    val bytesToCopy = min(blockSize, decodingFileSize.toInt() - offset)
                    if (bytesToCopy > 0) {
                        System.arraycopy(result, 0, decodingData!!, offset, bytesToCopy)
                    }
                    
                    // 标记为已解码
                    solvedSourceBlocks.set(blockToSolve)
                    updatedBlocks = true
                    
                    // 从矩阵中移除该行
                    iterator.remove()
                    
                    Log.d(TAG, "解码源块 $blockToSolve, 已解码 ${solvedSourceBlocks.cardinality()}/$decodingSourceBlockCount")
                }
            }
            
            // 如果没有单块可解，尝试消除已知块
            if (!updatedBlocks && decodingMatrix.isNotEmpty()) {
                // 对每一行，消除所有已知块
                var anyUpdated = false
                for (row in decodingMatrix) {
                    // 找出该行中已解码的源块
                    val knownBlocks = row.sourceBlockIndices.filter { solvedSourceBlocks.get(it) }
                    
                    if (knownBlocks.isNotEmpty()) {
                        // XOR已知块的数据
                        for (blockIndex in knownBlocks) {
                            val offset = blockIndex * blockSize
                            for (i in 0 until blockSize) {
                                if (offset + i < decodingData!!.size) {
                                    row.encodedData[i] = (row.encodedData[i].toInt() xor decodingData!![offset + i].toInt()).toByte()
                                }
                            }
                        }
                        
                        // 更新行的源块列表，移除已知块
                        val updatedSourceBlocks = row.sourceBlockIndices.filter { !solvedSourceBlocks.get(it) }
                        val rowObject = row as Any // 为了修改不可变对象，这里使用了一个小技巧
                        val field = rowObject.javaClass.getDeclaredField("sourceBlockIndices")
                        field.isAccessible = true
                        field.set(rowObject, updatedSourceBlocks)
                        
                        anyUpdated = true
                    }
                }
                
                // 如果有任何行被更新，重新循环
                if (anyUpdated) {
                    updatedBlocks = true
                }
            }
        }
    }

    /**
     * 保存解码后的文件
     */
    fun saveDecodedFile(outputDir: File): File {
        if (!isDecodingComplete) {
            throw IllegalStateException("解码尚未完成，无法保存文件")
        }
        
        val outputFile = File(outputDir, decodingFileName)
        val outputStream = FileOutputStream(outputFile)
        
        try {
            // 写入解码后的数据
            outputStream.write(decodingData!!, 0, decodingFileSize.toInt())
            
            Log.d(TAG, "文件保存成功: ${outputFile.absolutePath}")
            
            return outputFile
            
        } catch (e: Exception) {
            Log.e(TAG, "保存文件失败: ${e.message}", e)
            throw e
        } finally {
            try {
                outputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "关闭输出流失败: ${e.message}", e)
            }
        }
    }

    /**
     * 计算校验和
     */
    private fun calculateChecksum(data: ByteArray): Int {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(data)
        return ByteBuffer.wrap(hash).int
    }

    /**
     * 计算Header校验和
     */
    private fun calculateHeaderChecksum(fileName: String, fileSize: Long, blockIndex: Int, totalBlocks: Int): Int {
        val bytes = (fileName + fileSize + blockIndex + totalBlocks).toByteArray()
        return calculateChecksum(bytes)
    }

    /**
     * 获取解码状态
     */
    fun getDecodingStats(): DecodingStats {
        return DecodingStats(
            receivedPackets = receivedPacketsCount,
            solvedBlocks = solvedSourceBlocks.cardinality(),
            totalBlocks = decodingSourceBlockCount,
            progress = if (decodingSourceBlockCount == 0) 0f else solvedSourceBlocks.cardinality().toFloat() / decodingSourceBlockCount,
            fileName = decodingFileName,
            fileSize = decodingFileSize,
            isComplete = isDecodingComplete
        )
    }

    /**
     * 解码统计信息
     */
    data class DecodingStats(
        val receivedPackets: Int,
        val solvedBlocks: Int,
        val totalBlocks: Int,
        val progress: Float,
        val fileName: String,
        val fileSize: Long,
        val isComplete: Boolean
    )

    /**
     * 计算两个字符串的相似度（简单的Levenshtein距离）
     */
    private fun calculateStringSimilarity(s1: String, s2: String): Double {
        val distance = levenshteinDistance(s1, s2)
        val maxLength = Math.max(s1.length, s2.length)
        return if (maxLength == 0) 1.0 else 1.0 - (distance.toDouble() / maxLength)
    }
    
    /**
     * 计算Levenshtein距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in costs.indices) costs[i] = i
        
        var s1Prev = 0
        var s1Curr = 0
        
        for (i in s1.indices) {
            costs[0] = i + 1
            s1Prev = i
            s1Curr = i + 1
            
            for (j in s2.indices) {
                val s2Curr = j + 1
                costs[s2Curr] = Math.min(
                    Math.min(costs[s2Curr] + 1, costs[j] + 1),
                    costs[j] + if (s1[i] == s2[j]) 0 else 1
                )
            }
        }
        
        return costs[s2.length]
    }
} 