package works.chiudesu.djdj.reader

import works.chiudesu.djdj.AudioArray
import works.chiudesu.djdj.audioArrayOf
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.logging.Logger
import javax.sound.sampled.AudioFormat
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
class ExpandAllReader :SeekableAudioReader {

    val logger :Logger = Logger.getLogger(this.javaClass.name)

    override val audioFormat :AudioFormat

    var head :Long = 0
        private set

    val buffer :ByteArray

    val headerSize :Int

    //PCMにデコード
    constructor(audioFile :File){
        val rawstream = AudioSystem.getAudioInputStream(audioFile)
        val rawformat = rawstream.format
        val decodeFormat = AudioFormat( AudioFormat.Encoding.PCM_SIGNED,
                                        rawformat.getSampleRate(),
                                        16,
                                        rawformat.getChannels(),
                                        rawformat.getChannels() * 2,
                                        rawformat.getSampleRate(),
                                        false) // PCMフォーマットを指定
        val decodedstream = AudioSystem.getAudioInputStream(decodeFormat, rawstream)
        audioFormat = decodedstream.format
        logger.info("initializing audioformat : $audioFormat")
        this.headerSize = 0

        //メモリからの読み出し
        var bigbuffer = MutableList<Byte>(32*1024, {0.toByte()})
        val b = ByteArray(4096, {0.toByte()})
        buffer = decodedstream.use {
            var readed = 0
            do{
                val len = it.read(b)
                if(len == -1)break
                b.forEachIndexed { index, value ->
                    if(index >= len)return@forEachIndexed
                    if(readed+index >= bigbuffer.size){
                        //容量追加
                        bigbuffer.addAll(MutableList<Byte>(32*1024, {0.toByte()}))
                    }
                    //ここでは必ず(readed+index < bigbuffer.size)
                    bigbuffer[readed+index] = value
                }
                readed += len
            }while(true)
            bigbuffer.take(readed).toByteArray()
        }
    }

    override fun seek(dest :Long) {
        //与えられるdestはフレーム番号なのでチャンネル・ビット数を考慮したdに変換＋ヘッダサイズ
        val d = dest*audioFormat.frameSize+headerSize
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

        //headがもともと負の位置で、Nextから呼んでもまだ正の位置に達しない場合
        //負の位置のデータは0にする
        var offset = 0
        if(head < 0) {
            //size上限まで、または正の位置にくるまで0で埋まっている
            offset = -head.toInt()
        }

        if(offset < bsize){
            //メモリからの読み出し
            for(i in offset..bsize-1){
                bout[i] = buffer.getOrNull(head.toInt()+i) ?: 0
            }
        }

        head += bsize


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

        return Pair(sout, size)
    }

    override fun close() {
    }
}