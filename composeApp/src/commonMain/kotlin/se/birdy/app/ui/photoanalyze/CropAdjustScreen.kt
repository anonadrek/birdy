package se.birdy.app.ui.photoanalyze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.a11y_crop_hint
import birdy_bird_scanner.composeapp.generated.resources.crop_cancel
import birdy_bird_scanner.composeapp.generated.resources.crop_confirm
import birdy_bird_scanner.composeapp.generated.resources.crop_rotate
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.PlatformBackHandler
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.paperBackground
import kotlin.math.roundToInt

private const val MIN_CROP_SIDE_PX = 224

/** Space between the crop area and the screen edge, so the corner handles are never cut in half. */
private val CROP_EDGE_MARGIN = 12.dp

/** Dims everything that is not kept: the backdrop around the photo and the photo outside the crop. */
private val CropDim = Color.Black.copy(alpha = 0.5f)

/**
 * Beskärnings- och rotations-yta för en uppladdad bild. Crop-rektangeln hålls i
 * källbildens pixel-koordinater; gester konverteras via en ContentScale.Fit-mappning.
 *
 * Self-contained: system-back → [onCancel] hanteras här via [PlatformBackHandler] (Android
 * actual = androidx BackHandler, iOS actual = no-op) — respektive plattforms-host behöver
 * inte bolta på en egen BackHandler runt anropet.
 */
@Composable
fun CropAdjustScreen(
    image: ImageBitmap,
    onRotate: () -> Unit,
    onConfirm: (CropRect) -> Unit,
    onCancel: () -> Unit,
) {
    PlatformBackHandler(enabled = true) { onCancel() }

    // Rect nollställs när bilden byts (efter rotation), tack vare remember(image)-key.
    var rect by remember(image) {
        mutableStateOf(CropGeometry.fullRect(image.width, image.height))
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val touchPx = with(LocalDensity.current) { 32.dp.toPx() }
    val edgeMarginPx = with(LocalDensity.current) { CROP_EDGE_MARGIN.toPx() }

    Column(modifier = Modifier.fillMaxSize().paperBackground()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Backdrop behind the photo, the edge margins included (drawn before the
                    // padding below so the margins match the rest of the dimmed area).
                    .background(CropDim)
                    // A wide photo fills the width, which put the left and right corner handles
                    // on the screen edges. With gesture navigation those edges are the system back
                    // gesture: dragging a corner inward closed the crop screen instead of cropping
                    // (Release 1.3.0 Plan 3 Task 7, API 36 emulator). Keep clear of the gesture
                    // zones (zero with button navigation). The photo itself is drawn a further
                    // CROP_EDGE_MARGIN inside the canvas on every side (see fitMapping), so the
                    // handles show whole and their outer half still sits inside the touch area.
                    .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.Center,
        ) {
            val cropHint = stringResource(Res.string.a11y_crop_hint)
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = cropHint }
                        .onSizeChanged { boxSize = it }
                        .pointerInput(image, boxSize, edgeMarginPx) {
                            val fit = fitMapping(boxSize, image.width, image.height, edgeMarginPx)
                            var mode: DragMode = DragMode.None
                            detectDragGestures(
                                onDragStart = { pos ->
                                    mode = pickDragMode(rect, pos, fit, touchPx)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dxSrc = (dragAmount.x / fit.scale).roundToInt()
                                    val dySrc = (dragAmount.y / fit.scale).roundToInt()
                                    rect =
                                        when (val m = mode) {
                                            is DragMode.Corner ->
                                                applyCorner(rect, m.handle, dxSrc, dySrc, image)
                                            DragMode.Move ->
                                                CropGeometry.move(
                                                    rect,
                                                    dxSrc,
                                                    dySrc,
                                                    image.width,
                                                    image.height,
                                                )
                                            DragMode.None -> rect
                                        }
                                },
                            )
                        },
            ) {
                val canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                val fit = fitMapping(canvasSize, image.width, image.height, edgeMarginPx)
                // 1. Bilden
                drawImage(
                    image = image,
                    dstOffset = IntOffset(fit.offsetX.roundToInt(), fit.offsetY.roundToInt()),
                    dstSize = IntSize(fit.dispWidth.roundToInt(), fit.dispHeight.roundToInt()),
                )
                // 2. Crop-rektangelns skärm-koordinater
                val l = fit.offsetX + rect.left * fit.scale
                val t = fit.offsetY + rect.top * fit.scale
                val r = fit.offsetX + rect.right * fit.scale
                val b = fit.offsetY + rect.bottom * fit.scale
                // 3. Mörkad overlay över bilden utanför crop (fyra rektanglar). Ytan runt bilden
                // är redan mörkad av Boxens bakgrund (CropDim), så overlayn täcker bara bilden.
                val imgL = fit.offsetX
                val imgT = fit.offsetY
                val imgR = fit.offsetX + fit.dispWidth
                val imgB = fit.offsetY + fit.dispHeight
                drawRect(CropDim, topLeft = Offset(imgL, imgT), size = Size(imgR - imgL, t - imgT))
                drawRect(CropDim, topLeft = Offset(imgL, b), size = Size(imgR - imgL, imgB - b))
                drawRect(CropDim, topLeft = Offset(imgL, t), size = Size(l - imgL, b - t))
                drawRect(CropDim, topLeft = Offset(r, t), size = Size(imgR - r, b - t))
                // 4. Rule-of-thirds-linjer
                val third = AccentCopper.copy(alpha = 0.6f)
                val cw = (r - l) / 3f
                val ch = (b - t) / 3f
                for (i in 1..2) {
                    drawLine(third, Offset(l + cw * i, t), Offset(l + cw * i, b), strokeWidth = 1.5f)
                    drawLine(third, Offset(l, t + ch * i), Offset(r, t + ch * i), strokeWidth = 1.5f)
                }
                // 5. Crop-ram + hörnhandtag
                drawRect(AccentCopper, topLeft = Offset(l, t), size = Size(r - l, b - t), style = Stroke(width = 3f))
                val handle = 14f
                listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { c ->
                    drawCircle(AccentCopper, radius = handle, center = c)
                    drawCircle(Color.White, radius = handle, center = c, style = Stroke(width = 2f))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.crop_cancel), color = AccentCopper)
            }
            OutlinedButton(onClick = onRotate, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.crop_rotate), color = AccentCopper)
            }
            Button(
                onClick = { onConfirm(rect) },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentCopper,
                        contentColor = OffwhiteWarm,
                    ),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.crop_confirm))
            }
        }
    }
}

