package works.chiudesu.djdj.script

import works.chiudesu.djdj.AudioSource
import works.chiudesu.djdj.audioArrayOf

/**
 * @param now 再生中のオーディオID
 * @param next 次に挿入するオーディオID（nowAudioのcopyでもよい）
 * @param insertTime コンストラクタ生成から何ミリ秒後から挿入するか
 * @param workTime 挿入後から何ミリ秒で終了するか
 *
 * nextのフレーム開始位置はコンストラクタが呼ばれた時点でnext.headがあった位置
 */
class FastCutIn(val nowID :Int, val nextID :Int, val insertTime :Long, val workTime :Long) :MixPattern {

    override var isCompleted = false
    private val startTime = System.nanoTime()

    override fun apply(sources :MutableMap<Int,AudioSource>) {
        val nowSource = sources.get(nowID)
        val nextSource = sources.get(nextID)
        if(nowSource == null || nextSource == null) {
            isCompleted = true
            return
        }

        nowSource.load() //未ロードならロードする

        val nowTime = System.nanoTime()
        val sampling = nowSource.audioFormat!!.sampleRate
        val begin = 0
        val border = (startTime+insertTime - nowTime)*sampling/1000 //insertTimeまでにheadから何フレームあるか
        val end = nowSource.procsize

        if(border < nowSource.buffersize + nowSource.marginsize_next){
            //処理開始

            if(!nextSource.isPlaying){ //未再生状態
                // 再生状態にしてバッファを得る
                // シーク位置はすでに合っているものとして考える
                nextSource.play()
                nextSource.onPlayUpdate()
            }


            nowSource.processBuffer = nowSource.processBuffer!!.modifyBetween(border.toInt(), end){ c, f -> 0}
            nextSource.processBuffer = nextSource.processBuffer!!.modifyBetween(begin, border.toInt()){ c, f -> 0}

            if(border < nowSource.buffersize){
                //nextマージンでなくbuffersize内に入っていたら完了
                isCompleted = true
            }

        }else{
            //nextは無音のまま
            val array = nextSource.processBuffer!!
            nextSource.processBuffer = audioArrayOf(array.hz, array.channelSize, array.frameSize, { c, f -> 0})
        }
    }

}