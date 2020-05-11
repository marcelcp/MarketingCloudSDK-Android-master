/**
 * Copyright 2019 Salesforce, Inc
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * <p>
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * <p>
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.marketingcloud.learningapp

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.google.android.gms.common.GoogleApiAvailability
import com.salesforce.marketingcloud.InitializationStatus.Status.*
import com.salesforce.marketingcloud.MCLogListener
import com.salesforce.marketingcloud.MarketingCloudConfig
import com.salesforce.marketingcloud.MarketingCloudSdk
import com.salesforce.marketingcloud.UrlHandler
import com.salesforce.marketingcloud.messages.iam.InAppMessage
import com.salesforce.marketingcloud.messages.iam.InAppMessageManager

const val LOG_TAG = "MCSDK"

abstract class BaseLearningApplication : Application(), UrlHandler {
    private val devKey = "a3oG5rwzrnQbdAddTws8K8";
    internal abstract val configBuilder: MarketingCloudConfig.Builder

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            MarketingCloudSdk.setLogLevel(MCLogListener.VERBOSE)
            MarketingCloudSdk.setLogListener(MCLogListener.AndroidLogListener())
        }

        // You MUST initialize the SDK in your Application's onCreate to ensure correct
        // functionality when the app is launched from a background service (receiving push message,
        // entering a geofence, ...)
        MarketingCloudSdk.init(this, configBuilder.build(this)) { initStatus ->
            when (initStatus.status) {
                SUCCESS -> Log.v(LOG_TAG, "Marketing Cloud initialization successful.")
                COMPLETED_WITH_DEGRADED_FUNCTIONALITY -> {
                    Log.v(
                        LOG_TAG,
                        "Marketing Cloud initialization completed with recoverable errors."
                    )
                    if (initStatus.locationsError && GoogleApiAvailability.getInstance().isUserResolvableError(
                            initStatus.playServicesStatus
                        )
                    ) {
                        // User will likely need to update GooglePlayServices through the Play Store.
                        GoogleApiAvailability.getInstance()
                            .showErrorNotification(this, initStatus.playServicesStatus)
                    }
                }
                FAILED -> {
                    // Given that this app is used to show SDK functionality we will hard exit if SDK init outright failed.
                    Log.e(
                        LOG_TAG,
                        "Marketing Cloud initialization failed.  Exiting Learning App with exception."
                    )
                    throw initStatus.unrecoverableException ?: RuntimeException("Init failed")

                }
            }
        }

        MarketingCloudSdk.requestSdk { sdk ->
            sdk.inAppMessageManager.run {

                // Set the status bar color to be used when displaying an In App Message.
                setStatusBarColor(
                    ContextCompat.getColor(
                        this@BaseLearningApplication,
                        R.color.primaryColor
                    )
                )
                // Set the font to be used when an In App Message is rendered by the SDK
                setTypeface(ResourcesCompat.getFont(this@BaseLearningApplication, R.font.fira_sans))

                setInAppMessageListener(object : InAppMessageManager.EventListener {
                    override fun shouldShowMessage(message: InAppMessage): Boolean {
                        // This method will be called before a in app message is presented.  You can return `false` to
                        // prevent the message from being displayed.  You can later use call `InAppMessageManager#showMessage`
                        // to display the message if the message is still on the device and active.
                        return true
                    }

                    override fun didShowMessage(message: InAppMessage) {
                        Log.v(LOG_TAG, "${message.id} was displayed.")
                    }

                    override fun didCloseMessage(message: InAppMessage) {
                        Log.v(LOG_TAG, "${message.id} was closed.")
                    }
                })
            }


        }

        //Appsflyer config
        val conversionDataListener  = object : AppsFlyerConversionListener {
            override fun onConversionDataSuccess(data: MutableMap<String, Any>?) {
                data?.let { cvData ->
                    cvData.map {
                        Log.i(LOG_TAG, "conversion_attribute:  ${it.key} = ${it.value}")
                    }
                }
            }

            override fun onConversionDataFail(error: String?) {
                Log.e(LOG_TAG, "error onAttributionFailure :  $error")
            }

            override fun onAppOpenAttribution(data: MutableMap<String, String>?) {
                data?.map {
                    Log.d(LOG_TAG, "onAppOpen_attribute: ${it.key} = ${it.value}")
                }
            }

            override fun onAttributionFailure(error: String?) {
                Log.e(LOG_TAG, "error onAttributionFailure :  $error")
            }
        }

        AppsFlyerLib.getInstance().init(devKey, conversionDataListener, applicationContext)
        AppsFlyerLib.getInstance().startTracking(this)
        AppsFlyerLib.getInstance().setDebugLog(true);
    }

    override fun handleUrl(context: Context, url: String, urlSource: String): PendingIntent? {
        return PendingIntent.getActivity(
            context,
            1,
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
