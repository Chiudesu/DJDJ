package works.chiudesu.djdj.viewer

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.BorderPane
import javafx.scene.paint.Color
import javafx.scene.transform.Affine
import works.chiudesu.djdj.script.ScriptUtility
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * JavaFXで波形を表示する
 * @param parent 親ウィンドウ
 * @param interval 更新間隔\[ms]
 * @param freqSize 周波数軸表示の分解能 > AudioManager.bufferSize
 *
 * ただ.show()すればよい
 */
class WaveViewer(val interval :Long = 32, freqSize :Int = 1024) :BorderPane() {
    val freqSize :Int

    @FXML
    private lateinit var timeCanvas :ScalableCanvas
    @FXML
    private lateinit var freqCanvas :ScalableCanvas
    @FXML
    private lateinit var logViewButton : KeyControlButton

    private var timeGCs :List<GraphicsContext>? = null
    private var freqGCs :List<GraphicsContext>? = null

    val scriptList :MutableList<WaveGetScript> = mutableListOf()
    
    var isLogView = true
        set(value){
            field = value
            requestDrawFAll = true
        }

    private var requestDrawTAll :Boolean = false
    private var requestDrawTOnlyGraph :Boolean = false
    private var requestDrawFAll :Boolean = false
    private var requestDrawFOnlyGraph :Boolean = false

    private val updateTimer :ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    init {
        val fxmlLoader = FXMLLoader(this.javaClass.classLoader.getResource("works/chiudesu/djdj/viewer/WaveViewer.fxml"))
        fxmlLoader.setRoot(this)
        fxmlLoader.setController(this)
        fxmlLoader.load<WaveViewer>()

        //周波数分解能は2の乗数
        this.freqSize = ScriptUtility.getFFTSize(freqSize)
    }

    fun reDraw() {
        //modelのrequestに沿って描画を行う
        //requestがなければ無駄な描画はしない

        if(timeGCs == null)timeCanvas.reDraw()
        if(timeGCs != null){
            var isCompleted = false
            if(requestDrawTAll){
                isCompleted = drawCallTBase(timeGCs!!.first())
            }
            if(requestDrawTAll || requestDrawTOnlyGraph){
                drawCallTGraph(timeGCs!!.last())

                if(isCompleted)requestDrawTAll = false
                requestDrawTOnlyGraph = false
            }
        }


        if(freqGCs == null)freqCanvas.reDraw()
        if(freqGCs != null){
            var isCompleted = false
            if(requestDrawFAll){
                isCompleted = drawCallFBase(freqGCs!!.first())
            }
            if(requestDrawFAll || requestDrawFOnlyGraph){
                drawCallFGraph(freqGCs!!.last())

                if(isCompleted)requestDrawFAll = false
                requestDrawFOnlyGraph = false
            }
        }
    }

    private fun drawCallT(gc :List<GraphicsContext>) {
        timeGCs = gc
        requestDrawTAll = true
    }

    //成功したらtrue
    private fun drawCallTBase(gc :GraphicsContext) :Boolean{
        val canvas = timeCanvas

        val vwidth = canvas.vwidth
        val vheight = canvas.vheight

        val af = Affine()
        gc.transform = af
        gc.fill = Color.WHITE
        gc.fillRect(0.0, 0.0, canvas.cwidth, canvas.cheight)

        //vwidth,vheightに合わせた描画位置を後でxy:-vx, wh:*zoomする
        af.appendTranslation(-canvas.vx, -canvas.vy)
        af.appendScale(canvas.zoom, canvas.zoom)
        gc.transform = af

        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, vwidth, vheight)

