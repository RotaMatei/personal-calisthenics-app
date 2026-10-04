package com.personal.calisthenicsguide.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.calisthenics.core.analytics.ProgressionGate
import com.personal.calisthenics.core.analytics.ProgressionState
import com.personal.calisthenics.core.model.DoDont
import com.personal.calisthenics.core.model.Exercise
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.model.HighlightKind
import com.personal.calisthenics.core.model.MovementCategory
import com.personal.calisthenics.core.model.PeakAt
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.Stills
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.ui.components.AppCard
import com.personal.calisthenicsguide.ui.components.ScreenTitle
import com.personal.calisthenicsguide.ui.components.SectionLabel
import com.personal.calisthenicsguide.ui.theme.AppColors
import com.personal.calisthenicsguide.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
fun GuideScreen(repository: Repository, modifier: Modifier = Modifier) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val exercise = selectedId?.let { SeedData.exerciseOrNull(it) }
    BackHandler(enabled = exercise != null) { selectedId = null }
    if (exercise == null) {
        ExerciseList(onOpen = { selectedId = it }, modifier = modifier)
    } else {
        ExerciseDetail(exercise, repository, onBack = { selectedId = null }, modifier = modifier)
    }
}

// ------------------------------------------------------------------------------------------------ list

@Composable
private fun ExerciseList(onOpen: (String) -> Unit, modifier: Modifier) {
    val grouped = remember { SeedData.exercises.groupBy { it.category }.toSortedMap(compareBy<MovementCategory> { it.ordinal }) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item {
            ScreenTitle(
                "Exercise guide",
                "${SeedData.exercises.size} exercises with looping 3D clips, DO / DON'T cards and a progression matrix.",
            )
        }
        grouped.forEach { (category: MovementCategory, list: List<Exercise>) ->
            item(key = "header_${category.name}") {
                SectionLabel(category.displayName, Modifier.padding(top = Space.lg))
            }
            items(list, key = { it.id }) { ex ->
                AppCard(onClick = { onOpen(ex.id) }) {
                    Text(ex.name, style = MaterialTheme.typography.titleMedium)
                    Text(ex.summary, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary, maxLines = 2)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ detail

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Space.xl, bottom = Space.xs))
}

@Composable
private fun ExerciseDetail(exercise: Exercise, repository: Repository, onBack: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val progression by repository.progression.collectAsState(initial = emptyMap())
    val logged by repository.loggedSets.collectAsState(initial = emptyList())
    val media by repository.mediaOverrides.collectAsState(initial = emptyMap())

    val state = progression[exercise.id] ?: ProgressionGate.initial(exercise)
    val stage1Hint = remember(logged, exercise.id) {
        val mine = logged.filter { it.exerciseId == exercise.id }
        val latest = mine.maxByOrNull { it.epochMs }?.sessionId
        latest != null && ProgressionGate.stage1Suggested(exercise, mine.filter { it.sessionId == latest })
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                Text(exercise.category.displayName, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(exercise.summary, style = MaterialTheme.typography.bodyLarge)

        SectionTitle("3D demonstration")
        MediaOverrideSection(
            override = media[exercise.id],
            onPick = { uri, mime -> scope.launch { repository.setMedia(exercise.id, uri, mime) } },
            onClear = { scope.launch { repository.clearMedia(exercise.id) } },
        ) { ClipView(exercise.id, exercise.highlights) }

        SectionTitle("Where the stress is")
        HighlightLegend(exercise.highlights)

        SectionTitle("Setup")
        exercise.setupCues.forEach { Bullet(it) }
        SectionTitle("Execution")
        exercise.executionCues.forEach { Bullet(it) }
        SectionTitle("Joint protection")
        Text(exercise.jointProtection, style = MaterialTheme.typography.bodyLarge)

        if (exercise.doDonts.isNotEmpty()) {
            SectionTitle("DO vs DON'T")
            exercise.doDonts.forEach { DoDontCard(it, exercise.highlights) }
        }

        SectionTitle("Progression matrix")
        ProgressionMatrix(
            exercise = exercise,
            state = state,
            stage1Suggested = stage1Hint,
            onChange = { updated: ProgressionState -> scope.launch { repository.saveProgression(updated) } },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("-  ", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun kindColor(kind: HighlightKind): Color = when (kind) {
    HighlightKind.MUSCLE -> AppColors.Muscle
    HighlightKind.TENDON -> AppColors.Tendon
    HighlightKind.JOINT -> AppColors.Joint
}

private fun kindLabel(kind: HighlightKind): String = when (kind) {
    HighlightKind.MUSCLE -> "Muscle"
    HighlightKind.TENDON -> "Tendon"
    HighlightKind.JOINT -> "Joint"
}

private fun peakLabel(peak: PeakAt): String = when (peak) {
    PeakAt.STEADY -> "steady through the rep"
    PeakAt.START -> "peaks at the start position (A)"
    PeakAt.END -> "peaks at the end of the range (B)"
}

@Composable
private fun HighlightLegend(highlights: List<Highlight>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HighlightKind.entries.forEach { kind ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(kindColor(kind)))
                    Spacer(Modifier.size(6.dp))
                    Text(kindLabel(kind), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            "Stronger colour = higher load. The colours pulse with the clip, brightest where that region is loaded most " +
                "in this exact exercise.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
        highlights.forEach { h ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 5.dp).size(14.dp).clip(CircleShape).background(kindColor(h.kind)))
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        "${h.region.displayName} - ${kindLabel(h.kind).lowercase()}, ${h.load.label.lowercase()} load",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(peakLabel(h.peak), style = MaterialTheme.typography.bodyMedium, color = AppColors.Info)
                    if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                }
            }
        }
        Text(
            "This is a general biomechanics and rehab-literature mapping, not measured data for you, and not medical advice.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Warn,
        )
    }
}

@Composable
private fun DoDontCard(card: DoDont, highlights: List<Highlight>) {
    var camera by remember { mutableStateOf(Camera.THREE_QUARTER) }
    val isDetail = card.wrongPoseKey.startsWith("detail.")
    AppCard(Modifier.padding(vertical = Space.xs)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            if (isDetail) {
                Text(card.wrongLabel, color = AppColors.Danger, style = MaterialTheme.typography.titleMedium)
                DetailView(card.wrongPoseKey)
                Text(card.wrongText, style = MaterialTheme.typography.bodyMedium)
                Text(card.wrongConsequence, style = MaterialTheme.typography.bodyMedium, color = AppColors.Danger)
                Text(card.rightLabel, color = AppColors.Good, style = MaterialTheme.typography.titleMedium)
                if (card.rightPoseKey != card.wrongPoseKey) DetailView(card.rightPoseKey)
                Text(card.rightText, style = MaterialTheme.typography.bodyMedium)
            } else {
                val keys = listOf(card.wrongPoseKey, card.rightPoseKey)
                val bounds = remember(camera, keys) { Stills.bounds(keys, camera) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = camera == Camera.THREE_QUARTER, onClick = { camera = Camera.THREE_QUARTER }, label = { Text("3/4 view") }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(selected = camera == Camera.SIDE, onClick = { camera = Camera.SIDE }, label = { Text("Side view") }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(selected = camera == Camera.FRONT, onClick = { camera = Camera.FRONT }, label = { Text("Front view") }, modifier = Modifier.heightIn(min = 48.dp))
                }
                Text("WRONG: ${card.wrongLabel}", color = AppColors.Danger, style = MaterialTheme.typography.titleMedium)
                StillView(card.wrongPoseKey, camera, bounds, highlights)
                Text(card.wrongText, style = MaterialTheme.typography.bodyMedium)
                Text(card.wrongConsequence, style = MaterialTheme.typography.bodyMedium, color = AppColors.Danger)
                Text("CORRECT: ${card.rightLabel}", color = AppColors.Good, style = MaterialTheme.typography.titleMedium)
                StillView(card.rightPoseKey, camera, bounds, highlights)
                Text(card.rightText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
