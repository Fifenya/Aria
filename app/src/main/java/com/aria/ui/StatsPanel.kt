package com.aria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aria.stats.AriaStats
import com.aria.train.TrainState

@Composable
fun StatsPanel(
    stats: AriaStats,
    trainState: TrainState,
    modelLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalAriaColors.current
    Column(
        modifier
            .background(c.panel)
            .padding(12.dp),
    ) {
        Text("STATS", color = c.accent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        StatLine("CPU", "${"%.0f".format(stats.cpuPercent)}%")
        StatLine(
            "RAM",
            "${"%.1f".format(stats.ramUsedGb)}/${"%.1f".format(stats.ramTotalGb)}G",
        )
        StatLine(
            "BAT",
            if (stats.batPercent >= 0) {
                "${stats.batPercent}%" + if (stats.batCharging) "+" else ""
            } else "—"
        )
        StatLine("TEMP", "${"%.1f".format(stats.batTempC)}C")

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        StatLine("MODEL", modelLabel.substringBefore(":"))
        StatLine("PARAMS", modelLabel.substringAfter(":"))

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        StatLine("EPOCH", trainState.epoch.toString())
        StatLine("STEP", trainState.step.toString())
        StatLine("LOSS", if (trainState.epoch > 0) "%.4f".format(trainState.loss) else "—")
        StatLine(
            "BEST",
            if (trainState.bestLoss < Float.MAX_VALUE) "%.4f".format(trainState.bestLoss) else "—"
        )
        StatLine("STATE", if (trainState.running) "RUN" else "IDLE")

        Spacer(Modifier.height(16.dp))
        Text("LOSS CHART", color = c.accent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(c.bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (trainState.epoch > 0) "%.4f".format(trainState.loss) else "no data",
                color = if (trainState.epoch > 0) c.accent else c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = if (trainState.epoch > 0) 18.sp else 10.sp,
            )
        }
    }
}

@Composable
private fun StatLine(name: String, value: String) {
    val c = LocalAriaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(value, color = c.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}