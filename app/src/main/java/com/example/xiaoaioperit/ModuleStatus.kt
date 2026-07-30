package com.example.xiaoaioperit

/**
 * LSPosed 是否真的把本模块激活了。
 *
 * 套路是标准的自欺:这里永远返回 false,而 [HookEntry] 在加载进本模块自己的进程时
 * 会把它替换成返回 true。所以界面读到 true 就说明 hook 框架确实生效了。
 */
object ModuleStatus {

    @JvmStatic
    fun isActive(): Boolean = false
}