package works.chiudesu.djdj.script

import works.chiudesu.djdj.AudioArray
import works.chiudesu.djdj.AudioSource
import works.chiudesu.djdj.audioArrayOf

//AudioArrayやAudioSourceに変更を加える便利メソッド
object ScriptUtility {


    //ホワイトガウスノイズを加算
    fun noiseMix(aa :AudioArray, volume: Double) :AudioArray {
        val noise = audioArrayOf(aa.hz, aa.channelSize, aa.frameSize, {c -> noiseOf(aa.frameSize, volume)})

        return aa.modify { c,f,value ->
            value + noise.get(c,f)
        }
    }

    //ホワイトガウスノイズにバンドパスフィルターをかけてから加算
    fun bandedNoiseMix(aa: AudioArray, volume: Double, freq: Float, q: Float) :AudioArray {
        val noise = audioArrayOf(aa.hz, aa.channelSize, aa.frameSize, {c -> noiseOf(aa.frameSize, volume)})
        val banded = bandpassFilter(noise, freq, q)

        return aa.modify { c,f,value ->
            value + banded.get(c,f)
        }
    }

    //ホワイトガウスノイズを生成
    fun noiseOf(size :Int, volume: Double) :List<Int> {
        val noise = MutableList(size, {0})
        var idx = 0
        while (idx < size) {
            val (a, b) = gaussRandom()
            noise[idx] = (volume * a).toInt()
            idx++
            if (idx >= size)break
            noise[idx] = (volume * b).toInt()
            idx++
        }
        return noise
    }

    //v倍速、p倍ピッチにする
    //head以降のみに影響を与える
    fun changeSpeed(source : AudioSource, v: Double, p: Double) {
        timeStretch(source, v / p)
        if (p < 1.0) {
            upSampling(source, 1 / p)
        } else if (p > 1.0) {
            downSampling(source, 1 / p)
        }
    }

    //v倍速にする
    //head以降のみに影響を与える
    //加速でnextバッファが足りない場合IndexOutOfBoundsExceptionが出る
    fun timeStretch(source : AudioSource, v: Double) {

        source.processBuffer = source.processBuffer!!.modifyByChannel { c ->
            val (t,d) = source.processBuffer!!.getChannelAt(c).split(source.marginsize_prev)
            val out = fitList(source.buffersize+ source.marginsize_next, timeStretch(d, (d.size / v).toInt()))
            t+out
        }

        //処理しきった先にseekする
        source.seek(source.head!! + ((source.seekTo - source.head!!) * v).toLong())
    }

    //to長（size/to=v倍速）にする
    fun timeStretch(array: List<Int>, to: Int): List<Int> {
        val v = array.size / to.toDouble()

        //ブロックに分割して1ブロックずつoutに書き込んでいく
        val blocksize: Int = getBlockSize()
        val fadesize: Int = 100
        val fadeIn = {i :Int -> i.toFloat() / fadesize}
//        val fadeIn = {i :Int -> Math.sin(Math.PI / 2.0f * i / fadesize)}
        val fadeOut = {i :Int -> (fadesize-i).toFloat() / fadesize}
//        val fadeOut = {i :Int -> Math.cos(Math.PI / 2.0f * i / fadesize)}

        var indexbuffer: Int = 0
        var indexout: Int = 0
        val out = MutableList<Int>(to, { 0 })

        //outのはじめにフェードアウト分を書き込み
        for (i in 0..fadesize - 1) {
            val pos = i
            if (pos >= out.size) break
            if (pos >= array.size) break
            val value = array[pos] * fadeOut(i)
            out[pos] = value.toInt()
        }

        while (indexout < out.size - 1) {
            //ブロックの始めにフェードイン分を書き込み
            for (i in 0..fadesize - 1) {
                val pos = i
                if (indexout + pos >= out.size) break
                if (indexbuffer + pos >= array.size) break
                val value = array[indexbuffer + pos] * fadeIn(i)
                out[indexout + pos] += value.toInt()
            }
            //outに1ブロック書き込み
            for (i in 0..blocksize - fadesize - 1) {
                val pos = i + fadesize
                if (indexout + pos >= out.size) break
                if (indexbuffer + pos >= array.size) break
                val value = array[indexbuffer + pos]
                out[indexout + pos] += value.toInt()

            }
            //ブロックの後ろにさらにフェードアウト分を書き込み
            for (i in 0..fadesize - 1) {
                val pos = i + blocksize
                if (indexout + pos >= out.size) break
                if (indexbuffer + pos >= array.size) break
                val value = array[indexbuffer + pos] * fadeOut(i)
                out[indexout + pos] += value.toInt()
            }
            indexout += blocksize
            //headが進むのはブロック分ではなく（そこで再生されるべきな）v倍した位置
            indexbuffer += (blocksize * v).toInt()
        }

        //outから移す
        //MutableからReadOnlyへ
        return out
    }

