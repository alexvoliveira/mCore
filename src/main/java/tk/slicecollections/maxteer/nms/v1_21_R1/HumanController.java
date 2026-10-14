package tk.slicecollections.maxteer.nms.v1_21_R1;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;
import tk.slicecollections.maxteer.libraries.npclib.NPCLibrary;
import tk.slicecollections.maxteer.libraries.npclib.api.npc.NPC;
import tk.slicecollections.maxteer.libraries.npclib.npc.AbstractEntityController;

/**
 * Keeps a server-side mob for navigation while NPCAdapter displays it as a
 * client-side player entity. This avoids depending on CraftBukkit/NMS internals.
 */
public final class HumanController extends AbstractEntityController {

    private NPC npc;
    private BukkitTask navigationTask;

    @Override
    public void spawn(Location location, NPC npc) {
        this.npc = npc;
        super.spawn(location, npc);
        if (getBukkitEntity() != null && NPCLibrary.getPlugin() != null) {
            navigationTask = Bukkit.getScheduler().runTaskTimer(
                NPCLibrary.getPlugin(), this::updateNavigation, 1L, 5L
            );
        }
    }

    @Override
    protected Entity createEntity(Location location, NPC npc) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return location.getWorld().spawn(location, Villager.class, villager -> {
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(false);
            villager.setCollidable(false);
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
            NPCLibrary.bindEntity(villager, npc);
        });
    }

    private void updateNavigation() {
        Entity entity = getBukkitEntity();
        if (!(entity instanceof Mob) || !entity.isValid() || npc == null) {
            if (navigationTask != null) {
                navigationTask.cancel();
                navigationTask = null;
            }
            return;
        }

        Mob mob = (Mob) entity;
        Location target = npc.getWalkingTo();
        boolean following = false;
        if (target == null) {
            Entity followed = npc.getFollowing();
            if (followed != null && followed.isValid()) {
                target = followed.getLocation();
                following = true;
                if (target.getWorld() != entity.getWorld()) {
                    npc.setFollowing(null);
                    mob.setAI(false);
                    return;
                }
            }
        }

        if (target == null) {
            mob.setAI(false);
            return;
        }
        if (target.getWorld() != entity.getWorld()) {
            mob.setAI(false);
            if (following) {
                npc.setFollowing(null);
            } else {
                npc.setWalkingTo(null);
                npc.finishNavigation();
            }
            return;
        }

        if (following && entity.getLocation().distanceSquared(target) > 625.0D) {
            npc.setFollowing(null);
            mob.setAI(false);
            return;
        }

        if (!following && entity.getLocation().distanceSquared(target) <= 2.25D) {
            mob.setAI(false);
            npc.setWalkingTo(null);
            npc.finishNavigation();
            return;
        }

        mob.setAI(true);
        mob.getPathfinder().moveTo(target, 1.0D);
    }

    @Override
    public void remove() {
        if (navigationTask != null) {
            navigationTask.cancel();
            navigationTask = null;
        }
        NPCLibrary.unbindEntity(getBukkitEntity());
        npc = null;
        super.remove();
    }
}
