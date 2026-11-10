package app.onklav.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.json.JSONObject

/** System phone-row picker: no address-book enumeration or READ_CONTACTS request. */
internal object SdkContacts {
    fun intent() = Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    fun selected(context: Context, uri: Uri, authorize: () -> Unit): JSONObject {
        authorize()
        require(uri.scheme == "content") { "Choose a contact from Contacts" }
        val selected = context.contentResolver.query(
            uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null
        )?.use { cursor ->
            require(cursor.moveToFirst()) { "Contact no longer exists" }
            val phone = cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER)).orEmpty()
            require(phone.isNotBlank() && phone.length <= 64) { "Choose a contact with a phone number" }
            val raw = cursor.getString(cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)).orEmpty()
            // Match Rust's plain-name contract; do not split a surrogate pair.
            val name = raw.filterNot { it.isISOControl() }.trim().take(80).dropLastWhile { it.isHighSurrogate() }
            JSONObject().put("phone", phone).put("name", name)
        } ?: error("Contact could not be opened")
        authorize()
        return selected
    }
}
