package com.example.fuelmileagecalculatorapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.fuelmileagecalculatorapp.ui.theme.FuelMileageCalculatorAppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// --- Data Models ---
data class Vehicle(val id: String, val name: String, val type: String)

data class CalculationResult(
    val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val numericValue: Double,
    val displayValue: String,
    val timestamp: String,
    val distance: String,
    val fuel: String,
    val amount: String,
    val price: String,
    val isCashMode: Boolean,
    val unit: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FuelMileageCalculatorAppTheme {
                MainNavigation()
            }
        }
    }
}

// --- Storage Logic ---
private const val PREFS_NAME = "fuel_tracker_v9"
private const val HISTORY_KEY = "mileage_data"
private const val USER_NAME_KEY = "user_name"

fun saveToDisk(context: Context, history: List<CalculationResult>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    history.forEach { item ->
        val obj = JSONObject().apply {
            put("id", item.id); put("vehicleId", item.vehicleId); put("numericValue", item.numericValue)
            put("displayValue", item.displayValue); put("timestamp", item.timestamp)
            put("distance", item.distance); put("fuel", item.fuel)
            put("amount", item.amount); put("price", item.price)
            put("isCashMode", item.isCashMode); put("unit", item.unit)
        }
        jsonArray.put(obj)
    }
    prefs.edit(commit = true) { putString(HISTORY_KEY, jsonArray.toString()) }
}

fun loadFromDisk(context: Context): List<CalculationResult> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonString = prefs.getString(HISTORY_KEY, null) ?: return emptyList()
    val list = mutableListOf<CalculationResult>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(CalculationResult(
                id = obj.getString("id"), vehicleId = obj.getString("vehicleId"),
                numericValue = obj.getDouble("numericValue"), displayValue = obj.getString("displayValue"),
                timestamp = obj.getString("timestamp"), distance = obj.getString("distance"),
                fuel = obj.getString("fuel"), amount = obj.getString("amount"),
                price = obj.getString("price"), isCashMode = obj.getBoolean("isCashMode"),
                unit = obj.getString("unit")
            ))
        }
    } catch (e: Exception) { e.printStackTrace() }
    return list
}

// --- Navigation ---
@Composable
fun MainNavigation() {
    var currentScreen by remember { mutableStateOf("calculator") }
    val context = LocalContext.current
    var historyList by remember { mutableStateOf(loadFromDisk(context)) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == "calculator",
                    onClick = { currentScreen = "calculator" },
                    icon = { Icon(Icons.Default.Build, null) },
                    label = { Text("Calc") }
                )
                NavigationBarItem(
                    selected = currentScreen == "profile",
                    onClick = { currentScreen = "profile" },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Profile") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (currentScreen == "calculator") {
                EnhancedMileageCalculator(historyList) { newList ->
                    historyList = newList
                    saveToDisk(context, newList)
                }
            } else {
                ProfileScreen(historyList)
            }
        }
    }
}