private data class FitMapping(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val dispWidth: Float,
    val dispHeight: Float,
)

/**
 * ContentScale.Fit of the photo into [box], keeping [inset] free on every side: a wide photo used
 * to touch the left and right edges, a tall one the top and bottom, with the corner handles half
 * outside the canvas and its touch area (Plan 3 Task 7 and its review).
 */
private fun fitMapping(
    box: IntSize,
    srcWidth: Int,
    srcHeight: Int,
    inset: Float,
): FitMapping {
    val availWidth = box.width - 2f * inset
    val availHeight = box.height - 2f * inset
    if (availWidth <= 0f || availHeight <= 0f) return FitMapping(1f, 0f, 0f, srcWidth.toFloat(), srcHeight.toFloat())
    val scale = minOf(availWidth / srcWidth, availHeight / srcHeight)
    val dispW = srcWidth * scale
    val dispH = srcHeight * scale
    return FitMapping(
        scale = scale,
        offsetX = (box.width - dispW) / 2f,
        offsetY = (box.height - dispH) / 2f,
        dispWidth = dispW,
        dispHeight = dispH,
    )
}

private sealed interface DragMode {
    data object None : DragMode

    data object Move : DragMode

    data class Corner(
        val handle: CropHandle,
    ) : DragMode
}

private fun pickDragMode(
    rect: CropRect,
    pos: Offset,
    fit: FitMapping,
    touchPx: Float,
): DragMode {
    val corners =
        mapOf(
            CropHandle.TOP_LEFT to Offset(fit.offsetX + rect.left * fit.scale, fit.offsetY + rect.top * fit.scale),
            CropHandle.TOP_RIGHT to Offset(fit.offsetX + rect.right * fit.scale, fit.offsetY + rect.top * fit.scale),
            CropHandle.BOTTOM_LEFT to Offset(fit.offsetX + rect.left * fit.scale, fit.offsetY + rect.bottom * fit.scale),
            CropHandle.BOTTOM_RIGHT to Offset(fit.offsetX + rect.right * fit.scale, fit.offsetY + rect.bottom * fit.scale),
        )
    val nearest = corners.minByOrNull { (_, c) -> (c - pos).getDistance() }
    if (nearest != null && (nearest.value - pos).getDistance() <= touchPx) {
        return DragMode.Corner(nearest.key)
    }
    val insideX = pos.x in (fit.offsetX + rect.left * fit.scale)..(fit.offsetX + rect.right * fit.scale)
    val insideY = pos.y in (fit.offsetY + rect.top * fit.scale)..(fit.offsetY + rect.bottom * fit.scale)
    return if (insideX && insideY) DragMode.Move else DragMode.None
}

private fun applyCorner(
    rect: CropRect,
    handle: CropHandle,
    dxSrc: Int,
    dySrc: Int,
    image: ImageBitmap,
): CropRect {
    val (cx, cy) =
        when (handle) {
            CropHandle.TOP_LEFT -> rect.left to rect.top
            CropHandle.TOP_RIGHT -> rect.right to rect.top
            CropHandle.BOTTOM_LEFT -> rect.left to rect.bottom
            CropHandle.BOTTOM_RIGHT -> rect.right to rect.bottom
        }
    return CropGeometry.resizeToCorner(
        rect = rect,
        handle = handle,
        x = cx + dxSrc,
        y = cy + dySrc,
        width = image.width,
        height = image.height,
        minSide = MIN_CROP_SIDE_PX,
    )
}
