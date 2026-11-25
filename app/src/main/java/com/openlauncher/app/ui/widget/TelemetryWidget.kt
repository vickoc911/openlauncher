package com.openlauncher.app.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.util.LocationData
import kotlin.math.abs
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Brush
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
// El error de "drawable" se solucionará automáticamente al importar tu clase R correcta:
import com.openlauncher.app.R

@Composable
fun TelemetryWidget(
    location: LocationData?,
    bearing: Float,
    accent: Color,
    isDayMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    // ── CONFIGURACIÓN DE PALETA ADAPTATIVA ─────────────────────────────────
    val contentColor = if (isDayMode) Color(0xFF222222) else MaterialTheme.colorScheme.onBackground
    val subColor     = if (isDayMode) Color(0xFF777777) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

    // Color de las marcas graduadas grandes y pequeñas
   // val tickColorMain = if (isDayMode) Color(0xFF111111) else Color(0xFFE0E0E0)
    val tickColorMain = if (isDayMode) Color(0xFF555555) else Color(0xFF111111)
    val tickColorSub  = if (isDayMode) Color(0xFF555555) else Color(0xFF666666)

    // Color del texto de los puntos cardinales y grados en formato Android nativo
    val nativeTextColor = if (isDayMode) android.graphics.Color.parseColor("#222222") else android.graphics.Color.WHITE

    Box(
        modifier = modifier
        .paint(
            painter = if (isDayMode) painterResource(id = R.drawable.mapa_viejo_white) else painterResource(id = R.drawable.mapa_viejo),
               contentScale = ContentScale.Crop // Ajusta la imagen para que llene el Box
        )
        .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // ── COMPASS RING (Centro absoluto) ───────────────────────────────────
        BoxWithConstraints(
            modifier         = Modifier.fillMaxSize(),
                           contentAlignment = Alignment.Center
        ) {
            val minDim = minOf(maxWidth, maxHeight)
            val radius = minDim * 0.55f

            val density = LocalDensity.current
            val radiusPx = with(density) { radius.toPx() }

            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = -bearing },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {

                        val cx = size.width / 2f
                        val cy = size.height / 2f

                        val outerRadius = radiusPx
                        val ringWidth = 6.dp.toPx()
                        val innerRadius = outerRadius - ringWidth

                        val ringColors = if (isDayMode) {
                            listOf(Color(0xFFCECECE), Color(0xFF8E8E8E)) // Tonos claros (Modo Día)
                        } else {
                            listOf(Color(0xFFCECECE), Color(0xFF8E8E8E)) // Tonos oscuros metálicos
                        }

                        // Aro exterior principal
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = ringColors,
                                start = Offset(cx - outerRadius, cy - outerRadius), end = Offset(cx + outerRadius, cy + outerRadius)
                            ),
                            radius = outerRadius,
                            center = Offset(cx, cy),
                                   style = Stroke(ringWidth)
                        )

                        // Borde exterior fino
                        drawCircle(
                            color = tickColorSub,
                                   radius = outerRadius + ringWidth / 2f,
                                   center = Offset(cx, cy),
                                   style = Stroke(1.dp.toPx())
                        )

                        // Borde interior fino
                        drawCircle(
                            color = tickColorSub,
                                   radius = innerRadius,
                                   center = Offset(cx, cy),
                                   style = Stroke(1.dp.toPx())
                        )

                        // Marcas grandes (cada 30 grados)
                        for (deg in 0 until 360 step 30) {
                            val rad = Math.toRadians((deg - 90).toDouble())
                            val outer = outerRadius + ringWidth / 2f
                            val inner = innerRadius + 1.dp.toPx()

                            drawLine(
                                color = tickColorMain,
                                start = Offset(
                                    cx + cos(rad).toFloat() * inner,
                                               cy + sin(rad).toFloat() * inner
                                ),
                                end = Offset(
                                    cx + cos(rad).toFloat() * outer,
                                             cy + sin(rad).toFloat() * outer
                                ),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }

                        // Marcas pequeñas (cada 5 grados)
                        for (deg in 0 until 360 step 5) {
                            if (deg % 30 == 0) continue

                                val rad = Math.toRadians((deg - 90).toDouble())
                                val startRadius = innerRadius - 1.dp.toPx()
                                val endRadius = innerRadius - 4.dp.toPx()

                                drawLine(
                                    color = tickColorSub,
                                    start = Offset(
                                        cx + cos(rad).toFloat() * startRadius,
                                                   cy + sin(rad).toFloat() * startRadius
                                    ),
                                    end = Offset(
                                        cx + cos(rad).toFloat() * endRadius,
                                                 cy + sin(rad).toFloat() * endRadius
                                    ),
                                    strokeWidth = 1.dp.toPx()
                                )
                        }

                        // Pintura nativa para textos internos
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = nativeTextColor
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }

                        val textRadius = innerRadius + ringWidth * 0.45f

                        for (deg in 0 until 360 step 30) {
                            val rad = Math.toRadians((deg - 90).toDouble())
                            val tx = cx + cos(rad).toFloat() * textRadius
                            val ty = cy + sin(rad).toFloat() * textRadius

                            drawContext.canvas.nativeCanvas.save()
                            drawContext.canvas.nativeCanvas.rotate(deg.toFloat(), tx, ty)

                            when (deg) {
                                0 -> {
                                    paint.textSize = 14.dp.toPx()
                                    drawContext.canvas.nativeCanvas.drawText("N", tx, ty + paint.textSize * 1.60f, paint)
                                }
                                90 -> {
                                    paint.textSize = 14.dp.toPx()
                                    drawContext.canvas.nativeCanvas.drawText("E", tx, ty + paint.textSize * 1.60f, paint)
                                }
                                180 -> {
                                    paint.textSize = 14.dp.toPx()
                                    drawContext.canvas.nativeCanvas.drawText("S", tx, ty + paint.textSize * 1.60f, paint)
                                }
                                270 -> {
                                    paint.textSize = 14.dp.toPx()
                                    drawContext.canvas.nativeCanvas.drawText("W", tx, ty + paint.textSize * 1.60f, paint)
                                }
                                else -> {
                                    paint.textSize = 6.dp.toPx()
                                    drawContext.canvas.nativeCanvas.drawText(deg.toString(), tx, ty + paint.textSize * 1.10f, paint)
                                }
                            }
                            drawContext.canvas.nativeCanvas.restore()
                        }

                        // Corona interior fina decorativa
                        drawCircle(
                            color = tickColorSub,
                                   radius = innerRadius - 5.dp.toPx(),
                                   center = Offset(cx, cy),
                                   style = Stroke(1.dp.toPx())
                        )
                    }
                }
            }

            // AGUJA CENTRAL 3D
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                val needleLength = radiusPx * 0.58f
                val needleWidth = needleLength * 0.11f

                // ─── AGUJA NORTE (ROJO 3D) ───────────────────────────────────────────
                // Norte Lado Izquierdo (Luz)
                drawPath(
                    path = Path().apply {
                        moveTo(cx, cy - needleLength)
                        lineTo(cx, cy)
                        lineTo(cx - needleWidth, cy)
                        close()
                    },
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF5252), Color(0xFFFF1744)),
                                                 start = Offset(cx - needleWidth, cy - needleLength),
                                                 end = Offset(cx, cy)
                    )
                )

                // Norte Lado Derecho (Sombra)
                drawPath(
                    path = Path().apply {
                        moveTo(cx, cy - needleLength)
                        lineTo(cx + needleWidth, cy)
                        lineTo(cx, cy)
                        close()
                    },
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFD50000), Color(0xFF9B0000)),
                                                 start = Offset(cx, cy - needleLength),
                                                 end = Offset(cx + needleWidth, cy)
                    )
                )

                // ─── AGUJA SUR (Ajustada para que resalte en fondos claros) ───────
                val surLightColors = if (isDayMode) {
                    listOf(Color(0xFFBFBFBF), Color(0xFFB0BEC5)) // Plata claro bien definido
                } else {
                    listOf(Color(0xFFFFFFFF), Color(0xFFDCDCDC))
                }

                val surDarkColors = if (isDayMode) {
                    listOf(Color(0xFF90A4AE), Color(0xFF546E7A)) // Gris sombra oscuro para contraste radical
                } else {
                    listOf(Color(0xFFB8B8B8), Color(0xFF757575))
                }

                // Sur Lado Izquierdo (Luz)
                drawPath(
                    path = Path().apply {
                        moveTo(cx, cy + needleLength)
                        lineTo(cx - needleWidth, cy)
                        lineTo(cx, cy)
                        close()
                    },
                    brush = Brush.linearGradient(
                        colors = surLightColors,
                        start = Offset(cx - needleWidth, cy),
                                                 end = Offset(cx, cy + needleLength)
                    )
                )

                // Sur Lado Derecho (Sombra)
                drawPath(
                    path = Path().apply {
                        moveTo(cx, cy + needleLength)
                        lineTo(cx, cy)
                        lineTo(cx + needleWidth, cy)
                        close()
                    },
                    brush = Brush.linearGradient(
                        colors = surDarkColors,
                        start = Offset(cx, cy),
                                                 end = Offset(cx + needleWidth, cy + needleLength)
                    )
                )

                // ─── BOTÓN CENTRAL DEL EJE ───────────────────
                val centerButtonRadius = 7.dp.toPx()
                val centerPinRadius = 5.dp.toPx()

                // Sombra/Borde exterior: En modo día lo hacemos bien oscuro (0xFF424242) para recortar la figura del fondo
                drawCircle(
                    color = if (isDayMode) Color(0xFF424242) else Color(0xFF212121),
                           radius = centerButtonRadius,
                           center = Offset(cx, cy),
                           style = Stroke(1.2.dp.toPx()) // Un grosor ligeramente mayor para definición limpia
                )

                // Esfera del eje con degradado radial corregido para modo día
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDayMode) {
                            // Mantenemos el punto blanco central de brillo, pero oscurecemos los extremos
                            listOf(Color(0xFFFFFFFF), Color(0xFFBDBDBD), Color(0xFF757575))
                        } else {
                            listOf(Color(0xFFFFFFFF), Color(0xFFCFCFCF), Color(0xFF616161))
                        },
                        center = Offset(cx - 1.5.dp.toPx(), cy - 1.5.dp.toPx()),
                                                 radius = centerPinRadius
                    ),
                    radius = centerPinRadius,
                    center = Offset(cx, cy)
                )
            }
        }

        // ── LATITUD / LONGITUD (Bloque inferior) ──────────────────────────────
        Row(
            modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text          = "LATITUDE",
                     style         = MaterialTheme.typography.labelSmall,
                     color         = subColor,
                     letterSpacing = 1.sp,
                     fontSize      = 7.sp
                )
                Text(
                    text     = if (location != null) formatLat(location.latitude) else "—",
                     color    = contentColor,
                     fontSize = 11.sp
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                   horizontalAlignment = Alignment.End
            ) {
                Text(
                    text          = "LONGITUDE",
                     style         = MaterialTheme.typography.labelSmall,
                     color         = subColor,
                     letterSpacing = 1.sp,
                     fontSize      = 7.sp,
                     textAlign     = TextAlign.End
                )
                Text(
                    text      = if (location != null) formatLon(location.longitude) else "—",
                     color     = contentColor,
                     fontSize  = 11.sp,
                     textAlign = TextAlign.End
                )
            }
        }
    }
}

private fun formatLat(lat: Double) = "%.4f° %s".format(abs(lat), if (lat >= 0) "N" else "S")
private fun formatLon(lon: Double) = "%.4f° %s".format(abs(lon), if (lon >= 0) "E" else "W")
