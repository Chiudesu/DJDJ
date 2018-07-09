package works.chiudesu.djdj.sample

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.geometry.Orientation
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.SplitPane
import works.chiudesu.djdj.AudioThread
import works.chiudesu.djdj.reader.ExpandAllReader
import works.chiudesu.djdj.script.AudioScript
import works.chiudesu.djdj.script.SpeedControlScript
import works.chiudesu.djdj.viewer.ScriptsViewer
import works.chiudesu.djdj.viewer.WaveGetScript
import works.chiudesu.djdj.viewer.WaveViewer
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.swing.JFrame

fun main(args :Array<String>){

    //ライン用出力フォーマット
    val formatForLine = AudioFormat(44100.0f, 16, 2, true, false)

    //オーディオスレッドが1ループで再生するフレーム数
    val bufferSize = 4096

    //オーディオスレッド作成とstart()
    val audioThread = AudioThread(formatForLine, bufferSize)
    audioThread.start()

    //以後managerを操作する
    val audioManager = audioThread.manager

    //曲生成
    //FileからSeekableAudioReaderへ変換するラムダ式を引数にとる
    val f = { file :File -> ExpandAllReader(file) }
    val sourceID1 = audioManager.createSource("WE_ARE_ONE_V3_free_ver", "wav", f)
    val sourceID2 = audioManager.createSource("WE_ARE_ONE_V3_free_ver", "wav", f)
    audioManager.load(sourceID1)
    audioManager.load(sourceID2)

    //1曲目の速度に2曲目の速度を合わせるには2曲目を何倍速にすればいいか
    val info1 = audioManager.getAdditionalInfoAt(sourceID1)!!
    val info2 = audioManager.getAdditionalInfoAt(sourceID2)!!
    val bpmframe1 = info1.getFrameAtJust(AudioScript.format.CATEGORY_BPM, 0)
    val bpm1 = info1.tags.getValue(AudioScript.format.CATEGORY_BPM).get(bpmframe1!!.toInt())!!.toInt()
    val bpmframe2 = info2.getFrameAtJust(AudioScript.format.CATEGORY_BPM, 0)
    val bpm2 = info2.tags.getValue(AudioScript.format.CATEGORY_BPM).get(bpmframe2!!.toInt())!!.toInt()

    //Script生成
    val speedControlScript = SpeedControlScript()
    speedControlScript.set(sourceID1, 1.0)
    speedControlScript.set(sourceID2, bpm1/bpm2.toDouble())

    //Script登録
    audioManager.addScriptLast(speedControlScript)

    //再生位置を合わせてから曲再生
    audioManager.seek(sourceID1, AudioScript.format.CATEGORY_PLAY, AudioScript.format.TAG_START)
    audioManager.seek(sourceID2, AudioScript.format.CATEGORY_PLAY, AudioScript.format.TAG_START)
    audioManager.play(sourceID1)
    audioManager.play(sourceID2)


    //UI
    val frame = JFrame("ScriptViewer")
    frame.setSize(800,500)
    val jfx = JFXPanel()
    Platform.runLater {
        val sppane = SplitPane()
        sppane.orientation = Orientation.VERTICAL

        //WaveGetScript生成
        val waveGetScript1 = WaveGetScript(sourceID1, 0)
        val waveGetScript2 = WaveGetScript(sourceID2, 0)
        //WaveViewer生成と登録
        val wavev = WaveViewer(interval = 100)
        wavev.scriptList.add(waveGetScript1)
        wavev.scriptList.add(waveGetScript2)
        audioManager.addScriptLast(waveGetScript1)
        audioManager.addScriptLast(waveGetScript2)

        val scriptv = ScriptsViewer(audioManager)

        val root = Group()
        sppane.items.add(wavev)
        sppane.items.add(scriptv)
        root.children.add(sppane)
        sppane.setDividerPosition(0, 0.80)
        val scene = Scene(root)
        jfx.scene = scene

        //WaveViewerをウィンドウに合わせてリサイズするように
        sppane.prefWidthProperty().bind(scene.widthProperty())
        sppane.prefHeightProperty().bind(scene.heightProperty())
    }
    frame.add(jfx)
    frame.isVisible = true
}
