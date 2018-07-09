package works.chiudesu.djdj

/**
 * イミュータブルクラス
 * コンストラクタに渡されるのがMutableListであってもcopyするので値が変わることはない
 */

class AudioArray{

    val content :List<List<Int>>
    val hz :Float
    val channelSize :Int
    val frameSize :Int

    /**
     * @param hz サンプリング周波数
     * @param channel チャンネル数
     * @param size フレーム数
     * @param init 初期化ラムダ式 cidx, fidx -> value
     */
    constructor(hz :Float, channelSize :Int, frameSize :Int, lambda :(Int, Int) -> Int){
        this.hz = hz
        this.channelSize = channelSize
        this.frameSize = frameSize
        content = List(channelSize, { c -> List(frameSize, { f -> lambda(c,f) })})
    }

    /**
     * @param hz サンプリング周波数
     * @param channel チャンネル数
     * @param size フレーム数
     * @param init 初期化ラムダ式 cidx -> List<value> ListのサイズがframeSizeに会ってなかったら後ろを0で埋める
     */
    constructor(hz :Float, channelSize :Int, frameSize :Int, lambda :(Int) -> List<Int>){
        this.hz = hz
        this.channelSize = channelSize
        this.frameSize = frameSize
        content = List(channelSize, { c ->
            val list = lambda(c)
            List(frameSize, {list.getOrNull(it) ?: 0})
        })
    }

    @Throws(IndexOutOfBoundsException::class)
    fun get(c :Int, f :Int): Int = content[c][f]

    fun getOrNull(c :Int, f :Int): Int? = content.getOrNull(c)?.getOrNull(f)

    fun getOrZero(c :Int, f :Int): Int = content.getOrNull(c)?.getOrNull(f) ?: 0

    @Throws(IndexOutOfBoundsException::class)
    fun getChannelAt(c :Int): List<Int> = content[c]

    @Throws(IndexOutOfBoundsException::class)
    fun getFrameAt(f :Int): List<Int> = content.map{it[f]}

    // 値を変更した新しいAudioArrayを返す
    fun modify(lambda :(Int, Int) -> Int) :AudioArray =
        audioArrayOf(hz, channelSize, frameSize, lambda)

    fun modify(lambda :(Int, Int, Int) -> Int) :AudioArray =
        audioArrayOf(hz, channelSize, frameSize, {c, f -> lambda(c,f,content[c][f])})

    // チャンネルごとに値を変更した新しいAudioArrayを返す
    // ListのサイズがframeSizeに会ってなかったら後ろを0で埋める
    fun modifyByChannel(lambda :(Int) -> List<Int>) :AudioArray =
        audioArrayOf(hz, channelSize, frameSize, lambda)

    // 型と値を変更した新しいAudioArrayを返す
    fun <T> map(lambda :(Int, Int) -> T) :List<List<T>> =
        List<List<T>>(channelSize, {c -> List<T>(frameSize, {f -> lambda(c,f)})})

    fun <T> map(lambda :(Int, Int, Int) -> T) :List<List<T>> =
        List<List<T>>(channelSize, {c -> List<T>(frameSize, {f -> lambda(c,f, content[c][f])})})


    //フレーム[begin]～[end-1]を持つAudioArrayを生成する
    fun take(begin :Int, end :Int) :AudioArray{
        val range = end - begin
        return if(range <= 0) audioArrayOf(hz)
                else audioArrayOf(hz, channelSize, range, {c, f -> content[c][begin+f] })
    }

    //フレーム[begin]～[end-1]を編集したAudioArrayを生成する、ラムダ式のfは 0～(end - begin)-1である
    //beginとendはマイナスであったりframeSizeを超過していたりしてもよい
    fun modifyBetween(begin :Int, end :Int, lambda :(Int, Int) -> Int) :AudioArray =
        if(end - begin <= 0)
            this
        else
            audioArrayOf(hz, channelSize, frameSize, {c, f ->
                if(f in begin..end-1) lambda(c,f-begin)
                else content[c][f]
            })

    fun modifyBetween(begin :Int, end :Int, lambda :(Int, Int, Int) -> Int) :AudioArray =
        if(end - begin <= 0)
            this
        else
            audioArrayOf(hz, channelSize, frameSize, {c, f ->
                if(f in begin..end-1) lambda(c,f-begin, content[c][f])
                else content[c][f]
            })

    // 値が同じ新しいAudioArrayを返す
    // イミュータブルなので自分を返しても同じはずなのでそもそもcopyする必要はない
    // fun copy() :AudioArray = audioArrayOf(hz, content)

    // イミュータブルなのでequalsは内容を見て一致ならtrue
    override fun equals(other :Any?) :Boolean {
        val o = other as AudioArray?
        o ?: return false
        if(o.frameSize != frameSize)return false
        if(o.channelSize != channelSize)return false
        content.forEachIndexed { c, channel -> channel.forEachIndexed {f, frame ->
            if(o.content[c][f] != frame)return false
        } }
        if(o.hz != hz)return false

        return true
    }

    //equalsがtrueならば、hashCodeは同じでないといけない
    //kotlinが生成するhashCodeがだいたいこんなかんじ
    override fun hashCode() :Int {
        return (this.hz * 31
                + this.channelSize * 31
                + this.frameSize * 31
                + this.getOrZero(0,0) * 31
                + this.getOrZero(channelSize-1,frameSize-1)).toInt()
    }

    override fun toString() :String = "${AudioArray::class.qualifiedName} hz:$hz, channelsize:$channelSize, frameSize:$frameSize"
}

//コンストラクタを増やすとあまりにも煩雑になるので基本的にaudioArrayOfで生成する
fun audioArrayOf(hz :Float, channel :Int, size :Int, init :(Int, Int) -> Int = {c,f -> 0}) :AudioArray{
    return AudioArray(hz, channel, size, init)
}

fun audioArrayOf(hz :Int, channel :Int, size :Int, init :(Int, Int) -> Int = {c,f -> 0}) :AudioArray{
    return AudioArray(hz.toFloat(), channel, size, init)
}

fun audioArrayOf(hz :Float, channel :Int, size :Int, init :(Int) -> List<Int>) :AudioArray{
    return AudioArray(hz, channel, size, init)
}

fun audioArrayOf(hz :Int, channel :Int, size :Int, init :(Int) -> List<Int>) :AudioArray{
    return AudioArray(hz.toFloat(), channel, size, init)
}

fun audioArrayOf(hz :Float, source :List<List<Int>>) :AudioArray{
    val channel = source.size
    val size = source.getOrNull(0)?.size ?: 0
    return AudioArray(hz, channel, size, {c,f -> source[c][f]})
}

fun audioArrayOf(hz :Float) :AudioArray{
    return AudioArray(hz, 0, 0, {c,f -> 0})
}

fun audioArrayOf(hz :Int) :AudioArray{
    return AudioArray(hz.toFloat(), 0, 0, {c,f -> 0})
}