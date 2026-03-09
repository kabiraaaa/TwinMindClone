package com.example.twinmindclone.service

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {

    fun writePcmToWav(
        pcmData: ByteArray,
        outputFile: File,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ) {
        val dataSize = pcmData.size
        val totalSize = dataSize + 44

        FileOutputStream(outputFile).use { fos ->
            fos.write("RIFF".toByteArray())
            fos.write(intToBytes(totalSize - 8))
            fos.write("WAVE".toByteArray())

            fos.write("fmt ".toByteArray())
            fos.write(intToBytes(16))
            fos.write(shortToBytes(1))
            fos.write(shortToBytes(channels.toShort()))
            fos.write(intToBytes(sampleRate))
            fos.write(intToBytes(sampleRate * channels * bitsPerSample / 8))
            fos.write(shortToBytes((channels * bitsPerSample / 8).toShort()))
            fos.write(shortToBytes(bitsPerSample.toShort()))

            fos.write("data".toByteArray())
            fos.write(intToBytes(dataSize))
            fos.write(pcmData)
        }
    }

    private fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToBytes(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }
}
