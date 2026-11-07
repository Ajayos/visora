package dev.visora.cinematic

import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLES2 passes: nebula, energy ribbons, rotating planetary surface, and independent star VBOs. A
 * 960×540 render target is upscaled by SurfaceFlinger; live text and logos remain at native 1080p.
 */
class GalaxySurface(context: Context, private val config: AnimationConfig) :
    GLSurfaceView(context) {
    val galaxy = GalaxyRenderer(config)
    private var active = false

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(galaxy)
        renderMode = RENDERMODE_WHEN_DIRTY
        holder.setFixedSize(960, 540)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private var bufferQuality = 1

    fun frame(time: Float, quality: Int) {
        galaxy.time = time
        galaxy.quality = quality
        if ((quality == 0) != (bufferQuality == 0)) {
            bufferQuality = quality
            holder.setFixedSize(if (quality == 0) 768 else 960, if (quality == 0) 432 else 540)
        }
        if (active) requestRender()
    }

    fun resumeRendering() {
        if (!active) {
            active = true
            onResume()
        }
    }

    fun pauseRendering() {
        if (active) {
            active = false
            onPause()
        }
    }
}

class GalaxyRenderer(private val config: AnimationConfig) : GLSurfaceView.Renderer {
    @Volatile var time = 0f
    @Volatile var quality = 1
    @Volatile var skyReveal = 0f
    @Volatile var nebulaReveal = 0f
    @Volatile var waveReveal = 0f
    @Volatile var planetReveal = 0f
    @Volatile var starReveal = 0f
    @Volatile var warm = false
    @Volatile
    var glReady = false
        private set

    @Volatile
    var glError = 0
        private set

    @Volatile
    var renderedFrames = 0L
        private set

    @Volatile
    var measuredFps = 0f
        private set

