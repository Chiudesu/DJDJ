package works.chiudesu.djdj

import javax.sound.sampled.AudioFormat

/**
 * @param audioFormat ライン出力に適応するフォーマット
 * @param bufferSize オーディオスレッド1フレームあたりにレンダリングするフレーム数（サンプリング周波数）
 */
class AudioThread(val audioFormat :AudioFormat, val bufferSize :Int) :Thread() {

    //※AudioManagerへのアクセスはスレッドセーフなscripts,sourcesから行う
    private val audioRoutine :AudioRoutine

    var isRunning :Boolean = true
    val manager = AudioManager(bufferSize)
    var fps :Float = audioFormat.sampleRate/bufferSize
    private var oldTime :Long = 0


    init {
        this.priority = Thread.MAX_PRIORITY//オーディオのスレッド優先度は最高

        //AudioManager生成
        audioRoutine = AudioRoutine(bufferSize, manager)

    }

    override fun run() {

        try {
            audioRoutine.open(audioFormat)

            oldTime = System.nanoTime()
            while(isRunning) {
                //毎フレーム呼び出し、メインスレッドのFPSとは異なる

                audioRoutine.update()
                audioRoutine.render()//スレッド止まる

                val nowTime = System.nanoTime()
                fps = 1000f*1000*1000/(nowTime-oldTime)
                oldTime = nowTime
            }
        } finally {
            audioRoutine.close()
        }
    }
}