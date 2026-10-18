package tk.slicecollections.maxteer.skins;

public final class SkinDefinition {
  private final String id;
  private final String displayName;
  private final String ownerName;
  private final String textureValue;
  private final String textureSignature;

  public SkinDefinition(String id, String displayName, String ownerName, String textureValue, String textureSignature) {
    this.id = id;
    this.displayName = displayName;
    this.ownerName = ownerName;
    this.textureValue = textureValue;
    this.textureSignature = textureSignature;
  }

  public String getId() { return id; }
  public String getDisplayName() { return displayName; }
  public String getOwnerName() { return ownerName; }
  public String getTextureValue() { return textureValue; }
  public String getTextureSignature() { return textureSignature; }

  public boolean hasStoredTexture() {
    return textureValue != null && !textureValue.trim().isEmpty()
        && textureSignature != null && !textureSignature.trim().isEmpty();
  }
}