        //lengthに合わせた線
        val buffer = scriptList.getOrNull(0)?.wave
        buffer?.forEachIndexed { j, value ->
            val length = buffer.size.toDouble()
            val xfun = { it :Int -> (vwidth - 100) * it / length + 50 }
            val x = xfun(j)

            gc.stroke = Color.GRAY
            gc.lineWidth = 0.1
            gc.setLineDashes(1.0)
            gc.strokeLine(x, 50.0, x, vheight - 50)
        }
        //基準線
        gc.stroke = Color.WHITE
        gc.lineWidth = 0.5
        gc.setLineDashes(1.0)
        gc.strokeLine(50.0, vheight / 2, vwidth - 50, vheight / 2)


        if(buffer == null){//まだ描けてない
            return false
        }
        return true
    }

    private fun drawCallTGraph(gc :GraphicsContext) {
        val canvas = timeCanvas

        val vwidth = canvas.vwidth
        val vheight = canvas.vheight

        val af = Affine()
        gc.transform = af
        gc.clearRect(0.0, 0.0, canvas.cwidth, canvas.cheight)

        //vwidth,vheightに合わせた描画位置を後でxy:-vx, wh:*zoomする
        af.appendTranslation(-canvas.vx, -canvas.vy)
        af.appendScale(canvas.zoom, canvas.zoom)
        gc.transform = af

        //lengthに合わせた線
        scriptList.forEachIndexed { index, script ->
            val buffer = script.wave
            val length = buffer.size.toDouble()
            val max = when(script.sampleSizeInBits){
                8 -> Byte.MAX_VALUE.toInt()
                16 -> Short.MAX_VALUE.toInt()
                32 -> Int.MAX_VALUE
                else -> Int.MAX_VALUE
            }
            val xfun = { it :Int -> (vwidth - 100) * it / length + 50 }
            val yfun = { it :Int -> vheight / 2 - (vheight - 100) / 2.0 * it / max }


            //折れ線グラフ
            val colorNode =
                when(index) {
                    0 -> Color.MAGENTA
                    else -> Color.CYAN
                }
            val colorLine =
                when(index) {
                    0 -> Color.RED
                    else -> Color.BLUE
                }

            drawGraphLine(gc, canvas, buffer.map { it.toDouble() }, colorNode, colorLine, xfun, { yfun(it.toInt()) })
        }
    }

    private fun drawCallF(gc :List<GraphicsContext>) {
        freqGCs = gc
        requestDrawFAll = true
    }

    private fun drawCallFBase(gc :GraphicsContext) :Boolean {
        val canvas = freqCanvas

        val vwidth = canvas.vwidth
        val vheight = canvas.vheight
        val firstscript = scriptList.getOrNull(0)
        val hz :Float = firstscript?.hz ?: 44100.0f
        val buffersize = freqSize/2
        val length = if(isLogView)Math.log10(buffersize.toDouble())
                        else ScriptUtility.getFFTSize(freqSize)/2.0

        val xfun :(Double)->Double =
            if(isLogView) { it :Double -> (vwidth - 100) * Math.log10(it + 1.0) / length + 50 }
            else { it :Double -> (vwidth - 100) * it / length + 50 }

        //背景1
        val af = Affine()
        gc.transform = af
        gc.fill = Color.WHITE
        gc.fillRect(0.0, 0.0, canvas.cwidth, canvas.cheight)

        //vwidth,vheightに合わせた描画位置を後でxy:-vx, wh:*zoomする
        af.appendTranslation(-canvas.vx, -canvas.vy)
        af.appendScale(canvas.zoom, canvas.zoom)
        gc.transform = af

        //背景2
        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, vwidth, vheight)

        //10^h[Hz]の線
        var h = 1.0
        var i = buffersize * h / hz * 2
        gc.lineWidth = 0.5
        gc.stroke = Color.WHITE
        gc.setLineDashes(1.0)
        while(i <= buffersize) {
            val ix = xfun(i)
            gc.strokeLine(ix, 50.0, ix, vheight - 50)

            h *= 10
            i = buffersize * h / hz * 2
        }
        //基準線
        gc.strokeLine(50.0, vheight - 50, vwidth - 50, vheight - 50)

        //lengthに合わせた線
        for(j in 0..buffersize-1){
            val x = xfun(j.toDouble())

            gc.stroke = Color.GRAY
            gc.lineWidth = 0.1
            gc.setLineDashes(1.0)
            gc.strokeLine(x, 50.0, x, vheight - 50)
        }

        if(firstscript == null){//まだ描けてない
            return false
        }
        return true
    }

    private fun drawCallFGraph(gc :GraphicsContext) {
        val canvas = freqCanvas

        val vwidth = canvas.vwidth
        val vheight = canvas.vheight

        //背景1
        val af = Affine()
        gc.transform = af
        gc.clearRect(0.0, 0.0, canvas.cwidth, canvas.cheight)

        //vwidth,vheightに合わせた描画位置を後でxy:-vx, wh:*zoomする
        af.appendTranslation(-canvas.vx, -canvas.vy)
        af.appendScale(canvas.zoom, canvas.zoom)
        gc.transform = af

        //折れ線グラフ
        scriptList.forEachIndexed { index, script ->
            val buffer = script.wave
            val max = when(script.sampleSizeInBits) {
                8 -> Byte.MAX_VALUE.toInt()
                16 -> Short.MAX_VALUE.toInt()
                32 -> Int.MAX_VALUE
                else -> Int.MAX_VALUE
            }

            val colorNode =
                when(index) {
                    0 -> Color.MAGENTA
                    else -> Color.CYAN
                }
            val colorLine =
                when(index) {
                    0 -> Color.RED
                    else -> Color.BLUE
                }

            if(isLogView){
                val ffted = ScriptUtility.stretchFFT(buffer, freqSize*2)
                val levelled = ScriptUtility.log10Levelling(ffted.dropLast(ffted.size/2).map{Math.abs(it)})
                val normalized = levelled.map{it/max/100.0}
                drawGraphLine(gc, canvas, normalized, colorNode, colorLine, { (vwidth - 100) * it / normalized.size + 50 }, { vheight - 50 - (vheight - 100) * it })
            }else{
                val ffted = ScriptUtility.stretchFFT(buffer, buffer.size)
                val normalized = ffted.dropLast(ffted.size/2).map{Math.abs(it)/max/100.0}
                drawGraphLine(gc, canvas, normalized, colorNode, colorLine, { (vwidth - 100) * it / normalized.size + 50 }, { vheight - 50 - (vheight - 100) * it })
            }
        }
    }

    fun drawGraphLine(gc :GraphicsContext, canvas :ScalableCanvas, list :List<Double>, colorNode :Color, colorLine :Color, xfun :(Int) -> Double, yfun :(Double) -> Double) {
        list.forEachIndexed { index, value ->
            val x = xfun(index)
            val y = yfun(value)

            if(index != 0) {
                val oldx = xfun(index - 1)
                val oldy = yfun(list[index - 1])
                gc.stroke = colorLine
                gc.lineWidth = 0.2
                gc.strokeLine(oldx, oldy, x, y)
            }
            gc.stroke = null
            gc.fill = colorNode
            gc.fillOval(x - 0.5, y - 0.5, 1.0, 1.0)
        }
    }


    @FXML
    fun initialize() {
        timeCanvas.newLayer()//base
        timeCanvas.newLayer()//graph
        timeCanvas.drawCall = ::drawCallT
        freqCanvas.newLayer()//base
        freqCanvas.newLayer()//graph
        freqCanvas.drawCall = ::drawCallF
        logViewButton.onSelect = {isLogView = true}
        logViewButton.onDeselect = {isLogView = false}
        logViewButton.isSelected = true

        updateTimer.scheduleWithFixedDelay(ViewerTask(), interval, interval, TimeUnit.MILLISECONDS)
    }

    /** 画面更新処理をTimerによって定期的に行う */
    private inner class ViewerTask :Runnable{

        override fun run() {

            requestDrawTOnlyGraph = true
            requestDrawFOnlyGraph = true
            Platform.runLater {
                reDraw()
            }
        }
    }
}