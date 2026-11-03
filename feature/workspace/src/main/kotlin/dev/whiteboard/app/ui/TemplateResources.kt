package dev.whiteboard.app.ui

import android.content.res.Resources
import dev.whiteboard.core.model.TemplateContent
import dev.whiteboard.core.model.TemplateLabel
import dev.whiteboard.core.model.TemplateLabel.*
import dev.whiteboard.ui.R

/** Capture the current app resource language before starting document creation. */
fun localizedTemplateContent(resources: Resources): TemplateContent = TemplateContent(
    TemplateLabel.entries.associateWith { label ->
        resources.getString(
            when (label) {
                PAGE_ONE -> R.string.template_content_page_one
                BRAINSTORM_TITLE -> R.string.template_content_brainstorm_title
                IDEAS -> R.string.template_content_ideas
                QUESTIONS -> R.string.template_content_questions
                NEXT_STEPS -> R.string.template_content_next_steps
                ADD_THOUGHTS -> R.string.template_content_add_thoughts
                KANBAN_TITLE -> R.string.template_content_kanban_title
                TO_DO -> R.string.template_content_to_do
                IN_PROGRESS -> R.string.template_content_in_progress
                DONE -> R.string.template_content_done
                NEW_TASK -> R.string.template_content_new_task
                WEEKLY_TITLE -> R.string.template_content_weekly_title
                MONDAY -> R.string.template_content_monday
                TUESDAY -> R.string.template_content_tuesday
                WEDNESDAY -> R.string.template_content_wednesday
                THURSDAY -> R.string.template_content_thursday
                FRIDAY -> R.string.template_content_friday
                SATURDAY -> R.string.template_content_saturday
                SUNDAY -> R.string.template_content_sunday
                STUDY_TITLE -> R.string.template_content_study_title
                KEY_CONCEPTS -> R.string.template_content_key_concepts
                NOTES_EXAMPLES -> R.string.template_content_notes_examples
                SUMMARY_QUESTIONS -> R.string.template_content_summary_questions
                MIND_MAP_TITLE -> R.string.template_content_mind_map_title
                CENTRAL_IDEA -> R.string.template_content_central_idea
                PEOPLE -> R.string.template_content_people
                POSSIBILITIES -> R.string.template_content_possibilities
                ACTIONS -> R.string.template_content_actions
                FLOWCHART_TITLE -> R.string.template_content_flowchart_title
                START -> R.string.template_content_start
                PROCESS -> R.string.template_content_process
                FINISH -> R.string.template_content_finish
                ARCHITECTURE_TITLE -> R.string.template_content_architecture_title
                MOBILE_APP -> R.string.template_content_mobile_app
                API -> R.string.template_content_api
                DATABASE -> R.string.template_content_database
                DECISIONS_CONSTRAINTS -> R.string.template_content_decisions_constraints
            }
        )
    }
)
