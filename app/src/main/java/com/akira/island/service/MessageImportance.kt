package com.akira.island.service

/** Minimized channels are silent too. A blocked (0) channel stays blocked even with Always. */
internal fun allowsMessageImportance(importance: Int, quiet: Boolean, always: Boolean): Boolean =
    importance >= if (quiet || always) 1 else 3