// --- Line Chart Component ---
@Composable
fun MileageLineChart(history: List<CalculationResult>, modifier: Modifier = Modifier) {
    val points = history.take(8).reversed()
    val primaryColor = MaterialTheme.colorScheme.primary

    if (points.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("No data to display", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val maxVal = points.maxOf { it.numericValue }.toFloat().coerceAtLeast(1f)
    val minVal = points.minOf { it.numericValue }.toFloat()

    Column(modifier = modifier.padding(8.dp)) {
        Text("Efficiency Trend (Line)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp, start = 8.dp, end = 8.dp)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val stepX = if (points.size > 1) canvasWidth / (points.size - 1) else 0f
            val rangeY = (maxVal - minVal).coerceAtLeast(1f)

            val path = Path()
            points.forEachIndexed { index, result ->
                val x = index * stepX
                val normalizedY = (result.numericValue.toFloat() - minVal) / rangeY
                val y = canvasHeight - (normalizedY * canvasHeight)

                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
            drawPath(path = path, color = primaryColor, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

// --- Profile Screen ---
@Composable
fun ProfileScreen(history: List<CalculationResult>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(prefs.getString(USER_NAME_KEY, "Driver") ?: "Driver") }
    var isEditingName by remember { mutableStateOf(false) }

    val totalDistance = history.sumOf { it.distance.toDoubleOrNull() ?: 0.0 }
    val totalFuel = history.sumOf { it.fuel.toDoubleOrNull() ?: 0.0 }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        if (isEditingName) {
            OutlinedTextField(value = userName, onValueChange = { userName = it }, label = { Text("Name") }, trailingIcon = {
                IconButton(onClick = { prefs.edit { putString(USER_NAME_KEY, userName) }; isEditingName = false }) { Icon(Icons.Default.Check, null) }
            })
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(userName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { isEditingName = true }) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lifetime Summary", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Distance"); Text("%.1f km".format(totalDistance), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Fuel"); Text("%.1f L".format(totalFuel), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- Calculator Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMileageCalculator(historyList: List<CalculationResult>, onHistoryChange: (List<CalculationResult>) -> Unit) {
    var distIn by remember { mutableStateOf("") }; var fuelIn by remember { mutableStateOf("") }
    var amtIn by remember { mutableStateOf("") }; var prcIn by remember { mutableStateOf("") }
    var isCash by remember { mutableStateOf(false) }

    val vehicles = remember { listOf(Vehicle("1","Car","Sedan"), Vehicle("2","Truck","Heavy"), Vehicle("3","Bike","Moto"), Vehicle("4","Scooter","Scooter")) }
    var selVeh by remember { mutableStateOf(vehicles[0]) }
    var vehExp by remember { mutableStateOf(false) }

    val filteredHistory = historyList.filter { it.vehicleId == selVeh.id }
    val bestRecord = filteredHistory.maxByOrNull { it.numericValue }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Mileage Dashboard", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(12.dp)) {
                if (bestRecord != null) {
                    Surface(color = Color(0xFFFFA000), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.End)) {
                        Text("Best: ${bestRecord.displayValue}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                    }
                }
                MileageLineChart(filteredHistory)
            }
        }

        ExposedDropdownMenuBox(expanded = vehExp, onExpandedChange = { vehExp = !vehExp }) {
            OutlinedTextField(
                value = "${selVeh.name} (${selVeh.type})", onValueChange = {}, readOnly = true, label = { Text("Vehicle") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(vehExp) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = vehExp, onDismissRequest = { vehExp = false }) {
                vehicles.forEach { v -> DropdownMenuItem(text = { Text(v.name) }, onClick = { selVeh = v; vehExp = false }) }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = { isCash = !isCash }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isCash) "Switch to Liters Mode" else "Switch to Cash Mode")
        }

        OutlinedTextField(value = distIn, onValueChange = { distIn = it }, label = { Text("Distance (km)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        if (isCash) {
            Row(Modifier.padding(top = 8.dp), Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amtIn, onValueChange = { amtIn = it }, label = { Text("Amt Spent") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = prcIn, onValueChange = { prcIn = it }, label = { Text("Price/L") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        } else {
            OutlinedTextField(value = fuelIn, onValueChange = { fuelIn = it }, label = { Text("Fuel (L)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }

        Button(onClick = {
            val d = distIn.toDoubleOrNull() ?: 0.0
            val f = if (isCash) (amtIn.toDoubleOrNull() ?: 0.0) / (prcIn.toDoubleOrNull() ?: 1.0) else fuelIn.toDoubleOrNull() ?: 0.0
            if (d > 0 && f > 0) {
                val mileage = d / f
                val display = "%.2f km/L".format(mileage)
                val newEntry = CalculationResult(
                    vehicleId = selVeh.id, numericValue = mileage, displayValue = display,
                    timestamp = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date()),
                    distance = distIn, fuel = "%.2f".format(f), amount = amtIn, price = prcIn, isCashMode = isCash, unit = "km/L"
                )
                onHistoryChange(listOf(newEntry) + historyList)
                distIn = ""; fuelIn = ""; amtIn = ""; prcIn = ""
            }
        }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Log Entry") }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(filteredHistory, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.displayValue, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("${item.timestamp} • ${item.distance} km", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { onHistoryChange(historyList.filter { it.id != item.id }) }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}