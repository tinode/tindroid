package com.example.testapp.client

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.CheckResult
import androidx.lifecycle.*
import co.tinode.tinsdk.*
import co.tinode.tinsdk.model.*
import co.tinode.tinui.TinodeClient
import co.tinode.tinui.db.StoredMessage
import co.tinode.tinui.media.VxCard
import com.example.testapp.client.api.ChatClientConfig
import com.example.testapp.client.api.ErrorCall
import com.example.testapp.client.api.InitConnectionListener
import com.example.testapp.client.api.models.FilterObject
import com.example.testapp.client.api.models.QueryChannelRequest
import com.example.testapp.client.api.models.QueryChannelsRequest
import com.example.testapp.client.api.models.filter
import com.example.testapp.client.clientstate.*
import com.example.testapp.client.clientstate.SocketState
import com.example.testapp.client.clientstate.SocketStateService
import com.example.testapp.client.clientstate.UserState
import com.example.testapp.client.clientstate.UserStateService
import com.example.testapp.client.events.*
import com.example.testapp.client.extensions.connectUser
import com.example.testapp.client.extensions.subscribeMeTopic
import com.example.testapp.client.extensions.subscribeTopic
import com.example.testapp.client.extensions.toChannelInfo
import com.example.testapp.client.models.*
import com.example.testapp.client.models.User
import com.example.testapp.client.plugin.Plugin
import com.example.testapp.client.plugin.factory.PluginFactory
import com.example.testapp.client.plugin.listeners.QueryChannelListener
import com.example.testapp.client.plugin.listeners.QueryChannelsListener
import com.example.testapp.client.setup.InitializationCoordinator
import com.example.testapp.client.utils.observable.ChatEventsObservable
import com.example.testapp.client.utils.observable.Disposable
import com.example.testapp.components.common.extensions.internal.cast
import com.example.testapp.core.client.call.*
import com.example.testapp.core.client.errors.ChatError
import com.example.testapp.core.client.utils.Result
import com.example.testapp.core.internal.InternalTinUiApi
import com.example.testapp.core.internal.coroutines.DispatcherProvider
import com.example.testapp.logger.ChatLogLevel
import com.example.testapp.logger.ChatLogger
import com.example.testapp.logger.ChatLoggerHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*
import co.tinode.tinsdk.User as TUser

/**
 * The ChatClient is the main entry point for all low-level operations on chat
 */
