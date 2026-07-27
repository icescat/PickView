package dev.aaa1115910.bv.viewmodel.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.network.NasServerApi
import dev.aaa1115910.bv.network.NasVideoItem
import dev.aaa1115910.bv.util.fInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class SearchResultViewModel(
    private val nasServerApi: NasServerApi
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    var keyword by mutableStateOf("")
    var videos by mutableStateOf<List<NasVideoItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun update() {
        if (keyword.isBlank()) return
        isLoading = true
        errorMessage = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                logger.fInfo { "Search local NAS videos with keyword: $keyword" }
                val all = nasServerApi.getVideos().getOrThrow()
                val kw = keyword.trim().lowercase()
                val filtered = all.filter { item ->
                    item.title.lowercase().contains(kw) ||
                            item.bvid.lowercase().contains(kw) ||
                            item.up_name.lowercase().contains(kw) ||
                            item.category.lowercase().contains(kw)
                }
                withContext(Dispatchers.Main) {
                    videos = filtered
                    isLoading = false
                }
            }.onFailure {
                logger.info { it.stackTraceToString() }
                withContext(Dispatchers.Main) {
                    errorMessage = it.message ?: "搜索失败"
                    isLoading = false
                }
            }
        }
    }
}
