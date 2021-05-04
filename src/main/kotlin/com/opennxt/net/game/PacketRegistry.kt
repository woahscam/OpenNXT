package com.opennxt.net.game

import com.opennxt.Constants
import com.opennxt.OpenNXT
import com.opennxt.model.files.FileChecker
import com.opennxt.net.Side
import com.opennxt.net.game.pipeline.DynamicGamePacketCodec
import com.opennxt.net.game.pipeline.GamePacketCodec
import com.opennxt.net.game.protocol.PacketFieldDeclaration
import com.opennxt.net.game.serverprot.NoTimeout
import com.opennxt.net.game.serverprot.RunClientScript
import com.opennxt.net.game.serverprot.UpdateStat
import com.opennxt.net.game.serverprot.variables.*
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import mu.KotlinLogging
import java.nio.file.Files
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaType

/*
    varp_small

  local_res20 = isaac;
  FUN_00024de0(local_68);
  l_packet = FUN_00024e60(local_68);
  l_packet->offset = l_packet->offset + 2;
  id_hibyte = l_packet->buffer[l_packet->offset + -1];
  id_lobyte = l_packet->buffer[l_packet->offset + -2];
  l_packet2 = FUN_00024e50(local_68);
  l_packet2_offset = l_packet2->offset;
  l_packet2->offset = l_packet2_offset + 1;
  value = l_packet2->buffer[l_packet2_offset];
  puVar1 = jag::game::ConfigProvider::GetVarType
                     (*(ConfigProvider **)(*(longlong *)(param1 + 8) + 0x4b8),
                      jag::game::VarDomainType::Player,(uint)CONCAT11(id_hibyte,id_lobyte));



 */

object PacketRegistry {
    private val logger = KotlinLogging.logger { }

    private val serverProtByOpcode = Int2ObjectOpenHashMap<Registration>()
    private val clientProtByOpcode = Int2ObjectOpenHashMap<Registration>()

    private val serverProtByClass = Object2ObjectOpenHashMap<KClass<*>, Registration>()
    private val clientProtByClass = Object2ObjectOpenHashMap<KClass<*>, Registration>()

    data class Registration(
        val name: String,
        val opcode: Int,
        val clazz: KClass<*>,
        val codec: GamePacketCodec<*>
    )

    fun <T : GamePacket> register(
        side: Side,
        name: String,
        clazz: KClass<T>,
        codecType: KClass<out DynamicGamePacketCodec<T>>
    ) {
        val constructor = codecType.constructors
            .first { it.parameters.size == 1 && it.parameters[0].type.javaType == Array<PacketFieldDeclaration>::class.java }

        val packetPath = Constants.PROT_PATH.resolve(FileChecker.latestBuild().toString())
            .resolve(if (side == Side.CLIENT) "clientProt" else "serverProt")
            .resolve("$name.txt")

        if (!Files.exists(packetPath)) {
            logger.warn { "Failed to load packet field declaration from ${packetPath}" }
            return
        }

        val fields =
            Files.readAllLines(packetPath).filter { it.isNotBlank() }.map { PacketFieldDeclaration.fromString(it) }
                .toTypedArray()

        val codec = constructor.call(fields)

        register(side, name, clazz, codec)
    }

    fun <T : GamePacket> register(side: Side, name: String, clazz: KClass<T>, codec: GamePacketCodec<T>?) {
        @Suppress("DEPRECATION")
        val opcode = (if (side == Side.CLIENT) OpenNXT.protocol.clientProtNames else OpenNXT.protocol.serverProtNames)
            .values[name] ?: throw NullPointerException("side $side name $name")

        if (codec == null) {
            logger.warn { "Skipping registering packet $name on side $side: Codec is null" }
            return
        }

        val registration = Registration(name, opcode, clazz, codec)

        logger.info { "Registered packet ${clazz.simpleName} on side $side to opcode $opcode with codec ${codec::class.simpleName}" }

        if (side == Side.CLIENT) {
            clientProtByClass[clazz] = registration
            clientProtByOpcode[opcode] = registration
        } else {
            serverProtByClass[clazz] = registration
            serverProtByOpcode[opcode] = registration
        }
    }

    fun reload() {
        clientProtByOpcode.clear()
        clientProtByClass.clear()
        serverProtByClass.clear()
        serverProtByOpcode.clear()

        register(Side.SERVER, "UPDATE_STAT", UpdateStat::class, UpdateStat.Codec::class)
        register(Side.SERVER, "VARP_SMALL", VarpSmall::class, VarpSmall.Codec::class)
        register(Side.SERVER, "VARP_LARGE", VarpLarge::class, VarpLarge.Codec::class)
        register(Side.SERVER, "RESET_CLIENT_VARCACHE", ResetClientVarcache::class, EmptyPacketCodec(ResetClientVarcache))
        register(Side.SERVER, "CLIENT_SETVARC_SMALL", ClientSetvarcSmall::class, ClientSetvarcSmall.Codec::class)
        register(Side.SERVER, "CLIENT_SETVARC_LARGE", ClientSetvarcLarge::class, ClientSetvarcLarge.Codec::class)
        register(Side.SERVER, "NO_TIMEOUT", NoTimeout::class, EmptyPacketCodec(NoTimeout))
        register(Side.SERVER, "RUNCLIENTSCRIPT", RunClientScript::class, RunClientScript.Codec)

        register(Side.CLIENT, "NO_TIMEOUT", NoTimeout::class, EmptyPacketCodec(NoTimeout))
    }


    fun getRegistration(side: Side, opcode: Int): Registration? {
        return if (side == Side.CLIENT) {
            clientProtByOpcode[opcode]
        } else {
            serverProtByOpcode[opcode]
        }
    }

    fun getRegistration(side: Side, clazz: KClass<*>): Registration? {
        return if (side == Side.CLIENT) {
            clientProtByClass[clazz]
        } else {
            serverProtByClass[clazz]
        }
    }
}