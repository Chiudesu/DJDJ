package works.chiudesu.djdj.sample

import works.chiudesu.djdj.AudioThread
import works.chiudesu.djdj.reader.ExpandAllReader
import java.io.File
import javax.sound.sampled.AudioFormat

fun main(args :Array<String>){
    //ライン用出力フォーマット
    val formatForLine = AudioFormat(44100.0f, 16, 2, true, false)

    //オーディオスレッドが1ループで再生するフレーム数
    val bufferSize = 735

    //オーディオスレッド作成とstart()
    val audioThread = AudioThread(formatForLine, bufferSize)
    audioThread.start()

    //以後managerを操作する
    val audioManager = audioThread.manager

    //曲生成
    //FileからSeekableAudioReaderへ変換するラムダ式を引数にとる
    val f = { file :File -> ExpandAllReader(file) }
    val sourceID = audioManager.createSource("WE_ARE_ONE_V3_free_ver", "wav", f)

    //IDを使って曲再生
    audioManager.play(sourceID)
}