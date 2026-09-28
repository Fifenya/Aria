package com.aria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aria.stats.AriaStats
import com.aria.train.ModelSummary
import com.aria.train.TrainState

@Composable
fun StatsPanel(
    stats: AriaStats,
    trainState: TrainState,
    summary: ModelSummary,
    modifier: Modifier = Modifier,
) {
    val c = LocalAriaColors.current

    Column(
        modifier
            .background(c.panel)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text("STATS", color = c.accent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        // --- Система ---
        SectionLabel("SYSTEM", c)
        StatLine("CPU", "${"%.0f".format(stats.cpuPercent)}%")
        StatLine(
            "RAM",
            "${"%.1f".format(stats.ramUsedGb)}/${"%.1f".format(stats.ramTotalGb)}G",
        )
        StatLine(
            "BAT",
            if (stats.batPercent >= 0) {
                "${stats.batPercent}%" + if (stats.batCharging) "+" else ""
            } else "—",
        )
        StatLine("TEMP", "${"%.1f".format(stats.batTempC)}C")

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        // --- Модель ---
        SectionLabel("MODEL", c)
        StatLine("NAME", summary.label.substringBefore(":"))
        StatLine("PARAMS", formatCount(summary.params))
        StatLine("VOCAB", summary.vocab.toString())
        StatLine("ALIVE", "${summary.alive}/${summary.vocab}")
        StatLine("EMB", summary.embDim.toString())
        StatLine("HIDDEN", summary.hidden.toString())
        StatLine("MELODIES", summary.melodies.toString())
        StatLine("TOKENS", formatCount(summary.tokenCount))

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        // --- Обучение ---
        SectionLabel("TRAIN", c)
        StatLine("EPOCH", trainState.epoch.toString())
        StatLine("STEP", formatCount(trainState.step))
        StatLine("LOSS", if (trainState.epoch > 0) "%.4f".format(trainState.loss) else "—")
        StatLine(
            "BEST",
            if (trainState.bestLoss < Float.MAX_VALUE) "%.4f".format(trainState.bestLoss) else "—",
        )
        StatLine("STATE", if (trainState.running) "RUN" else "IDLE")

        Spacer(Modifier.height(8.dp))
        Text("LOSS", color = c.accent, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 6.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(c.bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (trainState.epoch > 0) "%.4f".format(trainState.loss) else "no data",
                color = if (trainState.epoch > 0) c.accent else c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = if (trainState.epoch > 0) 16.sp else 10.sp,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, c: AriaColors) {
    Text(text, color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
}

@Composable
private fun StatLine(name: String, value: String) {
    val c = LocalAriaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text(value, color = c.text, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

/** 7840 → 7.8K, 2300000 → 2.3M, 1200000000 → 1.2B */
private fun formatCount(n: Long): String = when {
    n >= 1_000_000_000L -> "%.1fB".format(n / 1_000_000_000.0)
    n >= 1_000_000L -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000L -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}