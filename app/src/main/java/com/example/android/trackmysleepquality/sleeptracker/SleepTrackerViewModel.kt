/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.content.res.Resources
import android.provider.ContactsContract
import android.provider.SyncStateContract.Helpers.insert
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.room.Insert
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.sleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private var toNight = MutableLiveData<sleepNight>()
     val nights = database.getAllNights()

    val nightsString = Transformations.map(nights){nights ->
        formatNights(nights, application.resources)
    }

    val startButtonVisible = Transformations.map(toNight) {
        null == it
    }
    val stopButtonVisible = Transformations.map(toNight) {
        null != it
    }
    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }
    private var _showSnackbarEvent = MutableLiveData<Boolean>()
    val showSnackbarEvent: LiveData<Boolean>
    get() = _showSnackbarEvent

    private val _navigateToSleepQuality = MutableLiveData<sleepNight>()
    val navigateToSleepQuality: LiveData<sleepNight>
    get() = _navigateToSleepQuality

    private val _navigateToSleepDataQuality = MutableLiveData<Long>()
    val navigateToSleepDataQuality
    get() = _navigateToSleepDataQuality
    fun onSleepNightClicked(id: Long){
        _navigateToSleepDataQuality.value = id
    }
    fun onSleepDataQualityNavigated(){
        _navigateToSleepDataQuality.value = null
    }

    fun doneNavigating(){
        _navigateToSleepQuality.value = null
    }
    fun doneShowingSnackbar(){
        _showSnackbarEvent.value = null
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch {
            toNight.value = getTonightFromDatabase()
        }
    }

    private suspend fun getTonightFromDatabase(): sleepNight? {
        return  withContext(Dispatchers.IO){
            var  night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli){
                night = null
            }
            night
        }
    }
    fun onStartTracking(){
        uiScope.launch {
            val  newNight = sleepNight()
            insert(newNight)
            toNight.value = getTonightFromDatabase()
        }
    }
    private suspend fun insert(night: sleepNight) {
        withContext(Dispatchers.IO){
            database.insert(night)
        }
    }
    private suspend fun update(night: sleepNight){
        withContext(Dispatchers.IO){
            database.update(night)
        }
    }

   fun onStopTracking(){
       uiScope.launch {
           val oldNight = toNight.value ?: return@launch
           oldNight.endTimeMilli = System.currentTimeMillis()
           update(oldNight)
           _navigateToSleepQuality.value = oldNight
       }
   }
    fun onClear(){
        uiScope.launch {
            clear()
            toNight.value = null
            _showSnackbarEvent.value = true
        }
    }
    suspend fun clear(){
        withContext(Dispatchers.IO){
            database.clear()
        }
    }
}

