package io.nekohasekai.sagernet.hotspotshare.root

import com.topjohnwu.superuser.Shell

interface ShellExecutor {
    fun isRoot(): Boolean
    fun exec(command: String): ShellResult
    fun exec(commands: List<String>): ShellResult
}

data class ShellResult(
    val isSuccess: Boolean,
    val code: Int,
    val out: List<String>,
    val err: List<String>,
)

object LibsuShellExecutor : ShellExecutor {
    override fun isRoot(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (_: Throwable) {
            false
        }
    }

    override fun exec(command: String): ShellResult {
        val res = Shell.cmd(command).exec()
        return ShellResult(
            isSuccess = res.isSuccess,
            code = res.code,
            out = res.out,
            err = res.err,
        )
    }

    override fun exec(commands: List<String>): ShellResult {
        val res = Shell.cmd(*commands.toTypedArray()).exec()
        return ShellResult(
            isSuccess = res.isSuccess,
            code = res.code,
            out = res.out,
            err = res.err,
        )
    }
}
