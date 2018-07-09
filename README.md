DJ Development kit for Java (DJDJ)
====
A library for interactive music such as DJ Play.

## Description
### Hello Audio!
One play on kotlin REPL
```kotlin

import works.chiudesu.djdj.AudioThread
import works.chiudesu.djdj.reader.RandomAccessReader
import java.io.File
import javax.sound.sampled.AudioFormat

//ライン用出力フォーマット
val formatForLine = AudioFormat(44100.0f, 16, 2, true, false)

//オーディオスレッドが1ループで再生するフレーム数
val bufferSize = 735

//オーディオスレッド作成とstart()
val audioThread = AudioThread(formatForLine, bufferSize)
audioThread.start()

//以後managerを操作する
val audioManager = audioThread.manager

//曲生成
//FileからSeekableAudioReaderへ変換するラムダ式を引数にとる
val f = { file :File -> RandomAccessAudio(file, bufferSize) }
val sourceID = audioManager.createSource("audio/StarlightCarnival", "wav", bufferSize, f)

//IDを使って曲再生
audioManager.playAt(sourceID)
```



## LICENCE
<a rel="license" href="http://creativecommons.org/licenses/by/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by/4.0/88x31.png" /></a><br />
          <span xmlns:dct="http://purl.org/dc/terms/" property="dct:title">DJ Development kit for Java (DJDJ)</span> by <span xmlns:cc="http://creativecommons.org/ns#" property="cc:attributionName">Chiudesu</span> is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution 4.0 International License</a>.

Check [LICENSE.md](license/LICENSE.md) for details.
