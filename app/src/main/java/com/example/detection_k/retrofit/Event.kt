package com.example.detection_k.retrofit

import com.google.gson.annotations.SerializedName

class Event {
    @SerializedName("EventHeader")
    private var mEventHeader: EventHeader? = null

    @SerializedName("EventBodies")
    private var mEventBodies: List<EventBody>? = null

    class EventHeader {
        @SerializedName("UserId")
        var mUserId: String? = null

        @SerializedName("CameraId")
        var mCameraId = 0

        @SerializedName("Created")
        var mCreated: String? = null

        @SerializedName("Path")
        var mPath: String? = null

        @SerializedName("IsRequiredObjectDetection")
        var mIsRequiredObjectDetection = false
    }

    class EventBody(
        @field:SerializedName("Left") private val mLeft: Int,
        @field:SerializedName("Top") private val mTop: Int,
        @field:SerializedName("Right") private val mRight: Int,
        @field:SerializedName("Bottom") private val mBottom: Int,
        @field:SerializedName("Label") private val mLabel: String
    )

    fun setEventHeader(eventHeader: EventHeader?) {
        mEventHeader = eventHeader
    }

    fun setEventBodies(eventBodies: List<EventBody>?) {
        mEventBodies = eventBodies
    }
}
