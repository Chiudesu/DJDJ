package works.chiudesu.djdj.viewer

import works.chiudesu.djdj.AudioSource
import works.chiudesu.djdj.script.AudioScript

class WaveGetScript(var id :Int, var channel :Int) :AudioScript{
    internal var wave :List<Int> = listOf()
    internal var hz :Float = 44100.0f
    internal var sampleSizeInBits :Int = 16

    override fun apply(sources :MutableMap<Int, AudioSource>) {
        val source = sources.get(id)
        if(source?.processBuffer != null){
            hz = source.audioFormat!!.sampleRate
            sampleSizeInBits = source.audioFormat!!.sampleSizeInBits
            wave = source.processBuffer!!.take(source.marginsize_prev, source.marginsize_prev+source.buffersize).getChannelAt(channel)
        }
    }
}