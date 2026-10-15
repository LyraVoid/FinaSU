package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.data.model.LayoutVariant
import me.weishu.kernelsu.data.model.SupportLinksStyle
import me.weishu.kernelsu.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableNavigationBadge: Boolean,
    val moduleDescriptionMaxLines: Int = 4,
    val homeLayoutStyle: String = HomeLayoutStyle.DEFAULT,
    val supportLinksStyle: String = SupportLinksStyle.DEFAULT,
    val layoutVariant: String = LayoutVariant.DEFAULT,
)
