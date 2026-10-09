package com.ali.hafiflet

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/** Shizuku bağlantısını yönetir ve shell komutlarını çalıştırır. */
object Shell {

    enum class State { NOT_RUNNING, NO_PERMISSION, CONNECTING, READY }

    class Result(val code: Int, val out: String) {
        val ok get() = code == 0
    }

    private const val PERMISSION_CODE = 14
    private val PACKAGE_NAME = Regex("[A-Za-z0-9_.]+")

    private val listeners = mutableSetOf<(State) -> Unit>()
    private lateinit var serviceArgs: Shizuku.UserServiceArgs

    @Volatile
    private var service: IShellService? = null
    private var binding = false

    var state = State.NOT_RUNNING
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            binding = false
            if (binder != null && binder.pingBinder()) {
                service = IShellService.Stub.asInterface(binder)
            }
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            binding = false
            refresh()
        }
    }

    fun init(context: Context) {
        serviceArgs = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            binding = false
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_CODE) refresh()
        }
    }

    /** Durumu yeniden değerlendirir; gerekirse servise bağlanır. Ana iş parçacığından çağrılmalı. */
    fun refresh() {
        val next = when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> {
                service = null
                State.NOT_RUNNING
            }
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.NO_PERMISSION
            service != null -> State.READY
            else -> {
                if (!binding) {
                    binding = true
                    Shizuku.bindUserService(serviceArgs, connection)
                }
                State.CONNECTING
            }
        }
        if (next != state) {
            state = next
            listeners.toList().forEach { it(next) }
        }
    }

    /** İzin kalıcı olarak reddedildiyse false döner; kullanıcı Shizuku uygulamasından vermeli. */
    fun requestPermission(): Boolean {
        if (!Shizuku.pingBinder()) return false
        if (Shizuku.shouldShowRequestPermissionRationale()) return false
        Shizuku.requestPermission(PERMISSION_CODE)
        return true
    }

    fun addListener(listener: (State) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners -= listener
    }

    /**
     * Shizuku hazır olana kadar en fazla [timeoutMs] bekler, sonucu ana iş parçacığında bildirir.
     * Uygulama süreci yeni başladığında (örneğin bildirim paneli kutucuğundan) bağlantı birkaç yüz ms sürer.
     */
    fun awaitReady(timeoutMs: Long, callback: (Boolean) -> Unit) {
        var finished = false
        lateinit var listener: (State) -> Unit
        val timeout = Runnable {
            if (finished) return@Runnable
            finished = true
            removeListener(listener)
            callback(false)
        }
        listener = { state ->
            if (state == State.READY && !finished) {
                finished = true
                mainHandler.removeCallbacks(timeout)
                // Dinleyici listesi dolaşılırken listeden çıkarmamak için ertele.
                mainHandler.post { removeListener(listener) }
                callback(true)
            }
        }
        mainHandler.postDelayed(timeout, timeoutMs)
        addListener(listener)
        refresh()
    }

    /** Engelleyici çağrıdır; ana iş parçacığında çağırma. */
    fun exec(command: String): Result {
        val s = service ?: return Result(-1, "Shizuku bağlı değil")
        return try {
            val bundle = s.exec(command)
            Result(bundle.getInt("code", -1), bundle.getString("out").orEmpty())
        } catch (e: Exception) {
            Result(-1, e.message ?: e.toString())
        }
    }

    /** Komuta eklenecek paket adlarının güvenli olduğundan emin olur. */
    fun checkPackage(pkg: String): String {
        require(PACKAGE_NAME.matches(pkg)) { "Geçersiz paket adı: $pkg" }
        return pkg
    }
}
