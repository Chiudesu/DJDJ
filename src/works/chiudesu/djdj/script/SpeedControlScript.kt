package works.chiudesu.djdj.script

import works.chiudesu.djdj.AudioSource
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 各IDに再生速度を関連付ける
 */
class SpeedControlScript :AudioScript {

    //<id, speed>
    private val idSpeedMap = mutableMapOf<Int, Double>()

    private val locker = ReentrantLock()

    override fun apply(sources :MutableMap<Int,AudioSource>) {
        locker.withLock {
            for(elm in idSpeedMap){
                val source = sources.get(elm.key)
                ScriptUtility.changeSpeed(source!!, elm.value, 1.0)
            }
        }
    }

    /** 音源IDに速度倍率を設定する */
    fun set(id :Int, speed :Double){
        locker.withLock {
            idSpeedMap.set(id, speed)
        }
    }

    fun remove(id :Int){
        locker.withLock {
            idSpeedMap.remove(id)
        }
    }
}