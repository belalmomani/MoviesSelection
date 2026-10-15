package com.tvseries.Movies.ui

import com.tvseries.Movies.core.Axes
import com.tvseries.Movies.core.Condition
import com.tvseries.Movies.core.Selection

/** "Things I don't want": each option excludes one or more category values. */
class AvoidOption(val id: String, val ar: String, val en: String, val items: List<Pair<String, String>>) {
    fun isOn(sel: Selection): Boolean = items.all { (a, v) -> sel.isExcluded(a, v) }

    fun toggle(sel: Selection): Selection =
        if (isOn(sel)) items.fold(sel) { s, (a, v) -> s.remove(Condition(a, v, true)) }
        else items.fold(sel) { s, (a, v) -> if (s.isExcluded(a, v)) s else s.toggleExclude(a, v) }
}

object Presets {
    val avoid = listOf(
        AvoidOption("no_horror", "لا رعب", "No horror", listOf(Axes.GENRE to "genre.27")),
        AvoidOption("no_infidelity", "لا خيانة", "No infidelity", listOf(Axes.AVOID to "avoid.infidelity")),
        AvoidOption("no_sad_ending", "لا نهاية حزينة", "No sad ending", listOf(Axes.AVOID to "avoid.sad_ending")),
        AvoidOption("no_gore", "لا مشاهد دموية", "No gore", listOf(Axes.AVOID to "avoid.gore")),
        AvoidOption("no_school", "لا قصص مدرسية", "No school stories", listOf(Axes.AVOID to "avoid.school")),
        AvoidOption(
            "no_long", "لا أفلام طويلة", "No long films",
            listOf(Axes.RUNTIME to "runtime.m.120_150", Axes.RUNTIME to "runtime.m.o150"),
        ),
    )

    class Choice(val id: String, val ar: String, val en: String, val axis: String, val values: List<String>)

    val moods = listOf(
        Choice("laugh", "يضحكني", "Make me laugh", Axes.MOOD, listOf("mood.laugh")),
        Choice("think", "يثير تفكيري", "Make me think", Axes.MOOD, listOf("mood.think")),
        Choice("nerves", "يشد أعصابي", "Keep me on edge", Axes.MOOD, listOf("mood.nerves")),
        Choice("relax", "يريحني", "Relax me", Axes.MOOD, listOf("mood.relax")),
        // "Surprise me" = well-rated titles of any kind (there is no data to detect surprise itself).
        Choice("surprise", "يفاجئني", "Surprise me", Axes.RATING, listOf("rating.7")),
    )
    val tensions = listOf(
        Choice("low", "هادئ", "Calm", Axes.TENSION, listOf("tension.low")),
        Choice("medium", "متوسط", "Medium", Axes.TENSION, listOf("tension.medium")),
        Choice("high", "عالٍ", "High", Axes.TENSION, listOf("tension.high")),
    )
    val endings = listOf(
        Choice("happy", "سعيدة", "Happy", Axes.ENDING, listOf("ending.happy")),
        Choice("sad", "حزينة", "Sad", Axes.ENDING, listOf("ending.sad")),
        Choice("twist", "ملتوية", "Twist", Axes.ENDING, listOf("ending.twist")),
        Choice("open", "مفتوحة", "Open", Axes.ENDING, listOf("ending.open")),
    )
    val durations = listOf(
        Choice("short", "قصير (أقل من 90د)", "Short (< 90 min)", Axes.RUNTIME, listOf("runtime.m.u90")),
        Choice("medium", "متوسط (90–120د)", "Medium (90–120 min)", Axes.RUNTIME, listOf("runtime.m.90_120")),
        Choice("long", "طويل (أكثر من 120د)", "Long (120+ min)", Axes.RUNTIME, listOf("runtime.m.120_150", "runtime.m.o150")),
    )

    fun build(mood: Choice?, tension: Choice?, ending: Choice?, duration: Choice?, avoidOn: List<AvoidOption>): Selection {
        var s = Selection()
        for (c in listOfNotNull(mood, tension, ending, duration)) for (v in c.values) s = s.toggleInclude(c.axis, v)
        for (a in avoidOn) s = a.toggle(s)
        return s
    }
}
