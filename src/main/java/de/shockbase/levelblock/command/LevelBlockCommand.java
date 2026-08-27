package de.shockbase.levelblock.command;

import de.shockbase.levelblock.lobby.Lobby;
import de.shockbase.levelblock.lobby.LobbyManager;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.SessionManager;
import de.shockbase.levelblock.session.WorldProgress;
import de.shockbase.levelblock.util.SafeLocationFinder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class LevelBlockCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final SessionManager sessionManager;
    private final LobbyManager lobbyManager;
    private final SafeLocationFinder safeLocationFinder;

    public LevelBlockCommand(
            SessionManager sessionManager,
            LobbyManager lobbyManager,
            SafeLocationFinder safeLocationFinder
    ) {
        this.sessionManager = sessionManager;
        this.lobbyManager = lobbyManager;
        this.safeLocationFinder = safeLocationFinder;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "lobby" -> lobby(sender);
                case "start" -> start(sender);
                case "stop" -> stop(sender, args);
                case "invite" -> invite(sender, args);
                case "join" -> join(sender, args);
                case "leave" -> leave(sender);
                case "info" -> info(sender, args);
                case "list" -> list(sender);
                case "delete" -> delete(sender, args);
                case "help" -> {
                    sendHelp(sender);
                    yield true;
                }
                default -> {
                    sender.sendMessage(Component.text("Unbekannter Unterbefehl. /levelblock help", NamedTextColor.RED));
                    yield true;
                }
            };
        } catch (IllegalStateException | IllegalArgumentException exception) {
            sender.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            return true;
        }
    }

    private boolean lobby(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (sessionManager.getActiveSession(player) != null) {
            throw new IllegalStateException("Du bist bereits in einer aktiven Session.");
        }
        Lobby lobby = lobbyManager.createLobby(player);
        player.sendMessage(Component.text(
                "5x5-Lobby erstellt. Mittelpunkt: " + lobby.getCenterX() + ", " + lobby.getCenterZ()
                        + " in " + lobby.getWorldName() + ".",
                NamedTextColor.GREEN
        ));
        player.sendMessage(Component.text(
                "Alle Spieler, die nach dem Countdown in diesem Bereich stehen, werden Mitspieler.",
                NamedTextColor.GRAY
        ));
        return true;
    }

    private boolean start(CommandSender sender) {
        Player player = requirePlayer(sender);
        lobbyManager.startCountdown(player);
        return true;
    }

    private boolean stop(CommandSender sender, String[] args) {
        LevelBlockSession session;
        if (args.length >= 2) {
            requireAdmin(sender);
            session = sessionManager.findSession(args[1])
                    .orElseThrow(() -> new IllegalArgumentException("Session nicht gefunden."));
        } else {
            Player player = requirePlayer(sender);
            session = sessionManager.getActiveSession(player);
            if (session == null) {
                throw new IllegalStateException("Du bist in keiner aktiven Session.");
            }
            if (!session.isOwner(player.getUniqueId()) && !sender.hasPermission("levelblock.admin")) {
                throw new IllegalStateException("Nur der Session-Besitzer darf die Session stoppen.");
            }
        }

        if (!session.isActive()) {
            throw new IllegalStateException("Diese Session ist bereits gestoppt.");
        }
        sessionManager.stopSession(session);
        sender.sendMessage(Component.text("Session " + session.getId() + " gestoppt und gespeichert.", NamedTextColor.YELLOW));
        return true;
    }

    private boolean invite(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("Verwendung: /levelblock invite <spieler>");
        }

        LevelBlockSession session = sessionManager.getActiveSession(player);
        if (session == null) {
            throw new IllegalStateException("Du bist in keiner aktiven Session.");
        }
        if (!session.isOwner(player.getUniqueId())) {
            throw new IllegalStateException("Nur der Session-Besitzer darf Spieler einladen.");
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            throw new IllegalArgumentException("Spieler ist nicht online.");
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("Du bist bereits Besitzer der Session.");
        }
        if (session.isMember(target.getUniqueId())) {
            throw new IllegalStateException("Dieser Spieler ist bereits Mitglied.");
        }
        if (sessionManager.getActiveSession(target) != null) {
            throw new IllegalStateException("Dieser Spieler ist bereits in einer anderen aktiven Session.");
        }

        sessionManager.invite(session, target.getUniqueId());
        player.sendMessage(Component.text(target.getName() + " wurde eingeladen.", NamedTextColor.GREEN));
        target.sendMessage(Component.text(
                player.getName() + " hat dich zu LevelBlock eingeladen. Nutze /levelblock join " + player.getName(),
                NamedTextColor.GOLD
        ));
        return true;
    }

    private boolean join(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (sessionManager.getActiveSession(player) != null) {
            throw new IllegalStateException("Du bist bereits in einer aktiven Session.");
        }

        LevelBlockSession session;
        if (args.length >= 2) {
            session = sessionManager.findInvitedSession(player.getUniqueId(), args[1])
                    .orElseThrow(() -> new IllegalArgumentException("Keine passende aktive Einladung gefunden."));
        } else {
            List<LevelBlockSession> invitations = sessionManager.getInvitedSessions(player.getUniqueId());
            if (invitations.isEmpty()) {
                throw new IllegalStateException("Du hast keine aktive Einladung.");
            }
            if (invitations.size() > 1) {
                throw new IllegalStateException("Du hast mehrere Einladungen. Nutze /levelblock join <besitzer|uuid>.");
            }
            session = invitations.getFirst();
        }

        if (!sessionManager.join(session, player.getUniqueId())) {
            throw new IllegalStateException("Beitritt fehlgeschlagen.");
        }

        WorldProgress progress = sessionManager.getOrCreateWorldProgress(session, player.getLocation());
        if (!progress.isUnlocked(player.getLocation().getBlockX(), player.getLocation().getBlockZ())) {
            // Der Location-Listener korrigiert die Position spaetestens beim naechsten Tick/Move.
            player.teleport(safeLocationFinder.findNearestAllowed(player.getLocation(), progress));
        }

        player.sendMessage(Component.text("Du bist der Session von " + session.getOwnerName() + " beigetreten.", NamedTextColor.GREEN));
        Player owner = Bukkit.getPlayer(session.getOwnerId());
        if (owner != null) {
            owner.sendMessage(Component.text(player.getName() + " ist deiner Session beigetreten.", NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean leave(CommandSender sender) {
        Player player = requirePlayer(sender);
        LevelBlockSession session = sessionManager.getActiveSession(player);
        if (session == null) {
            throw new IllegalStateException("Du bist in keiner aktiven Session.");
        }
        if (session.isOwner(player.getUniqueId())) {
            throw new IllegalStateException("Als Besitzer kannst du die Session nicht verlassen. Nutze /levelblock stop.");
        }
        if (!sessionManager.leave(session, player.getUniqueId())) {
            throw new IllegalStateException("Session konnte nicht verlassen werden.");
        }
        player.sendMessage(Component.text("Du hast die LevelBlock-Session verlassen.", NamedTextColor.YELLOW));
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        LevelBlockSession session;
        if (args.length >= 2) {
            if (!sender.hasPermission("levelblock.admin")) {
                throw new IllegalStateException("Dafuer brauchst du levelblock.admin.");
            }
            session = sessionManager.findSession(args[1])
                    .orElseThrow(() -> new IllegalArgumentException("Session nicht gefunden."));
        } else if (sender instanceof Player player) {
            session = sessionManager.getActiveSession(player);
            if (session == null) {
                throw new IllegalStateException("Du bist in keiner aktiven Session. Admins: /levelblock info <uuid>.");
            }
        } else {
            throw new IllegalArgumentException("Konsole: /levelblock info <uuid>");
        }

        sender.sendMessage(Component.text("LevelBlock Session", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("UUID: " + session.getId(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Besitzer: " + session.getOwnerName() + " (" + session.getOwnerId() + ")", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Status: " + session.getStatus(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Erstellt: " + DATE_FORMAT.format(Instant.ofEpochMilli(session.getCreatedAt())), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Mitglieder: " + session.getMembers().size(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Welten: " + session.getWorlds().size(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Freigeschaltete Saeulen gesamt: " + sessionManager.countUnlocked(session), NamedTextColor.GRAY));
        return true;
    }

    private boolean list(CommandSender sender) {
        requireAdmin(sender);
        List<LevelBlockSession> sessions = sessionManager.getSessions().stream()
                .sorted(Comparator.comparingLong(LevelBlockSession::getCreatedAt).reversed())
                .toList();
        if (sessions.isEmpty()) {
            sender.sendMessage(Component.text("Keine gespeicherten Sessions.", NamedTextColor.GRAY));
            return true;
        }

        sender.sendMessage(Component.text("Gespeicherte LevelBlock-Sessions:", NamedTextColor.GOLD));
        for (LevelBlockSession session : sessions) {
            sender.sendMessage(Component.text(
                    session.getId() + " | " + session.getStatus() + " | " + session.getOwnerName()
                            + " | Mitglieder: " + session.getMembers().size()
                            + " | Saeulen: " + sessionManager.countUnlocked(session),
                    NamedTextColor.GRAY
            ));
        }
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("Verwendung: /levelblock delete <uuid>");
        }
        LevelBlockSession session = sessionManager.findSession(args[1])
                .orElseThrow(() -> new IllegalArgumentException("Session nicht gefunden."));
        sessionManager.deleteSession(session);
        sender.sendMessage(Component.text("Session " + session.getId() + " geloescht.", NamedTextColor.RED));
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalStateException("Dieser Befehl kann nur von einem Spieler ausgefuehrt werden.");
        }
        return player;
    }

    private void requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("levelblock.admin")) {
            throw new IllegalStateException("Dafuer brauchst du levelblock.admin.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("LevelBlock Befehle", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/levelblock lobby - 5x5-Lobby anlegen", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/levelblock start - Countdown und neue Session starten", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/levelblock stop - eigene Session stoppen", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/levelblock invite <spieler> - Spieler einladen", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/levelblock join [besitzer|uuid] - Einladung annehmen", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/levelblock leave - Session verlassen", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/levelblock info - aktuelle Session anzeigen", NamedTextColor.GRAY));
        if (sender.hasPermission("levelblock.admin")) {
            sender.sendMessage(Component.text("/levelblock list", NamedTextColor.DARK_GRAY));
            sender.sendMessage(Component.text("/levelblock info <uuid>", NamedTextColor.DARK_GRAY));
            sender.sendMessage(Component.text("/levelblock stop <uuid>", NamedTextColor.DARK_GRAY));
            sender.sendMessage(Component.text("/levelblock delete <uuid>", NamedTextColor.DARK_GRAY));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(
                    "lobby", "start", "stop", "invite", "join", "leave", "info", "help"
            ));
            if (sender.hasPermission("levelblock.admin")) {
                options.add("list");
                options.add("delete");
            }
            return filter(options, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("join") && sender instanceof Player player) {
            List<String> options = new ArrayList<>();
            for (LevelBlockSession session : sessionManager.getInvitedSessions(player.getUniqueId())) {
                options.add(session.getOwnerName());
                options.add(session.getId().toString());
            }
            return filter(options, args[1]);
        }

        if (args.length == 2 && sender.hasPermission("levelblock.admin")
                && (args[0].equalsIgnoreCase("delete")
                || args[0].equalsIgnoreCase("stop")
                || args[0].equalsIgnoreCase("info"))) {
            return filter(sessionManager.getSessions().stream().map(session -> session.getId().toString()).toList(), args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
                .distinct()
                .sorted()
                .toList();
    }
}
