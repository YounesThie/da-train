package com.example.traintimes

import android.content.Context
import com.example.traintimes.R
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.datastore.preferences.preferencesDataStore
import com.example.traintimes.model.TrainStatus
import com.example.traintimes.ui.theme.TrainTimesTheme
import com.example.traintimes.viewmodel.TrainViewModel
import com.example.traintimes.viewmodel.TrainViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale

val Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrainTimesTheme {
                MainScreen(viewModelFactory = TrainViewModelFactory(dataStore))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModelFactory: TrainViewModelFactory) {
    val navController = rememberNavController()
    val viewModel: TrainViewModel = viewModel(factory = viewModelFactory)
    val currentStation by viewModel.currentStation.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentStation.name) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(id = R.string.schedules_tab)) },
                    label = { Text(stringResource(id = R.string.schedules_tab)) },
                    selected = currentDestination?.hierarchy?.any { it.route == "schedules" } == true,
                    onClick = {
                        navController.navigate("schedules") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Info, contentDescription = stringResource(id = R.string.about_tab)) },
                    label = { Text(stringResource(id = R.string.about_tab)) },
                    selected = currentDestination?.hierarchy?.any { it.route == "about" } == true,
                    onClick = {
                        navController.navigate("about") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "schedules",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("schedules") {
                SchedulesScreen(viewModel)
            }
            composable("about") {
                AboutScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(viewModel: TrainViewModel) {
    val schedules by viewModel.schedules.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    val selectedJourney by viewModel.selectedJourney.collectAsState()
    val isJourneyLoading by viewModel.isJourneyLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                viewModel.searchStations(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(id = R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = stringResource(id = R.string.search_desc)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        viewModel.searchStations("")
                    }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(id = R.string.clear_desc))
                    }
                }
            },
            singleLine = true
        )

        // Search Results Dropdown
        if (searchResults.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(searchResults) { station ->
                        Text(
                            text = station.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectStation(station)
                                    searchQuery = ""
                                }
                                .padding(16.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        // Filter Chips
        val filters = listOf("ICE/IC", "Regional", "S-Bahn", "U-Bahn", "Tram", "Bus")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = {
                        if (selectedFilter == filter) viewModel.setFilter(null)
                        else viewModel.setFilter(filter)
                    },
                    label = { Text(filter) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
            if (isLoading && schedules.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null && schedules.isEmpty()) {
                Text(
                    text = errorMessage ?: stringResource(id = R.string.unknown_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (!isLoading && schedules.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.no_departures_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(schedules) { schedule ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { viewModel.loadJourney(schedule.tripId) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = schedule.lineName,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    Text(
                                        text = schedule.destination,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = schedule.departureTime,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = schedule.trackNumber,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    val statusColor = when (schedule.status) {
                                        TrainStatus.ON_TIME -> MaterialTheme.colorScheme.primary
                                        TrainStatus.DELAYED -> MaterialTheme.colorScheme.error
                                        TrainStatus.CANCELLED -> MaterialTheme.colorScheme.error
                                    }
                                    Text(
                                        text = schedule.status.name.replace("_", " "),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = statusColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    // Bottom Sheet for Journey Details
    if (isJourneyLoading || selectedJourney != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearJourney() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (isJourneyLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(id = R.string.loading_route))
                        }
                    }
                } else if (selectedJourney != null) {
                    val journey = selectedJourney!!
                    Text(
                        text = "${journey.line.name} -> ${journey.direction}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn {
                        items(journey.stopovers) { stopover ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                // Extract time
                                val rawTime = stopover.departureTime ?: stopover.arrivalTime ?: stopover.plannedDeparture ?: stopover.plannedArrival
                                val formattedTime = try {
                                    if (rawTime != null) {
                                        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                                        val date = format.parse(rawTime)
                                        if (date != null) {
                                            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                            outputFormat.format(date)
                                        } else ""
                                    } else ""
                                } catch(e: Exception) { "" }

                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(60.dp)
                                )
                                Text(
                                    text = stopover.stop.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.about_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.about_desc),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
