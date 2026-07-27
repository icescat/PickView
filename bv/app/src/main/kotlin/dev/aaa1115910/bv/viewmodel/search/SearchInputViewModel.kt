package dev.aaa1115910.bv.viewmodel.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.dao.AppDatabase
import dev.aaa1115910.bv.entity.db.SearchHistoryDB
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.swapListWithMainContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.util.Date

@KoinViewModel
class SearchInputViewModel(
    private val db: AppDatabase = BVApp.getAppDatabase()
) : ViewModel() {
    private val logger = KotlinLogging.logger { }

    var keyword by mutableStateOf("")
    val searchHistories = mutableStateListOf<SearchHistoryDB>()

    init {
        loadSearchHistories()
    }

    private fun loadSearchHistories() {
        logger.fInfo { "Load search histories" }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                searchHistories.swapListWithMainContext(db.searchHistoryDao().getHistories(20))
                logger.fInfo { "Load search histories finish, size: ${searchHistories.size}" }
            }
        }
    }

    fun addSearchHistory(keyword: String) {
        logger.fInfo { "Add search history: $keyword" }
        viewModelScope.launch(Dispatchers.IO) {
            db.searchHistoryDao().findHistory(keyword)?.let { history ->
                logger.fInfo { "Search history $keyword already exist" }
                history.searchDate = Date()
                db.searchHistoryDao().update(history)
            } ?: let {
                logger.fInfo { "Insert new search history $keyword" }
                val history = SearchHistoryDB(keyword = keyword)
                db.searchHistoryDao().insert(history)
            }
            loadSearchHistories()
        }
    }

    fun deleteSearchHistory(history: SearchHistoryDB) {
        logger.fInfo { "Delete search history: ${history.keyword}" }
        viewModelScope.launch(Dispatchers.IO) {
            db.searchHistoryDao().delete(history)
            loadSearchHistories()
        }
    }

    fun deleteAllSearchHistories() {
        logger.fInfo { "Delete all search histories" }
        viewModelScope.launch(Dispatchers.IO) {
            db.searchHistoryDao().deleteAll()
            loadSearchHistories()
        }
    }
}