public class ChatClient internal constructor(
    public val config: ChatClientConfig,
    public val socket: Tinode,
    private val socketStateService: SocketStateService = SocketStateService(),
    private val userStateService: UserStateService = UserStateService(),
    internal val scope: CoroutineScope,
    private val initializationCoordinator: InitializationCoordinator = InitializationCoordinator.getOrCreate(),
) {
    private var connectionListener: InitConnectionListener? = null
    private val logger = ChatLogger.get("Client")
    private val eventsObservable = ChatEventsObservable(socket, this)
    private val lifecycleObserver = TinodeLifecycleObserver(
        object : LifecycleHandler {
            override fun resume() = reconnectSocket()
            override fun stopped() {
                socket.disconnect(false)
            }
        }
    )

    /**
     * The list of plugins added once user is connected.
     *
     * @see [Plugin]
     */
    internal var plugins: List<Plugin> = emptyList()

    init {
        eventsObservable.subscribe { event ->
            when (event) {
                is UserLoginEvent -> {
                    socket.subscribeMeTopic()
                }
                is ConnectedEvent -> {
                    socketStateService.onConnected()
                    lifecycleObserver.observe()
                }
                is DisconnectedEvent -> {
                    when (event.disconnectCause) {
                        DisconnectCause.ConnectionReleased,
                        DisconnectCause.NetworkNotAvailable,
                        is DisconnectCause.Error,
                        -> socketStateService.onDisconnected()
                        is DisconnectCause.UnrecoverableError -> {
                            userStateService.onSocketUnrecoverableError()
                            socketStateService.onSocketUnrecoverableError()
                        }
                    }
                }
                else -> Unit // Ignore other events
            }

            val currentUser = when {
                event is HasOwnUser -> event.me
                event is UserEvent && event.user.id == getCurrentUser()?.id ?: "" -> event.user
                else -> null
            }
            currentUser?.let { updatedCurrentUser ->
                userStateService.onUserUpdated(updatedCurrentUser)
            }
        }
        logger.logI("Initialised: " + buildSdkTrackingHeaders())
    }

    internal fun addPlugins(plugins: List<Plugin>) {
        this.plugins = plugins
    }

    //region Set user

    /**
     * Creates a [PromisedReply] that wraps a call that would execute a connection call
     * asynchronous and provide results to an [InitConnectionListener].
     *
     * @param performCall This should perform the call, passing in the
     *                    [initListener] to it.
     */
    private fun createInitListenerCall(
        performCall: (initListener: InitConnectionListener) -> Unit,
    ): Call<ConnectionData> {
        return object : Call<ConnectionData> {
            override fun execute(): Result<ConnectionData> {
                // Uses coroutines to turn the async call into blocking
                return runBlocking { await() }
            }

            override fun enqueue(callback: Call.Callback<ConnectionData>) {
                // Converts InitConnectionListener to Call.Callback
                performCall(
                    object : InitConnectionListener() {
                        override fun onSuccess(data: ConnectionData) {
                            val connectionData =
                                com.example.testapp.client.models.ConnectionData(data.user)
                            callback.onResult(Result(connectionData))
                        }

                        override fun onError(error: ChatError) {
                            callback.onResult(Result(error))
                        }
                    }
                )
            }

            override fun cancel() {}
        }
    }

    /**
     * Initializes [ChatClient] for a specific user using the given user credentials.
     *
     * @param credentials Instance of [UserBasicCredentials] type.
     */
    @CheckResult
    public fun connectUser(credentials: UserBasicCredentials): Call<ConnectionData> {
        val user = User(username = credentials.username, password = credentials.password)
        return createInitListenerCall { listener -> setUser(user, listener) }
    }

    /**
     * Initializes [ChatClient] for a specific user.
     *
     * This method performs required operations before connecting with the chat server.
     * Moreover, it warms up the connection, sets up notifications, and connects to the socket.
     * You can use [listener] to get updates about socket connection.
     *
     * @param user The user to set.
     * @param listener Socket connection listener.
     */
    private fun setUser(
        user: User,
        listener: InitConnectionListener? = null,
    ) {
        val userState = userStateService.state
        when {
            userState is UserState.NotSet -> {
                initializeClientWithUser(user)
                connectionListener = listener
                socketStateService.onConnectionRequested()
                socket.connectUser(user)
            }
            userState is UserState.UserSet && userState.user.username == user.username && socketStateService.state is SocketState.Idle -> {
                userStateService.onUserUpdated(user)
                connectionListener = listener
                socketStateService.onConnectionRequested()
                socket.connectUser(user)
                initializationCoordinator.userConnected(user)
            }
            userState is UserState.UserSet && userState.user.username != user.username -> {
                logger.logE("Trying to set user without disconnecting the previous one - make sure that previously set user is disconnected.")
                listener?.onError(ChatError("User cannot be set until previous one is disconnected."))
            }
            else -> {
                logger.logE("Failed to connect user. Please check you don't have connected user already")
                listener?.onError(ChatError("User cannot be set until previous one is disconnected."))
            }
        }
    }

    private fun initializeClientWithUser(
        user: User,
    ) {
        // fire a handler here that the chatDomain and chatUI can use
        initializationCoordinator.userConnected(user)
        userStateService.onSetUser(user)
    }

    internal fun callConnectionListener(connectedEvent: ConnectedEvent?, error: ChatError?) {
        if (connectedEvent != null) {
            val user = connectedEvent.me
            connectionListener?.onSuccess(InitConnectionListener.ConnectionData(user))
        } else if (error != null) {
            connectionListener?.onError(error)
        }
        connectionListener = null
    }

    //endregion

    public fun isSocketConnected(): Boolean {
        return socketStateService.state is SocketState.Connected
    }

    public fun disconnectSocket() {
        socket.disconnect(false)
    }

    public fun reconnectSocket() {
        when (socketStateService.state) {
            is SocketState.Disconnected -> when (val userState = userStateService.state) {
                is UserState.UserSet -> socket.connectUser(userState.user)
//                is UserState.Anonymous.AnonymousUserSet -> socket.connectAnonymously()
                else -> error("Invalid user state $userState without user being set!")
            }
            else -> Unit
        }
    }

    public fun disconnect() {
        // fire a handler here that the chatDomain and chatUI can use
        getCurrentUser().let(initializationCoordinator::userDisconnected)
        connectionListener = null
        socketStateService.onDisconnectRequested()
        userStateService.onLogout()
        socket.disconnect(false)
        lifecycleObserver.dispose()
    }

    //region events-listeners-subscriptions

    public fun addSocketListener(listener: Tinode.EventListener) {
        socket.addListener(listener)
    }

    public fun removeSocketListener(listener: Tinode.EventListener) {
        socket.removeListener(listener)
    }

    public fun subscribe(
        listener: ChatEventListener<ChatEvent>,
    ): Disposable {
        return eventsObservable.subscribe(listener = listener)
    }

    /**
     * Subscribes to the specific [eventTypes] of the client.
     *
     * @see [EventType] for type constants
     */
    public fun subscribeFor(
        vararg eventTypes: String,
        listener: ChatEventListener<ChatEvent>,
    ): Disposable {
        val filter = { event: ChatEvent ->
            event.type in eventTypes
        }
        return eventsObservable.subscribe(filter, listener)
    }

    /**
     * Subscribes to the specific [eventTypes] of the client, in the lifecycle of [lifecycleOwner].
     *
     * Only receives events when the lifecycle is in a STARTED state, otherwise events are dropped.
     */
    public fun subscribeFor(
        lifecycleOwner: LifecycleOwner,
        vararg eventTypes: String,
        listener: ChatEventListener<ChatEvent>,
    ): Disposable {
        val disposable = subscribeFor(
            *eventTypes,
            listener = { event ->
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    listener.onEvent(event)
                }
            }
        )

        lifecycleOwner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    disposable.dispose()
                }
            }
        )

        return disposable
    }

    /**
     * Subscribes for the next event with the given [eventType].
     */
    public fun <T : ChatEvent> subscribeForSingle(
        eventType: Class<T>,
        listener: ChatEventListener<T>,
    ): Disposable {
        val filter = { event: ChatEvent ->
            eventType.isInstance(event)
        }
        return eventsObservable.subscribeSingle(filter) { event ->
            @Suppress("UNCHECKED_CAST")
            listener.onEvent(event as T)
        }
    }

    /**
     * Subscribes for the next event with the given [eventType].
     */
    public fun subscribeForSingle(
        eventType: String,
        listener: ChatEventListener<ChatEvent>,
    ): Disposable {
        val filter = { event: ChatEvent ->
            event.type == eventType
        }
        return eventsObservable.subscribeSingle(filter, listener)
    }

    //endregion

    //region: api calls

    /**
     * Get the channel without running any side effects.
     *
     * @param request The request's parameters combined into [QueryChannelsRequest] class.
     *
     * @return Executable async [Call] responsible for querying channels.
     */
    @CheckResult
    @InternalTinUiApi
    public fun queryChannelInternal(
        channelId: String,
        request: QueryChannelRequest,
    ): Call<Channel> = CoroutineCall(scope) {
        var topic: ComTopic<VxCard?> = socket.getTopic(channelId).cast()
        if (request.watch) {
            topic = socket.subscribeTopic(channelId, request)
        }
        Result.success(
            Channel.fromComTopic(topic).apply {
                socket.getLastMessage(name)?.let { msg ->
                    if (msg is StoredMessage) {
                        socket.getUser<VxCard>(msg.from)?.let { tuser ->
                            messages = listOf(
                                Message.fromStorageMessage(msg, User.fromTUser(tuser))
                                    .apply { channelInfo = toChannelInfo() })
                        }
                    }
                }
            }
        )
    }.onErrorReturn(scope) {
        Result.error(it)
    }

    @CheckResult
    public fun queryChannel(
        channelId: String,
        request: QueryChannelRequest,
    ): Call<Channel> {
        val relevantPlugins = plugins.filterIsInstance<QueryChannelListener>()
        return queryChannelInternal(channelId, request)
            .doOnStart(scope) {
                relevantPlugins.forEach {
                    it.onQueryChannelRequest(
                        channelId,
                        request
                    )
                }
            }
            .doOnResult(scope) { result ->
                relevantPlugins.forEach {
                    it.onQueryChannelResult(
                        result,
                        channelId,
                        request
                    )
                }
            }
            .precondition(relevantPlugins) {
                onQueryChannelPrecondition(
                    channelId,
                    request
                )
            }
    }

    public fun leaveChannelInternal(
        channelId: String,
    ): Call<Unit> = CoroutineCall(scope) {
        val topic: ComTopic<VxCard?> = socket.getTopic(channelId).cast()
        if (topic.isAttached) {
            topic.leave().result
        }
        Result.success(Unit)
    }.onErrorReturn(scope) {
        Result.error(it)
    }

    /**
     * Gets the channels without running any side effects.
     *
     * @param request The request's parameters combined into [QueryChannelsRequest] class.
     *
     * @return Executable async [Call] responsible for querying channels.
     */
    @CheckResult
    @InternalTinUiApi
    internal fun queryChannelsInternal(request: QueryChannelsRequest): Call<List<Channel>> =
        CoroutineCall(scope) {
            Result.success(socket.getFilteredTopics<ComTopic<VxCard?>> { t -> t.isUserType }
                .map {
                    Channel.fromComTopic(it).apply {
                        socket.getLastMessage(name)?.let { msg ->
                            if (msg is StoredMessage) {
                                socket.getUser<VxCard>(msg.from)?.let { tuser ->
                                    messages = listOf(
                                        Message.fromStorageMessage(msg, User.fromTUser(tuser))
                                            .apply { channelInfo = toChannelInfo() }
                                    )
                                }
                            }
                        }
                    }
                }.filter(request.filter))
        }.onErrorReturn(scope) {
            Result.error(it)
        }

    /**
     * Gets the channels from the server based on parameters from [QueryChannelsRequest].
     *
     * @param request The request's parameters combined into [QueryChannelsRequest] class.
     *
     * @return Executable async [Call] responsible for querying channels.
     */
    @CheckResult
    public fun queryChannels(request: QueryChannelsRequest): Call<List<Channel>> {
        val relevantPluginsLazy = { plugins.filterIsInstance<QueryChannelsListener>() }
        return queryChannelsInternal(request).doOnStart(scope) {
            relevantPluginsLazy().forEach { it.onQueryChannelsRequest(request) }
        }.doOnResult(scope) { result ->
            relevantPluginsLazy().forEach { it.onQueryChannelsResult(result, request) }
        }.precondition(relevantPluginsLazy()) {
            onQueryChannelsPrecondition(request)
        }
    }

    /**
     * Search messages across channels. There are two ways to paginate through search results:
     *
     * 1. Using [limit] and [offset] parameters
     * 1. Using [limit] and [next] parameters
     *
     * Limit and offset will allow you to access up to 1000 results matching your query.
     * You will not be able to sort using limit and offset. The results will instead be
     * sorted by relevance and message ID.
     *
     * Next pagination will allow you to access all search results that match your query,
     * and you will be able to sort using any filter-able fields and custom fields.
     * Pages of sort results will be returned with **next** and **previous** strings which
     * can be supplied as a next parameter when making a query to get a new page of results.
     *
     * @param channelFilter Channel filter conditions.
     * @param messageFilter Message filter conditions.
     * @param offset Pagination offset, cannot be used with sort or next.
     * @param limit The number of messages to return.
     * @param next Pagination parameter, cannot be used with non-zero offset.
     * @param sort The sort criteria applied to the result, cannot be used with non-zero offset.
     *
     * @return Executable async [Call] responsible for searching messages across channels.
     */
    @CheckResult
    public fun searchMessages(
        channelFilter: FilterObject,
        messageFilter: FilterObject,
        offset: Int? = null,
        limit: Int? = null,
        next: String? = null,
        sort: Any? = null,
    ): Call<List<Message>> {
        if (offset != null && (sort != null || next != null)) {
            return ErrorCall(ChatError("Cannot specify offset with sort or next parameters"))
        }
        return CoroutineCall(scope) {
            val messages = socket.getFilteredTopics<ComTopic<VxCard?>> { t -> t.isUserType }
                .map {
                    Channel.fromComTopic(it).apply {
                        socket.getLastMessage(name)?.let { msg ->
                            if (msg is StoredMessage) {
                                socket.getUser<VxCard>(msg.from)?.let { tuser ->
                                    messages = listOf(
                                        Message.fromStorageMessage(msg, User.fromTUser(tuser))
                                            .apply { channelInfo = toChannelInfo() }
                                    )
                                }
                            }
                        }
                    }.messages
                }.filter(channelFilter).flatten()
            Result.success(messages)
        }
    }

    //endregion

    /**
     * Return current connected user
     */
    public fun getCurrentUser(): User? {
        return runCatching { userStateService.state.userOrError() }.getOrNull()
    }

    /**
     * Builds a detailed header of information we track around the SDK, Android OS, API Level, device name and vendor and more.
     *
     * @return String formatted header that contains all the information.
     */
    public fun buildSdkTrackingHeaders(): String {
        val clientInformation = BuildConfig.VERSION_NAME
        val buildModel = Build.MODEL
        val deviceManufacturer = Build.MANUFACTURER
        val apiLevel = Build.VERSION.SDK_INT
        val osName = "Android ${Build.VERSION.RELEASE}"

        return """$clientInformation|os=$osName|api_version=$apiLevel|device_vendor=$deviceManufacturer|device_model=$buildModel"""
    }

    internal fun <R, T : Any> Call<T>.precondition(
        pluginsList: List<R>,
        preconditionCheck: suspend R.() -> Result<Unit>,
    ): Call<T> =
        withPrecondition(scope) {
            pluginsList.fold(Result.success(Unit)) { result, plugin ->
                if (result.isError) {
                    result
                } else {
                    val preconditionResult = preconditionCheck(plugin)
                    if (preconditionResult.isError) {
                        preconditionResult
                    } else {
                        result
                    }
                }
            }
        }

    public data class Builder(
        private val appContext: Context,
    ) : ChatClientBuilder() {
        private var apiKey = "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K"
        private var baseUrl: String = "sandbox.tinode.co"
        private var useTls: Boolean = true
        private var appName: String = ""
        private var logLevel = ChatLogLevel.NOTHING
        private var loggerHandler: ChatLoggerHandler? = null
        private var osName: String? = null
        private var deviceLocal: Locale = Locale.getDefault()

        public fun apiKey(key: String): Builder = apply {
            apiKey = key
        }

        /**
         * Sets the base URL to be used by the client.
         *
         * @param value The base URL to use.
         */
        public fun baseUrl(value: String): Builder {
            var baseUrl = value
            if (baseUrl.startsWith("https://")) {
                baseUrl = baseUrl.split("https://").toTypedArray()[1]
                useTls = true
            }
            if (baseUrl.startsWith("http://")) {
                baseUrl = baseUrl.split("http://").toTypedArray()[1]
                useTls = false
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length - 1)
            }
            this.baseUrl = baseUrl
            return this
        }

        public fun appName(name: String): Builder = apply {
            appName = name
        }

        /**
         * Sets the log level to be used by the client.
         *
         * See [ChatLogLevel] for details about the available options.
         *
         * We strongly recommend using [ChatLogLevel.NOTHING] in production builds,
         * which produces no logs.
         *
         * @param level The log level to use.
         */
        public fun logLevel(level: ChatLogLevel): Builder = apply {
            logLevel = level
        }

        public fun loggerHandler(handler: ChatLoggerHandler): Builder = apply {
            loggerHandler = handler
        }

        public fun osName(name: String): Builder = apply {
            osName = name
        }

        public fun deviceLocal(locale: Locale): Builder = apply {
            deviceLocal = locale
        }

        /**
         * Adds a plugin factory to be used by the client.
         * @see [PluginFactory]
         *
         * @param pluginFactory The factory to be added.
         */
        public fun withPlugin(pluginFactory: PluginFactory): Builder = apply {
            pluginFactories.add(pluginFactory)
        }

        private fun configureInitializer(chatClient: ChatClient) {
            chatClient.initializationCoordinator.addUserConnectedListener { user ->
                chatClient.addPlugins(
                    pluginFactories.map { pluginFactory ->
                        pluginFactory.get(user)
                    }
                )
            }
        }

        @InternalTinUiApi
        @Deprecated(
            message = "It shouldn't be used outside of SDK code. Created for testing purposes",
            replaceWith = ReplaceWith("this.build()"),
            level = DeprecationLevel.ERROR
        )
        override fun internalBuild(): ChatClient {
            if (apiKey.isBlank()) {
                throw IllegalStateException("apiKey is not defined in " + this::class.java.simpleName)
            }

            if (baseUrl.isBlank()) {
                throw IllegalStateException("baseUrl is not defined in " + this::class.java.simpleName)
            }

            instance?.run {
                Log.e(
                    "Chat",
                    "[ERROR] You have just re-initialized ChatClient, old configuration has been overridden [ERROR]"
                )
            }

            val config = ChatClientConfig(
                apiKey = apiKey,
                hostUrl = baseUrl,
                useTls = useTls,
                appName = appName,
                loggerConfig = ChatLogger.Config(logLevel, loggerHandler)
            )

            val tinode = TinodeClient.Builder(
                appName,
                apiKey,
            )
                //.setStorage(BaseDb.Builder(appContext).build().store)
                .setEventListener(Tinode.EventListener()).build()
            osName?.run {
                tinode.setOsString(this)
            }

            // Default types for parsing Public, Private fields of messages
            tinode.setDefaultTypeOfMetaPacket(
                VxCard::class.java,
                PrivateType::class.java
            )
            tinode.setMeTypeOfMetaPacket(VxCard::class.java)
            tinode.setFndTypeOfMetaPacket(VxCard::class.java)

            // Set device language
            tinode.setLanguage(deviceLocal.toString())

            tinode.setServer(baseUrl, useTls)

            ChatLogger.Builder(ChatLogger.Config(logLevel, loggerHandler)).build()

            val networkScope = CoroutineScope(DispatcherProvider.IO)
            return ChatClient(config, tinode, scope = networkScope).also {
                configureInitializer(it)
            }
        }

        private fun createCacheDir(path: String): File {
            val cache = File(appContext.applicationContext?.cacheDir, path)
            if (!cache.exists()) {
                cache.mkdirs()
            }
            return cache
        }
    }

    public abstract class ChatClientBuilder @InternalTinUiApi public constructor() {
        /**
         * Factories of plugins that will be added to the SDK.
         *
         * @see [Plugin]
         * @see [PluginFactory]
         */
        protected val pluginFactories: MutableList<PluginFactory> = mutableListOf()

        /**
         * Create a [ChatClient] instance based on the current configuration
         * of the [Builder].
         */
        public fun build(): ChatClient = internalBuild()
            .also {
                instance = it
            }

        @InternalTinUiApi
        public abstract fun internalBuild(): ChatClient
    }

    public companion object {
        private var instance: ChatClient? = null

        @JvmStatic
        public fun instance(): ChatClient = instance
            ?: throw IllegalStateException("ChatClient.Builder::build() must be called before obtaining ChatClient instance")

        public val isInitialized: Boolean
            get() = instance != null
    }
}