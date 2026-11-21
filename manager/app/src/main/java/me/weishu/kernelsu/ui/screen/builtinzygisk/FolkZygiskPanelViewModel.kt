package me.weishu.kernelsu.ui.screen.builtinzygisk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class FolkZygiskPanelViewModel : ViewModel() {
    val state = FolkZygiskStore.state
    fun refresh() { viewModelScope.launch { FolkZygiskStore.refresh() } }
    fun apply(action: PanelAction) { viewModelScope.launch { FolkZygiskStore.refresh(action) } }
}
