package com.example.testapp.client.models

import android.graphics.Bitmap
import co.tinode.tinsdk.ComTopic
import co.tinode.tinsdk.Topic
import co.tinode.tinsdk.Topic.TopicType
import co.tinode.tinui.media.VxCard
import java.lang.IllegalStateException
import java.util.*

/**

 */
public data class ChannelTrust(
    var verified: Boolean = false,
    var staff: Boolean = false,
    var danger: Boolean = false,
)
