package com.personal.calisthenicsguide.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.calisthenics.core.analytics.ProgressionGate
import com.personal.calisthenics.core.analytics.ProgressionState
import com.personal.calisthenics.core.model.Exercise
import com.personal.calisthenicsguide.ui.theme.AppColors

/** The 4-stage progression gatekeeper plus the exercise's leverage ladder. */
@Composable
fun ProgressionMatrix(
    exercise: Exercise,
    state: ProgressionState,
    stage1Suggested: Boolean,
    onChange: (ProgressionState) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val stages = ProgressionGate.stages(exercise, state.levelIndex)
    val current = exercise.ladder.getOrNull(state.levelIndex)
    val next = exercise.ladder.getOrNull(state.levelIndex + 1)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Current level: ${current?.name ?: "-"}", style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
        current?.let { Text(it.description, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary) }

        stages.take(3).forEach { stage ->
            val done = ProgressionGate.isDone(state.stageMask, stage.number)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = done,
                    onCheckedChange = { onChange(state.copy(stageMask = ProgressionGate.toggle(state.stageMask, stage.number))) },
                    modifier = Modifier.size(48.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text("Stage ${stage.number}: ${stage.title}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(stage.description, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    if (stage.number == 1 && stage1Suggested && !done) {
                        Text(
                            "Your last session logged 3+ clean sets of this - tick it if they felt controlled.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Good,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        val ready = ProgressionGate.readyToUnlock(state.stageMask) && next != null
        val stage4 = stages[3]
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = false, onCheckedChange = null, enabled = false, modifier = Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text("Stage 4: ${stage4.title}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(stage4.description, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
        }
        if (ready) {
            Button(
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Good, contentColor = AppColors.AccentOn),
            ) { Text("Unlock: ${next?.name}", style = MaterialTheme.typography.titleMedium) }
        } else if (next != null) {
            Text("Tick Stages 1-3 to unlock ${next.name}.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        }

        Text("Leverage ladder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        exercise.ladder.forEachIndexed { index, level ->
            val here = index == state.levelIndex
            Text(
                (if (here) "> " else if (index < state.levelIndex) "done  " else "      ") + "${index + 1}. ${level.name}",
                color = when {
                    here -> AppColors.Accent
                    index < state.levelIndex -> AppColors.Good
                    else -> AppColors.TextSecondary
                },
                fontWeight = if (here) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    if (confirm && next != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Advance to ${next.name}?") },
            text = { Text("The three stage checkboxes reset so you can master the harder leverage the same way.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onChange(ProgressionGate.advance(exercise, state))
                }) { Text("Unlock") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not yet") } },
        )
    }
}
