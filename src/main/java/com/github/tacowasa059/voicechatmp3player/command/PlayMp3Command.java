package com.github.tacowasa059.voicechatmp3player.command;

import com.github.tacowasa059.voicechatmp3player.VoiceChatMP3Player;
import com.github.tacowasa059.voicechatmp3player.utils.LavaPcmStream;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class PlayMp3Command implements CommandExecutor {

    @Override
    public boolean onCommand(@Nonnull CommandSender s, @Nonnull Command cmd, @Nonnull String label, String[] a) {
        if (a.length == 0) {
            sendUsage(s, label);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);

        if ("list".equals(sub)) {
            if (VoiceChatMP3Player.SESSIONS.isEmpty()) {
                info(s, "再生中のセッションはありません。");
            } else {
                VoiceChatMP3Player.SESSIONS.forEach((id, sess) -> {
                    String loopsStr = (sess.loops < 0 ? "∞(無限)" : String.valueOf(sess.loops));
                    s.sendMessage(
                            ChatColor.AQUA + "ID: " + ChatColor.WHITE + id + "  " +
                                    ChatColor.YELLOW + "loops: " + ChatColor.WHITE + loopsStr + "  " +
                                    ChatColor.YELLOW + "gain: " + ChatColor.WHITE +
                                    String.format(Locale.US, "%.3f", sess.gain)
                    );
                });
            }
            return true;
        }

        if ("remove".equals(sub)) {
            if (a.length < 2) {
                warn(s, "使い方: /" + label + " remove <uuid>");
                return true;
            }
            try {
                UUID id = UUID.fromString(a[1]);
                var sess = VoiceChatMP3Player.SESSIONS.remove(id);
                if (sess == null) {
                    error(s, "指定された UUID のセッションが見つかりません: " + a[1]);
                    return true;
                }
                sess.stopNow();
                info(s, "セッションを削除しました: " + id);
            } catch (IllegalArgumentException e) {
                error(s, "UUID の形式が正しくありません。");
            }
            return true;
        }

        if ("add".equals(sub)) {
            if (VoiceChatMP3Player.voicechatApi == null) {
                error(s, "VoiceChat API を初期化中です。数秒後に再試行してください。");
                return true;
            }
            // /mp3at add <world> <x> <y> <z> <radius> <url> <loops|infinity> <gain>
            if (a.length < 9) {
                warn(s, "使い方: /" + label + " add <world> <x> <y> <z> <radius> <mp3-url> <loops|infinity> <gain>");
                return true;
            }
            try {
                String worldName = a[1];
                World world = Bukkit.getWorld(worldName);
                if (world == null) { error(s, "ワールドが見つかりません: " + worldName); return true; }

                double x = Double.parseDouble(a[2]);
                double y = Double.parseDouble(a[3]);
                double z = Double.parseDouble(a[4]);
                int radius = Math.max(1, (int)Double.parseDouble(a[5]));
                String url = a[6];
                String loopStr = a[7];
                int loops = loopStr.equalsIgnoreCase("infinity") ? -1 : Math.max(1, Integer.parseInt(loopStr));
                float gain = Float.parseFloat(a[8]);

                startSession(s, world, x, y, z, radius, url, loops, gain);
            } catch (Exception e) {
                error(s, "引数が不正です: " + e.getMessage());
            }
            return true;
        }

        sendUsage(s, label);
        return true;
    }

    private void sendUsage(CommandSender s, String label) {
        s.sendMessage(ChatColor.GOLD + "使い方:");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " add " + ChatColor.WHITE + "<world> <x> <y> <z> <radius> <mp3-url> <loops|infinity> <gain>");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " remove " + ChatColor.WHITE + "<uuid>");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " list");
    }

    private void startSession(CommandSender s, World world, double x, double y, double z,
                              int radius, String url, int loops, float gain) {
        VoicechatServerApi api = (VoicechatServerApi) VoiceChatMP3Player.voicechatApi;

        Position pos = api.createPosition(x, y, z);
        UUID id = UUID.randomUUID();
        LocationalAudioChannel ch = api.createLocationalAudioChannel(id, api.fromServerLevel(world), pos);
        if (ch == null) { error(s, "音声チャンネルの作成に失敗しました。"); return; }
        ch.setDistance(radius);

        OpusEncoder enc = api.createEncoder();

        BlockingQueue<short[]> q = new ArrayBlockingQueue<>(256);
        AudioPlayer audioPlayer = api.createAudioPlayer(ch, enc, () -> {
            try {
                short[] data = q.take();
                return (data.length == 0) ? null : data;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        });

        // フィーダースレッド
        var future = VoiceChatMP3Player.POOL.submit(() -> {
            try (LavaPcmStream stream = new LavaPcmStream(url, loops)) {
                stream.start();

                short[] mono = new short[960];
                int fill = 0;

                short[] frame;
                while (!Thread.currentThread().isInterrupted()
                        && (frame = stream.provide()) != null) {

                    int stereoSamples = frame.length / 2;
                    for (int i = 0, j = 0; i < stereoSamples; i++, j += 2) {
                        int m = ((int) frame[j] + (int) frame[j + 1]) / 2;
                        mono[fill++] = (short) m;

                        if (fill == 960) {
                            applyGainClampInPlace(mono, gain);
                            enqueueFrame(q, mono);
                            fill = 0;
                        }
                    }
                }

                if (fill > 0) {
                    for (int k = fill; k < 960; k++) mono[k] = 0;
                    applyGainClampInPlace(mono, gain);
                    enqueueFrame(q, mono);
                }

                // 終了シグナル
                q.put(new short[0]);

            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                error(s, "再生に失敗しました: " + t.getClass().getSimpleName() + " : " + t.getMessage());
            }
        });

        // セッション登録
        var sess = new VoiceChatMP3Player.Session(id, ch, audioPlayer, future, loops, gain);
        VoiceChatMP3Player.SESSIONS.put(id, sess);

        audioPlayer.setOnStopped(() -> {
            VoiceChatMP3Player.SESSIONS.remove(id);
            future.cancel(true);
            info(s, "再生が終了しました: " + id);
        });

        // 再生開始
        audioPlayer.startPlaying();

        s.sendMessage(
                ChatColor.GREEN + "再生開始 " +
                        ChatColor.AQUA + "[" + id + "] " +
                        ChatColor.YELLOW + "URL: " + ChatColor.WHITE + url + "  " +
                        ChatColor.YELLOW + "座標: " + ChatColor.WHITE + String.format(Locale.US, "(%.2f, %.2f, %.2f)", x, y, z) + "  " +
                        ChatColor.YELLOW + "半径: " + ChatColor.WHITE + radius + "  " +
                        ChatColor.YELLOW + "ループ: " + ChatColor.WHITE + (loops < 0 ? "∞(無限)" : loops) + "  " +
                        ChatColor.YELLOW + "ゲイン: " + ChatColor.WHITE + String.format(Locale.US, "%.3f", gain)
        );
    }

    private static void applyGainClampInPlace(short[] buf, float gain){
        if (gain == 1.0f) return;
        for (int i = 0; i < buf.length; i++) {
            int v = Math.round(buf[i] * gain);
            if (v > Short.MAX_VALUE) v = Short.MAX_VALUE;
            if (v < Short.MIN_VALUE) v = Short.MIN_VALUE;
            buf[i] = (short) v;
        }
    }

    private static void enqueueFrame(BlockingQueue<short[]> q, short[] src) throws InterruptedException {
        short[] out = new short[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        while (!q.offer(out)) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Thread.sleep(1L);
        }
    }

    private static void info(CommandSender s, String msg)  { s.sendMessage(ChatColor.GRAY  + msg); }
    private static void warn(CommandSender s, String msg)  { s.sendMessage(ChatColor.GOLD  + msg); }
    private static void error(CommandSender s, String msg) { s.sendMessage(ChatColor.RED   + msg); }
}
