package com.opennxt.net.login

import com.opennxt.OpenNXT
import com.opennxt.ext.readString
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.math.BigInteger

sealed class LoginRSAHeader(val seeds: IntArray, val uniqueId: Long) {
    class Fresh(
        seeds: IntArray,
        uniqueId: Long,
        val weirdThingId: Int, // name-related i think ?
        val weirdThingValue: Int, // name-related i think ?
        val thatBoolean: Boolean, // some boolean
        val password: String,
        val someLong: Long, // seems to be sent by server in another login stage, no clue what it is
        val randClient: Long // random value generated from client
    ) : LoginRSAHeader(seeds, uniqueId)

    class Reconnecting(seeds: IntArray, uniqueId: Long, val oldSeeds: IntArray) : LoginRSAHeader(seeds, uniqueId)

    companion object {
        fun ByteBuf.readLoginHeader(type: LoginType, exponent: BigInteger, modulus: BigInteger): LoginRSAHeader {
            val reconnecting = type == LoginType.GAME && readUnsignedByte().toInt() == 1

            // read rsa block
            val size = readUnsignedShort()
            val raw = ByteArray(size)
            readBytes(raw)

            // decrypt + validate block
            val block = Unpooled.wrappedBuffer(
                BigInteger(raw).modPow(exponent, modulus).toByteArray()
            )
            if (block.readUnsignedByte().toInt() != 10)
                throw IllegalStateException("rsa magic != 10")

            val seeds = IntArray(4) { block.readInt() }
            val uniqueId = block.readLong()

            if (reconnecting) {
                return Reconnecting(seeds, uniqueId, IntArray(4) { block.readInt() })
            }

            val thingId = block.readUnsignedByte().toInt()
            val thingValue = when (thingId) { // could read int but if nxt doesnt bzero packet you get invalid values
                0, 1 -> {
                    val value = block.readUnsignedMedium()
                    block.skipBytes(1)
                    value
                }
                2 -> block.readInt()
                3 -> {
                    block.skipBytes(4)
                    0
                }
                else -> throw IllegalStateException("got thingId $thingId")
            }

            val someBool = block.readUnsignedByte().toInt() == 1
            val password = block.readString()
            val someLong = block.readLong()
            val randClient = block.readLong()

            return Fresh(seeds, uniqueId, thingId, thingValue, someBool, password, someLong, randClient)
        }

        fun ByteBuf.writeLoginHeader(
            type: LoginType,
            header: LoginRSAHeader,
            exponent: BigInteger,
            modulus: BigInteger
        ) {
            TODO("Write RSA header")
        }
    }
}