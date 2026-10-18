package tk.slicecollections.maxteer.skins;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.cmd.Commands;
import tk.slicecollections.maxteer.utils.MessageUtils;

public final class SkinCommand extends Commands {

  public SkinCommand() {
    super("skin", "skins");
  }

  @Override
  public void perform(CommandSender sender, String label, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(MessageUtils.error("COMANDO", "Apenas jogadores podem utilizar este comando."));
      return;
    }

    Player player = (Player) sender;
    if (!player.hasPermission("mcore.cmd.skin")) {
      player.sendMessage(MessageUtils.error("PERMISSÃO", "Você não possui permissão para utilizar este comando."));
      return;
    }

    Core core = Core.getInstance();
    SkinLibrary library = core == null ? null : core.getSkinLibrary();
    if (library == null) {
      player.sendMessage(MessageUtils.error("SKIN", "O sistema de skins ainda não está pronto."));
      return;
    }

    if (args.length == 0) {
      new MenuSkinCatalog(player, 0);
      return;
    }

    if (args.length == 1 && args[0].equalsIgnoreCase("history")) {
      MenuSkinHistory.open(player);
      return;
    }

    if (args.length != 1) {
      player.sendMessage(MessageUtils.info("Use §a/skin §7para abrir o catálogo, §a/skin history §7para ver o histórico ou §a/skin <id> §7para aplicar uma skin."));
      return;
    }

    library.applySkin(player, args[0]);
  }
}
