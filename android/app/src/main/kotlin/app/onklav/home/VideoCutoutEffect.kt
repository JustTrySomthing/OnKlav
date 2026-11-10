@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Chroma key and geometric alpha masks, shared by composition preview/export. */
internal class VideoCutoutEffect(private val layer:VideoLayer):GlEffect {
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram = Program(layer,useHdr)
    private class Program(private val layer:VideoLayer,hdr:Boolean):BaseGlShaderProgram(hdr,1){
        private val program=try{GlProgram(VERTEX,FRAGMENT)}catch(e:Exception){throw VideoFrameProcessingException.from(e)}
        override fun configure(inputWidth:Int,inputHeight:Int):Size{program.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),4);program.setFloatsUniform("uKey",floatArrayOf(Color.red(layer.keyColor)/255f,Color.green(layer.keyColor)/255f,Color.blue(layer.keyColor)/255f));program.setFloatUniform("uTolerance",layer.keyTolerance);program.setFloatUniform("uChroma",if(layer.chroma)1f else 0f);program.setFloatUniform("uMask",layer.mask.toFloat());program.setFloatUniform("uFeather",layer.feather.coerceAtLeast(.001f));program.setFloatUniform("uAspect",inputWidth.toFloat()/inputHeight);return Size(inputWidth,inputHeight)}
        override fun drawFrame(inputTexId:Int,presentationTimeUs:Long){try{program.use();program.setSamplerTexIdUniform("uTexSampler",inputTexId,0);program.bindAttributesAndUniforms();GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()}catch(e:Exception){throw VideoFrameProcessingException.from(e,presentationTimeUs)}}
        override fun release(){try{super.release()}finally{try{program.delete()}catch(e:Exception){throw VideoFrameProcessingException.from(e)}}}
    }
    companion object {
        internal const val VERTEX="""attribute vec4 aFramePosition; varying vec2 vUv; void main(){gl_Position=aFramePosition;vUv=aFramePosition.xy*0.5+0.5;}"""
        internal const val FRAGMENT="""precision highp float; uniform sampler2D uTexSampler; uniform vec3 uKey; uniform float uTolerance,uChroma,uMask,uFeather,uAspect; varying vec2 vUv;
        void main(){vec4 c=texture2D(uTexSampler,vUv);float alpha=1.0;
          if(uChroma>0.5){vec2 a=vec2(c.r-c.g,c.b-c.g);vec2 b=vec2(uKey.r-uKey.g,uKey.b-uKey.g);float d=length(a-b);alpha=smoothstep(uTolerance,uTolerance+0.16,d);float spill=(1.0-alpha)*0.65;if(uKey.g>uKey.r&&uKey.g>uKey.b)c.g=mix(c.g,min(c.g,max(c.r,c.b)+0.08),spill);}
          if(uMask>0.5&&uMask<1.5){vec2 p=(vUv-0.5)*vec2(max(uAspect,1.0),max(1.0/uAspect,1.0));alpha*=1.0-smoothstep(0.46-uFeather,0.46,length(p));}
          if(uMask>1.5){alpha*=1.0-smoothstep(0.25-uFeather,0.25,abs(vUv.y-0.5));}
          gl_FragColor=vec4(c.rgb,c.a*alpha);
        }"""
    }
}
