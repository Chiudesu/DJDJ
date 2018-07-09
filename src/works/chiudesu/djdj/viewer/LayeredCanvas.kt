package works.chiudesu.djdj.viewer

import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.Pane

class LayeredCanvas :Pane(){

    private val canvas :MutableList<Canvas>
    val size :Int
        get() = canvas.size
    val graphicsContexts :List<GraphicsContext>
        get() = canvas.map{it.graphicsContext2D}

    init{
        canvas = mutableListOf()
    }

    override fun isResizable() = true

    fun newLayer(){
        val c = Canvas()
        addLayer(c)
    }

    fun addLayer(c :Canvas){
        initializeCanvas(c)
    }

    private fun initializeCanvas(c :Canvas){
        canvas.add(c)
        this.children.add(c)

        c.width = this.width
        c.height = this.height

        this.widthProperty().addListener { event ->
            c.width = this.width
        }
        this.heightProperty().addListener { event ->
            c.height = this.height
        }
    }
}