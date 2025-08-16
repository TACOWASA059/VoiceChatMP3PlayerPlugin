package com.github.tacowasa059.voicechatmp3player.utils;


import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;


/**
 * URL の MP3 を再生し、20ms (960 samples/ch) の 48kHz S16LE ステレオ PCM を short[] で取り出す。
 */
public class LavaPcmStream implements AutoCloseable {

    private final String url;
    private final AudioPlayerManager manager;
    private final AudioPlayer player;

    private volatile boolean closed = false;
    private final CountDownLatch ended = new CountDownLatch(1);
    private volatile int played = 0;

    public LavaPcmStream(String url, int loops) {
        this.url = url;

        this.manager = new DefaultAudioPlayerManager();
        this.manager.getConfiguration()
                .setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_LE);// 48000

        AudioSourceManagers.registerRemoteSources(manager);
        AudioSourceManagers.registerLocalSource(manager);

        this.player = manager.createPlayer();

        this.player.addListener(new AudioEventAdapter() {
            @Override public void onTrackEnd(AudioPlayer p, AudioTrack t, AudioTrackEndReason endReason) {
                if (closed) { ended.countDown(); return; }
                played++;
                if (loops < 0 || played < loops) {
                    load(url);
                } else {
                    ended.countDown();
                }
            }

            @Override public void onTrackException(AudioPlayer p, AudioTrack t, FriendlyException e) {
                ended.countDown();
            }
        });
    }

    /** 非同期ロード開始 */
    public void start() {
        load(url);
    }

    private void load(String u) {
        manager.loadItemOrdered(this, u, new AudioLoadResultHandler() {
            @Override public void trackLoaded(AudioTrack track) {
                player.playTrack(track);
            }
            @Override public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack first = playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                if (first != null) player.playTrack(first);
                else ended.countDown();
            }
            @Override public void noMatches() { ended.countDown(); }
            @Override public void loadFailed(FriendlyException exception) { ended.countDown(); }
        });
    }

    /** 20ms 1フレーム分の PCM を取り出す（タイムアウトms）。null が返ったら終了。 */
    public short[] provide() throws InterruptedException {
        if (closed) return null;

        for (;;) {
            AudioFrame f = player.provide();
            if (f != null) {
                byte[] data = f.getData();
                if (data == null || data.length == 0) continue;
                short[] pcm = new short[data.length / 2];
                ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
                return pcm;
            }
            if (ended.getCount() == 0) return null;

            Thread.sleep(2L);
        }
    }

    @Override
    public void close() {
        closed = true;
        try { player.destroy(); } catch (Throwable ignored) {}
        try { manager.shutdown(); } catch (Throwable ignored) {}
        ended.countDown();
    }
}