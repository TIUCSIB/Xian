package dev.avsong.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import dev.avsong.AvSongApp
import dev.avsong.ui.library.CollectionContentViewModel
import dev.avsong.ui.library.FolderContentViewModel
import dev.avsong.ui.library.LibraryViewModel
import dev.avsong.ui.login.LoginViewModel

private class MainViewModelFactory(
    private val app: AvSongApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(
            app.songRepository,
            app.playerController,
            app.favoriteRepository,
            app.searchHistoryStore,
        ) as T
    }
}

/** 获取全局唯一的 MainViewModel(配置变更时保留)。 */
@Composable
fun rememberMainViewModel(): MainViewModel {
    val context = LocalContext.current
    val app = context.applicationContext as AvSongApp
    val factory = remember(app) { MainViewModelFactory(app) }
    return viewModel(factory = factory)
}

private class LoginViewModelFactory(
    private val app: AvSongApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LoginViewModel(app.authStore) as T
    }
}

@Composable
fun rememberLoginViewModel(): LoginViewModel {
    val context = LocalContext.current
    val app = context.applicationContext as AvSongApp
    val factory = remember(app) { LoginViewModelFactory(app) }
    return viewModel(factory = factory)
}

private class LibraryViewModelFactory(
    private val app: AvSongApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LibraryViewModel(
            app.favoriteRepository,
            dev.avsong.data.CollectionRepository,
            app.collectionStore,
        ) as T
    }
}

@Composable
fun rememberLibraryViewModel(): LibraryViewModel {
    val context = LocalContext.current
    val app = context.applicationContext as AvSongApp
    val factory = remember(app) { LibraryViewModelFactory(app) }
    return viewModel(factory = factory)
}

private class FolderContentViewModelFactory(
    private val app: AvSongApp,
    private val folderId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FolderContentViewModel(app.favoriteRepository, folderId) as T
    }
}

@Composable
fun rememberFolderContentViewModel(folderId: Long): FolderContentViewModel {
    val context = LocalContext.current
    val app = context.applicationContext as AvSongApp
    val factory = remember(app, folderId) { FolderContentViewModelFactory(app, folderId) }
    return viewModel(factory = factory, key = "folder-$folderId")
}

private class CollectionContentViewModelFactory(
    private val app: AvSongApp,
    private val mid: Long,
    private val seasonId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CollectionContentViewModel(dev.avsong.data.CollectionRepository, mid, seasonId) as T
    }
}

@Composable
fun rememberCollectionContentViewModel(mid: Long, seasonId: Long): CollectionContentViewModel {
    val context = LocalContext.current
    val app = context.applicationContext as AvSongApp
    val factory = remember(app, mid, seasonId) { CollectionContentViewModelFactory(app, mid, seasonId) }
    return viewModel(factory = factory, key = "collection-$mid-$seasonId")
}
