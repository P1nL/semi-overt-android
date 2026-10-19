package com.semiovert.app.core.designsystem.solid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.Build
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.semiovert.app.core.designsystem.MotionFramePolicy
import java.util.concurrent.atomic.AtomicBoolean

data class SolidTexture(val id: String,val bitmap: Bitmap,val backColor: Int)
data class SolidRenderSpec(
    val textures: List<SolidTexture?>,val scroll: Float,val roll: Float,val top: Float,
    val pageColor: Int,val yaw: Float=5f,val pitch: Float=0f,val radius: Float=70f,
    val thickness: Float=6f,val coverBlur: Float=0f,
)

/** A single native GLES scene, composited normally beneath Compose navigation.
 * No WebView, per-card SurfaceView, browser bridge, or frame-by-frame bitmap readback.
 */
class SolidSceneView(context: Context): TextureView(context),TextureView.SurfaceTextureListener {
    @Volatile private var latest: SolidRenderSpec?=null
    private class RenderLoop(val thread: HandlerThread) {
        val handler=Handler(thread.looper)
        val scheduled=AtomicBoolean(false)
        var renderer: SolidRenderer?=null
    }
    private var loop: RenderLoop?=null
    @Volatile private var generation=0
    @Volatile private var drawnSpec: SolidRenderSpec?=null
    @Volatile var presentedSpec: SolidRenderSpec?=null
        private set
    var onFrame: (Boolean)->Unit={}
    var onFailure: (Throwable)->Unit={}

