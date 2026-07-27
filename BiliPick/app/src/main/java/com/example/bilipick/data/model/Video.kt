package com.example.bilipick.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Video(
    val id: String,
    val bvid: String,
    val title: String,
    @SerialName("up_name")
    val upName: String,
    val duration: Int,
    val category: String,
    val cover: String = "",
    @SerialName("added_at")
    val addedAt: String,
    @SerialName("added_by")
    val addedBy: String
)

@Serializable
data class VideoCreateRequest(
    val bvid: String,
    val title: String,
    @SerialName("up_name")
    val upName: String,
    val duration: Int,
    val category: String,
    val cover: String = "",
    @SerialName("added_by")
    val addedBy: String = "未知设备"
)

@Serializable
data class BiliVideoInfo(
    val bvid: String,
    val title: String,
    val owner: Owner,
    val duration: Int,
    val tname: String? = null,
    val pic: String? = null
) {
    @Serializable
    data class Owner(
        val name: String
    )
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    @SerialName("display_order")
    val displayOrder: Int = 0
)

@Serializable
data class CategoryCreateRequest(
    val name: String,
    @SerialName("display_order")
    val displayOrder: Int = 0
)

@Serializable
data class CategoryUpdateRequest(
    val name: String? = null,
    @SerialName("display_order")
    val displayOrder: Int? = null
)

@Serializable
data class VideoCategoryUpdate(
    val category: String
)

@Serializable
data class Series(
    val id: String,
    val title: String,
    val cover: String = "",
    val description: String = "",
    @SerialName("display_order")
    val displayOrder: Int = 0,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("video_count")
    val videoCount: Int = 0
)

@Serializable
data class SeriesCreateRequest(
    val title: String,
    val cover: String = "",
    val description: String = "",
    @SerialName("display_order")
    val displayOrder: Int = 0
)

@Serializable
data class SeriesUpdateRequest(
    val title: String? = null,
    val cover: String? = null,
    val description: String? = null,
    @SerialName("display_order")
    val displayOrder: Int? = null
)

@Serializable
data class SeriesVideoAddRequest(
    val video_ids: List<String>,
    val episode_index: Int? = null
)
