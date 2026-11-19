// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.graphics.scale
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Lectura y generación de códigos QR con ZXing (libre, sin servicios de Google).
 */
object QrCodes {

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    /** Lector reutilizable (no es seguro entre hilos: uno por analizador). */
    fun newReader(): MultiFormatReader = MultiFormatReader().apply { setHints(hints) }

    /** Intenta leer un QR; prueba también con los colores invertidos (QR claros sobre fondo oscuro). */
    fun decode(reader: MultiFormatReader, source: LuminanceSource): String? {
        for (candidate in listOf(source, source.invert())) {
            try {
                return reader.decodeWithState(BinaryBitmap(HybridBinarizer(candidate))).text
            } catch (_: NotFoundException) {
            } catch (_: Exception) {
            } finally {
                reader.reset()
            }
        }
        return null
    }

    /** Lee el QR de una imagen (captura de pantalla, foto…). */
    fun decodeImage(context: Context, uri: Uri): String? {
        val bitmap = loadBitmap(context, uri) ?: return null
        val reader = newReader()
        // Las capturas grandes se leen mejor reducidas; se prueban ambos tamaños.
        val sizes = listOf(bitmap, scaled(bitmap, 1024)).distinct()
        for (b in sizes) {
            val pixels = IntArray(b.width * b.height)
            b.getPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
            decode(reader, RGBLuminanceSource(b.width, b.height, pixels))?.let { return it }
        }
        return null
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val max = maxOf(info.size.width, info.size.height)
                if (max > 2048) decoder.setTargetSampleSize(max / 2048 + 1)
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }

    private fun scaled(bitmap: Bitmap, maxSide: Int): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= maxSide) return bitmap
        val f = maxSide.toFloat() / max
        return bitmap.scale((bitmap.width * f).toInt(), (bitmap.height * f).toInt())
    }

    /** Genera un QR en blanco y negro (con margen) para mostrarlo en pantalla. */
    fun encode(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
        )
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            val offset = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[offset + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}
