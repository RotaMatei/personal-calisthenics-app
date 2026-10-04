package com.personal.calisthenicsguide.ui.guide

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.personal.calisthenics.core.analytics.ProgressionGate
import com.personal.calisthenics.core.analytics.ProgressionState
import com.personal.calisthenics.core.model.Exercise
import com.personal.calisthenics.core.model.ExerciseKind
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.model.HighlightKind
import com.personal.calisthenics.core.model.MovementCategory
import com.personal.calisthenics.core.model.PeakAt
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenicsguide.data.MediaOverrideEntity
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.ui.BigButton
import com.personal.calisthenicsguide.ui.Muted
import com.personal.calisthenicsguide.ui.OutlineBigButton
import com.personal.calisthenicsguide.ui.Palette
import com.personal.calisthenicsguide.ui.Panel
import com.personal.calisthenicsguide.ui.Pill
import com.personal.calisthenicsguide.ui.SectionTitle
import com.personal.calisthenicsguide.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GuideViewModel(private val repository: Repository) : ViewModel() {
    val progression: StateFlow<Map<String, ProgressionState>> =
        repository.progression.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val media: StateFlow<Map<String, MediaOverrideEntity>> =
        repository.mediaOverrides.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleStage(exercise: Exercise, stage: Int) {
        viewModelScope.launch {
            val current = repository.progressionOf(exercise.id)
            repository.saveProgression(current.copy(stageMask = ProgressionGate.toggle(current.stageMask, stage)))
        }
    }

    fun unlockNextLevel(exercise: Exercise) {
        viewModelScope.launch {
            val current = repository.progressionOf(exercise.id)
            repository.saveProgression(ProgressionGate.advance(exercise, current))
        }
    }

    fun setMedia(exerciseId: String, uri: String, mimeType: String) {
        viewModelScope.launch { repository.setMedia(exerciseId, uri, mimeType) }
    }

    fun clearMedia(exerciseId: String) {
        viewModelScope.launch { repository.clearMedia(exerciseId) }
    }
}

@Composable
fun GuideScreen(modifier: Modifier = Modifier) {
    val vm = appViewModel { GuideViewModel(it.repository) }
    val progression by vm.progression.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selected != null) { selected = null }

    val exercise = selected?.let { SeedData.exerciseOrNull(it) }
    if (exercise == null) {
        ExerciseList(progression, onOpen = { selected = it }, modifier = modifier)
    } else {
        ExerciseDetail(
            exercise = exercise,
            state = progression[exercise.id] ?: ProgressionGate.initial(exercise),
            media = media[exercise.id],
            vm = vm,
            onBack = { selected = null },
            modifier = modifier,
        )
    }
}

// ---------------------------------------------------------------------- list

