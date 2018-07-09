package works.chiudesu.djdj.script

import works.chiudesu.djdj.AudioSource

/**
 * 2音源の遷移の方法を表すクラス
 * メインスレッドで作成され、オーディオスレッドに渡される
 *
 * 既知のサブクラス
 * FastCutIn 即時切替
 * CutInByNote(n) n分音符単位でちょうどいいところで切替
 * CutInByBar(n) n小節単位でちょうどいいところで切替
 * CrossFadeByNote(n) n分音符単位でちょうどいいところから遷移しはじめる
 * CrossFadeByBar(n) n分小節単位でちょうどいいところから遷移しはじめる
 */
interface MixPattern :AudioScript{
    var isCompleted :Boolean
    override fun apply(sources :MutableMap<Int,AudioSource>)
}
