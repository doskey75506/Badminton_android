package com.badminton.club

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class HelpSection(val title: String, val lines: List<String>)

@Composable
fun HelpPage(state: AppState) {
    val sections = listOf(
        HelpSection(
            T("help_tonight_title"),
            listOf(
                T("help_tonight_1"),
                T("help_tonight_2"),
                T("help_tonight_3"),
                T("help_tonight_4"),
                T("help_tonight_5"),
                T("help_tonight_note"),
            ),
        ),
        HelpSection(
            T("help_members_title"),
            listOf(
                T("help_members_1"),
                T("help_members_2"),
            ),
        ),
        HelpSection(
            T("help_standings_title"),
            listOf(
                T("help_standings_1"),
                T("help_standings_2"),
            ),
        ),
        HelpSection(
            T("help_history_title"),
            listOf(
                T("help_history_1"),
            ),
        ),
        HelpSection(
            T("help_lang_title"),
            listOf(
                T("help_lang_1"),
            ),
        ),
        HelpSection(
            T("help_data_title"),
            listOf(
                T("help_data_1"),
                T("help_data_2"),
                T("help_data_3"),
            ),
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                T("p_help"),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item { Text(T("help_intro"), color = MaterialTheme.colorScheme.outline) }
        sections.forEach { s ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    s.lines.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
