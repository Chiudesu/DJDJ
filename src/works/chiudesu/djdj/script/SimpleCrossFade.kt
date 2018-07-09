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
class SimpleCrossFade(val nowID :Int, val nextID :Int, val insertTime :Long, val workTime :Long) :MixPattern {

    override var isCompleted = false
    private val startTime = System.nanoTime()/1000/1000 //[ms]

    override fun apply(sources :MutableMap<Int,AudioSource>) {
        val nowSource = sources.get(nowID)
        val nextSource = sources.get(nextID)
        if(nowSource == null || nextSource == null) {
            isCompleted = true
            return
        }

        nowSource.load() //未ロードならロードする

        val nowTime = System.nanoTime()/1000/1000 //[ms]
        val sampling = nowSource.audioFormat!!.sampleRate
        val border = (startTime+insertTime - nowTime)*sampling/1000 //insertTimeまでにheadから何フレームあるか
        val work = workTime*sampling/1000 //graceTimeまでにborderから何フレームあるか

        if(border < nowSource.buffersize + nowSource.marginsize_next){
            //処理開始

            if(!nextSource.isPlaying){ //未再生状態
                // 再生状態にしてバッファを得る
                // シーク位置はすでに合っているものとして考える
                nextSource.play()
                nextSource.onPlayUpdate()
            }


            nowSource.processBuffer = nowSource.processBuffer!!.modify { c, f, value ->
                if(f < border){
                    value
                }else if(f < border+work){
                    val mag = (work-(f-border)).toDouble()/work
                    (value*mag).toInt()
                }else{
                    0
                }
            }
            nextSource.processBuffer = nextSource.processBuffer!!.modify { c, f, value ->
                if(f < border){
                    0
                }else if(f < border+work){
                    val mag = (f-border).toDouble()/work
                    (value*mag).toInt()
                }else{
                    value
                }
            }

            if(border+work < nowSource.buffersize){
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