package works.chiudesu.djdj.viewer

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Orientation
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.ScrollBar
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.RowConstraints

class ScalableCanvas :GridPane() {

    private val canvas :LayeredCanvas
    private val hScrollBar :ScrollBar
    private val vScrollBar :ScrollBar

    //Canvas実際表示サイズ
    val cwidth :Double
        get() = canvas.width
    val cheight :Double
        get() = canvas.height

    //Canvas仮想表示サイズ
    val zoomRate = 1.2 //ホイール時拡大縮小速度
    val zoomProperty :DoubleProperty = SimpleDoubleProperty(1.0)
    var zoom :Double
        set(value) = zoomProperty.set(value)
        get() = zoomProperty.get()

    val vwidth :Double
        get() = 400.0
    val vheight :Double
        get() = 300.0
    val vx :Double
        get() = hScrollBar.value
    val vy :Double
        get() = vScrollBar.value

    //ドラッグによる画面移動用
    private var dragStartX = 0.0
    private var dragStartY = 0.0

    //画面更新コールバック
    var drawCall :(List<GraphicsContext>) -> Unit = {}

    override fun toString() = "ScalableCanvas width=${width}(${cwidth}), height=${height}(${cheight})"

    init {
        val clm1 = ColumnConstraints()
        clm1.hgrow = Priority.ALWAYS
        val clm2 = ColumnConstraints()
        clm2.hgrow = Priority.NEVER
        val row1 = RowConstraints()
        row1.vgrow = Priority.ALWAYS
        val row2 = RowConstraints()
        row2.vgrow = Priority.NEVER
        this.columnConstraints.addAll(clm1, clm2)
        this.rowConstraints.addAll(row1, row2)

        canvas = LayeredCanvas()
        canvas.setMinSize(10.0, 10.0)
        this.add(canvas, 0, 0)

        hScrollBar = ScrollBar()
        hScrollBar.min = 0.0
        hScrollBar.value = 0.0
        hScrollBar.max = vwidth * zoom - cwidth
        hScrollBar.orientation = Orientation.HORIZONTAL
        this.add(hScrollBar, 0, 1)


        vScrollBar = ScrollBar()
        vScrollBar.min = 0.0
        vScrollBar.value = 0.0
        vScrollBar.max = vheight * zoom - cheight
        vScrollBar.orientation = Orientation.VERTICAL
        this.add(vScrollBar, 1, 0)

        canvas.setOnScroll({ event ->
            if(event.deltaY >= 0) zoomUpdate(zoom * zoomRate, event.x, event.y)//拡大
            else zoomUpdate(zoom / zoomRate, event.x, event.y)//縮小
            reDraw()
        })
        canvas.setOnMousePressed({ event ->
            if(event.isSecondaryButtonDown) {
                dragStartX = event.x
                dragStartY = event.y
            }
        })
        canvas.setOnMouseDragged({ event ->
            if(event.isSecondaryButtonDown) {
                posUpdate(vx - (event.x - dragStartX), vy - (event.y - dragStartY))
                dragStartX = event.x
                dragStartY = event.y
                reDraw()
            }
        })
        canvas.widthProperty().addListener({ observable ->
            scrollMaxUpdate()
            reDraw()
        })
        canvas.heightProperty().addListener({ observable ->
            scrollMaxUpdate()
            reDraw()
        })



        hScrollBar.maxProperty().addListener({ observable -> hScrollBar.visibleAmount = hScrollBar.max * cwidth / vwidth / zoom })
        vScrollBar.maxProperty().addListener({ observable -> vScrollBar.visibleAmount = vScrollBar.max * cheight / vheight / zoom })
        zoomProperty.addListener({ observable -> scrollMaxUpdate() })

        hScrollBar.valueProperty().addListener({ observable -> reDraw() })
        vScrollBar.valueProperty().addListener({ observable -> reDraw() })
    }

    private fun posUpdate(x :Double, y :Double) {
        hScrollBar.value =
                when {
                    (x < 0) -> 0.0
                    (x > hScrollBar.max) -> hScrollBar.max
                    else -> x
                }
        vScrollBar.value =
                when {
                    (y < 0) -> 0.0
                    (y > vScrollBar.max) -> vScrollBar.max
                    else -> y
                }
    }

    private fun scrollMaxUpdate() {
        val wm = vwidth * zoom - cwidth
        val hm = vheight * zoom - cheight
        hScrollBar.max =
                if(wm >= 0) wm
                else 0.0
        vScrollBar.max =
                if(hm >= 0) hm
                else 0.0
    }

    private fun zoomUpdate(value :Double, cx :Double, cy :Double) {
        zoom = value
        posUpdate(vx, vy)
    }

    fun reDraw() {
        drawCall(canvas.graphicsContexts)
    }


    private class ResizableCanvas :Canvas() {
        override fun isResizable() = true
    }

    fun newLayer(){
        val c = ResizableCanvas()
        canvas.addLayer(c)
    }
}
