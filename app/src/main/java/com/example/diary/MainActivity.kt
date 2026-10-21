package com.example.diary

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diary.ui.theme.DiaryTheme
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiaryTheme(dynamicColor = false) { DiaryApp() }
        }
    }
}

private data class DiaryEntry(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val content: String,
    val mood: String
)

private enum class DiaryDestination { HOME, JOURNAL }
private data class MoodOption(val emoji: String, val label: String)

private val moods = listOf(
    MoodOption("😞", "Low"), MoodOption("😕", "Meh"), MoodOption("🙂", "Okay"),
    MoodOption("😊", "Good"), MoodOption("🤩", "Great")
)
private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** An offline-only store: entries remain on this device and require no account. */
private object DiaryStorage {
    private const val preferencesName = "diary_entries"
    private const val entriesKey = "entries"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun load(preferences: SharedPreferences): List<DiaryEntry> {
        val rawEntries = preferences.getString(entriesKey, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(rawEntries)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        DiaryEntry(
                            id = item.getLong("id"),
                            date = LocalDate.parse(item.getString("date")),
                            title = item.optString("title"),
                            content = item.optString("content"),
                            mood = item.optString("mood", "🙂")
                        )
                    )
                }
            }.sortedWith(compareByDescending<DiaryEntry> { it.date }.thenByDescending { it.id })
        }.getOrElse { emptyList() }
    }

    fun save(preferences: SharedPreferences, entries: List<DiaryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("date", entry.date.toString())
                    .put("title", entry.title)
                    .put("content", entry.content)
                    .put("mood", entry.mood)
            )
        }
        preferences.edit().putString(entriesKey, array.toString()).apply()
    }
}

@Composable
private fun DiaryApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { DiaryStorage.preferences(context) }
    var entries by remember { mutableStateOf(DiaryStorage.load(preferences)) }
    var destination by remember { mutableStateOf(DiaryDestination.HOME) }
    var selectedEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var entryBeingEdited by remember { mutableStateOf<DiaryEntry?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    fun updateEntries(updatedEntries: List<DiaryEntry>) {
        entries =
            updatedEntries.sortedWith(compareByDescending<DiaryEntry> { it.date }.thenByDescending { it.id })
        DiaryStorage.save(preferences, entries)
    }

    when {
        isEditing -> DiaryEditorScreen(
            entry = entryBeingEdited,
            onBack = { isEditing = false; entryBeingEdited = null },
            onSave = { savedEntry ->
                updateEntries(entries.filterNot { it.id == savedEntry.id } + savedEntry)
                isEditing = false
                entryBeingEdited = null
                selectedEntry = savedEntry
            }
        )

        selectedEntry != null -> {
            val currentEntry = entries.firstOrNull { it.id == selectedEntry?.id }
            if (currentEntry == null) selectedEntry = null else EntryDetailScreen(
                entry = currentEntry,
                onBack = { selectedEntry = null },
                onEdit = {
                    entryBeingEdited = currentEntry; selectedEntry = null; isEditing = true
                },
                onDelete = {
                    updateEntries(entries.filterNot { it.id == currentEntry.id }); selectedEntry =
                    null; destination = DiaryDestination.JOURNAL
                }
            )
        }

        else -> Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                DiaryBottomBar(
                    destination,
                    onDestinationChanged = { destination = it })
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { entryBeingEdited = null; isEditing = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Text("+", fontSize = 30.sp, fontWeight = FontWeight.Light) }
            }
        ) { paddingValues ->
            when (destination) {
                DiaryDestination.HOME -> HomeScreen(
                    entries, paddingValues,
                    onNewEntry = { entryBeingEdited = null; isEditing = true },
                    onOpenEntry = { selectedEntry = it },
                    onOpenJournal = { destination = DiaryDestination.JOURNAL }
                )

                DiaryDestination.JOURNAL -> JournalScreen(
                    entries,
                    paddingValues,
                    onOpenEntry = { selectedEntry = it })
            }
        }
    }
}

@Composable
private fun DiaryBottomBar(
    destination: DiaryDestination,
    onDestinationChanged: (DiaryDestination) -> Unit
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = destination == DiaryDestination.HOME,
            onClick = { onDestinationChanged(DiaryDestination.HOME) },
            icon = { Text("⌂", fontSize = 22.sp) }, label = { Text("Home") }
        )
        NavigationBarItem(
            selected = destination == DiaryDestination.JOURNAL,
            onClick = { onDestinationChanged(DiaryDestination.JOURNAL) },
            icon = { Text("▤", fontSize = 21.sp) }, label = { Text("Journal") }
        )
    }
}

