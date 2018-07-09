package works.chiudesu.djdj.script

import works.chiudesu.djdj.AudioSource


interface AudioScript {
    /**
     * sourceに何らかのフィルターをかけたりする
     * オーディオスレッドから呼ばれる
     * @param sources AudioManager.sources
     */
    fun apply(sources :MutableMap<Int,AudioSource>)

    companion object {
        var format :AudioAdditionalInfoFormat = AudioAdditionalInfoFormat()
    }

    /** AudioAdditionalInfoの読み取り方 */
    open class AudioAdditionalInfoFormat{
        val CATEGORY_BPM = "bpm"
        val CATEGORY_VOLUME = "volume"
        val CATEGORY_PLAY = "play"
        val TAG_START = "start"
    }
}