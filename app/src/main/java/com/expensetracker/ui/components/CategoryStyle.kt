package com.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.data.Category
import com.expensetracker.ui.theme.LocalDarkTheme

val Category.icon: ImageVector
    get() = when (this) {
        Category.FOOD -> Icons.Rounded.Restaurant
        Category.GROCERIES -> Icons.Rounded.LocalGroceryStore
        Category.TRANSPORT -> Icons.Rounded.DirectionsCar
        Category.SHOPPING -> Icons.Rounded.ShoppingBag
        Category.BILLS -> Icons.AutoMirrored.Rounded.ReceiptLong
        Category.HOUSING -> Icons.Rounded.Home
        Category.ENTERTAINMENT -> Icons.Rounded.Movie
        Category.HEALTH -> Icons.Rounded.MedicalServices
        Category.EDUCATION -> Icons.Rounded.School
        Category.TRAVEL -> Icons.Rounded.Flight
        Category.GIFTS -> Icons.Rounded.CardGiftcard
        Category.OTHER -> Icons.Rounded.MoreHoriz
    }

/**
 * Category colors, one step per theme. The first eight follow a colorblind-validated
 * categorical order; the remaining ones are extras, so charts always pair color with a
 * labelled legend and fold small categories into "Other".
 */
private fun Category.hex(dark: Boolean): Long = when (this) {
    Category.FOOD -> if (dark) 0xFFE76624 else 0xFFF47230
    Category.TRANSPORT -> if (dark) 0xFF338CED else 0xFF257DE0
    Category.TRAVEL -> if (dark) 0xFF00A888 else 0xFF00B894
    Category.BILLS -> if (dark) 0xFFD28E05 else 0xFFF2A600
    Category.SHOPPING -> if (dark) 0xFFE24D8B else 0xFFEE7FAC
    Category.GROCERIES -> if (dark) 0xFF0AAC23 else 0xFF008A17
    Category.ENTERTAINMENT -> if (dark) 0xFF9887EF else 0xFF6A4CE2
    Category.HEALTH -> if (dark) 0xFFEB6263 else 0xFFEC4647
    Category.HOUSING -> if (dark) 0xFFC4773A else 0xFFB5651D
    Category.EDUCATION -> if (dark) 0xFF1A9BBF else 0xFF0E7F9E
    Category.GIFTS -> if (dark) 0xFFC45BD6 else 0xFFA21CAF
    Category.OTHER -> if (dark) 0xFF8A929C else 0xFF7C8591
}

fun Category.color(dark: Boolean): Color = Color(hex(dark))

val Category.color: Color
    @Composable @ReadOnlyComposable
    get() = color(LocalDarkTheme.current)

/** Neutral used for the folded "Other" slice in charts. */
val OtherSliceColor: Color
    @Composable @ReadOnlyComposable
    get() = Category.OTHER.color

@Composable
fun CategoryIcon(
    category: Category,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(category.color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = category.color,
            modifier = Modifier.size(iconSize),
        )
    }
}
