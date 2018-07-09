package works.chiudesu.djdj

import org.yaml.snakeyaml.Yaml
import works.chiudesu.djdj.reader.SeekableAudioReader
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger
import javax.sound.sampled.AudioFormat

/**
 * オーディオのファイル名やフォーマット、再生位置を保持する
 * 特に重要なのは波形ByteArrayを保持すること
 * メインスレッドで生成してオーディオスレッドに登録されるが、
 * 非スレッドセーフなので必ずAudioManagerを通して操作される
 *
 * @constructor
 * @param filename オーディオファイルのパス（.拡張子を除く）"resource/hoge"
 * @param ext 拡張子（.を除く）"wav"
 * @param buffersize 再生のための毎フレームバッファサイズ
 * @param margin_prev procbufferの過去データのマージンサイズ
 * @param margin_next procbufferの先データのマージンサイズ
 */
class AudioSource(val filename :String, private val ext :String, val buffersize :Int, val marginsize_prev :Int, val marginsize_next :Int, private val factory :(File)-> SeekableAudioReader) {

    val logger :Logger = Logger.getLogger(this.javaClass.name)

    //未loadの場合はnull
    private var reader :SeekableAudioReader? = null
    var additionalInfo :AudioAdditionalInfo? = null
        private set

    val audioFormat :AudioFormat?
        get() = reader?.audioFormat

    val channelsize :Int?
        get() = reader?.audioFormat?.channels

    /**
     * 再生位置headはRandomAccessAudioの読み込み位置headとは別物である
     * this.onPlayUpdate()で読み込みを進める度に更新される
     * あるいはthis.seek()した際にも更新される
     */
    var head :Long? = null
        private set

    /**
     * onPlayUpdateでまずseekToへseekする
     */
    var seekTo :Long = 0
        private set

    /**
     * isLoaded==trueならば、AudioInputStream!=nullおよびadditionalInfo!=nullを保証する
     */
    var isLoaded :Boolean = false
        private set

    /**
     * isPlaying==trueならば、isLoaded==trueを保証する
     */
    var isPlaying :Boolean = false
        private set

    /**
     * AudioDataがisPlaying=trueであってもファイルの終端は超えているかもしれない
     */
    var isOverEOF :Boolean = false
        private set

    /**
     * 次のフレームで再生するデータ
     * これを編集してエフェクトを表現する
     *
     * リアルタイムフィルター適応を可能にするオーディオファイルの読み出し方
     * audiofile    [..........|........;      not yet loaded            ]
     * processing       prev[__|.....,__]next
     * buffer                  |.....]
     * timeline      ----------^playing------------------------------------->
     */

    val procsize = buffersize + marginsize_prev + marginsize_next
    var processBuffer :AudioArray? = null

    constructor(filename :String, ext :String, buffersize :Int, margin :Int = 0, factory :(File)-> SeekableAudioReader) :this(filename, ext, buffersize, margin, margin, factory) {
    }

    /**
     * 急にplayする際のオーバーヘッドが気になる場合
     * 次状態の曲が分かりきっているなら先にloadできる
     * loadではbufferとprocbufferは読まれない
     */
    fun load() {
        if(!isLoaded) {
            try {

                //jarにしたときにもリソースにアクセスするにはClassLoaderからURLを取得する
                //File(path)はjarで詰み
                //ClassLoader.getSystemResourceはSystem32に行ってしまう
                //works.chiudesu.djdj.AudioSource::class.java.getResourceならOK
                val wavurl :URL = AudioSource::class.java.classLoader.getResource(filename + "." + ext)
                val wavfile :File = File(wavurl.toURI())
                logger.log(Level.INFO,"loading : $wavurl")
                //SPIのjarが必要なmp3やoggも、classpathが通っていれば自動適応される
                reader = factory(wavfile)
                head = 0
                seekTo = 0
                //チャンネルの数だけデータ配列を用意する
                //チャンネル数はあとから追加できたりしない（読み取り専用）
                //各チャンネルの中のデータは編集できる（List<Int>はミュータブル）
                processBuffer = audioArrayOf(reader!!.audioFormat.sampleRate, channelsize!!, procsize)


                val ymlurl :URL? = AudioSource::class.java.classLoader.getResource(filename + ".yml")
                if(ymlurl != null){
                    val ymlfile :File = File(ymlurl.toURI())
                    logger.info("loading : $ymlurl")
                    additionalInfo = AudioAdditionalInfo(ymlfile.inputStream())
                }else{
                    additionalInfo = AudioAdditionalInfo(null)
                }

            } catch(e :Exception) {
                logger.log(Level.SEVERE,"loading : ERROR at read file", e)
                e.printStackTrace()

                return //isLoadedはfalseのまま
            }

            isLoaded = true
        }
    }

    /**
     * load状態をやめてメモリへの負担を減らす
     * finalizeはGCが遅くなるので頼るべきではない
     */
    fun flush() {
        reader?.close()
        reader = null
        additionalInfo = null
        head = 0
        seekTo = 0

        isPlaying = false
        isLoaded = false
    }

    /**
     * loadされてなければloadし、AudioManagerによる再生を待つ
     */
    fun play(){
        if(!isPlaying) {
            load()
            if(!isLoaded) return //load失敗

            isPlaying = true
        }
    }

