# Third-party notices

## MobileFaceNet face-embedding model

`app/src/main/assets/mobilefacenet.tflite` is sourced from
`hugocornellier/face_detection_tflite`, commit retrieved on 2026-09-20.
The upstream project declares the model Apache License 2.0. The bundled file's
SHA-256 is `be4bc7cfc53f7bc336d0f28b1ab92535f618c913a422b683210750f6b5354854`.

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0

## EfficientNet-Lite0 image-classification model

`app/src/main/assets/efficientnet_lite0.tflite` is Google’s MediaPipe
EfficientNet-Lite0 int8 image-classification model, retrieved on 2026-09-20
from `https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/latest/efficientnet_lite0.tflite`.
The model is used on-device alongside ML Kit; its SHA-256 is
`bc2ffe19c1118de0c0c2a9088992da5589722656e0fba81421385300a4a34b16`.
MediaPipe is licensed under Apache License 2.0:
https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE

## MapLibre Native and OpenFreeMap

The in-app photo map uses MapLibre Native Android 13.6.1, licensed under the
BSD 2-Clause License. Map tiles and styles are provided by OpenFreeMap using
OpenMapTiles and OpenStreetMap data. MapLibre displays the required map-data
attribution in the map UI.

MapLibre license: https://github.com/maplibre/maplibre-native/blob/main/LICENSE.md
OpenFreeMap: https://openfreemap.org/

## OpenCV

Document page detection uses OpenCV 4.12.0 for Android (`org.opencv:opencv`),
licensed under Apache License 2.0: https://github.com/opencv/opencv/blob/4.x/LICENSE

## Google ML Kit and CameraX

Barcode scanning (`com.google.mlkit:barcode-scanning`), text recognition,
face detection and image labelling run on-device through Google ML Kit, used
under the ML Kit Terms of Service: https://developers.google.com/ml-kit/terms
Camera capture uses AndroidX CameraX, licensed under Apache License 2.0.
