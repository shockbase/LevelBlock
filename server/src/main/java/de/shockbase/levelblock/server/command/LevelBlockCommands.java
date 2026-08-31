package de.shockbase.levelblock.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.shockbase.levelblock.server.LevelBlockRuntime;
import de.shockbase.levelblock.server.lobby.Lobby;
import de.shockbase.levelblock.server.session.SessionManager;
import de.shockbase.levelblock.session.LevelBlockSession;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class LevelBlockCommands {

    private LevelBlockCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            Supplier<LevelBlockRuntime> runtimeSupplier
    ) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("levelblock")
                .executes(context -> run(context.getSource(), runtimeSupplier, runtime -> help(context.getSource())))
                .then(Commands.literal("help")
                        .executes(context -> run(context.getSource(), runtimeSupplier, runtime -> help(context.getSource()))))
                .then(Commands.literal("lobby")
                        .executes(context -> run(context.getSource(), runtimeSupplier, runtime -> {
                            ServerPlayer player = requirePlayer(context.getSource());
                            Lobby lobby = runtime.lobbies().create(player);
                            success(context.getSource(), "5x5-Lobby bei " + lobby.centerX() + ", " + lobby.centerZ() + " erstellt.");
                        })))
                .then(Commands.literal("start")
                        .executes(context -> run(context.getSource(), runtimeSupplier, runtime -> {
                            runtime.lobbies().start(requirePlayer(context.getSource()));
                            success(context.getSource(), "Lobby-Countdown gestartet.");
                        })))
                .then(Commands.literal("cancel")
                        .executes(context -> run(context.getSource(), runtimeSupplier, runtime -> {
                            runtime.lobbies().cancel(requirePlayer(context.getSource()));
                            warn(context.getSource(), "Lobby abgebrochen.");
                        })))
                .then(Commands.literal("stop")
                        .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                stopOwn(context.getSource(), runtime.sessions())))
                        .then(Commands.argument("session", StringArgumentType.word())
                                .requires(LevelBlockCommands::isAdmin)
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        sessionIds(context.getSource(), runtimeSupplier), builder
                                ))
                                .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                        stop(context.getSource(), runtime.sessions(),
                                                StringArgumentType.getString(context, "session"))))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder
                                ))
                                .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                        invite(context.getSource(), runtime.sessions(),
                                                StringArgumentType.getString(context, "player"))))))
                .then(Commands.literal("join")
                        .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                join(context.getSource(), runtime.sessions(), null)))
                        .then(Commands.argument("session", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        invitationTokens(context.getSource(), runtimeSupplier), builder
                                ))
                                .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                        join(context.getSource(), runtime.sessions(),
                                                StringArgumentType.getString(context, "session"))))))
                .then(Commands.literal("leave")
                        .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                leave(context.getSource(), runtime.sessions()))))
                .then(Commands.literal("info")
                        .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                infoOwn(context.getSource(), runtime.sessions())))
                        .then(Commands.argument("session", StringArgumentType.word())
                                .requires(LevelBlockCommands::isAdmin)
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        sessionIds(context.getSource(), runtimeSupplier), builder
                                ))
                                .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                        info(context.getSource(), runtime.sessions(),
                                                StringArgumentType.getString(context, "session"))))))
                .then(Commands.literal("list")
                        .requires(LevelBlockCommands::isAdmin)
                        .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                list(context.getSource(), runtime.sessions()))))
                .then(Commands.literal("delete")
                        .requires(LevelBlockCommands::isAdmin)
                        .then(Commands.argument("session", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        sessionIds(context.getSource(), runtimeSupplier), builder
                                ))
                                .executes(context -> run(context.getSource(), runtimeSupplier, runtime ->
                                        delete(context.getSource(), runtime.sessions(),
                                                StringArgumentType.getString(context, "session"))))));

        var root = dispatcher.register(command);
        dispatcher.register(Commands.literal("lb").redirect(root));
    }

    private static int run(
            CommandSourceStack source,
            Supplier<LevelBlockRuntime> runtimeSupplier,
            CommandAction action
    ) {
        LevelBlockRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            source.sendFailure(Component.literal("LevelBlock ist noch nicht bereit."));
            return 0;
        }
        try {
            action.run(runtime);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal(exception.getMessage() == null
                    ? "LevelBlock-Befehl fehlgeschlagen."
                    : exception.getMessage()));
            return 0;
        }
    }

    private static void stopOwn(CommandSourceStack source, SessionManager sessions) {
        ServerPlayer player = requirePlayer(source);
        LevelBlockSession session = requireActive(sessions, player);
        if (!session.isOwner(player.getUUID()) && !isAdmin(source)) {
            throw new IllegalStateException("Nur der Session-Besitzer darf die Session stoppen.");
        }
        sessions.stopSession(session);
        warn(source, "Session " + session.getId() + " gestoppt.");
    }

    private static void stop(CommandSourceStack source, SessionManager sessions, String token) {
        LevelBlockSession session = find(sessions, token);
        if (!session.isActive()) {
            throw new IllegalStateException("Diese Session ist bereits gestoppt.");
        }
        sessions.stopSession(session);
        warn(source, "Session " + session.getId() + " gestoppt.");
    }

    private static void invite(CommandSourceStack source, SessionManager sessions, String playerName) {
        ServerPlayer owner = requirePlayer(source);
        LevelBlockSession session = requireActive(sessions, owner);
        if (!session.isOwner(owner.getUUID())) {
            throw new IllegalStateException("Nur der Session-Besitzer darf Spieler einladen.");
        }
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            throw new IllegalArgumentException("Spieler ist nicht online.");
        }
        if (target.getUUID().equals(owner.getUUID()) || session.isMember(target.getUUID())) {
            throw new IllegalStateException("Dieser Spieler ist bereits Mitglied.");
        }
        if (sessions.getActiveSession(target) != null) {
            throw new IllegalStateException("Dieser Spieler ist bereits in einer anderen Session.");
        }
        if (!sessions.invite(session, target.getUUID())) {
            throw new IllegalStateException("Dieser Spieler ist bereits eingeladen.");
        }
        success(source, target.getGameProfile().name() + " wurde eingeladen.");
        target.sendSystemMessage(Component.literal(
                owner.getGameProfile().name() + " hat dich eingeladen. Nutze /levelblock join "
                        + owner.getGameProfile().name()
        ).withStyle(ChatFormatting.GOLD));
    }

    private static void join(CommandSourceStack source, SessionManager sessions, String token) {
        ServerPlayer player = requirePlayer(source);
        if (sessions.getActiveSession(player) != null) {
            throw new IllegalStateException("Du bist bereits in einer aktiven Session.");
        }
        LevelBlockSession session;
        if (token == null) {
            List<LevelBlockSession> invitations = sessions.getInvitedSessions(player.getUUID());
            if (invitations.isEmpty()) {
                throw new IllegalStateException("Du hast keine aktive Einladung.");
            }
            if (invitations.size() != 1) {
                throw new IllegalStateException("Nutze /levelblock join <besitzer|uuid>.");
            }
            session = invitations.getFirst();
        } else {
            session = sessions.findInvitedSession(player.getUUID(), token)
                    .orElseThrow(() -> new IllegalArgumentException("Keine passende Einladung gefunden."));
        }
        if (!sessions.join(session, player.getUUID())) {
            throw new IllegalStateException("Beitritt fehlgeschlagen.");
        }
        success(source, "Du bist der Session von " + session.getOwnerName() + " beigetreten.");
        ServerPlayer owner = source.getServer().getPlayerList().getPlayer(session.getOwnerId());
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(
                    player.getGameProfile().name() + " ist deiner Session beigetreten."
            ).withStyle(ChatFormatting.GREEN));
        }
    }

    private static void leave(CommandSourceStack source, SessionManager sessions) {
        ServerPlayer player = requirePlayer(source);
        LevelBlockSession session = requireActive(sessions, player);
        if (session.isOwner(player.getUUID())) {
            throw new IllegalStateException("Als Besitzer nutze /levelblock stop.");
        }
        if (!sessions.leave(session, player.getUUID())) {
            throw new IllegalStateException("Session konnte nicht verlassen werden.");
        }
        warn(source, "Du hast die LevelBlock-Session verlassen.");
    }

    private static void infoOwn(CommandSourceStack source, SessionManager sessions) {
        info(source, requireActive(sessions, requirePlayer(source)));
    }

    private static void info(CommandSourceStack source, SessionManager sessions, String token) {
        info(source, find(sessions, token));
    }

    private static void info(CommandSourceStack source, LevelBlockSession session) {
        source.sendSystemMessage(Component.literal("LevelBlock Session").withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(Component.literal("UUID: " + session.getId()).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(Component.literal("Besitzer: " + session.getOwnerName()).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(Component.literal("Status: " + session.getStatus()).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(Component.literal("Mitglieder: " + session.getMembers().size()).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(Component.literal("Säulen: "
                + session.getWorlds().values().stream().mapToInt(progress -> progress.getUnlockedCount()).sum()
        ).withStyle(ChatFormatting.GRAY));
    }

    private static void list(CommandSourceStack source, SessionManager sessions) {
        List<LevelBlockSession> stored = sessions.getSessions().stream()
                .sorted(Comparator.comparingLong(LevelBlockSession::getCreatedAt).reversed())
                .toList();
        if (stored.isEmpty()) {
            source.sendSystemMessage(Component.literal("Keine Sessions vorhanden.").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (LevelBlockSession session : stored) {
            source.sendSystemMessage(Component.literal(
                    session.getId() + " | " + session.getStatus() + " | " + session.getOwnerName()
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void delete(CommandSourceStack source, SessionManager sessions, String token) {
        LevelBlockSession session = find(sessions, token);
        sessions.deleteSession(session);
        source.sendSystemMessage(Component.literal("Session " + session.getId() + " gelöscht.")
                .withStyle(ChatFormatting.RED));
    }

    private static void help(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("LevelBlock Befehle").withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(Component.literal(
                "/levelblock lobby | start | cancel | stop | invite <spieler> | join [session] | leave | info"
        ).withStyle(ChatFormatting.GRAY));
        if (isAdmin(source)) {
            source.sendSystemMessage(Component.literal(
                    "Admin: /levelblock list | stop <session> | info <session> | delete <session>"
            ).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static LevelBlockSession requireActive(SessionManager sessions, ServerPlayer player) {
        LevelBlockSession session = sessions.getActiveSession(player);
        if (session == null) {
            throw new IllegalStateException("Du bist in keiner aktiven Session.");
        }
        return session;
    }

    private static LevelBlockSession find(SessionManager sessions, String token) {
        return sessions.findSession(token)
                .orElseThrow(() -> new IllegalArgumentException("Session nicht gefunden."));
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw new IllegalStateException("Dieser Befehl kann nur von einem Spieler ausgeführt werden.");
        }
        return player;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }

    private static List<String> sessionIds(
            CommandSourceStack source,
            Supplier<LevelBlockRuntime> runtimeSupplier
    ) {
        LevelBlockRuntime runtime = runtimeSupplier.get();
        return runtime == null ? List.of() : runtime.sessions().getSessions().stream()
                .map(session -> session.getId().toString())
                .toList();
    }

    private static List<String> invitationTokens(
            CommandSourceStack source,
            Supplier<LevelBlockRuntime> runtimeSupplier
    ) {
        LevelBlockRuntime runtime = runtimeSupplier.get();
        ServerPlayer player = source.getPlayer();
        if (runtime == null || player == null) {
            return List.of();
        }
        return runtime.sessions().getInvitedSessions(player.getUUID()).stream()
                .flatMap(session -> java.util.stream.Stream.of(session.getOwnerName(), session.getId().toString()))
                .distinct()
                .toList();
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }

    private static void warn(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.YELLOW), false);
    }

    @FunctionalInterface
    private interface CommandAction {
        void run(LevelBlockRuntime runtime);
    }
}