    /**
     * これが呼び出されたとき、bufferとprocbufferは次の再生で使われるべき新しい値へと更新される
     */
    fun onPlayUpdate() {
        if(!isPlaying) return
        //以下load済みであるからaudio!=nullが保証される

        //procbuffer読み出し
        readProcBuffer()
    }

    private fun readProcBuffer() {
        //seek
        reader!!.seek(seekTo - marginsize_prev.toLong())
        head = seekTo
        seekTo = head!!+buffersize.toLong()


        val (out, readedSize) = reader!!.read(procsize)
        processBuffer = out
        //isOverEOFの更新
        isOverEOF = (readedSize == -1)

        //byteは-128~127
        //読み出されなかったreadedSize以降のデータは0であるべき
        //読み終わりでreadedSize==-1ならば、すべてのデータは0であるべき
        processBuffer = processBuffer!!.modifyBetween(readedSize, processBuffer!!.frameSize ,{ c, f -> 0 })

    }

    /**
     * 再生位置を変更する
     * この関数によってすぐにheadの値が変わることはない
     * この後onPlayUpdate()が呼ばれた際に、そこにseekする
     */
    fun seek(dest :Long) {
        seekTo = dest
    }

    /**
     * 再生を一時停止する
     */
    fun stop() {
        isPlaying = false
    }


    /**
     * オーディオ制御に参考になる付随情報
     * オーディオファイルと同名のYAMLから読む
     * このクラスはイミュータブルである
     * @constructor
     * @param inputStream YAMLファイル
     */
    class AudioAdditionalInfo(private val inputStream :InputStream?) {
        //第一keyに対応するvalueがnullでない保証はない
        val tags :Map<String, Map<Int, String>>

        init {
            if(inputStream != null){
                //YAMLからMap<category : Map<time : tag>>への変換
                //Mapが読み取れなかった場合load()はnullを返し代替map
                val yaml = Yaml()
                tags = yaml.load(inputStream) ?: mapOf()
            }else{
                tags = mapOf()
            }
        }

        /**
         * カテゴリ内の条件のついたフレームをすべて列挙する
         * @param ctg カテゴリー名
         * @param predicate 条件
         */
        fun getFrames(ctg :String, predicate :(Map.Entry<Int, String>) -> Boolean) :LongArray? {

            val category :Map<Int, String>? = this.tags.get(ctg)
            return category?.filter(predicate)?.map { it.key.toLong() }?.toLongArray()
        }

        /**
         * カテゴリ内のtagタグがついたframeをすべて列挙する
         * @param ctg カテゴリー名
         * @param tag 検索するタグ
         */
        fun getFrames(ctg :String, tag :String) :LongArray? {
            return getFrames(ctg, { it.value == tag })
        }

        /**
         * カテゴリ内のnowフレームの直前のなにかしらタグのついたフレームを得る
         * @param ctg カテゴリー名
         * @param now 検索する基準フレーム
         */
        fun getFrameAtJust(ctg :String, now :Long) :Long? {
            val frames :LongArray? = getFrames(ctg, { it.key.toLong() <= now })

            frames?.sort()
            return if(frames != null && frames.size > 0) frames[frames.size - 1] else null
        }

        /**
         * カテゴリ内のnowフレームの直後のなにかしらタグのついたフレームを得る
         * @param ctg カテゴリー名
         * @param now 検索する基準フレーム
         */
        fun getFrameAtNext(ctg :String, now :Long) :Long? {
            val frames :LongArray? = getFrames(ctg, { it.key.toLong() > now })

            frames?.sort()
            return if(frames != null && frames.size > 0) frames[0] else null
        }

        /**
         * カテゴリ内のタグのついたフレームで、最初のフレームを得る
         * @param ctg カテゴリー名
         * @param tag 検索するタグ
         */
        fun getFrameAtFirst(ctg :String, tag :String) :Long? {
            val frames :LongArray? = getFrames(ctg, tag)

            frames?.sort()
            return if(frames != null && frames.size > 0) frames[0] else null
        }

        /**
         * カテゴリ内のタグのついたフレームで、最後のフレームを得る
         * @param ctg カテゴリー名
         * @param tag 検索するタグ
         */
        fun getFrameAtLast(ctg :String, tag :String) :Long? {
            val frames :LongArray? = getFrames(ctg, tag)

            frames?.sort()
            return if(frames != null && frames.size > 0) frames[frames.size - 1] else null
        }

        /**
         * カテゴリ内のタグのついたフレームで、nowの直後のフレームを得る
         * @param ctg カテゴリー名
         * @param tag 検索するタグ
         * @param now 検索する基準フレーム
         */
        fun getFrameAtNext(ctg :String, tag :String, now :Long) :Long? {
            val frames :LongArray? = getFrames(ctg, { it.value == tag && it.key.toLong() > now })

            frames?.sort()
            return if(frames != null && frames.size > 0) frames[0] else null
        }

        /**
         * カテゴリ内のタグのついたフレームで、nowの直前のフレームを得る
         * @param ctg カテゴリー名
         * @param tag 検索するタグ
         * @param now 検索する基準フレーム
         */
        fun getFrameAtJust(ctg :String, tag :String, now :Long) :Long? {
            val frames :LongArray? = getFrames(ctg, { it.value == tag && it.key.toLong() <= now })

            frames?.sort()
            return if(frames != null && frames.size > 0) frames[frames.size - 1] else null
        }
    }
}