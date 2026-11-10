@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.*
import androidx.media3.effect.*
import kotlin.math.*

/** Only the upper of two overlapping main tracks is masked; the lower stays opaque. */
internal class VideoTransitionEffect(private val incoming:String,private val inMs:Long,private val outgoing:String,private val outMs:Long,private val duration:Long):GlEffect {
    internal fun settings(ms:Long):Triple<Int,Float,Float>{
        val entering=inMs>0&&ms<inMs;val leaving=outMs>0&&ms>=duration-outMs
        val type=if(entering)incoming else if(leaving)outgoing else "None"
        val p=if(entering)ms.toFloat()/inMs else if(leaving)(ms-(duration-outMs)).toFloat()/outMs else 1f
        return Triple(videoTransitions.indexOf(type).coerceAtLeast(0),p.coerceIn(0f,1f),if(leaving)1f else 0f)
    }
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram=object:BaseGlShaderProgram(useHdr,1){
        private val program=GlProgram(VideoCutoutEffect.VERTEX,FRAGMENT)
        override fun configure(inputWidth:Int,inputHeight:Int):Size{program.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),4);return Size(inputWidth,inputHeight)}
        override fun drawFrame(inputTexId:Int,presentationTimeUs:Long){try{val(type,p,reverse)=settings(presentationTimeUs/1000);program.use();program.setFloatUniform("uType",type.toFloat());program.setFloatUniform("uProgress",p);program.setFloatUniform("uOutgoing",reverse);program.setSamplerTexIdUniform("uTexSampler",inputTexId,0);program.bindAttributesAndUniforms();GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()}catch(e:Exception){throw VideoFrameProcessingException.from(e,presentationTimeUs)}}
        override fun release(){try{super.release()}finally{program.delete()}}
    }
    companion object{internal const val FRAGMENT="""precision highp float; uniform sampler2D uTexSampler; uniform float uType,uProgress,uOutgoing; varying vec2 vUv;
    void main(){vec4 c=texture2D(uTexSampler,vUv);float a=1.0;
      if(uType>0.5&&uType<1.5)a=uProgress;
      if(uType>1.5&&uType<2.5)a=1.0-smoothstep(uProgress-0.002,uProgress+0.002,vUv.x);
      if(uType>2.5&&uType<3.5)a=1.0-smoothstep(uProgress-0.002,uProgress+0.002,1.0-vUv.x);
      if(uType>3.5&&uType<4.5)a=1.0-smoothstep(uProgress-0.002,uProgress+0.002,1.0-vUv.y);
      if(uType>4.5)a=1.0-smoothstep(uProgress-0.002,uProgress+0.002,vUv.y);
      if(uType>0.5&&uOutgoing>0.5)a=1.0-a;gl_FragColor=vec4(c.rgb,c.a*a);
    }"""}
}
