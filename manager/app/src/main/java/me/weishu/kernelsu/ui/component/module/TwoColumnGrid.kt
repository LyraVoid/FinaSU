package me.weishu.kernelsu.ui.component.module

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun <T> TwoColumnGrid(
    items: List<T>,
    key: (item: T) -> Any,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = 16.dp,
    horizontalSpacing: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(),
    itemContent: @Composable (item: T) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        itemsIndexed(items = items, key = { _, item -> key(item) }) { _, item ->
            itemContent(item)
        }
    }
}
