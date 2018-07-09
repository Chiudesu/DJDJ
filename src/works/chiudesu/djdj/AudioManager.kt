package works.chiudesu.djdj

import works.chiudesu.djdj.reader.SeekableAudioReader
import works.chiudesu.djdj.script.AudioScript
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class AudioManager(val bufferSize :Int){

    //scriptsの操作、sourcesの操作、各AudioSourceの操作でlock
    private val audioLocker = ReentrantReadWriteLock()

    //モジュール内オーディオスレッドから既知のアクセスを行うメソッド
    internal fun updateLock(f :()->Unit) = audioLocker.write{ f() }

    /**
     * 登録中のスクリプトたち
     * AudioThread.update()途中でAudioScriptが増えることはない
     * script.apply()実行中にメインスレッドからscriptにアクセスされる可能性はある
     * オーディオスレッドからscriptsに追加したり削除したりしないことは既知である
     */
    private val scripts :MutableList<AudioScript> = mutableListOf()
    fun getScripts() = audioLocker.read { scripts.toList() }
    fun getScriptsSize() = audioLocker.read { scripts.size }
    fun addScript(element :AudioScript) = audioLocker.write { scripts.add(element) }
    fun addScript(index :Int, element :AudioScript) = audioLocker.write { scripts.add(index, element) }
    fun addScriptFirst(element :AudioScript) = audioLocker.write { scripts.add(0, element) }
    fun addScriptLast(element :AudioScript) = audioLocker.write { scripts.add(element) }
    fun removeScript(index :Int) = audioLocker.write { scripts.removeAt(index) }
    fun removeScript(element :AudioScript) = audioLocker.write { scripts.remove(element) }

    /**
     * 登録中のオーディオデータたちとIDのマップ
     * AudioSourceは、AudioScript.apply()の引数をscriptが保存してメインスレッドへ渡すという特殊な場合以外でメインスレッドからアクセスされることはない
     * メインスレッドからのplay(), stop(), seek()は
     * AudioThread.update()中にはlockされてアクセスすることはできない
     */
    private val sources :MutableMap<Int,AudioSource> = mutableMapOf()
    internal fun getSources() = sources // AudioThread.update()からの既知のアクセス
    fun getSourcesSize() = audioLocker.read { sources.size }
    fun containsID(id :Int) = audioLocker.read { sources.containsKey(id) }
    fun load(id :Int) = audioLocker.write { sources.get(id)?.load() }
    fun play(id :Int) = audioLocker.write { sources.get(id)?.play() }
    fun stop(id :Int) = audioLocker.write { sources.get(id)?.stop() }
    fun getAudioFormatAt(id :Int) = audioLocker.read { sources.get(id)?.audioFormat }
    fun getAdditionalInfoAt(id :Int) = audioLocker.read { sources.get(id)?.additionalInfo }
    fun seek(id :Int, category :String, tag :String) = audioLocker.write {
        val source = sources.get(id) ?: return@write
        val startFrame :Long? = source.additionalInfo!!.getFrameAtFirst(category, tag)
        source.seek(startFrame ?: 0)
    }
    fun seek(id :Int, frame :Long) = audioLocker.write {
        val source = sources.get(id) ?: return@write
        source.seek(frame)
    }



    /**
     * 新規AudioSourceを作成し、sourcesに登録する
     * @return 登録されたID
     */
    fun createSource(filename :String, ext :String, marginsize_prev :Int, marginsize_next :Int, factory :(File)-> SeekableAudioReader) :Int {
        val newSource = AudioSource(filename, ext, bufferSize, marginsize_prev, marginsize_next, factory)
        return audioLocker.write{ addSource(newSource) }
    }

    fun createSource(filename :String, ext :String, factory :(File)-> SeekableAudioReader) :Int {
        return createSource(filename, ext, bufferSize, bufferSize, factory)
    }

    private var idCounter = 0 //60FPSで曲を作り続けたとして400日分
    private fun addSource(source :AudioSource) :Int{
        idCounter++
        sources.set(idCounter, source)
        return idCounter
    }

    fun removeSource(id :Int){
        audioLocker.write{
            sources.get(id)?.flush()
            sources.remove(id)
        }
    }
}