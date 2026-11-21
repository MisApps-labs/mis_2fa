// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ferlagod.mis2fa.R
import com.ferlagod.mis2fa.ui.components.EmptyState
import com.ferlagod.mis2fa.ui.theme.LogoMint
import com.ferlagod.mis2fa.ui.viewmodel.ScanOutcome
import com.ferlagod.mis2fa.util.QrCodes
import com.google.zxing.PlanarYUVLuminanceSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Escáner de códigos QR.
 *
 * @param onScanned procesa el texto leído y devuelve qué ha pasado.
 * @param onFinished se llama cuando ya no hace falta seguir escaneando (con el resultado a mostrar).
 */
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onScanned: suspend (String) -> ScanOutcome,
    onFinished: (ScanOutcome) -> Unit,
    onPickImage: () -> Unit,
    onManual: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionAsked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionAsked = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    // Lotes de Google Authenticator ya leídos (para pedir el siguiente QR).
    var batchesDone by remember { mutableStateOf(emptySet<Int>()) }
    var batchId by remember { mutableStateOf<Long?>(null) }
    var lastText by remember { mutableStateOf<String?>(null) }
    val notOtpText = stringResource(R.string.scan_not_otp)
    val batchTemplate = stringResource(R.string.scan_batch_progress)

    val onDetected: (String) -> Unit = handler@{ text ->
        if (processing || text == lastText) return@handler
        lastText = text
        processing = true
        scope.launch {
            val outcome = onScanned(text)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            when (outcome) {
                is ScanOutcome.Batch -> {
                    // QR de otra exportación (p. ej. se empezó de nuevo en el otro móvil): se cuenta desde cero.
                    if (batchId != outcome.id) {
                        batchId = outcome.id
                        batchesDone = emptySet()
                    }
                    val done = batchesDone + outcome.index
                    batchesDone = done
                    if (done.size >= outcome.size) {
                        onFinished(outcome)
                    } else {
                        message = batchTemplate.format(done.size, outcome.size)
                    }
                }
                ScanOutcome.NotOtp -> {
                    message = notOtpText
                    delay(1500)
                    lastText = null
                }
                else -> onFinished(outcome)
            }
            processing = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(onDetected = onDetected)
            ViewfinderOverlay()
        } else if (permissionAsked) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    EmptyState(
                        icon = Icons.Rounded.NoPhotography,
                        title = stringResource(R.string.camera_permission_title),
                        message = stringResource(R.string.camera_permission_message)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.open_app_settings))
                    }
                }
            }
        }

        // Barra superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = if (hasPermission) Color.White else MaterialTheme.colorScheme.primary
                )
            }
            Text(
                stringResource(R.string.scan_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (hasPermission) Color.White else MaterialTheme.colorScheme.onBackground
            )
        }

        // Ayuda y alternativas
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasPermission) {
                Text(
                    text = message ?: stringResource(R.string.scan_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onPickImage, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_from_image_short))
                }
                FilledTonalButton(onClick = onManual, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_manual_short))
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDetected by rememberUpdatedState(onDetected)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val reader = QrCodes.newReader()
                analysis.setAnalyzer(executor) { image ->
                    val text = decode(reader, image)
                    if (text != null) mainExecutor.execute { currentOnDetected(text) }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Log.e("ScanScreen", "No se pudo abrir la cámara", e)
            }
        }, mainExecutor)
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Lee la luminancia (plano Y) del fotograma y busca un QR. */
private fun decode(reader: com.google.zxing.MultiFormatReader, image: ImageProxy): String? = image.use {
    try {
        val plane = it.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = it.width
        val height = it.height
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val source = PlanarYUVLuminanceSource(data, rowStride, height, 0, 0, width, height, false)
        QrCodes.decode(reader, source)
    } catch (_: Exception) {
        null
    }
}

/** Oscurece todo menos un recuadro central con esquinas en el color menta del logo. */
@Composable
private fun ViewfinderOverlay() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val side = minOf(size.width, size.height) * 0.68f
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2.3f)
        val radius = CornerRadius(28.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, Size(side, side), radius, blendMode = BlendMode.Clear)
        drawRoundRect(LogoMint, topLeft, Size(side, side), radius, style = Stroke(4.dp.toPx()))
    }
}
