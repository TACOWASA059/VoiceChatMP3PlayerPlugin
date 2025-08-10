package com.github.tacowasa059.voicechatmp3player.command;

import com.github.tacowasa059.voicechatmp3player.VoiceChatMP3Player;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlayMp3TabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(@Nonnull CommandSender sender,
                                      @Nonnull Command cmd, @Nonnull String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("add"); out.add("remove"); out.add("list");
            return out;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("add".equals(sub)) {
            switch (args.length) {
                case 2 -> { for (World w : Bukkit.getWorlds()) out.add(w.getName()); }
                case 3,4,5 -> {
                    if (sender instanceof Player p) {
                        double v = (args.length==3)? p.getLocation().getX()
                                : (args.length==4)? p.getLocation().getY()
                                : p.getLocation().getZ();
                        out.add(String.format(Locale.US,"%.2f", v));
                    } else {
                        out.add(args.length==3? "0.00" : args.length==4? "64.00" : "0.00");
                    }
                }
                case 6 -> out.add("40");
                case 7 -> {out.add("http");}
                case 8 -> { out.add("infinity"); out.add("1"); out.add("2"); }
                case 9 -> { out.add("1.0"); out.add("0.5"); out.add("2.0"); }
            }
        } else if ("remove".equals(sub)) {
            if (args.length == 2) {
                for (UUID id : VoiceChatMP3Player.SESSIONS.keySet()) out.add(id.toString());
            }
        }
        return out;
    }
}
