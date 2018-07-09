package works.chiudesu.djdj.script

import works.chiudesu.djdj.AudioSource

object AdditionalInfoUtility{

    //tagを読んで（最初のフレームの）startまでseek
    fun seekToStart(source :AudioSource) {
        val startFrame :Long? = source.additionalInfo!!.getFrameAtFirst(AudioScript.format.CATEGORY_PLAY, AudioScript.format.TAG_START)
        source.seek(startFrame ?: 0)
        return
    }

    //tagを読んでボリュームを変えるフィルターをかける
    fun adjustVolume(source :AudioSource) {
        var retProcBuffer = source.processBuffer!!

        var thisframe = source.head!! - source.marginsize_prev
        if(thisframe < 0)thisframe = 0
        do {
            val justframe :Long? = source.additionalInfo!!.getFrameAtJust(AudioScript.format.CATEGORY_VOLUME, thisframe)
            val nextframe :Long = source.additionalInfo!!.getFrameAtNext(AudioScript.format.CATEGORY_VOLUME, thisframe) ?: source.head!!+source.buffersize+source.marginsize_next
            justframe ?: break

            val read :String? = source.additionalInfo!!.tags.getValue(AudioScript.format.CATEGORY_VOLUME).get(justframe.toInt())
            val volume :Int = read?.toInt() ?: 100

            //frameをindexに変換したbegin,end間をvolumeで編集する
            val begin :Int = (thisframe - (source.head!! - source.marginsize_prev)).toInt()
            val end :Int = (nextframe - (source.head!! - source.marginsize_prev)).toInt()
            retProcBuffer = retProcBuffer.modifyBetween(begin, end, { c, f, value ->
                value * volume / 100
            })

            thisframe = nextframe

        } while(thisframe < source.head!! + source.marginsize_next)

        source.processBuffer = retProcBuffer
    }

    //tagを読んでBPMを変えるフィルターをかける
    //バッファサイズ未満の細かな変化はできない
    fun adjustBPM(source :AudioSource, to :Int) {
        val (bpm :Int?) = getBPM(source)
        bpm ?: return
        ScriptUtility.timeStretch(source, to.toDouble() / bpm)
    }

    //tagを読んでsourceのBPMをtoのBPMに合わせるフィルターをかける
    //バッファサイズ未満の細かな変化はできない
    fun adjustBPM(source :AudioSource, to :AudioSource) {
        val (bpm :Int?) = getBPM(source)
        bpm ?: return
        adjustBPM(source, bpm)
    }

    //tagを読んでsourceの(BPM, justFrame)を得る
    fun getBPM(source :AudioSource, index :Int = 0) :Pair<Int?, Long?> {
        var thisframe = source.head!! - source.marginsize_prev + index
        if(thisframe < 0)thisframe = 0
        val bpmframe = source.additionalInfo!!.getFrameAtJust(AudioScript.format.CATEGORY_BPM, thisframe)
        bpmframe ?: return Pair(null, null)
        val bpm = source.additionalInfo!!.tags.getValue(AudioScript.format.CATEGORY_BPM).get(bpmframe.toInt())?.toInt()
        return Pair(bpm, bpmframe)
    }
}