package dev.whiteboard.core.model

enum class BoardTemplate { BLANK, GRID, DOTS, RULED, BRAINSTORM, KANBAN, WEEKLY, STUDY, MIND_MAP, FLOWCHART, ARCHITECTURE }

object TemplateFactory {
    fun create(name: String, template: BoardTemplate): Board {
        val objects = mutableListOf<Element>()
        fun title(text: String, x: Float, y: Float, width: Float = 900f) {
            objects.add(TextElement(bounds = Bounds(x, y, x + width, y + 70f), text = text, fontSize = 32f, bold = true))
        }
        fun note(text: String, x: Float, y: Float, color: Int = 0xFFF4E6A3.toInt(), width: Float = 240f, height: Float = 180f) {
            objects.add(Note(bounds = Bounds(x, y, x + width, y + height), text = text, color = color))
        }
        fun arrow(x: Float, y: Float, toX: Float, toY: Float) {
            objects.add(Line(start = Point(x, y), end = Point(toX, toY), style = InkStyle(color = 0xFF315D4C.toInt()), arrow = true))
        }
        when (template) {
            BoardTemplate.BLANK, BoardTemplate.GRID, BoardTemplate.DOTS, BoardTemplate.RULED -> Unit
            BoardTemplate.BRAINSTORM -> {
                title("What could we create?", 40f, 30f)
                listOf("Ideas", "Questions", "Next steps").forEachIndexed { index, text ->
                    title(text, 40f + index * 300, 140f, 270f)
                    note("Add your thoughts here", 40f + index * 300, 240f)
                    note("", 40f + index * 300, 450f, 0xFFD5E7CF.toInt())
                }
            }
            BoardTemplate.KANBAN -> {
                title("Make progress visible", 40f, 30f)
                listOf("To do", "In progress", "Done").forEachIndexed { index, text ->
                    title(text, 40f + index * 300, 140f, 270f)
                    note("New task", 40f + index * 300, 240f, listOf(0xFFF4E6A3.toInt(), 0xFFD4E3F3.toInt(), 0xFFD5E7CF.toInt())[index])
                }
            }
            BoardTemplate.WEEKLY -> {
                title("A week with intention", 40f, 30f)
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { index, text ->
                    note(text, 40f + index * 170, 150f, 0xFFD5E7CF.toInt(), 150f, 430f)
                }
            }
            BoardTemplate.STUDY -> {
                title("Study notes", 40f, 30f)
                note("Key concepts", 40f, 150f, 0xFFD4E3F3.toInt(), 280f, 450f)
                note("Notes & examples", 350f, 150f, 0xFFF4E6A3.toInt(), 580f, 450f)
                note("Summary & questions", 40f, 640f, 0xFFD5E7CF.toInt(), 890f, 180f)
            }
            BoardTemplate.MIND_MAP -> {
                title("Explore an idea", 40f, 30f)
                listOf(100f to 170f, 780f to 170f, 100f to 540f, 780f to 540f).forEach { (x, y) -> arrow(650f, 430f, x + 120f, y + 80f) }
                note("Central idea", 440f, 350f, 0xFFD5E7CF.toInt())
                listOf("People", "Possibilities", "Questions", "Actions").forEachIndexed { index, label ->
                    val location = listOf(100f to 170f, 780f to 170f, 100f to 540f, 780f to 540f)[index]
                    note(label, location.first, location.second, 0xFFD4E3F3.toInt())
                }
            }
            BoardTemplate.FLOWCHART -> {
                title("From start to finish", 40f, 30f)
                note("Start", 360f, 130f, 0xFFD5E7CF.toInt())
                arrow(480f, 310f, 480f, 370f)
                note("Process", 360f, 390f, 0xFFD4E3F3.toInt())
                arrow(480f, 570f, 480f, 630f)
                note("Finish", 360f, 650f, 0xFFD5E7CF.toInt())
            }
            BoardTemplate.ARCHITECTURE -> {
                title("System architecture", 40f, 30f)
                note("Mobile app", 40f, 200f, 0xFFD5E7CF.toInt())
                arrow(290f, 290f, 360f, 290f)
                note("API", 380f, 200f, 0xFFD4E3F3.toInt())
                arrow(630f, 290f, 700f, 290f)
                note("Database", 720f, 200f, 0xFFE6D9EE.toInt())
                note("Decisions & constraints", 40f, 470f, 0xFFF4E6A3.toInt(), 920f, 200f)
            }
        }
        val background = when (template) {
            BoardTemplate.GRID -> Background.GRID
            BoardTemplate.DOTS -> Background.DOTS
            BoardTemplate.RULED -> Background.RULED
            else -> Background.PLAIN
        }
        return Board(name = name, pages = listOf(Page(background = background, elements = objects)))
    }
}
