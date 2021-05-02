package com.opennxt.filesystem

import com.opennxt.ext.getCrc32
import com.opennxt.ext.getWhirlpool
import com.opennxt.ext.rsaEncrypt
import com.opennxt.util.Whirlpool
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer

class ChecksumTable(val entries: Array<TableEntry>) {
    data class TableEntry(val crc: Int, val version: Int, val files: Int, val size: Int, val whirlpool: ByteArray) {
        companion object {
            val EMPTY = TableEntry(0, 0, 0, 0, ByteArray(64))
        }
    }

    companion object {
        fun decode(buffer: ByteBuffer): ChecksumTable {
            return ChecksumTable(Array(buffer.get().toInt()) { id ->
                val crc = buffer.int
                val version = buffer.int
                val files = buffer.int
                val size = buffer.int
                val whirlpool = ByteArray(64)
                buffer.get(whirlpool)

                TableEntry(crc, version, files, size, whirlpool)
            })
        }

        fun create(fs: Filesystem, http: Boolean): ChecksumTable {
            val entries = Array(if (http) 41 else fs.numIndices()) { index ->
                val raw = fs.readReferenceTable(index) ?: return@Array TableEntry.EMPTY
                val table = fs.getReferenceTable(index) ?: return@Array TableEntry.EMPTY

                if ((http && index == 40) || !http) {
                    return@Array TableEntry(
                        raw.getCrc32(),
                        table.version,
                        table.highestEntry(),
                        table.archiveSize(),
                        raw.getWhirlpool()
                    )
                }

                TableEntry.EMPTY
            }

            return ChecksumTable(entries)
        }
    }

    fun encode(modulus: BigInteger, exponent: BigInteger): ByteArray {
        val bout = ByteArrayOutputStream()
        val dos = DataOutputStream(bout)

        dos.use { os ->
            os.write(entries.size)

            for(element in entries) {
                val entry = element
                os.writeInt(entry.crc)
                os.writeInt(entry.version)
                os.writeInt(entry.files)
                os.writeInt(entry.size)
                os.write(entry.whirlpool)
            }

            val all = bout.toByteArray()
            var temp = ByteBuffer.allocate(65)
            temp.put(10)
            temp.put(Whirlpool.getHash(all, 0, all.size))
            temp.flip()
            temp = temp.rsaEncrypt(modulus, exponent)

            val bytes = ByteArray(temp.limit())
            temp.get(bytes)
            os.write(bytes)

            return bout.toByteArray()
        }
    }
}