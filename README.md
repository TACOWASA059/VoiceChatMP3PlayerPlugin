# VoiceChatMP3Player

PaperMC（1.20 以降）＋ Simple Voice Chat プラグイン環境で、  
指定した座標から MP3 音声を再生するための追加プラグインです。

## 特徴
- `/mp3at add` コマンドでワールド座標・音量（ゲイン）・ループ回数を指定して再生開始
- `/mp3at list` で現在再生中のセッションを確認
- `/mp3at remove` で任意のセッションを停止
- 再生管理は **ランタイムのみ**（config.yml は使用しません）
- 再生終了または停止時にセッションは自動削除

## 必要環境
- PaperMC 1.20.x
- [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) （Bukkit/Paper 用）
- Java 17 以上
- ネットワークアクセス可能な MP3 ファイル（URL またはローカルパス）

## インストール方法
1. **前提プラグイン** Simple Voice Chat を導入し、サーバーを起動して動作確認してください。
2. 本プラグインの JAR ファイルを `plugins` フォルダに配置します。
3. サーバーを再起動します。

## コマンド一覧

### 再生開始
```
/mp3at add <world> <x> <y> <z> <radius> <mp3-url> <loops|infinity> <gain>
```
- `<world>` : ワールド名（例: `world`）
- `<x> <y> <z>` : 再生位置
- `<radius>` : 聞こえる範囲（ブロック単位）
- `<mp3-url>` : MP3 ファイルの URL またはローカルパス
- `<loops|infinity>` : 繰り返し回数（`infinity` で無限ループ）
- `<gain>` : 音量倍率（`1.0` が等倍）
### 再生中の一覧表示
```
/mp3at list
```
- 再生中のセッション ID・ループ設定・再生回数・ゲインを表示

### 再生停止
```
/mp3at remove <uuid>
```
- `/mp3at list` で表示された UUID を指定して停止

## 動作仕様
- 再生ごとにランダム UUID を割り当てて管理します。
- ループ回数が尽きるか `/mp3at remove` するとセッションを削除します。
- 再生データはモノラルに変換して送信されます。
- ゲイン値はサーバー側でサンプルごとに適用されます。

## 権限
- `voicechatmp3.play` : コマンド使用権限（デフォルト: OP のみ）

## 注意事項
- Simple Voice Chat 側の設定で距離制限や音質設定を確認してください。
- 大きな MP3 ファイルや多数の同時再生はサーバー負荷の原因になります。
- 外部 URL を利用する場合、配信権・著作権に注意してください。
