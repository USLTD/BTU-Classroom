package ge.usltd.btu_classroom.core.portal

import android.content.Context
import ge.usltd.btu_classroom.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Localizes known interface labels without changing cached university data or change detection. */
object PortalTerminology {
  fun labelResource(source: String): Int? = when (normalize(source)) {
    "საგანი", "კურსის დასახელება", "course", "course title", "subject" -> R.string.portal_field_course
    "დღე", "day" -> R.string.portal_field_day
    "საათი", "დრო", "time" -> R.string.portal_field_time
    "აუდიტორია", "classroom", "room" -> R.string.portal_field_room
    "ადგილი", "seat", "place" -> R.string.portal_field_seat
    "ფორმატი", "format" -> R.string.portal_field_format
    "ფაკულტეტი", "faculty" -> R.string.portal_field_faculty
    "პროგრამა", "program", "programme" -> R.string.portal_field_program
    "ძირითადი სპეციალობა", "major" -> R.string.portal_field_major
    "დამატებითი სპეციალობა", "minor" -> R.string.portal_field_minor
    "საშუალო ქულა", "official average score", "average score" -> R.string.portal_field_average
    "მიმდინარე სემესტრამდე გავლილი სასწავლო კურსების საშუალო ქულა",
    "official average before the current semester" -> R.string.portal_field_rating_average
    "ნომერი", "number", "#" -> R.string.portal_field_number
    "სათაური", "title" -> R.string.portal_field_title
    "თარიღი", "date" -> R.string.portal_field_date
    "პასუხი", "response" -> R.string.portal_field_response
    "კომენტარი", "comment" -> R.string.portal_field_comment
    "კრედიტი", "credits", "credit" -> R.string.portal_field_credits
    "ქულა", "score" -> R.string.portal_field_score
    "gpa" -> R.string.portal_field_gpa
    else -> null
  }

  /** Use for headings and notices only; course names, official results, and documents remain verbatim. */
  fun localizeInterfaceText(source: String, context: Context): String {
    val normalized = normalize(source)
    val resource = labelResource(source) ?: when (normalized) {
      "ჩემი ცხრილი", "my timetable", "my schedule" -> R.string.nav_schedule
      "საგამოცდო ცხრილი", "examination schedule" -> R.string.academic_exams
      "ჩემი პროგრამები", "my programs", "my programmes" -> R.string.academic_program
      "კალენდარი", "calendar" -> R.string.academic_calendar
      "სასწავლო ბარათი", "academic record" -> R.string.academic_record
      "განცხადებები", "statements" -> R.string.academic_statements
      "რეიტინგი", "rating" -> R.string.academic_rating
      "ინფორმაცია არ მოიძებნა", "no information found" -> R.string.portal_empty_body
      "სულ", "total" -> R.string.course_detail_score
      else -> null
    }
    if (resource != null) return context.getString(resource)
    DAYS[normalized]?.let { day ->
      return day.getDisplayName(TextStyle.FULL, context.resources.configuration.locales[0])
    }
    SEMESTER_GE.matchEntire(normalized)?.let { match ->
      val resourceId = if (match.groupValues[2] == "შემოდგომის") R.string.portal_semester_autumn else R.string.portal_semester_spring
      return context.getString(resourceId, match.groupValues[1])
    }
    SEMESTER_EN.matchEntire(normalized)?.let { match ->
      val resourceId = if (match.groupValues[2] in setOf("autumn", "fall")) R.string.portal_semester_autumn else R.string.portal_semester_spring
      return context.getString(resourceId, match.groupValues[1])
    }
    return source
  }

  private fun normalize(source: String): String = source.replace('\u00a0', ' ')
    .replace(WHITESPACE, " ").trim().removeSuffix(":").trim().lowercase(Locale.ROOT)

  private val WHITESPACE = Regex("\\s+")
  private val SEMESTER_GE = Regex("(\\d{4}\\s*[-–]\\s*\\d{4}) წლის (შემოდგომის|გაზაფხულის) სემესტრი")
  private val SEMESTER_EN = Regex("(\\d{4}\\s*[-–]\\s*\\d{4}) (autumn|fall|spring) semester")
  private val DAYS = mapOf(
    "ორშაბათი" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
    "სამშაბათი" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
    "ოთხშაბათი" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
    "ხუთშაბათი" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
    "პარასკევი" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
    "შაბათი" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
    "კვირა" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY,
  )
}
