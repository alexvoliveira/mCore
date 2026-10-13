package tk.slicecollections.maxteer.cmd;

import tk.slicecollections.maxteer.utils.MessageUtils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.Manager;
import tk.slicecollections.maxteer.bukkit.BukkitParty;
import tk.slicecollections.maxteer.bukkit.BukkitPartyManager;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.utils.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

import static tk.slicecollections.maxteer.party.PartyRole.LEADER;

/**
 * @author Maxter
 */
public class PartyCommand extends Commands {

  public PartyCommand() {
    super("party", "p");
  }

  @Override
  public void perform(CommandSender sender, String label, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(MessageUtils.error("COMANDO", "Apenas jogadores podem utilizar este comando."));
      return;
    }

    Player player = (Player) sender;
    if (label.equalsIgnoreCase("p")) {
      if (args.length == 0) {
        player.sendMessage(MessageUtils.error("SINTAXE", "Utilize /p [mensagem] para conversar com a sua Party."));
        return;
      }

      BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
      if (party == null) {
        player.sendMessage(MessageUtils.error("PARTY", "Você não pertence a uma Party."));
        return;
      }

      party.broadcast("§7[§d§lPARTY§7] ➜ " + Role.getPrefixed(player.getName()) + "§7: " + StringUtils.join(args, " "));
    } else {
      if (args.length == 0) {
        player.sendMessage(MessageUtils.info("§a§lAJUDA §7• COMANDOS DE PARTY\n§a/p [mensagem] §7➜ Conversar com os membros.\n§a/party abrir §7➜ Tornar a Party pública.\n§a/party fechar §7➜ Tornar a Party privada.\n§a/party entrar [jogador] §7➜ Entrar em uma Party pública.\n§a/party aceitar [jogador] §7➜ Aceitar um convite.\n§a/party convidar [jogador] §7➜ Convidar um jogador.\n§a/party deletar §7➜ Excluir a Party.\n§a/party expulsar [jogador] §7➜ Expulsar um membro.\n§a/party info §7➜ Ver informações da Party.\n§a/party negar [jogador] §7➜ Recusar um convite.\n§a/party sair §7➜ Sair da Party.\n§a/party transferir [jogador] §7➜ Transferir a liderança."));
        return;
      }

      String action = args[0];
      if (action.equalsIgnoreCase("abrir")) {
        BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
        if (party == null) {
          player.sendMessage(MessageUtils.error("PARTY", "Você não pertence a uma Party."));
          return;
        }

        if (!party.isLeader(player.getName())) {
          player.sendMessage(MessageUtils.error("LIDERANÇA", "Você não é o Líder da Party."));
          return;
        }

        if (party.isOpen()) {
          player.sendMessage(MessageUtils.info("A Party já está pública."));
          return;
        }

        party.setIsOpen(true);
        player.sendMessage(MessageUtils.success("PARTY ABERTA", "Você abriu a party para qualquer jogador."));
      } else if (action.equalsIgnoreCase("fechar")) {
        BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
        if (party == null) {
          player.sendMessage(MessageUtils.error("PARTY", "Você não pertence a uma Party."));
          return;
        }

        if (!party.isLeader(player.getName())) {
          player.sendMessage(MessageUtils.error("LIDERANÇA", "Você não é o Líder da Party."));
          return;
        }

        if (!party.isOpen()) {
          player.sendMessage(MessageUtils.info("A Party já está privada."));
          return;
        }

        party.setIsOpen(false);
        player.sendMessage(MessageUtils.success("PARTY FECHADA", "A Party agora aceita apenas convidados."));
      } else if (action.equalsIgnoreCase("entrar")) {
        if (args.length == 1) {
          player.sendMessage(MessageUtils.error("SINTAXE", "Utilize /party entrar [jogador]"));
          return;
        }

        String target = args[1];
        if (target.equalsIgnoreCase(player.getName())) {
          player.sendMessage(MessageUtils.error("AÇÃO BLOQUEADA", "Você não pode entrar na party de você mesmo."));
          return;
        }

        BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
        if (party != null) {
          player.sendMessage(MessageUtils.error("PARTY", "Você já pertence a uma Party."));
          return;
        }

        party = BukkitPartyManager.getLeaderParty(target);
        if (party == null) {
          player.sendMessage(MessageUtils.error("LIDERANÇA", Manager.getCurrent(target) + " não é um Líder de Party."));
          return;
        }

        target = party.getName(target);
        if (!party.isOpen()) {
          player.sendMessage(MessageUtils.error("PARTY FECHADA", "A Party de " + Manager.getCurrent(target) + " aceita apenas convidados."));
          return;
        }

        if (!party.canJoin()) {
          player.sendMessage(MessageUtils.error("PARTY LOTADA", "A Party de " + Manager.getCurrent(target) + " atingiu o limite de membros."));
          return;
        }

        party.join(player.getName());
        player.sendMessage(MessageUtils.success("PARTY", "Você entrou na Party de " + Role.getPrefixed(target) + "§7."));
      } else if (action.equalsIgnoreCase("aceitar")) {
        if (args.length == 1) {
          player.sendMessage(MessageUtils.error("SINTAXE", "Utilize /party aceitar [jogador]"));
          return;
        }

        String target = args[1];
        if (target.equalsIgnoreCase(player.getName())) {
          player.sendMessage(MessageUtils.error("CONVITE", "Você não pode aceitar convites de você mesmo."));
          return;
        }

        BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
        if (party != null) {
          player.sendMessage(MessageUtils.error("PARTY", "Você já pertence a uma Party."));
          return;
        }

        party = BukkitPartyManager.getLeaderParty(target);
        if (party == null) {
          player.sendMessage(MessageUtils.error("LIDERANÇA", Manager.getCurrent(target) + " não é um Líder de Party."));
          return;
        }

        target = party.getName(target);
        if (!party.isInvited(player.getName())) {
          player.sendMessage(MessageUtils.error("CONVITE", Manager.getCurrent(target) + " não convidou você para a Party."));
          return;
        }

        if (!party.canJoin()) {
          player.sendMessage(MessageUtils.error("PARTY LOTADA", "A Party de " + Manager.getCurrent(target) + " atingiu o limite de membros."));
          return;
        }

        party.join(player.getName());
        player.sendMessage(MessageUtils.success("PARTY", "Você entrou na Party de " + Role.getPrefixed(target) + "§7."));
      } else if (action.equalsIgnoreCase("ajuda")) {
        player.sendMessage(MessageUtils.info("§a§lAJUDA §7• COMANDOS DE PARTY\n§a/p [mensagem] §7➜ Conversar com os membros.\n§a/party abrir §7➜ Tornar a Party pública.\n§a/party fechar §7➜ Tornar a Party privada.\n§a/party entrar [jogador] §7➜ Entrar em uma Party pública.\n§a/party aceitar [jogador] §7➜ Aceitar um convite.\n§a/party convidar [jogador] §7➜ Convidar um jogador.\n§a/party deletar §7➜ Excluir a Party.\n§a/party expulsar [jogador] §7➜ Expulsar um membro.\n§a/party info §7➜ Ver informações da Party.\n§a/party negar [jogador] §7➜ Recusar um convite.\n§a/party sair §7➜ Sair da Party.\n§a/party transferir [jogador] §7➜ Transferir a liderança."));
      } else if (action.equalsIgnoreCase("deletar")) {
        BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
        if (party == null) {
          player.sendMessage(MessageUtils.error("PARTY", "Você não pertence a uma Party."));
          return;
        }

        if (!party.isLeader(player.getName())) {
          player.sendMessage(MessageUtils.error("LIDERANÇA", "Você não é o Líder da Party."));
          return;
        }

        party.broadcast("§7[§d§lPARTY§7] ➜ " + Role.getPrefixed(player.getName()) + " §c§lEXCLUIU §7a Party.", true);
        party.delete();
        player.sendMessage(MessageUtils.success("PARTY REMOVIDA", "Você deletou a Party."));
      } else if (action.equalsIgnoreCase("expulsar")) {
        if (args.length == 1) {
          player.sendMessage(MessageUtils.error("SINTAXE", "Utilize /party expulsar [jogador]"));
          return;
        }

        BukkitParty party = BukkitPartyManager.getLeaderParty(player.getName());
        if (party == null) {
          player.sendMessage(MessageUtils.error("LIDERANÇA", "Você não é um Líder de Party."));
          return;
        }

        String target = args[1];
        if (target.equalsIgnoreCase(player.getName())) {
          player.sendMessage(MessageUtils.error("AÇÃO BLOQUEADA", "Você não pode se expulsar."));
          return;
        }

        if (!party.isMember(target)) {
          player.sendMessage(MessageUtils.error("PARTY", "Esse jogador não pertence a sua Party."));
          return;
        }

        target = party.getName(target);
        party.kick(target);
        party.broadcast("§7[§d§lPARTY§7] ➜ " + Role.getPrefixed(player.getName()) + " §c§lEXPULSOU §7" + Role.getPrefixed(target) + " §7da Party.");
      } else if (action.equalsIgnoreCase("info")) {
        BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
        if (party == null) {
          player.sendMessage(MessageUtils.error("PARTY", "Você não pertence a uma Party."));
          return;
        }

        List<String> members =
          party.listMembers().stream().filter(pp -> pp.getRole() != LEADER).map(pp -> (pp.isOnline() ? "§a" : "§c") + pp.getName()).collect(Collectors.toList());
        player.sendMessage(MessageUtils.info("§a§lINFORMAÇÕES DA PARTY\n§7Líder: " + Role.getPrefixed(party.getLeader()) + "\n§7Status: " + (party.isOpen() ? "§aPÚBLICA" : "§cPRIVADA") + "\n§7Membros: §f" + party.listMembers().size() + "/" + party.getSlots() + "\n§7Lista: " + StringUtils.join(members, "§7, ")));
      } else if (action.equalsIgnoreCase("negar")) {
        if (args.length == 1) {
          player.sendMessage(MessageUtils.error("SINTAXE", "Utilize /party negar [jogador]"));
          return;
        }

        String target = args[1];
        if (target.equalsIgnoreCase(player.getName())) {
          player.sendMessage(MessageUtils.error("CONVITE", "Você não pode negar convites de você mesmo."));
          return;
        }

        BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
        if (party != null) {
          player.sendMessage(MessageUtils.error("PARTY", "Você já pertence a uma Party."));
          return;
        }

        party = BukkitPartyManager.getLeaderParty(target);
        if (party == null) {
          player.sendMessage(MessageUtils.error("LIDERANÇA", Manager.getCurrent(target) + " não é um Líder de Party."));
          return;
        }

        target = party.getName(target);
        if (!party.isInvited(player.getName())) {
          player.sendMessage(MessageUtils.error("CONVITE", Manager.getCurrent(target) + " não convidou você para a Party."));
          return;
        }

        party.reject(player.getName());
        player.sendMessage(MessageUtils.success("CONVITE NEGADO", "Você negou o convite de Party de " + Role.getPrefixed(target) + "§7."));
      } else if (action.equalsIgnoreCase("sair")) {
        BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
        if (party == null) {
          player.sendMessage(MessageUtils.error("PARTY", "Você não pertence a uma Party."));
          return;
        }

        party.leave(player.getName());
        player.sendMessage(MessageUtils.success("PARTY ENCERRADA", "Você saiu da Party!"));
      } else if (action.equalsIgnoreCase("transferir")) {
        if (args.length == 1) {
          player.sendMessage(MessageUtils.error("SINTAXE", "Utilize /party transferir [jogador]"));
          return;
        }

        BukkitParty party = BukkitPartyManager.getLeaderParty(player.getName());
        if (party == null) {
          player.sendMessage(MessageUtils.error("LIDERANÇA", "Você não é um Líder de Party."));
          return;
        }

        String target = args[1];
        if (target.equalsIgnoreCase(player.getName())) {
          player.sendMessage(MessageUtils.error("AÇÃO BLOQUEADA", "Você não pode transferir a Party para você mesmo."));
          return;
        }

        if (!party.isMember(target)) {
          player.sendMessage(MessageUtils.error("PARTY", "Esse jogador não pertence a sua Party."));
          return;
        }

        target = party.getName(target);
        party.transfer(target);
        party.broadcast("§7[§d§lPARTY§7] ➜ " + Role.getPrefixed(player.getName()) + " §a§lTRANSFERIU A LIDERANÇA §7para " + Role.getPrefixed(target) + "§7.");
      } else {
        if (action.equalsIgnoreCase("convidar")) {
          if (args.length == 1) {
            player.sendMessage(MessageUtils.error("SINTAXE", "Utilize /party convidar [jogador]"));
            return;
          }

          action = args[1];
        }

        Player target = Bukkit.getPlayerExact(action);
        if (target == null) {
          player.sendMessage(MessageUtils.error("JOGADOR NÃO ENCONTRADO", "Usuário não encontrado."));
          return;
        }

        action = target.getName();
        if (action.equalsIgnoreCase(player.getName())) {
          player.sendMessage(MessageUtils.error("CONVITE", "Você não pode enviar convites para você mesmo."));
          return;
        }

        BukkitParty party = BukkitPartyManager.getMemberParty(player.getName());
        if (party == null) {
          party = BukkitPartyManager.createParty(player);
        }

        if (!party.isLeader(player.getName())) {
          player.sendMessage(MessageUtils.error("LIDERANÇA", "Apenas o Líder da Party pode enviar convites!"));
          return;
        }

        if (!party.canJoin()) {
          player.sendMessage(MessageUtils.error("PARTY", "A sua Party está lotada."));
          return;
        }

        if (party.isInvited(action)) {
          player.sendMessage(MessageUtils.error("CONVITE", "Você já enviou um convite para " + Manager.getCurrent(action) + "."));
          return;
        }

        if (BukkitPartyManager.getMemberParty(action) != null) {
          player.sendMessage(MessageUtils.error("PARTY", Manager.getCurrent(action) + " já pertence a uma Party."));
          return;
        }

        party.invite(target);
        player.sendMessage(MessageUtils.success("CONVITE ENVIADO", Role.getPrefixed(action) + " foi convidado para a Party. O convite expira em 60 segundos."));
      }
    }
  }
}
