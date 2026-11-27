package com.expensetracker.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.R
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the Google Play icon and feature graphic into docs/play-store (./gradlew recordRoborazziDebug). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlayStoreAssetsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The adaptive icon's two layers, full-bleed: Play applies its own rounded mask. */
    private fun iconBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { id ->
            ContextCompat.getDrawable(context, id)!!.apply {
                setBounds(0, 0, size, size)
                draw(canvas)
            }
        }
        return bitmap
    }

    @Test
    fun icon() {
        iconBitmap(512).captureRoboImage("../docs/play-store/icon-512.png")
    }

    @Test
    @Config(qualifiers = "w1024dp-h500dp-mdpi")
    fun featureGraphic() {
        val icon = iconBitmap(192).asImageBitmap()
        val screen = BitmapFactory.decodeFile("../docs/play-store/phone/9_cash_flow.png")
            ?: BitmapFactory.decodeFile("../docs/screenshots/9_cash_flow.png")
        captureRoboImage("../docs/play-store/feature-graphic.png") { FeatureGraphic(icon, screen?.asImageBitmap()) }
    }
}

@Composable
private fun FeatureGraphic(icon: ImageBitmap, screen: ImageBitmap?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF2F7BFF), Color(0xFF1D4ED8), Color(0xFF0A1F5C)))),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 72.dp, end = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Image(icon, contentDescription = null, modifier = Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)))
                Spacer(Modifier.height(28.dp))
                Text("Expense Tracker", color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Spending, income and cash flow.\nDollars, riel and more, with live rates.",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 26.sp,
                    lineHeight = 36.sp,
                )
            }
            if (screen != null) {
                // A phone that runs off the bottom edge, showing the cash flow screen.
                Box(
                    Modifier
                        .width(250.dp)
                        .fillMaxHeight()
                        .offset(y = 70.dp)
                        .padding(top = 20.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF111218))
                        .border(7.dp, Color(0xFF111218), RoundedCornerShape(32.dp))
                        .padding(7.dp)
                        .clip(RoundedCornerShape(26.dp)),
                ) {
                    Image(screen, contentDescription = null, contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
