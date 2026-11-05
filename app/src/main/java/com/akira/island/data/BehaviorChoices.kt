package com.akira.island.data

/** One visible choice replaces overlapping player/source switches without changing saved defaults. */
enum class SourceVisibility { SHOW, HIDE_MUSIC, HIDE_ALL }
val IslandSettings.sourceVisibility: SourceVisibility get() = when {
    hideWhenSourceOpen -> SourceVisibility.HIDE_ALL
    hideWhenPlayerOpen -> SourceVisibility.HIDE_MUSIC
    else -> SourceVisibility.SHOW
}
fun IslandSettings.withSourceVisibility(mode: SourceVisibility) = copy(
    hideWhenSourceOpen = mode == SourceVisibility.HIDE_ALL,
    hideWhenPlayerOpen = mode == SourceVisibility.HIDE_MUSIC,
)
enum class SideSwipe { OFF, TRACKS, ACTIVITIES }
val IslandSettings.sideSwipe: SideSwipe get() = when {
    swipeActivities -> SideSwipe.ACTIVITIES
    swipeTracks -> SideSwipe.TRACKS
    else -> SideSwipe.OFF
}
fun IslandSettings.withSideSwipe(mode: SideSwipe) = copy(swipeActivities = mode == SideSwipe.ACTIVITIES, swipeTracks = mode == SideSwipe.TRACKS)

/** Empty visibility is a single choice; a clock cannot be enabled inside a hidden capsule. */
enum class EmptyCapsule { HIDDEN, PLAIN, CLOCK }
val IslandSettings.emptyCapsule: EmptyCapsule get() = when {
    hideIdle -> EmptyCapsule.HIDDEN
    idleClockBattery -> EmptyCapsule.CLOCK
    else -> EmptyCapsule.PLAIN
}
fun IslandSettings.withEmptyCapsule(mode: EmptyCapsule) = copy(hideIdle = mode == EmptyCapsule.HIDDEN, idleClockBattery = mode == EmptyCapsule.CLOCK)
