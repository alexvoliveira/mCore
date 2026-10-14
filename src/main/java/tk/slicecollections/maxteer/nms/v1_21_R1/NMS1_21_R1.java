package tk.slicecollections.maxteer.nms.v1_21_R1;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import tk.slicecollections.maxteer.libraries.holograms.api.Hologram;
import tk.slicecollections.maxteer.libraries.holograms.api.HologramLine;
import tk.slicecollections.maxteer.libraries.npclib.api.npc.NPCAnimation;
import tk.slicecollections.maxteer.libraries.npclib.npc.skin.SkinnableEntity;
import tk.slicecollections.maxteer.nms.interfaces.INMS;
import tk.slicecollections.maxteer.nms.interfaces.entity.IArmorStand;
import tk.slicecollections.maxteer.nms.interfaces.entity.IItem;
import tk.slicecollections.maxteer.nms.interfaces.entity.ISlime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit-backed compatibility layer for Spigot 1.21.1.
 *
 * Custom entities are implemented through the public Bukkit API. Packet-level
 * behavior is delegated to ProtocolLib rather than CraftBukkit/NMS internals.
 */
public final class NMS1_21_R1 implements INMS {

    private final Map<Integer, HologramLine> hologramLines = new ConcurrentHashMap<>();

    @Override
    public IArmorStand createArmorStand(Location location, String name, HologramLine line) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        ArmorStand entity = location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setArms(false);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setCustomName(name);
            stand.setCustomNameVisible(name != null && !name.isEmpty());
            if (line != null) {
                hologramLines.put(stand.getEntityId(), line);
            }
        });
        return new ArmorStandHandle(entity, line);
    }

    @Override
    public IItem createItem(Location location, ItemStack stack, HologramLine line) {
        if (location == null || location.getWorld() == null || stack == null) {
            return null;
        }

        Item entity = location.getWorld().dropItem(location, stack, item -> {
            item.setGravity(false);
            item.setInvulnerable(true);
            item.setSilent(true);
            item.setPersistent(false);
            item.setUnlimitedLifetime(true);
            item.setPickupDelay(0);
            if (line != null) {
                hologramLines.put(item.getEntityId(), line);
            }
        });
        return new ItemHandle(entity, line);
    }

    @Override
    public ISlime createSlime(Location location, HologramLine line) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        Slime entity = location.getWorld().spawn(location, Slime.class, slime -> {
            slime.setSize(1);
            slime.setAI(false);
            slime.setInvisible(true);
            slime.setGravity(false);
            slime.setInvulnerable(true);
            slime.setSilent(true);
            slime.setPersistent(false);
            slime.setCollidable(false);
            if (line != null) {
                hologramLines.put(slime.getEntityId(), line);
            }
        });
        return new SlimeHandle(entity, line);
    }

    @Override
    public Hologram getHologram(Entity entity) {
        HologramLine line = getHologramLine(entity);
        return line == null ? null : line.getHologram();
    }

    @Override
    public Hologram getPreHologram(int entityId) {
        HologramLine line = hologramLines.get(entityId);
        return line == null ? null : line.getHologram();
    }

    @Override
    public HologramLine getHologramLine(Entity entity) {
        return entity == null ? null : hologramLines.get(entity.getEntityId());
    }

    @Override
    public boolean isHologramEntity(Entity entity) {
        return getHologramLine(entity) != null;
    }

    @Override
    public boolean isHologramItem(Entity entity) {
        return entity instanceof Item && isHologramEntity(entity);
    }

    private void unregister(Entity entity) {
        if (entity != null) {
            hologramLines.remove(entity.getEntityId());
            entity.remove();
        }
    }

    @Override
    public void playAnimation(Entity entity, NPCAnimation animation) {
        if (entity == null || animation == null
            || animation == NPCAnimation.CROUCH || animation == NPCAnimation.UNCROUCH) {
            return;
        }

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ANIMATION);
        packet.getIntegers().write(0, entity.getEntityId());
        packet.getBytes().write(0, (byte) animation.getId());
        for (Player viewer : entity.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(entity.getLocation()) <= 4096.0D) {
                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, packet);
            }
        }
    }

    @Override
    public void setValueAndSignature(Player player, String value, String signature) {
        // Texture properties are applied to the synthetic player profile in NPCAdapter.
    }

    @Override
    public void sendTabListAdd(Player player, Player listPlayer) {
        if (player == null || listPlayer == null) {
            return;
        }

        WrappedGameProfile profile = WrappedGameProfile.fromPlayer(listPlayer);
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);
        packet.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));
        packet.getPlayerInfoDataLists().write(0, Collections.singletonList(new PlayerInfoData(
            profile, 0, EnumWrappers.NativeGameMode.valueOf(listPlayer.getGameMode().name()),
            WrappedChatComponent.fromText(listPlayer.getPlayerListName() == null
                ? listPlayer.getName() : listPlayer.getPlayerListName())
        )));
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    }

    @Override
    public void sendTabListRemove(Player player, Collection<SkinnableEntity> skinnableEntities) {
        if (player == null || skinnableEntities == null || skinnableEntities.isEmpty()) {
            return;
        }
        List<UUID> uuids = new ArrayList<>();
        for (SkinnableEntity skinnable : skinnableEntities) {
            if (skinnable != null && skinnable.getEntity() != null) {
                uuids.add(skinnable.getEntity().getUniqueId());
            }
        }
        sendTabListRemove(player, uuids);
    }

    @Override
    public void sendTabListRemove(Player player, Player listPlayer) {
        if (player != null && listPlayer != null) {
            sendTabListRemove(player, Collections.singletonList(listPlayer.getUniqueId()));
        }
    }

    private void sendTabListRemove(Player player, List<UUID> uuids) {
        if (uuids.isEmpty()) {
            return;
        }
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        packet.getUUIDLists().write(0, uuids);
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    }

    @Override
    public void removeFromPlayerList(Player player) {
        if (player == null) {
            return;
        }
        for (Player viewer : player.getServer().getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                sendTabListRemove(viewer, player);
            }
        }
    }

    @Override
    public void removeFromServerPlayerList(Player player) {
        // Bukkit owns the server-side player list; there is no public removal API.
    }

    @Override
    public boolean addToWorld(World world, Entity entity, SpawnReason reason) {
        return world != null && entity != null && entity.isValid() && entity.getWorld().equals(world);
    }

    @Override
    public void removeFromWorld(Entity entity) {
        unregister(entity);
    }

    @Override
    public void replaceTrackerEntry(Player player) {
        // Entity tracker internals are implementation details and are not accessed.
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        if (player == null || packet == null) {
            return;
        }
        if (!(packet instanceof PacketContainer)) {
            throw new IllegalArgumentException("Spigot 1.21.1 requires a ProtocolLib PacketContainer");
        }
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, (PacketContainer) packet);
    }

    @Override
    public void look(Entity entity, float yaw, float pitch) {
        if (entity != null) {
            entity.setRotation(yaw, pitch);
        }
    }

    @Override
    public void setHeadYaw(Entity entity, float yaw) {
        if (entity != null) {
            entity.setRotation(yaw, entity.getLocation().getPitch());
        }
    }

    @Override
    public void setStepHeight(LivingEntity entity, float height) {
        // Step height is not exposed by the Bukkit API in 1.21.1.
    }

    @Override
    public float getStepHeight(LivingEntity entity) {
        return 1.0F;
    }

    @Override
    public SkinnableEntity getSkinnable(Entity entity) {
        return entity instanceof SkinnableEntity ? (SkinnableEntity) entity : null;
    }

    @Override
    public void flyingMoveLogic(LivingEntity entity, float strafe, float forward) {
        // Custom flying NPC navigation is not exposed through the Bukkit API.
    }

    @Override
    public void sendActionBar(Player player, String message) {
        if (player != null) {
            player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message == null ? "" : message)
            );
        }
    }

    @Override
    public void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 20, 60, 20);
    }

    @Override
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player != null) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    @Override
    public void sendTabHeaderFooter(Player player, String header, String footer) {
        if (player != null) {
            player.setPlayerListHeaderFooter(header, footer);
        }
    }

    @Override
    public void refreshPlayer(Player player) {
        if (player != null) {
            player.updateInventory();
        }
    }

    private final class ArmorStandHandle implements IArmorStand {
        private final ArmorStand entity;
        private final HologramLine line;

        private ArmorStandHandle(ArmorStand entity, HologramLine line) {
            this.entity = entity;
            this.line = line;
        }

        @Override public int getId() { return entity.getEntityId(); }
        @Override public void setName(String name) {
            entity.setCustomName(name);
            entity.setCustomNameVisible(name != null && !name.isEmpty());
        }
        @Override public void setLocation(double x, double y, double z) {
            Location current = entity.getLocation();
            entity.teleport(new Location(entity.getWorld(), x, y, z, current.getYaw(), current.getPitch()));
        }
        @Override public boolean isDead() { return entity.isDead(); }
        @Override public void killEntity() { unregister(entity); }
        @Override public ArmorStand getEntity() { return entity; }
        @Override public HologramLine getLine() { return line; }
    }

    private final class ItemHandle implements IItem {
        private final Item entity;
        private final HologramLine line;

        private ItemHandle(Item entity, HologramLine line) {
            this.entity = entity;
            this.line = line;
        }

        @Override public void setPassengerOf(Entity mount) {
            if (mount != null && mount.getWorld().equals(entity.getWorld())) {
                mount.addPassenger(entity);
            }
        }
        @Override public void setItemStack(ItemStack stack) {
            if (stack != null) {
                entity.setItemStack(stack);
            }
        }
        @Override public void setLocation(double x, double y, double z) {
            entity.teleport(new Location(entity.getWorld(), x, y, z));
        }
        @Override public boolean isDead() { return entity.isDead(); }
        @Override public void killEntity() { unregister(entity); }
        @Override public Item getEntity() { return entity; }
        @Override public HologramLine getLine() { return line; }
    }

    private final class SlimeHandle implements ISlime {
        private final Slime entity;
        private final HologramLine line;

        private SlimeHandle(Slime entity, HologramLine line) {
            this.entity = entity;
            this.line = line;
        }

        @Override public void setPassengerOf(Entity mount) {
            if (mount != null && mount.getWorld().equals(entity.getWorld())) {
                mount.addPassenger(entity);
            }
        }
        @Override public void setLocation(double x, double y, double z) {
            entity.teleport(new Location(entity.getWorld(), x, y, z));
        }
        @Override public boolean isDead() { return entity.isDead(); }
        @Override public void killEntity() { unregister(entity); }
        @Override public Slime getEntity() { return entity; }
        @Override public HologramLine getLine() { return line; }
    }
}