    private fun getBlockSize(): Int {
        //キックの音は50Hz、可聴域は20Hz-15000Hz,20000Hz
        //標本化定理から20Hzの再現には40Hz必要で
        //一般オーディオは44100Hzであるから幅1102.5f以下のブロックで区切ればいいと思った
        //※ackiesound.ifdef.jpによると、低周波のために逆にブロックを大きくして、区切りは50ms(2205f)ぐらいがいいらしいが
        return 1100
    }

    //アップサンプリング（フレームを伸ばす）、倍率mag > 1
    //head以降のみに影響を与える
    //高周波にノイズが生まれる
    fun upSampling(source : AudioSource, mag: Double) {
        if (mag <= 1.0) return

        //headから伸ばす
        source.processBuffer = source.processBuffer!!.modifyByChannel { c ->
            val (t,d) = source.processBuffer!!.getChannelAt(c).split(source.marginsize_prev)
            val out = fitList(source.buffersize+ source.marginsize_next, upSampling(d, mag))
            t+out
        }

        //処理しきった先にseekする
        source.seek(source.head!! + ((source.seekTo - source.head!!) / mag).toLong())
    }

    //アップサンプリング（srcの[0]から伸ばす）、倍率mag > 1
    //配列長も伸びる
    fun upSampling(src: List<Int>, mag: Double) :List<Int> {
        if (mag <= 1.0) return src

        //y[2n] = x[n], y[2n+1] = 1/2{x[n]+x[n+1]}
        //y[mn] = x[n], y[mn+1] = 1/m{x[n]+x[n+1]+x[n+2]...}
        // 元の値or0で伸ばす方法では0値が中途半端に増えて中周波ノイズがでるので却下
        // 割合を使った線形補間を行う
        val interpolate = {n :Int, w:Double -> src[n]*(1-w) + src[n+1]*w}
        // 平均補間
//        val interpolate = {n :Int, w:Double -> (src[n]+src[n+1])/2}

        return List<Int>((src.size*mag).toInt(), {i ->
            val n = (i/mag).toInt()
            val weightnext = (i % mag)/mag //nに近ければ0、遠ければ1
            val isCore = (Math.abs(i % mag) < 0.01) // nが十分元のフレームに近いかどうか
            val value = if (isCore) src[n]
                        else if(n+1 < src.size) interpolate(n,weightnext).toInt()
                        else src[n]
            value
        })
    }

    //ダウンサンプリング（フレームを縮める）してseekする、倍率mag < 1
    //head以降のみに影響を与える
    //nextバッファが足りない場合でもIndexOutOfBoundsExceptionは出ない
    fun downSampling(source : AudioSource, mag: Double) {
        if (mag >= 1.0) return

        //headから縮める
        source.processBuffer = source.processBuffer!!.modifyByChannel { c ->
            val (t,d) = source.processBuffer!!.getChannelAt(c).split(source.marginsize_prev)
            val out = fitList(source.buffersize+ source.marginsize_next, downSampling(d, mag))
            t+out
        }

        //処理しきった先にseekする
        source.seek(source.head!! + ((source.seekTo - source.head!!) / mag).toLong())
    }

    //ダウンサンプリング（srcの[0]から縮める）、倍率mag < 1
    //配列長も縮む
    //縮めて生まれた後ろの隙間は0で埋める
    fun downSampling(src: List<Int>, mag: Double) :List<Int> {
        if (mag >= 1.0) return src

        //y[2n] = x[n], y[2n+1] = 1/2{x[n]+x[n+1]}
        //y[mn] = x[n], y[mn+1] = 1/m{x[n]+x[n+1]+x[n+2]...}
        // 割合を使った線形補間を行う
        val interpolate = {n :Int, w:Double -> src[n]*(1-w) + src[n+1]*w}
        // 平均補間
//        val interpolate = {n :Int, w:Double -> (src[n]+src[n+1])/2}

        return List<Int>((src.size*mag).toInt(), { i->
            val n = (i/mag).toInt()
            val weightnext = (i % mag)/mag //nに近ければ0、遠ければ1
            val isCore = (Math.abs(i % mag) < 0.01) // nが十分元のフレームに近いかどうか
            val value = if (isCore) src.getOrNull(n) ?: 0
                        else if(n+1 < src.size) interpolate(n,weightnext).toInt()
                        else src.getOrNull(n) ?: 0
            value
        })
    }

