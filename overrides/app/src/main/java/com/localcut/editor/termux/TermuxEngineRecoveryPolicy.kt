package com.localcut.editor.termux

object TermuxEngineRecoveryPolicy {
    const val TERMUX_PACKAGE = "com.termux"
    const val REPOSITORY_URL = "https://github.com/Astabr2003BR/LocalCut.git"
    const val DEVELOPMENT_BRANCH = "feature/localcut-bridge-v1"
    private const val MANAGED_SOURCE_DIR = "\$HOME/.localcut-engine/source"

    fun quickCommand(paired: Boolean): String = if (paired) {
        "bash \"\$HOME/.localcut-engine/start-localcut-engine\""
    } else {
        "cd \"$MANAGED_SOURCE_DIR\" && python termux-engine/localcut_supervisor.py --pair"
    }

    fun repairCommand(paired: Boolean): String {
        val launch = quickCommand(paired)
        return listOf(
            "pkg update -y",
            "pkg install -y git python ffmpeg",
            "mkdir -p \"\$HOME/.localcut-engine\"",
            "if [ -d \"$MANAGED_SOURCE_DIR/.git\" ]; then git -C \"$MANAGED_SOURCE_DIR\" fetch --depth 1 origin $DEVELOPMENT_BRANCH && git -C \"$MANAGED_SOURCE_DIR\" checkout --detach FETCH_HEAD; else git clone --depth 1 --branch $DEVELOPMENT_BRANCH --single-branch $REPOSITORY_URL \"$MANAGED_SOURCE_DIR\"; fi",
            "cd \"$MANAGED_SOURCE_DIR\"",
            "bash termux-engine/setup_termux.sh",
            launch,
        ).joinToString(" && ")
    }
}
