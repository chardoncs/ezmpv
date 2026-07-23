package dev.chardoncs.ezmpv.browse

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri =
        Uri.parse(decoder.decodeString())
}

enum class IconType { DOWNLOAD, MOVIES, MUSIC, PODCASTS, FOLDER, SDCARD, USB }

@Serializable
data class BrowseBookmark(
    @Serializable(with = UriSerializer::class) val uri: Uri,
    val title: String,
    val iconType: IconType = IconType.FOLDER,
)