package com.kalotrapezis.drive

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

@RunWith(AndroidJUnit4::class)
class SceneModelDeviceTest {
    @Test fun bundledEfficientNetModelLoads() {
        ImageClassifier.createFromFile(ApplicationProvider.getApplicationContext(), "efficientnet_lite0.tflite").close()
    }
}
