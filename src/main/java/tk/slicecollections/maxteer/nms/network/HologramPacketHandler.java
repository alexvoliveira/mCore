package tk.slicecollections.maxteer.nms.network;

import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntity;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityLiving;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.libraries.holograms.HologramLibrary;
import tk.slicecollections.maxteer.libraries.holograms.api.Hologram;
import tk.slicecollections.maxteer.nms.NMS;

public final class HologramPacketHandler implements NMSPacketInterceptor.PacketHandler {

  @Override
  public Object onPacketReceiving(Player player, Object packet) {
    return packet;
  }

  @Override
  public Object onPacketSending(Player player, Object packet) {
    if (!(packet instanceof PacketPlayOutSpawnEntity)
        && !(packet instanceof PacketPlayOutSpawnEntityLiving)
        && !(packet instanceof PacketPlayOutEntityMetadata)) {
      return packet;
    }

    int entityId = (Integer) NMSPacketAccess.get(packet, int.class, 0);
    Entity entity = HologramLibrary.getHologramEntity(entityId);
    Hologram hologram = null;

    if (entity == null || !HologramLibrary.isHologramEntity(entity)
        || (hologram = HologramLibrary.getHologram(entity)) == null) {
      hologram = NMS.getPreHologram(entityId);
    }

    if (hologram == null || hologram.canSee(player)) {
      return packet;
    }
    return null;
  }
}
