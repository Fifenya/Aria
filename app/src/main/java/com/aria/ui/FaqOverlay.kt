package com.aria.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FaqOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val c = LocalAriaColors.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) +
                scaleIn(
                    initialScale = 0.15f,
                    animationSpec = tween(280),
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
        exit = fadeOut(animationSpec = tween(180)) +
                scaleOut(
                    targetScale = 0.15f,
                    animationSpec = tween(220),
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            // stopPropagation: клик по самой карточке не закрывает её
            Box(
                Modifier
                    .padding(20.dp)
                    .clickable(enabled = false) {},
            ) {
                Surface(
                    color = c.panel,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                    ) {
                        // Заголовок + крестик
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "FAQ",
                                color = c.accent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            AriaIconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp),
                            ) {
                                IconClose(c.accent, size = 18.dp)
                            }
                        }

                        HorizontalDivider(
                            color = c.line,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )

                        Column(
                            Modifier
                                .heightIn(max = 460.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            FaqSection("Кнопки", c)
                            FaqItem(
                                "▶ Train",
                                "Запускает обучение. Сеть крутит эпохи, loss падает. " +
                                        "Обучение идёт в фоновом потоке — можно сворачивать.",
                                c,
                            )
                            FaqItem(
                                "⏸ Pause",
                                "Останавливает обучение и автоматически сохраняет модель. " +
                                        "При следующем Train обучение продолжится с той же эпохи.",
                                c,
                            )
                            FaqItem(
                                "⬇ Save",
                                "Ручное сохранение модели прямо сейчас, без паузы. " +
                                        "Если эпоха не изменилась с прошлого сохранения — сообщит " +
                                        "«unchanged» и ничего не запишет.",
                                c,
                            )
                            FaqItem(
                                "♪ Play",
                                "Генерирует мелодию текущей моделью и проигрывает её " +
                                        "через динамик. Параметры берутся из настроек.",
                                c,
                            )

                            Spacer(Modifier.height(4.dp))
                            FaqSection("Параметры генерации", c)
                            FaqItem(
                                "temperature",
                                "Управляет случайностью. Низкие значения (0.3–0.7) — сеть " +
                                        "повторяет выученное, стабильно и предсказуемо. " +
                                        "Высокие (1.0–2.0) — эксперименты, хаос, сюрпризы.",
                                c,
                            )
                            FaqItem(
                                "note ms",
                                "Длительность одной ноты при воспроизведении в миллисекундах. " +
                                        "100 мс — быстрое стаккато. 1000 мс — медленное легато.",
                                c,
                            )
                            FaqItem(
                                "length",
                                "Сколько нот генерировать при нажатии Play. " +
                                        "16 — короткая фраза, 128 — почти полноценная пьеса.",
                                c,
                            )

                            Spacer(Modifier.height(4.dp))
                            FaqSection("Тема и панель", c)
                            FaqItem(
                                "Theme",
                                "Цветовая схема интерфейса. Выбор сохраняется между запусками.",
                                c,
                            )
                            FaqItem(
                                "MODEL / PARAMS",
                                "Имя модели (aria + версия) и количество обучаемых параметров. " +
                                        "Сейчас — 2.3k, что означает ~2300 весов.",
                                c,
                            )
                            FaqItem(
                                "LOSS / BEST / EPOCH / STEP",
                                "LOSS — текущая ошибка (чем ниже, тем лучше выучено). " +
                                        "BEST — минимальная ошибка за всё время. " +
                                        "EPOCH — сколько раз сеть прошла по датасету. " +
                                        "STEP — сколько шагов обучения сделано.",
                                c,
                            )

                            Spacer(Modifier.height(4.dp))
                            FaqSection("Хранение", c)
                            FaqItem(
                                "Модель",
                                "Сохраняется как aria1:2.3k.bin в папке Projects/Aria models. " +
                                        "На каждый 500-й epoch и на паузу — автоматически. " +
                                        "При старте подхватывается автоматически.",
                                c,
                            )
                            FaqItem(
                                "Лог",
                                "Рядом пишется log_YYYYMMDD_HHMMSS.txt со всеми эпохами " +
                                        "и сэмплами. Можно открыть в любом текстовом редакторе.",
                                c,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqSection(title: String, c: AriaColors) {
    Text(
        title.uppercase(),
        color = c.accent,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun FaqItem(title: String, body: String, c: AriaColors) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = c.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            body,
            color = c.dim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}