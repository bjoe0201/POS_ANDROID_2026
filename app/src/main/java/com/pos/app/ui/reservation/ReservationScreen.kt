package com.pos.app.ui.reservation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ReservationScreen() {
    val viewModel: ReservationViewModel = hiltViewModel()
    val selectedDate by viewModel.selectedDate.collectAsState()

    if (selectedDate == null) {
        ReservationCalendarScreen(viewModel = viewModel)
    } else {
        ReservationDayScreen(viewModel = viewModel, date = selectedDate!!)
    }
}
