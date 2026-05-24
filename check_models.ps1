$modelsDir = "c:\Users\moham\Desktop\Qualcom\DeepShieldAI\app\src\main\assets\models"
$files = @("audio_classifier_int8.tflite", "blazeface.tflite", "deepfake_b0.onnx", "mobilenet_face_int8.tflite", "tiny_vit_int8.onnx", "efficientnet_lite_int8.tflite")

foreach ($f in $files) {
    $path = Join-Path $modelsDir $f
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $header = $bytes[0..7]
    $hexStr = [BitConverter]::ToString($header)
    $size = $bytes.Length
    $ascii = [System.Text.Encoding]::ASCII.GetString($header) -replace '[^\x20-\x7E]', '.'
    Write-Host "$f | Size=$size bytes | Hex=[$hexStr] | ASCII=[$ascii]"
}
