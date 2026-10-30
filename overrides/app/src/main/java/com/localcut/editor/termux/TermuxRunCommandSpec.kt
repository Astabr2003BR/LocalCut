package com.localcut.editor.termux

object TermuxRunCommandSpec {
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val ACTION = "com.termux.RUN_COMMAND"
    const val SERVICE_CLASS = "com.termux.app.RunCommandService"
    const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

    const val BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
    const val HOME_PATH = "/data/data/com.termux/files/home"
    const val AUTO_RECOVER_PATH = "/data/data/com.termux/files/home/.localcut-engine/auto-recover"

    fun arguments(): Array<String> = arrayOf(AUTO_RECOVER_PATH, "--once")
}
