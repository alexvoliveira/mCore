package tk.slicecollections.maxteer.utils.particles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public enum ParticleEffect {

  EXPLOSION_NORMAL("explode", 0, -1, ParticleProperty.DIRECTIONAL),
  EXPLOSION_LARGE("largeexplode", 1, -1),
  FIREWORKS_SPARK("fireworksSpark", 3, -1, ParticleProperty.DIRECTIONAL),
  WATER_BUBBLE("bubble", 4, -1, ParticleProperty.DIRECTIONAL, ParticleProperty.REQUIRES_WATER),
  WATER_SPLASH("splash", 5, -1, ParticleProperty.DIRECTIONAL),
  WATER_WAKE("wake", 6, 7, ParticleProperty.DIRECTIONAL),
  SUSPENDED("suspended", 7, -1, ParticleProperty.REQUIRES_WATER),
  SUSPENDED_DEPTH("depthSuspend", 8, -1, ParticleProperty.DIRECTIONAL),
  CRIT("crit", 9, -1, ParticleProperty.DIRECTIONAL),
  CRIT_MAGIC("magicCrit", 10, -1, ParticleProperty.DIRECTIONAL),
  SMOKE_NORMAL("smoke", 11, -1, ParticleProperty.DIRECTIONAL),
  SMOKE_LARGE("largesmoke", 12, -1, ParticleProperty.DIRECTIONAL),
  SPELL("spell", 13, -1),
  SPELL_INSTANT("instantSpell", 14, -1),
  SPELL_MOB("mobSpell", 15, -1, ParticleProperty.COLORABLE),
  SPELL_MOB_AMBIENT("mobSpellAmbient", 16, -1, ParticleProperty.COLORABLE),
  SPELL_WITCH("witchMagic", 17, -1),
  DRIP_WATER("dripWater", 18, -1),
  DRIP_LAVA("dripLava", 19, -1),
  VILLAGER_ANGRY("angryVillager", 20, -1),
  VILLAGER_HAPPY("happyVillager", 21, -1, ParticleProperty.DIRECTIONAL),
  TOWN_AURA("townaura", 22, -1, ParticleProperty.DIRECTIONAL),
  NOTE("note", 23, -1, ParticleProperty.COLORABLE),
  PORTAL("portal", 24, -1, ParticleProperty.DIRECTIONAL),
  ENCHANTMENT_TABLE("enchantmenttable", 25, -1, ParticleProperty.DIRECTIONAL),
  FLAME("flame", 26, -1, ParticleProperty.DIRECTIONAL),
  LAVA("lava", 27, -1),
  FOOTSTEP("footstep", 28, -1),
  CLOUD("cloud", 29, -1, ParticleProperty.DIRECTIONAL),
  REDSTONE("reddust", 30, -1, ParticleProperty.COLORABLE),
  SNOWBALL("snowballpoof", 31, -1),
  SNOW_SHOVEL("snowshovel", 32, -1, ParticleProperty.DIRECTIONAL),
  SLIME("slime", 33, -1),
  HEART("heart", 34, -1),
  BARRIER("barrier", 35, 8),
  ITEM_CRACK("iconcrack", 36, -1, ParticleProperty.DIRECTIONAL, ParticleProperty.REQUIRES_DATA),
  BLOCK_CRACK("blockcrack", 37, -1, ParticleProperty.DIRECTIONAL, ParticleProperty.REQUIRES_DATA),
  BLOCK_DUST("blockdust", 38, 7, ParticleProperty.DIRECTIONAL, ParticleProperty.REQUIRES_DATA),
  WATER_DROP("droplet", 39, 8),
  ITEM_TAKE("take", 40, 8),
  MOB_APPEARANCE("mobappearance", 41, 8);

  private static final Map<String, ParticleEffect> NAME_MAP = new HashMap<String, ParticleEffect>();
  private static final Map<Integer, ParticleEffect> ID_MAP = new HashMap<Integer, ParticleEffect>();
  private final String name;
  private final int id;
  private final int requiredVersion;
  private final List<ParticleProperty> properties;

  static {
    for (ParticleEffect effect : values()) {
      NAME_MAP.put(effect.name, effect);
      ID_MAP.put(effect.id, effect);
    }
  }

  private ParticleEffect(String name, int id, int requiredVersion, ParticleProperty... properties) {
    this.name = name;
    this.id = id;
    this.requiredVersion = requiredVersion;
    this.properties = Arrays.asList(properties);
  }

  public String getName() {
    return name;
  }

  public int getId() {
    return id;
  }

  public int getRequiredVersion() {
    return requiredVersion;
  }

  public boolean hasProperty(ParticleProperty property) {
    return properties.contains(property);
  }

  public boolean isSupported() {
    return ParticlePacket.getBukkitParticle(this) != null;
  }

  public static ParticleEffect fromName(String name) {
    for (Entry<String, ParticleEffect> entry : NAME_MAP.entrySet())
      if (!entry.getKey().equalsIgnoreCase(name))
        continue;
      else
        return entry.getValue();
    return null;
  }

  public static ParticleEffect fromId(int id) {
    for (Entry<Integer, ParticleEffect> entry : ID_MAP.entrySet()) {
      if (entry.getKey() != id) {
        continue;
      }
      return entry.getValue();
    }
    return null;
  }

  private static boolean isWater(Location location) {
    Material material = location.getBlock().getType();
    return material == Material.WATER;
  }

  private static boolean isLongDistance(Location location, List<Player> players) {
    for (Player player : players) {
      if (player.getLocation().getWorld().equals(location.getWorld()) && player.getLocation().distanceSquared(location) < 65536) {
        continue;
      }
      return true;
    }
    return false;
  }

  private static boolean isDataCorrect(ParticleEffect effect, ParticleData data) {
    return ((effect == BLOCK_CRACK || effect == BLOCK_DUST) && data instanceof BlockData) || (effect == ITEM_CRACK && data instanceof ItemData);
  }

  private static boolean isColorCorrect(ParticleEffect effect, ParticleColor color) {
    return ((effect == SPELL_MOB || effect == SPELL_MOB_AMBIENT || effect == REDSTONE) && color instanceof OrdinaryColor) || (effect == NOTE && color instanceof NoteColor);
  }

  public void display(float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, double range)
    throws ParticleVersionException, ParticleDataException, IllegalArgumentException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (hasProperty(ParticleProperty.REQUIRES_DATA)) {
      throw new ParticleDataException("This particle effect requires additional data");
    }
    if (hasProperty(ParticleProperty.REQUIRES_WATER) && !isWater(center)) {
      throw new IllegalArgumentException("There is no water at the center location");
    }
    new ParticlePacket(this, offsetX, offsetY, offsetZ, speed, amount, range > 256, null).sendTo(center, range);
  }

  public void display(float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, List<Player> players)
    throws ParticleVersionException, ParticleDataException, IllegalArgumentException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (hasProperty(ParticleProperty.REQUIRES_DATA)) {
      throw new ParticleDataException("This particle effect requires additional data");
    }
    if (hasProperty(ParticleProperty.REQUIRES_WATER) && !isWater(center)) {
      throw new IllegalArgumentException("There is no water at the center location");
    }
    new ParticlePacket(this, offsetX, offsetY, offsetZ, speed, amount, isLongDistance(center, players), null).sendTo(center, players);
  }

  public void display(float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, Player... players)
    throws ParticleVersionException, ParticleDataException, IllegalArgumentException {
    display(offsetX, offsetY, offsetZ, speed, amount, center, Arrays.asList(players));
  }

  public void display(Vector direction, float speed, Location center, double range) throws ParticleVersionException, ParticleDataException, IllegalArgumentException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (hasProperty(ParticleProperty.REQUIRES_DATA)) {
      throw new ParticleDataException("This particle effect requires additional data");
    }
    if (!hasProperty(ParticleProperty.DIRECTIONAL)) {
      throw new IllegalArgumentException("This particle effect is not directional");
    }
    if (hasProperty(ParticleProperty.REQUIRES_WATER) && !isWater(center)) {
      throw new IllegalArgumentException("There is no water at the center location");
    }
    new ParticlePacket(this, direction, speed, range > 256, null).sendTo(center, range);
  }

  public void display(Vector direction, float speed, Location center, List<Player> players) throws ParticleVersionException, ParticleDataException, IllegalArgumentException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (hasProperty(ParticleProperty.REQUIRES_DATA)) {
      throw new ParticleDataException("This particle effect requires additional data");
    }
    if (!hasProperty(ParticleProperty.DIRECTIONAL)) {
      throw new IllegalArgumentException("This particle effect is not directional");
    }
    if (hasProperty(ParticleProperty.REQUIRES_WATER) && !isWater(center)) {
      throw new IllegalArgumentException("There is no water at the center location");
    }
    new ParticlePacket(this, direction, speed, isLongDistance(center, players), null).sendTo(center, players);
  }

  public void display(Vector direction, float speed, Location center, Player... players) throws ParticleVersionException, ParticleDataException, IllegalArgumentException {
    display(direction, speed, center, Arrays.asList(players));
  }

  public void display(ParticleColor color, Location center, double range) throws ParticleVersionException, ParticleColorException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (!hasProperty(ParticleProperty.COLORABLE)) {
      throw new ParticleColorException("This particle effect is not colorable");
    }
    if (!isColorCorrect(this, color)) {
      throw new ParticleColorException("The particle color type is incorrect");
    }
    new ParticlePacket(this, color, range > 256).sendTo(center, range);
  }

  public void display(ParticleColor color, Location center, List<Player> players) throws ParticleVersionException, ParticleColorException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (!hasProperty(ParticleProperty.COLORABLE)) {
      throw new ParticleColorException("This particle effect is not colorable");
    }
    if (!isColorCorrect(this, color)) {
      throw new ParticleColorException("The particle color type is incorrect");
    }
    new ParticlePacket(this, color, isLongDistance(center, players)).sendTo(center, players);
  }

  public void display(ParticleColor color, Location center, Player... players) throws ParticleVersionException, ParticleColorException {
    display(color, center, Arrays.asList(players));
  }

  public void display(ParticleData data, float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, double range)
    throws ParticleVersionException, ParticleDataException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (!hasProperty(ParticleProperty.REQUIRES_DATA)) {
      throw new ParticleDataException("This particle effect does not require additional data");
    }
    if (!isDataCorrect(this, data)) {
      throw new ParticleDataException("The particle data type is incorrect");
    }
    new ParticlePacket(this, offsetX, offsetY, offsetZ, speed, amount, range > 256, data).sendTo(center, range);
  }

  public void display(ParticleData data, float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, List<Player> players)
    throws ParticleVersionException, ParticleDataException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (!hasProperty(ParticleProperty.REQUIRES_DATA)) {
      throw new ParticleDataException("This particle effect does not require additional data");
    }
    if (!isDataCorrect(this, data)) {
      throw new ParticleDataException("The particle data type is incorrect");
    }
    new ParticlePacket(this, offsetX, offsetY, offsetZ, speed, amount, isLongDistance(center, players), data).sendTo(center, players);
  }

  public void display(ParticleData data, float offsetX, float offsetY, float offsetZ, float speed, int amount, Location center, Player... players)
    throws ParticleVersionException, ParticleDataException {
    display(data, offsetX, offsetY, offsetZ, speed, amount, center, Arrays.asList(players));
  }

  public void display(ParticleData data, Vector direction, float speed, Location center, double range) throws ParticleVersionException, ParticleDataException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (!hasProperty(ParticleProperty.REQUIRES_DATA)) {
      throw new ParticleDataException("This particle effect does not require additional data");
    }
    if (!isDataCorrect(this, data)) {
      throw new ParticleDataException("The particle data type is incorrect");
    }
    new ParticlePacket(this, direction, speed, range > 256, data).sendTo(center, range);
  }

  public void display(ParticleData data, Vector direction, float speed, Location center, List<Player> players) throws ParticleVersionException, ParticleDataException {
    if (!isSupported()) {
      throw new ParticleVersionException("This particle effect is not supported by your server version");
    }
    if (!hasProperty(ParticleProperty.REQUIRES_DATA)) {
      throw new ParticleDataException("This particle effect does not require additional data");
    }
    if (!isDataCorrect(this, data)) {
      throw new ParticleDataException("The particle data type is incorrect");
    }
    new ParticlePacket(this, direction, speed, isLongDistance(center, players), data).sendTo(center, players);
  }

  public void display(ParticleData data, Vector direction, float speed, Location center, Player... players) throws ParticleVersionException, ParticleDataException {
    display(data, direction, speed, center, Arrays.asList(players));
  }

  public static enum ParticleProperty {
    REQUIRES_WATER,
    REQUIRES_DATA,
    DIRECTIONAL,
    COLORABLE;
  }


  public static abstract class ParticleData {
    private final Material material;
    private final byte data;
    private final int[] packetData;

    @SuppressWarnings("deprecation")
    public ParticleData(Material material, byte data) {
      this.material = material;
      this.data = data;
      this.packetData = new int[] {material.getId(), data};
    }

    public Material getMaterial() {
      return material;
    }

    public byte getData() {
      return data;
    }

    public int[] getPacketData() {
      return packetData;
    }

    public String getPacketDataString() {
      return "_" + packetData[0] + "_" + packetData[1];
    }
  }


  public static final class ItemData extends ParticleData {
    public ItemData(Material material, byte data) {
      super(material, data);
    }
  }


  public static final class BlockData extends ParticleData {
    public BlockData(Material material, byte data) throws IllegalArgumentException {
      super(material, data);
      if (!material.isBlock()) {
        throw new IllegalArgumentException("The material is not a block");
      }
    }
  }


  public static abstract class ParticleColor {
    public abstract float getValueX();

    public abstract float getValueY();

    public abstract float getValueZ();
  }


  public static final class OrdinaryColor extends ParticleColor {
    private final int red;
    private final int green;
    private final int blue;

    public OrdinaryColor(int red, int green, int blue) throws IllegalArgumentException {
      if (red < 0) {
        throw new IllegalArgumentException("The red value is lower than 0");
      }
      if (red > 255) {
        throw new IllegalArgumentException("The red value is higher than 255");
      }
      this.red = red;
      if (green < 0) {
        throw new IllegalArgumentException("The green value is lower than 0");
      }
      if (green > 255) {
        throw new IllegalArgumentException("The green value is higher than 255");
      }
      this.green = green;
      if (blue < 0) {
        throw new IllegalArgumentException("The blue value is lower than 0");
      }
      if (blue > 255) {
        throw new IllegalArgumentException("The blue value is higher than 255");
      }
      this.blue = blue;
    }

    public int getRed() {
      return red;
    }

    public int getGreen() {
      return green;
    }

    public int getBlue() {
      return blue;
    }

    @Override
    public float getValueX() {
      return (float) red / 255F;
    }

    @Override
    public float getValueY() {
      return (float) green / 255F;
    }

    @Override
    public float getValueZ() {
      return (float) blue / 255F;
    }
  }


  public static final class NoteColor extends ParticleColor {
    private final int note;

    public NoteColor(int note) throws IllegalArgumentException {
      if (note < 0) {
        throw new IllegalArgumentException("The note value is lower than 0");
      }
      if (note > 24) {
        throw new IllegalArgumentException("The note value is higher than 24");
      }
      this.note = note;
    }

    @Override
    public float getValueX() {
      return (float) note / 24F;
    }

    @Override
    public float getValueY() {
      return 0;
    }

    @Override
    public float getValueZ() {
      return 0;
    }

  }


  private static final class ParticleDataException extends RuntimeException {
    private static final long serialVersionUID = 3203085387160737484L;

    public ParticleDataException(String message) {
      super(message);
    }
  }


  private static final class ParticleColorException extends RuntimeException {
    private static final long serialVersionUID = 3203085387160737484L;

    public ParticleColorException(String message) {
      super(message);
    }
  }


  private static final class ParticleVersionException extends RuntimeException {
    private static final long serialVersionUID = 3203085387160737484L;

    public ParticleVersionException(String message) {
      super(message);
    }
  }


  /**
   * Sends particles through Bukkit's public API instead of reflecting into NMS.
   * The numeric IDs and data bytes above remain for source compatibility with
   * callers of the legacy mCore API; modern block/item variants use Bukkit data.
   */
  public static final class ParticlePacket {
    private static int version;
    private static boolean initialized;
    private final ParticleEffect effect;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;
    private final float speed;
    private final int amount;
    private final boolean longDistance;
    private final ParticleData data;

    public ParticlePacket(ParticleEffect effect, float offsetX, float offsetY, float offsetZ,
                          float speed, int amount, boolean longDistance, ParticleData data) {
      initialize();
      if (effect == null) {
        throw new IllegalArgumentException("Particle effect cannot be null");
      }
      if (speed < 0) {
        throw new IllegalArgumentException("The speed is lower than 0");
      }
      if (amount < 0) {
        throw new IllegalArgumentException("The amount is lower than 0");
      }
      if (getBukkitParticle(effect) == null) {
        throw new IllegalArgumentException("Particle effect " + effect.name() + " has no Bukkit equivalent in 1.21.1");
      }
      this.effect = effect;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.offsetZ = offsetZ;
      this.speed = speed;
      this.amount = amount;
      this.longDistance = longDistance;
      this.data = data;
    }

    public ParticlePacket(ParticleEffect effect, Vector direction, float speed,
                          boolean longDistance, ParticleData data) {
      this(effect, (float) direction.getX(), (float) direction.getY(), (float) direction.getZ(),
          speed, 0, longDistance, data);
    }

    public ParticlePacket(ParticleEffect effect, ParticleColor color, boolean longDistance) {
      this(effect, color.getValueX(), color.getValueY(), color.getValueZ(), 1, 0, longDistance, null);
    }

    public static synchronized void initialize() {
      if (initialized) {
        return;
      }
      String bukkitVersion = Bukkit.getBukkitVersion();
      if (bukkitVersion == null || bukkitVersion.isEmpty()) {
        throw new IllegalStateException("Cannot determine the current Bukkit version");
      }
      String[] parts = bukkitVersion.split("-", 2)[0].split("\\.");
      if (parts.length < 2) {
        throw new IllegalStateException("Unexpected Bukkit version: " + bukkitVersion);
      }
      try {
        version = Integer.parseInt(parts[1]);
      } catch (NumberFormatException exception) {
        throw new IllegalStateException("Unexpected Bukkit version: " + bukkitVersion, exception);
      }
      initialized = true;
    }

    public static int getVersion() {
      initialize();
      return version;
    }

    public static boolean isInitialized() {
      return initialized;
    }

    private static Particle getBukkitParticle(ParticleEffect effect) {
      switch (effect) {
        case EXPLOSION_NORMAL: return Particle.POOF;
        case EXPLOSION_LARGE: return Particle.EXPLOSION;
        case FIREWORKS_SPARK: return Particle.FIREWORK;
        case WATER_BUBBLE: return Particle.BUBBLE;
        case WATER_SPLASH: return Particle.SPLASH;
        case WATER_WAKE: return Particle.FISHING;
        case SUSPENDED:
        case SUSPENDED_DEPTH: return Particle.UNDERWATER;
        case CRIT: return Particle.CRIT;
        case CRIT_MAGIC: return Particle.ENCHANTED_HIT;
        case SMOKE_NORMAL: return Particle.SMOKE;
        case SMOKE_LARGE: return Particle.LARGE_SMOKE;
        case SPELL: return Particle.EFFECT;
        case SPELL_INSTANT: return Particle.INSTANT_EFFECT;
        case SPELL_MOB:
        case SPELL_MOB_AMBIENT: return Particle.ENTITY_EFFECT;
        case SPELL_WITCH: return Particle.WITCH;
        case DRIP_WATER: return Particle.DRIPPING_WATER;
        case DRIP_LAVA: return Particle.DRIPPING_LAVA;
        case VILLAGER_ANGRY: return Particle.ANGRY_VILLAGER;
        case VILLAGER_HAPPY: return Particle.HAPPY_VILLAGER;
        case TOWN_AURA: return Particle.MYCELIUM;
        case NOTE: return Particle.NOTE;
        case PORTAL: return Particle.PORTAL;
        case ENCHANTMENT_TABLE: return Particle.ENCHANT;
        case FLAME: return Particle.FLAME;
        case LAVA: return Particle.LAVA;
        case CLOUD: return Particle.CLOUD;
        case REDSTONE: return Particle.DUST;
        case SNOWBALL: return Particle.ITEM_SNOWBALL;
        case SNOW_SHOVEL: return Particle.SNOWFLAKE;
        case SLIME: return Particle.ITEM_SLIME;
        case HEART: return Particle.HEART;
        case BARRIER: return Particle.BLOCK_MARKER;
        case ITEM_CRACK: return Particle.ITEM;
        case BLOCK_CRACK: return Particle.BLOCK;
        case BLOCK_DUST: return Particle.FALLING_DUST;
        case WATER_DROP: return Particle.RAIN;
        case MOB_APPEARANCE: return Particle.ELDER_GUARDIAN;
        case FOOTSTEP:
        case ITEM_TAKE:
        default: return null;
      }
    }

    private Object getBukkitData() {
      if (effect == SPELL_MOB || effect == SPELL_MOB_AMBIENT) {
        return Color.fromRGB(toChannel(offsetX), toChannel(offsetY), toChannel(offsetZ));
      }
      if (effect == REDSTONE) {
        return new Particle.DustOptions(
            Color.fromRGB(toChannel(offsetX), toChannel(offsetY), toChannel(offsetZ)), 1.0F);
      }
      if (data instanceof ParticleEffect.BlockData) {
        return data.getMaterial().createBlockData();
      }
      if (data instanceof ParticleEffect.ItemData) {
        // Legacy durability/data values have no general equivalent in modern item components.
        return new ItemStack(data.getMaterial());
      }
      return null;
    }

    private int toChannel(float component) {
      return Math.max(0, Math.min(255, Math.round(component * 255.0F)));
    }

    public void sendTo(Location center, Player player) {
      if (center == null || center.getWorld() == null) {
        throw new IllegalArgumentException("Particle location must belong to a world");
      }
      if (player == null || !player.isOnline() || !player.getWorld().equals(center.getWorld())) {
        return;
      }
      Particle particle = getBukkitParticle(effect);
      if (particle == null) {
        throw new IllegalStateException("Unsupported particle effect: " + effect.name());
      }
      player.spawnParticle(particle, center, amount, offsetX, offsetY, offsetZ,
          speed, getBukkitData(), longDistance);
    }

    public void sendTo(Location center, List<Player> players) {
      if (players == null || players.isEmpty()) {
        throw new IllegalArgumentException("The player list is empty");
      }
      for (Player player : players) {
        sendTo(center, player);
      }
    }

    public void sendTo(Location center, double range) {
      if (center == null || center.getWorld() == null) {
        throw new IllegalArgumentException("Particle location must belong to a world");
      }
      if (range < 1) {
        throw new IllegalArgumentException("The range is lower than 1");
      }
      double squaredRange = range * range;
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (player.getWorld().equals(center.getWorld())
            && player.getLocation().distanceSquared(center) <= squaredRange) {
          sendTo(center, player);
        }
      }
    }
  }
}