    private var sampleNanos = 0L
    private var sampleFrames = 0
    private var backgroundProgram = 0
    private var starsProgram = 0
    private var quadVbo = 0
    private var starsVbo = 0
    private val starCount = 2600
    @Volatile var requestedStars = config.stars
    @Volatile var motion = config.motion
    private val quad = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val stars =
        floats(
            FloatArray(starCount * 4).also { data ->
                val r = Random(7148)
                for (i in 0 until starCount) {
                    data[i * 4] = r.nextFloat() * 2 - 1
                    data[i * 4 + 1] = r.nextFloat() * 1.95f - .95f
                    data[i * 4 + 2] = r.nextFloat() * 6.283f
                    data[i * 4 + 3] = .35f + r.nextFloat()
                }
            },
        )
    private var aPosition = 0
    private var aStar = 0
    private var uTime = 0
    private var uSky = 0
    private var uNebula = 0
    private var uWaves = 0
    private var uPlanet = 0
    private var uWarm = 0
    private var sTime = 0
    private var sAlpha = 0
    private var sSize = 0
    private var width = 960

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        glReady = false
        try {
            backgroundProgram = program(VERTEX, BACKGROUND)
            starsProgram = program(STAR_VERTEX, STAR_FRAGMENT)
            val ids = IntArray(2)
            glGenBuffers(2, ids, 0)
            quadVbo = ids[0]
            starsVbo = ids[1]
            glBindBuffer(GL_ARRAY_BUFFER, quadVbo)
            glBufferData(GL_ARRAY_BUFFER, 8 * 4, quad, GL_STATIC_DRAW)
            glBindBuffer(GL_ARRAY_BUFFER, starsVbo)
            glBufferData(GL_ARRAY_BUFFER, starCount * 4 * 4, stars, GL_STATIC_DRAW)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
            aPosition = glGetAttribLocation(backgroundProgram, "aPosition")
            uTime = glGetUniformLocation(backgroundProgram, "uTime")
            uSky = glGetUniformLocation(backgroundProgram, "uSky")
            uNebula = glGetUniformLocation(backgroundProgram, "uNebula")
            uWaves = glGetUniformLocation(backgroundProgram, "uWaves")
            uPlanet = glGetUniformLocation(backgroundProgram, "uPlanet")
            uWarm = glGetUniformLocation(backgroundProgram, "uWarm")
            aStar = glGetAttribLocation(starsProgram, "aStar")
            sTime = glGetUniformLocation(starsProgram, "uTime")
            sAlpha = glGetUniformLocation(starsProgram, "uAlpha")
            sSize = glGetUniformLocation(starsProgram, "uSize")
            glDisable(GL_DEPTH_TEST)
            glClearColor(.006f, .009f, .025f, 1f)
            glReady = true
        } catch (e: RuntimeException) {
            glError = -1
            RuntimeEvents.publish(
                EventLevel.ERROR,
                "renderer",
                "GPU background unavailable; native live widgets remain active",
            )
            android.util.Log.e("VISORA", "GLES initialization failed", e)
        }
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        this.width = width
        glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(unused: GL10?) {
        if (!glReady) return
        glClear(GL_COLOR_BUFFER_BIT)
        val t = time * motion
        glDisable(GL_BLEND)
        glUseProgram(backgroundProgram)
        glUniform1f(uTime, t)
        glUniform1f(uSky, skyReveal)
        glUniform1f(uNebula, nebulaReveal)
        glUniform1f(uWaves, waveReveal)
        glUniform1f(uPlanet, planetReveal)
        glUniform1f(uWarm, if (warm) 1f else 0f)
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo)
        glEnableVertexAttribArray(aPosition)
        glVertexAttribPointer(aPosition, 2, GL_FLOAT, false, 8, 0)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(aPosition)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE)
        glUseProgram(starsProgram)
        glUniform1f(sTime, t)
        glUniform1f(sAlpha, starReveal)
        glUniform1f(sSize, width / 960f)
        glBindBuffer(GL_ARRAY_BUFFER, starsVbo)
        glEnableVertexAttribArray(aStar)
        glVertexAttribPointer(aStar, 4, GL_FLOAT, false, 16, 0)
        glDrawArrays(GL_POINTS, 0, if (quality == 0) requestedStars * 2 / 3 else requestedStars)
        glDisableVertexAttribArray(aStar)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glDisable(GL_BLEND)
        glError = glGetError()
        renderedFrames++
        sampleFrames++
        val now = System.nanoTime()
        if (sampleNanos == 0L) sampleNanos = now
        if (sampleFrames >= 60) {
            measuredFps = sampleFrames * 1_000_000_000f / (now - sampleNanos)
            sampleFrames = 0
            sampleNanos = now
        }
    }

    private fun shader(type: Int, source: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, source)
        glCompileShader(shader)
        val compiled = IntArray(1)
        glGetShaderiv(shader, GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val message = glGetShaderInfoLog(shader)
            glDeleteShader(shader)
            error("Galaxy shader compilation failed: $message")
        }
        return shader
    }

    private fun program(vertex: String, fragment: String): Int {
        val vs = shader(GL_VERTEX_SHADER, vertex)
        val fs = shader(GL_FRAGMENT_SHADER, fragment)
        val program = glCreateProgram()
        glAttachShader(program, vs)
        glAttachShader(program, fs)
        glLinkProgram(program)
        glDeleteShader(vs)
        glDeleteShader(fs)
        val linked = IntArray(1)
        glGetProgramiv(program, GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { "Galaxy program link failed: ${glGetProgramInfoLog(program)}" }
        return program
    }

    companion object {
        private fun floats(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(values)
                    position(0)
                }

        private const val VERTEX =
            """
            attribute vec2 aPosition;
            varying vec2 vUv;
            void main(){ vUv=aPosition*.5+.5; gl_Position=vec4(aPosition,0.,1.); }
        """
        private const val BACKGROUND =
            """
            precision highp float;
            varying vec2 vUv;
            uniform float uTime,uSky,uNebula,uWaves,uPlanet,uWarm;
            float hash(vec2 p){vec3 q=fract(vec3(p.xyx)*.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z);}
            float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.,1.)),f.x),f.y);}
            vec3 palette(float f){return mix(mix(vec3(.02,.48,1.),vec3(.58,.08,1.),smoothstep(0.,.5,f)),mix(vec3(1.,.08,.65),vec3(1.,.55,.08),smoothstep(.5,1.,f)),smoothstep(.25,.95,f));}
            void main(){
                vec2 p=vUv;
                vec3 color=mix(vec3(.003,.005,.016),vec3(.007,.01,.035),uSky);
                vec2 q=p*vec2(3.1,2.7)+vec2(uTime*.008,-uTime*.006);
                float n=noise(q)*.58+noise(q*2.3)*.28+noise(q*4.8)*.14;
                float cloud=smoothstep(.27,.83,n);
                float patch=exp(-dot((p-vec2(.72,.58))*vec2(2.2,2.),(p-vec2(.72,.58))*vec2(2.2,2.)));
                color+=uNebula*cloud*patch*mix(vec3(.055,.02,.20),vec3(.35,.018,.28),smoothstep(.25,.8,n))*.72;
                color+=uNebula*vec3(.005,.04,.13)*exp(-length((p-vec2(.20,.30))*vec2(2.,3.)))*.5;
                float y=.49+.09*sin(p.x*6.+uTime*.21)+.13*cos(p.x*3.7-uTime*.13);
                float edge=abs(p.x-.5)*2.;
                float width=.018+.085*edge;
                float d=abs(p.y-y);
                float glow=exp(-d*55.);
                float ribbon=(1.-smoothstep(width*.75,width,d))*.09;
                float mesh=pow(.5+.5*sin((p.y-y)*1100.+p.x*20.-uTime*.45),10.)*ribbon;
                color+=uWaves*palette(p.x)*(glow*.5+mesh+ribbon);
                float y2=.50+.08*sin(p.x*5.5-uTime*.18+2.5)+.11*cos(p.x*3.+uTime*.09);
                float d2=abs(p.y-y2);
                color+=uWaves*palette(1.-p.x)*(exp(-d2*95.)*.75+exp(-d2*30.)*.13);
                vec2 planetCoord=vec2((p.x-.5)/1.53,(p.y+.46)/.615);
                float sphere=length(planetCoord);
                float rim=abs(sphere-1.);
                vec3 rimColor=mix(vec3(.8,.15,1.),vec3(.04,.8,1.),smoothstep(.05,.65,p.x));
                color+=uPlanet*rimColor*(exp(-rim*160.)*.9+exp(-rim*40.)*.35);
                if(sphere<1.006){
                    vec2 surface=vec2(p.x+uTime*.002,p.y);
                    float cities=pow(max(0.,noise(surface*90.)),16.);
                    float grid=pow(.5+.5*sin((p.x+uTime*.0008)*80.+p.y*30.),28.)*.08;
                    color=mix(color,vec3(.004,.014,.045)+rimColor*exp(-rim*24.)*.13+vec3(1.,.46,.13)*cities*.8+vec3(.06,.22,.46)*grid,uPlanet*(1.-smoothstep(.997,1.003,sphere)));
                }
                // Deterministic shooting-star streak with a smooth zero-alpha interval at the cycle boundary.
                float shot=mod(uTime+3.,19.);
                vec2 head=vec2(.12+shot*.55,.88-shot*.20);
                vec2 delta=p-head;
                float along=dot(delta,normalize(vec2(-.55,.20)));
                float across=abs(dot(delta,normalize(vec2(.20,.55))));
                float trail=step(0.,along)*exp(-along*45.)*exp(-across*1600.)*(1.-smoothstep(.7,1.4,shot))*smoothstep(0.,.1,shot);
                color+=uSky*vec3(.5,.75,1.)*trail;
                color*=mix(vec3(1.),vec3(1.18,.86,.94),uWarm);
                gl_FragColor=vec4(color,1.);
            }
        """
        private const val STAR_VERTEX =
            """
            attribute vec4 aStar;
            uniform float uTime,uAlpha,uSize;
            varying float vAlpha;
            varying float vBlue;
            void main(){
                vec2 drift=vec2(sin(uTime*.023+aStar.z),cos(uTime*.017+aStar.z))*.004*aStar.w;
                gl_Position=vec4(aStar.xy+drift,0.,1.);
                gl_PointSize=(1.+aStar.w*1.4)*uSize;
                vAlpha=(.16+.5*(.5+.5*sin(uTime*(.25+aStar.w*.15)+aStar.z)))*uAlpha;
                vBlue=aStar.w;
                vAlpha*=smoothstep(-.78,-.65,aStar.y);
            }
        """
        private const val STAR_FRAGMENT =
            """
            precision highp float;
            varying float vAlpha,vBlue;
            void main(){float d=length(gl_PointCoord-.5);float glow=1.-smoothstep(.05,.5,d);gl_FragColor=vec4(mix(vec3(.58,.72,1.),vec3(1.),vBlue*.6),vAlpha*glow);}
        """
    }
}
