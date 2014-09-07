precision mediump float; // Your precision, I use lowp, it's up to you obviously
varying vec2 glFragment_uv; // Texture UV sent from vertex shader

uniform sampler2D    uniform_texture0; // Our WebView's texture! Note that it is NOT (And can't be) sampler2D
void main() {
    gl_FragColor = texture2D( uniform_texture0, glFragment_uv );
}