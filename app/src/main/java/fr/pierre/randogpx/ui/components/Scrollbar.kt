package fr.pierre.randogpx.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ascenseur vertical minimal (pas de dépendance externe) pour une LazyColumn. */
fun Modifier.simpleVerticalScrollbar(state: LazyListState, width: Dp = 4.dp): Modifier =
    drawWithContent {
        drawContent()
        val layoutInfo = state.layoutInfo
        val totalItemsCount = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo
        if (totalItemsCount > 0 && visibleItems.isNotEmpty() && visibleItems.size < totalItemsCount) {
            val firstVisibleIndex = visibleItems.first().index
            val thumbHeight = size.height * visibleItems.size / totalItemsCount
            val thumbOffsetY = size.height * firstVisibleIndex / totalItemsCount
            drawRect(
                color = Color.Gray.copy(alpha = 0.6f),
                topLeft = Offset(size.width - width.toPx(), thumbOffsetY),
                size = Size(width.toPx(), thumbHeight)
            )
        }
    }

/** Ascenseur vertical minimal (pas de dépendance externe) pour une Column en verticalScroll. */
fun Modifier.simpleVerticalScrollbar(state: ScrollState, width: Dp = 4.dp): Modifier =
    drawWithContent {
        drawContent()
        val viewportHeight = size.height
        val contentHeight = viewportHeight + state.maxValue
        if (state.maxValue > 0) {
            val thumbHeight = viewportHeight * viewportHeight / contentHeight
            val thumbOffsetY = (viewportHeight - thumbHeight) * state.value / state.maxValue
            drawRect(
                color = Color.Gray.copy(alpha = 0.6f),
                topLeft = Offset(size.width - width.toPx(), thumbOffsetY),
                size = Size(width.toPx(), thumbHeight)
            )
        }
    }