@Composable
private fun ExerciseList(progression: Map<String, ProgressionState>, onOpen: (String) -> Unit, modifier: Modifier) {
    val groups = remember { SeedData.exercises.groupBy { it.category } }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Exercise guide", style = MaterialTheme.typography.headlineMedium, color = Palette.Amber)
                Muted("Tap an exercise for the 3D clip, tension map, form checks and progression.")
            }
        }
        for (category in MovementCategory.entries) {
            val list = groups[category].orEmpty()
            if (list.isEmpty()) continue
            item(key = "header-${category.name}") {
                Text(category.displayName.uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.Cyan, modifier = Modifier.padding(top = 8.dp))
            }
            items(list, key = { it.id }) { exercise ->
                val level = progression[exercise.id]?.levelIndex ?: exercise.startLevel
                Panel(modifier = Modifier.clickable { onOpen(exercise.id) }) {
                    Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                    Muted(exercise.summary)
                    exercise.ladder.getOrNull(level)?.let { Pill("Level: ${it.name}", Palette.Amber) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- detail

@Composable
private fun ExerciseDetail(
    exercise: Exercise,
    state: ProgressionState,
    media: MediaOverrideEntity?,
    vm: GuideViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    var view by rememberSaveable(exercise.id) { mutableStateOf(ClipView.ORBIT) }
    var realTempo by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var playing by rememberSaveable(exercise.id) { mutableStateOf(true) }
    var threeQuarter by rememberSaveable(exercise.id) { mutableStateOf(true) }
    val progress = remember(exercise.id) { mutableFloatStateOf(0f) }
    val hasTempo = exercise.kind == ExerciseKind.REPS &&
        SeedData.steps.any { (it.exerciseId == exercise.id || it.exerciseByDay.values.contains(exercise.id)) && it.tempo != null }

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setMedia(exercise.id, uri.toString(), context.contentResolver.getType(uri) ?: "image/gif")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("< All exercises", style = MaterialTheme.typography.labelLarge, color = Palette.Cyan)
                }
                Text(exercise.name, style = MaterialTheme.typography.headlineMedium, color = Palette.Amber)
                Muted(exercise.summary)
            }
        }

        // ---- visual
        item {
            Panel {
                Box(modifier = Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111318))) {
                    if (media != null) {
                        MediaOverrideView(media.uri, media.mimeType, Modifier.fillMaxSize())
                    } else if (RigLibrary.animationOrNull(exercise.id) != null) {
                        ClipCanvas(
                            exercise.id,
                            Modifier.fillMaxSize(),
                            view = view,
                            realTempo = realTempo,
                            playing = playing,
                            onProgress = { progress.floatValue = it },
                        )
                    } else {
                        Muted("No animation for this exercise.", modifier = Modifier.padding(16.dp))
                    }
                }
                if (media == null) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in ClipView.entries) {
                            FilterChip(
                                selected = view == option,
                                onClick = { view = option },
                                label = { Text(option.title, modifier = Modifier.padding(vertical = 10.dp)) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("A", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                        LinearProgressIndicator(
                            progress = { progress.floatValue },
                            modifier = Modifier.weight(1f).height(8.dp),
                            color = Palette.Amber,
                            trackColor = Palette.SurfaceHigh,
                        )
                        Text("B", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { playing = !playing }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(if (playing) "Pause" else "Play", style = MaterialTheme.typography.labelLarge)
                        }
                        if (hasTempo) {
                            Switch(checked = realTempo, onCheckedChange = { realTempo = it })
                            Text("Real tempo", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlineBigButton(
                        if (media == null) "Use my own GIF / video" else "Replace my GIF / video",
                        onClick = { picker.launch(arrayOf("image/gif", "video/mp4")) },
                        modifier = Modifier.weight(1f),
                    )
                    if (media != null) {
                        OutlineBigButton("Back to 3D clip", onClick = { vm.clearMedia(exercise.id) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ---- tension map
        item { TensionLegend(exercise.highlights) }

        // ---- cues
        item {
            Panel {
                SectionTitle("Set-up")
                for (cue in exercise.setupCues) Text("- $cue", style = MaterialTheme.typography.bodyLarge)
                SectionTitle("Execution")
                for (cue in exercise.executionCues) Text("- $cue", style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Panel(color = MaterialTheme.colorScheme.primaryContainer) {
                Text("Protect your joints", style = MaterialTheme.typography.titleLarge, color = Palette.Amber)
                Text(exercise.jointProtection, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // ---- do / don't
        if (exercise.doDonts.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Form checks: do and don't")
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = threeQuarter, onClick = { threeQuarter = true }, label = { Text("3/4 view", modifier = Modifier.padding(vertical = 10.dp)) })
                        FilterChip(selected = !threeQuarter, onClick = { threeQuarter = false }, label = { Text("Side view", modifier = Modifier.padding(vertical = 10.dp)) })
                    }
                }
            }
            items(exercise.doDonts) { item -> DoDontCard(item, threeQuarter) }
        }

        // ---- progression
        item { ProgressionCard(exercise, state, vm) }
    }
}

// ---------------------------------------------------------------------- tension legend

private fun kindColor(kind: HighlightKind): Color = when (kind) {
    HighlightKind.MUSCLE -> Color(0xFFEF4444)
    HighlightKind.TENDON -> Color(0xFF38BDF8)
    HighlightKind.JOINT -> Color(0xFFFACC15)
}

private fun kindName(kind: HighlightKind): String = when (kind) {
    HighlightKind.MUSCLE -> "Muscle"
    HighlightKind.TENDON -> "Tendon"
    HighlightKind.JOINT -> "Joint"
}

@Composable
private fun TensionLegend(highlights: List<Highlight>) {
    Panel {
        SectionTitle("Where you feel it")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (kind in HighlightKind.entries) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(kindColor(kind)))
                    Text(kindName(kind), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Muted("Brighter means harder. The colours rise and fall with the movement, peaking where this exercise is hardest.")
        for (h in highlights) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.padding(top = 6.dp).size(12.dp).clip(CircleShape).background(kindColor(h.kind)))
                Column {
                    val peak = when (h.peak) {
                        PeakAt.START -> ", hardest at the start (Position A)"
                        PeakAt.END -> ", hardest at the end (Position B)"
                        PeakAt.STEADY -> ""
                    }
                    Text("${h.region.displayName}: ${h.load.label}$peak", style = MaterialTheme.typography.bodyLarge)
                    if (h.note.isNotBlank()) Muted(h.note)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- progression gatekeeper

@Composable
private fun ProgressionCard(exercise: Exercise, state: ProgressionState, vm: GuideViewModel) {
    val level = state.levelIndex.coerceIn(0, (exercise.ladder.size - 1).coerceAtLeast(0))
    val stages = remember(exercise.id, level) { ProgressionGate.stages(exercise, level) }
    Panel {
        SectionTitle("Progression gatekeeper")
        if (exercise.ladder.isNotEmpty()) {
            Muted("Ladder, easiest to hardest")
            exercise.ladder.forEachIndexed { i, step ->
                val current = i == level
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge, color = if (current) Palette.Amber else Palette.Muted)
                    Column {
                        Text(
                            step.name + if (current) "   (you are here)" else "",
                            style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            color = if (current) Palette.Amber else Color.White,
                        )
                        if (current) Muted(step.description)
                    }
                }
            }
        }
        Muted("Move up only after the first three stages are ticked. Each unlock resets them for the new level.")
        for (info in stages) {
            if (info.number <= 3) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { vm.toggleStage(exercise, info.number) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = ProgressionGate.isDone(state.stageMask, info.number),
                        onCheckedChange = { vm.toggleStage(exercise, info.number) },
                        colors = CheckboxDefaults.colors(checkedColor = Palette.Green),
                    )
                    Column {
                        Text("Stage ${info.number}: ${info.title}", style = MaterialTheme.typography.titleMedium)
                        Muted(info.description)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stage 4: ${info.title}", style = MaterialTheme.typography.titleMedium)
                    Muted(info.description)
                    val ready = ProgressionGate.readyToUnlock(state.stageMask)
                    if (ProgressionGate.hasNextLevel(exercise, level)) {
                        BigButton(
                            if (ready) "Unlock: ${exercise.ladder[level + 1].name}" else "Tick stages 1 to 3 to unlock",
                            onClick = { vm.unlockNextLevel(exercise) },
                            enabled = ready,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
