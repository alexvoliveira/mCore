package tk.slicecollections.maxteer.nms.network;

import com.mojang.authlib.GameProfile;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.PacketPlayInChat;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_8_R3.PacketPlayOutScoreboardObjective;
import net.minecraft.server.v1_8_R3.PacketPlayOutScoreboardScore;
import net.minecraft.server.v1_8_R3.PacketPlayOutScoreboardTeam;
import net.minecraft.server.v1_8_R3.PacketPlayOutTabComplete;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.player.fake.FakeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FakePacketHandler implements NMSPacketInterceptor.PacketHandler {

  @Override
  public Object onPacketReceiving(Player player, Object packet) {
    if (!(packet instanceof PacketPlayInChat)) {
      return packet;
    }

    String message = (String) NMSPacketAccess.get(packet, String.class, 0);
    String replaced = message.startsWith("/")
        ? FakeManager.replaceNickedPlayers(message, false)
        : FakeManager.replaceNickedChanges(message);
    NMSPacketAccess.set(packet, String.class, 0, replaced);
    return packet;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object onPacketSending(Player player, Object packet) {
    if (packet instanceof PacketPlayOutTabComplete) {
      String[] completions = (String[]) NMSPacketAccess.get(packet, String[].class, 0);
      String[] replaced = new String[completions.length];
      for (int i = 0; i < completions.length; i++) {
        replaced[i] = FakeManager.replaceNickedPlayers(completions[i], true);
      }
      NMSPacketAccess.set(packet, String[].class, 0, replaced);
    } else if (packet instanceof PacketPlayOutPlayerInfo) {
      List<PacketPlayOutPlayerInfo.PlayerInfoData> data =
          (List<PacketPlayOutPlayerInfo.PlayerInfoData>) NMSPacketAccess.get(packet, List.class, 0);
      List<PacketPlayOutPlayerInfo.PlayerInfoData> replaced = new ArrayList<>(data.size());
      PacketPlayOutPlayerInfo owner = (PacketPlayOutPlayerInfo) packet;
      for (PacketPlayOutPlayerInfo.PlayerInfoData entry : data) {
        GameProfile profile = entry.a();
        if (FakeManager.isFake(profile.getName())) {
          profile = FakeManager.cloneProfile(profile);
          entry = owner.new PlayerInfoData(profile, entry.b(), entry.c(), entry.d());
        }
        replaced.add(entry);
      }
      data.clear();
      data.addAll(replaced);
    } else if (packet instanceof PacketPlayOutChat) {
      IChatBaseComponent component = (IChatBaseComponent) NMSPacketAccess.get(packet, IChatBaseComponent.class, 0);
      if (component != null) {
        String json = IChatBaseComponent.ChatSerializer.a(component);
        String replaced = FakeManager.replaceNickedPlayers(json, true);
        NMSPacketAccess.set(packet, IChatBaseComponent.class, 0, IChatBaseComponent.ChatSerializer.a(replaced));
      }

      BaseComponent[] components = (BaseComponent[]) NMSPacketAccess.get(packet, BaseComponent[].class, 0);
      if (components != null) {
        List<BaseComponent> newComponents = new ArrayList<>();
        for (BaseComponent componentPart : components) {
          TextComponent newComponent = new TextComponent("");
          String json = ComponentSerializer.toString(componentPart);
          for (BaseComponent replacedPart : ComponentSerializer.parse(FakeManager.replaceNickedPlayers(json, true))) {
            newComponent.addExtra(replacedPart);
          }
          newComponents.add(newComponent);
        }
        NMSPacketAccess.set(packet, BaseComponent[].class, 0, newComponents.toArray(new BaseComponent[0]));
      }
    } else if (packet instanceof PacketPlayOutScoreboardObjective) {
      String displayName = (String) NMSPacketAccess.get(packet, String.class, 1);
      NMSPacketAccess.set(packet, String.class, 1, FakeManager.replaceNickedPlayers(displayName, true));
    } else if (packet instanceof PacketPlayOutScoreboardScore) {
      String name = (String) NMSPacketAccess.get(packet, String.class, 0);
      NMSPacketAccess.set(packet, String.class, 0, FakeManager.replaceNickedPlayers(name, true));
    } else if (packet instanceof PacketPlayOutScoreboardTeam) {
      Collection<String> members = (Collection<String>) NMSPacketAccess.get(packet, Collection.class, 0);
      if (members != null) {
        List<String> replaced = new ArrayList<>(members.size());
        for (String member : members) {
          replaced.add(FakeManager.isFake(member) ? FakeManager.getFake(member) : member);
        }
        members.clear();
        members.addAll(replaced);
      }
    }
    return packet;
  }
}
