package com.pudding233.qrtransfer.model

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException

/**
 * 数据包的头部信息
 */
data class PacketHeader(
    val magic: String,
    val fileSize: Long,
    val fileName: String,
    val totalBlocks: Int,
    val blockIndex: Int,
    val checksum: Int,
    val reserved: Int = 0
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("magic", magic)
        json.put("fileSize", fileSize)
        json.put("fileName", fileName)
        json.put("totalBlocks", totalBlocks)
        json.put("blockIndex", blockIndex)
        json.put("checksum", checksum)
        json.put("reserved", reserved)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): PacketHeader {
            try {
                // 验证所有必要字段
                val requiredFields = listOf("magic", "fileSize", "fileName", "totalBlocks", "blockIndex", "checksum")
                for (field in requiredFields) {
                    if (!json.has(field)) {
                        throw JSONException("缺少必要字段: $field")
                    }
                }
                
                return PacketHeader(
                    magic = json.optString("magic", ""),
                    fileSize = json.optLong("fileSize", 0),
                    fileName = json.optString("fileName", ""),
                    totalBlocks = json.optInt("totalBlocks", 0),
                    blockIndex = json.optInt("blockIndex", 0),
                    checksum = json.optInt("checksum", 0),
                    reserved = if (json.has("reserved")) json.optInt("reserved", 0) else 0
                )
            } catch (e: JSONException) {
                throw e
            } catch (e: Exception) {
                throw JSONException("解析PacketHeader失败: ${e.message}")
            }
        }
    }
}

/**
 * 数据包的编码信息
 */
data class PacketEncoding(
    val seed: Int,
    val degree: Int,
    val sourceBlocks: List<Int>,
    val checksum: Int
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("seed", seed)
        json.put("degree", degree)
        
        val sourceBlocksArray = JSONArray()
        sourceBlocks.forEach { sourceBlocksArray.put(it) }
        json.put("sourceBlocks", sourceBlocksArray)
        
        json.put("checksum", checksum)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): PacketEncoding {
            try {
                // 验证所有必要字段
                val requiredFields = listOf("seed", "degree", "sourceBlocks", "checksum")
                for (field in requiredFields) {
                    if (!json.has(field)) {
                        throw JSONException("缺少必要字段: $field")
                    }
                }
                
                // 处理sourceBlocks数组
                val sourceBlocksArray = json.optJSONArray("sourceBlocks")
                    ?: throw JSONException("sourceBlocks字段不是有效的JSON数组")
                
                val sourceBlocks = mutableListOf<Int>()
                for (i in 0 until sourceBlocksArray.length()) {
                    try {
                        sourceBlocks.add(sourceBlocksArray.getInt(i))
                    } catch (e: Exception) {
                        throw JSONException("sourceBlocks数组中包含无效的整数值")
                    }
                }
                
                return PacketEncoding(
                    seed = json.optInt("seed", 0),
                    degree = json.optInt("degree", 0),
                    sourceBlocks = sourceBlocks,
                    checksum = json.optInt("checksum", 0)
                )
            } catch (e: JSONException) {
                throw e
            } catch (e: Exception) {
                throw JSONException("解析PacketEncoding失败: ${e.message}")
            }
        }
    }
}

/**
 * 完整的数据包
 */
data class DataPacket(
    val header: PacketHeader,
    val encoding: PacketEncoding,
    val payload: String
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("header", header.toJson())
        json.put("encoding", encoding.toJson())
        json.put("payload", payload)
        return json
    }

    override fun toString(): String {
        return toJson().toString()
    }

    companion object {
        fun fromJson(jsonString: String): DataPacket {
            try {
                val json = JSONObject(jsonString)
                
                // 验证必要的字段存在
                if (!json.has("header") || !json.has("encoding") || !json.has("payload")) {
                    throw JSONException("JSON数据缺少必要字段(header/encoding/payload)")
                }
                
                // 获取字段并验证类型
                val headerObj = json.optJSONObject("header") 
                    ?: throw JSONException("header字段不是有效的JSON对象")
                    
                val encodingObj = json.optJSONObject("encoding")
                    ?: throw JSONException("encoding字段不是有效的JSON对象")
                    
                val payload = json.optString("payload")
                if (payload.isNullOrEmpty()) {
                    throw JSONException("payload字段为空或不是字符串")
                }
                
                return DataPacket(
                    header = PacketHeader.fromJson(headerObj),
                    encoding = PacketEncoding.fromJson(encodingObj),
                    payload = payload
                )
            } catch (e: JSONException) {
                throw e
            } catch (e: Exception) {
                throw JSONException("解析DataPacket失败: ${e.message}")
            }
        }
    }
} 