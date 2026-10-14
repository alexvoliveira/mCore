package tk.slicecollections.maxteer.libraries.npclib;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.HandlerList;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import tk.slicecollections.maxteer.libraries.npclib.api.EntityController;
import tk.slicecollections.maxteer.libraries.npclib.api.npc.NPC;
import tk.slicecollections.maxteer.libraries.npclib.npc.AbstractNPC;
import tk.slicecollections.maxteer.libraries.npclib.npc.EntityControllers;
import tk.slicecollections.maxteer.libraries.npclib.npc.ai.NPCHolder;
import tk.slicecollections.maxteer.plugin.MPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Maxter
 */
public class NPCLibrary {

  private static Plugin plugin;
  private static Listener LISTENER;
  private static final List<NPC> NPCS = new ArrayList<>();
  private static final Map<Integer, NPC> ENTITY_NPCS = new ConcurrentHashMap<>();

  public static void setupNPCs(MPlugin pl) {
    if (pl == null || plugin != null) {
      return;
    }

    plugin = pl;
    LISTENER = new NPCListeners();
    Bukkit.getServer().getPluginManager().registerEvents(LISTENER, pl);
  }

  public static NPC createNPC(EntityType type, String name) {
    return createNPC(type, UUID.randomUUID(), name);
  }

  public static NPC createNPC(EntityType type, UUID uuid, String name) {
    Preconditions.checkNotNull(type, "Tipo nao pode ser null");
    Preconditions.checkNotNull(name, "Nome nao pode ser null");

    EntityController controller = EntityControllers.getController(type);
    NPC npc = new AbstractNPC(uuid, name, controller);
    NPCS.add(npc);
    return npc;
  }

  public static void unregister(NPC npc) {
    NPCS.remove(npc);
    ENTITY_NPCS.values().removeIf(candidate -> candidate == npc);
    if (LISTENER instanceof NPCListeners) {
      ((NPCListeners) LISTENER).removeNPC(npc);
    }
  }

  public static void unregisterAll() {
    for (NPC npc : listNPCS()) {
      npc.destroy();
    }

    if (LISTENER != null) {
      HandlerList.unregisterAll(LISTENER);
      if (LISTENER instanceof NPCListeners) {
        ((NPCListeners) LISTENER).shutdown();
      }
      LISTENER = null;
    }
    NPCS.clear();
    ENTITY_NPCS.clear();
    plugin = null;
  }

  public static boolean isNPC(Entity entity) {
    return getNPC(entity) != null;
  }

  public static NPC getNPC(Entity entity) {
    if (entity == null) {
      return null;
    }
    if (entity instanceof NPCHolder) {
      return ((NPCHolder) entity).getNPC();
    }
    for (MetadataValue value : entity.getMetadata("NPC")) {
      Object stored = value.value();
      if (stored instanceof NPC) {
        return (NPC) stored;
      }
    }
    return ENTITY_NPCS.get(entity.getEntityId());
  }

  public static NPC findNPCByEntityId(int entityId) {
    return ENTITY_NPCS.get(entityId);
  }

  public static void bindEntity(Entity entity, NPC npc) {
    if (entity == null || npc == null) {
      return;
    }
    ENTITY_NPCS.put(entity.getEntityId(), npc);
    if (plugin != null) {
      entity.setMetadata("NPC", new FixedMetadataValue(plugin, npc));
    }
  }

  public static void unbindEntity(Entity entity) {
    if (entity != null) {
      ENTITY_NPCS.remove(entity.getEntityId());
    }
  }

  public static NPC findNPC(UUID uuid) {
    return listNPCS().stream().filter(npc -> npc.getUUID().equals(uuid)).findFirst().orElse(null);
  }

  public static Plugin getPlugin() {
    return plugin;
  }

  public static Collection<NPC> listNPCS() {
    return ImmutableList.copyOf(NPCS);
  }
}
