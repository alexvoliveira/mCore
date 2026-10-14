package tk.slicecollections.maxteer.nms.v1_21_R1;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;
import tk.slicecollections.maxteer.libraries.npclib.NPCLibrary;
import tk.slicecollections.maxteer.libraries.npclib.api.npc.NPC;
import tk.slicecollections.maxteer.libraries.npclib.npc.AbstractEntityController;

/**
 * Uses a Bukkit villager for server-side entity tracking and movement while
 * NPCAdapter renders the entity as a player on clients. This implementation
 * depends only on the Spigot API, not Paper-only pathfinder methods or NMS.
 */
public final class HumanController extends AbstractEntityController {

    private static final double WALK_STEP = 0.18D;
    private NPC npc;
    private BukkitTask navigationTask;

    @Override
    public void spawn(Location location, NPC npc) {
        this.npc = npc;
        super.spawn(location, npc);
        if (getBukkitEntity() != null && NPCLibrary.getPlugin() != null) {
            navigationTask = Bukkit.getScheduler().runTaskTimer(
                NPCLibrary.getPlugin(), this::updateNavigation, 1L, 1L
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
        if (entity == null || !entity.isValid() || npc == null) {
            cancelNavigationTask();
            return;
        }

        Location target = npc.getWalkingTo();
        boolean following = false;
        if (target == null) {
            Entity followed = npc.getFollowing();
            if (followed != null && followed.isValid()) {
                target = followed.getLocation();
                following = true;
            } else if (followed != null) {
                npc.setFollowing(null);
            }
        }

        if (target == null) {
            return;
        }
        if (target.getWorld() == null || !target.getWorld().equals(entity.getWorld())) {
            if (following) {
                npc.setFollowing(null);
            } else {
                npc.setWalkingTo(null);
                npc.finishNavigation();
            }
            return;
        }

        Location current = entity.getLocation();
        double distanceSquared = current.distanceSquared(target);
        if (!following && distanceSquared <= 2.25D) {
            npc.setWalkingTo(null);
            npc.finishNavigation();
            return;
        }
        if (following && distanceSquared > 625.0D) {
            npc.setFollowing(null);
            return;
        }

        double dx = target.getX() - current.getX();
        double dz = target.getZ() - current.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        Location next = current.clone();

        if (horizontalDistance > 0.05D) {
            double step = Math.min(WALK_STEP, horizontalDistance);
            next.add(dx / horizontalDistance * step, 0.0D, dz / horizontalDistance * step);
            next.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            next.setPitch(0.0F);
        } else {
            double dy = target.getY() - current.getY();
            if (Math.abs(dy) > 0.5D) {
                next.setY(current.getY() + Math.copySign(Math.min(0.2D, Math.abs(dy)), dy));
            }
        }

        if (!canOccupy(next)) {
            Location raised = next.clone().add(0.0D, 1.0D, 0.0D);
            if (canOccupy(raised)) {
                next = raised;
            } else {
                // Do not teleport the NPC through a solid wall.
                if (!following) {
                    npc.setWalkingTo(null);
                    npc.finishNavigation();
                }
                return;
            }
        }

        entity.teleport(next);
    }

    private boolean canOccupy(Location location) {
        if (location.getWorld() == null) {
            return false;
        }
        return location.getWorld().getBlockAt(location).isPassable()
            && location.getWorld().getBlockAt(location.clone().add(0.0D, 1.0D, 0.0D)).isPassable();
    }

    private void cancelNavigationTask() {
        if (navigationTask != null) {
            navigationTask.cancel();
            navigationTask = null;
        }
    }

    @Override
    public void remove() {
        cancelNavigationTask();
        NPCLibrary.unbindEntity(getBukkitEntity());
        npc = null;
        super.remove();
    }
}