@Composable
private fun HomeScreen(
    entries: List<DiaryEntry>, contentPadding: PaddingValues, onNewEntry: () -> Unit,
    onOpenEntry: (DiaryEntry) -> Unit, onOpenJournal: () -> Unit
) {
    val today = LocalDate.now()
    val todayEntry = entries.firstOrNull { it.date == today }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            20.dp,
            contentPadding.calculateTopPadding() + 18.dp,
            20.dp,
            contentPadding.calculateBottomPadding() + 90.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                today.format(fullDateFormatter), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Your private corner",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Slow down, notice the moment, and make it yours.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            TodayPromptCard(todayEntry) {
                if (todayEntry == null) onNewEntry() else onOpenEntry(
                    todayEntry
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat(
                    entries.size.toString(),
                    if (entries.size == 1) "entry saved" else "entries saved",
                    Modifier.weight(1f)
                )
                MiniStat(entries.firstOrNull()?.mood ?: "—", "latest mood", Modifier.weight(1f))
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Recent reflections",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (entries.size > 3) TextButton(onClick = onOpenJournal) { Text("See all") }
            }
        }
        if (entries.isEmpty()) item { EmptyEntriesCard(onNewEntry) }
        else items(
            entries.take(3),
            key = { it.id }) { entry -> EntryCard(entry) { onOpenEntry(entry) } }
    }
}

@Composable
private fun TodayPromptCard(entry: DiaryEntry?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "TODAY'S PAGE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (entry == null) "How are you, really?" else entry.title.ifBlank { "Today’s reflection" },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(7.dp))
            Text(
                if (entry == null) "Take a few minutes to put your thoughts into words." else "Open today’s entry and continue where you left off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(17.dp))
            Surface(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                contentColor = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ) {
                Text(
                    if (entry == null) "Write today’s entry  →" else "Open today’s entry  →",
                    Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyEntriesCard(onCreate: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "A blank page is waiting",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "There is no right way to begin. One honest sentence is enough.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onCreate,
                contentPadding = PaddingValues(top = 10.dp)
            ) { Text("Create your first entry") }
        }
    }
}

@Composable
private fun JournalScreen(
    entries: List<DiaryEntry>,
    contentPadding: PaddingValues,
    onOpenEntry: (DiaryEntry) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredEntries = remember(entries, query) {
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        if (normalizedQuery.isEmpty()) entries else entries.filter { entry ->
            entry.title.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                    entry.content.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                    entry.date.format(shortDateFormatter).lowercase(Locale.getDefault())
                        .contains(normalizedQuery)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            20.dp,
            contentPadding.calculateTopPadding() + 18.dp,
            20.dp,
            contentPadding.calculateBottomPadding() + 90.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Journal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Every page you save stays on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Text("⌕", fontSize = 23.sp) },
                placeholder = { Text("Search your reflections") },
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (filteredEntries.isEmpty()) item {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Text(
                    if (entries.isEmpty()) "Your saved entries will appear here." else "No entries match “$query”.",
                    Modifier.padding(22.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else items(filteredEntries, key = { it.id }) { entry ->
            EntryCard(entry) {
                onOpenEntry(
                    entry
                )
            }
        }
    }
}

@Composable
private fun EntryCard(entry: DiaryEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.42f
            )
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center) { Text(entry.mood, fontSize = 21.sp) }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.date.format(shortDateFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.title.ifBlank { "Untitled reflection" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.content.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        entry.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DiaryEditorScreen(
    entry: DiaryEntry?,
    onBack: () -> Unit,
    onSave: (DiaryEntry) -> Unit
) {
    var title by remember(entry?.id) { mutableStateOf(entry?.title.orEmpty()) }
    var content by remember(entry?.id) { mutableStateOf(entry?.content.orEmpty()) }
    var mood by remember(entry?.id) { mutableStateOf(entry?.mood ?: "🙂") }
    val entryDate = entry?.date ?: LocalDate.now()
    val canSave = title.isNotBlank() || content.isNotBlank()
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        EditorTopBar(if (entry == null) "New entry" else "Edit entry", onBack, {
            onSave(
                DiaryEntry(
                    entry?.id ?: System.currentTimeMillis(),
                    entryDate,
                    title.trim(),
                    content.trim(),
                    mood
                )
            )
        }, canSave)
    }) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                entryDate.format(fullDateFormatter),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "How did the day feel?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            MoodPicker(mood, onMoodSelected = { mood = it })
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Give this moment a title") },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp),
                placeholder = { Text("What’s on your mind?") },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Your entries are stored locally on this device.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("‹  Back", fontSize = 16.sp) }
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) { Text("Save") }
    }
}

@Composable
private fun MoodPicker(selectedMood: String, onMoodSelected: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        moods.forEach { option ->
            val selected = option.emoji == selectedMood
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onMoodSelected(option.emoji) }
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(
                            alpha = 0.4f
                        ),
                        RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(option.emoji, fontSize = 22.sp)
                    Text(option.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun EntryDetailScreen(
    entry: DiaryEntry,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹  Journal", fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        }
    }) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(entry.mood, fontSize = 24.sp) }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        entry.date.format(fullDateFormatter),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Personal reflection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                entry.title.ifBlank { "Untitled reflection" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))
            Text(
                entry.content.ifBlank { "No written reflection for this day." },
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Delete this entry?") },
        text = { Text("This removes the entry from this device. This action can’t be undone.") },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } }
    )
}


