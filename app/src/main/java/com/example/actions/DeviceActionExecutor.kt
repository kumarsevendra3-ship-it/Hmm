package com.example.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class DeviceActionExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ArushiActions"
    }

    data class ActionResult(
        val success: Boolean,
        val message: String,
        val outputJson: JSONObject
    )

    fun execute(toolName: String, args: JSONObject): ActionResult {
        Log.d(TAG, "Executing tool: $toolName with args: $args")
        return when (toolName) {
            "openWhatsApp" -> openWhatsApp()
            "openApp" -> {
                val appName = args.optString("appName", "")
                openApp(appName)
            }
            "openUrl", "openWebsite" -> {
                val url = args.optString("url", args.optString("websiteUrl", ""))
                openUrl(url)
            }
            "makeCall" -> {
                val phoneNumber = args.optString("phoneNumber", "")
                makeCall(phoneNumber)
            }
            "callContact" -> {
                val contactName = args.optString("contactName", "")
                callContact(contactName)
            }
            else -> {
                ActionResult(
                    success = false,
                    message = "Unsupported tool: $toolName",
                    outputJson = JSONObject().put("success", false).put("error", "Unknown tool: $toolName")
                )
            }
        }
    }

    private fun openWhatsApp(): ActionResult {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.whatsapp")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"))

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    message = "Opened WhatsApp",
                    outputJson = JSONObject().put("success", true).put("action", "openWhatsApp")
                )
            } else {
                ActionResult(
                    success = false,
                    message = "WhatsApp is not installed on this device",
                    outputJson = JSONObject()
                        .put("success", false)
                        .put("action", "openWhatsApp")
                        .put("error", "WhatsApp is not installed on this device")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening WhatsApp: ${e.message}", e)
            ActionResult(
                success = false,
                message = "Could not open WhatsApp: ${e.message}",
                outputJson = JSONObject()
                    .put("success", false)
                    .put("action", "openWhatsApp")
                    .put("error", e.message ?: "Failed to launch")
            )
        }
    }

    private fun openApp(rawAppName: String): ActionResult {
        val appName = rawAppName.trim().lowercase()
        val pm = context.packageManager

        return try {
            when {
                appName.contains("whatsapp") -> openWhatsApp()

                appName.contains("youtube") -> {
                    val intent = pm.getLaunchIntentForPackage("com.google.android.youtube")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                    launchIntent(intent, "YouTube")
                }

                appName.contains("instagram") -> {
                    val intent = pm.getLaunchIntentForPackage("com.instagram.android")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com"))
                    launchIntent(intent, "Instagram")
                }

                appName.contains("spotify") -> {
                    val intent = pm.getLaunchIntentForPackage("com.spotify.music")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
                    launchIntent(intent, "Spotify")
                }

                appName.contains("chrome") || appName.contains("browser") -> {
                    val intent = pm.getLaunchIntentForPackage("com.android.chrome")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                    launchIntent(intent, "Browser")
                }

                appName.contains("camera") -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    launchIntent(intent, "Camera")
                }

                appName.contains("map") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=nearby"))
                    launchIntent(intent, "Maps")
                }

                appName.contains("setting") -> {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    launchIntent(intent, "Settings")
                }

                appName.contains("calculator") -> {
                    val intent = Intent().apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    }
                    if (intent.resolveActivity(pm) != null) {
                        launchIntent(intent, "Calculator")
                    } else {
                        val calcIntent = pm.getLaunchIntentForPackage("com.google.android.calculator")
                        if (calcIntent != null) launchIntent(calcIntent, "Calculator")
                        else {
                            ActionResult(
                                success = false,
                                message = "Calculator app not found",
                                outputJson = JSONObject().put("success", false).put("error", "Calculator app not found")
                            )
                        }
                    }
                }

                appName.contains("calendar") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/"))
                    launchIntent(intent, "Calendar")
                }

                appName.contains("phone") || appName.contains("dialer") -> {
                    val intent = Intent(Intent.ACTION_DIAL)
                    launchIntent(intent, "Phone")
                }

                else -> {
                    ActionResult(
                        success = false,
                        message = "App '$rawAppName' is not in the supported applications list",
                        outputJson = JSONObject()
                            .put("success", false)
                            .put("error", "Application '$rawAppName' is not supported or not installed")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app $rawAppName: ${e.message}", e)
            ActionResult(
                success = false,
                message = "Failed to launch $rawAppName: ${e.message}",
                outputJson = JSONObject().put("success", false).put("error", e.message ?: "Launch failed")
            )
        }
    }

    private fun launchIntent(intent: Intent, appDisplayName: String): ActionResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pm = context.packageManager
        return if (intent.resolveActivity(pm) != null) {
            context.startActivity(intent)
            ActionResult(
                success = true,
                message = "Opened $appDisplayName",
                outputJson = JSONObject().put("success", true).put("action", "openApp").put("app", appDisplayName)
            )
        } else {
            ActionResult(
                success = false,
                message = "$appDisplayName is not available on this device",
                outputJson = JSONObject()
                    .put("success", false)
                    .put("action", "openApp")
                    .put("error", "$appDisplayName is not installed or available")
            )
        }
    }

    private fun openUrl(rawUrl: String): ActionResult {
        var cleanUrl = rawUrl.trim()
        if (cleanUrl.isEmpty()) {
            return ActionResult(
                success = false,
                message = "URL cannot be empty",
                outputJson = JSONObject().put("success", false).put("error", "Empty URL")
            )
        }

        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }

        return try {
            val uri = Uri.parse(cleanUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                message = "Opened $cleanUrl",
                outputJson = JSONObject().put("success", true).put("action", "openUrl").put("url", cleanUrl)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL $cleanUrl: ${e.message}", e)
            ActionResult(
                success = false,
                message = "Invalid or unhandled URL",
                outputJson = JSONObject().put("success", false).put("error", e.message ?: "Failed to open URL")
            )
        }
    }

    private fun makeCall(rawPhoneNumber: String): ActionResult {
        val cleanNumber = rawPhoneNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleanNumber.isEmpty()) {
            return ActionResult(
                success = false,
                message = "Invalid phone number provided",
                outputJson = JSONObject().put("success", false).put("error", "Invalid phone number")
            )
        }

        return try {
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber"))
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber"))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            ActionResult(
                success = true,
                message = "Dialing $cleanNumber",
                outputJson = JSONObject()
                    .put("success", true)
                    .put("action", "makeCall")
                    .put("phoneNumber", cleanNumber)
                    .put("dialerOpened", true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call $cleanNumber: ${e.message}", e)
            ActionResult(
                success = false,
                message = "Failed to start phone call: ${e.message}",
                outputJson = JSONObject().put("success", false).put("error", e.message ?: "Dialer error")
            )
        }
    }

    data class ContactInfo(val name: String, val number: String)

    private fun callContact(contactName: String): ActionResult {
        val queryName = contactName.trim()
        if (queryName.isEmpty()) {
            return ActionResult(
                success = false,
                message = "Contact name cannot be empty",
                outputJson = JSONObject().put("success", false).put("error", "Empty contact name")
            )
        }

        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPermission) {
            return ActionResult(
                success = false,
                message = "Contacts permission needed to search for '$queryName'",
                outputJson = JSONObject()
                    .put("success", false)
                    .put("error", "Contacts permission has not been granted yet. Please allow contacts access to call by name.")
            )
        }

        return try {
            val contacts = searchContacts(queryName)

            when {
                contacts.isEmpty() -> {
                    ActionResult(
                        success = false,
                        message = "No contact found for '$queryName'",
                        outputJson = JSONObject()
                            .put("success", false)
                            .put("error", "I couldn't find any contact named '$queryName' in your address book.")
                    )
                }

                contacts.size == 1 -> {
                    val contact = contacts.first()
                    makeCall(contact.number).copy(
                        message = "Calling ${contact.name} (${contact.number})",
                        outputJson = JSONObject()
                            .put("success", true)
                            .put("action", "callContact")
                            .put("contactName", contact.name)
                            .put("phoneNumber", contact.number)
                    )
                }

                else -> {
                    // Multiple matches found - ask the user to clarify
                    val candidateNames = contacts.take(4).map { "${it.name} (${it.number})" }
                    val array = JSONArray()
                    candidateNames.forEach { array.put(it) }

                    ActionResult(
                        success = false,
                        message = "Found ${contacts.size} contacts for '$queryName'",
                        outputJson = JSONObject()
                            .put("success", false)
                            .put("error", "Found ${contacts.size} contacts matching '$queryName': ${candidateNames.joinToString(", ")}. Which one would you like to call?")
                            .put("candidates", array)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts: ${e.message}", e)
            ActionResult(
                success = false,
                message = "Error searching contacts: ${e.message}",
                outputJson = JSONObject().put("success", false).put("error", e.message ?: "Contact search failed")
            )
        }
    }

    private fun searchContacts(query: String): List<ContactInfo> {
        val results = mutableListOf<ContactInfo>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext() && results.size < 5) {
                val name = cursor.getString(nameIndex) ?: ""
                val number = cursor.getString(numberIndex) ?: ""
                if (number.isNotBlank()) {
                    results.add(ContactInfo(name, number))
                }
            }
        }

        return results
    }
}