    init {surfaceTextureListener=this;isOpaque=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    fun submit(spec: SolidRenderSpec) {if(latest==spec)return;latest=spec;requestFrame()}

    private fun requestFrame() {
        val backend=loop?:return
        if(!backend.scheduled.compareAndSet(false,true))return
        val token=generation
        backend.handler.post {
            backend.scheduled.set(false)
            if(token!=generation)return@post
            val spec=latest?:return@post
            try {backend.renderer?.let {it.render(spec);if(token==generation)drawnSpec=spec}}
            catch(error: RuntimeException) {Log.e("SolidCard","Native solid card render failed",error);post {if(token==generation)onFailure(error)}}
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture,width: Int,height: Int) {
        val token=++generation
        val backend=RenderLoop(HandlerThread("SO-solid-card").apply {start()});loop=backend
        backend.handler.post {
            try {
                if(token==generation)backend.renderer=SolidRenderer(context,texture,width,height)
            } catch(error: RuntimeException) {Log.e("SolidCard","Native solid card initialization failed",error);this@SolidSceneView.post {if(token==generation)onFailure(error)}}
        }
        requestFrame()
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture,width: Int,height: Int) {val backend=loop;backend?.handler?.post {backend.renderer?.resize(width,height)};requestFrame()}
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {drawnSpec?.let {presentedSpec=it;onFrame(it.textures.all {texture ->texture!=null})}}
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {dispose();return true}
    fun dispose() {
        generation++
        val backend=loop;loop=null;drawnSpec=null;presentedSpec=null
        backend?.handler?.post {backend.renderer?.release();backend.renderer=null;backend.thread.quitSafely()}
    }
}

private class SolidRenderer(context: Context,texture: SurfaceTexture,private var width: Int,private var height: Int) {
    private val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val eglContext: EGLContext
    private val surface=Surface(texture)
    private val eglSurface: EGLSurface
    private val meshes=listOf(SolidGeometry.face(false),SolidGeometry.face(true),SolidGeometry.rim())
    private val floor=SolidGeometry.floor()
    private val identity=FloatArray(16).apply {Matrix.setIdentityM(this,0)}
    private val model=FloatArray(16)
    private val view=FloatArray(16)
    private val projection=FloatArray(16)
    private val modelView=FloatArray(16)
    private val shadowView=FloatArray(16).apply {Matrix.setLookAtM(this,0,-250f,60f,1200f,0f,-260f,-100f,0f,1f,0f)}
    private val shadowProjection=FloatArray(16).apply {Matrix.orthoM(this,0,-800f,800f,-800f,800f,1f,2800f)}
    private val shadowModelView=FloatArray(16)
    private val card: GlProgram
    private val depth: GlProgram
    private val floorProgram: GlProgram
    private val textures=java.util.IdentityHashMap<Bitmap,Int>()
    private val framebuffer=IntArray(1)
    private val shadowTexture=IntArray(1)
    private val depthBuffer=IntArray(1)

    init {
        if(Build.VERSION.SDK_INT>=30)surface.setFrameRate(MotionFramePolicy.TARGET_FPS,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        check(display!=EGL14.EGL_NO_DISPLAY&&EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0)) {"EGL display unavailable"}
        fun choose(samples: Int): EGLConfig? {
            val attributes=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_DEPTH_SIZE,24,EGL14.EGL_SAMPLE_BUFFERS,if(samples>0)1 else 0,EGL14.EGL_SAMPLES,samples,EGL14.EGL_NONE)
            val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
            EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0)
            return if(count[0]>0)configs[0] else null
        }
        val config=choose(4)?:choose(0)?:error("No GLES2 depth-buffer configuration")
        eglContext=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        check(eglContext!=EGL14.EGL_NO_CONTEXT) {"EGL context unavailable"}
        eglSurface=EGL14.eglCreateWindowSurface(display,config,surface,intArrayOf(EGL14.EGL_NONE),0)
        check(eglSurface!=EGL14.EGL_NO_SURFACE&&EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext)) {"EGL window unavailable"}
        fun source(name: String)=context.assets.open("solid/$name").bufferedReader().use {it.readText()}
        card=GlProgram(source("card.vert"),source("card.frag"))
        depth=GlProgram(source("depth.vert"),source("depth.frag"))
        floorProgram=GlProgram(source("floor.vert"),source("floor.frag"))
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glFrontFace(GLES20.GL_CCW);GLES20.glCullFace(GLES20.GL_BACK)
        createShadowTarget()
    }

    fun resize(w: Int,h: Int) {width=w;height=h}
    private fun rgb(color: Int)=floatArrayOf(android.graphics.Color.red(color)/255f,android.graphics.Color.green(color)/255f,android.graphics.Color.blue(color)/255f)
    private fun texture(item: SolidTexture): Int {
        textures[item.bitmap]?.let {return it}
        val id=IntArray(1);GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        // OGL's source texture uses flipY; GLUtils doesn't. Flip once during upload.
        val flipped=Bitmap.createBitmap(item.bitmap,0,0,item.bitmap.width,item.bitmap.height,android.graphics.Matrix().apply {preScale(1f,-1f)},false)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,flipped,0);GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        if(flipped!==item.bitmap)flipped.recycle()
        textures[item.bitmap]=id[0]
        return id[0]
    }

    private fun createShadowTarget() {
        GLES20.glGenTextures(1,shadowTexture,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,shadowTexture[0])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,1024,1024,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGenRenderbuffers(1,depthBuffer,0);GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER,depthBuffer[0])
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER,GLES20.GL_DEPTH_COMPONENT16,1024,1024)
        GLES20.glGenFramebuffers(1,framebuffer,0);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,shadowTexture[0],0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,GLES20.GL_DEPTH_ATTACHMENT,GLES20.GL_RENDERBUFFER,depthBuffer[0])
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE) {"Shadow framebuffer incomplete"}
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)
    }

    fun render(spec: SolidRenderSpec) {
        if(width<=0||height<=0)return
        val ids=spec.textures.map {it?.let(::texture)}
        val live=spec.textures.filterNotNull().map {it.bitmap}.toSet()
        textures.keys.filterNot {it in live}.forEach {bitmap ->textures.remove(bitmap)?.let {GLES20.glDeleteTextures(1,intArrayOf(it),0)}}
        val scale=width/412f;val worldHeight=height/scale;val centerY=spec.top+worldHeight/2
        val yaw=Math.toRadians(spec.yaw.toDouble());val pitch=Math.toRadians(spec.pitch.toDouble())
        val horizontal=kotlin.math.cos(pitch)*1200
        val eye=floatArrayOf((kotlin.math.sin(yaw)*horizontal).toFloat(),(-centerY+kotlin.math.sin(pitch)*1200).toFloat(),(kotlin.math.cos(yaw)*horizontal-40).toFloat())
        Matrix.setLookAtM(view,0,eye[0],eye[1],eye[2],0f,-centerY,-40f,0f,1f,0f)
        Matrix.orthoM(projection,0,-206f,206f,-worldHeight/2,worldHeight/2,1f,2500f)
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,0f,-spec.roll*(worldHeight+SolidFlowMath.HEIGHT),0f)
        Matrix.multiplyMM(modelView,0,view,0,model,0);Matrix.multiplyMM(shadowModelView,0,shadowView,0,model,0)
        val frames=spec.textures.indices.map {SolidFlowMath.frame(it,spec.scroll+spec.roll*SolidFlowMath.HEIGHT,spec.radius,spec.thickness,spec.textures.size)}
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer[0]);GLES20.glViewport(0,0,1024,1024)
        GLES20.glClearColor(1f,1f,1f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        depth.use();depth.matrix("projectionMatrix",shadowProjection);depth.matrix("modelViewMatrix",shadowModelView)
        frames.forEachIndexed {index,frame ->if(ids[index]!=null) {deformation(depth,frame,spec);meshes.forEach {depth.draw(it)}}}
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);GLES20.glViewport(0,0,width,height)
        val background=rgb(spec.pageColor);GLES20.glClearColor(background[0],background[1],background[2],1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glEnable(GLES20.GL_CULL_FACE)
        floorProgram.use();floorProgram.matrix("modelMatrix",identity);floorProgram.matrix("modelViewMatrix",view);floorProgram.matrix("projectionMatrix",projection)
        floorProgram.matrix("shadowProjectionMatrix",shadowProjection);floorProgram.matrix("shadowViewMatrix",shadowView)
        floorProgram.vector("uPageColor",background);floorProgram.sampler("tShadow",shadowTexture[0]);floorProgram.draw(floor)
        card.use();card.matrix("modelMatrix",model);card.matrix("modelViewMatrix",modelView);card.matrix("projectionMatrix",projection);card.vector("cameraPosition",eye)
        frames.forEachIndexed {index,frame ->
            val item=spec.textures[index];val id=ids[index]
            if(item!=null&&id!=null) {
                deformation(card,frame,spec);card.vector("uBackColor",rgb(item.backColor));card.number("uCoverBlur",spec.coverBlur);card.sampler("tCard",id)
                meshes.forEachIndexed {material,geometry ->card.number("uMaterial",material.toFloat());card.draw(geometry)}
            }
        }
        val error=GLES20.glGetError();check(error==GLES20.GL_NO_ERROR) {"GLES frame error 0x${error.toString(16)}"}
        check(EGL14.eglSwapBuffers(display,eglSurface)) {"EGL swap failed"}
    }

    private fun deformation(program: GlProgram,frame: SolidFlowMath.Frame,spec: SolidRenderSpec) {
        program.number("uScroll",frame.progress);program.number("uRadius",spec.radius);program.number("uThickness",spec.thickness);program.number("uReturnLine",frame.returnLine);program.number("uLayer",frame.layer)
    }
    fun release() {
        EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext)
        textures.values.forEach {GLES20.glDeleteTextures(1,intArrayOf(it),0)};textures.clear()
        card.release();depth.release();floorProgram.release()
        GLES20.glDeleteFramebuffers(1,framebuffer,0);GLES20.glDeleteTextures(1,shadowTexture,0);GLES20.glDeleteRenderbuffers(1,depthBuffer,0)
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display,eglSurface);EGL14.eglDestroyContext(display,eglContext);EGL14.eglTerminate(display);surface.release()
    }
}

