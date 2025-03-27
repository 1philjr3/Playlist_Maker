package com.practicum.playlist_maker.mediaLibrary.model

import android.os.Parcel
import android.os.Parcelable

data class Directory(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val parentId: String? = null,
    val contents: List<Content> = emptyList()
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.createTypedArrayList(Content.CREATOR) ?: emptyList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(description)
        parcel.writeString(imageUrl)
        parcel.writeString(parentId)
        parcel.writeTypedList(contents)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Directory> {
        override fun createFromParcel(parcel: Parcel): Directory {
            return Directory(parcel)
        }

        override fun newArray(size: Int): Array<Directory?> {
            return arrayOfNulls(size)
        }
    }
}