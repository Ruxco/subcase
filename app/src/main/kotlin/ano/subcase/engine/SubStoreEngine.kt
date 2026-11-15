package ano.subcase.engine

/**
 * Sub-Store 本地服务引擎。
 *
 * 实现必须在主线程创建、[startServer] 和 [stopServer]；启动失败时直接抛异常，
 * 运行期失败通过构造参数中的回调上报。
 */
interface SubStoreEngine {
    fun startServer()

    fun stopServer()
}
