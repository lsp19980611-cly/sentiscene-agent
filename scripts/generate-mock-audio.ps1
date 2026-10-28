$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.SelectVoice('Microsoft Huihui Desktop')
$synth.Rate = -1
$lines = @{
hello='你也来躲雨吗？坐吧，咖啡还是热的。我叫米拉，是个旅行摄影师。今晚这里很安静，安静得让人忍不住想起一些旧事。'
waiting='其实，我在等妹妹的来信。我们约好每到一座新城市，就寄一张照片。已经很久没收到她的消息了。也许，她只是走到一个没有信号的地方。'
photo='给你看这张照片。那天海边的灯塔刚亮起来，我妹妹站在光里。她说，迷路的人也会找到回家的方向。你有没有一张一直舍不得删掉的照片？'
clear='你看，雨好像停了。刚才我一直盯着远处，差点错过窗上的第一道光。谢谢你愿意陪我坐一会儿。等天亮，我们去街角拍张照片吧。'
bye='晚安。谢谢你把这个陌生的夜晚，变成了一段值得记住的旅程。下次下雨时，也许我们还会在这里碰面。'
chat='我喜欢听别人讲旅途里的小事。有时候，一次偶然的停留，比目的地更让人难忘。你想听听我在等谁，还是看看相机里的旅行照片？'
}
$folder = Join-Path $PSScriptRoot '../frontend/public/audio'
New-Item -ItemType Directory -Force $folder | Out-Null
try { foreach($entry in $lines.GetEnumerator()) { $dest=[IO.Path]::GetFullPath((Join-Path $folder ($entry.Key+'.wav'))); $synth.SetOutputToWaveFile($dest); $synth.Speak($entry.Value) } }
finally { $synth.Dispose() }
Get-ChildItem $folder | Select-Object Name,Length
