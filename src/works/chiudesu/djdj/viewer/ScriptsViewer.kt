package works.chiudesu.djdj.viewer

import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.BorderPane
import works.chiudesu.djdj.AudioManager
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * JavaFXでScript一覧を表示する
 * @param interval 更新間隔\[ms]
 * @param manager AudioManager
 *
 * ただ.show()すればよい
 */
class ScriptsViewer(private val manager :AudioManager, val interval :Long = 32) :BorderPane() {

    @FXML
    private lateinit var scriptTable :TableView<AudioScriptItem>
    @FXML
    private lateinit var columnIndex :TableColumn<AudioScriptItem,Int>
    @FXML
    private lateinit var columnName :TableColumn<AudioScriptItem,String>

    private val updateTimer :ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    init {
        val fxmlLoader = FXMLLoader(this.javaClass.classLoader.getResource("works/chiudesu/djdj/viewer/ScriptsViewer.fxml"))
        fxmlLoader.setRoot(this)
        fxmlLoader.setController(this)
        fxmlLoader.load<ScriptsViewer>()
    }

    @FXML
    fun initialize() {
        columnIndex.cellValueFactory = PropertyValueFactory<AudioScriptItem,Int>("index")
        columnIndex.sortType = TableColumn.SortType.ASCENDING
        columnIndex.isSortable = false
        columnName.cellValueFactory = PropertyValueFactory<AudioScriptItem,String>("name")
        columnName.isSortable = false
        scriptTable.columns.setAll(columnIndex, columnName)

        updateTimer.scheduleWithFixedDelay(ViewerTask(), interval, interval, TimeUnit.MILLISECONDS)
    }

    /** 画面更新処理をTimerによって定期的に行う */
    private inner class ViewerTask :Runnable{

        override fun run() {
            scriptTable.items.setAll(manager.getScripts().mapIndexed{index, script ->  AudioScriptItem(index, script.javaClass.name)})
        }
    }

    data class AudioScriptItem(var index :Int, var name :String)
}