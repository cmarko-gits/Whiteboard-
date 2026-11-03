package dev.whiteboard.core.model

import dev.whiteboard.core.model.TemplateLabel.*

enum class BoardTemplate { BLANK, GRID, DOTS, RULED, BRAINSTORM, KANBAN, WEEKLY, STUDY, MIND_MAP, FLOWCHART, ARCHITECTURE }

object TemplateFactory {
    fun create(name: String, template: BoardTemplate, content: TemplateContent = TemplateContent.ENGLISH): Board {
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
                title(content[BRAINSTORM_TITLE], 40f, 30f)
                listOf(IDEAS, QUESTIONS, NEXT_STEPS).forEachIndexed { index, label ->
                    title(content[label], 40f + index * 300, 140f, 270f)
                    note(content[ADD_THOUGHTS], 40f + index * 300, 240f)
                    note("", 40f + index * 300, 450f, 0xFFD5E7CF.toInt())
                }
            }
            BoardTemplate.KANBAN -> {
                title(content[KANBAN_TITLE], 40f, 30f)
                listOf(TO_DO, IN_PROGRESS, DONE).forEachIndexed { index, label ->
                    title(content[label], 40f + index * 300, 140f, 270f)
                    note(
                        content[NEW_TASK], 40f + index * 300, 240f,
                        listOf(0xFFF4E6A3.toInt(), 0xFFD4E3F3.toInt(), 0xFFD5E7CF.toInt())[index]
                    )
                }
            }
            BoardTemplate.WEEKLY -> {
                title(content[WEEKLY_TITLE], 40f, 30f)
                listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY).forEachIndexed { index, label ->
                    note(content[label], 40f + index * 170, 150f, 0xFFD5E7CF.toInt(), 150f, 430f)
                }
            }
            BoardTemplate.STUDY -> {
                title(content[STUDY_TITLE], 40f, 30f)
                note(content[KEY_CONCEPTS], 40f, 150f, 0xFFD4E3F3.toInt(), 280f, 450f)
                note(content[NOTES_EXAMPLES], 350f, 150f, 0xFFF4E6A3.toInt(), 580f, 450f)
                note(content[SUMMARY_QUESTIONS], 40f, 640f, 0xFFD5E7CF.toInt(), 890f, 180f)
            }
            BoardTemplate.MIND_MAP -> {
                title(content[MIND_MAP_TITLE], 40f, 30f)
                listOf(100f to 170f, 780f to 170f, 100f to 540f, 780f to 540f).forEach { (x, y) -> arrow(650f, 430f, x + 120f, y + 80f) }
                note(content[CENTRAL_IDEA], 440f, 350f, 0xFFD5E7CF.toInt())
                listOf(PEOPLE, POSSIBILITIES, QUESTIONS, ACTIONS).forEachIndexed { index, label ->
                    val location = listOf(100f to 170f, 780f to 170f, 100f to 540f, 780f to 540f)[index]
                    note(content[label], location.first, location.second, 0xFFD4E3F3.toInt())
                }
            }
            BoardTemplate.FLOWCHART -> {
                title(content[FLOWCHART_TITLE], 40f, 30f)
                note(content[START], 360f, 130f, 0xFFD5E7CF.toInt())
                arrow(480f, 310f, 480f, 370f)
                note(content[PROCESS], 360f, 390f, 0xFFD4E3F3.toInt())
                arrow(480f, 570f, 480f, 630f)
                note(content[FINISH], 360f, 650f, 0xFFD5E7CF.toInt())
            }
            BoardTemplate.ARCHITECTURE -> {
                title(content[ARCHITECTURE_TITLE], 40f, 30f)
                note(content[MOBILE_APP], 40f, 200f, 0xFFD5E7CF.toInt())
                arrow(290f, 290f, 360f, 290f)
                note(content[API], 380f, 200f, 0xFFD4E3F3.toInt())
                arrow(630f, 290f, 700f, 290f)
                note(content[DATABASE], 720f, 200f, 0xFFE6D9EE.toInt())
                note(content[DECISIONS_CONSTRAINTS], 40f, 470f, 0xFFF4E6A3.toInt(), 920f, 200f)
            }
        }
        val background = when (template) {
            BoardTemplate.GRID -> Background.GRID
            BoardTemplate.DOTS -> Background.DOTS
            BoardTemplate.RULED -> Background.RULED
            else -> Background.PLAIN
        }
        return Board(name = name, pages = listOf(Page(name = content[PAGE_ONE], background = background, elements = objects)))
    }
}
