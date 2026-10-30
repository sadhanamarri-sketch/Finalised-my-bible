package com.example.mybible.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.QuoteSide
import com.example.mybible.ui.theme.WorkSansFontFamily

/**
 * The small tag on a cross reference that is one of the Old Testament's quotations in the New, in [color]:
 * "Quotes" on the verse quoting ([side]), "Quoted in" on the verse quoted.
 */
@Composable
fun QuoteTag(side: QuoteSide, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = if (side == QuoteSide.QUOTES) "QUOTES" else "QUOTED IN",
        color = color,
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp)
    )
}
