     skipped++
                continue
            }

            try {
                val parsed = MidiReader.parse(f)
                if (parsed.notes.isEmpty()) {
                    lines.add("midi empty: ${f.name}")
                    continue
                }

                // Режем на куски по 64 ноты
                var i = 0
                var chunks = 0
                while (i < parsed.notes.size) {
                    val end = minOf(i + 64, parsed.notes.size)
                    val slice = parsed.notes.subList(i, end).toIntArray()
                    if (slice.size >= 4) {
                        addMelody(slice)
                        addedNotes += slice.size
                        chunks++
                    }
                    i = end
                }

                seen[f.name] = size
                newFiles++
                lines.add(
                    "imported ${f.name}  notes=${parsed.notes.size}  " +
                            "chunks=$chunks  tracks=${parsed.trackCount}"
                )
            } catch (e: Exception) {
                lines.add("failed ${f.name}: ${e.message}")
            }
        }

        // Сохраняем трекер
        try {
            trackerFile.writeText(
                seen.entries.joinToString("\n") { "${it.key}\t${it.value}" }
            )
        } catch (_: Exception) {}

        if (newFiles > 0) {
            lines.add(
                "midi import done: new=$newFiles skipped=$skipped " +
                        "added=$addedNotes notes  melodies=${raw.size}"
            )
        } else {
            lines.add("midi import: nothing new (skipped=$skipped)")
        }

        return lines
    }
}