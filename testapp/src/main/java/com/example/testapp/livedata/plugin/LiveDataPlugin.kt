package com.example.testapp.livedata.plugin

import com.example.testapp.client.plugin.Plugin
import com.example.testapp.client.plugin.listeners.QueryChannelListener
import com.example.testapp.client.plugin.listeners.QueryChannelsListener
import com.example.testapp.client.plugin.listeners.QueryMembersListener

/**
 * Implementation of [Plugin] that brings support for the offline feature. This class work as a delegator of calls for one
 * of its dependencies, so avoid to add logic here.
 *
 * @param queryChannelsListener [QueryChannelsListener]
// * @param queryChannelListener [QueryChannelListener]
// * @param threadQueryListener [ThreadQueryListener]
// * @param channelMarkReadListener [ChannelMarkReadListener]
// * @param editMessageListener [EditMessageListener]
// * @param hideChannelListener [HideChannelListener]
// * @param markAllReadListener [MarkAllReadListener]
// * @param deleteReactionListener [DeleteReactionListener]
// * @param sendReactionListener [SendReactionListener]
// * @param deleteMessageListener [DeleteMessageListener]
// * @param sendGiphyListener [SendGiphyListener]
// * @param shuffleGiphyListener [ShuffleGiphyListener]
// * @param sendMessageListener [SendMessageListener]
 * @param queryMembersListener [QueryMembersListener]
// * @param typingEventListener [TypingEventListener]
// * @param createChannelListener [CreateChannelListener]
 */
internal class LiveDataPlugin(
    private val queryChannelsListener: QueryChannelsListener,
    private val queryChannelListener: QueryChannelListener,
//    private val threadQueryListener: ThreadQueryListener,
//    private val channelMarkReadListener: ChannelMarkReadListener,
//    private val editMessageListener: EditMessageListener,
//    private val hideChannelListener: HideChannelListener,
//    private val markAllReadListener: MarkAllReadListener,
//    private val deleteReactionListener: DeleteReactionListener,
//    private val sendReactionListener: SendReactionListener,
//    private val deleteMessageListener: DeleteMessageListener,
//    private val sendGiphyListener: SendGiphyListener,
//    private val shuffleGiphyListener: ShuffleGiphyListener,
//    private val sendMessageListener: SendMessageListener,
//    private val queryMembersListener: QueryMembersListener,
//    private val typingEventListener: TypingEventListener,
//    private val createChannelListener: CreateChannelListener,
) : Plugin,
    QueryChannelsListener by queryChannelsListener,
    QueryChannelListener by queryChannelListener
//    ThreadQueryListener by threadQueryListener,
//    ChannelMarkReadListener by channelMarkReadListener,
//    EditMessageListener by editMessageListener,
//    HideChannelListener by hideChannelListener,
//    MarkAllReadListener by markAllReadListener,
//    DeleteReactionListener by deleteReactionListener,
//    SendReactionListener by sendReactionListener,
//    DeleteMessageListener by deleteMessageListener,
//    SendGiphyListener by sendGiphyListener,
//    ShuffleGiphyListener by shuffleGiphyListener,
//    SendMessageListener by sendMessageListener,
//    QueryMembersListener by queryMembersListener
//    TypingEventListener by typingEventListener,
//    CreateChannelListener by createChannelListener
{

    override val name: String = MODULE_NAME

    private companion object {
        /**
         * Name of this plugin module.
         */
        private const val MODULE_NAME: String = "LiveData"
    }
}
