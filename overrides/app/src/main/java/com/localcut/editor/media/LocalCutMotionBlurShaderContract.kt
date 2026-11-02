package com.localcut.editor.media

internal object LocalCutMotionBlurShaderContract {
    const val MAX_SHADER_SAMPLES = 8

    val vertexShader: String = """
        #version 100
        attribute vec4 aFramePosition;
        uniform mat4 uTransformationMatrix;
        uniform mat4 uTexTransformationMatrix;
        varying vec2 vTexSamplingCoord;
        void main() {
            gl_Position = uTransformationMatrix * aFramePosition;
            vec4 texturePosition = vec4(
                aFramePosition.x * 0.5 + 0.5,
                aFramePosition.y * 0.5 + 0.5,
                0.0,
                1.0
            );
            vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
        }
    """.trimIndent()

    val fragmentShader: String = """
        #version 100
        precision mediump float;
        uniform sampler2D uCurrentSampler;
        uniform sampler2D uHistory0;
        uniform sampler2D uHistory1;
        uniform sampler2D uHistory2;
        uniform sampler2D uHistory3;
        uniform sampler2D uHistory4;
        uniform sampler2D uHistory5;
        uniform sampler2D uHistory6;
        uniform float uWeight0;
        uniform float uWeight1;
        uniform float uWeight2;
        uniform float uWeight3;
        uniform float uWeight4;
        uniform float uWeight5;
        uniform float uWeight6;
        uniform float uWeight7;
        uniform float uSampleCount;
        varying vec2 vTexSamplingCoord;

        void main() {
            vec4 result = texture2D(uCurrentSampler, vTexSamplingCoord) * uWeight0;
            if (uSampleCount > 1.5) {
                result += texture2D(uHistory0, vTexSamplingCoord) * uWeight1;
            }
            if (uSampleCount > 2.5) {
                result += texture2D(uHistory1, vTexSamplingCoord) * uWeight2;
            }
            if (uSampleCount > 3.5) {
                result += texture2D(uHistory2, vTexSamplingCoord) * uWeight3;
            }
            if (uSampleCount > 4.5) {
                result += texture2D(uHistory3, vTexSamplingCoord) * uWeight4;
            }
            if (uSampleCount > 5.5) {
                result += texture2D(uHistory4, vTexSamplingCoord) * uWeight5;
            }
            if (uSampleCount > 6.5) {
                result += texture2D(uHistory5, vTexSamplingCoord) * uWeight6;
            }
            if (uSampleCount > 7.5) {
                result += texture2D(uHistory6, vTexSamplingCoord) * uWeight7;
            }
            gl_FragColor = result;
        }
    """.trimIndent()

    fun currentFrameOnlyWeights(): List<Float> =
        List(MAX_SHADER_SAMPLES) { index -> if (index == 0) 1f else 0f }
}
