package com.practicum.playlist_maker.mediaLibrary.model
import android.os.Parcel
import android.os.Parcelable

data class Content(
    val id: String,
    val name: String,
    val type: ContentType,
    val fileUrl: String? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        ContentType.valueOf(parcel.readString() ?: "FILE"),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(type.name)
        parcel.writeString(fileUrl)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Content> {
        override fun createFromParcel(parcel: Parcel): Content {
            return Content(parcel)
        }

        override fun newArray(size: Int): Array<Content?> {
            return arrayOfNulls(size)
        }
    }
}

enum class ContentType {
    FILE, DIRECTORY
}