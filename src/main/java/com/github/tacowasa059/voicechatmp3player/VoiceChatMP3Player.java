package com.github.tacowasa059.voicechatmp3player;

import com.github.tacowasa059.voicechatmp3player.command.PlayMp3Command;
import com.github.tacowasa059.voicechatmp3player.command.PlayMp3TabCompleter;
import com.github.tacowasa059.voicechatmp3player.plugin.VoicechatMp3PlayerPlugin;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import org.bukkit.plugin.java.JavaPlugin;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;

public final class VoiceChatMP3Player extends JavaPlugin {

    public static final String PLUGIN_ID = "voicechat_mp3_plugin";
    @Nullable public static VoicechatMp3PlayerPlugin voicechatPlugin;
    @Nullable public static BukkitVoicechatService service;
    @Nullable public static VoicechatApi voicechatApi;

    /** ランタイムの再生管理: UUID -> セッション */
    public static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    public static final ExecutorService POOL = Executors.newCachedThreadPool();

    @Override
    public void onEnable() {
        // config は使わない
        service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            voicechatPlugin = new VoicechatMp3PlayerPlugin();
            service.registerPlugin(voicechatPlugin);
        }
        // コマンド登録（Executor/TabCompleter は新実装に差し替え）
        var c = getCommand("mp3at");
        if (c != null) {
            c.setExecutor(new PlayMp3Command());
            c.setTabCompleter(new PlayMp3TabCompleter());
        }
    }

    @Override
    public void onDisable() {
        if (voicechatPlugin != null) {
            getServer().getServicesManager().unregister(voicechatPlugin);
        }
        // すべて停止・解放
        for (Map.Entry<UUID, Session> e : SESSIONS.entrySet()) {
            try { e.getValue().stopNow(); } catch (Throwable ignored) {}
        }
        SESSIONS.clear();
        POOL.shutdownNow();
        try { POOL.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    /** 再生セッション情報 */
    public static final class Session {
        public final UUID id;
        public final LocationalAudioChannel channel;
        public final AudioPlayer player;
        public final Future<?> feederFuture;
        public final int loops;
        public final float gain;

        public Session(UUID id,
                       LocationalAudioChannel channel,
                       AudioPlayer player,
                       Future<?> feederFuture,
                       int loops,
                       float gain) {
            this.id = id; this.channel = channel; this.player = player;
            this.feederFuture = feederFuture; this.loops = loops; this.gain = gain;
        }

        public void stopNow() {
            try { player.stopPlaying(); } catch (Throwable ignored) {}
            if (feederFuture != null) feederFuture.cancel(true);
        }
    }
}