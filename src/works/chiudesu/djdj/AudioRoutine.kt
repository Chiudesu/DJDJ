package works.chiudesu.djdj

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.logging.Logger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.DataLine

/**
 * RealtimeAudioManagementSystem
 * オーディオを複数解放し編集して流す
 * 正常な再生のために、play前にopen()、終了前にclose()することが必要
 */
class AudioRoutine {

    val logger :Logger = Logger.getLogger(this.javaClass.name)

    /**
     * @param line 出力先のライン
     * @param size 毎出力時のバッファサイズ
     */
    private val outLine :SourceDataLine
    val lineformat :AudioFormat

    private val manager :AudioManager

    //1フレーム間で使う（updateで更新される）
    private var tmpSources :List<AudioArray> = listOf()

    constructor(line :SourceDataLine, size :Int, manager :AudioManager) {
        outLine = line
        this.manager = manager
        lineformat = outLine.format

        logger.fine("use line format : ${outLine.format}")
    }

    /**
     * 出力先のラインをnullとしてDataLine.infoの自動生成に任せる
     * @param size 毎出力時のバッファサイズ
     */
    constructor(size :Int, manager :AudioManager) {
        //出力ライン自動生成
        val info = DataLine.Info(SourceDataLine::class.java, null)
        val line :SourceDataLine = AudioSystem.getLine(info) as SourceDataLine//SourceDataLineじゃなかったらException

        outLine = line
        this.manager = manager
        lineformat = outLine.format
        logger.fine("use line format : ${outLine.format}")
    }


    //format==nullでデフォルト処理
    fun open(format :AudioFormat?) {
        if(format != null) {
            outLine.open(format, manager.bufferSize * format.frameSize)
        } else {
            outLine.open(format, manager.bufferSize * lineformat.frameSize)
        }
        outLine.start()
    }

    fun close() {
        //outLine.drain()//最後まで読み切り
        outLine.flush()
        outLine.close()
    }

    fun update() {
        // lockしてこの間メインスレッド側からAudioSourceにアクセスすることはできない
        // 処理途中でAudioScriptが増えることもない
        manager.updateLock {
            val sources = manager.getSources()
            sources.forEach {
                it.value.onPlayUpdate()
            }
            manager.getScripts().forEach { it.apply(sources) }
            tmpSources = sources.map{ it.value }.filter{ it.isPlaying }.map{ it.processBuffer!!.take(it.marginsize_prev, it.marginsize_prev + it.buffersize) }
        }
    }

    /**
     * 全てのaudiosを各状態に合わせて合成しoutLineに流す
     * 毎フレーム呼び出されることを想定している
     * 音割れを防ぐために多くの場合各Lineのボリュームを下げる必要がある
     */
    fun render() {
        val masterBuffer :ByteArray = ByteArray(manager.bufferSize * lineformat.frameSize, { 0 })

        //buffersの各バッファサイズはマージンを除いたbuffersizeになるはずである
        //[曲番号][チャンネル番号][フレーム番号] = Int
        val buffers :List<AudioArray> = tmpSources
        val bitsper8 :Int = lineformat.sampleSizeInBits / 8
        for(c in 0..lineformat.channels - 1) {
            for(f in 0..manager.bufferSize - 1) {

                //曲を統合
                //曲のサンプリングレートがlineのサンプリングレートと異なる場合バグる（エラーはない）
                var value :Int = 0
                buffers.forEach {
                    //チャンネル数は曲ごとに違う
                    //cがlineより少ないのでコピー
                    value += it.getOrNull(c,f) ?: it.getOrZero(0,f)
                }

                //valueが決まったらmasterへ変換
                val endian =
                    if(lineformat.isBigEndian)
                        ByteOrder.BIG_ENDIAN
                    else
                        ByteOrder.LITTLE_ENDIAN
                val order = ByteBuffer.allocate(bitsper8).order(endian)
                val b :ByteArray =
                    if(bitsper8 == 4) order.putInt(value).array()
                    else if(bitsper8 == 2) order.putShort(intToShortRange(value)).array()
                    else if(bitsper8 == 1) order.put(intToByteRange(value)).array()
                    else throw RuntimeException()

                b.forEachIndexed { index, byte ->
                    masterBuffer[f * bitsper8 * lineformat.channels + c * lineformat.channels + index] = byte
                }

            }
        }

        //流し込み
        var complete = 0
        var isSleep = true
        while(complete < masterBuffer.size){//lineのバッファに残っていれば流し終えるまで待機
            val available = Math.min(outLine.available(), manager.bufferSize * lineformat.frameSize-complete)
            val len = outLine.write(masterBuffer, complete, available)
            if(len == -1)break
            if(isSleep && len != masterBuffer.size){
                //初回だけ
                val sleep = manager.bufferSize/lineformat.sampleRate/3 //[s]
                Thread.sleep((sleep*1000).toLong())
                isSleep = false
            }
            complete += len
        }
    }

    //加算の上限下限
    private fun intToByteRange(a :Int) :Byte = when{
        (a > Byte.MAX_VALUE) -> Byte.MAX_VALUE
        (a < Byte.MIN_VALUE) -> Byte.MIN_VALUE
        else -> a.toByte()
    }

    private fun intToShortRange(a :Int) :Short = when{
        (a > Short.MAX_VALUE) -> Short.MAX_VALUE
        (a < Short.MIN_VALUE) -> Short.MIN_VALUE
        else -> a.toShort()
    }
}

