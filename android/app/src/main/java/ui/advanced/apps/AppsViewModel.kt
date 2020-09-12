/*
 * This file is part of Blokada.
 *
 * Blokada is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Blokada is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Blokada.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright © 2020 Blocka AB. All rights reserved.
 *
 * @author Karol Gusak (karol@blocka.net)
 */

package ui.advanced.apps

import androidx.lifecycle.*
import kotlinx.coroutines.launch
import model.*
import repository.AppRepository
import engine.EngineService
import ui.utils.cause
import utils.Logger
import java.lang.Exception

class AppsViewModel : ViewModel() {

    enum class Group {
        INSTALLED, SYSTEM
    }

    enum class Filter {
        ALL, BYPASSED, NOT_BYPASSED
    }

    private val log = Logger("Apps")
    private val engine = EngineService
    private val appRepo = AppRepository

    private var group = Group.INSTALLED
    private var filter = Filter.ALL
    private var searchTerm: String? = null

    private val _apps = MutableLiveData<List<App>>()
    val apps = _apps.map {
        applyFilters(it)
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _apps.value = appRepo.getApps()
            } catch (ex: Exception) {
                log.e("Could not refresh apps".cause(ex))
            }
        }
    }

    fun getFilter() = filter

    fun filter(filter: Filter) {
        this.filter = filter
        updateLiveData()
    }

    fun showGroup(group: Group) {
        this.group = group
        updateLiveData()
    }

    fun search(search: String?) {
        this.searchTerm = search
        updateLiveData()
    }

    fun switchBypass(name: AppId) {
        viewModelScope.launch {
            log.v("Switching bypass for app: $name")
            appRepo.switchBypassForApp(name)
            engine.restart()
            refresh()
        }
    }

    fun switchBypassForAllSystemApps() {
        viewModelScope.launch {
            log.v("Switching bypass for all system apps")
            _apps.value?.let {
                val isBypassed = it.first { it.isSystem }.isBypassed
                it.filter { app -> app.isSystem && app.isBypassed == isBypassed }.forEach { app ->
                    appRepo.switchBypassForApp(app.id)
                }
                engine.restart()
                refresh()
            }
        }
    }

    private fun updateLiveData() {
        viewModelScope.launch {
            // This will cause to emit new event and to refresh the public LiveData
            _apps.value?.let {
                _apps.value = it
            }
        }
    }

    private fun applyFilters(apps: List<App>): List<App> {
        var entries = apps

        // Apply search term
        searchTerm?.run {
            entries = apps.filter {
                it.name.contains(this, ignoreCase = true) ||
                        it.id.contains(this, ignoreCase = true)
            }
        }

        // Apply filtering
        when (filter) {
            Filter.BYPASSED -> {
                entries = entries.filter { it.isBypassed }
            }
            Filter.NOT_BYPASSED -> {
                entries = entries.filter { !it.isBypassed }
            }
            else -> {}
        }

        // Apply filtering on group
        when(group) {
            Group.INSTALLED -> {
                entries = entries.filter { !it.isSystem }
            }
            Group.SYSTEM -> {
                entries = entries.filter { it.isSystem }
            }
        }

        return entries.sortedBy { it.name }
    }

}