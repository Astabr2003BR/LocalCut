package com.localcut.editor.termux

object TermuxEngineRecoveryPolicy {
    const val TERMUX_PACKAGE = "com.termux"
    const val REPOSITORY_URL = "https://github.com/Astabr2003BR/LocalCut.git"
    const val DEVELOPMENT_BRANCH = "feature/localcut-bridge-v1"
    private const val MANAGED_SOURCE_DIR = "\$HOME/.localcut-engine/source"
    private const val VALIDATED_RUNS_URL =
        "https://api.github.com/repos/Astabr2003BR/LocalCut/actions/workflows/termux-engine-ci.yml/runs?branch=feature%2Flocalcut-bridge-v1&status=success&per_page=20"

    fun quickCommand(paired: Boolean): String = if (paired) {
        "bash \"\$HOME/.localcut-engine/auto-recover\" --once"
    } else {
        "cd \"$MANAGED_SOURCE_DIR\" && python termux-engine/localcut_supervisor.py --pair"
    }

    fun missingCreativeCapabilities(capabilities: Set<String>): Set<String> =
        TermuxAutomationPolicy.missingCreativeCapabilities(capabilities)

    fun shouldOfferRepair(
        paired: Boolean,
        connected: Boolean,
        errorCode: String?,
        managedReady: Boolean,
        capabilities: Set<String>,
    ): Boolean {
        if (!paired || errorCode == "TERMUX_ENGINE_UNAVAILABLE") return true
        if (!connected) return !managedReady
        if (!TermuxAutomationPolicy.hasManagedAutomationCapability(capabilities)) return true
        if (managedReady && TermuxAutomationPolicy.hasFullCreativeCapabilities(capabilities)) return false
        return missingCreativeCapabilities(capabilities).isNotEmpty()
    }

    fun bootstrapCommand(paired: Boolean): String {
        val resolveValidatedRevision =
            "REV=\$(python -c 'import json,urllib.request; " +
                "u=\"$VALIDATED_RUNS_URL\"; " +
                "q=urllib.request.Request(u,headers={\"User-Agent\":\"LocalCut-Bootstrap/1.0\"}); " +
                "d=json.load(urllib.request.urlopen(q,timeout=15)); " +
                "print(next(r[\"head_sha\"] for r in d[\"workflow_runs\"] " +
                "if r.get(\"conclusion\")==\"success\" and r.get(\"head_branch\")==\"$DEVELOPMENT_BRANCH\"))')"
        val syncExisting =
            "test \"\$(git -C \"$MANAGED_SOURCE_DIR\" remote get-url origin)\" = \"$REPOSITORY_URL\" && " +
                "test -z \"\$(git -C \"$MANAGED_SOURCE_DIR\" status --porcelain)\" && " +
                "if [ -f \"$MANAGED_SOURCE_DIR/.git/shallow\" ]; then " +
                "git -C \"$MANAGED_SOURCE_DIR\" fetch --unshallow origin $DEVELOPMENT_BRANCH; " +
                "else git -C \"$MANAGED_SOURCE_DIR\" fetch origin $DEVELOPMENT_BRANCH; fi && " +
                "git -C \"$MANAGED_SOURCE_DIR\" fetch origin \"\$REV\" && " +
                "git -C \"$MANAGED_SOURCE_DIR\" merge-base --is-ancestor HEAD \"\$REV\" && " +
                "git -C \"$MANAGED_SOURCE_DIR\" checkout --detach \"\$REV\""
        val syncNew =
            "git clone --no-checkout --branch $DEVELOPMENT_BRANCH --single-branch $REPOSITORY_URL \"$MANAGED_SOURCE_DIR\" && " +
                "git -C \"$MANAGED_SOURCE_DIR\" fetch origin \"\$REV\" && " +
                "git -C \"$MANAGED_SOURCE_DIR\" checkout --detach \"\$REV\""
        val validateSource = listOf(
            "cd \"$MANAGED_SOURCE_DIR\"",
            "python -m py_compile termux-engine/localcut_automation.py termux-engine/localcut_supervisor.py termux-engine/localcut_creative_server.py",
            "bash -n termux-engine/setup_termux.sh",
            "bash -n termux-engine/bootstrap_termux.sh",
            "bash -n termux-engine/restart_termux.sh",
        )
        val activate = if (paired) {
            "bash termux-engine/bootstrap_termux.sh"
        } else {
            "bash termux-engine/setup_termux.sh --enable-external-automation && " +
                "python termux-engine/localcut_supervisor.py --pair"
        }
        return (listOf(
            "pkg install -y git python ffmpeg",
            "mkdir -p \"\$HOME/.localcut-engine\"",
            resolveValidatedRevision,
            "printf '%s' \"\$REV\" | grep -Eq '^[0-9a-f]{40}\$'",
            "if [ -d \"$MANAGED_SOURCE_DIR/.git\" ]; then $syncExisting; else $syncNew; fi",
        ) + validateSource + activate).joinToString(" && ")
    }

    fun repairCommand(paired: Boolean): String = bootstrapCommand(paired)
}