    //平均化フィルタ（ローパス）
    // TODO : 双二次化
    fun mean(aa :AudioArray, range: Int) :AudioArray {
        return if (range < 2) aa
                else aa.modify { c, f, value ->
                    val a1 = aa.getOrZero(c,f)
                    val a2 = aa.getOrZero(c,f-1)
                    (a1+a2) / 2
                }
    }

    /**
     * 双二次フィルタ
     * http://vstcpp.wpblog.jp/?page_id=523#%E5%8F%8C2%E6%AC%A1(BiQuad)%E3%83%95%E3%82%A3%E3%83%AB%E3%82%BF
     * @return 返す長さはsrcと同じ
     */
    fun BiQuad(src: List<Int>, a0: Float, a1: Float, a2: Float, b0: Float, b1: Float, b2: Float): List<Int> {
        val out = MutableList(src.size, {0})
        for (i in src.indices) {
            val src1 = src.getOrNull(i-1) ?: 0
            val src2 = src.getOrNull(i-2) ?: 0
            val out1 = out.getOrNull(i-1) ?: 0
            val out2 = out.getOrNull(i-2) ?: 0
            out[i] = (b0 / a0 * src[i] + b1 / a0 * src1 + b2 / a0 * src2
                    - a1 / a0 * out1 - a2 / a0 * out2).toInt()
        }
        return out
    }

    /**
     * ピーキングフィルタ
     *
     * @param aa オーディオデータ
     * @param freq 中心周波数
     * @param q フィルタのQ値（選択度）、大きい方が鋭い freq*2～freq/2くらい
     * @param gain 増幅量、±15dBくらい
     */
    fun peakingFilter(aa: AudioArray, freq: Float, q: Float, gain: Float) :AudioArray {
        val bw = freq / q
        val omega = 2.0 * Math.PI * freq / aa.hz
        val alpha = Math.sin(omega) * Math.sinh(Math.log(2.0) / 2.0 * bw * omega / Math.sin(omega))
        val A = Math.pow(10.0, (gain / 40.0))

        val a0 = 1.0f + alpha / A
        val a1 = -2.0f * Math.cos(omega)
        val a2 = 1.0f - alpha / A
        val b0 = 1.0f + alpha * A
        val b1 = -2.0f * Math.cos(omega)
        val b2 = 1.0f - alpha * A
        return audioArrayOf(aa.hz, aa.channelSize, aa.frameSize, { c ->
            BiQuad(aa.getChannelAt(c), a0.toFloat(), a1.toFloat(), a2.toFloat(), b0.toFloat(), b1.toFloat(), b2.toFloat())
        })
    }

    /**
     * バンドパスフィルタ
     *
     * @param aa オーディオデータ
     * @param freq 中心周波数
     * @param q フィルタのQ値（選択度）、大きい方が鋭い freq*2～freq/2くらい
     */
    fun bandpassFilter(aa: AudioArray, freq: Float, q: Float) :AudioArray {
        val bw = freq / q
        val omega = 2.0 * Math.PI * freq / aa.hz
        val alpha = Math.sin(omega) * Math.sinh(Math.log(2.0) / 2.0 * bw * omega / Math.sin(omega))

        val a0 = 1.0f + alpha
        val a1 = -2.0f * Math.cos(omega)
        val a2 = 1.0f - alpha
        val b0 = alpha
        val b1 = 0.0f
        val b2 = -alpha
        return audioArrayOf(aa.hz, aa.channelSize, aa.frameSize, { c ->
            BiQuad(aa.getChannelAt(c), a0.toFloat(), a1.toFloat(), a2.toFloat(), b0.toFloat(), b1.toFloat(), b2.toFloat())
        })
    }

