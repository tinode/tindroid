package com.example.testapp.livedata.plugin

import android.content.Context
import com.example.testapp.client.ChatClient
import com.example.testapp.client.models.User
import com.example.testapp.client.plugin.Plugin
import com.example.testapp.client.plugin.factory.PluginFactory
import com.example.testapp.client.setup.InitializationCoordinator
import com.example.testapp.core.internal.coroutines.DispatcherProvider
import com.example.testapp.livedata.event.EventHandlerImpl
import com.example.testapp.livedata.logic.LogicRegistry
import com.example.testapp.livedata.plugin.listener.QueryChannelListenerImpl
import com.example.testapp.livedata.plugin.listener.QueryChannelsListenerImpl
import com.example.testapp.livedata.state.StateRegistry
import com.example.testapp.livedata.state.global.GlobalMutableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

public class LiveDataPluginFactory(
//    private val config: Config,
    private val appContext: Context,
) : PluginFactory {
    /**
     * Creates a [Plugin]
     *
     * @return The [Plugin] instance.
     */
    override fun get(user: User): Plugin = createOfflinePlugin(user)

    /**
     * Creates the [LiveDataPlugin] and initialized its dependencies. This method must be called after the user is set in the SDK.
     */
    private fun createOfflinePlugin(user: User): LiveDataPlugin {
        val chatClient = ChatClient.instance()
        val globalState = GlobalMutableState.getOrCreate().apply {
            clearState()
        }

        val job = SupervisorJob()
        val scope = CoroutineScope(job + DispatcherProvider.IO)

        val userStateFlow = MutableStateFlow(ChatClient.instance().getCurrentUser())
        //TODO: use real users flow
        val stateRegistry = StateRegistry.create(job, scope, userStateFlow, MutableStateFlow(emptyMap()))
        val logic = LogicRegistry.create(stateRegistry, globalState, true, chatClient)

        val eventHandler = EventHandlerImpl(
            recoveryEnabled = true,
            client = chatClient,
            logic = logic,
            state = stateRegistry,
            mutableGlobalState = globalState,
//            repos = repos,
//            syncManager = syncManager,
        ).also { eventHandler ->
//            EventHandlerProvider.eventHandler = eventHandler
            eventHandler.initialize(user, scope)
            eventHandler.startListening(scope)
        }

        InitializationCoordinator.getOrCreate().run {
            addUserDisconnectedListener {
                stateRegistry.clear()
                logic.clear()
                globalState.clearState()
                eventHandler.stopListening()
            }
        }

        globalState._user.value = user

        return LiveDataPlugin(
            queryChannelsListener = QueryChannelsListenerImpl(logic),
            queryChannelListener = QueryChannelListenerImpl(logic),
//            threadQueryListener = ThreadQueryListenerImpl(logic),
//            channelMarkReadListener = ChannelMarkReadListenerImpl(channelMarkReadHelper),
//            editMessageListener = EditMessageListenerImpl(logic, globalState),
//            hideChannelListener = HideChannelListenerImpl(logic, repos),
//            markAllReadListener = MarkAllReadListenerImpl(logic, stateRegistry.scope, channelMarkReadHelper),
//            deleteReactionListener = DeleteReactionListenerImpl(logic, globalState, repos),
//            sendReactionListener = SendReactionListenerImpl(logic, globalState, repos),
//            deleteMessageListener = DeleteMessageListenerImpl(logic, globalState, repos),
//            sendMessageListener = SendMessageListenerImpl(logic, repos),
//            sendGiphyListener = SendGiphyListenerImpl(logic),
//            shuffleGiphyListener = ShuffleGiphyListenerImpl(logic),
//            queryMembersListener = QueryMembersListenerImpl(repos),
//            typingEventListener = TypingEventListenerImpl(stateRegistry),
//            createChannelListener = CreateChannelListenerImpl(globalState, repos),
        )
    }
}