package com.pos.app.ui.reservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.db.dao.TableDao
import com.pos.app.data.db.entity.ReservationEntity
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.data.repository.ReservationRepository
import com.pos.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReservationViewModel @Inject constructor(
    private val reservationRepository: ReservationRepository,
    private val tableDao: TableDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _currentYearMonth = MutableStateFlow(YearMonth.now())
    val currentYearMonth: StateFlow<YearMonth> = _currentYearMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    val monthReservations: StateFlow<List<ReservationEntity>> = _currentYearMonth
        .flatMapLatest { ym ->
            reservationRepository.getByMonth(ym.format(DateTimeFormatter.ofPattern("yyyy-MM")))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dayReservations: StateFlow<List<ReservationEntity>> = _selectedDate
        .flatMapLatest { date ->
            if (date == null) flowOf(emptyList())
            else reservationRepository.getByDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeTables: StateFlow<List<TableEntity>> = tableDao.getActiveTables()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bizStart: StateFlow<String> = settingsRepository.bizStart
        .stateIn(viewModelScope, SharingStarted.Eagerly, "11:00")

    val bizEnd: StateFlow<String> = settingsRepository.bizEnd
        .stateIn(viewModelScope, SharingStarted.Eagerly, "22:00")

    val breakStart: StateFlow<String> = settingsRepository.breakStart
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val breakEnd: StateFlow<String> = settingsRepository.breakEnd
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val defaultDuration: StateFlow<Int> = settingsRepository.defaultDuration
        .stateIn(viewModelScope, SharingStarted.Eagerly, 90)

    val calendarChipsPerRow: StateFlow<Int> = settingsRepository.calendarChipsPerRow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    fun prevMonth() { _currentYearMonth.value = _currentYearMonth.value.minusMonths(1) }
    fun nextMonth() { _currentYearMonth.value = _currentYearMonth.value.plusMonths(1) }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        // Sync month view to selected date's month
        val ym = YearMonth.of(date.year, date.month)
        if (_currentYearMonth.value != ym) _currentYearMonth.value = ym
    }

    fun clearSelectedDate() { _selectedDate.value = null }

    fun upsertReservation(reservation: ReservationEntity) {
        viewModelScope.launch { reservationRepository.upsert(reservation) }
    }

    fun deleteReservation(reservation: ReservationEntity) {
        viewModelScope.launch { reservationRepository.delete(reservation) }
    }
}
