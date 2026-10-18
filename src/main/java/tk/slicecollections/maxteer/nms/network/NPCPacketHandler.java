package tk.slicecollections.maxteer.nms.network;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.libraries.npclib.NPCLibrary;
import tk.slicecollections.maxteer.libraries.npclib.api.npc.NPC;

import java.util.List;

public final class NPCPacketHandler implements NMSPacketInterceptor.PacketHandler {

  @Override
  public Object onPacketReceiving(Player player, Object packet) {
    return packet;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object onPacketSending(Player player, Object packet) {
    if (!(packet instanceof PacketPlayOutPlayerInfo)) {
      return packet;
    }

    List<PacketPlayOutPlayerInfo.PlayerInfoData> data =
        (List<PacketPlayOutPlayerInfo.PlayerInfoData>) NMSPacketAccess.get(packet, List.class, 0);
    GameProfile viewerProfile = ((CraftPlayer) player).getProfile();

    for (PacketPlayOutPlayerInfo.PlayerInfoData entry : data) {
      GameProfile profile = entry.a();
      NPC npc = NPCLibrary.findNPC(profile.getId());
      if (npc == null || !npc.data().get(NPC.COPY_PLAYER_SKIN, false)) {
        continue;
      }

      profile.getProperties().clear();
      viewerProfile.getProperties().get("textures").stream().findFirst().ifPresent(
          property -> profile.getProperties().put("textures", property));
    }
    return packet;
  }
}
