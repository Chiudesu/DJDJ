package works.chiudesu.djdj.reader

import works.chiudesu.djdj.AudioArray
import java.io.Closeable
import javax.sound.sampled.AudioFormat

/**
 * コンストラクタでロードし、
 * readでバイトデータを出力する
 * 扱うのはバイトデータそのままであり、チャンネル・ビット数によりデータの意味が変わる
 * AudioFormatはAudioFormat.Encoding.PCM_SIGNEDにデコードするように
 */
interface SeekableAudioReader :Closeable{
    val audioFormat :AudioFormat
        get

    fun seek(dest :Long)
    //byte列からフレーム毎の読みやすい値に変換するのもSeekableAudioReader内部で行う
    fun read(size :Int) :Pair<AudioArray, Int>
    override fun close()
}