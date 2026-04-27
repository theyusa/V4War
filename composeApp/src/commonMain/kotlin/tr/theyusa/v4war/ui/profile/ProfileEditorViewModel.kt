@file:Suppress("UNCHECKED_CAST")

package tr.theyusa.v4war.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import tr.theyusa.v4war.GroupType
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.ProfileManager
import tr.theyusa.v4war.database.ProxyEntity
import tr.theyusa.v4war.database.ProxyGroup
import tr.theyusa.v4war.database.SagerDatabase
import tr.theyusa.v4war.fmt.AbstractBean
import tr.theyusa.v4war.fmt.SingBoxOptions
import tr.theyusa.v4war.ktx.applyDefaultValues
import tr.theyusa.v4war.ktx.onIoDispatcher
import tr.theyusa.v4war.ktx.runOnIoDispatcher
import tr.theyusa.v4war.resources.*
import tr.theyusa.v4war.ui.StringOrRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

@Immutable
internal sealed interface ProfileEditorUiEvent {
    data class Alert(val title: StringOrRes, val message: StringOrRes) : ProfileEditorUiEvent
}

@Immutable
internal sealed interface ProfileEditorUiState {
    val customConfig: String
    val customOutbound: String
}

@Stable
internal abstract class ProfileEditorViewModel<T : AbstractBean> : ViewModel() {

    abstract val uiState: StateFlow<ProfileEditorUiState>

    private val _uiEvent = MutableSharedFlow<ProfileEditorUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    protected suspend fun emitAlert(title: StringOrRes, message: StringOrRes) {
        _uiEvent.emit(ProfileEditorUiEvent.Alert(title, message))
    }

    private val _initialState = MutableStateFlow<ProfileEditorUiState?>(null)
    val isDirty by lazy(LazyThreadSafetyMode.NONE) {
        combine(uiState, _initialState) { currentState, initialState ->
            initialState?.let {
                it != currentState
            } ?: false
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )
    }

    protected abstract fun createBean(): T
    protected abstract suspend fun T.writeToUiState()
    protected abstract fun T.loadFromUiState()

    var editingId = -1L
        private set
    val isNew get() = editingId < 0L
    lateinit var proxyEntity: ProxyEntity
    lateinit var bean: T
    var isSubscription = false

    fun initialize(editingId: Long, isSubscription: Boolean) {
        this@ProfileEditorViewModel.editingId = editingId
        this@ProfileEditorViewModel.isSubscription = isSubscription
        viewModelScope.launch {
            _initialState.value = null
            bean = if (isNew) {
                createBean().applyDefaultValues()
            } else {
                proxyEntity = onIoDispatcher { SagerDatabase.proxyDao.getById(editingId)!! }
                (proxyEntity.requireBean() as T)
            }
            bean.writeToUiState()
            _initialState.value = uiState.value
        }
    }

    fun delete() = runOnIoDispatcher {
        ProfileManager.deleteProfile(proxyEntity.groupId, editingId)
    }

    fun save() = runOnIoDispatcher {
        if (isNew) {
            val editingGroup = DataStore.selectedGroupForImport()
            DataStore.selectedGroup = editingGroup
            val bean = createBean()
            bean.loadFromUiState()
            ProfileManager.createProfile(editingGroup, bean)
            return@runOnIoDispatcher
        }
        bean.loadFromUiState()
        proxyEntity.putBean(bean)
        ProfileManager.updateProfile(proxyEntity)
    }

    suspend fun groupsForMove(): List<ProxyGroup> = onIoDispatcher {
        SagerDatabase.groupDao.allGroups()
            .first()
            .filter {
                it.type == GroupType.BASIC && it.id != proxyEntity.groupId
            }
    }

    fun move(to: Long) = runOnIoDispatcher {
        val from = proxyEntity.groupId
        proxyEntity.groupId = to
        ProfileManager.updateProfile(proxyEntity)
        DataStore.selectedGroup = to
    }

    abstract fun setCustomConfig(config: String)
    abstract fun setCustomOutbound(outbound: String)

}

@Composable
internal inline fun <reified VM : ProfileEditorViewModel<*>> profileEditorViewModel(
    profileId: Long,
    isSubscription: Boolean,
    noinline create: () -> VM,
): VM = viewModel(
    factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T {
            return create().also {
                it.initialize(profileId, isSubscription)
            } as T
        }
    },
)

internal val fingerprints
    get() = listOf(
        "",
        SingBoxOptions.FINGERPRINT_CHROME,
        SingBoxOptions.FINGERPRINT_FIREFOX,
        SingBoxOptions.FINGERPRINT_EDGE,
        SingBoxOptions.FINGERPRINT_SAFARI,
        SingBoxOptions.FINGERPRINT_360,
        SingBoxOptions.FINGERPRINT_QQ,
        SingBoxOptions.FINGERPRINT_IOS,
        SingBoxOptions.FINGERPRINT_ANDROID,
        SingBoxOptions.FINGERPRINT_RANDOM,
        SingBoxOptions.FINGERPRINT_RANDOMIZED,
    )

internal val congestionControls
    get() = listOf(
        "bbr",
        "cubic",
        "new_reno",
    )

internal val muxTypes = listOf("h2mux", "smux", "yamux")

internal val muxStrategies = listOf(
    Res.string.mux_max_connections,
    Res.string.mux_min_streams,
    Res.string.mux_max_streams,
)
