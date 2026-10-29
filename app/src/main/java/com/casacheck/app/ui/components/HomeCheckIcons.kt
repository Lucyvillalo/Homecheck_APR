package com.casacheck.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.casacheck.app.ui.theme.Navy

@androidx.compose.runtime.Composable
internal fun HomeCheckIcon(kind: String, description: String, modifier: Modifier, tint: Color = Navy) {
    Canvas(modifier.semantics { contentDescription = description }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.6f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x,y), Offset(x2,y2), 1.6f, StrokeCap.Round)
            when (kind) {
                "camera" -> { drawRoundRect(tint, Offset(3f,7f), Size(18f,13f), CornerRadius(2f), style = stroke); drawCircle(tint,4f, Offset(12f,13f),style=stroke); line(8f,7f,9f,4f); line(9f,4f,15f,4f); line(15f,4f,16f,7f) }
                "add" -> { drawRoundRect(tint,Offset(3f,3f),Size(18f,18f),CornerRadius(2f),style=stroke); line(7f,12f,17f,12f);line(12f,7f,12f,17f) }
                "home" -> { val path=Path().apply { moveTo(2f,11f);lineTo(12f,3f);lineTo(22f,11f);moveTo(5f,9f);lineTo(5f,21f);lineTo(19f,21f);lineTo(19f,9f);moveTo(9f,21f);lineTo(9f,14f);lineTo(15f,14f);lineTo(15f,21f) };drawPath(path,tint,style=stroke) }
                "inventory" -> { val path=Path().apply { moveTo(3f,7f);lineTo(12f,2f);lineTo(21f,7f);lineTo(21f,18f);lineTo(12f,23f);lineTo(3f,18f);close();moveTo(3f,7f);lineTo(12f,12f);lineTo(21f,7f);moveTo(12f,12f);lineTo(12f,23f);moveTo(7f,4f);lineTo(17f,10f) };drawPath(path,tint,style=stroke) }
                else -> { drawRoundRect(tint, Offset(5f,3f),Size(14f,18f),CornerRadius(2f),style=stroke);line(9f,8f,15f,8f);line(9f,12f,15f,12f);line(9f,16f,15f,16f);if(kind=="checklist") {drawRoundRect(tint,Offset(9f,1f),Size(6f,4f),CornerRadius(1f),style=stroke)} }
            }
        }
    }
}