    /**
     * @param resolution 欲しい分解能
     * @return resolutionが2の乗数でない場合、より小さい2の乗数を計算して返す
     */
    fun getFFTSize(resolution :Int) :Int{
        var length = 1
        var next = 2
        while (next <= resolution) {
            length = next
            next *= 2
        }
        return length
    }

    /**
     * @param tarray 時間軸配列
     * @param hz サンプリング周波数
     * @param resolution 分解能、2の乗数でない場合、近くて小さい2の乗数として計算する
     * @return farray 周波数軸配列、この長さはサンプリング周波数になる
     */
    fun stretchFFT(tarray: List<Int>, resolution: Int): List<Double> {

        val length = getFFTSize(resolution)
        val stretched = timeStretch(tarray, length)
        val farray: List<Double> = pFFT(stretched).map { it.re }

        return farray
    }

    /**
     * @param tarray 時間軸配列、長さが2の乗数でない場合、先頭から2の乗数分だけ計算する
     * @return farray 周波数軸配列、この長さはサンプリング周波数になる
     */
    fun FFT(tarray: List<Int>): List<Double> {

        val length = getFFTSize(tarray.size)
        val farray: List<Double> = pFFT(tarray.take(length)).map { it.re }

        return farray
    }

    //tarrayのsizeは必ず2の乗数
    private fun pFFT(tarray: List<Int>): List<Complex> {
        val out = MutableList<Complex>(tarray.size, { Complex.zero })
        if (tarray.size <= 2) {
            //直接計算
            when (tarray.size) {
                1 -> {
                    out[0] = Complex(tarray[0].toDouble(), 0.0)
                }
                2 -> {
                    out[0] = Complex((tarray[0] + tarray[1]).toDouble(), 0.0)
                    out[1] = Complex((tarray[0] - tarray[1]).toDouble(), 0.0)
                }
            }
        } else {
            //再帰
            val m = out.size / 2
            val tarray_even = tarray.filterIndexed { index, i -> (index % 2 == 0) }
            val tarray_odd = tarray.filterIndexed { index, i -> (index % 2 == 1) }
            val farray_even = pFFT(tarray_even)
            val farray_odd = pFFT(tarray_odd)
            for (k in 0..m - 1) {
                val v = 2 * k * Math.PI / tarray.size
                val w = Complex(Math.cos(v), -Math.sin(v))

                val a = w * farray_odd[k]
                out[k] = farray_even[k] + a
                out[k + m] = farray_even[k] - a
            }
        }
        //MutableからReadOnlyへ
        return out
    }

    //横軸log10をとってからヒストグラム化
    fun log10Levelling(src: List<Double>): List<Double> {
        val range = Math.log10(1 + 1.0) / 2 // ヒストグラムの幅は一番広いindex==0に合わせる
        val out = mutableListOf<Double>()

        var outidx = 0
        var srcidx = 0
        while (true) {
            val end = outidx * range * 2 + range

            //各範囲に区分け
            val list = src.drop(srcidx).filterIndexed { index, value ->
                (Math.log10(srcidx + index + 1.0) < end)
            }
            srcidx += list.size

            //重み付き平均
            var sum = 0.0
            var weight = 0.0
            list.forEachIndexed { index, value ->
                val w = Math.log10(index + 1 + 1.0) - Math.log10(index + 1.0)
                sum += value * w
                weight += w
            }

            out.add(sum / weight)
            outidx++
            if (srcidx >= src.size) break
        }

        return out.toList()
    }

    // 2つの正規乱数 N(0,1) を生成する
    fun gaussRandom(): Pair<Double, Double> {
        val a = Math.random()
        val b = Math.random()
        val x = Math.sqrt(-2 * Math.log10(a)) * Math.sin(2 * Math.PI * b)
        val y = Math.sqrt(-2 * Math.log10(a)) * Math.cos(2 * Math.PI * b)
        return Pair(x, y)
    }

    // 長さを合わせる、隙間は0埋め
    fun fitList(length :Int, src :List<Int>) :List<Int>{
        return if(src.size > length) src.take(length)
                else if(src.size < length) src+List(length-src.size, {0})
                else src
    }

    fun <T> List<T>.split(n :Int) :Pair<List<T>,List<T>>{
        return Pair(this.take(n), this.drop(n))
    }

    fun <T> List<T>.split(n1 :Int, n2 :Int) :Triple<List<T>,List<T>,List<T>>{
        return Triple(this.take(n1), this.subList(n1,n2), this.drop(n2))
    }
}