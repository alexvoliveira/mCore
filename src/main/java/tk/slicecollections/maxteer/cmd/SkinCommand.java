package tk.slicecollections.maxteer.cmd;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.skins.MenuSkinCatalog;
import tk.slicecollections.maxteer.skins.MenuSkinHistory;
import tk.slicecollections.maxteer.skins.SkinLibrary;
import tk.slicecollections.maxteer.utils.MessageUtils;

public final class SkinCommand extends Commands {
  public SkinCommand() {
    super("skin", "skins");
  }

  @Override
  public void perform(CommandSender sender, String label, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(MessageUtils.error("SKIN", "Este comando só pode ser utilizado dentro do jogo."));
      return;
    }

    Player player = (Player) sender;
    SkinLibrary library = Core.getInstance().getSkinLibrary();
    if (library == null || library.getSkins().isEmpty()) {
      player.sendMessage(MessageUtils.error("SKIN", "Nenhuma skin está configurada em skins.yml."));
      return;
    }

    if (args.length > 0 && ("history".equalsIgnoreCase(args[0])
        || "historico".equalsIgnoreCase(args[0]) || "histórico".equalsIgnoreCase(args[0])
        || "hist".equalsIgnoreCase(args[0]))) {
      MenuSkinHistory.open(player);
      return;
    }
    new MenuSkinCatalog(player, 0);
  }
}
