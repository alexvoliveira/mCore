package tk.slicecollections.maxteer.cmd;

import tk.slicecollections.maxteer.utils.MessageUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.player.fake.FakeManager;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.utils.Validator;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static tk.slicecollections.maxteer.player.fake.FakeManager.ALEX;
import static tk.slicecollections.maxteer.player.fake.FakeManager.STEVE;

/**
 * @author Maxter
 */
public class FakeCommand extends Commands {

  public FakeCommand() {
    super("fake", "faker", "fakel");
  }

  @Override
  public void perform(CommandSender sender, String label, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(MessageUtils.error("COMANDO", "Apenas jogadores podem utilizar este comando."));
      return;
    }

    Player player = (Player) sender;
    if (!player.hasPermission("mcore.cmd.fake") || (label.equalsIgnoreCase("fakel") && !player.hasPermission("mcore.cmd.fakelist"))) {
      player.sendMessage(MessageUtils.error("PERMISSÃO", "Você não possui permissão para utilizar este comando."));
      return;
    }

    Profile profile = Profile.getProfile(player.getName());
    if (label.equalsIgnoreCase("fake")) {
      if (profile != null && profile.playingGame()) {
        player.sendMessage(MessageUtils.error("AÇÃO BLOQUEADA", "Você não pode utilizar este comando enquanto estiver em uma partida."));
        return;
      }

      if (FakeManager.getRandomNicks().stream().noneMatch(FakeManager::isUsable)) {
        player.sendMessage(MessageUtils.error("NICKNAME INDISPONÍVEL", "Nenhum nickname está disponível para uso no momento."));
        return;
      }

      if (args.length == 0) {
        FakeManager.sendRole(player);
        return;
      }

      String roleName = args[0];
      if (!FakeManager.isFakeRole(roleName)) {
        EnumSound.VILLAGER_NO.play(player, 1.0F, 1.0F);
        FakeManager.sendRole(player);
        return;
      }

      if (Role.getRoleByName(roleName) == null) {
        EnumSound.VILLAGER_NO.play(player, 1.0F, 1.0F);
        FakeManager.sendRole(player);
        return;
      }

      if (args.length == 1) {
        EnumSound.ORB_PICKUP.play(player, 1.0F, 2.0F);
        FakeManager.sendSkin(player, roleName);
        return;
      }

      String skin = args[1];
      if (!skin.equalsIgnoreCase("alex") && !skin.equalsIgnoreCase("steve")) {
        EnumSound.VILLAGER_NO.play(player, 1.0F, 1.0F);
        FakeManager.sendSkin(player, roleName);
        return;
      }
      
      List<String> enabled = FakeManager.getRandomNicks().stream().filter(FakeManager::isUsable).collect(Collectors.toList());
      String fakeName = enabled.isEmpty() ? null : enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
      if (fakeName == null) {
        player.sendMessage(MessageUtils.error("NICKNAME INDISPONÍVEL", "Nenhum nickname está disponível para uso no momento."));
        return;
      }

      enabled.clear();
      FakeManager.applyFake(player, fakeName, roleName, skin.equalsIgnoreCase("steve") ? STEVE : ALEX);
    } else if (label.equalsIgnoreCase("faker")) {
      if (profile != null && profile.playingGame()) {
        player.sendMessage(MessageUtils.error("AÇÃO BLOQUEADA", "Você não pode utilizar este comando enquanto estiver em uma partida."));
        return;
      }

      if (!FakeManager.isFake(player.getName())) {
        player.sendMessage(MessageUtils.error("NICKNAME", "Você não está utilizando um nickname falso."));
        return;
      }

      FakeManager.removeFake(player);
    } else {
      List<String> nicked = FakeManager.listNicked();
      StringBuilder sb = new StringBuilder();
      for (int index = 0; index < nicked.size(); index++) {
        sb.append("§7• §f").append(nicked.get(index)).append(" §7é na verdade §amcorefakereal:").append(nicked.get(index)).append(index + 1 == nicked.size() ? "" : "\n");
      }

      nicked.clear();
      if (sb.length() == 0) {
        sb.append("§7Nenhum jogador está utilizando um nickname falso.");
      }

      player.sendMessage(MessageUtils.info("§a§lNICKNAMES FALSOS\n\n" + sb.toString()));
    }
  }
}
