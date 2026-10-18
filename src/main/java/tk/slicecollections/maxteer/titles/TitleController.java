package tk.slicecollections.maxteer.titles;

import lombok.Getter;
import net.minecraft.server.v1_8_R3.DataWatcher;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.PacketPlayOutAttachEntity;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityLiving;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.nms.NMS;
import tk.slicecollections.maxteer.nms.network.NMSPacketAccess;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.reflection.Accessors;
import tk.slicecollections.maxteer.reflection.MinecraftReflection;
import tk.slicecollections.maxteer.reflection.acessors.FieldAccessor;

/**
 * @author Maxter
 */
public class TitleController {

  private static final FieldAccessor<Integer> ENTITY_ID =
      Accessors.getField(MinecraftReflection.getEntityClass(), "entityCount", int.class);

  @Getter
  private Player owner;
  private DataWatcher watcher;
  private boolean disabled = true;
  private int entityId;
  private String name;

  public TitleController(Player owner, String title) {
    this.owner = owner;
    this.name = title;
    this.watcher = createWatcher(title);
    this.entityId = ENTITY_ID.get(null);
    ENTITY_ID.set(null, this.entityId + 1);
  }

  private static DataWatcher createWatcher(String title) {
    DataWatcher watcher = new DataWatcher(null);
    watcher.a(0, (byte) 0x20);
    watcher.a(2, title);
    watcher.a(3, (byte) 1);
    watcher.a(5, (byte) 1);
    watcher.a(10, (byte) 1);
    return watcher;
  }

  public void setName(String name) {
    String previousName = this.name;
    this.name = name;
    this.watcher = createWatcher(name);

    if ("disabled".equals(previousName)) {
      Profile.listProfiles().forEach(profile -> {
        Player player = profile.getPlayer();
        if (player != null && player.canSee(this.owner)) {
          showToPlayer(player);
        }
      });
      return;
    }

    if ("disabled".equals(name)) {
      Profile.listProfiles().forEach(profile -> {
        Player player = profile.getPlayer();
        if (player != null && player.canSee(this.owner)) {
          hideToPlayer(player);
        }
      });
      return;
    }

    PacketPlayOutEntityMetadata metadata =
        new PacketPlayOutEntityMetadata(this.entityId, this.watcher, true);
    Profile.listProfiles().forEach(profile -> {
      Player player = profile.getPlayer();
      if (player != null && player.canSee(this.owner)) {
        NMS.sendPacket(player, metadata);
      }
    });
  }

  public void destroy() {
    this.disable();
    this.owner = null;
    this.watcher = null;
    this.name = null;
  }

  public void enable() {
    if (!this.disabled) {
      return;
    }

    this.disabled = false;
    Profile.listProfiles().forEach(profile -> {
      Player player = profile.getPlayer();
      if (player != null && player.canSee(this.owner)) {
        this.showToPlayer(player);
      }
    });
  }

  public void disable() {
    if (this.disabled) {
      return;
    }

    Profile.listProfiles().forEach(profile -> {
      Player player = profile.getPlayer();
      if (player != null && player.canSee(this.owner)) {
        this.hideToPlayer(player);
      }
    });
    this.disabled = true;
  }

  void showToPlayer(Player player) {
    if (player.equals(this.owner)) {
      return;
    }

    if (!this.disabled && !"disabled".equals(this.name)) {
      PacketPlayOutSpawnEntityLiving spawn = new PacketPlayOutSpawnEntityLiving();
      NMSPacketAccess.set(spawn, int.class, 0, this.entityId);
      NMSPacketAccess.set(spawn, int.class, 1, EntityType.ARMOR_STAND.getTypeId());
      NMSPacketAccess.set(spawn, int.class, 2,
          MathHelper.floor(this.owner.getLocation().getX() * 32.0D));
      NMSPacketAccess.set(spawn, int.class, 3,
          MathHelper.floor(this.owner.getLocation().getY() * 32.0D));
      NMSPacketAccess.set(spawn, int.class, 4,
          MathHelper.floor(this.owner.getLocation().getZ() * 32.0D));
      NMSPacketAccess.set(spawn, DataWatcher.class, 0, this.watcher);

      PacketPlayOutAttachEntity attach = new PacketPlayOutAttachEntity();
      // In 1.8_R3 the integer fields serialize as entity id, vehicle id, then attach type.
      NMSPacketAccess.set(attach, int.class, 1, this.entityId);
      NMSPacketAccess.set(attach, int.class, 2, this.owner.getEntityId());

      NMS.sendPacket(player, spawn);
      NMS.sendPacket(player, attach);
    }
  }

  void hideToPlayer(Player player) {
    if (player.equals(this.owner)) {
      return;
    }

    if (!this.disabled) {
      NMS.sendPacket(player, new PacketPlayOutEntityDestroy(this.entityId));
    }
  }
}
