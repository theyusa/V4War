package tr.theyusa.v4war.ui.configuration

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import tr.theyusa.v4war.GroupOrder
import tr.theyusa.v4war.Key
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.ProfileManager
import tr.theyusa.v4war.database.ProxyEntity
import tr.theyusa.v4war.database.ProxyGroup
import tr.theyusa.v4war.database.SagerDatabase
import tr.theyusa.v4war.database.displayType
import tr.theyusa.v4war.ktx.onIoDispatcher
import tr.theyusa.v4war.ktx.runOnDefaultDispatcher
import tr.theyusa.v4war.repository.resolveRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class GroupProfilesHolderUiState(
    val profiles: List<ProfileItem> = emptyList(),
    val hiddenProfiles: Int = 0,
    val scrollIndex: Int? = null,
    val shouldRequestFocus: Boolean = false,
)

@Immutable
data class ProfileItem(
    val profile: ProxyEntity,
    val isSelected: Boolean,
    val started: Boolean,
)

@Stable
class GroupProfilesHolderViewModel(
    initialGroup: ProxyGroup,
    val preSelected: Long?,
) : ViewModel() {

    var group: ProxyGroup = initialGroup
        private set

    private val _uiState = MutableStateFlow(GroupProfilesHolderUiState())
    val uiState = _uiState.asStateFlow()

    val alwaysShowAddress = DataStore.configurationStore.booleanFlow(Key.ALWAYS_SHOW_ADDRESS, false)
    val blurredAddress = DataStore.configurationStore.booleanFlow(Key.BLURRED_ADDRESS, false)
    val trafficStatistics =
        DataStore.configurationStore.booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS, true)
    val securityAdvisory = DataStore.configurationStore.booleanFlow(Key.SECURITY_ADVISORY, true)
    val selectedProxy = DataStore.configurationStore.longFlow(Key.PROFILE_ID)

    private var isFirstLoad = true
    private var observeJob: Job? = null
    private var loadJob: Job? = null
    private var deleteTimer: Job? = null
    private val hiddenProfileAccess = Mutex()
    private val hiddenProfileIds = mutableSetOf<Long>()

    fun startObserving() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    SagerDatabase.proxyDao.getByGroup(group.id).collect { profiles ->
                        val shouldScroll = isFirstLoad
                        isFirstLoad = false
                        reloadProfiles(profiles, shouldScroll)
                    }
                }

                if (preSelected == null) {
                    launch {
                        selectedProxy.collect {
                            reloadProfiles(null, false)
                        }
                    }
                }

                launch {
                    SagerDatabase.groupDao.getById(group.id).collectLatest { updated ->
                        if (updated != null && updated != group) {
                            group = updated
                            reloadProfiles(null, false)
                        }
                    }
                }
            }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    fun submitReordered(changes: List<OrderedItem<ProfileItem>>) = runOnDefaultDispatcher {
        val toChange = changes.mapNotNull { orderedItem ->
            val profile = orderedItem.value.profile
            val newOrder = orderedItem.newIndex.toLong()
            if (profile.userOrder != newOrder) {
                profile.copy(userOrder = newOrder)
            } else {
                null
            }
        }
        if (toChange.isNotEmpty()) onIoDispatcher {
            ProfileManager.updateProfile(toChange)
        }
    }

    var query: String = ""
        set(value) {
            val lowercase = value.lowercase()
            if (lowercase != field) {
                field = lowercase
                loadJob?.cancel()
                loadJob = viewModelScope.launch {
                    reloadProfiles(null, false)
                }
            }
        }

    private suspend fun reloadProfiles(
        raw: List<ProxyEntity>?,
        shouldScroll: Boolean,
    ) = hiddenProfileAccess.withLock {
        val started = DataStore.serviceState.started
        val current = DataStore.currentProfile
        val selected = preSelected ?: DataStore.selectedProxy

        val comparator: Comparator<ProxyEntity> = when (group.order) {
            GroupOrder.BY_NAME -> compareBy { it.displayName() }
            GroupOrder.BY_DELAY -> compareBy {
                if (it.status == ProxyEntity.STATUS_AVAILABLE) {
                    it.ping
                } else {
                    Int.MAX_VALUE
                }
            }

            else -> compareBy { it.userOrder }
        }
        var selectedIndex = -1
        val profiles = (raw ?: onIoDispatcher {
            SagerDatabase.proxyDao.getByGroup(group.id).first()
        })
            .filter {
                if (it.id in hiddenProfileIds) return@filter false
                val query = query
                if (query.isBlank()) {
                    true
                } else {
                    it.displayName().lowercase().contains(query)
                            || it.displayType().lowercase().contains(query)
                            || it.displayAddress().lowercase().contains(query)
                }
            }
            .sortedWith(comparator)
            .mapIndexed { index, entity ->
                val isSelected = entity.id == selected
                if (isSelected) selectedIndex = index
                ProfileItem(
                    profile = entity,
                    isSelected = isSelected,
                    started = isSelected && started && entity.id == current,
                )
            }

        _uiState.update { state ->
            state.copy(
                profiles = profiles,
                hiddenProfiles = hiddenProfileIds.size,
                scrollIndex = selectedIndex.takeIf { shouldScroll && selectedIndex >= 0 },
            )
        }
    }

    fun consumeScrollIndex() {
        _uiState.update { it.copy(scrollIndex = null) }
    }

    fun scrollToProxy(proxyId: Long, fallbackToTop: Boolean) {
        viewModelScope.launch {
            val profiles = _uiState.value.profiles
            val index = profiles.indexOfFirst { it.profile.id == proxyId }
            if (index >= 0) {
                _uiState.update { it.copy(scrollIndex = index) }
            } else if (fallbackToTop) {
                _uiState.update { it.copy(scrollIndex = 0) }
            }
        }
    }

    fun requestFocusIfNotHave() {
        _uiState.update { it.copy(shouldRequestFocus = true) }
    }

    fun consumeFocusRequest() {
        _uiState.update { it.copy(shouldRequestFocus = false) }
    }

    fun onProfileSelected(profileId: Long) {
        viewModelScope.launch {
            reloadProfiles(null, false)
        }
    }

    fun undoableRemove(id: Long) = viewModelScope.launch {
        hiddenProfileAccess.withLock {
            _uiState.update { state ->
                val profiles = state.profiles.toMutableList()
                val index = profiles.indexOfFirst { it.profile.id == id }
                if (index >= 0) {
                    profiles.removeAt(index)
                    hiddenProfileIds.add(id)
                }
                state.copy(
                    profiles = profiles,
                    hiddenProfiles = hiddenProfileIds.size,
                )
            }
        }
        startDeleteTimer()
    }

    private fun startDeleteTimer() {
        deleteTimer?.cancel()
        deleteTimer = viewModelScope.launch {
            delay(5000)
            commit()
        }
    }

    fun undo() = viewModelScope.launch {
        deleteTimer?.cancel()
        deleteTimer = null
        hiddenProfileAccess.withLock {
            hiddenProfileIds.clear()
        }
        val profiles = onIoDispatcher { SagerDatabase.proxyDao.getByGroup(group.id).first() }
        reloadProfiles(profiles, false)
    }

    fun commit() = runOnDefaultDispatcher {
        deleteTimer?.cancel()
        deleteTimer = null
        val toDelete = hiddenProfileAccess.withLock {
            val toDelete = hiddenProfileIds.toList()
            hiddenProfileIds.clear()
            toDelete
        }
        onIoDispatcher {
            ProfileManager.deleteProfiles(group.id, toDelete)
        }
    }
}
