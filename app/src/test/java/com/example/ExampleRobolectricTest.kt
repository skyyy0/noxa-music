package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      val options = FirebaseOptions.Builder()
        .setApplicationId("1:421601545057:android:6efa9114fdb644f0ffd320")
        .setApiKey("AIzaSyB-C8kMqLMwLNv5CrnqUk_pH9-PN3Ykcqk")
        .setProjectId("gen-lang-client-0710198930")
        .build()
      FirebaseApp.initializeApp(context, options)
    }
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NOXA", appName)
  }
}
