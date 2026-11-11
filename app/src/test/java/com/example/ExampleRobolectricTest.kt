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
      // Nilai placeholder: test ini tidak memanggil jaringan, jadi tidak perlu
      // kredensial proyek Firebase yang asli (jangan simpan kunci di repo).
      val options = FirebaseOptions.Builder()
        .setApplicationId("1:000000000000:android:0000000000000000000000")
        .setApiKey("test-api-key")
        .setProjectId("noxa-test")
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
