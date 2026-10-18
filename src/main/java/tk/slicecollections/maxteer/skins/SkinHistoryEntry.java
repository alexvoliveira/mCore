package tk.slicecollections.maxteer.skins;

public final class SkinHistoryEntry {
  private final String skinId;
  private final String displayName;
  private final String ownerName;
  private final String textureValue;
  private final String textureSignature;
  private final long usedAt;

  public SkinHistoryEntry(String skinId, String displayName, String ownerName,
                          String textureValue, String textureSignature, long usedAt) {
    this.skinId = skinId;
    this.displayName = displayName;
    this.ownerName = ownerName;
    this.textureValue = textureValue;
    this.textureSignature = textureSignature;
    this.usedAt = usedAt;
  }

  public String getSkinId() { return skinId; }
  public String getDisplayName() { return displayName; }
  public String getOwnerName() { return ownerName; }
  public String getTextureValue() { return textureValue; }
  public String getTextureSignature() { return textureSignature; }
  public long getUsedAt() { return usedAt; }
}
