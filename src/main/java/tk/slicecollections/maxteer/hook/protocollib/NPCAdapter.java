package tk.slicecollections.maxteer.hook.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import tk.slicecollections.maxteer.libraries.npclib.NPCLibrary;
import tk.slicecollections.maxteer.libraries.npclib.api.npc.NPC;
import tk.slicecollections.maxteer.libraries.npclib.npc.skin.Skin;

import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Replaces the server-side Villager used for NPC navigation with a client-side
 * player entity. No CraftBukkit/NMS classes or versioned internals are used.
 */
public final class NPCAdapter extends PacketAdapter {

    public NPCAdapter() {
        super(params()
            .plugin(NPCLibrary.getPlugin())
            .types(PacketType.Play.Server.SPAWN_ENTITY, PacketType.Play.Server.ENTITY_METADATA));
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet.getType() == PacketType.Play.Server.SPAWN_ENTITY) {
            int entityId = packet.getIntegers().read(0);
            NPC npc = NPCLibrary.findNPCByEntityId(entityId);
            if (npc != null) {
                event.setCancelled(true);
                sendFakePlayer(event.getPlayer(), npc, entityId);
            }
            return;
        }

        if (packet.getType() == PacketType.Play.Server.ENTITY_METADATA) {
            int entityId = packet.getIntegers().read(0);
            if (NPCLibrary.findNPCByEntityId(entityId) != null) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        // NPC interactions are resolved through Bukkit events and entity IDs.
    }

    public static void refreshNPC(NPC npc) {
        if (npc == null || !npc.isSpawned() || NPCLibrary.getPlugin() == null) {
            return;
        }

        Entity entity = npc.getEntity();
        if (entity == null || !entity.isValid()) {
            return;
        }

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(entity.getWorld())
                || viewer.getLocation().distanceSquared(entity.getLocation()) > 4096.0D) {
                continue;
            }

            PacketContainer destroy = manager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, Collections.singletonList(entity.getEntityId()));
            manager.sendServerPacket(viewer, destroy);
            sendFakePlayer(viewer, npc, entity.getEntityId());
        }
    }

    private static void sendFakePlayer(Player viewer, NPC npc, int entityId) {
        Entity entity = npc.getEntity();
        if (entity == null || !entity.isValid() || npc.getUUID() == null) {
            return;
        }

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        WrappedGameProfile profile = new WrappedGameProfile(npc.getUUID(), profileName(npc.getName()));
        if (npc.data().get(NPC.COPY_PLAYER_SKIN, false)) {
            copyViewerSkin(profile, viewer);
        } else {
            Skin skin = npc.getSkin();
            if (skin != null && skin.getValue() != null && skin.getSignature() != null) {
                profile.getProperties().put("textures",
                    new WrappedSignedProperty("textures", skin.getValue(), skin.getSignature()));
            }
        }

        PacketContainer info = manager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        info.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));
        info.getPlayerInfoDataLists().write(0, Collections.singletonList(
            new PlayerInfoData(profile, 0, EnumWrappers.NativeGameMode.SURVIVAL,
                WrappedChatComponent.fromText(npc.getName() == null ? "" : npc.getName()))
        ));
        manager.sendServerPacket(viewer, info);

        Location location = entity.getLocation();
        PacketContainer spawn = manager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        spawn.getIntegers().write(0, entityId);
        spawn.getUUIDs().write(0, npc.getUUID());
        spawn.getDoubles().write(0, location.getX());
        spawn.getDoubles().write(1, location.getY());
        spawn.getDoubles().write(2, location.getZ());
        spawn.getBytes().write(0, encodeAngle(location.getYaw()));
        spawn.getBytes().write(1, encodeAngle(location.getPitch()));
        manager.sendServerPacket(viewer, spawn);

        if (npc.data().get(NPC.TAB_LIST_KEY, true)) {
            Plugin plugin = NPCLibrary.getPlugin();
            if (plugin != null && plugin.isEnabled()) {
                UUID uuid = npc.getUUID();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    PacketContainer remove = manager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                    remove.getUUIDLists().write(0, Collections.singletonList(uuid));
                    manager.sendServerPacket(viewer, remove);
                }, 20L);
            }
        }
    }

    private static void copyViewerSkin(WrappedGameProfile profile, Player viewer) {
        WrappedGameProfile viewerProfile = WrappedGameProfile.fromPlayer(viewer);
        profile.getProperties().clear();
        viewerProfile.getProperties().get("textures").stream().findFirst()
            .ifPresent(property -> profile.getProperties().put("textures", property));
    }

    private static String profileName(String name) {
        String safe = name == null ? "NPC" : name.replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.isEmpty()) {
            safe = "NPC";
        }
        return safe.substring(0, Math.min(16, safe.length()));
    }

    private static byte encodeAngle(float angle) {
        return (byte) Math.floor(angle * 256.0F / 360.0F);
    }
}
