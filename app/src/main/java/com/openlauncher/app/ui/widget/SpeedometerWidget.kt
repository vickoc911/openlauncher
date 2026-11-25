package com.openlauncher.app.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.util.LocationData
import kotlin.math.cos
import kotlin.math.sin
import com.openlauncher.app.R
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.Image

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.TextUnit

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage

@Composable
fun SpeedometerWidget(
    packageName: String,
    location: LocationData?,
    isMetric: Boolean,
    accent: Color,
    isDayMode: Boolean = false,
    digitalOnly: Boolean = false,
    vehicleImage: String = "",
    modifier: Modifier = Modifier
) {
    val maxSpeed     = if (isMetric) 200f else 124f
    val rawSpeedMps = location?.speedMps ?: 0f
    val filteredSpeedMps = if (rawSpeedMps < 0.5f) 0f else rawSpeedMps
    val targetSpeed = (filteredSpeedMps * if (isMetric) 3.6f else 2.237f).coerceAtLeast(0f)

    // Esto obliga al sistema a rellenar el vacío entre los calculos de la velocidad,
    // dándole tiempo al usuario de ver la transición numérica.
    val animatedSpeed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = tween(
            durationMillis = 900,
            easing = LinearOutSlowInEasing
        ),
        label = "SpeedAnimation"
    )

    // El resto de los cálculos numéricos usando el valor animado y fluido
    val speed = animatedSpeed.toInt()
    val hundredsText = if (speed >= 100) ((speed / 100) % 10).toString() else ""
    val tensText = if (speed >= 10) ((speed / 10) % 10).toString() else ""
    val onesText = (speed % 10).toString()

    val unitLabel    = if (isMetric) "KM/H" else "MPH"
    val trackAlpha   = if (isDayMode) 0.18f else 0.07f
    val tickAlphaMaj = if (isDayMode) 0.50f else 0.28f
    val tickAlphaMin = if (isDayMode) 0.25f else 0.13f

    val contentColor = if (isDayMode) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.onBackground
    val subAlpha      = if (isDayMode) 0.55f else 0.32f
    val tickBaseColor = if (isDayMode) Color(0xFF222222) else MaterialTheme.colorScheme.onBackground
    val colorfondob = if (isDayMode) Color.Black.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.2f)

    val context = LocalContext.current

    Box(
        modifier = modifier
        .paint(
            painter = if (isDayMode) painterResource(id = R.drawable.fondo_carro) else painterResource(id = R.drawable.fondo_carro_b),
               contentScale = ContentScale.Crop
        )
        .background(colorfondob),
        contentAlignment = Alignment.Center
    ) {

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ------------------ VELOCIDAD (20%) ------------------
            Box(
                modifier = Modifier
                .fillMaxHeight()
                .weight(0.3f),
                contentAlignment = Alignment.Center
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        RollingDigit(
                            targetDigit = hundredsText,
                            color = contentColor,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-1).sp,
                                     moveUp = false
                        )

                        RollingDigit(
                            targetDigit = tensText,
                            color = contentColor,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-1).sp,
                                     moveUp = true
                        )

                        RollingDigit(
                            targetDigit = onesText,
                            color = contentColor,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-1).sp,
                                     moveUp = false
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = unitLabel,
                         color = contentColor.copy(alpha = subAlpha * 1.5f),
                         fontSize = 10.sp,
                         fontWeight = FontWeight.Bold,
                         letterSpacing = 2.sp
                    )
                }
            }

            // ------------------ VEHÍCULO (80%) ------------------
            Box(
                modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                val isCustomUri = vehicleImage.startsWith("content://") || vehicleImage.startsWith("file://")
                val clickModifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (packageName.isNotEmpty()) {
                            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                            intent?.let {
                                context.startActivity(it)
                            }
                        }
                    }

                if (isCustomUri) {
                    AsyncImage(
                        model = Uri.parse(vehicleImage),
                        contentDescription = "Vehicle",
                        modifier = clickModifier,
                        contentScale = ContentScale.Fit
                    )
                } else {
                    val resId = when (vehicleImage) {
                        "honda_hrv" -> R.drawable.honda_hrv
                        "nissan_kicks" -> R.drawable.nissan_kicks
                        else -> R.drawable.car_model
                    }
                    Image(
                        painter = painterResource(resId),
                        contentDescription = "Vehicle",
                        modifier = clickModifier,
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}
