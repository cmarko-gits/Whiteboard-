package dev.whiteboard.core.model

/** Stable semantic keys. Android resource lookup stays in presentation. */
enum class TemplateLabel(val english: String) {
    PAGE_ONE("Page 1"),
    BRAINSTORM_TITLE("What could we create?"),
    IDEAS("Ideas"),
    QUESTIONS("Questions"),
    NEXT_STEPS("Next steps"),
    ADD_THOUGHTS("Add your thoughts here"),
    KANBAN_TITLE("Make progress visible"),
    TO_DO("To do"),
    IN_PROGRESS("In progress"),
    DONE("Done"),
    NEW_TASK("New task"),
    WEEKLY_TITLE("A week with intention"),
    MONDAY("Mon"),
    TUESDAY("Tue"),
    WEDNESDAY("Wed"),
    THURSDAY("Thu"),
    FRIDAY("Fri"),
    SATURDAY("Sat"),
    SUNDAY("Sun"),
    STUDY_TITLE("Study notes"),
    KEY_CONCEPTS("Key concepts"),
    NOTES_EXAMPLES("Notes & examples"),
    SUMMARY_QUESTIONS("Summary & questions"),
    MIND_MAP_TITLE("Explore an idea"),
    CENTRAL_IDEA("Central idea"),
    PEOPLE("People"),
    POSSIBILITIES("Possibilities"),
    ACTIONS("Actions"),
    FLOWCHART_TITLE("From start to finish"),
    START("Start"),
    PROCESS("Process"),
    FINISH("Finish"),
    ARCHITECTURE_TITLE("System architecture"),
    MOBILE_APP("Mobile app"),
    API("API"),
    DATABASE("Database"),
    DECISIONS_CONSTRAINTS("Decisions & constraints")
}

/** A captured language, used only when creating a new document. Never relabel existing user text. */
class TemplateContent(labels: Map<TemplateLabel, String>) {
    private val labels = labels.toMap()
    init {
        require(this.labels.keys == TemplateLabel.entries.toSet())
        require(this.labels.values.all { it.isNotBlank() && it.length <= 120 })
    }
    operator fun get(label: TemplateLabel): String = labels.getValue(label)
    companion object {
        val ENGLISH = TemplateContent(TemplateLabel.entries.associateWith { it.english })
    }
}
