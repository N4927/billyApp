package com.example.billyapp.core

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.floor

class RotatingIdGenerator(
    private val userSecret: ByteArray,
    private val rotationSeconds: Long = Constants.ROTATION_SECONDS,
    private val salt: ByteArray = "nearby-salt-v1".encodeToByteArray(),
    private val lengthBytes: Int = 16
) {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun currentIdBytes(epochSeconds: Long = System.currentTimeMillis() / 1000): ByteArray {
        val bucket = floor(epochSeconds.toDouble() / rotationSeconds).toLong()
        val payload = ByteBuffer.allocate(userSecret.size + salt.size + Long.SIZE_BYTES).apply {
            put(userSecret); put(salt); putLong(bucket)
        }.array()
        val hash = digest.digest(payload)
        return hash.copyOfRange(0, lengthBytes)
    }

    fun currentIdUuid(epochSeconds: Long = System.currentTimeMillis() / 1000): UUID {
        val bb = ByteBuffer.wrap(currentIdBytes(epochSeconds), 0, 16)
        return UUID(bb.long, bb.long)
    }
}
