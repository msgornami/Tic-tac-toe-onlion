package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest
import java.util.Locale

object AppSignatureHelper {
    fun getSha1Fingerprint(context: Context): String {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signingSignatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signingSignatures != null && signingSignatures.isNotEmpty()) {
                val md = MessageDigest.getInstance("SHA-1")
                val publicKey = md.digest(signingSignatures[0].toByteArray())
                val hexString = StringBuilder()
                for (i in publicKey.indices) {
                    val appendString = Integer.toHexString(0xFF and publicKey[i].toInt())
                        .uppercase(Locale.US)
                    if (appendString.length == 1) {
                        hexString.append("0")
                    }
                    hexString.append(appendString)
                    if (i < publicKey.size - 1) {
                        hexString.append(":")
                    }
                }
                return hexString.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Could not retrieve SHA-1 автоматически"
    }
}
