package com.josxtm.fm1ramloader

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** This is the only transport type accepted by the RAM upload path in this APK. */
internal class SimulatedRom(
    private val fragmentSize: Int = 173,
    private val fault: Fault? = null
) : BulkWire {
    internal enum class Fault { TIMEOUT_ON_WRITE, DETACH_ON_WRITE, INVALID_STATUS, DETACH_ON_JUMP, BAD_JUMP_ECHO }

    init { require(fragmentSize in 1..AppSlot.BLOCK_BYTES) }

    override val inPacketSize = 64
    private val memory = ByteArrayOutputStream()
    private var cbw: ByteArray? = null
    private var reply: ByteArray? = null
    private var block: ByteArray? = null
    private var blockOffset = 0
    private var tag = 0
    private var opcode = 0
    var jumps = 0
        private set
    val commands = mutableListOf<Int>()

    override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
        val pending = block
        if (pending != null) {
            when (fault) {
                Fault.TIMEOUT_ON_WRITE -> throw UsbProblem(UsbFailure.TIMEOUT)
                Fault.DETACH_ON_WRITE -> throw UsbProblem(UsbFailure.DISCONNECTED)
                else -> Unit
            }
            val count = minOf(length, fragmentSize, pending.size - blockOffset)
            bytes.copyInto(pending, blockOffset, offset, offset + count)
            blockOffset += count
            if (blockOffset == pending.size) {
                val wrapper = checkNotNull(cbw)
                val address = AppSlot.START + memory.size()
                if (!wrapper.copyOfRange(15, 31).contentEquals(RomProtocol.writeCdb(address, pending)))
                    throw UsbProblem(UsbFailure.POLICY)
                memory.write(RomProtocol.transform(pending))
                block = null
            }
            return count
        }

        if (cbw != null || length != 31 || le32(bytes, offset) != RomProtocol.CBW)
            throw UsbProblem(UsbFailure.POLICY)
        val command = bytes.copyOfRange(offset, offset + length)
        cbw = command
        tag = le32(command, 4)
        val size = le32(command, 8)
        val code = ((command[15].toInt() and 255) shl 8) or (command[16].toInt() and 255)
        opcode = if (RomProtocol.inquiryOnly(command)) RomProtocol.INQUIRY else code
        commands.add(opcode)
        when (opcode) {
            RomProtocol.INQUIRY -> reply = ByteArray(36).apply {
                "WL82    ".toByteArray().copyInto(this, 8)
                "UBOOT1.00       ".toByteArray().copyInto(this, 16)
            }
            RomProtocol.RAM_WRITE -> {
                val address = ByteBuffer.wrap(command, 17, 4).int.toLong() and 0xffffffffL
                if (!AppSlot.contains(address, size) || size > AppSlot.BLOCK_BYTES) throw UsbProblem(UsbFailure.POLICY)
                val wireSize = ((command[21].toInt() and 255) shl 8) or (command[22].toInt() and 255)
                if (address != AppSlot.START + memory.size() || wireSize != size ||
                    command[12].toInt() != 0 || command[13].toInt() != 0 || command[14].toInt() != 16)
                    throw UsbProblem(UsbFailure.POLICY)
                block = ByteArray(size)
                blockOffset = 0
            }
            RomProtocol.RAM_JUMP -> {
                if (!command.contentEquals(RomProtocol.wrapper(RomProtocol.jumpCdb(), tag, 16, true)) ||
                    ApprovedApp.inspect(memory.toByteArray()).approved == null)
                    throw UsbProblem(UsbFailure.POLICY)
                jumps++
                reply = ByteArray(16).apply {
                    this[0] = 0xfb.toByte()
                    this[1] = if (fault == Fault.BAD_JUMP_ECHO) 7 else 8
                }
            }
            else -> throw UsbProblem(UsbFailure.POLICY)
        }
        return length
    }

    override fun read(buffer: ByteArray): Int {
        if (cbw == null || block != null) throw UsbProblem(UsbFailure.TRANSFER)
        if (opcode == RomProtocol.RAM_JUMP && fault == Fault.DETACH_ON_JUMP)
            throw UsbProblem(UsbFailure.DISCONNECTED)
        val data = reply
        if (data != null) {
            data.copyInto(buffer)
            reply = null
            return data.size
        }
        val echoedTag = if (opcode == RomProtocol.RAM_WRITE && fault == Fault.INVALID_STATUS) tag + 1 else tag
        ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(RomProtocol.CSW).putInt(echoedTag).putInt(0).put(0)
        cbw = null
        return 13
    }
}

internal enum class SimulationResult { START_ACKNOWLEDGED, START_UNCONFIRMED }

internal class RamSimulation(private val wire: SimulatedRom = SimulatedRom()) {
    fun run(app: ApprovedApp, progress: (Int, Int) -> Unit, log: (String) -> Unit): SimulationResult {
        val bot = BotSession(wire)
        log("SIM · INQUIRY 0x12 · ${bot.inquiry()}")
        for (offset in 0 until app.size step AppSlot.BLOCK_BYTES) {
            val address = AppSlot.START + offset
            val data = RomProtocol.transform(app.chunk(offset))
            bot.command(RomProtocol.writeCdb(address, data), payload = data)
            log("SIM · 0xFB06 · 0x${hex(address)} · ${data.size} B · CSW válido")
            progress(offset + data.size, app.size)
        }
        log("SIM · 0xFB08 · salto de RAM solicitado una vez")
        try {
            val response = bot.command(RomProtocol.jumpCdb(), readSize = 16)
            if (response[0] != 0xfb.toByte() || response[1] != 8.toByte())
                throw UsbProblem(UsbFailure.RESPONSE)
        } catch (failure: UsbProblem) {
            if (failure.reason == UsbFailure.DISCONNECTED || failure.reason == UsbFailure.TIMEOUT) {
                log("SIM · salto sin confirmación · sin reintento")
                return SimulationResult.START_UNCONFIRMED
            }
            throw failure
        }
        log("SIM · 0xFB08 · eco y CSW válidos")
        return SimulationResult.START_ACKNOWLEDGED
    }
}
