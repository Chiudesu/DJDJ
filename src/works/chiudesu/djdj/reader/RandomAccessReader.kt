package works.chiudesu.djdj.reader

import works.chiudesu.djdj.AudioArray
import works.chiudesu.djdj.audioArrayOf
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.logging.Logger
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * InputStreamからbyte列を読み出す
 * 必ずclose()で終了されるべきである
 * 負を含む任意のindexにseekできる
 * 負のindexでは0が読み出される
 * バッファを超えた読み込み済みの位置にseekする場合AudioInputStreamを再取得するため、
 * バッファは十分な長さを設定する必要がある
 *
 * RanodmAccessAudioはread命令の度にデータを読み、メモリに全展開することはしないので
 * リソースマネージャ等で先にメモリに用意したりするのは不必要
 */
class RandomAccessReader :SeekableAudioReader {

    val logger :Logger = Logger.getLogger(this.javaClass.name)

    private var stream :AudioInputStream
    val audioFileFormat :AudioFileFormat
    override val audioFormat :AudioFormat
        get() = stream.format

    var head :Long = 0
        private set

    /**
     * 過去のバッファ
     * この範囲内なら飛べる
     * 0 過去 - lastindex 現在
     */
    var prevBuffer :MutableList<Byte> = mutableListOf()
        private set

    /**
     * 未来のバッファ
     * 過去に飛んだ後、読み込みの際に使う
     * 0 現在 - lastindex 未来
     */
    var nextBuffer :MutableList<Byte> = mutableListOf()
        private set

    private val audioFile :File
    val bufferMaxSize :Int
    val headerSize :Int

    //与えられるファイルはPCMでなければいけない
    constructor(audioFile :File, bufferFMaxSize :Int){
        audioFileFormat = AudioSystem.getAudioFileFormat(audioFile)
        logger.info("initializing audioformat : $audioFileFormat")
        stream = AudioInputStream(audioFile.inputStream(), audioFileFormat.format, audioFileFormat.byteLength.toLong())
        this.audioFile = audioFile
        this.bufferMaxSize = bufferFMaxSize*audioFormat.frameSize
        this.headerSize = audioFileFormat.byteLength-audioFileFormat.frameLength*audioFormat.frameSize
    }

    override fun seek(dest :Long) {
        //与えられるdestはフレーム番号なのでチャンネル・ビット数を考慮したdに変換＋ヘッダサイズ
        val d = dest*audioFormat.frameSize+headerSize
        //destが負の場合skip先は0
        if(d == head) {
            //seek先が現在な場合
            //なにもしない


        } else if(d < head - Math.min(prevBuffer.size, bufferMaxSize)) {
            //過去にseekし、バッファを超える場合
            //streamの再生成
            recreateStream()

            stream.skip(if(d >= 0) d else 0)


        } else if(d < head) {
            //過去にseekし、バッファを超えない場合
            //乗り越えた分の過去のバッファを未来のバッファへ移動する
            nextBuffer = prevBuffer.takeLast((head - d).toInt()).toMutableList()
            prevBuffer = prevBuffer.dropLast((head - d).toInt()).toMutableList()


        } else {
            //未来にseekする場合
            //TODO:バッファを初期化しないより効率的な方法

            //バッファは初期化する
            prevBuffer = mutableListOf()
            nextBuffer = mutableListOf()
            stream.skip(if(d - head >= 0) d - head else 0)
        }

        head = d
    }

    /**
     * 引数outに値を詰めて返し、関数の返り値は読み込んだサイズ、あるいは終端を表す-1
     * val readedSize :Int = this.read(prevBuffer, prevBuffer.size)
     */
    override fun read(size :Int) :Pair<AudioArray, Int> {

        val bitsper8 :Int = audioFormat.sampleSizeInBits/8
        val bsize = size * audioFormat.frameSize
        val bout = ByteArray(bsize, { 0 })
        var offset :Int = 0

        //未来のバッファを先に読み出す
        if(nextBuffer.size > 0) {
            val offsetNext = Math.min(nextBuffer.size, bsize)//未来のバッファが残っている場合にずれるオフセット
            for(i in 0..offsetNext - 1) {
                bout[i] = nextBuffer[i]
            }
            //消費した未来のバッファは削除
            nextBuffer = nextBuffer.drop(offsetNext).toMutableList()

            offset = offsetNext
        }

        //headがもともと負の位置で、Nextから呼んでもまだ正の位置に達しない場合
        //負の位置のデータは0にする
        if(head + offset < 0) {
            val offsetMinus = (-head - offset).toInt()//head + offset + offsetMinus == 0

            //size上限まで、または正の位置にくるまで0で埋める
            for(i in offset..Math.min(bsize - 1, offset + offsetMinus - 1)) {
                bout[i] = 0
            }

            offset += offsetMinus
        }


        //streamからの読み出し
        val readedSize :Int =
            if(0 <= offset && offset < bout.size && 0 < bsize - offset)
                stream.read(bout, offset, bsize - offset)//本当に必要な時のみreadを呼び出す(オーバーヘッドが大きいため)
            else
                bsize

        head += bsize


        //outに読み出した値はbufferにも蓄えられる
        prevBuffer.addAll(bout.toList())
        //bufferMaxSizeを超えた分は削除
        if(prevBuffer.size > bufferMaxSize)
            prevBuffer = prevBuffer.drop(prevBuffer.size - bufferMaxSize).toMutableList()

        //byteからチャンネル分けされたInt配列へ変換
        val endian =
            if(audioFormat.isBigEndian)
                ByteOrder.BIG_ENDIAN
            else
                ByteOrder.LITTLE_ENDIAN
        val sout = audioArrayOf(audioFormat.sampleRate, audioFormat.channels, size, { c, f ->
            val pos = f * bitsper8 * audioFormat.channels + c * audioFormat.channels

            val b = ByteArray(bitsper8, { bout[pos + it] })
            val order = ByteBuffer.wrap(b).order(endian)
            val ret =
                if(bitsper8 == 4) order.getInt()
                else if(bitsper8 == 2) order.getShort().toInt()
                else if(bitsper8 == 1) order.get().toInt()
                else throw RuntimeException()
            ret
        })
        val rout =
            if(readedSize == -1)    -1
            else                    (offset + readedSize)/audioFormat.frameSize

        return Pair(sout, rout)
    }

    /**
     * streamの再生成
     * bufferも初期化される
     */
    private fun recreateStream() {
        stream.close()

        val newstream :InputStream = audioFile.inputStream()
        stream = AudioInputStream(newstream, audioFileFormat.format, audioFileFormat.byteLength.toLong())
        prevBuffer = mutableListOf()
        nextBuffer = mutableListOf()
    }

    override fun close() {
        stream.close()
    }
}