private class GlProgram(vertex: String,fragment: String) {
    private val id=GLES20.glCreateProgram()
    private val uniforms=HashMap<String,Int>()
    init {
        fun shader(type: Int,source: String): Int {
            val shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader)
            val status=IntArray(1);GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
            check(status[0]!=0) {GLES20.glGetShaderInfoLog(shader)}
            return shader
        }
        val vs=shader(GLES20.GL_VERTEX_SHADER,vertex);val fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment)
        GLES20.glAttachShader(id,vs);GLES20.glAttachShader(id,fs)
        GLES20.glBindAttribLocation(id,0,"position");GLES20.glBindAttribLocation(id,1,"normal");GLES20.glBindAttribLocation(id,2,"uv")
        GLES20.glLinkProgram(id);val status=IntArray(1);GLES20.glGetProgramiv(id,GLES20.GL_LINK_STATUS,status,0)
        check(status[0]!=0) {GLES20.glGetProgramInfoLog(id)}
        GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs)
    }
    fun use()=GLES20.glUseProgram(id)
    private fun uniform(name: String)=uniforms.getOrPut(name) {GLES20.glGetUniformLocation(id,name)}
    fun matrix(name: String,value: FloatArray)=GLES20.glUniformMatrix4fv(uniform(name),1,false,value,0)
    fun vector(name: String,value: FloatArray)=GLES20.glUniform3fv(uniform(name),1,value,0)
    fun number(name: String,value: Float)=GLES20.glUniform1f(uniform(name),value)
    fun sampler(name: String,texture: Int) {GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(uniform(name),0)}
    fun draw(geometry: SolidGeometry) {
        val buffer=geometry.vertexBuffer
        val sizes=intArrayOf(3,3,2);val offsets=intArrayOf(0,3,6)
        for(location in 0..2) {
            buffer.position(offsets[location]);GLES20.glVertexAttribPointer(location,sizes[location],GLES20.GL_FLOAT,false,32,buffer);GLES20.glEnableVertexAttribArray(location)
        }
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,geometry.indices.size,GLES20.GL_UNSIGNED_SHORT,geometry.indexBuffer)
    }
    fun release()=GLES20.glDeleteProgram(id)
}
